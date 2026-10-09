package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

/** Synthetic boundary faults and lifecycle scheduling, not device memory pressure or model semantics. */
@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeResilienceTest {
    private class Engine : LocalReplyEngine, RuntimeRecovery {
        override var requiresRestart = false
        var failure: Throwable? = null
        var failurePhase = ReplyPhase.ModelLoading
        var returnFailure = false
        var delayedPhase: ReplyPhase? = null
        var delayMillis = 0L
        var nonCooperative = false
        var cleanupFailure: Throwable? = null
        var initializations = 0
        var analyses = 0
        var drafts = 0
        var cleanups = 0
        var active = 0
        var peak = 0
        var usable = false
        var draftText = "Synthetic editable reply"

        private suspend fun <T> call(phase: ReplyPhase, value: T): Result<T> {
            val fault = failure.takeIf { failurePhase == phase }
            val asResult = returnFailure
            val wait = if (delayedPhase == phase) delayMillis else 0L
            peak = maxOf(peak, ++active)
            try {
                if (nonCooperative) withContext(NonCancellable) { delay(wait) } else delay(wait)
                if (fault != null) {
                    if (asResult) return Result.failure(fault)
                    throw fault
                }
                return Result.success(value)
            } finally { active-- }
        }

        override suspend fun initialize(): Result<Unit> {
            initializations++
            return call(ReplyPhase.ModelLoading, Unit).also { if (it.isSuccess) usable = true }
        }
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> {
            analyses++
            return call(ReplyPhase.Analyzing, AnalysisResult("invitation", "Synthetic summary", false,
                listOf(SuggestedAction("accept", "Decline"), SuggestedAction("decline", "Accept")), AnalysisSource.LOCAL_MODEL))
        }
        override suspend fun draft(request: DraftRequest): Result<String> {
            drafts++
            return call(ReplyPhase.Drafting, draftText)
        }
        override suspend fun invalidateRuntime(): Result<Unit> {
            assertEquals("Cleanup must not overlap inference", 0, active)
            cleanups++
            usable = false
            return cleanupFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }

    private fun TestScope.coordinator(engine: Engine, timeout: Long = 60_000) =
        ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler), timeoutMillis = timeout)

    private fun TestScope.choosing(engine: Engine, timeout: Long = 60_000): ReplyCoordinator {
        val replies = coordinator(engine, timeout)
        replies.initialize(); runCurrent()
        replies.setMessage("Synthetic original source")
        replies.analyze(); runCurrent()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        return replies
    }

    private fun confirm(replies: ReplyCoordinator): Long {
        replies.draft("accept", ReplyTone.FRIENDLY)
        val pending = requireNotNull(replies.state.value.pendingConfirmation)
        assertEquals("Accept", pending.action.label)
        replies.confirmDraft(pending.id)
        return pending.id
    }

    private fun assertNoOutput(replies: ReplyCoordinator) {
        assertNull(replies.state.value.analysis)
        assertNull(replies.state.value.pendingConfirmation)
        assertEquals("", replies.state.value.draft)
        assertFalse(replies.state.value.canCopy)
    }

    private fun TestScope.checkNativeFault(fault: Throwable, asResult: Boolean) {
        listOf(ReplyPhase.ModelLoading, ReplyPhase.Analyzing, ReplyPhase.Drafting).forEach { phase ->
            val engine = Engine()
            val replies = if (phase == ReplyPhase.ModelLoading) coordinator(engine) else choosing(engine)
            engine.failure = fault; engine.failurePhase = phase; engine.returnFailure = asResult
            when (phase) {
                ReplyPhase.ModelLoading -> replies.initialize()
                ReplyPhase.Analyzing -> replies.analyze()
                else -> confirm(replies)
            }
            runCurrent()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertFalse(replies.state.value.busy)
            assertNoOutput(replies)
            assertFalse(engine.usable)
            assertEquals(1, engine.cleanups)
            assertFalse(replies.state.value.notice.orEmpty().contains("PRIVATE"))
            assertEquals(if (fault is OutOfMemoryError)
                "The phone ran out of memory while running the local model. Close other apps and retry."
                else "The local AI runtime is not available on this device.", replies.state.value.notice)
            val analyses = engine.analyses
            val drafts = engine.drafts
            replies.recover()
            assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
            replies.analyze(); replies.draft("accept", ReplyTone.CONCISE); replies.confirmDraft(1)
            assertEquals(analyses, engine.analyses)
            assertEquals(drafts, engine.drafts)
            engine.failure = null
            replies.initialize(); runCurrent()
            assertEquals(ReplyPhase.Ready, replies.state.value.phase)
            assertTrue(engine.usable)
            assertEquals(1, engine.cleanups)
        }
    }

    @Test fun thrownLinkageFaultAtEveryStageInvalidatesTheActualEngine() = runTest {
        checkNativeFault(UnsatisfiedLinkError("PRIVATE native path"), false)
    }
    @Test fun resultLinkageFaultAtEveryStageInvalidatesTheActualEngine() = runTest {
        checkNativeFault(NoClassDefFoundError("PRIVATE missing binding"), true)
    }
    @Test fun thrownOomAtEveryStageKeepsItsClassification() = runTest {
        checkNativeFault(OutOfMemoryError("PRIVATE synthetic OOM"), false)
    }
    @Test fun resultOomAtEveryStageKeepsItsClassification() = runTest {
        checkNativeFault(OutOfMemoryError("PRIVATE synthetic OOM"), true)
    }

    @Test fun failedCleanupRequiresProcessRestartAndDoesNotRetryThePoisonedHandle() = runTest {
        listOf<Throwable>(IllegalStateException("PRIVATE close"), UnsatisfiedLinkError("PRIVATE close"), OutOfMemoryError("PRIVATE close")).forEach { cleanup ->
            val engine = Engine().apply { failure = UnsatisfiedLinkError("PRIVATE load"); cleanupFailure = cleanup }
            val replies = coordinator(engine)
            replies.initialize(); runCurrent()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertEquals("Local AI cleanup failed. Restart Akma before retrying.", replies.state.value.notice)
            engine.failure = null; engine.cleanupFailure = null
            repeat(3) { replies.initialize(); runCurrent() }
            assertEquals(1, engine.initializations)
            assertEquals(1, engine.cleanups)
            assertFalse(engine.usable)
            assertNoOutput(replies)
        }
    }

    @Test fun missingModelRequiresSuccessfulInitializationBeforeAnalysis() = runTest {
        val engine = Engine().apply { failure = ModelUnavailableException(); returnFailure = true }
        val replies = coordinator(engine)
        replies.initialize(); runCurrent()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertEquals("No local model is configured.", replies.state.value.notice)
        replies.analyze(); assertEquals(0, engine.analyses)
        engine.failure = null
        replies.initialize(); runCurrent()
        replies.setMessage("Synthetic source"); replies.analyze(); runCurrent()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
    }

    @Test fun corruptModelAndInitializationExceptionsNeverLeaveRuntimeReady() = runTest {
        listOf<Throwable>(IllegalArgumentException("PRIVATE corrupted model"), IllegalStateException("PRIVATE initialize")).forEach { error ->
            val engine = Engine().apply { failure = error }
            val replies = coordinator(engine)
            replies.initialize(); runCurrent()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertNoOutput(replies)
            assertEquals("Local processing failed. Check the model and retry.", replies.state.value.notice)
            replies.recover()
            assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
            replies.analyze(); assertEquals(0, engine.analyses)
        }
    }

    @Test fun spontaneousInitializationCancellationIsNotConvertedIntoFailure() = runTest {
        val engine = Engine().apply { failure = CancellationException("PRIVATE cancel") }
        val replies = coordinator(engine)
        replies.initialize(); runCurrent()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertEquals("Cancelled.", replies.state.value.notice)
        assertFalse(replies.state.value.busy)
        assertEquals(1, engine.cleanups)
    }

    @Test fun unrelatedFatalErrorsPropagateInsteadOfBecomingFriendlyFailures() = runTest {
        listOf<Throwable>(AssertionError("PRIVATE assertion"), StackOverflowError("PRIVATE stack"), InternalError("PRIVATE VM"), ThreadDeath()).forEach { fatal ->
            val escaped = mutableListOf<Throwable>()
            val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> escaped += error })
            try {
                val engine = Engine().apply { failure = fatal }
                val replies = ReplyCoordinator(engine, owner, StandardTestDispatcher(testScheduler))
                replies.initialize(); runCurrent()
                assertEquals(listOf(fatal), escaped)
                assertNull(replies.state.value.notice)
                assertEquals(0, engine.cleanups)
            } finally { owner.cancel(); runCurrent() }
        }
    }

    @Test fun nonCooperativeNativeTimeoutEndsLoadingBeforeCompletionAndSerializesResetAndRetry() = runTest {
        val engine = Engine()
        val replies = choosing(engine, 100)
        engine.delayedPhase = ReplyPhase.Drafting; engine.delayMillis = 300; engine.nonCooperative = true
        val oldToken = confirm(replies)
        runCurrent(); advanceTimeBy(101); runCurrent()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertFalse(replies.state.value.busy)
        assertNoOutput(replies)
        assertEquals(0, engine.cleanups)
        replies.recover()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        replies.setMessage("Replacement synthetic source")
        replies.confirmDraft(oldToken)
        assertEquals(1, engine.drafts)
        engine.delayMillis = 0; engine.nonCooperative = false
        replies.initialize(); runCurrent()
        assertEquals(1, engine.initializations)
        // The queued retry times out safely while the old call still owns the mutex.
        advanceTimeBy(101); runCurrent()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        advanceUntilIdle()
        assertEquals(1, engine.cleanups)
        assertEquals(1, engine.initializations)
        assertEquals(1, engine.peak)
        assertNoOutput(replies)
        replies.initialize(); runCurrent()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertEquals(2, engine.initializations)
        assertEquals("Replacement synthetic source", replies.state.value.message)
    }

    @Test fun canceledQueuedWorkNeverInvokesInferenceOrClosesAnActiveHandle() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        engine.delayedPhase = ReplyPhase.Analyzing; engine.delayMillis = 300; engine.nonCooperative = true
        replies.analyze(); runCurrent()
        replies.cancel(); replies.setMessage("Replacement synthetic source")
        replies.analyze(); runCurrent()
        replies.cancel(); replies.setMessage("")
        advanceUntilIdle()
        assertEquals(2, engine.analyses)
        assertEquals(0, engine.cleanups)
        assertEquals(1, engine.peak)
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertEquals("", replies.state.value.message)
        assertNoOutput(replies)
    }

    @Test fun canceledLateNativeFaultBlocksQueuedInferenceWithoutPublishingPrivateFailure() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        engine.delayedPhase = ReplyPhase.Analyzing; engine.delayMillis = 300; engine.nonCooperative = true
        engine.failurePhase = ReplyPhase.Analyzing; engine.failure = OutOfMemoryError("PRIVATE late failure")
        replies.analyze(); runCurrent(); replies.cancel()
        replies.setMessage("Replacement synthetic source")
        engine.failure = null; engine.delayMillis = 0; engine.nonCooperative = false
        replies.analyze(); runCurrent(); advanceUntilIdle()
        assertEquals(2, engine.analyses)
        assertEquals(1, engine.cleanups)
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertEquals("No local model is configured.", replies.state.value.notice)
        assertEquals("Replacement synthetic source", replies.state.value.message)
        assertNoOutput(replies)
    }

    @Test fun cancellationClearsSessionAndLateOutputCannotRestoreConfirmationOrCopy() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        engine.delayedPhase = ReplyPhase.Drafting; engine.delayMillis = 300; engine.nonCooperative = true
        val token = confirm(replies)
        runCurrent(); replies.cancel(); replies.setMessage("")
        replies.confirmDraft(token); advanceUntilIdle()
        assertEquals(1, engine.drafts)
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertEquals("", replies.state.value.message)
        assertNoOutput(replies)
        assertEquals(0, engine.cleanups)
    }

    @Test fun quarantineDuringHungCancellationBlocksRetryWithoutWaitingForTheNativeMutex() = runTest {
        val engine = Engine()
        val replies = choosing(engine, 100)
        engine.delayedPhase = ReplyPhase.Drafting; engine.delayMillis = 300; engine.nonCooperative = true
        confirm(replies); runCurrent(); advanceTimeBy(101); runCurrent()
        engine.requiresRestart = true
        replies.initialize()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertFalse(replies.state.value.busy)
        assertEquals("Local AI cleanup failed. Restart Akma before retrying.", replies.state.value.notice)
        assertEquals(1, engine.initializations)
        assertNoOutput(replies)
        advanceUntilIdle()
        replies.initialize()
        assertEquals(1, engine.initializations)
    }

    @Test fun rawDraftLimitAppliesBeforeCleanupAndNeverSilentlyTruncates() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        engine.draftText = "x".repeat(1_500)
        confirm(replies); runCurrent()
        assertEquals(engine.draftText, replies.state.value.draft)
        assertTrue(replies.state.value.canCopy)
        engine.draftText = "x" + "\u200B".repeat(1_500)
        confirm(replies); runCurrent()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertEquals("The local model returned an unusable reply. Retry.", replies.state.value.notice)
        assertEquals("", replies.state.value.draft)
        assertFalse(replies.state.value.canCopy)
    }

    @Test fun malformedUnicodeCannotBecomeEditableOrCopyable() = runTest {
        listOf("\uD800", "\uDC00", "Synthetic\uD800reply").forEach { malformed ->
            val engine = Engine().apply { draftText = malformed }
            val replies = choosing(engine)
            confirm(replies); runCurrent()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertEquals("The local model returned an unusable reply. Retry.", replies.state.value.notice)
            assertFalse(replies.state.value.canCopy)
            assertEquals("", replies.state.value.draft)
        }
    }
}
