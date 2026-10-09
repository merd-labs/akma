package ph.merd.akma.ui

import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState

/** Visible steps of the user journey. Display-only; never drives coordinator calls. */
enum class JourneyStep { Paste, Analyze, Choose, Confirm, Generate, Edit, Copy }

/**
 * Read-only mapping from coordinator state to the journey indicator.
 * A pending confirmation always shows Confirm so the human gate stays visible.
 * Error and no-model states keep the step the user was on, inferred from retained state.
 */
fun ReplyState.journeyStep(): JourneyStep = when (phase) {
    ReplyPhase.ModelUnavailable, ReplyPhase.ModelLoading, ReplyPhase.Ready -> JourneyStep.Paste
    ReplyPhase.Analyzing -> JourneyStep.Analyze
    ReplyPhase.ChoosingAction ->
        if (pendingConfirmation != null) JourneyStep.Confirm else JourneyStep.Choose
    ReplyPhase.Drafting -> JourneyStep.Generate
    ReplyPhase.Editing -> JourneyStep.Edit
    ReplyPhase.Copied -> JourneyStep.Copy
    ReplyPhase.Error -> when {
        draft.isNotBlank() -> JourneyStep.Edit
        analysis != null -> JourneyStep.Choose
        else -> JourneyStep.Paste
    }
}
