package ph.merd.akma.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import ph.merd.akma.R
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.theme.AkmaTokens

/**
 * Akma tokens for Android Views (the overlay, ADR-002, and native protected buttons).
 * Mirrors AkmaTheme so Views and Compose surfaces look the same. Appearance only; never behaviour.
 */
internal enum class AkmaText(val sizeSp: Float, val lineHeightSp: Float, @param:androidx.annotation.FontRes val font: Int, val color: Long) {
    TitleM(20f, 28f, R.font.plus_jakarta_sans_bold, AkmaTokens.TEXT_PRIMARY),
    TitleS(17f, 24f, R.font.plus_jakarta_sans_bold, AkmaTokens.TEXT_PRIMARY),
    Wordmark(17f, 24f, R.font.plus_jakarta_sans_extrabold, AkmaTokens.TEXT_PRIMARY),
    Body(14f, 20f, R.font.plus_jakarta_sans_regular, AkmaTokens.TEXT_SECONDARY),
    BodyPrimary(14f, 20f, R.font.plus_jakarta_sans_regular, AkmaTokens.TEXT_PRIMARY),
    BodyStrong(14f, 20f, R.font.plus_jakarta_sans_semibold, AkmaTokens.TEXT_PRIMARY),
    LabelS(12f, 16f, R.font.plus_jakarta_sans_bold, AkmaTokens.TEXT_BRAND_STRONG),
}

internal fun Context.akmaDp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

internal fun TextView.akmaText(style: AkmaText) {
    typeface = ResourcesCompat.getFont(context, style.font)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
    setLineSpacing(0f, style.lineHeightSp / style.sizeSp)
    setTextColor(style.color.toInt())
    includeFontPadding = false
}

private fun rounded(context: Context, fill: Long, radiusDp: Float, strokeDp: Float = 0f, stroke: Long = 0L) = GradientDrawable().apply {
    cornerRadius = context.akmaDp(radiusDp).toFloat()
    setColor(fill.toInt())
    if (strokeDp > 0f) setStroke(context.akmaDp(strokeDp), stroke.toInt())
}

private val disabledText = (AkmaTokens.TEXT_SECONDARY and 0x00FFFFFFL or 0x99000000L).toInt()

/** Figma Button: 52dp, 16dp radius, Label/L. Enabled and disabled colours come from state lists. */
internal fun Button.akmaButton(variant: ButtonVariant, @DrawableRes icon: Int? = null) {
    isAllCaps = false
    stateListAnimator = null
    elevation = 0f
    gravity = Gravity.CENTER
    minHeight = context.akmaDp(52f)
    minimumHeight = context.akmaDp(52f)
    setPadding(context.akmaDp(20f), 0, context.akmaDp(20f), 0)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)
    compoundDrawablePadding = context.akmaDp(8f)
    val enabledFill = when (variant) {
        ButtonVariant.Primary -> rounded(context, AkmaTokens.BG_BRAND_STRONG, 16f)
        ButtonVariant.Success -> rounded(context, AkmaTokens.BG_SUCCESS, 16f)
        ButtonVariant.Secondary -> rounded(context, AkmaTokens.BG_SURFACE, 16f, 1.5f, AkmaTokens.BORDER_STRONG)
    }
    val states = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), rounded(context, AkmaTokens.BG_SUBTLE, 16f))
        addState(intArrayOf(), enabledFill)
    }
    background = RippleDrawable(ColorStateList.valueOf(0x33000000), states, null)
    val enabledText = if (variant == ButtonVariant.Secondary) AkmaTokens.TEXT_PRIMARY.toInt() else AkmaTokens.TEXT_ON_BRAND.toInt()
    val textColors = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(disabledText, enabledText))
    setTextColor(textColors)
    val drawable = icon?.let { ContextCompat.getDrawable(context, it)?.mutate() }?.apply {
        setTintList(textColors)
        setBounds(0, 0, context.akmaDp(20f), context.akmaDp(20f))
    }
    setCompoundDrawablesRelative(drawable, null, null, null)
}

/** Figma message field: 16dp radius, strong border, brand border while focused. */
internal fun EditText.akmaInput() {
    akmaText(AkmaText.BodyPrimary)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    setHintTextColor(AkmaTokens.TEXT_SECONDARY.toInt())
    val pad = context.akmaDp(16f)
    setPadding(pad, context.akmaDp(14f), pad, context.akmaDp(14f))
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), rounded(context, AkmaTokens.BG_SURFACE, 16f, 1.5f, AkmaTokens.BG_BRAND))
        addState(intArrayOf(), rounded(context, AkmaTokens.BG_SURFACE, 16f, 1.5f, AkmaTokens.BORDER_STRONG))
    }
}

internal fun ProgressBar.akmaProgress() {
    indeterminateTintList = ColorStateList.valueOf(AkmaTokens.BG_BRAND.toInt())
}

/** Figma card surfaces: rounded fill, optional border. */
internal fun Context.akmaCard(fill: Long, radiusDp: Float = 20f, strokeDp: Float = 0f, stroke: Long = 0L) =
    rounded(this, fill, radiusDp, strokeDp, stroke)

/** Figma "Akma panel": white sheet with 28dp top corners. */
internal fun Context.akmaSheet() = GradientDrawable().apply {
    val r = akmaDp(28f).toFloat()
    cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    setColor(AkmaTokens.BG_SURFACE.toInt())
}
