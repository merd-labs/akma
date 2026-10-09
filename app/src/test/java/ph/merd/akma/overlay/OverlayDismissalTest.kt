package ph.merd.akma.overlay

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import ph.merd.akma.domain.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OverlayDismissalTest {
    private val analysis = AnalysisResult(
        category = "other",
        summary = "Synthetic summary",
        requiresUserDecision = true,
        actions = listOf(SuggestedAction("ask_to_clarify", "Ask to clarify")),
        source = AnalysisSource.DETERMINISTIC,
    )
    private inner class Engine(val pending: CompletableDeferred<AnalysisResult>? = null) : LocalReplyEngine {
        var initializations = 0
        override suspend fun initialize(): Result<Unit> { initializations++; return Result.success(Unit) }
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> =
            Result.success(pending?.let { withContext(NonCancellable) { it.await() } } ?: analysis)
        override suspend fun draft(request: DraftRequest) = Result.success("Synthetic test-only draft")
    }

    @Test fun dismissalClearsCopiedContentButKeepsInitializedModel() = runTest {
        val engine = Engine()
        val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
        replies.initialize(); runCurrent()
        replies.setMessage("Synthetic message")
        replies.analyze(); runCurrent()
        replies.draft("ask_to_clarify", ReplyTone.CONCISE); runCurrent()
        val confirmation = requireNotNull(replies.state.value.pendingConfirmation)
        replies.confirmDraft(confirmation.id); runCurrent()
        replies.editDraft("Synthetic edited draft"); replies.copied()
        assertTrue(replies.state.value.canCopy)
        clearOverlayReplySession(replies)
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
        assertFalse(replies.state.value.canCopy)
        replies.setMessage("Second synthetic message")
        replies.analyze(); runCurrent()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals(1, engine.initializations)
    }

    @Test fun dismissalDropsLateNonCooperativeResult() = runTest {
        val pending = CompletableDeferred<AnalysisResult>()
        val replies = ReplyCoordinator(Engine(pending), backgroundScope, StandardTestDispatcher(testScheduler))
        replies.initialize(); runCurrent()
        replies.setMessage("Synthetic message")
        replies.analyze(); runCurrent()
        assertTrue(replies.state.value.busy)
        clearOverlayReplySession(replies)
        pending.complete(analysis); runCurrent()
        assertEquals(ReplyState(phase = ReplyPhase.Ready), replies.state.value)
    }

    @Test fun clearingUnavailableSessionDoesNotPretendModelIsReady() = runTest {
        val replies = ReplyCoordinator(UnavailableReplyEngine(), backgroundScope, StandardTestDispatcher(testScheduler))
        replies.setMessage("Synthetic message")
        clearOverlayReplySession(replies)
        clearOverlayReplySession(replies)
        assertEquals(ReplyState(), replies.state.value)
    }

    @Test fun clearingDuringModelLoadPreventsLateReadyState() = runTest {
        val release = CompletableDeferred<Unit>()
        val engine = object : LocalReplyEngine {
            override suspend fun initialize(): Result<Unit> {
                withContext(NonCancellable) { release.await() }
                return Result.success(Unit)
            }
            override suspend fun analyze(request: AnalyzeRequest) = Result.success(analysis)
            override suspend fun draft(request: DraftRequest) = Result.success("Synthetic test-only draft")
        }
        val replies = ReplyCoordinator(engine, backgroundScope, StandardTestDispatcher(testScheduler))
        replies.setMessage("Synthetic message")
        replies.initialize(); runCurrent()
        clearOverlayReplySession(replies)
        release.complete(Unit); runCurrent()
        assertEquals(ReplyState(), replies.state.value)
    }
}
