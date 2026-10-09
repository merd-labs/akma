package ph.merd.akma.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyValidationTest {
    private val action = SuggestedAction("reschedule", "Request reschedule")

    @Test
    fun rejectsBlankMessage() {
        assertTrue(ReplyValidation.validate(AnalyzeRequest(" \n\t")).isFailure)
    }

    @Test
    fun acceptsMessageAtLimit() {
        assertTrue(ReplyValidation.validate(AnalyzeRequest("x".repeat(1_500))).isSuccess)
    }

    @Test
    fun rejectsMessageOverLimit() {
        assertTrue(ReplyValidation.validate(AnalyzeRequest("x".repeat(1_501))).isFailure)
    }

    @Test
    fun rejectsOversizedContextAndInstruction() {
        val oversized = "x".repeat(1_501)
        assertTrue(ReplyValidation.validate(AnalyzeRequest("Hello", history = oversized)).isFailure)
        assertTrue(ReplyValidation.validate(AnalyzeRequest("Hello", relationship = oversized)).isFailure)
        assertTrue(ReplyValidation.validate(draftRequest().copy(userInstruction = oversized), listOf(action)).isFailure)
    }

    @Test
    fun acceptsThreeCatalogActions() {
        val actions = listOf("accept", "reschedule", "clarify").map { ActionCatalog.action(it)!! }
        assertTrue(ReplyValidation.validate(analysis(actions), actions.map { it.id }.toSet()).isSuccess)
    }

    @Test
    fun rejectsFourReviewedActions() {
        val actions = ActionCatalog.actionsFor("interview_invitation")
        assertTrue(ReplyValidation.validate(analysis(actions), actions.map { it.id }.toSet()).isFailure)
    }

    @Test
    fun rejectsUnreviewedOrDuplicateActions() {
        assertTrue(ReplyValidation.validate(analysis(listOf(action)), setOf("acknowledge")).isFailure)
        assertTrue(ReplyValidation.validate(analysis(listOf(action, action)), setOf(action.id)).isFailure)
        assertTrue(ReplyValidation.validate(analysis(listOf(SuggestedAction("", "Invalid"))), setOf("")).isFailure)
    }

    @Test
    fun acceptsAvailableSelectedAction() {
        assertTrue(ReplyValidation.validate(draftRequest(), listOf(action)).isSuccess)
    }

    @Test
    fun rejectsUnknownOrBlankSelectedAction() {
        assertTrue(ReplyValidation.validate(draftRequest().copy(selectedActionId = "refund"), listOf(action)).isFailure)
        assertTrue(ReplyValidation.validate(draftRequest().copy(selectedActionId = ""), listOf(action)).isFailure)
    }

    @Test
    fun rejectsDraftRequestWithBlankOriginal() {
        assertTrue(ReplyValidation.validate(draftRequest().copy(original = AnalyzeRequest("")), listOf(action)).isFailure)
    }

    @Test
    fun rejectsBlankOrOversizedDraft() {
        assertTrue(ReplyValidation.validateDraft(" \n").isFailure)
        assertTrue(ReplyValidation.validateDraft("x".repeat(1_501)).isFailure)
    }

    @Test
    fun acceptsNonblankDraftAtLimit() {
        assertTrue(ReplyValidation.validateDraft("x".repeat(1_500)).isSuccess)
    }

    @Test
    fun rejectsEmptyOrIncompleteAnalysis() {
        assertTrue(ReplyValidation.validate(analysis(emptyList()), setOf(action.id)).isFailure)
        assertTrue(ReplyValidation.validate(analysis(listOf(action)).copy(summary = ""), setOf(action.id)).isFailure)
        assertTrue(ReplyValidation.validate(analysis(listOf(action.copy(label = ""))), setOf(action.id)).isSuccess) // Labels come from the catalog.
    }

    private fun draftRequest() = DraftRequest(AnalyzeRequest("Are you free Friday at 10?"), action.id, ReplyTone.PROFESSIONAL)

    private fun analysis(actions: List<SuggestedAction>) = AnalysisResult("invitation", "Synthetic test", true, actions, AnalysisSource.DETERMINISTIC)
}
