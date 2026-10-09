package ph.merd.akma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaSpacing
import ph.merd.akma.ui.theme.AkmaTheme

/** Figma "Akma panel": white sheet, 28dp top corners, handle, header, 16dp gaps. */
@Composable
fun AkmaPanel(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = AkmaRadius.xl, topEnd = AkmaRadius.xl)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = AkmaTheme.colors.textPrimary, spotColor = AkmaTheme.colors.textPrimary)
            .clip(shape)
            .background(AkmaTheme.colors.bgSurface)
            .padding(start = AkmaSpacing.lg, end = AkmaSpacing.lg, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(AkmaSpacing.md),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AkmaTheme.colors.bgMuted),
            )
        }
        PanelHeader(onClose)
        content()
    }
}

/** Figma Panel header: tile, name, on-device status, close. */
@Composable
fun PanelHeader(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LogoTile(32.dp)
        Text(stringResource(R.string.app_name), style = AkmaTheme.type.wordmark, color = AkmaTheme.colors.textPrimary)
        AkmaTag(stringResource(R.string.akma_on_device), TagVariant.Brand, icon = R.drawable.ic_akma_lock)
        Spacer(Modifier.weight(1f))
        CircleIconButton(R.drawable.ic_akma_close, stringResource(R.string.akma_close), onClose)
    }
}

/** Small icon-plus-caption line, e.g. "No internet needed" or the privacy note. */
@Composable
fun NoteRow(icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        AkmaIcon(icon, 18.dp, AkmaTheme.colors.textSecondary)
        Text(text, style = AkmaTheme.type.caption, color = AkmaTheme.colors.textSecondary)
    }
}

/** Section label above chips and the reply ("What do you want to say?"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AkmaTheme.type.bodyMStrong, color = AkmaTheme.colors.textPrimary, modifier = modifier)
}

@Composable
internal fun SkeletonBar(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width))
            .height(height)
            .clip(RoundedCornerShape(7.dp))
            .background(AkmaTheme.colors.bgBrandMuted),
    )
}

@Composable
internal fun IconTile(icon: Int, tileSize: Dp, iconSize: Dp, radius: Dp, tint: Color = AkmaTheme.colors.textPrimary) {
    Box(
        Modifier
            .size(tileSize)
            .clip(RoundedCornerShape(radius))
            .background(AkmaTheme.colors.bgBrandMuted),
        contentAlignment = Alignment.Center,
    ) {
        AkmaIcon(icon, iconSize, tint)
    }
}
