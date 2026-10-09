package ph.merd.akma.demo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ReplyValidation
import ph.merd.akma.ui.RefineKind

class DemoReplyEngineTest {
    private val engine = DemoReplyEngine(analyzeDelayMillis = 0, draftDelayMillis = 0)
    private val request = AnalyzeRequest("Good afternoon! We'd like to invite you for an interview.")

    @Test
    fun demoAnalysisIsDeterministicAndPassesValidation() = runBlocking {
        val analysis = engine.analyze(request).getOrThrow()
        assertEquals(AnalysisSource.DETERMINISTIC, analysis.source)
        val normalized = ReplyValidation.normalize(analysis).getOrThrow()
        assertEquals("interview_invitation", normalized.category)
        assertEquals(listOf("Accept", "Reschedule", "Ask for details"), normalized.actions.map { it.label })
    }

    @Test
    fun draftFollowsActionToneAndRefine() = runBlocking {
        val accept = DraftRequest(request, "accept", ReplyTone.PROFESSIONAL)
        assertTrue(engine.draft(accept).getOrThrow().startsWith("Good afternoon! Thank you for the invitation."))
        assertEquals("Thank you! Friday at 2:00 PM works for me.", engine.draft(accept.copy(userInstruction = DemoReplies.SHORTER)).getOrThrow())
        assertTrue(engine.draft(accept.copy(userInstruction = DemoReplies.MORE_FORMAL)).getOrThrow().contains("pleased to confirm"))
    }

    @Test
    fun refineInstructionsMatchTheUi() {
        assertEquals(DemoReplies.SHORTER, RefineKind.Shorter.instruction)
        assertEquals(DemoReplies.MORE_FORMAL, RefineKind.MoreFormal.instruction)
    }

    @Test
    fun unknownCombinationGetsALabelledFallback() {
        assertTrue(DemoReplies.reply("decline", ReplyTone.FRIENDLY).endsWith("(Demo reply)"))
    }
}
