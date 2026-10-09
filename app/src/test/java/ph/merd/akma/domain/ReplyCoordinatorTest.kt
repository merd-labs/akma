package ph.merd.akma.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyCoordinatorTest {
    // Synthetic engine exists only in tests. Production never returns fixture drafts.
    private class FixtureEngine : LocalReplyEngine {
        var analyses = 0
        var drafts = 0
        var analysisDelay = 0L
        var analysisResult = Result.success(AnalysisResult("invitation", "Synthetic summary", true, listOf(SuggestedAction("reschedule", "Request reschedule")), AnalysisSource.DETERMINISTIC))
        var draftResult = Result.success("Synthetic test draft")
        override suspend fun initialize() = Result.success(Unit)
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> {
            analyses++
            delay(analysisDelay)
            return analysisResult
        }
        override suspend fun draft(request: DraftRequest): Result<String> {
            drafts++
            delay(20)
            return draftResult
        }
    }

    @Test
    fun unavailableEngineNeverProducesFixtures() = runTest {
        val engine = UnavailableReplyEngine()
        assertTrue(engine.initialize().isFailure)
        assertTrue(engine.analyze(AnalyzeRequest("Synthetic input")).isFailure)
        assertTrue(engine.draft(DraftRequest(AnalyzeRequest("Synthetic input"), "reschedule", ReplyTone.PROFESSIONAL)).isFailure)
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
        assertEquals("", replies.state.value.draft)
        replies.analyze()
        assertEquals(ReplyPhase.ModelUnavailable, replies.state.value.phase)
    }

    @Test
    fun validWorkflowRequiresActionAndExplicitCopyAcknowledgement() = runTest {
        val engine = FixtureEngine()
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        assertEquals(ReplyPhase.Analyzing, replies.state.value.phase)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        replies.draft("reschedule", ReplyTone.PROFESSIONAL)
        replies.state.value.pendingConfirmation?.let { replies.confirmDraft(it.id) }
        advanceUntilIdle()
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        replies.editDraft("Edited synthetic test draft")
        assertTrue(replies.state.value.canCopy)
        replies.copied()
        assertEquals(ReplyPhase.Copied, replies.state.value.phase)
        replies.editDraft("")
        assertFalse(replies.state.value.canCopy)
        assertEquals(1, engine.drafts)
    }

    @Test
    fun actionTapStagesDraftUntilSeparateConfirmation() = runTest {
        val engine = FixtureEngine()
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        advanceUntilIdle()

        replies.draft("reschedule", ReplyTone.PROFESSIONAL)
        val pending = requireNotNull(replies.state.value.pendingConfirmation)
        replies.draft("reschedule", ReplyTone.PROFESSIONAL)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals(0, engine.drafts)
        assertEquals(pending, replies.state.value.pendingConfirmation)

        replies.confirmDraft(pending.id)
        advanceUntilIdle()
        assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        assertEquals("Synthetic test draft", replies.state.value.draft)
        assertEquals(1, engine.drafts)
    }

    @Test
    fun duplicateClicksDoNotStartMoreEngineCalls() = runTest {
        val engine = FixtureEngine().apply { analysisDelay = 100 }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        replies.analyze()
        replies.initialize()
        advanceUntilIdle()
        assertEquals(1, engine.analyses)
        replies.draft("reschedule", ReplyTone.PROFESSIONAL)
        replies.state.value.pendingConfirmation?.let { replies.confirmDraft(it.id) }
        replies.draft("reschedule", ReplyTone.PROFESSIONAL)
        replies.state.value.pendingConfirmation?.let { replies.confirmDraft(it.id) }
        advanceUntilIdle()
        assertEquals(1, engine.drafts)
    }

    @Test
    fun cancellationPreventsLateResultAndAllowsRetry() = runTest {
        val engine = FixtureEngine().apply { analysisDelay = 1_000 }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        runCurrent()
        replies.cancel()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        advanceUntilIdle()
        assertEquals(null, replies.state.value.analysis)
        engine.analysisDelay = 0
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals(2, engine.analyses)
    }

    @Test
    fun timeoutIsRecoverableAndDoesNotExposePartialOutput() = runTest {
        val engine = FixtureEngine().apply { analysisDelay = 1_000 }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler), timeoutMillis = 100)
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        advanceTimeBy(101)
        runCurrent()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertTrue(replies.state.value.notice!!.contains("timed out"))
        assertEquals(null, replies.state.value.analysis)
        replies.recover()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
        engine.analysisDelay = 0
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
    }

    @Test
    fun invalidInputNeverReachesEngine() = runTest {
        val engine = FixtureEngine()
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.analyze()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertEquals(0, engine.analyses)
        replies.setMessage("x".repeat(1_501))
        assertEquals("", replies.state.value.message)
        replies.setMessage("Synthetic input")
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
    }

    @Test
    fun unapprovedActionAndMalformedAnalysisFailVisibly() = runTest {
        val engine = FixtureEngine().apply {
            analysisResult = Result.success(AnalysisResult("invitation", "Synthetic summary", true, listOf(SuggestedAction("refund", "Promise refund")), AnalysisSource.DETERMINISTIC))
        }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic input")
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertEquals(null, replies.state.value.analysis)
        engine.analysisResult = Result.success(AnalysisResult("", "", true, emptyList()))
        replies.recover()
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
    }

    @Test
    fun invalidSelectedActionAndBlankDraftCannotBeCopied() = runTest {
        val engine = FixtureEngine().apply { draftResult = Result.success(" ") }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic input")
        replies.analyze()
        advanceUntilIdle()
        replies.draft("refund", ReplyTone.PROFESSIONAL)
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertEquals(0, engine.drafts)
        replies.recover()
        replies.draft("reschedule", ReplyTone.PROFESSIONAL)
        replies.state.value.pendingConfirmation?.let { replies.confirmDraft(it.id) }
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertFalse(replies.state.value.canCopy)
    }

    @Test
    fun engineErrorsCannotLeakPromptText() = runTest {
        val engine = FixtureEngine().apply {
            analysisResult = Result.failure(IllegalStateException("PRIVATE SYNTHETIC CONTENT"))
        }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic input")
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertFalse(replies.state.value.notice!!.contains("PRIVATE"))
    }

    @Test
    fun repeatedFailureStillRecoversToReady() = runTest {
        val engine = FixtureEngine().apply { analysisResult = Result.failure(IllegalStateException("Synthetic error")) }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic input")
        repeat(2) {
            replies.analyze()
            advanceUntilIdle()
            assertEquals(ReplyPhase.Error, replies.state.value.phase)
        }
        replies.recover()
        assertEquals(ReplyPhase.Ready, replies.state.value.phase)
    }

    @Test
    fun messageCannotChangeDuringProcessing() = runTest {
        val engine = FixtureEngine().apply { analysisDelay = 100 }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Original synthetic input")
        replies.analyze()
        replies.setMessage("Replacement synthetic input")
        assertEquals("Original synthetic input", replies.state.value.message)
        advanceUntilIdle()
    }
}
