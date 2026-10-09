package ph.merd.akma.domain

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.gson.JsonParser
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ph.merd.akma.provisioning.BundledModelProvisioner
import ph.merd.akma.provisioning.BundledQwenArtifact
import ph.merd.akma.provisioning.ModelProvisionResult
import ph.merd.akma.provisioning.ProvisionFailure
import ph.merd.akma.safety.ModelOutputSafety
import ph.merd.akma.safety.SafetyResult
import ph.merd.akma.safety.UnsafeModelOutputException

/** One CPU engine. The coordinator serializes calls and owns the timeout. */
class LiteRtReplyEngine(context: Context) : LocalReplyEngine {
    private val app = context.applicationContext
    private var engine: Engine? = null
    private val provisioner = BundledModelProvisioner(app)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var nativeBusy = false

    override suspend fun initialize(): Result<Unit> = guarded {
        check(!nativeBusy) { "Local inference is still stopping." }
        engine?.close()
        engine = null
        val model = when (val result = provisioner.ensureBundledModel(BundledQwenArtifact.spec)) {
            is ModelProvisionResult.Verified -> result.model.file
            is ModelProvisionResult.Failure -> {
                if (result.reason == ProvisionFailure.SOURCE_NOT_FOUND || result.reason == ProvisionFailure.MODEL_MISSING) {
                    throw ModelUnavailableException()
                }
                error("Model provisioning failed: ${result.reason}")
            }
        }
        val started = SystemClock.elapsedRealtime()
        val loaded = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), cacheDir = app.cacheDir.absolutePath))
        try {
            loaded.initialize()
            engine = loaded
            Log.i(TAG, "model_initialized_ms=${SystemClock.elapsedRealtime() - started}")
        } catch (failure: Throwable) {
            runCatching { loaded.close() }
            provisioner.invalidateVerification()
            throw failure
        }
    }

    override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = guarded {
        ReplyValidation.validate(request).getOrThrow()
        val safeMessage = ModelOutputSafety.neutralizePromptInput(request.message)
        val input = org.json.JSONObject().put("incoming_message", safeMessage)
            .put("previous_context", ModelOutputSafety.neutralizePromptInput(request.history))
            .put("relationship", request.relationship?.let(ModelOutputSafety::neutralizePromptInput)).toString()
        val raw = generate(prompt("analyze_v2.txt"), input, 192)
        val clean = raw.trim()
        require(clean.startsWith("{") && clean.endsWith("}") && clean.length <= 20_000) { "Malformed analysis." }
        val parsed = JsonParser.parseString(clean)
        require(parsed.isJsonObject) { "Malformed analysis." }
        val purposeNode = parsed.asJsonObject.get("message_purpose")
        require(purposeNode?.isJsonPrimitive == true && purposeNode.asJsonPrimitive.isString) { "Malformed analysis." }
        val purpose = when (val safe = ModelOutputSafety.sanitizeDisplayText(purposeNode.asString, ReplyValidation.MAX_SUMMARY_CODE_POINTS)) {
            is SafetyResult.Accepted -> safe.text
            is SafetyResult.Rejected -> throw UnsafeModelOutputException(safe.reason)
        }
        val category = categoryFor(safeMessage, purpose)
        val actions = ActionCatalog.actionsFor(category).take(ReplyValidation.MAX_ACTIONS)
        AnalysisResult(category, purpose, true, actions, AnalysisSource.HYBRID)
            .also { ReplyValidation.validate(it).getOrThrow() }
    }

    override suspend fun draft(request: DraftRequest): Result<String> = guarded {
        ReplyValidation.validate(request.original).getOrThrow()
        val selected = ActionCatalog.action(request.selectedActionId) ?: error("Unknown action.")
        ReplyValidation.validate(request, listOf(selected)).getOrThrow()
        val input = org.json.JSONObject().put("incoming_message", ModelOutputSafety.neutralizePromptInput(request.original.message))
            .put("previous_context", ModelOutputSafety.neutralizePromptInput(request.original.history))
            .put("relationship", request.original.relationship?.let(ModelOutputSafety::neutralizePromptInput))
            .put("selected_action_id", request.selectedActionId)
            .put("selected_intention", selected.label)
            .put("tone", request.tone.name.lowercase())
            .put("user_instructions", ModelOutputSafety.neutralizePromptInput(request.userInstruction)).toString()
        val actionRule = when (request.selectedActionId) {
            "reschedule" -> "The user selected RESCHEDULE. Ask the sender for a different interview time. Do not say you are available Friday at 10 or accept that time."
            "clarify", "ask_agenda", "ask_to_clarify" -> "The user selected a question. Ask for details before agreeing to anything."
            "decline" -> "The user selected DECLINE. Politely decline without an invented excuse."
            else -> "Follow the selected action exactly."
        }
        val raw = generate(prompt("generate_v2.txt") + "\n" + actionRule, input, 128)
        val clean = when (val safe = ModelOutputSafety.sanitizeDraft(raw, ModelOutputSafety.MAX_RAW_CHARS)) {
            is SafetyResult.Accepted -> safe.text
            is SafetyResult.Rejected -> throw UnsafeModelOutputException(safe.reason)
        }
        val draft = if (clean.startsWith("{")) {
            val parsed = JsonParser.parseString(clean)
            require(parsed.isJsonObject) { "Malformed draft." }
            val reply = parsed.asJsonObject.get("reply")
            require(reply?.isJsonPrimitive == true && reply.asJsonPrimitive.isString) { "Malformed draft." }
            reply.asString.trim()
        } else clean
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
        val text = "$message $purpose".lowercase(Locale.ROOT)
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
    private suspend fun generate(instruction: String, input: String, maxTokens: Int): String {
        val active = engine ?: throw ModelUnavailableException()
        check(!nativeBusy) { "Local inference is still stopping." }
        val started = SystemClock.elapsedRealtime()
        val config = ConversationConfig(
            systemInstruction = Contents.of(instruction),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 42),
            maxOutputToken = maxTokens,
            chatTemplate = QWEN_CHAT_TEMPLATE,
        )
        val conversation = active.createConversation(config)
        nativeBusy = true
        val completion = CompletableDeferred<Result<String>>()
        val terminal = AtomicBoolean(false)
        val firstToken = AtomicLong(-1)
        val callbackFailure = AtomicReference<Throwable?>(null)
        val output = StringBuilder()
        fun finish(result: Result<String>) {
            if (!terminal.compareAndSet(false, true)) return
            cleanupScope.launch {
                try {
                    conversation.close()
                } catch (_: Throwable) {
                    engine = null
                } finally {
                    nativeBusy = false
                    completion.complete(result)
                }
            }
        }
        try {
            conversation.sendMessageAsync(input, object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (terminal.get()) return
                    try {
                        firstToken.compareAndSet(-1, SystemClock.elapsedRealtime() - started)
                        val length = synchronized(output) { output.append(message.toString()).length }
                        if (length > ModelOutputSafety.MAX_RAW_CHARS && callbackFailure.compareAndSet(null, IllegalStateException("Local model output is too long."))) {
                            conversation.cancelProcess()
                        }
                    } catch (failure: Throwable) {
                        callbackFailure.compareAndSet(null, failure)
                        runCatching { conversation.cancelProcess() }
                    }
                }

                override fun onDone() {
                    val result = synchronized(output) { output.toString() }
                    Log.i(TAG, "generation_ms=${SystemClock.elapsedRealtime() - started} first_token_ms=${firstToken.get()} chars=${result.length}")
                    val failure = callbackFailure.get()
                    finish(if (failure == null) Result.success(result) else Result.failure(failure))
                }

                override fun onError(throwable: Throwable) {
                    finish(Result.failure(callbackFailure.get() ?: throwable))
                }
            })
            return completion.await().getOrThrow()
        } catch (cancel: CancellationException) {
            if (!terminal.get()) runCatching { conversation.cancelProcess() }
            throw cancel
        } catch (failure: Throwable) {
            finish(Result.failure(failure))
            throw failure
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: OutOfMemoryError) {
        if (!nativeBusy) runCatching { engine?.close() }
        engine = null
        Result.failure(failure)
    } catch (failure: LinkageError) {
        if (!nativeBusy) runCatching { engine?.close() }
        engine = null
        Result.failure(failure)
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    companion object {
        private const val TAG = "AkmaInference"
        const val MODEL_NAME = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val MODEL_BYTES = 1597931520L
        const val MODEL_SHA256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"
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
