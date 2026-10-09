package ph.merd.akma.demo

import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.ReplyTone

/**
 * TEMPORARY hardcoded demo content (debug builds only), taken from the AKMA Figma reply flow.
 * Never shipped in release builds and always shown under a "Demo" label, never as AI output.
 */
object DemoReplies {
    const val CATEGORY = "interview_invitation"
    const val SUMMARY = "Demo data: an interview invitation that asks if you are available."
    val actionIds = listOf("accept", "reschedule", "clarify")

    /** Refine instructions; they must match ui.RefineKind. */
    const val SHORTER = "Make it shorter."
    const val MORE_FORMAL = "Make it more formal."

    private val replies: Map<Pair<String, ReplyTone>, String> = mapOf(
        ("accept" to ReplyTone.PROFESSIONAL) to "Good afternoon! Thank you for the invitation. I'm available this Friday at 2:00 PM and look forward to speaking with you.",
        ("accept" to ReplyTone.FRIENDLY) to "Hi! Thanks so much for the invite. Friday at 2:00 PM works great for me. See you then!",
        ("accept" to ReplyTone.CONCISE) to "Thank you. Friday at 2:00 PM works for me.",
        ("reschedule" to ReplyTone.PROFESSIONAL) to "Good afternoon! Thank you for the invitation. Unfortunately, I'm not available this Friday at 2:00 PM. Would another day or time work for you?",
        ("reschedule" to ReplyTone.FRIENDLY) to "Hi! Thanks for the invite. Friday at 2:00 PM doesn't work for me, sadly. Could we find another time?",
        ("reschedule" to ReplyTone.CONCISE) to "Thank you. Friday at 2:00 PM doesn't work for me. Could we pick another time?",
        ("clarify" to ReplyTone.PROFESSIONAL) to "Good afternoon! Thank you for the invitation. Could you share more details about the interview, such as the format and who I will be meeting?",
        ("clarify" to ReplyTone.FRIENDLY) to "Hi! Thanks for reaching out. Could you tell me a bit more about the interview before Friday?",
        ("clarify" to ReplyTone.CONCISE) to "Thank you. Could you share the interview format and details?",
    )

    private val shorter: Map<String, String> = mapOf(
        "accept" to "Thank you! Friday at 2:00 PM works for me.",
        "reschedule" to "Thanks! Friday at 2:00 PM doesn't work. Another time?",
        "clarify" to "Thanks! Could you share more details first?",
    )

    private val formal: Map<String, String> = mapOf(
        "accept" to "Good afternoon. Thank you very much for the invitation. I am pleased to confirm my availability this Friday at 2:00 PM, and I look forward to our conversation.",
        "reschedule" to "Good afternoon. Thank you very much for the invitation. Regrettably, I am unavailable this Friday at 2:00 PM. Kindly let me know if an alternative schedule would be possible.",
        "clarify" to "Good afternoon. Thank you very much for the invitation. May I kindly request further details regarding the interview format and participants?",
    )

    /** Draft text for an action, tone and refine instruction. Unknown combinations get a labelled generic reply. */
    fun reply(actionId: String, tone: ReplyTone, instruction: String = ""): String = when (instruction) {
        SHORTER -> shorter[actionId]
        MORE_FORMAL -> formal[actionId]
        else -> replies[actionId to tone]
    } ?: "${ActionCatalog.action(actionId)?.label ?: "Reply"}: thank you for your message. (Demo reply)"

    /** Demo language tag for the intent card. The real engine supplies this later. */
    @Suppress("UNUSED_PARAMETER")
    fun language(message: String): String? = "English"
}
