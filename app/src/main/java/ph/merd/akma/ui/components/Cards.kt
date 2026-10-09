package ph.merd.akma.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.domain.AnalysisSource
import ph.merd.akma.ui.CategoryPresentation
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaSpacing
import ph.merd.akma.ui.theme.AkmaTheme

/**
 * Figma "Reading" block (23:532): title, body, brand progress, skeleton intent card, offline note.
 * Used while the model loads or reads. [onCancel] keeps the user in control of long work.
 */
@Composable
fun ProcessingCard(title: String, body: String, onCancel: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AkmaSpacing.md)) {
        Column(verticalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
            Text(title, style = AkmaTheme.type.titleM, color = AkmaTheme.colors.textPrimary)
            Text(body, style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary)
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .semantics { contentDescription = title },
                color = AkmaTheme.colors.bgBrand,
                trackColor = AkmaTheme.colors.bgBrandMuted,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AkmaRadius.lg))
                .background(AkmaTheme.colors.bgBrandSubtle)
                .padding(AkmaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AkmaSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AkmaRadius.sm))
                        .background(AkmaTheme.colors.bgBrandMuted),
                )
                Column(verticalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
                    SkeletonBar(90.dp, 10.dp)
                    SkeletonBar(170.dp, 14.dp)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AkmaTheme.colors.bgSurface),
            )
        }
        NoteRow(R.drawable.ic_akma_wifi_off, stringResource(R.string.akma_no_internet))
        if (onCancel != null) {
            AkmaButton(stringResource(R.string.akma_cancel), onCancel, variant = ButtonVariant.Secondary)
        }
    }
}

/**
 * Figma Intent card (18:141): what Akma understood, plus the copied message (2 lines max)
 * so the user can check Akma read the right thing. [source] adds the provenance caption.
 */
@Composable
fun IntentCard(
    categoryId: String,
    message: String,
    source: AnalysisSource,
    modifier: Modifier = Modifier,
    language: String? = null,
    demo: Boolean = false,
) {
    val category = CategoryPresentation.category(categoryId)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AkmaRadius.lg))
            .background(AkmaTheme.colors.bgBrandSubtle)
            .padding(AkmaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AkmaSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconTile(category?.icon ?: R.drawable.ic_akma_message, 40.dp, 22.dp, AkmaRadius.sm)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.akma_detected_intent), style = AkmaTheme.type.caption, color = AkmaTheme.colors.textSecondary)
                    language?.let { AkmaTag(it, TagVariant.Neutral, icon = R.drawable.ic_akma_message) }
                }
                Text(
                    category?.let { stringResource(it.label) } ?: stringResource(R.string.akma_category_other),
                    style = AkmaTheme.type.titleS,
                    color = AkmaTheme.colors.textPrimary,
                )
            }
        }
        QuotedMessage(message, maxLines = 2)
        // Provenance: demo data is never presented as model output.
        (if (demo) R.string.akma_demo_caption else CategoryPresentation.sourceCaption(source))?.let {
            Text(stringResource(it), style = AkmaTheme.type.caption, color = AkmaTheme.colors.textSecondary)
        }
    }
}

/** The user's own message in the "Their message" bubble shape. */
@Composable
fun QuotedMessage(message: String, modifier: Modifier = Modifier, maxLines: Int = 2) {
    val shape = RoundedCornerShape(topStart = AkmaRadius.md, topEnd = AkmaRadius.md, bottomEnd = AkmaRadius.md, bottomStart = AkmaRadius.xs)
    Text(
        "“$message”",
        style = AkmaTheme.type.bodyM,
        color = AkmaTheme.colors.textPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AkmaTheme.colors.bgSurface)
            .border(1.dp, AkmaTheme.colors.borderDefault, shape)
            .padding(horizontal = AkmaSpacing.sm, vertical = 10.dp),
    )
}

/** Figma Empty state (25:1688): icon tile, title, body. Pair with an AkmaButton below. */
@Composable
fun EmptyStateCard(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = AkmaSpacing.sm, bottom = AkmaSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AkmaSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(icon, 56.dp, 28.dp, AkmaRadius.md, tint = AkmaTheme.colors.bgBrandStrong)
        Text(title, style = AkmaTheme.type.titleM, color = AkmaTheme.colors.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * Paste step for the Activity journey (the overlay pastes from its own control).
 * The field never reads the clipboard; [onPaste] must run only from the explicit Paste tap.
 */
@Composable
fun MessageInputCard(
    message: String,
    maxLength: Int,
    onMessageChange: (String) -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inputModifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AkmaRadius.md)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.akma_paste_title), style = AkmaTheme.type.bodyMStrong, color = AkmaTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            RefineButton(stringResource(R.string.akma_paste), onPaste, enabled = enabled)
        }
        BasicTextField(
            value = message,
            onValueChange = { if (it.length <= maxLength) onMessageChange(it) },
            enabled = enabled,
            textStyle = AkmaTheme.type.bodyL.copy(color = AkmaTheme.colors.textPrimary),
            cursorBrush = SolidColor(AkmaTheme.colors.bgBrandStrong),
            modifier = inputModifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(shape)
                .background(AkmaTheme.colors.bgSurface)
                .border(1.5.dp, AkmaTheme.colors.borderStrong, shape)
                .padding(AkmaSpacing.md),
            decorationBox = { inner ->
                Box {
                    if (message.isEmpty()) {
                        Text(stringResource(R.string.akma_paste_hint), style = AkmaTheme.type.bodyL, color = AkmaTheme.colors.textSecondary)
                    }
                    inner()
                }
            },
        )
        Text(
            stringResource(R.string.akma_char_count, message.length, maxLength),
            style = AkmaTheme.type.caption,
            color = AkmaTheme.colors.textSecondary,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** Figma setup step (22:296): number badge, title, body, optional trailing or action content. */
@Composable
fun SetupStepCard(
    number: Int,
    title: String,
    body: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AkmaTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = if (action == null) Alignment.CenterVertically else Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(AkmaRadius.full)
                .background(if (active) colors.bgBrandStrong else colors.bgSubtle),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = AkmaTheme.type.labelM, color = if (active) colors.textOnBrand else colors.textSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = AkmaTheme.type.bodyLStrong, color = if (active) colors.textPrimary else colors.textSecondary)
            Text(body, style = AkmaTheme.type.bodyM, color = colors.textSecondary)
            if (action != null) Box(Modifier.padding(top = AkmaSpacing.xs)) { action() }
        }
        trailing?.invoke()
    }
}

/** Figma Setup card container (22:296): white, 20dp radius, Elevation/1 Card. */
@Composable
fun SetupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(AkmaRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, ambientColor = AkmaTheme.colors.textPrimary, spotColor = AkmaTheme.colors.textPrimary)
            .clip(shape)
            .background(AkmaTheme.colors.bgSurface)
            .padding(AkmaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AkmaSpacing.lg),
    ) { content() }
}
