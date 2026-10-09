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
class DraftConfirmationTest {
    // Synthetic test double checks control flow only. It provides no semantic faithfulness evidence.
    private class Engine : LocalReplyEngine {
        var analyses = 0
        val requests = mutableListOf<DraftRequest>()
        var analysis = AnalysisResult("invitation", "Synthetic summary", false, listOf(SuggestedAction("accept", "Decline"), SuggestedAction("decline", "Accept")), AnalysisSource.DETERMINISTIC)
        var draftDelay = 0L
        var nonCooperative = false
        var output: Result<String> = Result.success("Synthetic draft")
        var activeCalls = 0
        var peakCalls = 0

        override suspend fun initialize() = Result.success(Unit)
        override suspend fun analyze(request: AnalyzeRequest): Result<AnalysisResult> {
            analyses++
            peakCalls = maxOf(peakCalls, ++activeCalls)
            activeCalls--
            return Result.success(analysis)
        }
        override suspend fun draft(request: DraftRequest): Result<String> {
            requests += request
            peakCalls = maxOf(peakCalls, ++activeCalls)
            try {
                if (nonCooperative) withContext(NonCancellable) { delay(draftDelay) } else delay(draftDelay)
                return output
            } finally {
                activeCalls--
            }
        }
    }

    private fun TestScope.choosing(engine: Engine, timeout: Long = 60_000): ReplyCoordinator {
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler), timeoutMillis = timeout)
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic invitation")
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        return replies
    }

    @Test
    fun analysisAndSelectionNeverDraftEvenWhenModelSaysNoDecisionRequired() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        assertTrue(replies.state.value.analysis!!.requiresUserDecision)
        assertTrue(engine.requests.isEmpty())
        replies.draft("accept", ReplyTone.FRIENDLY)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals(SuggestedAction("accept", "Accept"), replies.state.value.pendingConfirmation!!.action)
        assertFalse(replies.state.value.canCopy)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun canonicalDeclineConfirmationSendsDeclineAndExactTone() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        ReplyTone.entries.forEach { tone ->
            replies.draft("decline", tone)
            val pending = replies.state.value.pendingConfirmation!!
            assertEquals(SuggestedAction("decline", "Decline politely"), pending.action)
            replies.confirmDraft(pending.id)
            advanceUntilIdle()
            assertEquals(DraftRequest(AnalyzeRequest("Synthetic invitation"), "decline", tone), engine.requests.last())
            assertEquals(ReplyPhase.Editing, replies.state.value.phase)
        }
        assertEquals(3, engine.requests.size)
    }

    @Test
    fun deceptiveAcceptLabelCannotTriggerAnUnconfirmedAccept() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        assertEquals("Accept", replies.state.value.analysis!!.actions.first().label)
        replies.draft("accept", ReplyTone.PROFESSIONAL)
        replies.confirmDraft(-1)
        advanceUntilIdle()
        assertTrue(engine.requests.isEmpty())
        val pending = replies.state.value.pendingConfirmation!!
        replies.confirmDraft(pending.id)
        advanceUntilIdle()
        assertEquals("accept", engine.requests.single().selectedActionId)
    }

    @Test
    fun duplicateSelectionAndConfirmationStartOneEngineCall() = runTest {
        val engine = Engine().apply { draftDelay = 100 }
        val replies = choosing(engine)
        replies.draft("decline", ReplyTone.CONCISE)
        val pending = replies.state.value.pendingConfirmation!!
        replies.draft("decline", ReplyTone.CONCISE)
        assertEquals(pending.id, replies.state.value.pendingConfirmation!!.id)
        repeat(4) { replies.confirmDraft(pending.id) }
        replies.analyze()
        replies.initialize()
        advanceUntilIdle()
        replies.confirmDraft(pending.id)
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)
        assertEquals(1, engine.analyses)
    }

    @Test
    fun replacingSelectionOrToneInvalidatesOldConfirmation() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        replies.draft("accept", ReplyTone.PROFESSIONAL)
        val old = replies.state.value.pendingConfirmation!!
        replies.draft("decline", ReplyTone.FRIENDLY)
        val replacement = replies.state.value.pendingConfirmation!!
        replies.confirmDraft(old.id)
        assertEquals(replacement, replies.state.value.pendingConfirmation)
        assertTrue(engine.requests.isEmpty())
        replies.draft("decline", ReplyTone.CONCISE)
        val latest = replies.state.value.pendingConfirmation!!
        replies.confirmDraft(replacement.id)
        assertTrue(engine.requests.isEmpty())
        replies.confirmDraft(latest.id)
        advanceUntilIdle()
        assertEquals("decline", engine.requests.single().selectedActionId)
        assertEquals(ReplyTone.CONCISE, engine.requests.single().tone)
    }

    @Test
    fun cancellationClearsPendingAndDoesNotDraft() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        replies.draft("decline", ReplyTone.CONCISE)
        val old = replies.state.value.pendingConfirmation!!.id
        replies.cancel()
        replies.confirmDraft(old)
        advanceUntilIdle()
        assertNull(replies.state.value.pendingConfirmation)
        assertTrue(engine.requests.isEmpty())
        replies.draft("decline", ReplyTone.CONCISE)
        val retry = replies.state.value.pendingConfirmation!!.id
        assertTrue(retry > old)
        replies.confirmDraft(retry)
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun messageReplacementReanalysisAndInitializationInvalidateConfirmation() = runTest {
        listOf("message", "analyze", "initialize").forEach { operation ->
            val engine = Engine()
            val replies = choosing(engine)
            replies.draft("accept", ReplyTone.FRIENDLY)
            val old = replies.state.value.pendingConfirmation!!.id
            when (operation) {
                "message" -> replies.setMessage("Replacement synthetic input")
                "analyze" -> replies.analyze()
                "initialize" -> replies.initialize()
            }
            advanceUntilIdle()
            replies.confirmDraft(old)
            advanceUntilIdle()
            assertNull(replies.state.value.pendingConfirmation)
            assertTrue(engine.requests.isEmpty())
        }
    }

    @Test
    fun unavailableSelectionClearsPendingWithoutDrafting() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        replies.draft("accept", ReplyTone.FRIENDLY)
        val old = replies.state.value.pendingConfirmation!!.id
        replies.draft("offer_fix", ReplyTone.FRIENDLY)
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertNull(replies.state.value.pendingConfirmation)
        replies.recover()
        replies.confirmDraft(old)
        advanceUntilIdle()
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun cancellationDuringDraftSuppressesOutputAndRequiresNewConfirmation() = runTest {
        val engine = Engine().apply { draftDelay = 100 }
        val replies = choosing(engine)
        replies.draft("decline", ReplyTone.PROFESSIONAL)
        val old = replies.state.value.pendingConfirmation!!.id
        replies.confirmDraft(old)
        runCurrent()
        replies.cancel()
        advanceUntilIdle()
        replies.confirmDraft(old)
        advanceUntilIdle()
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals("", replies.state.value.draft)
        assertFalse(replies.state.value.canCopy)
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun lateNonCooperativeDraftCannotPublishOrOverlapNewAnalysis() = runTest {
        val engine = Engine().apply { draftDelay = 100; nonCooperative = true }
        val replies = choosing(engine)
        replies.draft("accept", ReplyTone.PROFESSIONAL)
        replies.confirmDraft(replies.state.value.pendingConfirmation!!.id)
        runCurrent()
        replies.cancel()
        replies.setMessage("Replacement synthetic message")
        replies.analyze()
        runCurrent()
        assertEquals(1, engine.analyses)
        advanceTimeBy(100)
        advanceUntilIdle()
        assertEquals(2, engine.analyses)
        assertEquals(1, engine.peakCalls)
        assertEquals("", replies.state.value.draft)
        assertEquals("Replacement synthetic message", replies.state.value.message)
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
    }

    @Test
    fun timeoutConsumesConfirmationAndFailsWithoutPartialDraft() = runTest {
        val engine = Engine().apply { draftDelay = 1_000 }
        val replies = choosing(engine, timeout = 100)
        replies.draft("decline", ReplyTone.PROFESSIONAL)
        val id = replies.state.value.pendingConfirmation!!.id
        replies.confirmDraft(id)
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertTrue(replies.state.value.notice!!.contains("timed out"))
        assertNull(replies.state.value.pendingConfirmation)
        assertFalse(replies.state.value.canCopy)
        replies.recover()
        replies.confirmDraft(id)
        advanceUntilIdle()
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun unavailableAndMalformedDraftsStayUncopyableAfterConfirmation() = runTest {
        val failures = listOf(Result.failure<String>(ModelUnavailableException()), Result.success(" \n"), Result.success("x".repeat(1_501)))
        failures.forEachIndexed { index, output ->
            val engine = Engine().apply { this.output = output }
            val replies = choosing(engine)
            replies.draft("decline", ReplyTone.PROFESSIONAL)
            replies.confirmDraft(replies.state.value.pendingConfirmation!!.id)
            advanceUntilIdle()
            assertEquals(if (index == 0) ReplyPhase.ModelUnavailable else ReplyPhase.Error, replies.state.value.phase)
            assertFalse(replies.state.value.canCopy)
            assertNull(replies.state.value.pendingConfirmation)
            assertEquals("", replies.state.value.draft)
        }
    }

    @Test
    fun unmarkedAnalysisFailsInsteadOfSilentlyBecomingOther() = runTest {
        val engine = Engine().apply { analysis = analysis.copy(source = AnalysisSource.UNSPECIFIED) }
        val replies = ReplyCoordinator(engine, this, StandardTestDispatcher(testScheduler))
        replies.initialize()
        advanceUntilIdle()
        replies.setMessage("Synthetic message")
        replies.analyze()
        advanceUntilIdle()
        assertEquals(ReplyPhase.Error, replies.state.value.phase)
        assertNull(replies.state.value.analysis)
        replies.draft("accept", ReplyTone.PROFESSIONAL)
        assertNull(replies.state.value.pendingConfirmation)
        assertTrue(engine.requests.isEmpty())
    }

    @Test
    fun pendingConfirmationPreventsCopyingPreviousDraft() = runTest {
        val engine = Engine()
        val replies = choosing(engine)
        replies.draft("decline", ReplyTone.PROFESSIONAL)
        replies.confirmDraft(replies.state.value.pendingConfirmation!!.id)
        advanceUntilIdle()
        assertTrue(replies.state.value.canCopy)
        replies.draft("accept", ReplyTone.FRIENDLY)
        replies.copied()
        assertFalse(replies.state.value.canCopy)
        assertEquals(ReplyPhase.ChoosingAction, replies.state.value.phase)
        assertEquals("", replies.state.value.draft)
    }
}
