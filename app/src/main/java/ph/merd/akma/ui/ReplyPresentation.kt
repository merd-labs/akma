package ph.merd.akma.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState

fun ReplyState.statusText(): String = when (phase) {
    ReplyPhase.ModelUnavailable -> "Model unavailable. No local AI is configured."
    ReplyPhase.ModelLoading -> "Loading local model…"
    ReplyPhase.Ready -> "Ready"
    ReplyPhase.Analyzing -> "Analyzing locally…"
    ReplyPhase.ChoosingAction -> "Choose an action"
    ReplyPhase.Drafting -> "Drafting locally…"
    ReplyPhase.Editing -> "Edit your draft"
    ReplyPhase.Copied -> "Copied. Paste and send manually."
    ReplyPhase.Error -> "Processing error"
}

/** Only invoke from an explicit Copy click. This helper never reads the clipboard. */
fun copyDraft(context: Context, state: ReplyState): Boolean {
    if (!state.canCopy) return false
    return try {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Akma draft", state.draft))
        true
    } catch (_: RuntimeException) {
        false
    }
}
