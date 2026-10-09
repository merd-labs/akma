package ph.merd.akma.domain

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ph.merd.akma.provisioning.BundledModelProvisioner
import ph.merd.akma.provisioning.BundledQwenArtifact
import ph.merd.akma.provisioning.ModelProvisionResult
import ph.merd.akma.safety.ModelOutputSafety

/** One CPU engine. The coordinator serializes calls and owns the timeout. */
class LiteRtReplyEngine(context: Context) : LocalReplyEngine, RuntimeRecovery {
    private val app = context.applicationContext
    private val runtime = NativeHandleSlot<Engine>()
    override val requiresRestart: Boolean get() = runtime.quarantined

    override suspend fun invalidateRuntime(): Result<Unit> = try {
        runtime.invalidate()
    } finally {
        // A retry after native failure must fully verify bytes rather than reuse the old receipt.
        provisioner.invalidateVerification()
    }
    private val provisioner = BundledModelProvisioner(app)

    override suspend fun initialize(): Result<Unit> = guarded {
        runtime.invalidate().getOrThrow()
        val model = when (val result = provisioner.ensureBundledModel(BundledQwenArtifact.spec)) {
            is ModelProvisionResult.Verified -> result.model.file
            is ModelProvisionResult.Failure -> throw LocalModelProvisioningException(result.reason)
        }
        currentCoroutineContext().ensureActive()
        val started = SystemClock.elapsedRealtime()
        val loaded = Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(), cacheDir = app.cacheDir.absolutePath))
        runtime.install(loaded)
        try {
            currentCoroutineContext().ensureActive()
            loaded.initialize()
            Log.i(TAG, "model_initialized_ms=${SystemClock.elapsedRealtime() - started}")
        } catch (failure: Throwable) {
            // Cleanup only; this catch never converts fatal VM errors into recoverable results.
            withContext(NonCancellable) { invalidateRuntime() }
            throw failure
        }
    }

    override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = guarded {
        ReplyValidation.validate(request).getOrThrow()
        val safe = neutralizedForPrompt(request)
        val input = org.json.JSONObject().put("incoming_message", safe.message)
            .put("previous_context", safe.history).put("relationship", safe.relationship).toString()
        val raw = generate(prompt("analyze_v2.txt"), input, 192)
        val purpose = LocalModelOutput.purpose(raw)
        val category = categoryFor(request.message)
        val actions = ActionCatalog.actionsFor(category).take(ReplyValidation.MAX_ACTIONS)
        AnalysisResult(category, "Untrusted model summary: $purpose", true, actions, AnalysisSource.DETERMINISTIC)
            .also { ReplyValidation.validate(it).getOrThrow() }
    }

    override suspend fun draft(request: DraftRequest): Result<String> = guarded {
        ReplyValidation.validate(request.original).getOrThrow()
        val selected = ActionCatalog.action(request.selectedActionId) ?: error("Unknown action.")
        ReplyValidation.validate(request, listOf(selected)).getOrThrow()
        val safe = neutralizedForPrompt(request)
        val input = org.json.JSONObject().put("incoming_message", safe.original.message)
            .put("previous_context", safe.original.history)
            .put("relationship", safe.original.relationship)
            .put("selected_action_id", request.selectedActionId)
            .put("selected_intention", selected.label)
            .put("tone", request.tone.name.lowercase(Locale.ROOT))
            .put("user_instructions", safe.userInstruction).toString()
        val actionRule = when (request.selectedActionId) {
            "reschedule" -> "The user selected RESCHEDULE. Ask the sender for a different interview time. Do not say you are available Friday at 10 or accept that time."
            "clarify", "ask_agenda", "ask_to_clarify" -> "The user selected a question. Ask for details before agreeing to anything."
            "decline" -> "The user selected DECLINE. Politely decline without an invented excuse."
            else -> "Follow the selected action exactly."
        }
        val raw = generate(prompt("generate_v2.txt") + "\n" + actionRule, input, 128)
        val draft = LocalModelOutput.draft(raw)
        if (request.selectedActionId == "reschedule") {
            require(!Regex("(?i)\\b(i(?:'m| am)|we(?:'re| are))\\s+(?:available|free)\\b").containsMatchIn(draft)) {
                "Draft contradicts reschedule action."
            }
        }
        draft.also { ReplyValidation.validateDraft(it).getOrThrow() }
    }

    private fun prompt(name: String): String = app.assets.open("prompts/$name")
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun categoryFor(message: String): String {
        val text = message.lowercase(Locale.ROOT)
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
        val active = runtime.current ?: throw ModelUnavailableException()
        val started = SystemClock.elapsedRealtime()
        val firstTextMillis = AtomicLong(-1)
        val config = ConversationConfig(
            systemInstruction = Contents.of(instruction),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 42),
            maxOutputToken = maxTokens,
            chatTemplate = QWEN_CHAT_TEMPLATE,
        )
        currentCoroutineContext().ensureActive()
        val conversation = active.createConversation(config)
        val handle = NativeHandleSlot<Conversation>().also { it.install(conversation) }
        val operation = NativeReplyOperation(ModelOutputSafety.MAX_RAW_CHARS, onCancellationFailure = { runtime.quarantine() })
        var primaryFailure = false
        try {
            return operation.await(
                start = { callbacks ->
                    conversation.sendMessageAsync(input, object : MessageCallback {
                        override fun onMessage(message: Message) {
                            try {
                                val text = message.toString()
                                if (text.isNotEmpty()) firstTextMillis.compareAndSet(-1, SystemClock.elapsedRealtime() - started)
                                callbacks.onText(text)
                            } catch (failure: Throwable) {
                                // Transfer the original failure to the coroutine owner; this is not a terminal event.
                                callbacks.onFailure(failure)
                            }
                        }
                        override fun onDone() = callbacks.onComplete()
                        override fun onError(throwable: Throwable) = callbacks.onComplete(throwable)
                    })
                },
                cancel = { conversation.cancelProcess() },
            ).also {
                Log.i(TAG, "generation_ms=${SystemClock.elapsedRealtime() - started} first_text_ms=${firstTextMillis.get()} chars=${it.length}")
            }
        } catch (failure: Throwable) {
            // Preserve cancellation and every original failure; classification happens in guarded/the coordinator.
            primaryFailure = true
            throw failure
        } finally {
            if (operation.started && !operation.completed) {
                // Startup or cancellation failed before a terminal callback. Closing could race native work.
                handle.quarantine()
                runtime.quarantine()
            } else {
                try {
                    val cleanup = handle.invalidate()
                    if (cleanup.isFailure && !primaryFailure) cleanup.getOrThrow()
                } finally {
                    if (handle.quarantined) runtime.quarantine()
                }
            }
        }
    }

    private suspend inline fun <T> guarded(block: () -> T): Result<T> = try {
        currentCoroutineContext().ensureActive()
        if (runtime.quarantined) throw RuntimeRestartRequiredException()
        Result.success(block())
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (failure: LinkageError) {
        withContext(NonCancellable) { invalidateRuntime() }
        Result.failure(failure)
    } catch (failure: OutOfMemoryError) {
        withContext(NonCancellable) { invalidateRuntime() }
        Result.failure(failure)
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    companion object {
        private const val TAG = "AkmaInference"
        const val MODEL_NAME = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val MODEL_BYTES = 1597931520L
        const val MODEL_SHA256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"

        fun modelFile(context: Context): File = File(context.filesDir, "models/$MODEL_NAME")

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
