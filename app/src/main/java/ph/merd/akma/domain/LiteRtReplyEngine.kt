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
        val input = ph.merd.akma.ai.protocol.AkmaProtocol.compileAnalysisPrompt(request)
        val raw = generate(ph.merd.akma.ai.protocol.AkmaProtocol.SYSTEM_PROMPT, input, 192)
        ph.merd.akma.ai.protocol.AkmaProtocol.decodeAnalysis(raw).getOrThrow().also { 
            ReplyValidation.validate(it).getOrThrow() 
        }
    }

    override suspend fun draft(request: DraftRequest): Result<String> = guarded {
        ReplyValidation.validate(request.original).getOrThrow()
        val input = ph.merd.akma.ai.protocol.AkmaProtocol.compileDraftPrompt(request)
        val raw = generate(ph.merd.akma.ai.protocol.AkmaProtocol.SYSTEM_PROMPT, input, 128)
        
        val draft = ph.merd.akma.ai.protocol.AkmaProtocol.validateParsedReply(raw).getOrThrow()
        if (request.selectedActionId == "reschedule") {
            require(!Regex("(?i)\\b(i(?:'m| am)|we(?:'re| are))\\s+(?:available|free)\\b").containsMatchIn(draft)) {
                "Draft contradicts reschedule action."
            }
        }
        draft.also { ReplyValidation.validateDraft(it).getOrThrow() }
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
    } catch (_: OutOfMemoryError) {
        engine?.close()
        engine = null
        Result.failure(IllegalStateException("Insufficient memory for local model."))
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
