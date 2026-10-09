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
import ph.merd.akma.overlay.clearOverlayReplySession

/** Test-only engines exercise real coordinator bindings; they are never installed in the app. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DraftConfirmationUiTest {
    private val message = "Synthetic invitation: please ask for details before committing."

    private class Engine(
        private val finish: CompletableDeferred<String>? = null,
        private val initializeFinish: CompletableDeferred<Unit>? = null,
    ) : LocalReplyEngine {
        val requests = mutableListOf<DraftRequest>()
        var initializationCalls = 0
        override suspend fun initialize(): Result<Unit> {
            initializationCalls++
            initializeFinish?.let { withContext(NonCancellable) { it.await() } }
            return Result.success(Unit)
        }
        override suspend fun analyze(request: AnalyzeRequest) = Result.success(AnalysisResult(
            "interview_invitation", "Synthetic analysis", false,
            listOf(SuggestedAction("accept", "Decline"), SuggestedAction("reschedule", "Accept")),
            AnalysisSource.DETERMINISTIC,
        ))
        override suspend fun draft(request: DraftRequest): Result<String> {
            requests += request
            return Result.success(finish?.let { withContext(NonCancellable) { it.await() } }
                ?: "Synthetic test-only draft")
        }
    }

    private fun TestScope.ready(engine: Engine): ReplyCoordinator {
        val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
        replies.initialize()
        runCurrent()
        replies.setMessage(message)
        replies.analyze()
        runCurrent()
        return replies
    }

    @Test fun selectionAndRenderingNeverGenerateUntilSeparateConfirmation() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val displayed = requireNotNull(replies.state.value.displayedConfirmation())
        assertEquals("Accept", displayed.action.label)
        assertEquals(ReplyTone.FRIENDLY, displayed.request.tone)
        assertEquals(message, displayed.request.original.message)
        assertFalse(replies.state.value.canCopy)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        replies.confirmDisplayedDraft(displayed.id)
        runCurrent()
        assertEquals(listOf(displayed.request), engine.requests)
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        replies.editDraft("Synthetic edited draft")
        replies.copied()
        assertEquals(ReplyPhase.Copied, replies.state.value.phase)
        assertEquals("Synthetic edited draft", replies.state.value.draft)
        assertTrue(replies.state.value.canCopy)
    }

    @Test fun reconstructedBindingKeepsImmutablePendingToneContextAndToken() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("reschedule", ReplyTone.CONCISE)
        val firstSurface = replies.state.value.displayedConfirmation()
        val reconstructedSurface = replies.state.value.copy().displayedConfirmation()
        assertEquals(firstSurface, reconstructedSurface)
        assertEquals(ReplyTone.CONCISE, reconstructedSurface?.request?.tone)
        assertEquals(message, reconstructedSurface?.request?.original?.message)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
    }

    @Test fun repeatedConfirmTapsCannotStartAnotherDraftEvenAfterCompletion() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.PROFESSIONAL)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        repeat(10) { replies.confirmDisplayedDraft(id) }
        runCurrent()
        repeat(10) { replies.confirmDisplayedDraft(id) }
        runCurrent()
        assertEquals(1, engine.requests.size)
    }

    @Test fun staleConfirmNeverSubstitutesReplacementTokenOrTone() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val old = requireNotNull(replies.state.value.displayedConfirmation())
        val oldClick = { replies.confirmDisplayedDraft(old.id) }
        // Simulate a replacement from another client of the domain API.
        replies.draft("reschedule", ReplyTone.CONCISE)
        val replacement = requireNotNull(replies.state.value.displayedConfirmation())
        oldClick()
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        assertEquals(replacement, replies.state.value.pendingConfirmation)
        replies.confirmDisplayedDraft(replacement.id)
        runCurrent()
        assertEquals(listOf(replacement.request), engine.requests)
    }

    @Test fun cancelBeforeConfirmClearsSessionAndPreventsGeneration() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.cancelDisplayedDraft(id)
        replies.confirmDisplayedDraft(id)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertFalse(replies.state.value.canChooseDraft)
    }

    @Test fun staleCancelCannotDismissAnotherSurfacesNewSelection() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val oldId = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.cancelDisplayedDraft(oldId)
        replies.setMessage("Second synthetic message")
        replies.analyze()
        runCurrent()
        replies.selectDraft("reschedule", ReplyTone.CONCISE)
        val replacement = requireNotNull(replies.state.value.displayedConfirmation())
        replies.cancelDisplayedDraft(oldId)
        assertEquals(replacement, replies.state.value.pendingConfirmation)
        assertEquals("Second synthetic message", replies.state.value.message)
    }

    @Test fun cancelProcessingClearsSessionAndDropsNonCooperativeLateOutput() = runTest {
        val finish = CompletableDeferred<String>()
        val engine = Engine(finish)
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.confirmDisplayedDraft(id)
        runCurrent()
        val displayed = replies.state.value
        replies.cancelDisplayedProcessing(displayed)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        finish.complete("Synthetic cancelled draft must not reappear")
        runCurrent()
        replies.cancelDisplayedProcessing(displayed)
        replies.confirmDisplayedDraft(id)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertEquals(1, engine.requests.size)
    }

    @Test fun staleProcessingCancelCannotClearAnotherSessionsContent() = runTest {
        val finish = CompletableDeferred<String>()
        val replies = ready(Engine(finish))
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        replies.confirmDisplayedDraft(requireNotNull(replies.state.value.displayedConfirmation()).id)
        runCurrent()
        val oldDisplayed = replies.state.value
        replies.cancelDisplayedProcessing(oldDisplayed)
        replies.setMessage("Second synthetic message")
        replies.analyze()
        val nextDisplayed = replies.state.value
        assertTrue(nextDisplayed.busy)
        replies.cancelDisplayedProcessing(oldDisplayed)
        assertSame(nextDisplayed, replies.state.value)
        replies.cancelDisplayedProcessing(nextDisplayed)
        finish.complete("Synthetic old native output")
        runCurrent()
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
    }

    @Test fun retryOnlyRunsWhenUnavailableOrErrorAndNeverWhileBusyOrPending() = runTest {
        val finish = CompletableDeferred<String>()
        val engine = Engine(finish)
        val replies = ready(engine)
        ReplyPhase.entries.forEach { phase ->
            assertEquals(phase in setOf(ReplyPhase.ModelUnavailable, ReplyPhase.Error),
                ReplyState(phase = phase).canRetryLocalModel)
        }
        replies.retryLocalModel()
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        assertFalse(replies.state.value.canRetryLocalModel)
        replies.retryLocalModel()
        replies.confirmDisplayedDraft(requireNotNull(replies.state.value.displayedConfirmation()).id)
        replies.retryLocalModel()
        runCurrent()
        assertEquals(1, engine.initializationCalls)
        finish.completeExceptionally(IllegalStateException("Synthetic private prompt must never display"))
        runCurrent()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertEquals("Local processing failed. Check the model and retry.", replies.state.value.notice)
        assertFalse(replies.state.value.statusText().contains("Synthetic private prompt"))
        replies.retryLocalModel()
        runCurrent()
        assertEquals(2, engine.initializationCalls)
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
    }

    @Test fun cancelledInitializationCannotRestoreReadyOrSensitiveContent() = runTest {
        val finish = CompletableDeferred<Unit>()
        val engine = Engine(initializeFinish = finish)
        val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
        replies.setMessage(message)
        replies.retryLocalModel()
        runCurrent()
        assertEquals(ReplyPhase.ModelLoading, replies.state.value.phase)
        assertEquals("Loading local model…", replies.state.value.statusText())
        replies.retryLocalModel()
        replies.cancelDisplayedProcessing(replies.state.value)
        assertEquals(ReplyState(), replies.state.value)
        finish.complete(Unit)
        runCurrent()
        assertEquals(ReplyState(), replies.state.value)
        assertEquals(1, engine.initializationCalls)
    }

    @Test fun recreatedBusyBindingMustUseLiveSnapshotToCancel() = runTest {
        val finish = CompletableDeferred<String>()
        val replies = ready(Engine(finish))
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        replies.confirmDisplayedDraft(requireNotNull(replies.state.value.displayedConfirmation()).id)
        runCurrent()
        val live = replies.state.value
        replies.cancelDisplayedProcessing(live.copy())
        assertSame(live, replies.state.value)
        replies.cancelDisplayedProcessing(live)
        finish.complete("Synthetic late result")
        runCurrent()
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
    }

    @Test fun pendingSelectionCannotBeChangedByRacingActionOrToneClicks() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val pending = requireNotNull(replies.state.value.displayedConfirmation())
        assertFalse(replies.state.value.canChooseDraft)
        assertFalse(replies.state.value.canStartProcessing)
        replies.selectDraft("reschedule", ReplyTone.CONCISE)
        replies.selectDraft("accept", ReplyTone.PROFESSIONAL)
        assertEquals(pending, replies.state.value.displayedConfirmation())
        assertTrue(engine.requests.isEmpty())
    }

    @Test fun busyStateRejectsSelectionAndOldPendingCancel() = runTest {
        val finish = CompletableDeferred<String>()
        val engine = Engine(finish)
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.confirmDisplayedDraft(id)
        runCurrent()
        replies.selectDraft("reschedule", ReplyTone.CONCISE)
        replies.cancelDisplayedDraft(id)
        assertTrue(replies.state.value.busy)
        assertFalse(replies.state.value.canChooseDraft)
        assertNull(replies.state.value.pendingConfirmation)
        finish.complete("Synthetic test-only draft")
        runCurrent()
        assertEquals(1, engine.requests.size)
    }

    @Test fun editedMessageInvalidatesTheDisplayedClick() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.setMessage("Second synthetic message")
        replies.confirmDisplayedDraft(id)
        runCurrent()
        assertTrue(engine.requests.isEmpty())
        assertNull(replies.state.value.displayedConfirmation())
        assertEquals("Second synthetic message", replies.state.value.message)
    }

    @Test fun invalidOrMismatchedPresentationCannotEnableConfirm() = runTest {
        val replies = ready(Engine())
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val state = replies.state.value
        val pending = requireNotNull(state.pendingConfirmation)
        assertNull(state.copy(message = "Changed synthetic context").displayedConfirmation())
        assertNull(state.copy(phase = ReplyPhase.Drafting).displayedConfirmation())
        assertNull(state.copy(phase = ReplyPhase.Ready).displayedConfirmation())
        assertNull(state.copy(pendingConfirmation = pending.copy(
            action = SuggestedAction("accept", "Decline"),
        )).displayedConfirmation())
        assertNull(state.copy(pendingConfirmation = pending.copy(
            request = pending.request.copy(selectedActionId = "unsupported"),
        )).displayedConfirmation())
        assertNull(state.copy(analysis = state.analysis!!.copy(
            actions = listOf(SuggestedAction("accept", "Decline")),
        )).displayedConfirmation())
    }

    @Test fun bothSurfaceCallbacksConsumeOnlyOneSharedConfirmation() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val activityId = requireNotNull(replies.state.value.displayedConfirmation()).id
        val overlayId = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.confirmDisplayedDraft(activityId)
        replies.confirmDisplayedDraft(overlayId)
        replies.cancelDisplayedDraft(overlayId)
        runCurrent()
        assertEquals(1, engine.requests.size)
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
    }

    @Test fun dismissalClearsPendingSelectionAndCannotReviveAnOldClick() = runTest {
        val engine = Engine()
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        clearOverlayReplySession(replies)
        replies.confirmDisplayedDraft(id)
        replies.cancelDisplayedDraft(id)
        runCurrent()
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertTrue(engine.requests.isEmpty())
    }

    @Test fun dismissalDuringGenerationDropsNonCooperativeLateDraft() = runTest {
        val finish = CompletableDeferred<String>()
        val engine = Engine(finish)
        val replies = ready(engine)
        replies.selectDraft("accept", ReplyTone.FRIENDLY)
        val id = requireNotNull(replies.state.value.displayedConfirmation()).id
        replies.confirmDisplayedDraft(id)
        runCurrent()
        clearOverlayReplySession(replies)
        finish.complete("Synthetic late draft must not reappear")
        runCurrent()
        replies.confirmDisplayedDraft(id)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertEquals(1, engine.requests.size)
    }

    @Test fun confirmTouchGuardRejectsFullAndPartialObscuringOnly() {
        assertFalse(isObscuredTouch(0))
        assertFalse(isObscuredTouch(0x100))
        assertTrue(isObscuredTouch(android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED))
        assertTrue(isObscuredTouch(android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED))
        assertTrue(isObscuredTouch(android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED or 0x100))
    }
}
