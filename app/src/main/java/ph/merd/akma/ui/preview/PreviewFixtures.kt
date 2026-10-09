package ph.merd.akma.ui.preview

import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalysisResult
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftConfirmation
import ph.merd.akma.domain.DraftRequest
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone

/**
 * SAMPLE TEXT FOR PREVIEWS ONLY. Copied from the Figma mock chat, not produced by any model.
 * Only files in ui/preview may reference this object (PreviewIsolationTest), and every
 * preview that shows it also shows PreviewBadge.
 */
internal object PreviewFixtures {
    const val MESSAGE = "Good afternoon! We'd like to invite you for an interview this Friday at 2:00 PM. Are you available?"
    const val SAMPLE_DRAFT = "Good afternoon! Thank you for the invitation. I'm available this Friday at 2:00 PM and look forward to speaking with you."

    private val interview = AnalysisResult(
        category = "interview_invitation",
        summary = "Sample summary for previews",
        requiresUserDecision = true,
        actions = ActionCatalog.actionsFor("interview_invitation").take(3),
        source = AnalysisSource.LOCAL_MODEL,
    )

    private val accept = ActionCatalog.action("accept")!!

    val ready = ReplyState(phase = ReplyPhase.Ready)
    val readyWithMessage = ReplyState(phase = ReplyPhase.Ready, message = MESSAGE)
    val modelUnavailable = ReplyState(phase = ReplyPhase.ModelUnavailable)
    val modelLoading = ReplyState(phase = ReplyPhase.ModelLoading)
    val analyzing = ReplyState(phase = ReplyPhase.Analyzing, message = MESSAGE)
    val choosing = ReplyState(phase = ReplyPhase.ChoosingAction, message = MESSAGE, analysis = interview)
    val confirming = choosing.copy(
        pendingConfirmation = DraftConfirmation(
            id = 1L,
            action = accept,
            request = DraftRequest(AnalyzeRequest(MESSAGE), accept.id, ReplyTone.PROFESSIONAL),
        ),
    )
    val drafting = ReplyState(phase = ReplyPhase.Drafting, message = MESSAGE, analysis = interview)
    val editing = ReplyState(phase = ReplyPhase.Editing, message = MESSAGE, analysis = interview, draft = SAMPLE_DRAFT)
    val copied = editing.copy(phase = ReplyPhase.Copied)
    val fallback = ReplyState(phase = ReplyPhase.ChoosingAction, message = "Paki-sabi kay Ana na naiwan niya yung charger dito.", analysis = ActionCatalog.otherAnalysis())
    val error = ReplyState(phase = ReplyPhase.Error, message = MESSAGE, analysis = interview, notice = "Processing error")
}
