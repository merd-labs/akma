package ph.merd.akma.overlay

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import ph.merd.akma.R
import ph.merd.akma.ui.JourneyCoordinatorAdapter
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.ui.copyDraft
import ph.merd.akma.ui.statusText
import ph.merd.akma.ui.canStartProcessing
import ph.merd.akma.ui.canChooseDraft
import ph.merd.akma.ui.displayedConfirmation
import ph.merd.akma.ui.confirmationButton
import ph.merd.akma.ui.copyButton
import ph.merd.akma.ui.canRetryLocalModel
import ph.merd.akma.ui.retryLocalModel

/** Views keep overlay lifecycle independent from Compose. Input is never saved or autofilled. */
class OverlayPanel(context: Context, private val replies: ReplyCoordinator, close: () -> Unit) : LinearLayout(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = resources.getDimensionPixelSize(R.dimen.akma_space_md)
        setPadding(padding, padding, padding, padding)
        overlaySurface()
    }
    private val status = label("")
    private val notice = label("")
    private val progress = ProgressBar(context)
    private val cancel = button("Cancel and clear session") {}
    private val check = button("Retry local model", replies::retryLocalModel)
    private val recover = button("Dismiss error and retry", replies::recover)
    private val message = input(context.getString(R.string.akma_paste_hint), replies::setMessage)
    private val paste = button(context.getString(R.string.akma_paste)) {}
    private val analyze = button(context.getString(R.string.akma_analyze)) {}.apply { overlayStyle(primary = true) }
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val confirmationArea = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val tone = Spinner(context).apply {
        val labels = listOf(R.string.akma_tone_professional, R.string.akma_tone_friendly, R.string.akma_tone_concise)
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, labels.map(context::getString)) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getView(position, convertView, parent).apply { (this as? TextView)?.overlayText() }
            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply { (this as? TextView)?.overlayText() }
        }
    }
    private val draft = input(context.getString(R.string.akma_tap_to_edit), replies::editDraft)
    private val review = label("Review before copying. Paste and send manually.")
    private val copy = copyButton(context, ::blockedTap).apply {
        overlayStyle(primary = true, icon = R.drawable.ic_akma_copy)
    }
    private val adapter = JourneyCoordinatorAdapter(
        replies, paste = ::pasteMessage, copy = { copyDraft(context, it) }, close = close,
        copyFailed = { showNotice(context.getString(R.string.akma_copy_failed)) },
    )
    private var rendering = false
    private var actionKey: Any? = null
    private var lastRendered: ReplyState? = null
    private var localNotice: String? = null
    private var availableHeightPx: Int? = null

    init {
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        orientation = VERTICAL
        overlaySurface(radius = R.dimen.akma_radius_xl)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            val padding = resources.getDimensionPixelSize(R.dimen.akma_space_sm)
            setPadding(padding, padding, padding, 0)
            addView(label("Akma").apply { overlayText(20f, R.font.plus_jakarta_sans_extrabold) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(button("Close panel", close).apply { overlayStyle(icon = R.drawable.ic_akma_close) })
        }
        addView(header)
        addView(ScrollView(context).apply { isFillViewport = false; addView(content) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        listOf(status, notice, progress, cancel, check, recover, message, paste, analyze, tone, actions, confirmationArea, review, draft, copy).forEach {
            content.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.akma_space_xs)
            })
        }
        paste.overlayStyle(icon = R.drawable.ic_akma_clipboard)
        // Limit panel height so Close remains accessible above the keyboard on small screens.
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxHeight = availableHeightPx ?: (resources.displayMetrics.heightPixels * 0.65).toInt()
        val limit = minOf(maxHeight, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: maxHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }

    fun setAvailableHeight(height: Int) {
        val next = height.coerceAtLeast(1)
        if (availableHeightPx != next) { availableHeightPx = next; requestLayout() }
    }

    private fun bindings(state: ReplyState) = adapter.callbacks(
        state, ReplyTone.entries[tone.selectedItemPosition.coerceIn(ReplyTone.entries.indices)],
    )

    fun render(state: ReplyState) {
        rendering = true
        try {
            if (lastRendered !== state) localNotice = null
            lastRendered = state
            val callbacks = bindings(state)
            status.text = state.statusText()
            when (state.phase) {
                ReplyPhase.ModelLoading -> status.text = context.getString(R.string.akma_loading_model_body)
                ReplyPhase.Analyzing -> status.text = context.getString(R.string.akma_reading_body)
                ReplyPhase.Drafting -> status.text = context.getString(R.string.akma_writing_body)
                else -> Unit
            }
            notice.text = localNotice ?: state.notice.orEmpty()
            notice.visibility = if (notice.text.isBlank()) GONE else VISIBLE
            paste.setOnClickListener { callbacks.onPaste() }
            analyze.setOnClickListener { callbacks.onAnalyze() }
            check.setOnClickListener { callbacks.onRetry() }
            recover.setOnClickListener { callbacks.onDismissError() }
            copy.cancelPendingInputEvents()
            copy.isPressed = false
            copy.setOnClickListener { callbacks.onCopy() }
            progress.visibility = if (state.busy) VISIBLE else GONE
            cancel.visibility = if (state.busy) VISIBLE else GONE
            cancel.cancelPendingInputEvents()
            cancel.isPressed = false
            cancel.setOnClickListener { callbacks.onCancelProcessing() }
            check.isEnabled = state.canRetryLocalModel
            check.visibility = if (state.canRetryLocalModel) VISIBLE else GONE
            recover.visibility = if (state.phase == ReplyPhase.Error) VISIBLE else GONE
            message.isEnabled = !state.busy
            paste.isEnabled = state.canStartProcessing
            syncText(message, state.message)
            analyze.isEnabled = state.canStartProcessing && state.message.isNotBlank() &&
                state.phase in setOf(ReplyPhase.Ready, ReplyPhase.ChoosingAction, ReplyPhase.Editing, ReplyPhase.Copied)
            tone.visibility = if (state.analysis == null) GONE else VISIBLE
            tone.isEnabled = state.canChooseDraft
            state.pendingConfirmation?.request?.tone?.let { selected ->
                if (tone.selectedItemPosition != selected.ordinal) tone.setSelection(selected.ordinal)
            }
            if (actionKey != state.analysis) {
                actionKey = state.analysis
                actions.removeAllViews()
                state.analysis?.let { analysis ->
                    actions.addView(label(analysis.summary))
                    analysis.actions.forEach { action ->
                        actions.addView(button(ActionCatalog.action(action.id)?.label ?: "Unavailable action") {}.apply {
                            tag = action.id
                        })
                    }
                }
            }
            for (index in 0 until actions.childCount) {
                val child = actions.getChildAt(index)
                child.isEnabled = state.canChooseDraft
                (child.tag as? String)?.let { id ->
                    child.cancelPendingInputEvents()
                    child.isPressed = false
                    child.setOnClickListener { bindings(state).onSelectAction(id) }
                }
            }
            confirmationArea.removeAllViews()
            confirmationArea.visibility = if (state.pendingConfirmation != null) VISIBLE else GONE
            state.pendingConfirmation?.let { displayed ->
                val confirmation = state.displayedConfirmation()
                if (confirmation != null) {
                    confirmationArea.addView(label("Review before generating"))
                    confirmationArea.addView(label("Action: ${confirmation.action.label}"))
                    confirmationArea.addView(label("Tone: ${confirmation.request.tone.name}"))
                    confirmationArea.addView(label("Message context (untrusted copied text):"))
                    confirmationArea.addView(label(confirmation.request.original.message))
                    if (confirmation.request.original.history.isNotBlank()) {
                        confirmationArea.addView(label("History (untrusted text):"))
                        confirmationArea.addView(label(confirmation.request.original.history))
                    }
                    confirmation.request.original.relationship?.takeIf { it.isNotBlank() }?.let {
                        confirmationArea.addView(label("Relationship (untrusted text):"))
                        confirmationArea.addView(label(it))
                    }
                    if (confirmation.request.userInstruction.isNotBlank()) {
                        confirmationArea.addView(label("Instruction (untrusted text):"))
                        confirmationArea.addView(label(confirmation.request.userInstruction))
                    }
                } else confirmationArea.addView(label("Selection no longer valid. Cancel and choose again."))
                confirmationArea.addView(label("Generating a draft does not send or accept anything. Cancel clears this session."))
                confirmationArea.addView(confirmationButton(context, ::blockedTap).apply {
                    text = context.getString(R.string.akma_write_reply)
                    overlayStyle(primary = true)
                    isEnabled = confirmation != null
                    setOnClickListener { adapter.confirm(displayed.id) }
                })
                confirmationArea.addView(button("Cancel and clear session") { callbacks.onCancelConfirmation() })
            }
            val editing = state.phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied)
            review.visibility = if (editing) VISIBLE else GONE
            draft.visibility = if (editing) VISIBLE else GONE
            copy.visibility = if (editing) VISIBLE else GONE
            copy.isEnabled = state.canCopy
            val copied = state.phase == ReplyPhase.Copied
            copy.text = context.getString(if (copied) R.string.akma_copied else R.string.akma_copy_reply)
            copy.overlayStyle(primary = true, icon = if (copied) R.drawable.ic_akma_check else R.drawable.ic_akma_copy, success = copied)
            syncText(draft, state.draft)
        } finally {
            rendering = false
        }
    }

    private fun blockedTap() = showNotice("Another window covers this control. Move it away and tap again.")

    private fun showNotice(text: String) {
        localNotice = text
        notice.text = text
        notice.visibility = VISIBLE
        notice.announceForAccessibility(text)
    }

    private fun pasteMessage() {
        // Only an explicit user tap enters Android's native Paste action.
        if (!message.requestFocus() || !message.hasWindowFocus()) {
            showNotice("Tap the message field, then Paste message.")
            return
        }
        try {
            message.post {
                if (message.isAttachedToWindow && message.hasWindowFocus()) {
                    context.getSystemService(InputMethodManager::class.java)
                        .showSoftInput(message, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            if (!message.onTextContextMenuItem(android.R.id.paste)) {
                showNotice(context.getString(R.string.akma_paste_empty))
            }
        } catch (_: RuntimeException) {
            showNotice("Paste unavailable. Copy text and retry, or use the Activity.")
        }
    }

    private fun label(text: String) = TextView(context).apply { this.text = text; overlayText() }

    private fun button(text: String, click: () -> Unit) = Button(context).apply {
        this.text = text
        overlayStyle()
        setOnClickListener { click() }
    }

    private fun input(hint: String, change: (String) -> Unit) = EditText(context).apply {
        this.hint = hint
        minLines = 2
        maxLines = 4
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        overlayText(16f)
        setHintTextColor(context.getColor(R.color.akma_text_secondary))
        overlaySurface(R.color.akma_bg_subtle)
        val padding = resources.getDimensionPixelSize(R.dimen.akma_space_sm)
        setPadding(padding, padding, padding, padding)
        isSaveEnabled = false
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (!rendering) { change(s.toString()); render(replies.state.value) }
            }
        })
    }

    private fun syncText(field: EditText, value: String) {
        if (field.text.toString() != value) {
            val start = field.selectionStart.coerceIn(0, value.length)
            val end = field.selectionEnd.coerceIn(0, value.length)
            field.setText(value)
            field.setSelection(start, end)
        }
    }
}
