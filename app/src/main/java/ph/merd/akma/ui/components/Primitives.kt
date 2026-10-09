package ph.merd.akma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaSpacing
import ph.merd.akma.ui.theme.AkmaTheme

/** Tinted 24dp-frame Figma icon drawn at [size]. Decorative unless [contentDescription] is set. */
@Composable
fun AkmaIcon(@DrawableRes icon: Int, size: Dp, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}

/** Figma Logo/Tile: brand tile with the white face mark. */
@Composable
fun LogoTile(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(AkmaTheme.colors.bgBrand),
    ) {
        Icon(
            painterResource(R.drawable.akma_logo_mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = size * 0.175f, end = size * 0.175f, top = size * 0.215f, bottom = size * 0.185f),
        )
    }
}

/** Figma Logo/Lockup: tile plus wordmark, for the top of Akma-owned screens. */
@Composable
fun LogoLockup(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(40.dp)
        Text(stringResource(R.string.app_name), style = AkmaTheme.type.wordmarkL, color = AkmaTheme.colors.textPrimary)
    }
}

enum class TagVariant { Brand, Neutral, Success }

/** Figma Tag: Brand for on-device, Neutral for language, Success for copied. */
@Composable
fun AkmaTag(text: String, variant: TagVariant, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val colors = AkmaTheme.colors
    val (bg, fg) = when (variant) {
        TagVariant.Brand -> colors.bgBrandMuted to colors.textBrandStrong
        TagVariant.Neutral -> colors.bgSubtle to colors.textSecondary
        TagVariant.Success -> colors.bgSuccessSubtle to colors.textSuccess
    }
    Row(
        modifier
            .height(24.dp)
            .clip(AkmaRadius.full)
            .background(bg)
            .padding(start = if (icon != null) 8.dp else 10.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) AkmaIcon(icon, 14.dp, fg)
        Text(text, style = AkmaTheme.type.labelS, color = fg, maxLines = 1)
    }
}

/** Figma Icon button: 40dp round. Always pass an accessible label. */
@Composable
fun CircleIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(AkmaRadius.full)
            .background(AkmaTheme.colors.bgSubtle)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AkmaIcon(icon, 20.dp, AkmaTheme.colors.textPrimary, contentDescription = contentDescription)
    }
}

enum class ButtonVariant { Primary, Secondary, Success }

/** Figma Button, 52dp. Use one Primary per screen. Disabled renders muted and ignores taps. */
@Composable
fun AkmaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val colors = AkmaTheme.colors
    val shape = RoundedCornerShape(AkmaRadius.md)
    val (bg, fg) = when {
        !enabled -> colors.bgSubtle to colors.textSecondary.copy(alpha = 0.6f)
        variant == ButtonVariant.Primary -> colors.bgBrandStrong to colors.textOnBrand
        variant == ButtonVariant.Success -> colors.bgSuccess to colors.textOnBrand
        else -> colors.bgSurface to colors.textPrimary
    }
    val border = if (variant == ButtonVariant.Secondary && enabled) Modifier.border(BorderStroke(1.5.dp, colors.borderStrong), shape) else Modifier
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .then(border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AkmaSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) AkmaIcon(icon, 20.dp, fg)
        Text(text, style = AkmaTheme.type.labelL, color = fg, textAlign = TextAlign.Center)
    }
}

/** Figma Refine button. Not placed in the journey until the coordinator offers a refine API. */
@Composable
fun RefineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true) {
    val colors = AkmaTheme.colors
    val borderColor = when {
        selected -> colors.bgBrand
        enabled -> colors.borderStrong
        else -> colors.borderDefault
    }
    val textColor = when {
        selected -> colors.textBrandStrong
        enabled -> colors.textPrimary
        else -> colors.textSecondary.copy(alpha = 0.6f)
    }
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(AkmaRadius.full)
            .background(colors.bgSurface)
            .border(1.5.dp, borderColor, AkmaRadius.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AkmaTheme.type.labelM, color = textColor)
    }
}

/** Marks sample content in previews and test screens. Never shown in production journeys. */
@Composable
fun PreviewBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.akma_preview_badge),
        style = AkmaTheme.type.labelS,
        color = AkmaTheme.colors.textOnBrand,
        modifier = modifier
            .clip(AkmaRadius.full)
            .background(Color(0xFFB45309))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
