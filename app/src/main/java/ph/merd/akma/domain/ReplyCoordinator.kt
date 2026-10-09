package ph.merd.akma.domain

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import ph.merd.akma.safety.SafetyRejection
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
    allowedActionIds: Set<String> = ActionCatalog.actionIds,
    private val initTimeoutMillis: Long = timeoutMillis,
) {
    init { require(timeoutMillis > 0 && initTimeoutMillis > 0) }
    private val allowedActionIds = allowedActionIds.toSet()
    private val mutableState = MutableStateFlow(ReplyState())
    val state = mutableState.asStateFlow()
    private val engineMutex = Mutex()
    private var operation: Job? = null
    private var generation = 0L
    @Volatile private var initialized = false
    @Volatile private var restartRequired = false
    private val resetPending = AtomicBoolean(false)
    private var flight: Flight? = null

    private class Flight {
        val started = AtomicBoolean(false)
        val completed = CompletableDeferred<Result<ReplyState>>()
        lateinit var job: Job
    }
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

    fun initialize() {
        if (state.value.busy) return
        if (restartRequired || (engine as? RuntimeRecovery)?.requiresRestart == true) {
            initialized = false
            restartRequired = true
            showFailure(RuntimeRestartRequiredException())
            return
        }
        process(ReplyPhase.ModelLoading) {
            engine.initialize().getOrThrow()
            state.value.copy(phase = ReplyPhase.Ready, analysis = null, draft = "", notice = null)
        }
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
            val raw = engine.analyze(request).getOrThrow()
            currentCoroutineContext().ensureActive()
            val analysis = ReplyValidation.normalize(raw, allowedActionIds).getOrThrow()
            state.value.copy(phase = ReplyPhase.ChoosingAction, analysis = analysis, draft = "", notice = null)
        }
    }

    /**
     * An action tap requests confirmation. It never starts inference on its own.
     * [instruction] is an optional refine request (e.g. "Make it shorter."); it is staged and shown for
     * confirmation like any other draft request.
     */
    fun draft(actionId: String, tone: ReplyTone, instruction: String = "") {
        if (!initialized || state.value.phase !in setOf(ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied)) return
        val request = DraftRequest(AnalyzeRequest(state.value.message), actionId, tone, userInstruction = instruction)
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
            val raw = engine.draft(request).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (raw.length > ReplyValidation.MAX_TEXT_LENGTH) {
                throw UnsafeModelOutputException(SafetyRejection.TOO_LONG)
            }
            val draft = when (val safe = ModelOutputSafety.sanitizeDraft(raw, ReplyValidation.MAX_TEXT_LENGTH)) {
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
        stopFlight(invalidate = state.value.phase == ReplyPhase.ModelLoading)
        operation?.cancel()
        operation = null
        mutableState.value = state.value.copy(phase = if (initialized) recoveryPhase else ReplyPhase.ModelUnavailable, notice = "Cancelled.")
    }

    fun recover() {
        if (state.value.phase == ReplyPhase.Error) {
            mutableState.value = state.value.copy(phase = if (initialized) recoveryPhase else ReplyPhase.ModelUnavailable, notice = null)
        }
    }

    private fun readyPhase() = if (initialized) ReplyPhase.Ready else ReplyPhase.ModelUnavailable

    private fun validate(result: Result<Unit>): Boolean {
        if (result.isSuccess) return true
        if (state.value.phase != ReplyPhase.Error) recoveryPhase = state.value.phase
        mutableState.value = state.value.copy(phase = ReplyPhase.Error, pendingConfirmation = null, notice = result.exceptionOrNull()?.message)
        return false
    }

    private fun stopFlight(invalidate: Boolean) {
        val active = flight ?: return
        if (invalidate && active.started.get() && engine is RuntimeRecovery) {
            initialized = false
            resetPending.set(true)
        }
        active.job.cancel()
    }

    private fun failed(error: Throwable, phase: ReplyPhase, started: Boolean): Result<ReplyState> {
        if (error is RuntimeRestartRequiredException) restartRequired = true
        if (error is LinkageError || error is OutOfMemoryError || error is ModelUnavailableException || error is RuntimeRestartRequiredException ||
            phase == ReplyPhase.ModelLoading) {
            initialized = false
            if (started && engine is RuntimeRecovery) resetPending.set(true)
        }
        return Result.failure(error)
    }

    private suspend fun resetRuntimeIfNeeded() {
        if (restartRequired) throw RuntimeRestartRequiredException()
        if (!resetPending.getAndSet(false)) return
        val recovery = engine as? RuntimeRecovery ?: return
        try {
            recovery.invalidateRuntime().getOrThrow()
        } catch (cancel: CancellationException) {
            resetPending.set(true)
            throw cancel
        } catch (_: LinkageError) {
            restartRequired = true
            throw RuntimeRestartRequiredException()
        } catch (_: OutOfMemoryError) {
            restartRequired = true
            throw RuntimeRestartRequiredException()
        } catch (_: Exception) {
            restartRequired = true
            throw RuntimeRestartRequiredException()
        }
    }

    private fun showFailure(error: Throwable) {
        if (!initialized) recoveryPhase = ReplyPhase.ModelUnavailable
        mutableState.value = state.value.copy(
            phase = if (error is ModelUnavailableException) ReplyPhase.ModelUnavailable else ReplyPhase.Error,
            analysis = if (initialized) state.value.analysis else null,
            draft = "",
            pendingConfirmation = null,
            notice = if (error is RuntimeRestartRequiredException) {
                "Local AI cleanup failed. Restart Akma before retrying."
            } else if (error is LocalModelProvisioningException) {
                error.userNotice
            } else ModelOutputSafety.safeFailure(error).userMessage,
        )
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
        val active = Flight()
        flight = active
        // Sibling jobs let the UI timeout without joining a non-cooperative native worker.
        // The worker retains the mutex through native completion and cleanup.
        active.job = scope.launch(worker, start = CoroutineStart.LAZY) {
            var result: Result<ReplyState>? = null
            try {
                engineMutex.withLock {
                    try {
                        resetRuntimeIfNeeded()
                        currentCoroutineContext().ensureActive()
                        if (phase != ReplyPhase.ModelLoading && !initialized) throw ModelUnavailableException()
                        active.started.set(true)
                        result = Result.success(work().also { currentCoroutineContext().ensureActive() })
                    } catch (cancel: CancellationException) {
                        if (phase == ReplyPhase.ModelLoading && active.started.get() && engine is RuntimeRecovery) {
                            initialized = false
                            resetPending.set(true)
                        }
                        throw cancel
                    } catch (error: LinkageError) {
                        result = failed(error, phase, active.started.get())
                    } catch (error: OutOfMemoryError) {
                        result = failed(error, phase, active.started.get())
                    } catch (error: Exception) {
                        result = failed(error, phase, active.started.get())
                    } finally {
                        if (resetPending.get()) {
                            withContext(NonCancellable) {
                                try {
                                    resetRuntimeIfNeeded()
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (error: Exception) {
                                    result = Result.failure(error)
                                }
                            }
                        }
                    }
                }
                result?.let { active.completed.complete(it) }
            } catch (cancel: CancellationException) {
                active.completed.cancel(cancel)
                throw cancel
            } finally {
                if ((engine as? RuntimeRecovery)?.requiresRestart == true) {
                    initialized = false
                    restartRequired = true
                }
                // A late failure may invalidate the shared engine, even though its old result is discarded.
                scope.launch {
                    if (!initialized && generation != currentGeneration && !state.value.busy &&
                        state.value.phase !in setOf(ReplyPhase.Error, ReplyPhase.ModelUnavailable)) {
                        showFailure(if (restartRequired) RuntimeRestartRequiredException()
                            else result?.exceptionOrNull() ?: ModelUnavailableException())
                    }
                }
            }
            // Other Error subclasses deliberately propagate to the owner's scope.
        }
        operation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val deadline = if (phase == ReplyPhase.ModelLoading) initTimeoutMillis else timeoutMillis
                val result = withTimeout(deadline) { active.completed.await().getOrThrow() }
                if (generation == currentGeneration) {
                    if (phase == ReplyPhase.ModelLoading) initialized = true
                    mutableState.value = result
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == currentGeneration) {
                    stopFlight(invalidate = true)
                    if (!initialized) recoveryPhase = ReplyPhase.ModelUnavailable
                    mutableState.value = state.value.copy(
                        phase = ReplyPhase.Error,
                        analysis = if (initialized) state.value.analysis else null,
                        draft = "",
                        pendingConfirmation = null,
                        notice = "Local processing timed out. Retry or cancel.",
                    )
                }
            } catch (cancel: CancellationException) {
                if (generation == currentGeneration) {
                    mutableState.value = state.value.copy(phase = if (initialized) recoveryPhase else ReplyPhase.ModelUnavailable,
                        notice = "Cancelled.")
                }
                throw cancel
            } catch (error: LinkageError) {
                if (generation == currentGeneration) showFailure(error)
            } catch (error: OutOfMemoryError) {
                if (generation == currentGeneration) showFailure(error)
            } catch (error: Exception) {
                if (generation == currentGeneration) showFailure(error)
            } finally {
                // Completed UI waits must not retain an old message/draft through the deferred result.
                // An unfinished native worker remains owned by the scope and its mutex, not this UI reference.
                if (flight === active) flight = null
                if (generation == currentGeneration) operation = null
            }
        }.also { it.start() }
        active.job.start()
    }
}
