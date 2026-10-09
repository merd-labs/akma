package ph.merd.akma.overlay

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import ph.merd.akma.R

/** Views consume Danielle's resource mirrors; visual token definitions stay with the UI owner. */
internal fun View.overlayDp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun TextView.overlayText(size: Float = 14f, font: Int = R.font.plus_jakarta_sans_regular) {
    textSize = size
    typeface = ResourcesCompat.getFont(context, font)
    setTextColor(context.getColor(R.color.akma_text_primary))
    includeFontPadding = false
}

internal fun View.overlaySurface(color: Int = R.color.akma_bg_surface, radius: Int = R.dimen.akma_radius_md) {
    background = GradientDrawable().apply {
        cornerRadius = resources.getDimension(radius)
        setColor(context.getColor(color))
    }
}

internal fun Button.overlayStyle(primary: Boolean = false, icon: Int? = null, success: Boolean = false) {
    overlayText(16f, R.font.plus_jakarta_sans_bold)
    isAllCaps = false
    stateListAnimator = null
    elevation = 0f
    minHeight = resources.getDimensionPixelSize(R.dimen.akma_button_height)
    minimumHeight = minHeight
    setPadding(overlayDp(16), overlayDp(8), overlayDp(16), overlayDp(8))
    val foreground = context.getColor(if (primary || success) R.color.akma_text_on_brand else R.color.akma_text_primary)
    val disabled = context.getColor(R.color.akma_text_secondary)
    val colors = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(disabled, foreground))
    setTextColor(colors)
    fun shape(enabled: Boolean) = GradientDrawable().apply {
        cornerRadius = resources.getDimension(R.dimen.akma_radius_md)
        setColor(context.getColor(when {
            !enabled -> R.color.akma_bg_subtle
            success -> R.color.akma_bg_success
            primary -> R.color.akma_bg_brand_strong
            else -> R.color.akma_bg_surface
        }))
        if (enabled && !primary && !success) setStroke(overlayDp(1).coerceAtLeast(1), context.getColor(R.color.akma_border_strong))
    }
    val states = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), shape(false))
        addState(intArrayOf(), shape(true))
    }
    background = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.akma_bg_brand_muted)), states, null)
    val drawable = icon?.let { ContextCompat.getDrawable(context, it)?.mutate() }?.apply {
        setBounds(0, 0, overlayDp(20), overlayDp(20))
    }
    setCompoundDrawablesRelative(drawable, null, null, null)
    compoundDrawableTintList = colors
    compoundDrawablePadding = overlayDp(8)
}
