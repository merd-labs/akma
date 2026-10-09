package ph.merd.akma.safety

import java.util.Locale

/**
 * Advisory concerns about a generated reply. They are shown to the user so the draft is read before it is copied.
 * They are NOT a faithfulness check: absence of a concern does not mean a draft is true or safe, and a concern does
 * not mean it is wrong. Presence of a concern must never block or auto-edit a draft.
 */
enum class GroundingConcern {
    /** A time, date, weekday, month or number appears in the draft but not in the sender's message or the user's instruction. */
    UNGROUNDED_DETAIL,

    /** First-person promise ("I will attend", "I'll pay", "I confirm") that the selected action does not call for. */
    COMMITMENT_PHRASE,

    /** Claims the user is unavailable / cannot attend, which the user never said and the selected action does not state. */
    UNAVAILABILITY_CLAIM,
}

data class GroundingReport(val concerns: Set<GroundingConcern>, val examples: List<String>) {
    val isClear: Boolean get() = concerns.isEmpty()
}

/**
 * Deterministic cross-check of a draft against what the user actually provided. It reads only the draft and the
 * text already on screen; it stores and logs nothing.
 */
object DraftGrounding {
    /** Actions whose purpose is to commit the user ("accept", "confirm", ...). Commitment phrases are expected there. */
    private val commitmentActions = setOf("accept", "confirm", "agree_new_time", "thank_confirm")

    /** Actions whose purpose is to say the user cannot do it. */
    private val refusalActions = setOf("decline")

    private val weekdays = listOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "lunes", "martes", "miyerkules", "huwebes", "biyernes", "sabado", "linggo",
    )
    private val months = listOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
        "enero", "pebrero", "marso", "abril", "mayo", "hunyo", "hulyo", "agosto", "setyembre", "oktubre", "nobyembre", "disyembre",
    )
    private val relativeDays = listOf("today", "tonight", "tomorrow", "yesterday", "bukas", "ngayon", "mamaya", "kahapon", "next week", "next month")

    private val timePattern = Regex("(?i)\\b\\d{1,2}(?::\\d{2})?\\s?(?:a\\.?m\\.?|p\\.?m\\.?)\\b|\\b\\d{1,2}:\\d{2}\\b|\\b(?:noon|midnight)\\b")
    private val numberPattern = Regex("(?:(?:php|₱|\\$|usd|pesos?)\\s?)?\\d[\\d,]*(?:\\.\\d+)?(?:\\s?(?:pesos?|php|usd|dollars?))?", RegexOption.IGNORE_CASE)

    private val commitment = Regex(
        "(?i)\\b(?:i|we)\\s?(?:'ll|will|shall|can|am\\s+(?:able|happy|glad)\\s+to|'m\\s+(?:able|happy|glad)\\s+to)\\s+" +
            "(?:definitely\\s+|certainly\\s+|gladly\\s+|happily\\s+)?(?:be\\s+(?:there|present|available|free|on\\s+time)|attend|come|join|pay|send|transfer|refund|deliver|finish|complete|submit|accept|confirm|agree|do\\s+it)\\b" +
            "|\\b(?:i|we)\\s+(?:confirm|accept|agree|promise|guarantee|commit)\\b" +
            "|\\bcount\\s+me\\s+in\\b|\\bi(?:'m|\\s+am)\\s+(?:available|free)\\b|\\bwe(?:'re|\\s+are)\\s+(?:available|free)\\b" +
            "|\\bi\\s+(?:agree|accept)\\s+to\\b|\\bfull\\s+refund\\b|\\brefund\\s+(?:you|will)\\b",
    )
    private val unavailable = Regex(
        "(?i)\\b(?:i|we)(?:'m|\\s+am|'re|\\s+are)\\s+(?:not|un)\\s?(?:be\\s+)?(?:available|free|able)\\b" +
            "|\\bunavailable\\b|\\b(?:i|we)\\s+(?:can(?:'|no)?t|cannot|won't|will\\s+not)\\s+(?:make\\s+it|attend|come|join|be\\s+there)\\b" +
            "|\\b(?:i|we)\\s+(?:have|got)\\s+(?:a\\s+)?(?:conflict|prior\\s+commitment|another\\s+(?:meeting|appointment|commitment))\\b" +
            "|\\bnot\\s+available\\b|\\bi\\s+(?:am|'m)\\s+afraid\\s+i\\s+can(?:'|no)?t\\b",
    )

    fun check(draft: String, senderMessage: String, userInstruction: String = "", actionId: String? = null): GroundingReport {
        val concerns = linkedSetOf<GroundingConcern>()
        val examples = mutableListOf<String>()
        val source = normalize("$senderMessage\n$userInstruction")
        val text = normalize(draft)

        fun addExample(value: String) { if (value.isNotBlank() && examples.size < 5 && value !in examples) examples += value.trim().take(60) }

        // Details the user never provided.
        val unknown = linkedSetOf<String>()
        timePattern.findAll(draft).forEach { if (normalizeToken(it.value) !in source.compact) unknown += it.value }
        weekdays.forEach { day -> if (containsWord(text.lower, day) && !containsWord(source.lower, day)) unknown += day }
        months.forEach { month -> if (containsWord(text.lower, month) && !containsWord(source.lower, month)) unknown += month }
        relativeDays.forEach { day -> if (containsWord(text.lower, day) && !containsWord(source.lower, day)) unknown += day }
        numberPattern.findAll(draft).forEach { match ->
            val digits = match.value.filter(Char::isDigit).trimStart('0').ifEmpty { if (match.value.any(Char::isDigit)) "0" else "" }
            if (digits.isNotEmpty() && digits !in source.digitRuns && !(digits.length <= 2 && inTime(draft, match.range.first))) unknown += match.value
        }
        if (unknown.isNotEmpty()) {
            concerns += GroundingConcern.UNGROUNDED_DETAIL
            unknown.forEach(::addExample)
        }

        val commitmentOk = actionId in commitmentActions
        val refusalOk = actionId in refusalActions
        if (!commitmentOk) {
            commitment.findAll(draft).firstOrNull()?.let { concerns += GroundingConcern.COMMITMENT_PHRASE; addExample(it.value) }
        }
        if (!refusalOk && !commitmentOk) {
            unavailable.findAll(draft).firstOrNull()?.let {
                // Only a concern when the sender's own message did not already say so.
                if (!unavailable.containsMatchIn(senderMessage)) { concerns += GroundingConcern.UNAVAILABILITY_CLAIM; addExample(it.value) }
            }
        }
        return GroundingReport(concerns, examples)
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private class Normalized(val lower: String, val compact: String, val digitRuns: Set<String>)

    private fun normalize(text: String): Normalized {
        val lower = text.lowercase(Locale.ROOT)
        val compact = lower.replace(Regex("[\\s.]"), "")
        val digits = Regex("\\d+").findAll(lower.replace(",", "")).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()
        return Normalized(lower, compact, digits)
    }

    private fun normalizeToken(token: String) = token.lowercase(Locale.ROOT).replace(Regex("[\\s.]"), "")

    private fun containsWord(haystack: String, word: String) = Regex("(?<![\\p{L}])${Regex.escape(word)}(?![\\p{L}])").containsMatchIn(haystack)

    /** True when the digit run is part of a time already handled by [timePattern]. */
    private fun inTime(text: String, index: Int) = timePattern.findAll(text).any { index in it.range }

}
