package ph.merd.akma.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyValidationBoundaryTest {
    private val action = SuggestedAction("clarify", "Ask for details")
    private val analysis = AnalysisResult("invitation", "Synthetic summary", true, listOf(action), AnalysisSource.DETERMINISTIC)
    private val original = AnalyzeRequest("Synthetic message")
    private val draft = DraftRequest(original, action.id, ReplyTone.PROFESSIONAL)

    private fun assertRejected(result: Result<Unit>, reason: String) {
        assertTrue("Expected rejection: $reason", result.isFailure)
        assertEquals(reason, result.exceptionOrNull()?.message)
    }

    @Test
    fun rejectsEmptyAndWhitespaceMessages() {
        listOf("", " ", "\n\t\r").forEach {
            assertRejected(ReplyValidation.validate(original.copy(message = it)), "Message must not be blank.")
        }
    }

    @Test
    fun acceptsContextAndInstructionAt1500Characters() {
        val boundary = "x".repeat(1_500)
        val request = AnalyzeRequest(boundary, history = boundary, relationship = boundary)
        assertTrue(ReplyValidation.validate(request).isSuccess)
        assertTrue(ReplyValidation.validate(draft.copy(original = request, userInstruction = boundary), listOf(action)).isSuccess)
    }

    @Test
    fun rejectsEachInputFieldAt1501Characters() {
        val oversized = "x".repeat(1_501)
        assertRejected(ReplyValidation.validate(original.copy(message = oversized)), "Message exceeds the text limit.")
        assertRejected(ReplyValidation.validate(original.copy(history = oversized)), "History exceeds the text limit.")
        assertRejected(ReplyValidation.validate(original.copy(relationship = oversized)), "Relationship exceeds the text limit.")
        assertRejected(ReplyValidation.validate(draft.copy(userInstruction = oversized), listOf(action)), "Instruction exceeds the text limit.")
    }

    @Test
    fun boundsSummaryAt1500CharactersAndDiscardsUntrustedLabel() {
        val boundary = "x".repeat(1_500)
        val result = ReplyValidation.normalize(analysis.copy(summary = boundary, actions = listOf(action.copy(label = boundary)))).getOrThrow()
        assertEquals("interview_invitation", result.category)
        assertEquals("x".repeat(200), result.summary.substringAfter("catalog: "))
        assertEquals(listOf(action), result.actions)
        assertFalse(result.actions.single().label.contains(boundary))
    }

    @Test
    fun rejectsOversizedAnalysisFieldsAndDiscardsOversizedLabels() {
        val oversized = "x".repeat(1_501)
        listOf(analysis.copy(category = oversized), analysis.copy(summary = oversized)).forEach {
            assertRejected(ReplyValidation.validate(it, setOf(action.id)), "Analysis exceeds the text limit.")
        }
        val result = ReplyValidation.normalize(analysis.copy(actions = listOf(action.copy(label = oversized)))).getOrThrow()
        assertEquals(listOf(action), result.actions)
    }

    @Test
    fun rejectsWhitespaceAnalysisFieldsAndUsesCanonicalLabel() {
        listOf(analysis.copy(category = " \t"), analysis.copy(summary = "\n")).forEach {
            assertRejected(ReplyValidation.validate(it, setOf(action.id)), "Analysis is incomplete.")
        }
        val result = ReplyValidation.normalize(analysis.copy(actions = listOf(action.copy(label = " \n")))).getOrThrow()
        assertEquals(listOf(action), result.actions)
    }

    @Test
    fun acceptsThreeDistinctReviewedActions() {
        val actions = listOf("clarify", "accept", "decline").map { SuggestedAction(it, "Synthetic $it") }
        assertTrue(ReplyValidation.validate(analysis.copy(actions = actions), actions.map { it.id }.toSet()).isSuccess)
    }

    @Test
    fun requestedThreeActionCapRejectsFourReviewedActions() {
        // Four valid interview candidates still exceed the displayed cap; do not silently truncate.
        val actions = listOf("clarify", "accept", "decline", "reschedule").map { SuggestedAction(it, "Synthetic $it") }
        assertRejected(ReplyValidation.validate(analysis.copy(actions = actions), actions.map { it.id }.toSet()), "Too many actions.")
    }

    @Test
    fun rejectsWhitespaceAndCaseChangedSelectedAction() {
        listOf(" ", "CLARIFY", " clarify").forEach {
            assertRejected(ReplyValidation.validate(draft.copy(selectedActionId = it), listOf(action)), "Select an available action.")
        }
    }

    @Test
    fun draftValidationRejectsInvalidOriginalContext() {
        val oversized = "x".repeat(1_501)
        assertRejected(ReplyValidation.validate(draft.copy(original = original.copy(history = oversized)), listOf(action)), "History exceeds the text limit.")
        assertRejected(ReplyValidation.validate(draft.copy(original = original.copy(relationship = oversized)), listOf(action)), "Relationship exceeds the text limit.")
    }
}
