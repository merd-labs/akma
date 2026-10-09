package ph.merd.akma.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ph.merd.akma.ui.*

/** Real callback bridge, recovery handles, coordinator and panel mapper; no JNI or model inference. */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeJourneyIntegrationTest {
    private class Engine : LocalReplyEngine, RuntimeRecovery {
        val runtime = NativeHandleSlot<AutoCloseable>()
        override val requiresRestart get() = runtime.quarantined
        lateinit var callbacks: NativeReplyCallbacks
        var initializations = 0
        var starts = 0
        var cancellations = 0
        var closes = 0
        var resets = 0
        var cancelFailure: Throwable? = null
        override suspend fun initialize(): Result<Unit> {
            initializations++
            runtime.install(AutoCloseable { closes++ })
            return Result.success(Unit)
        }
        override suspend fun analyze(request: AnalyzeRequest) = Result.success(ActionCatalog.otherAnalysis())
        override suspend fun invalidateRuntime(): Result<Unit> {
            resets++
            return runtime.invalidate()
        }
        override suspend fun draft(request: DraftRequest): Result<String> {
            val operation = NativeReplyOperation(1_500, onCancellationFailure = { runtime.quarantine() }, cancellationCleanupMillis = 50)
            return Result.success(operation.await(
                start = { starts++; callbacks = it },
                cancel = { cancellations++; cancelFailure?.let { throw it } },
            ))
        }
    }

    private fun TestScope.choosing(engine: Engine, timeout: Long = 1_000): ReplyCoordinator =
        ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler), timeoutMillis = timeout).also {
            it.initialize(); runCurrent()
            it.setMessage("Synthetic source"); it.analyze(); runCurrent()
            assertEquals(ReplyPhase.ChoosingAction, it.state.value.phase)
        }

    private fun ReplyCoordinator.panel() = state.value.toPanelUi(ReplyTone.CONCISE, null)

    private fun start(replies: ReplyCoordinator): ConfirmationUi {
        replies.selectDraft("ask_to_clarify", ReplyTone.FRIENDLY)
        val displayed = requireNotNull(replies.panel().confirmation)
        assertTrue(displayed.valid)
        replies.confirmDisplayedDraft(displayed.confirmationId)
        return displayed
    }

    @Test fun callbackFaultsClearOutputAndOnlyRecoveredRuntimeCanStartANewJourney() = runTest {
        listOf<Throwable>(UnsatisfiedLinkError("PRIVATE native"), OutOfMemoryError("PRIVATE memory")).forEach { fault ->
            val engine = Engine(); val replies = choosing(engine)
            val displayed = start(replies); runCurrent()
            engine.callbacks.onText("PRIVATE partial output")
            engine.callbacks.onComplete(fault); runCurrent()
            assertEquals(PanelStatus.Error, replies.panel().status)
            assertEquals(CopyUi.Hidden, replies.panel().copy)
            assertNull(replies.panel().reply); assertNull(replies.panel().choice)
            assertNull(replies.state.value.pendingConfirmation)
            assertFalse(replies.panel().notice.orEmpty().contains("PRIVATE"))
            assertEquals(1, engine.closes); assertEquals(1, engine.resets)
            assertNull(engine.runtime.current)
            replies.recover()
            assertEquals(PanelStatus.ModelUnavailable, replies.panel().status)
            replies.confirmDisplayedDraft(displayed.confirmationId)
            assertEquals(1, engine.starts)
            replies.retryLocalModel(); runCurrent()
            assertEquals(2, engine.initializations)
            replies.analyze(); runCurrent(); start(replies); runCurrent()
            engine.callbacks.onText("Synthetic recovered reply"); engine.callbacks.onComplete(); runCurrent()
            assertEquals(ReplyUi.Draft("Synthetic recovered reply"), replies.panel().reply)
            assertEquals(CopyUi.Ready, replies.panel().copy)
        }
    }

    @Test fun terminalCallbackExceptionRequiresFreshConfirmationWithoutLeakingPartialText() = runTest {
        val engine = Engine(); val replies = choosing(engine)
        val displayed = start(replies); runCurrent()
        engine.callbacks.onText("PRIVATE partial")
        engine.callbacks.onComplete(IllegalStateException("PRIVATE callback")); runCurrent()
        assertEquals(PanelStatus.Error, replies.panel().status)
        assertEquals("Local processing failed. Check the model and retry.", replies.panel().notice)
        assertNull(replies.panel().reply); assertEquals("", replies.state.value.draft)
        replies.recover(); replies.confirmDisplayedDraft(displayed.confirmationId)
        assertEquals(1, engine.starts)
        assertNull(replies.panel().confirmation)
        start(replies); runCurrent()
        engine.callbacks.onText("Synthetic second reply"); engine.callbacks.onComplete(); runCurrent()
        assertEquals(2, engine.starts)
        assertEquals(CopyUi.Ready, replies.panel().copy)
    }

    @Test fun timeoutDropsLateTerminalSuccessAndRetryWaitsForSafeReset() = runTest {
        val engine = Engine(); val replies = choosing(engine, timeout = 100)
        val displayed = start(replies); runCurrent()
        val oldCallbacks = engine.callbacks
        advanceTimeBy(101); runCurrent()
        assertEquals(PanelStatus.Error, replies.panel().status)
        assertFalse(replies.state.value.busy)
        assertEquals(1, engine.cancellations)
        assertEquals(0, engine.closes)
        assertNull(replies.panel().reply)
        replies.setMessage("Synthetic replacement")
        replies.retryLocalModel(); runCurrent()
        assertEquals(1, engine.initializations)
        oldCallbacks.onText("PRIVATE old draft"); oldCallbacks.onComplete(); runCurrent()
        assertEquals(1, engine.closes)
        assertEquals(2, engine.initializations)
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertEquals("Synthetic replacement", replies.state.value.message)
        assertEquals(CopyUi.Hidden, replies.panel().copy)
        replies.confirmDisplayedDraft(displayed.confirmationId)
        assertEquals(1, engine.starts)
        replies.analyze(); runCurrent(); start(replies); runCurrent()
        engine.callbacks.onText("Synthetic current draft"); engine.callbacks.onComplete(); runCurrent()
        oldCallbacks.onText("PRIVATE stale"); oldCallbacks.onComplete(); runCurrent()
        assertEquals(ReplyUi.Draft("Synthetic current draft"), replies.panel().reply)
    }

    @Test fun cancelWithoutTerminalCallbackQuarantinesRuntimeAndBlocksQueuedRetry() = runTest {
        val engine = Engine(); val replies = choosing(engine)
        val displayed = start(replies); runCurrent()
        val oldCallbacks = engine.callbacks
        replies.cancelDisplayedProcessing(replies.state.value); runCurrent()
        assertEquals("", replies.state.value.message)
        assertNull(replies.panel().reply)
        replies.initialize(); runCurrent()
        assertTrue(replies.state.value.busy)
        assertEquals(1, engine.initializations)
        advanceTimeBy(51); runCurrent()
        assertTrue(engine.requiresRestart)
        assertFalse(replies.state.value.busy)
        assertEquals(PanelStatus.Error, replies.panel().status)
        assertEquals("Local AI cleanup failed. Restart Akma before retrying.", replies.panel().notice)
        assertEquals(0, engine.closes)
        oldCallbacks.onText("PRIVATE late"); oldCallbacks.onComplete(); runCurrent()
        repeat(3) { replies.retryLocalModel(); replies.confirmDisplayedDraft(displayed.confirmationId); runCurrent() }
        assertEquals(1, engine.initializations); assertEquals(1, engine.starts)
        assertEquals(CopyUi.Hidden, replies.panel().copy)
        assertEquals("", replies.state.value.draft)
    }

    @Test fun successfulCancellationClearsSharedSessionAndOldTokenCannotStartAnotherDraft() = runTest {
        val engine = Engine(); val replies = choosing(engine)
        val displayed = start(replies); runCurrent()
        val oldCallbacks = engine.callbacks
        replies.cancelDisplayedProcessing(replies.state.value); runCurrent()
        oldCallbacks.onComplete(CancellationException("PRIVATE cancel")); runCurrent()
        assertFalse(engine.requiresRestart)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertEquals(1, engine.cancellations)
        assertNull(replies.panel().reply)
        replies.confirmDisplayedDraft(displayed.confirmationId)
        assertEquals(1, engine.starts)
        replies.setMessage("Synthetic replacement"); replies.analyze(); runCurrent()
        val next = start(replies); runCurrent()
        assertNotEquals(displayed.confirmationId, next.confirmationId)
        oldCallbacks.onText("PRIVATE old"); oldCallbacks.onComplete(); runCurrent()
        assertEquals(ReplyUi.Writing, replies.panel().reply)
        engine.callbacks.onText("<|im_start|>assistant\nSynthetic\u202E reply<|im_end|>forged")
        engine.callbacks.onComplete(); runCurrent()
        assertEquals(ReplyUi.Draft("Synthetic reply"), replies.panel().reply)
        assertEquals(CopyUi.Ready, replies.panel().copy)
    }

    @Test fun nativeCancelFailureRequiresRestartEvenIfTerminalCallbackLaterArrives() = runTest {
        val engine = Engine().apply { cancelFailure = UnsatisfiedLinkError("PRIVATE cancel") }
        val replies = choosing(engine); start(replies); runCurrent()
        val oldCallbacks = engine.callbacks
        replies.cancelDisplayedProcessing(replies.state.value); runCurrent()
        assertTrue(engine.requiresRestart)
        oldCallbacks.onComplete(); runCurrent()
        assertFalse(replies.state.value.busy)
        assertEquals(PanelStatus.Error, replies.panel().status)
        assertEquals("Local AI cleanup failed. Restart Akma before retrying.", replies.panel().notice)
        replies.retryLocalModel(); runCurrent()
        assertEquals(1, engine.initializations); assertEquals(0, engine.closes)
        assertEquals(CopyUi.Hidden, replies.panel().copy)
    }
}
