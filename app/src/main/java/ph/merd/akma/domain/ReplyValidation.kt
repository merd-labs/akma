package ph.merd.akma.domain

/** Shared validation for future engine adapters and UI callers. No user text is logged. */
object ReplyValidation {
    const val MAX_TEXT_LENGTH = 1_500
    const val MAX_ACTIONS = 4

    fun validate(request: AnalyzeRequest): Result<Unit> = runCatching {
        require(request.message.isNotBlank()) { "Message must not be blank." }
        require(request.message.length <= MAX_TEXT_LENGTH) { "Message exceeds the text limit." }
        require(request.history.length <= MAX_TEXT_LENGTH) { "History exceeds the text limit." }
        require((request.relationship?.length ?: 0) <= MAX_TEXT_LENGTH) { "Relationship exceeds the text limit." }
    }

    /** The caller supplies reviewed action IDs, not IDs taken from untrusted model output. */
    fun validate(analysis: AnalysisResult, allowedActionIds: Set<String>): Result<Unit> = runCatching {
        require(analysis.category.isNotBlank() && analysis.summary.isNotBlank()) { "Analysis is incomplete." }
        require(analysis.category.length <= MAX_TEXT_LENGTH && analysis.summary.length <= MAX_TEXT_LENGTH) {
            "Analysis exceeds the text limit."
        }
        require(analysis.actions.isNotEmpty()) { "No available actions." }
        require(analysis.actions.size <= MAX_ACTIONS) { "Too many actions." }
        require(analysis.actions.all { it.id.isNotBlank() && it.id in allowedActionIds }) { "Unsupported action." }
        require(analysis.actions.all { it.label.isNotBlank() && it.label.length <= MAX_TEXT_LENGTH }) {
            "Invalid action label."
        }
        require(analysis.actions.map { it.id }.distinct().size == analysis.actions.size) { "Duplicate action." }
    }

    fun validate(request: DraftRequest, actions: List<SuggestedAction>): Result<Unit> = runCatching {
        validate(request.original).getOrThrow()
        require(request.selectedActionId.isNotBlank() && actions.any { it.id == request.selectedActionId }) {
            "Select an available action."
        }
        require(request.userInstruction.length <= MAX_TEXT_LENGTH) { "Instruction exceeds the text limit." }
    }

    fun validateDraft(draft: String): Result<Unit> = runCatching {
        require(draft.isNotBlank()) { "Draft must not be blank." }
        require(draft.length <= MAX_TEXT_LENGTH) { "Draft exceeds the text limit." }
    }
}
