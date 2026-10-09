package ph.merd.akma.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.MotionEvent
import android.widget.Button
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.domain.AnalyzeRequest
import ph.merd.akma.domain.DraftConfirmation
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone

internal val ReplyState.canStartProcessing: Boolean
    get() = !busy && pendingConfirmation == null

internal val ReplyState.canChooseDraft: Boolean
    get() = canStartProcessing && analysis != null &&
        phase in setOf(ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied)

/** Recreated surfaces read the immutable request; local tone widgets never redefine it. */
internal fun ReplyState.displayedConfirmation(): DraftConfirmation? {
    val pending = pendingConfirmation ?: return null
    val canonical = ActionCatalog.action(pending.request.selectedActionId) ?: return null
    if (busy || phase != ReplyPhase.ChoosingAction || pending.action != canonical ||
        analysis?.actions?.singleOrNull { it.id == canonical.id } != canonical ||
        pending.request.original != AnalyzeRequest(message)
    ) return null
    return pending
}

/** Check live state as well as enabled widgets: another surface can consume the request first. */
internal fun ReplyCoordinator.selectDraft(actionId: String, tone: ReplyTone) {
    val current = state.value
    val canonical = ActionCatalog.action(actionId) ?: return
    if (!current.canChooseDraft || current.analysis?.actions?.singleOrNull { it.id == actionId } != canonical) return
    draft(actionId, tone)
}

internal fun ReplyCoordinator.confirmDisplayedDraft(displayedId: Long) {
    if (state.value.displayedConfirmation()?.id != displayedId) return
    confirmDraft(displayedId)
}

internal fun ReplyCoordinator.cancelDisplayedDraft(displayedId: Long) {
    val current = state.value
    if (current.busy || current.phase != ReplyPhase.ChoosingAction || current.pendingConfirmation?.id != displayedId) return
    cancel()
}

internal fun isObscuredTouch(flags: Int): Boolean =
    flags and (MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0

/**
 * Native filtering also protects the button when hosted inside Compose through AndroidView. Used for the two
 * consequential controls (Confirm, Copy): a tap delivered while another app's window covers the tap point is dropped.
 * Deliberately not applied panel-wide: full-screen dimmer overlays would otherwise block every control.
 */
internal fun obscuredAwareButton(context: Context, label: String): Button = object : Button(context) {
    override fun onFilterTouchEventForSecurity(event: MotionEvent): Boolean {
        if (isObscuredTouch(event.flags)) {
            cancelPendingInputEvents()
            isPressed = false
            return false
        }
        return super.onFilterTouchEventForSecurity(event)
    }
}.apply {
    text = label
    isSaveEnabled = false
    filterTouchesWhenObscured = true
}

internal fun confirmationButton(context: Context): Button = obscuredAwareButton(context, "Confirm and generate draft")

internal fun copyButton(context: Context): Button = obscuredAwareButton(context, "Copy draft")

fun ReplyState.statusText(): String = when (phase) {
    ReplyPhase.ModelUnavailable -> "Model unavailable. No local AI is configured."
    ReplyPhase.ModelLoading -> "Loading local model…"
    ReplyPhase.Ready -> "Ready"
    ReplyPhase.Analyzing -> "Analyzing locally…"
    ReplyPhase.ChoosingAction -> if (pendingConfirmation != null) "Review your selection before drafting" else "Choose an action"
    ReplyPhase.Drafting -> "Drafting locally…"
    ReplyPhase.Editing -> "Edit your draft"
    ReplyPhase.Copied -> "Copied. Paste and send manually."
    ReplyPhase.Error -> "Processing error"
}

/** Only invoke from an explicit Copy click. This helper never reads the clipboard. */
fun copyDraft(context: Context, state: ReplyState): Boolean {
    if (!state.canCopy) return false
    return try {
        val clip = ClipData.newPlainText("Akma draft", state.draft)
        if (Build.VERSION.SDK_INT >= 33) {
            // Asks the system UI/keyboards not to show the draft in clipboard previews. Ignored below API 33.
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        true
    } catch (_: RuntimeException) {
        false
    }
}
