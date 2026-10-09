package ph.merd.akma.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.ui.ReplyUi
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaSpacing
import ph.merd.akma.ui.theme.AkmaTheme

private val ReplyBubbleShape = RoundedCornerShape(
    topStart = AkmaRadius.lg,
    topEnd = AkmaRadius.lg,
    bottomEnd = AkmaRadius.xs,
    bottomStart = AkmaRadius.lg,
)

/**
 * Figma Reply card (18:193). Draft: editable, focus border while editing. Writing: skeleton while
 * the on-device model runs. Text comes only from the coordinator draft; this card never fills it.
 */
@Composable
fun ReplyCard(reply: ReplyUi, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AkmaTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.akma_your_reply), style = AkmaTheme.type.bodyMStrong, color = colors.textPrimary)
            Spacer(Modifier.weight(1f))
            when (reply) {
                ReplyUi.Writing -> Text(stringResource(R.string.akma_writing_on_phone), style = AkmaTheme.type.caption, color = colors.bgBrandStrong)
                is ReplyUi.Draft -> {
                    AkmaIcon(R.drawable.ic_akma_edit, 16.dp, colors.textSecondary)
                    Text(stringResource(R.string.akma_tap_to_edit), style = AkmaTheme.type.caption, color = colors.textSecondary)
                }
            }
        }
        when (reply) {
            ReplyUi.Writing -> WritingSkeleton()
            is ReplyUi.Draft -> {
                val interaction = remember { MutableInteractionSource() }
                val focused by interaction.collectIsFocusedAsState()
                BasicTextField(
                    value = reply.text,
                    onValueChange = onTextChange,
                    interactionSource = interaction,
                    textStyle = AkmaTheme.type.bodyL.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.bgBrandStrong),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ReplyBubbleShape)
                        .background(colors.bgBrandSubtle)
                        .border(if (focused) 1.5.dp else 1.dp, if (focused) colors.bgBrand else colors.borderDefault, ReplyBubbleShape)
                        .padding(AkmaSpacing.md),
                )
            }
        }
    }
}

@Composable
private fun WritingSkeleton() {
    val label = stringResource(R.string.akma_writing_on_phone)
    val pulse by rememberInfiniteTransition(label = "writing").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "writing-alpha",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(ReplyBubbleShape)
            .background(AkmaTheme.colors.bgBrandSubtle)
            .border(1.dp, AkmaTheme.colors.borderDefault, ReplyBubbleShape)
            .padding(AkmaSpacing.md)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBar(null, 12.dp, Modifier.alpha(pulse))
        SkeletonBar(null, 12.dp, Modifier.alpha(pulse))
        SkeletonBar(150.dp, 12.dp, Modifier.alpha(pulse))
    }
}

/** Copy reply / Copied button plus the next-step hint. Copy runs only from this explicit tap. */
@Composable
fun CopyReplyButton(copied: Boolean, enabled: Boolean, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AkmaSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        if (copied) {
            AkmaButton(stringResource(R.string.akma_copied), onCopy, variant = ButtonVariant.Success, icon = R.drawable.ic_akma_check)
            Text(stringResource(R.string.akma_copied_hint), style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary)
        } else {
            AkmaButton(stringResource(R.string.akma_copy_reply), onCopy, enabled = enabled, icon = R.drawable.ic_akma_copy)
        }
    }
}
