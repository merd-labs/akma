package ph.merd.akma.ui

import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ReplyValidation

/** What the panel shows. Built only from coordinator state; it never invents text. */
data class JourneyPanelUi(
    val step: JourneyStep,
    val status: PanelStatus?,
    val input: InputUi?,
    val reading: Boolean,
    val intent: IntentUi?,
    val choice: ChoiceUi?,
    val confirmation: ConfirmationUi?,
    val reply: ReplyUi?,
    val copy: CopyUi,
    val canCancelProcessing: Boolean,
    val canRetry: Boolean = false,
    val canStartOver: Boolean = false,
    /** Regenerate / Shorter / More formal: only with a draft, an action to refine, and nothing busy. */
    val canRefine: Boolean = false,
    /** Debug demo engine: every result is labelled as demo data, never as AI output. */
    val demo: Boolean = false,
    /** Coordinator notice, shown verbatim. Null when nothing needs saying. */
    val notice: String? = null,
)

enum class PanelStatus { ModelUnavailable, LoadingModel, Error }

data class InputUi(val message: String, val maxLength: Int, val canAnalyze: Boolean)

data class IntentUi(val categoryId: String, val message: String, val source: AnalysisSource, val language: String? = null)

data class ActionUi(val id: String, val label: String)

data class ChoiceUi(
    val actions: List<ActionUi>,
    val selectedActionId: String?,
    val tone: ReplyTone,
    val enabled: Boolean,
)

/**
 * The exact staged request. The confirm control must confirm [confirmationId], never a newer one.
 * [valid] is false when the staged request no longer matches live state; confirming is then disabled.
 */
data class ConfirmationUi(
    val confirmationId: Long,
    val actionLabel: String,
    val tone: ReplyTone,
    val message: String,
    val valid: Boolean = true,
    val refine: RefineKind? = null,
)

sealed interface ReplyUi {
    data object Writing : ReplyUi
    data class Draft(val text: String) : ReplyUi
}

enum class CopyUi { Hidden, Disabled, Ready, Copied }

/**
 * Figma Refine buttons. They re-stage the same action and tone with an instruction, which the user
 * confirms like any other draft request. [instruction] is what the engine receives.
 */
enum class RefineKind(val instruction: String) {
    Regenerate(""),
    Shorter("Make it shorter."),
    MoreFormal("Make it more formal."),
    ;

    companion object {
        fun of(instruction: String): RefineKind? = entries.firstOrNull { it.instruction == instruction && it != Regenerate }
    }
}

/**
 * Maps coordinator state to panel content.
 * [selectedTone] and [lastSelectedActionId] are UI-held choices; a pending confirmation overrides both
 * and locks the choices, so the staged action and tone cannot change until the user cancels.
 * Analysis reaching ReplyState is already normalized, so its action labels are catalog labels.
 */
fun ReplyState.toPanelUi(
    selectedTone: ReplyTone,
    lastSelectedActionId: String?,
    language: String? = null,
    demo: Boolean = false,
): JourneyPanelUi {
    val pending = pendingConfirmation
    val tone = pending?.request?.tone ?: selectedTone
    val selectedId = pending?.action?.id ?: lastSelectedActionId
    val result = analysis
    val intent = result?.let { IntentUi(it.category, message, it.source, language) }
    fun choice(enabled: Boolean) = result?.let { r ->
        ChoiceUi(
            actions = r.actions.map { ActionUi(it.id, it.label) },
            selectedActionId = selectedId?.takeIf { id -> r.actions.any { it.id == id } },
            tone = tone,
            enabled = enabled,
        )
    }
    val base = JourneyPanelUi(
        step = journeyStep(),
        status = null,
        input = null,
        reading = false,
        intent = null,
        choice = null,
        confirmation = null,
        reply = null,
        copy = CopyUi.Hidden,
        canCancelProcessing = busy,
        canRetry = canRetryLocalModel,
        canStartOver = !busy && pending == null && phase in setOf(ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied),
        notice = notice?.takeIf { it.isNotBlank() && phase != ReplyPhase.Copied },
        demo = demo,
    )
    return when (phase) {
        ReplyPhase.ModelUnavailable -> base.copy(
            status = PanelStatus.ModelUnavailable,
            input = InputUi(message, ReplyValidation.MAX_TEXT_LENGTH, canAnalyze = false),
        )
        ReplyPhase.ModelLoading -> base.copy(status = PanelStatus.LoadingModel)
        ReplyPhase.Ready -> base.copy(
            input = InputUi(message, ReplyValidation.MAX_TEXT_LENGTH, canAnalyze = message.isNotBlank()),
        )
        ReplyPhase.Analyzing -> base.copy(reading = true)
        ReplyPhase.ChoosingAction -> base.copy(
            intent = intent,
            choice = choice(enabled = canChooseDraft),
            confirmation = pending?.let {
                ConfirmationUi(
                    confirmationId = it.id,
                    actionLabel = it.action.label,
                    tone = it.request.tone,
                    message = it.request.original.message,
                    valid = displayedConfirmation()?.id == it.id,
                    refine = RefineKind.of(it.request.userInstruction),
                )
            },
            copy = CopyUi.Disabled,
        )
        ReplyPhase.Drafting -> base.copy(
            intent = intent,
            choice = choice(enabled = false),
            reply = ReplyUi.Writing,
            copy = CopyUi.Disabled,
        )
        ReplyPhase.Editing, ReplyPhase.Copied -> base.copy(
            intent = intent,
            choice = choice(enabled = canChooseDraft),
            reply = ReplyUi.Draft(draft),
            canRefine = canChooseDraft && selectedId != null && result?.actions?.any { it.id == selectedId } == true,
            copy = when {
                !canCopy -> CopyUi.Disabled
                phase == ReplyPhase.Copied -> CopyUi.Copied
                else -> CopyUi.Ready
            },
        )
        ReplyPhase.Error -> base.copy(status = PanelStatus.Error, intent = intent)
    }
}
