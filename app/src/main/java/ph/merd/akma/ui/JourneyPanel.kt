package ph.merd.akma.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.ui.components.ActionChipGroup
import ph.merd.akma.ui.components.AkmaButton
import ph.merd.akma.ui.components.AkmaPanel
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.components.ConfirmationCard
import ph.merd.akma.ui.components.CopyReplyButton
import ph.merd.akma.ui.components.DemoBadge
import ph.merd.akma.ui.components.EmptyStateCard
import ph.merd.akma.ui.components.IntentCard
import ph.merd.akma.ui.components.MessageInputCard
import ph.merd.akma.ui.components.NoteRow
import ph.merd.akma.ui.components.ProcessingCard
import ph.merd.akma.ui.components.RefineButton
import ph.merd.akma.ui.components.ReplyCard
import ph.merd.akma.ui.components.ToneSelector
import ph.merd.akma.ui.theme.AkmaTheme

/**
 * User intents from the panel. The shell maps them to ReplyCoordinator calls:
 * onSelectAction -> draft(id, tone) (stages only); onSelectTone only changes the UI-held tone;
 * onCancelConfirmation and onCancelProcessing cancel and clear; onStartOver -> setMessage("");
 * onRetry -> initialize(); onDismissError -> recover(); onCopy -> copyDraft(...) then copied().
 * onRefine -> draft(selectedActionId, tone, kind.instruction): it stages a confirmation like any draft.
 * Confirmation itself is handled by the confirm slot, never by these callbacks.
 * A null [onClose] hides the Close button.
 */
data class JourneyCallbacks(
    val onClose: (() -> Unit)? = null,
    val onMessageChange: (String) -> Unit = {},
    val onPaste: () -> Unit = {},
    val onAnalyze: () -> Unit = {},
    val onSelectAction: (String) -> Unit = {},
    val onSelectTone: (ReplyTone) -> Unit = {},
    val onCancelConfirmation: () -> Unit = {},
    val onCancelProcessing: () -> Unit = {},
    val onStartOver: () -> Unit = {},
    val onDraftChange: (String) -> Unit = {},
    val onRefine: (RefineKind) -> Unit = {},
    val onCopy: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDismissError: () -> Unit = {},
)

/**
 * The full reply journey in Figma panel styling. Stateless: render [ui] from ReplyState.toPanelUi.
 * Scrolls and pads for the keyboard itself, so do not nest it in another vertical scroll.
 * [confirmButton] receives the displayed confirmation and must confirm exactly its ID, and only
 * when [ConfirmationUi.valid]. [copyButton] replaces the Compose copy button, so a host can use an
 * obscured-touch-safe control; it receives the copy state. [footer] renders below the journey.
 */
@Composable
fun JourneyPanel(
    ui: JourneyPanelUi,
    callbacks: JourneyCallbacks,
    modifier: Modifier = Modifier,
    copyButton: (@Composable (CopyUi) -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
    imePadding: Boolean = true,
    confirmButton: @Composable (ConfirmationUi) -> Unit,
) {
    AkmaPanel(
        onClose = callbacks.onClose,
        modifier = modifier
            .then(if (imePadding) Modifier.imePadding() else Modifier)
            .verticalScroll(rememberScrollState()),
    ) {
        if (ui.demo) DemoBadge()
        when (ui.status) {
            PanelStatus.LoadingModel -> ProcessingCard(
                stringResource(R.string.akma_loading_model_title),
                stringResource(R.string.akma_loading_model_body),
                onCancel = callbacks.onCancelProcessing.takeIf { ui.canCancelProcessing },
            )
            PanelStatus.ModelUnavailable -> {
                EmptyStateCard(R.drawable.ic_akma_wifi_off, stringResource(R.string.akma_no_model_title), stringResource(R.string.akma_no_model_body))
                ui.notice?.let { NoteRow(R.drawable.ic_akma_message, it) }
                if (ui.canRetry) AkmaButton(stringResource(R.string.akma_retry), callbacks.onRetry)
            }
            PanelStatus.Error -> {
                ui.intent?.let { IntentCard(it.categoryId, it.message, it.source, language = it.language, demo = ui.demo) }
                EmptyStateCard(R.drawable.ic_akma_ask, stringResource(R.string.akma_error_title), stringResource(R.string.akma_error_body))
                ui.notice?.let { NoteRow(R.drawable.ic_akma_message, it) }
                if (ui.canRetry) AkmaButton(stringResource(R.string.akma_retry), callbacks.onRetry)
                AkmaButton(
                    stringResource(R.string.akma_dismiss),
                    callbacks.onDismissError,
                    variant = if (ui.canRetry) ButtonVariant.Secondary else ButtonVariant.Primary,
                )
            }
            null -> Unit
        }
        // Status cards show the notice under their explanation; other states show it here.
        if (ui.status == null || ui.status == PanelStatus.LoadingModel) ui.notice?.let { NoteRow(R.drawable.ic_akma_message, it) }
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
            ui.intent?.let { IntentCard(it.categoryId, it.message, it.source, language = it.language, demo = ui.demo) }
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
                refineLabel = confirmation.refine?.let { stringResource(it.label) },
            ) {
                if (!confirmation.valid) {
                    Text(stringResource(R.string.akma_review_invalid), style = AkmaTheme.type.bodyMStrong, color = AkmaTheme.colors.textPrimary)
                }
                confirmButton(confirmation)
            }
        }
        ui.reply?.let { reply ->
            ReplyCard(reply, callbacks.onDraftChange)
            if (reply is ReplyUi.Draft) RefineRow(ui.canRefine, callbacks.onRefine)
            if (reply == ReplyUi.Writing) NoteRow(R.drawable.ic_akma_lock, stringResource(R.string.akma_writing_body))
            if (reply == ReplyUi.Writing && ui.canCancelProcessing) {
                AkmaButton(stringResource(R.string.akma_cancel), callbacks.onCancelProcessing, variant = ButtonVariant.Secondary)
            }
        }
        if (ui.copy != CopyUi.Hidden) {
            if (copyButton != null) {
                copyButton(ui.copy)
            } else {
                CopyReplyButton(
                    copied = ui.copy == CopyUi.Copied,
                    enabled = ui.copy == CopyUi.Ready || ui.copy == CopyUi.Copied,
                    onCopy = callbacks.onCopy,
                )
            }
        }
        if (ui.canStartOver) {
            AkmaButton(stringResource(R.string.akma_new_message), callbacks.onStartOver, variant = ButtonVariant.Secondary)
        }
        footer()
    }
}

/** Figma Refine row (27:1828): refines the current reply; each tap is confirmed before writing. */
@Composable
private fun RefineRow(enabled: Boolean, onRefine: (RefineKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RefineKind.entries.forEach { kind ->
            RefineButton(stringResource(kind.label), { onRefine(kind) }, enabled = enabled)
        }
    }
}

@get:StringRes
internal val RefineKind.label: Int
    get() = when (this) {
        RefineKind.Regenerate -> R.string.akma_refine_regenerate
        RefineKind.Shorter -> R.string.akma_refine_shorter
        RefineKind.MoreFormal -> R.string.akma_refine_more_formal
    }
