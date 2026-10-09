package ph.merd.akma.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ph.merd.akma.R
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ReplyValidation
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.theme.AkmaTheme

/**
 * The live reply journey, wired to the single ReplyCoordinator. Used by the Activity "Reply here"
 * screen and by the floating overlay panel, so both surfaces behave and look the same.
 *
 * Engine-agnostic: [demo] and [languageLabel] come from the build's ReplyEngineChoice, so swapping the
 * temporary demo engine for the on-device model needs no change here.
 * Confirm and Copy stay the obscured-touch-safe native buttons; every generation needs Confirm.
 */
@Composable
fun LiveJourneyPanel(
    replies: ReplyCoordinator,
    demo: Boolean,
    languageLabel: (String) -> String?,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    imePadding: Boolean = true,
) {
    val context = LocalContext.current
    val state by replies.state.collectAsState()
    var tone by remember { mutableStateOf(ReplyTone.PROFESSIONAL) }
    var lastActionId by remember { mutableStateOf<String?>(null) }
    var localNotice by remember { mutableStateOf<String?>(null) }
    var copyError by remember { mutableStateOf(false) }
    // Guards compare against the state this composition displayed, not a later one.
    val displayed = state
    val language = displayed.analysis?.let { languageLabel(displayed.message) }
    val ui = displayed.toPanelUi(tone, lastActionId, language, demo).let { it.copy(notice = localNotice ?: it.notice) }
    val pasteNotices = PasteNotices(
        empty = stringResource(R.string.akma_paste_empty),
        tooLong = stringResource(R.string.akma_paste_too_long),
        blocked = stringResource(R.string.akma_paste_blocked),
    )
    JourneyPanel(
        ui = ui,
        modifier = modifier,
        imePadding = imePadding,
        callbacks = JourneyCallbacks(
            onClose = onClose,
            onMessageChange = { localNotice = null; replies.setMessage(it) },
            onPaste = { localNotice = pasteFromClipboard(context, replies, pasteNotices) },
            onAnalyze = { if (replies.state.value.canStartProcessing) replies.analyze() },
            onSelectAction = { id ->
                if (replies.state.value.canChooseDraft) {
                    lastActionId = id
                    replies.selectDraft(id, tone)
                }
            },
            onSelectTone = { if (replies.state.value.canChooseDraft) tone = it },
            onRefine = { kind -> lastActionId?.let { replies.selectDraft(it, tone, kind.instruction) } },
            onCancelConfirmation = {
                displayed.pendingConfirmation?.let { replies.cancelDisplayedDraft(it.id) }
                lastActionId = null
            },
            onCancelProcessing = { replies.cancelDisplayedProcessing(displayed) },
            onStartOver = {
                localNotice = null
                lastActionId = null
                replies.setMessage("")
            },
            onDraftChange = { copyError = false; replies.editDraft(it) },
            onRetry = { replies.retryLocalModel() },
            onDismissError = replies::recover,
        ),
        copyButton = { copy ->
            val copied = copy == CopyUi.Copied
            ProtectedAkmaButton(
                factory = { copyButton(it) },
                text = stringResource(if (copied) R.string.akma_copied else R.string.akma_copy_reply),
                enabled = copy == CopyUi.Ready || copied,
                onClick = {
                    copyError = !copyDraft(context, replies.state.value)
                    if (!copyError) replies.copied()
                },
                variant = if (copied) ButtonVariant.Success else ButtonVariant.Primary,
                icon = if (copied) R.drawable.ic_akma_check else R.drawable.ic_akma_copy,
            )
            if (copyError) SecondaryText(stringResource(R.string.akma_copy_failed))
            else if (copied) SecondaryText(stringResource(R.string.akma_copied_hint))
        },
    ) { confirmation ->
        // Replacing a token replaces its control, cancelling any in-flight tap.
        key(confirmation.confirmationId) {
            ProtectedAkmaButton(
                factory = { confirmationButton(it) },
                text = stringResource(R.string.akma_write_reply),
                enabled = confirmation.valid,
                onClick = { replies.confirmDisplayedDraft(confirmation.confirmationId) },
            )
        }
    }
}

private class PasteNotices(val empty: String, val tooLong: String, val blocked: String)

/**
 * Reads the clipboard only from an explicit Paste tap. Android returns nothing when the window lacks
 * focus; then the user is told to paste with the keyboard instead. Returns a notice, or null on success.
 */
private fun pasteFromClipboard(context: Context, replies: ReplyCoordinator, notices: PasteNotices): String? {
    if (!replies.state.value.canStartProcessing) return null
    val clip = try {
        context.getSystemService(ClipboardManager::class.java)?.primaryClip
    } catch (_: RuntimeException) {
        null
    } ?: return notices.blocked
    val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    return when {
        text.isBlank() -> notices.empty
        text.length > ReplyValidation.MAX_TEXT_LENGTH -> notices.tooLong
        else -> { replies.setMessage(text); null }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary)
}
