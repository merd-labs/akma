package ph.merd.akma.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyCoordinatorContractTest {
    private val message = "Synthetic invitation: please suggest a response."
    private val action = SuggestedAction("clarify", "Ask for details")

    // Test doubles verify orchestration and validation only, never model quality or real inference.
    private class RecordingEngine : LocalReplyEngine {
        var initializationCalls = 0
        val analysisRequests = mutableListOf<AnalyzeRequest>()
        val draftRequests = mutableListOf<DraftRequest>()
        var initializationDelay = 0L
        var analysisDelay = 0L
        var draftDelay = 0L
        var initializationResult: Result<Unit> = Result.success(Unit)
        var analysisResult = Result.success(AnalysisResult("invitation", "Synthetic summary", true, listOf(SuggestedAction("clarify", "Ask for details"))))
        var draftResult = Result.success("Synthetic draft for orchestration tests")
        var nonCancellableAnalysis = false

        override suspend fun initialize(): Result<Unit> {
            initializationCalls++
            delay(initializationDelay)
            return initializationResult
        }

        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> {
            analysisRequests += request
            val result = analysisResult
            val wait = analysisDelay
            if (nonCancellableAnalysis) withContext(NonCancellable) { delay(wait) } else delay(wait)
            return result
        }

        override suspend fun draft(request: DraftRequest): Result<String> {
            draftRequests += request
            delay(draftDelay)
            return draftResult
        }
    }

    private fun TestScope.coordinator(engine: LocalReplyEngine) =
        ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))

    private fun TestScope.ready(engine: RecordingEngine): ReplyCoordinator {
        val replies = coordinator(engine)
        replies.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        replies.setMessage(message)
        return replies
    }

    private fun TestScope.choosing(engine: RecordingEngine): ReplyCoordinator {
        val replies = ready(engine)
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        return replies
    }

    @Test
    fun allTonesAndSelectedActionReachEngineUnchanged() = runTest {
        val engine = RecordingEngine()
        val replies = choosing(engine)
        ReplyTone.entries.forEach { tone ->
            replies.draft(action.id, tone)
            advanceUntilIdle()
            assertEquals(DraftRequest(AnalyzeRequest(message), action.id, tone), engine.draftRequests.last())
            assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        }
        assertEquals(3, engine.draftRequests.size)
        assertEquals(listOf(AnalyzeRequest(message)), engine.analysisRequests)
    }

    @Test
    fun eachDefaultApprovedActionIsAcceptedAndPropagated() = runTest {
        listOf("reschedule", "acknowledge", "clarify", "accept", "decline").forEach { id ->
            val engine = RecordingEngine().apply {
                analysisResult = Result.success(AnalysisResult("invitation", "Synthetic summary", true, listOf(SuggestedAction(id, "Synthetic $id"))))
            }
            val replies = choosing(engine)
            replies.draft(id, ReplyTone.FRIENDLY)
            advanceUntilIdle()
            assertEquals(id, engine.draftRequests.single().selectedActionId)
            assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        }
    }

    @Test
    fun callerSuppliedAllowlistOverridesDefaultIds() = runTest {
        val engine = RecordingEngine()
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler), allowedActionIds = setOf("accept"))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage(message)
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertNull(replies.state.value.analysis)
        assertTrue(engine.draftRequests.isEmpty())
    }

    @Test
    fun uninitializedOperationsNeverCallEngine() = runTest {
        val engine = RecordingEngine()
        val replies = coordinator(engine)
        replies.setMessage(message)
        replies.analyze()
        replies.draft(action.id, ReplyTone.CONCISE)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertEquals(0, engine.initializationCalls)
        assertTrue(engine.analysisRequests.isEmpty())
        assertTrue(engine.draftRequests.isEmpty())
        assertFalse(replies.state.value.canCopy)
    }

    @Test
    fun modelUnavailableDuringInitializationCanRetry() = runTest {
        val engine = RecordingEngine().apply { initializationResult = Result.failure(ModelUnavailableException()) }
        val replies = coordinator(engine)
        replies.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertFalse(replies.state.value.canCopy)
        engine.initializationResult = Result.success(Unit)
        replies.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertEquals(2, engine.initializationCalls)
    }

    @Test
    fun modelUnavailableDuringAnalysisRequiresReinitialization() = runTest {
        val engine = RecordingEngine().apply { analysisResult = Result.failure(ModelUnavailableException()) }
        val replies = ready(engine)
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertNull(replies.state.value.analysis)
        replies.analyze()
        advanceUntilIdle()
        assertEquals(1, engine.analysisRequests.size)
        assertFalse(replies.state.value.canCopy)
        replies.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
    }

    @Test
    fun modelUnavailableDuringDraftingPreventsFurtherDraftCalls() = runTest {
        val engine = RecordingEngine()
        val replies = choosing(engine)
        engine.draftResult = Result.failure(ModelUnavailableException())
        replies.draft(action.id, ReplyTone.PROFESSIONAL)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertFalse(replies.state.value.canCopy)
        assertEquals("", replies.state.value.draft)
        replies.draft(action.id, ReplyTone.PROFESSIONAL)
        advanceUntilIdle()
        assertEquals(1, engine.draftRequests.size)
    }

    @Test
    fun duplicateInitializationAndOtherTapsWhileLoadingStartOneCall() = runTest {
        val engine = RecordingEngine().apply { initializationDelay = 100 }
        val replies = coordinator(engine)
        replies.initialize()
        replies.initialize()
        replies.analyze()
        replies.draft(action.id, ReplyTone.FRIENDLY)
        advanceUntilIdle()
        assertEquals(1, engine.initializationCalls)
        assertTrue(engine.analysisRequests.isEmpty())
        assertTrue(engine.draftRequests.isEmpty())
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
    }

    @Test
    fun repeatedDraftAndAnalyzeTapsWhileDraftingStartOneCall() = runTest {
        val engine = RecordingEngine().apply { draftDelay = 100 }
        val replies = choosing(engine)
        replies.draft(action.id, ReplyTone.FRIENDLY)
        repeat(3) {
            replies.draft(action.id, ReplyTone.CONCISE)
            replies.analyze()
            replies.initialize()
        }
        assertEquals(ReplyPhase.Drafting, replies.state.value.phase)
        advanceUntilIdle()
        assertEquals(1, engine.initializationCalls)
        assertEquals(1, engine.analysisRequests.size)
        assertEquals(1, engine.draftRequests.size)
        assertEquals(ReplyTone.FRIENDLY, engine.draftRequests.single().tone)
    }

    @Test
    fun cancelledInitializationReturnsToUnavailableAndCanRetry() = runTest {
        val engine = RecordingEngine().apply { initializationDelay = 100 }
        val replies = coordinator(engine)
        replies.initialize()
        runCurrent()
        replies.cancel()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertEquals("Cancelled.", replies.state.value.notice)
        replies.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertEquals(2, engine.initializationCalls)
    }

    @Test
    fun cancelledDraftReturnsToActionSelectionWithoutPublishingOutput() = runTest {
        val engine = RecordingEngine().apply { draftDelay = 100 }
        val replies = choosing(engine)
        replies.draft(action.id, ReplyTone.PROFESSIONAL)
        runCurrent()
        replies.cancel()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals("", replies.state.value.draft)
        assertFalse(replies.state.value.canCopy)
        assertEquals(listOf(action), replies.state.value.analysis!!.actions)
        replies.draft(action.id, ReplyTone.CONCISE)
        advanceUntilIdle()
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        assertEquals(2, engine.draftRequests.size)
    }

    @Test
    fun cancelledNonCooperativeAnalysisCannotPublishOrOverlapRetry() = runTest {
        val engine = RecordingEngine().apply {
            analysisDelay = 100
            nonCancellableAnalysis = true
        }
        val replies = ready(engine)
        replies.analyze()
        runCurrent()
        replies.cancel()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        engine.analysisResult = Result.success(AnalysisResult("retry", "Synthetic retry summary", true, listOf(action)))
        replies.analyze()
        runCurrent()
        assertEquals(1, engine.analysisRequests.size)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, engine.analysisRequests.size)
        assertEquals(ReplyPhase.Analyzing, replies.state.value.phase)
        assertNull(replies.state.value.analysis)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals("retry", replies.state.value.analysis!!.category)
    }

    @Test
    fun malformedAnalysisNeverBecomesSelectable() = runTest {
        val valid = AnalysisResult("invitation", "Synthetic summary", true, listOf(action))
        val malformed = listOf(
            valid.copy(category = " \n"),
            valid.copy(summary = "\t"),
            valid.copy(actions = emptyList()),
            valid.copy(actions = listOf(action, action)),
            valid.copy(actions = listOf(action.copy(id = "refund"))),
            valid.copy(actions = listOf(action.copy(label = " "))),
            valid.copy(summary = "x".repeat(1_501)),
        )
        malformed.forEach { result ->
            val engine = RecordingEngine().apply { analysisResult = Result.success(result) }
            val replies = ready(engine)
            replies.analyze()
            advanceUntilIdle()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertNull(replies.state.value.analysis)
            assertFalse(replies.state.value.canCopy)
            replies.draft(action.id, ReplyTone.PROFESSIONAL)
            assertTrue(engine.draftRequests.isEmpty())
        }
    }

    @Test
    fun malformedDraftsAreNotPublishedAndCanRetry() = runTest {
        listOf("", " \n\t", "x".repeat(1_501)).forEach { output ->
            val engine = RecordingEngine().apply { draftResult = Result.success(output) }
            val replies = choosing(engine)
            replies.draft(action.id, ReplyTone.PROFESSIONAL)
            advanceUntilIdle()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            assertEquals("", replies.state.value.draft)
            assertFalse(replies.state.value.canCopy)
            replies.copied()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
            replies.recover()
            assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
            engine.draftResult = Result.success("Synthetic valid retry")
            replies.draft(action.id, ReplyTone.PROFESSIONAL)
            advanceUntilIdle()
            assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        }
    }

    @Test
    fun initializationAndDraftErrorsDoNotExposeExceptionContent() = runTest {
        val privateError = IllegalStateException("PRIVATE SYNTHETIC CONTENT")
        val failedInitialization = RecordingEngine().apply { initializationResult = Result.failure(privateError) }
        val unavailable = coordinator(failedInitialization)
        unavailable.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, unavailable.state.value.phase)
        assertFalse(unavailable.state.value.notice!!.contains(privateError.message!!))
        val failedDraft = RecordingEngine().apply { draftResult = Result.failure(privateError) }
        val replies = choosing(failedDraft)
        replies.draft(action.id, ReplyTone.PROFESSIONAL)
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertFalse(replies.state.value.notice!!.contains(privateError.message!!))
        assertFalse(replies.state.value.canCopy)
    }

    @Test
    fun boundaryMessageReachesEngineAndOversizedReplacementIsRejected() = runTest {
        val engine = RecordingEngine()
        val replies = ready(engine)
        val boundary = "x".repeat(1_500)
        replies.setMessage(boundary)
        replies.setMessage("x".repeat(1_501))
        assertEquals(boundary, replies.state.value.message)
        assertEquals("Message exceeds 1,500 characters.", replies.state.value.notice)
        replies.analyze()
        advanceUntilIdle()
        assertEquals(AnalyzeRequest(boundary), engine.analysisRequests.single())
    }

    @Test
    fun oversizedEditPreservesDraftAndChangingMessageClearsCopyableOutput() = runTest {
        val engine = RecordingEngine()
        val replies = choosing(engine)
        replies.draft(action.id, ReplyTone.PROFESSIONAL)
        advanceUntilIdle()
        val boundary = "x".repeat(1_500)
        replies.editDraft(boundary)
        replies.editDraft("x".repeat(1_501))
        assertEquals(boundary, replies.state.value.draft)
        replies.copied()
        assertEquals(ReplyPhase.Copied, replies.state.value.phase)
        replies.setMessage("Replacement synthetic message")
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        assertNull(replies.state.value.analysis)
        assertEquals("", replies.state.value.draft)
        assertFalse(replies.state.value.canCopy)
    }
}
