package ph.merd.akma.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ph.merd.akma.R
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.ReplyTone

/**
 * Display-only labels and icons for catalog IDs. ActionCatalog stays the authority for which
 * IDs exist and for action labels; this file only decorates IDs that already passed validation.
 */
object CategoryPresentation {
    data class Category(@param:StringRes val label: Int, @param:DrawableRes val icon: Int)

    private val categories = mapOf(
        "interview_invitation" to Category(R.string.akma_category_interview_invitation, R.drawable.ic_akma_briefcase),
        "meeting" to Category(R.string.akma_category_meeting, R.drawable.ic_akma_calendar),
        "reschedule_request" to Category(R.string.akma_category_reschedule_request, R.drawable.ic_akma_calendar),
        "follow_up" to Category(R.string.akma_category_follow_up, R.drawable.ic_akma_message),
        "complaint" to Category(R.string.akma_category_complaint, R.drawable.ic_akma_ask),
        "casual" to Category(R.string.akma_category_casual, R.drawable.ic_akma_message),
        "other" to Category(R.string.akma_category_other, R.drawable.ic_akma_message),
    )

    private val calendarActions = setOf("reschedule", "suggest_time", "agree_new_time", "suggest_alternative", "request_more_time")
    private val askActions = setOf("clarify", "ask_agenda", "ask_reason", "ask_to_clarify")
    private val affirmActions = setOf("accept", "confirm", "thank_confirm", "acknowledge")

    val categoryIds: Set<String> get() = categories.keys

    /** Null for an unknown category; callers show nothing rather than guessing. */
    fun category(categoryId: String): Category? = categories[categoryId]

    /** Icon for an unselected action chip. A selected chip always shows the check icon. */
    @DrawableRes
    fun actionIcon(actionId: String): Int = when (actionId) {
        in calendarActions -> R.drawable.ic_akma_calendar
        in askActions -> R.drawable.ic_akma_ask
        in affirmActions -> R.drawable.ic_akma_check
        else -> R.drawable.ic_akma_message
    }

    @StringRes
    fun toneLabel(tone: ReplyTone): Int = when (tone) {
        ReplyTone.PROFESSIONAL -> R.string.akma_tone_professional
        ReplyTone.FRIENDLY -> R.string.akma_tone_friendly
        ReplyTone.CONCISE -> R.string.akma_tone_concise
    }

    /** Provenance caption required by CONTRACT.md; UNSPECIFIED never reaches display. */
    @StringRes
    fun sourceCaption(source: AnalysisSource): Int? = when (source) {
        AnalysisSource.LOCAL_MODEL -> R.string.akma_source_local_model
        AnalysisSource.DETERMINISTIC -> R.string.akma_source_deterministic
        AnalysisSource.UNSPECIFIED -> null
    }
}
