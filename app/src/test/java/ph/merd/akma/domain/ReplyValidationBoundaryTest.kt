package ph.merd.akma.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyValidationBoundaryTest {
    private val action = SuggestedAction("clarify", "Ask for details")
    private val analysis = AnalysisResult("invitation", "Synthetic summary", true, listOf(action))
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
    fun acceptsAnalysisFieldsAndLabelAt1500Characters() {
        val boundary = "x".repeat(1_500)
        val result = analysis.copy(category = boundary, summary = boundary, actions = listOf(action.copy(label = boundary)))
        assertTrue(ReplyValidation.validate(result, setOf(action.id)).isSuccess)
    }

    @Test
    fun rejectsOversizedAnalysisFieldsAndLabel() {
        val oversized = "x".repeat(1_501)
        listOf(analysis.copy(category = oversized), analysis.copy(summary = oversized)).forEach {
            assertRejected(ReplyValidation.validate(it, setOf(action.id)), "Analysis exceeds the text limit.")
        }
        assertRejected(
            ReplyValidation.validate(analysis.copy(actions = listOf(action.copy(label = oversized))), setOf(action.id)),
            "Invalid action label.",
        )
    }

    @Test
    fun rejectsWhitespaceAnalysisFieldsAndLabel() {
        listOf(analysis.copy(category = " \t"), analysis.copy(summary = "\n")).forEach {
            assertRejected(ReplyValidation.validate(it, setOf(action.id)), "Analysis is incomplete.")
        }
        assertRejected(
            ReplyValidation.validate(analysis.copy(actions = listOf(action.copy(label = " \n"))), setOf(action.id)),
            "Invalid action label.",
        )
    }

    @Test
    fun acceptsThreeDistinctReviewedActions() {
        val actions = listOf("clarify", "accept", "decline").map { SuggestedAction(it, "Synthetic $it") }
        assertTrue(ReplyValidation.validate(analysis.copy(actions = actions), actions.map { it.id }.toSet()).isSuccess)
    }

    @Test
    fun requestedThreeActionCapRejectsFourReviewedActions() {
        // Product regression: current source permits four. Keep this failure visible until owner reconciliation.
        val actions = listOf("clarify", "accept", "decline", "acknowledge").map { SuggestedAction(it, "Synthetic $it") }
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
