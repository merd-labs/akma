package ph.merd.akma.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import ph.merd.akma.R
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.theme.AkmaTokens

/**
 * Hosts an obscured-touch-safe native button (confirmationButton / copyButton from
 * ReplyPresentation) in Compose, styled like the Figma Button. The factory's touch filtering is kept;
 * only appearance, label and enabled state change here.
 */
@Composable
internal fun ProtectedAkmaButton(
    factory: (Context) -> Button,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    @DrawableRes icon: Int? = null,
) {
    AndroidView(
        factory = { context -> factory(context).apply { applyAkmaStyle() } },
        update = { button ->
            button.text = text
            button.contentDescription = text
            button.isEnabled = enabled
            button.applyAkmaColors(variant, icon)
            button.setOnClickListener { onClick() }
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    )
}

private fun Button.applyAkmaStyle() {
    isAllCaps = false
    stateListAnimator = null
    elevation = 0f
    gravity = Gravity.CENTER
    minHeight = dp(52f)
    minimumHeight = dp(52f)
    setPadding(dp(20f), 0, dp(20f), 0)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    typeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_bold)
    compoundDrawablePadding = dp(8f)
}

private fun Button.applyAkmaColors(variant: ButtonVariant, @DrawableRes icon: Int?) {
    val fill = when {
        !isEnabled -> AkmaTokens.BG_SUBTLE
        variant == ButtonVariant.Success -> AkmaTokens.BG_SUCCESS
        variant == ButtonVariant.Secondary -> AkmaTokens.BG_SURFACE
        else -> AkmaTokens.BG_BRAND_STRONG
    }.toInt()
    val textColor = when {
        !isEnabled -> (AkmaTokens.TEXT_SECONDARY and 0x00FFFFFFL or 0x99000000L).toInt()
        variant == ButtonVariant.Secondary -> AkmaTokens.TEXT_PRIMARY.toInt()
        else -> AkmaTokens.TEXT_ON_BRAND.toInt()
    }
    val shape = GradientDrawable().apply {
        cornerRadius = dp(16f).toFloat()
        setColor(fill)
        if (variant == ButtonVariant.Secondary && isEnabled) setStroke(dp(1.5f), AkmaTokens.BORDER_STRONG.toInt())
    }
    background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
    setTextColor(textColor)
    val drawable = icon?.let { ContextCompat.getDrawable(context, it)?.mutate() }?.apply {
        setTint(textColor)
        setBounds(0, 0, dp(20f), dp(20f))
    }
    setCompoundDrawablesRelative(drawable, null, null, null)
}

private fun Button.dp(value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
