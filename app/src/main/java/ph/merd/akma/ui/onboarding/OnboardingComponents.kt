package ph.merd.akma.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaTheme

/**
 * Figma Switch (16:284): 52 x 32 track, 24dp white knob. On uses violet 700; off uses ink 500
 * so the track stays visible on white. Disabled renders muted and ignores taps.
 */
@Composable
fun AkmaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = AkmaTheme.colors
    val knobX by animateDpAsState(if (checked) 24.dp else 4.dp, label = "knob")
    val track = when {
        !enabled -> colors.bgMuted
        checked -> colors.bgBrandStrong
        else -> colors.textSecondary
    }
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(AkmaRadius.full)
            .background(track)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset { IntOffset(knobX.roundToPx(), 0) }
                .size(24.dp)
                .shadow(2.dp, CircleShape, ambientColor = colors.textPrimary, spotColor = colors.textPrimary)
                .background(Color.White, CircleShape),
        )
    }
}

/** Figma "How to reply" step number: 24dp, brand-muted fill, Label/S brand text. */
@Composable
fun NumberBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AkmaTheme.colors.bgBrandMuted),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = AkmaTheme.type.labelS, color = AkmaTheme.colors.textBrandStrong)
    }
}

/** Figma cards on setup screens: white, 20dp radius, Elevation/1 Card, 20dp padding. */
@Composable
fun OnboardingCard(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    background: Color = AkmaTheme.colors.bgSurface,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(AkmaRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (elevated) Modifier.shadow(3.dp, shape, ambientColor = AkmaTheme.colors.textPrimary, spotColor = AkmaTheme.colors.textPrimary) else Modifier)
            .clip(shape)
            .background(background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** One numbered "How to reply" line. */
@Composable
fun HowToStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        NumberBadge(number)
        Text(text, style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textPrimary, modifier = Modifier.weight(1f))
    }
}
