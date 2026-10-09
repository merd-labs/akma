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
import ph.merd.akma.safety.ModelOutputSafety
import ph.merd.akma.safety.SafetyResult
import ph.merd.akma.safety.UnsafeModelOutputException

enum class ReplyPhase {
    ModelUnavailable, ModelLoading, Ready, Analyzing, ChoosingAction, Drafting, Editing, Copied, Error,
}

data class DraftConfirmation(
    val id: Long,
    val action: SuggestedAction,
    val request: DraftRequest,
)

data class ReplyState(
    val phase: ReplyPhase = ReplyPhase.ModelUnavailable,
    val message: String = "",
    val analysis: AnalysisResult? = null,
    val draft: String = "",
    val notice: String? = null,
    val pendingConfirmation: DraftConfirmation? = null,
) {
    val busy: Boolean get() = phase in setOf(ReplyPhase.ModelLoading, ReplyPhase.Analyzing, ReplyPhase.Drafting)
    val canCopy: Boolean get() = pendingConfirmation == null && phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied) && draft.isNotBlank()
}

/** Call UI methods on the main thread. One process owns one coordinator and one engine. */
class ReplyCoordinator(
    private val engine: LocalReplyEngine,
    private val scope: CoroutineScope,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Long = 60_000,
    /** Model load (copy, hash, cold native load) is far slower than one generation; null keeps [timeoutMillis]. */
    private val initTimeoutMillis: Long? = null,
    allowedActionIds: Set<String> = ActionCatalog.actionIds,
) {
    private val allowedActionIds = allowedActionIds.toSet()
    private val mutableState = MutableStateFlow(ReplyState())
    val state = mutableState.asStateFlow()
    private val engineMutex = Mutex()
    private var operation: Job? = null
    private var generation = 0L
    private var initialized = false
    private var confirmationId = 0L
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
            val analysis = ReplyValidation.normalize(engine.analyze(request).getOrThrow(), allowedActionIds).getOrThrow()
            state.value.copy(phase = ReplyPhase.ChoosingAction, analysis = analysis, draft = "", notice = null)
        }
    }

    /** An action tap requests confirmation. It never starts inference on its own. */
    fun draft(actionId: String, tone: ReplyTone) {
        if (!initialized || state.value.phase !in setOf(ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied)) return
        val request = DraftRequest(AnalyzeRequest(state.value.message), actionId, tone)
        val analysis = state.value.analysis ?: return
        if (!validate(ReplyValidation.validate(request, analysis.actions))) return
        if (state.value.pendingConfirmation?.request == request) return
        val action = analysis.actions.single { it.id == actionId }
        mutableState.value = state.value.copy(
            phase = ReplyPhase.ChoosingAction,
            draft = "",
            pendingConfirmation = DraftConfirmation(++confirmationId, action, request),
            notice = "Confirm your selected action before drafting.",
        )
    }

    /** Invoke only from a separate human Confirm control, using the ID the control displayed. */
    fun confirmDraft(confirmationId: Long) {
        if (state.value.busy) return
        val pending = state.value.pendingConfirmation
        if (!initialized || state.value.phase != ReplyPhase.ChoosingAction || pending == null || pending.id != confirmationId) {
            mutableState.value = state.value.copy(notice = "Confirmation is no longer available. Select an action again.")
            return
        }
        val analysis = state.value.analysis ?: return
        if (!validate(ReplyValidation.validate(pending.request, analysis.actions))) return
        if (pending.request.original != AnalyzeRequest(state.value.message)) {
            mutableState.value = state.value.copy(pendingConfirmation = null, notice = "Message changed. Select an action again.")
            return
        }
        val request = pending.request
        // Consume before scheduling. A duplicate confirmation cannot reuse this request.
        mutableState.value = state.value.copy(pendingConfirmation = null)
        process(ReplyPhase.Drafting) {
            val draft = when (val safe = ModelOutputSafety.sanitizeDraft(engine.draft(request).getOrThrow(), ReplyValidation.MAX_TEXT_LENGTH)) {
                is SafetyResult.Accepted -> safe.text
                is SafetyResult.Rejected -> throw UnsafeModelOutputException(safe.reason)
            }
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
        if (state.value.pendingConfirmation != null) {
            mutableState.value = state.value.copy(pendingConfirmation = null, notice = "Cancelled.")
            return
        }
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
        mutableState.value = state.value.copy(phase = ReplyPhase.Error, pendingConfirmation = null, notice = result.exceptionOrNull()?.message)
        return false
    }

    private fun process(phase: ReplyPhase, work: suspend () -> ReplyState) {
        if (state.value.busy) return
        val clearOutput = phase == ReplyPhase.ModelLoading || phase == ReplyPhase.Analyzing
        when (phase) {
            ReplyPhase.ModelLoading -> {
                initialized = false
                recoveryPhase = ReplyPhase.ModelUnavailable
            }
            ReplyPhase.Analyzing -> recoveryPhase = readyPhase()
            else -> if (state.value.phase != ReplyPhase.Error) recoveryPhase = state.value.phase
        }
        val currentGeneration = ++generation
        mutableState.value = state.value.copy(
            phase = phase,
            analysis = if (clearOutput) null else state.value.analysis,
            draft = if (clearOutput) "" else state.value.draft,
            pendingConfirmation = null,
            notice = null,
        )
        operation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withTimeout(if (phase == ReplyPhase.ModelLoading) initTimeoutMillis ?: timeoutMillis else timeoutMillis) {
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
            } catch (exception: Throwable) {
                // Throwable, not Exception: native runtime bindings fail with Error subclasses (UnsatisfiedLinkError,
                // NoClassDefFoundError, OutOfMemoryError) that would otherwise kill the process and leave the UI busy.
                if (generation == currentGeneration) {
                    // Engine exception strings can contain private prompts. Never render or log them.
                    val unavailable = exception is ModelUnavailableException
                    if (unavailable) initialized = false
                    mutableState.value = state.value.copy(
                        phase = if (unavailable) ReplyPhase.ModelUnavailable else ReplyPhase.Error,
                        notice = ModelOutputSafety.safeFailure(exception).userMessage,
                    )
                }
            }
        }.also { it.start() }
    }
}
