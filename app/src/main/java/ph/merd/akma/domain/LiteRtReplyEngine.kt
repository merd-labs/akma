package ph.merd.akma.domain

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import ph.merd.akma.safety.ModelOutputSafety

/** One CPU engine. The coordinator serializes calls and owns the timeout. */
class LiteRtReplyEngine(context: Context) : LocalReplyEngine {
    private val app = context.applicationContext
    private var engine: Engine? = null

    override suspend fun initialize(): Result<Unit> = guarded {
        engine?.close()
        engine = null
        val model = modelFile(app)
        if (!model.isFile) {
            model.parentFile?.mkdirs()
            val temporary = File(model.parentFile, "model-provision.tmp")
            try {
                app.assets.open(MODEL_NAME).use { source ->
                    temporary.outputStream().buffered().use { destination -> source.copyTo(destination) }
                }
                require(temporary.length() == MODEL_BYTES) { "Invalid bundled model." }
                require(temporary.renameTo(model)) { "Could not provision local model." }
            } catch (missing: java.io.FileNotFoundException) {
                throw ModelUnavailableException()
            } finally {
                temporary.delete()
            }
        }
        require(model.length() == MODEL_BYTES && sha256(model) == MODEL_SHA256) { "Invalid model file." }
        val started = SystemClock.elapsedRealtime()
        val loaded = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), cacheDir = app.cacheDir.absolutePath))
        try {
            loaded.initialize()
            engine = loaded
            Log.i(TAG, "model_initialized_ms=${SystemClock.elapsedRealtime() - started}")
        } catch (failure: Throwable) {
            loaded.close()
            throw failure
        }
    }

    override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = guarded {
        ReplyValidation.validate(request).getOrThrow()
        // Copied text is untrusted data: strip chat-template tokens so it cannot close a turn or forge a role.
        val safe = neutralizedForPrompt(request)
        val input = org.json.JSONObject().put("incoming_message", safe.message)
            .put("previous_context", safe.history).put("relationship", safe.relationship).toString()
        val raw = generate(prompt("analyze_v2.txt"), input, 192)
        val json = org.json.JSONObject(raw.trim())
        require(raw.trim().startsWith("{") && raw.trim().endsWith("}")) { "Malformed analysis." }
        val purpose = json.getString("message_purpose")
        val category = categoryFor(request.message, purpose)
        val actions = ActionCatalog.actionsFor(category).take(ReplyValidation.MAX_ACTIONS)
        AnalysisResult(category, purpose, true, actions, AnalysisSource.LOCAL_MODEL)
            .also { ReplyValidation.validate(it).getOrThrow() }
    }

    override suspend fun draft(request: DraftRequest): Result<String> = guarded {
        ReplyValidation.validate(request.original).getOrThrow()
        val selected = ActionCatalog.action(request.selectedActionId) ?: error("Unknown action.")
        val safe = neutralizedForPrompt(request)
        val input = org.json.JSONObject().put("incoming_message", safe.original.message)
            .put("previous_context", safe.original.history)
            .put("relationship", safe.original.relationship)
            .put("selected_action_id", request.selectedActionId)
            .put("selected_intention", selected.label)
            .put("tone", request.tone.name.lowercase())
            .put("user_instructions", safe.userInstruction).toString()
        val actionRule = when (request.selectedActionId) {
            "reschedule" -> "The user selected RESCHEDULE. Ask the sender for a different interview time. Do not say you are available Friday at 10 or accept that time."
            "clarify", "ask_agenda", "ask_to_clarify" -> "The user selected a question. Ask for details before agreeing to anything."
            "decline" -> "The user selected DECLINE. Politely decline without an invented excuse."
            else -> "Follow the selected action exactly."
        }
        val raw = generate(prompt("generate_v2.txt") + "\n" + actionRule, input, 128).trim()
        val draft = if (raw.startsWith("{") && raw.endsWith("}")) {
            org.json.JSONObject(raw).getString("reply").trim()
        } else raw
        require(!draft.startsWith("{") && !draft.endsWith("}")) { "Malformed draft." }
        if (request.selectedActionId == "reschedule") {
            require(!Regex("(?i)\\b(i(?:'m| am)|we(?:'re| are))\\s+(?:available|free)\\b").containsMatchIn(draft)) {
                "Draft contradicts reschedule action."
            }
        }
        draft.also { ReplyValidation.validateDraft(it).getOrThrow() }
    }

    private fun prompt(name: String): String = app.assets.open("prompts/$name")
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun categoryFor(message: String, purpose: String): String {
        val text = "$message $purpose".lowercase()
        return when {
            "interview" in text -> "interview_invitation"
            "reschedul" in text || "move our meeting" in text -> "reschedule_request"
            "meeting" in text || "call" in text -> "meeting"
            "complain" in text || "late delivery" in text || "issue with" in text -> "complaint"
            "deadline" in text || "update" in text || "finish" in text -> "follow_up"
            "friend" in text || "hang out" in text || "catch up" in text -> "casual"
            else -> "other"
        }
    }
    private fun generate(instruction: String, input: String, maxTokens: Int): String {
        val active = engine ?: throw ModelUnavailableException()
        val started = SystemClock.elapsedRealtime()
        val config = ConversationConfig(
            systemInstruction = Contents.of(instruction),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 42),
            maxOutputToken = maxTokens,
            chatTemplate = QWEN_CHAT_TEMPLATE,
        )
        return active.createConversation(config).use { conversation ->
            conversation.sendMessage(input).toString().also {
                Log.i(TAG, "generation_ms=${SystemClock.elapsedRealtime() - started} chars=${it.length}")
            }
        }
    }

    private inline fun <T> guarded(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (oom: OutOfMemoryError) {
        // Release the native engine, then let the coordinator map the Error to a constant, content-free message.
        engine?.close()
        engine = null
        throw oom
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    companion object {
        private const val TAG = "AkmaInference"
        const val MODEL_NAME = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val MODEL_BYTES = 1597931520L
        const val MODEL_SHA256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"

        fun modelFile(context: Context): File = File(context.filesDir, "models/$MODEL_NAME")

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        // The artifact's embedded template expects a different content representation.
        // This is the Qwen role wrapper validated by the desktop probe, passed through
        // Android's ConversationConfig rather than preformatting the user message.
        private val QWEN_CHAT_TEMPLATE = """
            {%- for message in messages -%}
            {{- '<|im_start|>' + message['role'] + '\n' -}}
            {%- for item in message['content'] -%}
            {%- if item['type'] == 'text' -%}{{- item['text'] -}}{%- endif -%}
            {%- endfor -%}
            {{- '<|im_end|>\n' -}}
            {%- endfor -%}
            {%- if add_generation_prompt -%}{{- '<|im_start|>assistant\n' -}}{%- endif -%}
        """.trimIndent()

    }
}

/** Every copied/pasted field that reaches a prompt passes through here; output sanitising happens in the coordinator. */
internal fun neutralizedForPrompt(request: AnalyzeRequest): AnalyzeRequest = request.copy(
    message = ModelOutputSafety.neutralizePromptInput(request.message),
    history = ModelOutputSafety.neutralizePromptInput(request.history),
    relationship = request.relationship?.let(ModelOutputSafety::neutralizePromptInput),
)

internal fun neutralizedForPrompt(request: DraftRequest): DraftRequest = request.copy(
    original = neutralizedForPrompt(request.original),
    userInstruction = ModelOutputSafety.neutralizePromptInput(request.userInstruction),
)
