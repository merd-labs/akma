package ph.merd.akma.overlay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalysisResult
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftConfirmation
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.LocalReplyEngine
import ph.merd.akma.domain.ModelUnavailableException
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.SuggestedAction

@OptIn(ExperimentalCoroutinesApi::class)
class OverlayDomainIntegrationTest {
    // Synthetic fixtures exercise the real coordinator/session helper, not model quality or phone UI.
    private class Engine : LocalReplyEngine {
        var initializationCalls = 0
        val analysisRequests = mutableListOf<AnalyzeRequest>()
        val draftRequests = mutableListOf<DraftRequest>()
        var initializationResult: Result<Unit> = Result.success(Unit)
        var analysisResult = Result.success(ActionCatalog.otherAnalysis().copy(
            summary = "Synthetic summary",
            requiresUserDecision = false,
            actions = ActionCatalog.actionsFor("other").map { it.copy(label = "Untrusted label") },
        ))
        var initializationRelease: CompletableDeferred<Unit>? = null
        var draftRelease: CompletableDeferred<Unit>? = null
        var active = 0
        var peak = 0

        private suspend fun <T> operation(work: suspend () -> T): T {
            peak = maxOf(peak, ++active)
            return try { work() } finally { active-- }
        }

        override suspend fun initialize(): Result<Unit> = operation {
            initializationCalls++
            val result = initializationResult
            initializationRelease?.let { withContext(NonCancellable) { it.await() } }
            result
        }

        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> = operation {
            analysisRequests += request
            analysisResult
        }

        override suspend fun draft(request: DraftRequest): Result<String> = operation {
            draftRequests += request
            draftRelease?.let { withContext(NonCancellable) { it.await() } }
            Result.success("Synthetic editable draft")
        }
    }

