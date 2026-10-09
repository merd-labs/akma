package ph.merd.akma.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionSafetyTest {
    private fun analysis(
        category: String = "interview_invitation",
        actions: List<SuggestedAction> = listOf(SuggestedAction("accept", "Decline")),
        summary: String = "Synthetic summary",
        source: AnalysisSource = AnalysisSource.LOCAL_MODEL,
    ) = AnalysisResult(category, summary, false, actions, source)

    private fun rejected(result: Result<*>, reason: String) {
        assertTrue(result.isFailure)
        assertEquals(reason, result.exceptionOrNull()?.message)
    }

    @Test
    fun replacesDeceptiveLabelsWithoutChangingActionMeaning() {
        val result = ReplyValidation.normalize(analysis()).getOrThrow()
        assertEquals(listOf(SuggestedAction("accept", "Accept")), result.actions)
        assertTrue(result.requiresUserDecision)
        val decline = ReplyValidation.normalize(analysis(actions = listOf(SuggestedAction("decline", "Accept")))).getOrThrow()
        assertEquals(listOf(SuggestedAction("decline", "Decline politely")), decline.actions)
    }

    @Test
    fun ignoresEmptyOversizedAndMaliciousModelLabels() {
        listOf("", "x".repeat(10_000), "Decline\u202E\u0000").forEach { label ->
            val result = ReplyValidation.normalize(analysis(actions = listOf(SuggestedAction("accept", label)))).getOrThrow()
            assertEquals(SuggestedAction("accept", "Accept"), result.actions.single())
        }
    }

    @Test
    fun rejectsFourReviewedActionsBeforeAnyTruncation() {
        rejected(ReplyValidation.normalize(analysis(actions = ActionCatalog.actionsFor("interview_invitation"))), "Too many actions.")
        assertEquals(3, ReplyValidation.MAX_ACTIONS)
    }

    @Test
    fun rejectsUnknownDuplicateAndCategoryIncompatibleActions() {
        listOf("refund", "ACCEPT", " accept", "acc\u202Eept", "offer_fix").forEach { id ->
            rejected(ReplyValidation.normalize(analysis(actions = listOf(SuggestedAction(id, "Accept")))), "Unsupported action.")
        }
        val action = SuggestedAction("accept", "Accept")
        rejected(ReplyValidation.normalize(analysis(actions = listOf(action, action))), "Duplicate action.")
        rejected(ReplyValidation.normalize(analysis(actions = emptyList())), "No available actions.")
    }

    @Test
    fun callerAllowlistCanNarrowButNeverExtendCatalog() {
        rejected(ReplyValidation.normalize(analysis(), setOf("decline")), "Unsupported action.")
        rejected(ReplyValidation.normalize(analysis(actions = listOf(SuggestedAction("refund", "Refund"))), setOf("refund")), "Unsupported action.")
        assertTrue(ReplyValidation.normalize(analysis(), setOf("accept")).isSuccess)
    }

    @Test
    fun allSevenCatalogsHaveReviewedLabelsAndValidDisplayedSubsets() {
        val expected = mapOf(
            "interview_invitation" to listOf("accept" to "Accept", "reschedule" to "Reschedule", "clarify" to "Ask for details", "decline" to "Decline politely"),
            "meeting" to listOf("confirm" to "Confirm", "suggest_time" to "Suggest another time", "ask_agenda" to "Ask for agenda", "decline" to "Decline politely"),
            "reschedule_request" to listOf("agree_new_time" to "Agree to new time", "suggest_alternative" to "Suggest alternative", "ask_reason" to "Ask why"),
            "follow_up" to listOf("give_update" to "Give update", "request_more_time" to "Ask for more time", "thank_confirm" to "Thank and confirm"),
            "complaint" to listOf("apologize" to "Apologize", "explain" to "Explain", "offer_fix" to "Offer a fix"),
            "casual" to listOf("reply_warmly" to "Reply warmly", "catch_up" to "Catch up", "keep_short" to "Keep it short"),
            "other" to listOf("acknowledge" to "Acknowledge", "ask_to_clarify" to "Ask to clarify", "respond_briefly" to "Respond briefly"),
        )
        assertEquals(expected.keys, ActionCatalog.categoryIds)
        expected.forEach { (category, actions) ->
            assertEquals(actions.map { SuggestedAction(it.first, it.second) }, ActionCatalog.actionsFor(category))
            // Verify every candidate, including fourth candidates, can appear in a valid one-action result.
            actions.forEach { (id, label) ->
                val normalized = ReplyValidation.normalize(analysis(category, listOf(SuggestedAction(id, "Untrusted label")))).getOrThrow()
                assertEquals(listOf(SuggestedAction(id, label)), normalized.actions)
            }
            assertTrue(ReplyValidation.normalize(analysis(category, ActionCatalog.actionsFor(category).take(3))).isSuccess)
        }
    }

    @Test
    fun supportsOnlyDocumentedAliasAndSanitizedCategoryIds() {
        assertEquals("interview_invitation", ReplyValidation.normalize(analysis(category = " \ninvitation\u202C ")).getOrThrow().category)
        listOf("Invitation", "not_a_category", "meeting or schedule request", "other fake system status").forEach { category ->
            rejected(ReplyValidation.normalize(analysis(category = category)), "Unsupported category.")
        }
        assertTrue(ReplyValidation.normalize(analysis(category = " ")).isFailure)
    }

    @Test
    fun explicitOtherFallbackIsDeterministicAndDoesNotRescueInvalidOutput() {
        val fallback = ReplyValidation.normalize(ActionCatalog.otherAnalysis()).getOrThrow()
        assertEquals("other", fallback.category)
        assertEquals(AnalysisSource.DETERMINISTIC, fallback.source)
        assertEquals(listOf("acknowledge", "ask_to_clarify", "respond_briefly"), fallback.actions.map { it.id })
        assertTrue(fallback.summary.startsWith("Deterministic analysis;"))
        rejected(ReplyValidation.normalize(analysis(category = "unknown")), "Unsupported category.")
        rejected(ReplyValidation.normalize(analysis(category = "other")), "Unsupported action.")
    }

    @Test
    fun provenanceMustBeExplicitAndIsDisplayedWithoutClaimingCatalogInference() {
        rejected(ReplyValidation.normalize(analysis(source = AnalysisSource.UNSPECIFIED)), "Analysis source is unsupported.")
        val model = ReplyValidation.normalize(analysis()).getOrThrow()
        assertTrue(model.summary.startsWith("Model summary (untrusted); actions from local catalog: "))
        assertEquals(AnalysisSource.LOCAL_MODEL, model.source)
    }

    @Test
    fun sanitizesSummaryControlsBidiAndUnicodeWhitespace() {
        val input = " \u0000Synthetic\t\u202Esummary\u202C\n\u2066text\u2069\u200F\u00A0 end\r "
        val result = ReplyValidation.normalize(analysis(summary = input)).getOrThrow()
        assertEquals("Model summary (untrusted); actions from local catalog: Synthetic summary text end", result.summary)
        assertFalse(result.summary.any { Character.getType(it) in listOf(Character.CONTROL.toInt(), Character.FORMAT.toInt()) })
    }

    @Test
    fun truncatesSummaryAt200CodePointsWithoutSplittingEmoji() {
        val result = ReplyValidation.normalize(analysis(summary = "x".repeat(199) + "😀" + "tail")).getOrThrow()
        val content = result.summary.substringAfter("catalog: ")
        assertEquals(200, content.codePointCount(0, content.length))
        assertEquals("x".repeat(199) + "😀", content)
    }

    @Test
    fun rejectsOversizedEmptyAfterSanitizationAndMalformedDisplayText() {
        rejected(ReplyValidation.normalize(analysis(summary = "x".repeat(1_501))), "Analysis exceeds the text limit.")
        rejected(ReplyValidation.normalize(analysis(category = "x".repeat(1_501))), "Analysis exceeds the text limit.")
        rejected(ReplyValidation.normalize(analysis(summary = "\u202E\u2066\u0000")), "Analysis is incomplete.")
        rejected(ReplyValidation.normalize(analysis(summary = "bad\uD800")), "Analysis text is malformed.")
        rejected(ReplyValidation.normalize(analysis(category = "bad\uDFFF")), "Analysis text is malformed.")
    }

    @Test
    fun draftRequestsRequireKnownAvailableIdsAndValidInput() {
        val actions = listOf(SuggestedAction("accept", "Accept"))
        val original = AnalyzeRequest("Synthetic message")
        assertTrue(ReplyValidation.validate(DraftRequest(original, "decline", ReplyTone.FRIENDLY), actions).isFailure)
        assertTrue(ReplyValidation.validate(DraftRequest(original, "refund", ReplyTone.FRIENDLY), listOf(SuggestedAction("refund", "Refund"))).isFailure)
        assertTrue(ReplyValidation.validate(DraftRequest(original.copy(message = ""), "accept", ReplyTone.FRIENDLY), actions).isFailure)
    }
}
