package ph.merd.akma.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefineInstructionTest {
    // Synthetic engine exists only in tests. It records what the coordinator asks for.
    private class RecordingEngine : LocalReplyEngine {
        val requests = mutableListOf<DraftRequest>()
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun analyze(request: AnalyzeRequest) = Result.success(
            AnalysisResult("invitation", "Synthetic summary", true, listOf(SuggestedAction("accept", "Accept")), AnalysisSource.DETERMINISTIC),
        )
        override suspend fun draft(request: DraftRequest): Result<String> {
            requests += request
            return Result.success("Synthetic test draft ${requests.size}")
        }
    }

    @Test
    fun refineInstructionIsStagedForConfirmationAndReachesTheEngine() = runTest {
        val engine = RecordingEngine()
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        advanceUntilIdle()
        replies.draft("accept", ReplyTone.PROFESSIONAL)
        replies.confirmDraft(requireNotNull(replies.state.value.pendingConfirmation).id)
        advanceUntilIdle()
        assertEquals("", engine.requests.single().userInstruction)

        // Refine stages a new confirmation; nothing runs until the user confirms it.
        replies.draft("accept", ReplyTone.PROFESSIONAL, "Make it shorter.")
        val pending = requireNotNull(replies.state.value.pendingConfirmation)
        assertEquals("Make it shorter.", pending.request.userInstruction)
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)

        replies.confirmDraft(pending.id)
        advanceUntilIdle()
        assertEquals("Make it shorter.", engine.requests.last().userInstruction)
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
    }
}
