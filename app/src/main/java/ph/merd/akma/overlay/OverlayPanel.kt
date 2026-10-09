package ph.merd.akma.overlay

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import ph.merd.akma.R
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
import ph.merd.akma.ui.selectDraft
import ph.merd.akma.ui.confirmDisplayedDraft
import ph.merd.akma.ui.cancelDisplayedDraft
import ph.merd.akma.ui.confirmationButton
import ph.merd.akma.ui.copyButton
import ph.merd.akma.ui.canRetryLocalModel
import ph.merd.akma.ui.retryLocalModel
import ph.merd.akma.ui.cancelDisplayedProcessing
import ph.merd.akma.ui.AkmaText
import ph.merd.akma.ui.akmaButton
import ph.merd.akma.ui.akmaCard
import ph.merd.akma.ui.akmaDp
import ph.merd.akma.ui.akmaInput
import ph.merd.akma.ui.akmaProgress
import ph.merd.akma.ui.akmaSheet
import ph.merd.akma.ui.akmaText
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.theme.AkmaTokens

/** Views keep overlay lifecycle independent from Compose. Input is never saved or autofilled. */
class OverlayPanel(context: Context, private val replies: ReplyCoordinator, close: () -> Unit) : LinearLayout(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, context.akmaDp(16f))
    }
    private val status = label("").apply { akmaText(AkmaText.TitleS) }
    private val notice = label("").apply {
        akmaText(AkmaText.Body)
        visibility = GONE
        // Hide the empty line so it does not leave a gap; any code path that sets text shows it.
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { visibility = if (s.isNullOrEmpty()) GONE else VISIBLE }
        })
    }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = true
        akmaProgress()
    }
    private val cancel = button("Cancel and clear session", ButtonVariant.Secondary) {}
    private val check = button("Retry local model", ButtonVariant.Primary, replies::retryLocalModel)
    private val recover = button("Dismiss error and retry", ButtonVariant.Secondary, replies::recover)
    private val message = input("Message — tap Paste message", replies::setMessage)
    private val paste = button("Paste message", ButtonVariant.Secondary) {
        // Service-hosted Views may have no floating selection toolbar. Use Android's native
        // paste action, only after this user click and while our input window has focus.
        if (!message.requestFocus() || !message.hasWindowFocus()) {
            notice.text = "Tap the message field, then Paste message."
        } else {
            try {
                // Focus alone does not display the IME when a button initiated Paste.
                // This explicit user action requests normal IME display, never forced display.
                message.post {
                    if (message.isAttachedToWindow && message.hasWindowFocus()) {
                        context.getSystemService(InputMethodManager::class.java)
                            .showSoftInput(message, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                if (!message.onTextContextMenuItem(android.R.id.paste)) {
                    notice.text = "Paste unavailable. Copy text and retry, or use the Activity."
                }
            } catch (_: RuntimeException) {
                notice.text = "Paste unavailable. Copy text and retry, or use the Activity."
            }
        }
    }
    private val analyze = button("Analyze locally", ButtonVariant.Primary) { if (replies.state.value.canStartProcessing) replies.analyze() }
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val confirmationArea = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.akmaCard(AkmaTokens.BG_BRAND_SUBTLE, 20f, 1.5f, AkmaTokens.BG_BRAND)
        val pad = context.akmaDp(16f)
        setPadding(pad, pad, pad, pad)
    }
    private val tone = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ReplyTone.entries.map { it.name })
        background = context.akmaCard(AkmaTokens.BG_SUBTLE, 16f)
        minimumHeight = context.akmaDp(48f)
    }
    private val draft = input("Editable draft", replies::editDraft)
    private val review = label("Review before copying. Paste and send manually.").apply { akmaText(AkmaText.Body) }
    private val copy = copyButton(context).apply {
        akmaButton(ButtonVariant.Primary, R.drawable.ic_akma_copy)
        setOnClickListener {
            if (copyDraft(context, replies.state.value)) replies.copied()
            else notice.text = "Copy failed. Select the draft and copy manually."
        }
    }
    private var rendering = false
    private var actionKey: Any? = null

    init {
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        orientation = VERTICAL
        // Figma "Akma panel": white bottom sheet, 28dp top corners, handle, header, 20dp sides.
        background = context.akmaSheet()
        elevation = context.akmaDp(16f).toFloat()
        val side = context.akmaDp(20f)
        setPadding(side, context.akmaDp(8f), side, 0)
        addView(handle(), LayoutParams(context.akmaDp(36f), context.akmaDp(4f)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(header(close), LayoutParams(LayoutParams.MATCH_PARENT, context.akmaDp(40f)).apply { topMargin = context.akmaDp(16f) })
        addView(ScrollView(context).apply { addView(content) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f))
        listOf(status, notice, progress, cancel, check, recover, message, paste, analyze, tone, actions, confirmationArea, review, draft, copy).forEach {
            content.addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = context.akmaDp(12f) })
        }
        // Limit panel height so Close remains accessible above the keyboard on small screens.
        layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxHeight = (resources.displayMetrics.heightPixels * 0.65).toInt()
        val limit = minOf(maxHeight, MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 } ?: maxHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST))
    }

    fun render(state: ReplyState) {
        rendering = true
        try {
            status.text = state.statusText()
            notice.text = state.notice.orEmpty()
            progress.visibility = if (state.busy) VISIBLE else GONE
            cancel.visibility = if (state.busy) VISIBLE else GONE
            cancel.cancelPendingInputEvents()
            cancel.isPressed = false
            cancel.setOnClickListener { replies.cancelDisplayedProcessing(state) }
            check.isEnabled = state.canRetryLocalModel
            check.visibility = if (state.canRetryLocalModel) VISIBLE else GONE
            recover.visibility = if (state.phase == ReplyPhase.Error) VISIBLE else GONE
            message.isEnabled = !state.busy
            paste.isEnabled = !state.busy
            syncText(message, state.message)
            analyze.isEnabled = state.canStartProcessing && state.phase != ReplyPhase.ModelUnavailable
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
                        actions.addView(button(ActionCatalog.action(action.id)?.label ?: "Unavailable action") {
                            replies.selectDraft(action.id, ReplyTone.entries[tone.selectedItemPosition])
                        })
                    }
                }
            }
            for (index in 0 until actions.childCount) actions.getChildAt(index).isEnabled = state.canChooseDraft
            confirmationArea.removeAllViews()
            confirmationArea.visibility = if (state.pendingConfirmation != null) VISIBLE else GONE
            state.pendingConfirmation?.let { displayed ->
                val confirmation = state.displayedConfirmation()
                if (confirmation != null) {
                    confirmationArea.addView(label("Review before generating").apply { akmaText(AkmaText.TitleS) })
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
                confirmationArea.addView(confirmationButton(context).apply {
                    akmaButton(ButtonVariant.Primary)
                    isEnabled = confirmation != null
                    setOnClickListener { replies.confirmDisplayedDraft(displayed.id) }
                })
                confirmationArea.addView(button("Cancel and clear session") { replies.cancelDisplayedDraft(displayed.id) })
            }
            val editing = state.phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied)
            review.visibility = if (editing) VISIBLE else GONE
            draft.visibility = if (editing) VISIBLE else GONE
            copy.visibility = if (editing) VISIBLE else GONE
            copy.isEnabled = state.canCopy
            syncText(draft, state.draft)
        } finally {
            rendering = false
        }
    }

    private fun label(text: String) = TextView(context).apply { this.text = text; akmaText(AkmaText.BodyPrimary) }

    private fun button(text: String, variant: ButtonVariant = ButtonVariant.Secondary, click: () -> Unit) = Button(context).apply {
        this.text = text
        akmaButton(variant)
        setOnClickListener { click() }
    }

    private fun handle() = View(context).apply { background = context.akmaCard(AkmaTokens.BG_MUTED, 2f) }

    /** Figma Panel header: logo tile, wordmark, On-device tag, round Close. */
    private fun header(close: () -> Unit) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val tile = FrameLayout(context).apply {
            background = context.akmaCard(AkmaTokens.BG_BRAND, 12f * 32f / 40f)
            addView(ImageView(context).apply { setImageResource(R.drawable.akma_logo_mark) },
                FrameLayout.LayoutParams(context.akmaDp(20.8f), context.akmaDp(19.2f), Gravity.CENTER))
        }
        addView(tile, LayoutParams(context.akmaDp(32f), context.akmaDp(32f)))
        addView(TextView(context).apply { text = context.getString(R.string.app_name); akmaText(AkmaText.Wordmark) },
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = context.akmaDp(8f) })
        addView(TextView(context).apply {
            text = context.getString(R.string.akma_on_device)
            akmaText(AkmaText.LabelS)
            background = context.akmaCard(AkmaTokens.BG_BRAND_MUTED, 12f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.akmaDp(8f), 0, context.akmaDp(10f), 0)
            compoundDrawablePadding = context.akmaDp(4f)
            val lock = context.getDrawable(R.drawable.ic_akma_lock)?.mutate()?.apply {
                setTint(AkmaTokens.TEXT_BRAND_STRONG.toInt())
                setBounds(0, 0, context.akmaDp(14f), context.akmaDp(14f))
            }
            setCompoundDrawablesRelative(lock, null, null, null)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, context.akmaDp(24f)).apply { marginStart = context.akmaDp(8f) })
        addView(View(context), LayoutParams(0, 0, 1f))
        addView(ImageButton(context).apply {
            setImageResource(R.drawable.ic_akma_close)
            imageTintList = android.content.res.ColorStateList.valueOf(AkmaTokens.TEXT_PRIMARY.toInt())
            background = context.akmaCard(AkmaTokens.BG_SUBTLE, 20f)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = context.akmaDp(10f)
            setPadding(pad, pad, pad, pad)
            contentDescription = "Close panel"
            setOnClickListener { close() }
        }, LayoutParams(context.akmaDp(40f), context.akmaDp(40f)))
    }

    private fun input(hint: String, change: (String) -> Unit) = EditText(context).apply {
        this.hint = hint
        minLines = 2
        maxLines = 4
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        akmaInput()
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
