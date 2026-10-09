package ph.merd.akma.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ph.merd.akma.R
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.ui.components.ActionChipGroup
import ph.merd.akma.ui.components.AkmaButton
import ph.merd.akma.ui.components.AkmaPanel
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.components.ConfirmationCard
import ph.merd.akma.ui.components.CopyReplyButton
import ph.merd.akma.ui.components.EmptyStateCard
import ph.merd.akma.ui.components.IntentCard
import ph.merd.akma.ui.components.MessageInputCard
import ph.merd.akma.ui.components.ProcessingCard
import ph.merd.akma.ui.components.ReplyCard
import ph.merd.akma.ui.components.ToneSelector

/**
 * User intents from the panel. The shell maps them to ReplyCoordinator calls:
 * onSelectAction -> draft(id, tone) (stages only); onSelectTone while a confirmation is pending ->
 * draft(pendingActionId, tone) to restage; onCancelConfirmation and onCancelProcessing -> cancel();
 * onRetry -> initialize() or recover(); onCopy -> copyDraft(...) then copied().
 * Confirmation itself is handled by the confirm slot, never by these callbacks.
 */
data class JourneyCallbacks(
    val onClose: () -> Unit = {},
    val onMessageChange: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onSelectAction: (String) -> Unit = {},
    val onSelectTone: (ReplyTone) -> Unit = {},
    val onCancelConfirmation: () -> Unit = {},
    val onCancelProcessing: () -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onCopy: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/**
 * The full reply journey in Figma panel styling. Stateless: render [ui] from ReplyState.toPanelUi.
 * Scrolls and pads for the keyboard itself, so do not nest it in another vertical scroll.
 * [confirmButton] receives the displayed confirmation and must confirm exactly its ID.
 */
@Composable
fun JourneyPanel(
    ui: JourneyPanelUi,
    callbacks: JourneyCallbacks,
    modifier: Modifier = Modifier,
    confirmButton: @Composable (ConfirmationUi) -> Unit,
) {
    AkmaPanel(
        onClose = callbacks.onClose,
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        when (ui.status) {
            PanelStatus.LoadingModel -> ProcessingCard(
                stringResource(R.string.akma_loading_model_title),
                stringResource(R.string.akma_loading_model_body),
                onCancel = callbacks.onCancelProcessing.takeIf { ui.canCancelProcessing },
            )
            PanelStatus.ModelUnavailable -> {
                EmptyStateCard(R.drawable.ic_akma_wifi_off, stringResource(R.string.akma_no_model_title), stringResource(R.string.akma_no_model_body))
                AkmaButton(stringResource(R.string.akma_retry), callbacks.onRetry)
            }
            PanelStatus.Error -> {
                ui.intent?.let { IntentCard(it.categoryId, it.message, it.source) }
                EmptyStateCard(R.drawable.ic_akma_ask, stringResource(R.string.akma_error_title), stringResource(R.string.akma_error_body))
                AkmaButton(stringResource(R.string.akma_retry), callbacks.onRetry)
                AkmaButton(stringResource(R.string.akma_dismiss), callbacks.onDismissError, variant = ButtonVariant.Secondary)
            }
            null -> Unit
        }
        ui.input?.let { input ->
            MessageInputCard(input.message, input.maxLength, callbacks.onMessageChange, callbacks.onPaste)
            if (ui.status == null) {
                AkmaButton(stringResource(R.string.akma_analyze), callbacks.onAnalyze, enabled = input.canAnalyze)
            }
        }
        if (ui.reading) {
            ProcessingCard(
                stringResource(R.string.akma_reading_title),
                stringResource(R.string.akma_reading_body),
                onCancel = callbacks.onCancelProcessing.takeIf { ui.canCancelProcessing },
            )
        }
        if (ui.status != PanelStatus.Error) {
            ui.intent?.let { IntentCard(it.categoryId, it.message, it.source) }
        }
        ui.choice?.let { choice ->
            ActionChipGroup(choice.actions, choice.selectedActionId, choice.enabled, callbacks.onSelectAction)
            ToneSelector(choice.tone, choice.enabled, callbacks.onSelectTone)
        }
        ui.confirmation?.let { confirmation ->
            ConfirmationCard(
                actionLabel = confirmation.actionLabel,
                tone = confirmation.tone,
                message = confirmation.message,
                onCancel = callbacks.onCancelConfirmation,
            ) { confirmButton(confirmation) }
        }
        ui.reply?.let { reply ->
            ReplyCard(reply, callbacks.onDraftChange)
            if (reply == ReplyUi.Writing && ui.canCancelProcessing) {
                AkmaButton(stringResource(R.string.akma_cancel), callbacks.onCancelProcessing, variant = ButtonVariant.Secondary)
            }
        }
        if (ui.copy != CopyUi.Hidden) {
            CopyReplyButton(
                copied = ui.copy == CopyUi.Copied,
                enabled = ui.copy == CopyUi.Ready || ui.copy == CopyUi.Copied,
                onCopy = callbacks.onCopy,
            )
        }
    }
}
