package ph.merd.akma.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.ui.ActionUi
import ph.merd.akma.ui.CategoryPresentation
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaSpacing
import ph.merd.akma.ui.theme.AkmaTheme

/** Figma Action chip (16:145). Unselected: white, ink 300 border. Selected: violet 700 with check. */
@Composable
fun ActionChip(action: ActionUi, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AkmaTheme.colors
    val bg = if (selected) colors.bgBrandStrong else colors.bgSurface
    val fg = when {
        selected -> colors.textOnBrand
        enabled -> colors.textPrimary
        else -> colors.textSecondary
    }
    val border = if (selected) Modifier else Modifier.border(1.5.dp, if (enabled) colors.borderStrong else colors.borderDefault, AkmaRadius.full)
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(AkmaRadius.full)
            .background(bg)
            .then(border)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (selected) R.drawable.ic_akma_check else CategoryPresentation.actionIcon(action.id)
        AkmaIcon(icon, 18.dp, if (selected) colors.textOnBrand else colors.textSecondary)
        Text(action.label, style = AkmaTheme.type.labelM, color = fg)
    }
}

/**
 * "What do you want to say?" with 1–3 single-select chips.
 * Selecting only stages a choice through [onSelect]; it must never start drafting.
 */
@Composable
fun ActionChipGroup(
    actions: List<ActionUi>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
        SectionLabel(stringResource(R.string.akma_what_to_say))
        FlowRow(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AkmaSpacing.xs),
        ) {
            actions.forEach { action ->
                ActionChip(action, selected = action.id == selectedId, enabled = enabled, onClick = { onSelect(action.id) })
            }
        }
    }
}

/** Figma Tone selector (16:250): three segments; the selected one is a raised white pill. */
@Composable
fun ToneSelector(selected: ReplyTone, enabled: Boolean, onSelect: (ReplyTone) -> Unit, modifier: Modifier = Modifier) {
    val colors = AkmaTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(AkmaRadius.md))
            .background(colors.bgSubtle)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReplyTone.entries.forEach { tone ->
            val isSelected = tone == selected
            val shape = RoundedCornerShape(AkmaRadius.sm)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(if (isSelected) Modifier.shadow(2.dp, shape, ambientColor = colors.textPrimary, spotColor = colors.textPrimary) else Modifier)
                    .clip(shape)
                    .background(if (isSelected) colors.bgSurface else colors.bgSubtle)
                    .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(tone) })
                    .padding(horizontal = AkmaSpacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(CategoryPresentation.toneLabel(tone)),
                    style = if (isSelected) AkmaTheme.type.labelMStrong else AkmaTheme.type.labelM,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Explicit second confirmation (CONTRACT.md). Not in the Figma; styled with its tokens.
 * Shows the exact staged action, tone and message. [confirmButton] is supplied by the shell so it
 * can use the obscured-touch-safe control and confirm only the displayed confirmation ID.
 */
@Composable
fun ConfirmationCard(
    actionLabel: String,
    tone: ReplyTone,
    message: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AkmaRadius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AkmaTheme.colors.bgBrandSubtle)
            .border(1.5.dp, AkmaTheme.colors.bgBrand, shape)
            .padding(AkmaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AkmaSpacing.sm),
    ) {
        Text(stringResource(R.string.akma_review_title), style = AkmaTheme.type.titleS, color = AkmaTheme.colors.textPrimary)
        Text(
            stringResource(R.string.akma_review_choice, actionLabel, stringResource(CategoryPresentation.toneLabel(tone))),
            style = AkmaTheme.type.labelMStrong,
            color = AkmaTheme.colors.textBrandStrong,
        )
        QuotedMessage(message, maxLines = 3)
        Text(stringResource(R.string.akma_review_body), style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary)
        confirmButton()
        AkmaButton(stringResource(R.string.akma_cancel), onCancel, variant = ButtonVariant.Secondary)
    }
}

/** Default confirm control for previews and Compose-only hosts. */
@Composable
fun WriteReplyButton(onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    AkmaButton(stringResource(R.string.akma_write_reply), onConfirm, modifier)
}

/** Divider used between setup steps. */
@Composable
fun AkmaDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AkmaTheme.colors.bgMuted),
    )
}
