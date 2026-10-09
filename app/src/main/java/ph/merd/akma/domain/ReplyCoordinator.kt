package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class ReplyPhase {
    ModelUnavailable, ModelLoading, Ready, Analyzing, ChoosingAction, Drafting, Editing, Copied, Error,
}

data class ReplyState(
    val phase: ReplyPhase = ReplyPhase.ModelUnavailable,
    val message: String = "",
    val analysis: AnalysisResult? = null,
    val draft: String = "",
    val notice: String? = null,
) {
    val busy: Boolean get() = phase in setOf(ReplyPhase.ModelLoading, ReplyPhase.Analyzing, ReplyPhase.Drafting)
    val canCopy: Boolean get() = phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied) && draft.isNotBlank()
}

/** Call UI methods on the main thread. One process owns one coordinator and one engine. */
class ReplyCoordinator(
    private val engine: LocalReplyEngine,
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = 60_000,
    private val allowedActionIds: Set<String> = setOf("reschedule", "acknowledge", "clarify", "accept", "decline"),
) {
    private val mutableState = MutableStateFlow(ReplyState())
    val state = mutableState.asStateFlow()
    private val engineMutex = Mutex()
    private var operation: Job? = null
    private var generation = 0L
    private var initialized = false
    private var recoveryPhase = ReplyPhase.ModelUnavailable

    fun setMessage(message: String) {
        if (state.value.busy) return
        if (message.length > ReplyValidation.MAX_TEXT_LENGTH) {
            mutableState.value = state.value.copy(notice = "Message exceeds 1,500 characters.")
            return
        }
        mutableState.value = ReplyState(phase = readyPhase(), message = message)
    }

    fun initialize() = process(ReplyPhase.ModelLoading) {
        engine.initialize().getOrThrow()
        state.value.copy(phase = ReplyPhase.Ready, analysis = null, draft = "", notice = null)
    }

    fun analyze() {
        if (state.value.busy) return
        if (!initialized) {
            mutableState.value = state.value.copy(phase = ReplyPhase.ModelUnavailable, notice = "No local model is configured.")
            return
        }
        val request = AnalyzeRequest(state.value.message)
        if (!validate(ReplyValidation.validate(request))) return
        process(ReplyPhase.Analyzing) {
            val analysis = engine.analyze(request).getOrThrow()
            ReplyValidation.validate(analysis, allowedActionIds).getOrThrow()
            state.value.copy(phase = ReplyPhase.ChoosingAction, analysis = analysis, draft = "", notice = null)
        }
    }

    fun draft(actionId: String, tone: ReplyTone) {
        if (!initialized || state.value.phase !in setOf(ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied)) return
        val request = DraftRequest(AnalyzeRequest(state.value.message), actionId, tone)
        if (!validate(ReplyValidation.validate(request, state.value.analysis!!.actions))) return
        process(ReplyPhase.Drafting) {
            val draft = engine.draft(request).getOrThrow()
            ReplyValidation.validateDraft(draft).getOrThrow()
            state.value.copy(phase = ReplyPhase.Editing, draft = draft, notice = null)
        }
    }

    fun editDraft(draft: String) {
        if (state.value.phase !in setOf(ReplyPhase.Editing, ReplyPhase.Copied)) return
        if (draft.length > ReplyValidation.MAX_TEXT_LENGTH) {
            mutableState.value = state.value.copy(notice = "Draft exceeds 1,500 characters.")
            return
        }
        mutableState.value = state.value.copy(phase = ReplyPhase.Editing, draft = draft, notice = null)
    }

    fun copied() {
        if (state.value.canCopy) mutableState.value = state.value.copy(phase = ReplyPhase.Copied, notice = "Copied. Paste and send manually.")
    }

    fun cancel() {
        if (!state.value.busy) return
        generation++
        operation?.cancel()
        operation = null
        mutableState.value = state.value.copy(phase = recoveryPhase, notice = "Cancelled.")
    }

    fun recover() {
        if (state.value.phase == ReplyPhase.Error) {
            mutableState.value = state.value.copy(phase = recoveryPhase, notice = null)
        }
    }

    private fun readyPhase() = if (initialized) ReplyPhase.Ready else ReplyPhase.ModelUnavailable

    private fun validate(result: Result<Unit>): Boolean {
        if (result.isSuccess) return true
        if (state.value.phase != ReplyPhase.Error) recoveryPhase = state.value.phase
        mutableState.value = state.value.copy(phase = ReplyPhase.Error, notice = result.exceptionOrNull()?.message)
        return false
    }

    private fun process(phase: ReplyPhase, work: suspend () -> ReplyState) {
        if (state.value.busy) return
        if (state.value.phase != ReplyPhase.Error) recoveryPhase = state.value.phase
        val currentGeneration = ++generation
        mutableState.value = state.value.copy(phase = phase, notice = null)
        operation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withTimeout(timeoutMillis) {
                    // Serialize even if a cancelled native call takes time to return.
                    engineMutex.withLock { withContext(worker) { work() } }
                }
                if (generation == currentGeneration) {
                    if (phase == ReplyPhase.ModelLoading) initialized = true
                    mutableState.value = result
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == currentGeneration) {
                    mutableState.value = state.value.copy(phase = ReplyPhase.Error, notice = "Local processing timed out. Retry or cancel.")
                }
            } catch (exception: CancellationException) {
                if (generation == currentGeneration) {
                    mutableState.value = state.value.copy(phase = recoveryPhase, notice = "Cancelled.")
                }
                throw exception
            } catch (exception: Exception) {
                if (generation == currentGeneration) {
                    // Engine exception strings can contain private prompts. Never render or log them.
                    val unavailable = exception is ModelUnavailableException
                    if (unavailable) initialized = false
                    mutableState.value = state.value.copy(
                        phase = if (unavailable) ReplyPhase.ModelUnavailable else ReplyPhase.Error,
                        notice = if (unavailable) "No local model is configured." else "Local processing failed. Check the model and retry.",
                    )
                }
            }
        }.also { it.start() }
    }
}
