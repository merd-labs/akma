package ph.merd.akma.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.ui.copyDraft
import ph.merd.akma.ui.statusText

/** Views keep overlay lifecycle independent from Compose. Input is never saved or autofilled. */
class OverlayPanel(context: Context, private val replies: ReplyCoordinator, close: () -> Unit) : ScrollView(context) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 16, 20, 16)
        setBackgroundColor(Color.WHITE)
    }
    private val status = label("")
    private val notice = label("")
    private val progress = ProgressBar(context)
    private val cancel = button("Cancel", replies::cancel)
    private val check = button("Check local model", replies::initialize)
    private val recover = button("Dismiss error and retry", replies::recover)
    private val message = input("Message — long-press to Paste", replies::setMessage)
    private val analyze = button("Analyze locally", replies::analyze)
    private val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val tone = Spinner(context).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, ReplyTone.entries.map { it.name })
    }
    private val draft = input("Editable draft", replies::editDraft)
    private val copy = button("Copy draft") {
        if (copyDraft(context, replies.state.value)) replies.copied()
        else notice.text = "Copy failed. Select the draft and copy manually."
    }
    private var rendering = false
    private var actionKey: Any? = null

    init {
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        addView(content)
        content.addView(label("Akma").apply { textSize = 20f; setTypeface(null, Typeface.BOLD) })
        content.addView(button("Close overlay", close))
        listOf(status, notice, progress, cancel, check, recover, message, analyze, tone, actions, draft, copy).forEach(content::addView)
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
            check.isEnabled = !state.busy
            recover.visibility = if (state.phase == ReplyPhase.Error) VISIBLE else GONE
            message.isEnabled = !state.busy
            syncText(message, state.message)
            analyze.isEnabled = !state.busy && state.phase != ReplyPhase.ModelUnavailable
            tone.visibility = if (state.analysis == null) GONE else VISIBLE
            tone.isEnabled = !state.busy
            if (actionKey != state.analysis) {
                actionKey = state.analysis
                actions.removeAllViews()
                state.analysis?.let { analysis ->
                    actions.addView(label(analysis.summary))
                    analysis.actions.forEach { action ->
                        actions.addView(button(action.label) { replies.draft(action.id, ReplyTone.entries[tone.selectedItemPosition]) })
                    }
                }
            }
            for (index in 0 until actions.childCount) actions.getChildAt(index).isEnabled = !state.busy
            val editing = state.phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied)
            draft.visibility = if (editing) VISIBLE else GONE
            copy.visibility = if (editing) VISIBLE else GONE
            copy.isEnabled = state.canCopy
            syncText(draft, state.draft)
        } finally {
            rendering = false
        }
    }

    private fun label(text: String) = TextView(context).apply { this.text = text; setTextColor(Color.BLACK) }

    private fun button(text: String, click: () -> Unit) = Button(context).apply {
        this.text = text
        setOnClickListener { click() }
    }

    private fun input(hint: String, change: (String) -> Unit) = EditText(context).apply {
        this.hint = hint
        minLines = 2
        maxLines = 4
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setTextColor(Color.BLACK)
        setHintTextColor(Color.DKGRAY)
        isSaveEnabled = false
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!rendering) { change(s.toString()); render(replies.state.value) }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun syncText(field: EditText, value: String) {
        if (field.text.toString() != value) {
            field.setText(value)
            field.setSelection(value.length)
        }
    }
}