    private fun TestScope.coordinator(engine: Engine) =
        ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))

    private fun TestScope.choosing(engine: Engine): ReplyCoordinator {
        val replies = coordinator(engine)
        replies.initialize()
        runCurrent()
        replies.setMessage("Synthetic original message")
        replies.analyze()
        runCurrent()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertTrue(replies.state.value.analysis!!.requiresUserDecision)
        assertTrue(replies.state.value.analysis!!.actions.size <= 3)
        assertEquals(ActionCatalog.actionsFor(replies.state.value.analysis!!.category).filter {
            action -> replies.state.value.analysis!!.actions.any { it.id == action.id }
        }, replies.state.value.analysis!!.actions)
        return replies
    }

    private fun select(replies: ReplyCoordinator, id: String, tone: ReplyTone): DraftConfirmation {
        replies.draft(id, tone)
        val pending = requireNotNull(replies.state.value.pendingConfirmation)
        assertEquals(ActionCatalog.action(id), pending.action)
        assertEquals(DraftRequest(AnalyzeRequest(replies.state.value.message), id, tone), pending.request)
        assertFalse(replies.state.value.canCopy)
        return pending
    }

    private fun assertCleared(replies: ReplyCoordinator, phase: ReplyPhase) {
        assertEquals(ReplyState(phase = phase), replies.state.value)
        assertFalse(replies.state.value.canCopy)
    }

    @Test
    fun canceledAndDismissedConfirmationsCannotDraftInNewSession() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        val canceled = select(replies, "ask_to_clarify", ReplyTone.PROFESSIONAL)
        runCurrent()
        assertTrue(engine.draftRequests.isEmpty())
        replies.cancel()
        assertNull(replies.state.value.pendingConfirmation)
        replies.confirmDraft(canceled.id)
        runCurrent()
        assertTrue(engine.draftRequests.isEmpty())

        val dismissed = select(replies, "ask_to_clarify", ReplyTone.CONCISE)
        clearOverlayReplySession(replies)
        assertCleared(replies, ReplyPhase.Ready)
        replies.setMessage("Synthetic replacement message")
        replies.analyze()
        runCurrent()
        val current = select(replies, "acknowledge", ReplyTone.FRIENDLY)
        assertNotEquals(dismissed.id, current.id)
        replies.confirmDraft(dismissed.id)
        runCurrent()
        assertEquals(current, replies.state.value.pendingConfirmation)
        assertTrue(engine.draftRequests.isEmpty())
        replies.confirmDraft(current.id)
        runCurrent()
        assertEquals(listOf(DraftRequest(AnalyzeRequest("Synthetic replacement message"), "acknowledge", ReplyTone.FRIENDLY)), engine.draftRequests)
        assertEquals(1, engine.initializationCalls)
    }

    @Test
    fun mislabeledActionAndDuplicateConfirmationKeepCanonicalRequestUntilDismissal() = runTest {
        val engine = Engine().apply {
            analysisResult = Result.success(AnalysisResult(
                "interview_invitation", "Synthetic summary", false,
                listOf(SuggestedAction("accept", "Decline"), SuggestedAction("decline", "Accept")),
                AnalysisSource.LOCAL_MODEL,
            ))
        }
        val replies = choosing(engine)
        assertEquals(listOf(SuggestedAction("accept", "Accept"), SuggestedAction("decline", "Decline politely")), replies.state.value.analysis!!.actions)
        val pending = select(replies, "decline", ReplyTone.FRIENDLY)
        replies.draft("decline", ReplyTone.FRIENDLY)
        assertEquals(pending, replies.state.value.pendingConfirmation)
        assertTrue(engine.draftRequests.isEmpty())
        repeat(3) { replies.confirmDraft(pending.id) }
        runCurrent()
        replies.confirmDraft(pending.id)
        runCurrent()
        assertEquals(listOf(DraftRequest(AnalyzeRequest("Synthetic original message"), "decline", ReplyTone.FRIENDLY)), engine.draftRequests)
        replies.copied()
        assertTrue(replies.state.value.canCopy)
        clearOverlayReplySession(replies)
        clearOverlayReplySession(replies)
        assertCleared(replies, ReplyPhase.Ready)
        replies.copied()
        assertCleared(replies, ReplyPhase.Ready)
    }

    @Test
    fun dismissedNonCooperativeDraftCannotRestoreOutputOrOverlapNextSession() = runTest {
        val release = CompletableDeferred<Unit>()
        val engine = Engine().apply { draftRelease = release }
        val replies = choosing(engine)
        try {
            val pending = select(replies, "ask_to_clarify", ReplyTone.PROFESSIONAL)
            replies.confirmDraft(pending.id)
            runCurrent()
            assertEquals(ReplyPhase.Drafting, replies.state.value.phase)
            assertEquals(1, engine.active)
            clearOverlayReplySession(replies)
            assertCleared(replies, ReplyPhase.Ready)
            replies.setMessage("Synthetic replacement message")
            replies.analyze()
            runCurrent()
            assertEquals(1, engine.analysisRequests.size)
            release.complete(Unit)
            runCurrent()
            assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
            assertEquals(listOf(AnalyzeRequest("Synthetic original message"), AnalyzeRequest("Synthetic replacement message")), engine.analysisRequests)
            assertEquals("", replies.state.value.draft)
            assertNull(replies.state.value.pendingConfirmation)
            assertFalse(replies.state.value.canCopy)
            engine.draftRelease = null
            val current = select(replies, "acknowledge", ReplyTone.CONCISE)
            replies.confirmDraft(current.id)
            runCurrent()
            assertEquals(2, engine.draftRequests.size)
            assertEquals(DraftRequest(AnalyzeRequest("Synthetic replacement message"), "acknowledge", ReplyTone.CONCISE), engine.draftRequests.last())
            assertEquals(1, engine.peak)
            assertEquals(0, engine.active)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
    }

    @Test
    fun dismissalAfterFailedInitializationRequiresSuccessfulRetry() = runTest {
        val engine = Engine().apply { initializationResult = Result.failure(IllegalStateException("PRIVATE SYNTHETIC FAILURE")) }
        val replies = coordinator(engine)
        replies.setMessage("Synthetic original message")
        replies.initialize()
        runCurrent()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertFalse(replies.state.value.notice!!.contains("PRIVATE"))
        clearOverlayReplySession(replies)
        assertCleared(replies, ReplyPhase.ModelUnavailable)
        replies.analyze()
        assertTrue(engine.analysisRequests.isEmpty())
        engine.initializationResult = Result.success(Unit)
        replies.initialize()
        runCurrent()
        assertCleared(replies, ReplyPhase.Ready)
        replies.setMessage("Synthetic replacement message")
        replies.analyze()
        runCurrent()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals(2, engine.initializationCalls)
    }

    @Test
    fun dismissalAfterUnavailableAnalysisKeepsUnavailableUntilReinitialized() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        engine.analysisResult = Result.failure(ModelUnavailableException())
        replies.analyze()
        runCurrent()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        clearOverlayReplySession(replies)
        assertCleared(replies, ReplyPhase.ModelUnavailable)
        replies.setMessage("Synthetic replacement message")
        replies.analyze()
        replies.draft("acknowledge", ReplyTone.CONCISE)
        runCurrent()
        assertEquals(2, engine.analysisRequests.size)
        assertTrue(engine.draftRequests.isEmpty())
        engine.analysisResult = Result.success(ActionCatalog.otherAnalysis())
        replies.initialize()
        runCurrent()
        replies.analyze()
        runCurrent()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals(3, engine.analysisRequests.size)
        assertEquals(2, engine.initializationCalls)
    }

    @Test
    fun dismissalDuringReinitializationRejectsLateReadinessAndOldConfirmation() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        val pending = select(replies, "ask_to_clarify", ReplyTone.CONCISE)
        val release = CompletableDeferred<Unit>()
        engine.initializationRelease = release
        try {
            replies.initialize()
            runCurrent()
            assertEquals(ReplyPhase.ModelLoading, replies.state.value.phase)
            assertNull(replies.state.value.pendingConfirmation)
            assertNull(replies.state.value.analysis)
            clearOverlayReplySession(replies)
            assertCleared(replies, ReplyPhase.ModelUnavailable)
            release.complete(Unit)
            runCurrent()
            assertCleared(replies, ReplyPhase.ModelUnavailable)
            replies.confirmDraft(pending.id)
            replies.analyze()
            runCurrent()
            assertTrue(engine.draftRequests.isEmpty())
            assertEquals(1, engine.analysisRequests.size)
            engine.initializationRelease = null
            replies.initialize()
            runCurrent()
            assertCleared(replies, ReplyPhase.Ready)
            assertEquals(3, engine.initializationCalls)
            assertEquals(1, engine.peak)
        } finally {
            release.complete(Unit)
            runCurrent()
        }
    }
}
