package ph.merd.akma.domain

/** Reviewed local meanings and labels. This catalog does not classify text or perform inference. */
object ActionCatalog {
    private val labels = mapOf(
        "accept" to "Accept",
        "reschedule" to "Reschedule",
        "clarify" to "Ask for details",
        "decline" to "Decline politely",
        "confirm" to "Confirm",
        "suggest_time" to "Suggest another time",
        "ask_agenda" to "Ask for agenda",
        "agree_new_time" to "Agree to new time",
        "suggest_alternative" to "Suggest alternative",
        "ask_reason" to "Ask why",
        "give_update" to "Give update",
        "request_more_time" to "Ask for more time",
        "thank_confirm" to "Thank and confirm",
        "apologize" to "Apologize",
        "explain" to "Explain",
        "offer_fix" to "Offer a fix",
        "reply_warmly" to "Reply warmly",
        "catch_up" to "Catch up",
        "keep_short" to "Keep it short",
        "acknowledge" to "Acknowledge",
        "ask_to_clarify" to "Ask to clarify",
        "respond_briefly" to "Respond briefly",
    )

    private val categories = mapOf(
        "interview_invitation" to listOf("accept", "reschedule", "clarify", "decline"),
        "meeting" to listOf("confirm", "suggest_time", "ask_agenda", "decline"),
        "reschedule_request" to listOf("agree_new_time", "suggest_alternative", "ask_reason"),
        "follow_up" to listOf("give_update", "request_more_time", "thank_confirm"),
        "complaint" to listOf("apologize", "explain", "offer_fix"),
        "casual" to listOf("reply_warmly", "catch_up", "keep_short"),
        "other" to listOf("acknowledge", "ask_to_clarify", "respond_briefly"),
    )

    val actionIds: Set<String> get() = labels.keys.toSet()
    val categoryIds: Set<String> get() = categories.keys.toSet()

    fun canonicalCategory(category: String): String? = when {
        category == "invitation" -> "interview_invitation"
        category in categories -> category
        else -> null
    }

    fun action(id: String): SuggestedAction? = labels[id]?.let { SuggestedAction(id, it) }

    /** A category's candidate pool may contain four entries; displayed results may contain only three. */
    fun actionsFor(category: String): List<SuggestedAction> =
        categories[canonicalCategory(category)].orEmpty().map { action(it)!! }

    /** Explicit fallback only. Callers must disclose that these are deterministic presets. */
    fun otherAnalysis(): AnalysisResult = AnalysisResult(
        category = "other",
        summary = "Choose how to respond to this message.",
        requiresUserDecision = true,
        actions = actionsFor("other"),
        source = AnalysisSource.DETERMINISTIC,
    )
}
