package ph.merd.akma.domain

/** Shared validation for future engine adapters and UI callers. No user text is logged. */
object ReplyValidation {
    const val MAX_TEXT_LENGTH = 1_500
    const val MAX_ACTIONS = 3
    const val MAX_SUMMARY_CODE_POINTS = 200

    fun validate(request: AnalyzeRequest): Result<Unit> = runCatching {
        require(request.message.isNotBlank()) { "Message must not be blank." }
        require(request.message.length <= MAX_TEXT_LENGTH) { "Message exceeds the text limit." }
        require(request.history.length <= MAX_TEXT_LENGTH) { "History exceeds the text limit." }
        require((request.relationship?.length ?: 0) <= MAX_TEXT_LENGTH) { "Relationship exceeds the text limit." }
    }

    fun validate(analysis: AnalysisResult, allowedActionIds: Set<String> = ActionCatalog.actionIds): Result<Unit> =
        normalize(analysis, allowedActionIds).map { Unit }

    /** Only this normalized result may enter display state. Model labels are discarded entirely. */
    fun normalize(analysis: AnalysisResult, allowedActionIds: Set<String> = ActionCatalog.actionIds): Result<AnalysisResult> = runCatching {
        require(analysis.category.isNotBlank() && analysis.summary.isNotBlank()) { "Analysis is incomplete." }
        require(analysis.category.length <= MAX_TEXT_LENGTH && analysis.summary.length <= MAX_TEXT_LENGTH) {
            "Analysis exceeds the text limit."
        }
        require(analysis.actions.isNotEmpty()) { "No available actions." }
        require(analysis.actions.size <= MAX_ACTIONS) { "Too many actions." }
        require(analysis.actions.map { it.id }.distinct().size == analysis.actions.size) { "Duplicate action." }
        require(analysis.source != AnalysisSource.UNSPECIFIED) { "Analysis source is unsupported." }
        val category = ActionCatalog.canonicalCategory(sanitizeText(analysis.category, removeControls = true))
        require(category != null) { "Unsupported category." }
        val categoryIds = ActionCatalog.actionsFor(category).map { it.id }.toSet()
        require(analysis.actions.all { it.id in categoryIds && it.id in allowedActionIds }) { "Unsupported action." }
        val summary = sanitizeText(analysis.summary)
        require(summary.isNotBlank()) { "Analysis is incomplete." }
        val end = summary.offsetByCodePoints(0, minOf(summary.codePointCount(0, summary.length), MAX_SUMMARY_CODE_POINTS))
        val prefix = when (analysis.source) {
            AnalysisSource.LOCAL_MODEL -> "Model summary (untrusted); actions from local catalog: "
            AnalysisSource.HYBRID -> "Model purpose (untrusted); category from keywords; actions from local catalog: "
            AnalysisSource.DETERMINISTIC -> "Deterministic analysis; actions from local catalog: "
            AnalysisSource.UNSPECIFIED -> error("Analysis source is unsupported.")
        }
        analysis.copy(
            category = category,
            summary = prefix + summary.substring(0, end).trimEnd(),
            requiresUserDecision = true,
            actions = analysis.actions.map { ActionCatalog.action(it.id)!! },
        )
    }

    fun validate(request: DraftRequest, actions: List<SuggestedAction>): Result<Unit> = runCatching {
        validate(request.original).getOrThrow()
        require(ActionCatalog.action(request.selectedActionId) != null && actions.any { it.id == request.selectedActionId }) {
            "Select an available action."
        }
        require(request.userInstruction.length <= MAX_TEXT_LENGTH) { "Instruction exceeds the text limit." }
    }

    fun validateDraft(draft: String): Result<Unit> = runCatching {
        require(draft.isNotBlank()) { "Draft must not be blank." }
        require(draft.length <= MAX_TEXT_LENGTH) { "Draft exceeds the text limit." }
    }

    /** Treat display text as plain text. Remove format controls and collapse controls/whitespace. */
    private fun sanitizeText(text: String, removeControls: Boolean = false): String = buildString {
        var index = 0
        var pendingSpace = false
        while (index < text.length) {
            val point = text.codePointAt(index)
            index += Character.charCount(point)
            require(point !in 0xD800..0xDFFF) { "Analysis text is malformed." }
            val type = Character.getType(point)
            when {
                type == Character.FORMAT.toInt() || (removeControls && type == Character.CONTROL.toInt()) -> Unit
                type == Character.CONTROL.toInt() || Character.isWhitespace(point) || Character.isSpaceChar(point) -> {
                    if (isNotEmpty()) pendingSpace = true
                }
                else -> {
                    if (pendingSpace) append(' ')
                    appendCodePoint(point)
                    pendingSpace = false
                }
            }
        }
    }
}
