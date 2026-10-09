package ph.merd.akma.ui

import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone

/** Event bindings only. Construction/rendering never starts work or creates another engine. */
internal class JourneyCoordinatorAdapter(
    private val replies: ReplyCoordinator,
    private val paste: () -> Unit,
    private val copy: (ReplyState) -> Boolean,
    private val close: (() -> Unit)? = null,
    private val copyFailed: () -> Unit = {},
) {
    fun callbacks(
        displayed: ReplyState,
        selectedTone: ReplyTone,
        toneChanged: (ReplyTone) -> Unit = {},
        actionSelected: (String?) -> Unit = {},
    ): JourneyCallbacks {
        // A tone tap followed by an action tap can precede the next composition.
        var tone = displayed.pendingConfirmation?.request?.tone ?: selectedTone
        var inputSnapshot = displayed
        var draftSnapshot = displayed
        fun current() = replies.state.value === displayed
        return JourneyCallbacks(
            onClose = close,
            onMessageChange = { text ->
                val live = replies.state.value
                // Continuous typing must not wait for recomposition between characters.
                if (live === inputSnapshot && !live.busy && live.pendingConfirmation == null && text != live.message) {
                    replies.setMessage(text)
                    inputSnapshot = replies.state.value
                }
            },
            onPaste = { if (current() && displayed.canStartProcessing) paste() },
            onAnalyze = {
                if (current() && displayed.canStartProcessing && displayed.phase in setOf(ReplyPhase.Ready, ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied) &&
                    displayed.message.isNotBlank()
                ) replies.analyze()
            },
            onSelectTone = { choice ->
                if (current() && displayed.canChooseDraft) {
                    tone = choice
                    toneChanged(choice)
                }
            },
            onSelectAction = { id ->
                if (current() && displayed.canChooseDraft) {
                    replies.selectDraft(id, tone)
                    if (replies.state.value.pendingConfirmation?.request?.selectedActionId == id) actionSelected(id)
                }
            },
            onCancelConfirmation = {
                displayed.pendingConfirmation?.let { pending ->
                    if (current()) {
                        replies.cancelDisplayedDraft(pending.id)
                        if (replies.state.value.pendingConfirmation == null) actionSelected(null)
                    }
                }
            },
            onCancelProcessing = { replies.cancelDisplayedProcessing(displayed) },
            onStartOver = {
                if (current() && displayed.canStartProcessing && displayed.analysis != null) {
                    replies.setMessage("")
                    actionSelected(null)
                }
            },
            onDraftChange = { text ->
                val live = replies.state.value
                if (live === draftSnapshot && !live.busy && live.pendingConfirmation == null &&
                    live.phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied)
                ) {
                    replies.editDraft(text)
                    draftSnapshot = replies.state.value
                }
            },
            onCopy = {
                if (current() && displayed.canCopy) {
                    if (copy(displayed)) {
                        if (current()) replies.copied()
                    } else copyFailed()
                }
            },
            onRetry = { if (current()) replies.retryLocalModel() },
            onDismissError = { if (current() && displayed.phase == ReplyPhase.Error) replies.recover() },
        )
    }

    /** Called only by the separate protected Confirm control, with its displayed token. */
    fun confirm(displayedId: Long) = replies.confirmDisplayedDraft(displayedId)
}
