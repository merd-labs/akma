package ph.merd.akma.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import ph.merd.akma.domain.*

/** Synthetic engines test event binding, not real native inference or clipboard behavior. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JourneyCoordinatorAdapterTest {
    private class Engine(private val finish: CompletableDeferred<String>? = null) : LocalReplyEngine {
        var initializations = 0
        var analyses = 0
        val requests = mutableListOf<DraftRequest>()
        override suspend fun initialize(): Result<Unit> { initializations++; return Result.success(Unit) }
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> {
            analyses++
            return Result.success(AnalysisResult("interview_invitation", "Synthetic summary", true,
                listOf(SuggestedAction("accept", "Decline"), SuggestedAction("reschedule", "Accept")), AnalysisSource.DETERMINISTIC))
        }
        override suspend fun draft(request: DraftRequest): Result<String> {
            requests += request
            return Result.success(finish?.let { withContext(NonCancellable) { it.await() } } ?: "Synthetic test draft")
        }
    }

    private fun TestScope.ready(engine: Engine): ReplyCoordinator =
        ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler)).also {
            it.initialize(); runCurrent(); it.setMessage("Synthetic invitation")
        }

    private fun adapter(replies: ReplyCoordinator) = JourneyCoordinatorAdapter(replies, {}, { true })

    @Test fun rapidAnalyzeAndCrossSurfaceConfirmationStartOnlyOneRequest() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        val bridge = adapter(replies)
        val callbacks = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        repeat(10) { callbacks.onAnalyze() }
        runCurrent()
        assertEquals(1, engine.analyses)
        val choice = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        choice.onSelectTone(ReplyTone.FRIENDLY)
        repeat(10) { choice.onSelectAction("accept") }
        val pending = requireNotNull(replies.state.value.displayedConfirmation())
        assertEquals("Accept", pending.action.label)
        assertEquals(ReplyTone.FRIENDLY, pending.request.tone)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        val secondSurface = adapter(replies)
        repeat(10) { bridge.confirm(pending.id); secondSurface.confirm(pending.id) }
        runCurrent()
        assertEquals(listOf(pending.request), engine.requests)
    }

    @Test fun pendingToneAndActionStayLockedAndStaleCancelCannotClearReplacement() = runTest {
        val replies = ready(Engine())
        replies.analyze(); runCurrent()
        val bridge = adapter(replies)
        bridge.callbacks(replies.state.value, ReplyTone.CONCISE).onSelectAction("reschedule")
        val old = requireNotNull(replies.state.value.pendingConfirmation)
        val callbacks = bridge.callbacks(replies.state.value, ReplyTone.FRIENDLY)
        callbacks.onSelectTone(ReplyTone.PROFESSIONAL)
        callbacks.onSelectAction("accept")
        assertEquals(old, replies.state.value.pendingConfirmation)
        replies.draft("accept", ReplyTone.FRIENDLY) // Simulate another coordinator client.
        val replacement = requireNotNull(replies.state.value.pendingConfirmation)
        bridge.confirm(old.id)
        callbacks.onCancelConfirmation()
        assertEquals(replacement, replies.state.value.pendingConfirmation)
    }

    @Test fun cancellationClearsSessionAndOldConfirmNeverStartsDraft() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.analyze(); runCurrent()
        val bridge = adapter(replies)
        bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onSelectAction("accept")
        val displayed = replies.state.value
        bridge.callbacks(displayed, ReplyTone.FRIENDLY).onCancelConfirmation()
        bridge.confirm(requireNotNull(displayed.pendingConfirmation).id)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
    }

    @Test fun cancelledNonCooperativeDraftCannotPublishAfterSessionClear() = runTest {
        val finish = CompletableDeferred<String>()
        val replies = ready(Engine(finish))
        replies.analyze(); runCurrent()
        val bridge = adapter(replies)
        bridge.callbacks(replies.state.value, ReplyTone.CONCISE).onSelectAction("reschedule")
        bridge.confirm(requireNotNull(replies.state.value.pendingConfirmation).id)
        runCurrent()
        val callbacks = bridge.callbacks(replies.state.value, ReplyTone.CONCISE)
        callbacks.onCancelProcessing()
        replies.setMessage("Next synthetic session")
        callbacks.onCancelProcessing()
        finish.complete("Synthetic late draft")
        runCurrent()
        assertEquals("Next synthetic session", replies.state.value.message)
        assertEquals("", replies.state.value.draft)
        assertNull(replies.state.value.analysis)
    }

    @Test fun copyIsExplicitAndRejectsStaleDisplayedDraft() = runTest {
        val replies = ready(Engine())
        replies.analyze(); runCurrent()
        replies.selectDraft("accept", ReplyTone.PROFESSIONAL)
        replies.confirmDisplayedDraft(requireNotNull(replies.state.value.pendingConfirmation).id)
        runCurrent()
        val copied = mutableListOf<String>()
        val bridge = JourneyCoordinatorAdapter(replies, {}, { copied += it.draft; true })
        val old = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        assertTrue(copied.isEmpty())
        replies.editDraft("Synthetic user edit")
        old.onCopy()
        assertTrue(copied.isEmpty())
        val current = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        repeat(10) { current.onCopy() }
        assertEquals(listOf("Synthetic user edit"), copied)
        assertEquals(ReplyPhase.Copied, replies.state.value.phase)
    }

    @Test fun failedCopyDoesNotClaimCopiedAndTypingDoesNotWaitForRecomposition() = runTest {
        val replies = ready(Engine())
        val bridge = adapter(replies)
        val input = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        input.onMessageChange("S"); input.onMessageChange("Sy"); input.onMessageChange("Synthetic text")
        assertEquals("Synthetic text", replies.state.value.message)
        replies.analyze(); runCurrent()
        replies.selectDraft("accept", ReplyTone.PROFESSIONAL)
        bridge.confirm(requireNotNull(replies.state.value.pendingConfirmation).id); runCurrent()
        var failures = 0
        val failed = JourneyCoordinatorAdapter(replies, {}, { false }, copyFailed = { failures++ })
        val edit = failed.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        edit.onDraftChange("First edit"); edit.onDraftChange("Second edit")
        assertEquals("Second edit", replies.state.value.draft)
        failed.callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onCopy()
        assertEquals(1, failures)
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
    }

    @Test fun repeatedRetryPasteAndRenderBindingsNeverCreateExtraWork() = runTest {
        val engine = Engine()
        val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
        var pastes = 0
        val bridge = JourneyCoordinatorAdapter(replies, { pastes++; replies.setMessage("Synthetic pasted text") }, { true })
        val callbacks = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        repeat(10) { bridge.callbacks(replies.state.value, ReplyTone.FRIENDLY) }
        assertEquals(0, engine.initializations)
        repeat(10) { callbacks.onRetry() }
        runCurrent()
        assertEquals(1, engine.initializations)
        val input = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        repeat(10) { input.onPaste() }
        assertEquals(1, pastes)
        bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onAnalyze()
        input.onPaste(); input.onRetry()
        runCurrent()
        assertEquals(1, pastes)
        assertEquals(1, engine.initializations)
    }

    @Test fun reconstructedCallbacksClearFinishedSessionAndRejectOldInput() = runTest {
        val replies = ready(Engine())
        replies.analyze(); runCurrent()
        val bridge = adapter(replies)
        bridge.callbacks(replies.state.value, ReplyTone.CONCISE).onSelectAction("reschedule")
        val pending = requireNotNull(replies.state.value.pendingConfirmation)
        val reconstructed = adapter(replies)
        reconstructed.confirm(pending.id); runCurrent()
        val old = reconstructed.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        old.onStartOver()
        old.onDraftChange("Synthetic stale edit")
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
    }

    @Test fun staleInputCannotOverwriteAnotherSurfaceAndBusyCallbacksCannotStageOrPaste() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        var pastes = 0
        val bridge = JourneyCoordinatorAdapter(replies, { pastes++ }, { true })
        val old = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        replies.setMessage("Synthetic other surface message")
        old.onMessageChange("Synthetic stale input")
        assertEquals("Synthetic other surface message", replies.state.value.message)
        replies.analyze()
        val busy = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL)
        busy.onSelectAction("accept"); busy.onPaste(); busy.onCopy(); busy.onAnalyze()
        runCurrent()
        assertEquals(0, pastes)
        assertEquals(1, engine.analyses)
        assertTrue(engine.requests.isEmpty())
        assertNull(replies.state.value.pendingConfirmation)
    }

    @Test fun refineStagesExactInstructionAndRequiresAnotherProtectedConfirmation() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.analyze(); runCurrent()
        val bridge = adapter(replies)
        bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onSelectAction("reschedule")
        bridge.confirm(requireNotNull(replies.state.value.pendingConfirmation).id); runCurrent()
        val callbacks = bridge.callbacks(replies.state.value, ReplyTone.CONCISE, selectedActionId = "reschedule")
        callbacks.onRefine(RefineKind.Shorter)
        val pending = requireNotNull(replies.state.value.pendingConfirmation)
        assertEquals("reschedule", pending.request.selectedActionId)
        assertEquals(ReplyTone.CONCISE, pending.request.tone)
        assertEquals("Make it shorter.", pending.request.userInstruction)
        callbacks.onRefine(RefineKind.MoreFormal)
        runCurrent()
        assertEquals(1, engine.requests.size)
        assertEquals(pending, replies.state.value.pendingConfirmation)
        bridge.confirm(pending.id); runCurrent()
        assertEquals(pending.request, engine.requests.last())
        assertEquals(2, engine.requests.size)
    }

    @Test fun staleOrMissingActionRefinementCannotCreateARequest() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.analyze(); runCurrent()
        val bridge = adapter(replies)
        bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onSelectAction("accept")
        bridge.confirm(requireNotNull(replies.state.value.pendingConfirmation).id); runCurrent()
        val stale = bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL, selectedActionId = "accept")
        replies.editDraft("Synthetic newer user edit")
        stale.onRefine(RefineKind.MoreFormal)
        bridge.callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onRefine(RefineKind.Regenerate)
        runCurrent()
        assertNull(replies.state.value.pendingConfirmation)
        assertEquals("Synthetic newer user edit", replies.state.value.draft)
        assertEquals(1, engine.requests.size)
    }

    @Test fun shellCloseClearsActiveSessionBeforeNavigationAndRejectsLateDraft() = runTest {
        val finish = CompletableDeferred<String>()
        val replies = ready(Engine(finish))
        replies.analyze(); runCurrent()
        replies.selectDraft("reschedule", ReplyTone.CONCISE)
        replies.confirmDisplayedDraft(requireNotNull(replies.state.value.pendingConfirmation).id)
        runCurrent()
        var closes = 0
        var selected: String? = "reschedule"
        val bridge = JourneyCoordinatorAdapter(replies, {}, { true }, close = {
            assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
            assertNull(selected)
            closes++
        })
        val callbacks = bridge.callbacks(replies.state.value, ReplyTone.CONCISE, actionSelected = { selected = it })
        requireNotNull(callbacks.onClose).invoke()
        finish.complete("Synthetic discarded after Close")
        runCurrent()
        assertEquals(1, closes)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertNull(adapter(replies).callbacks(replies.state.value, ReplyTone.PROFESSIONAL).onClose)
    }
}
