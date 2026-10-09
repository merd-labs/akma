package ph.merd.akma.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.domain.ReplyState
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.ui.JourneyCallbacks
import ph.merd.akma.ui.JourneyPanel
import ph.merd.akma.ui.ReplyUi
import ph.merd.akma.ui.components.AkmaButton
import ph.merd.akma.ui.components.AkmaDivider
import ph.merd.akma.ui.components.AkmaTag
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.components.EmptyStateCard
import ph.merd.akma.ui.components.LogoLockup
import ph.merd.akma.ui.components.NoteRow
import ph.merd.akma.ui.components.PreviewBadge
import ph.merd.akma.ui.components.RefineButton
import ph.merd.akma.ui.components.ReplyCard
import ph.merd.akma.ui.components.SetupCard
import ph.merd.akma.ui.components.SetupStepCard
import ph.merd.akma.ui.components.TagVariant
import ph.merd.akma.ui.components.WriteReplyButton
import ph.merd.akma.ui.theme.AkmaSpacing
import ph.merd.akma.ui.theme.AkmaTheme
import ph.merd.akma.ui.toPanelUi
import ph.merd.akma.ui.onboarding.HomeScreen
import ph.merd.akma.ui.onboarding.LandingScreen
import ph.merd.akma.ui.onboarding.SetupScreen

// Previews show sample text only, always under PreviewBadge. Sizes: Figma 360x800, tall 20:9, large font.

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    AkmaTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(AkmaTheme.colors.bgScrim),
            contentAlignment = Alignment.BottomCenter,
        ) {
            content()
            PreviewBadge(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        }
    }
}

@Composable
private fun JourneyPreview(state: ReplyState, tone: ReplyTone = ReplyTone.PROFESSIONAL, selected: String? = null) {
    PreviewFrame {
        JourneyPanel(state.toPanelUi(tone, selected), JourneyCallbacks()) { WriteReplyButton(onConfirm = {}) }
    }
}

@Preview(name = "Paste", widthDp = 360, heightDp = 800)
@Composable
private fun PastePreview() = JourneyPreview(PreviewFixtures.readyWithMessage)

@Preview(name = "Paste empty", widthDp = 360, heightDp = 800)
@Composable
private fun PasteEmptyPreview() = JourneyPreview(PreviewFixtures.ready)

@Preview(name = "Reading (23:532)", widthDp = 360, heightDp = 800)
@Composable
private fun ReadingPreview() = JourneyPreview(PreviewFixtures.analyzing)

@Preview(name = "Choose", widthDp = 360, heightDp = 800)
@Composable
private fun ChoosePreview() = JourneyPreview(PreviewFixtures.choosing)

@Preview(name = "Confirm", widthDp = 360, heightDp = 800)
@Composable
private fun ConfirmPreview() = JourneyPreview(PreviewFixtures.confirming)

@Preview(name = "Writing (25:1293)", widthDp = 360, heightDp = 800)
@Composable
private fun WritingPreview() = JourneyPreview(PreviewFixtures.drafting, selected = "accept")

@Preview(name = "Edit (24:562)", widthDp = 360, heightDp = 800)
@Composable
private fun EditPreview() = JourneyPreview(PreviewFixtures.editing, selected = "accept")

@Preview(name = "Copied (24:762)", widthDp = 360, heightDp = 800)
@Composable
private fun CopiedPreview() = JourneyPreview(PreviewFixtures.copied, selected = "accept")

@Preview(name = "Fallback Other (25:1491)", widthDp = 360, heightDp = 800)
@Composable
private fun FallbackPreview() = JourneyPreview(PreviewFixtures.fallback, ReplyTone.CONCISE)

@Preview(name = "No model", widthDp = 360, heightDp = 800)
@Composable
private fun NoModelPreview() = JourneyPreview(PreviewFixtures.modelUnavailable)

@Preview(name = "Loading model", widthDp = 360, heightDp = 800)
@Composable
private fun LoadingModelPreview() = JourneyPreview(PreviewFixtures.modelLoading)

@Preview(name = "Error", widthDp = 360, heightDp = 800)
@Composable
private fun ErrorPreview() = JourneyPreview(PreviewFixtures.error)

@Preview(name = "Edit tall 20:9", widthDp = 393, heightDp = 873)
@Composable
private fun EditTallPreview() = JourneyPreview(PreviewFixtures.editing, selected = "accept")

@Preview(name = "Edit font 1.3", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun EditLargeFontPreview() = JourneyPreview(PreviewFixtures.editing, selected = "accept")

@Preview(name = "Nothing copied (25:1688)", widthDp = 360, heightDp = 800)
@Composable
private fun NothingCopiedPreview() {
    PreviewFrame {
        ph.merd.akma.ui.components.AkmaPanel(onClose = {}) {
            EmptyStateCard(R.drawable.ic_akma_clipboard, stringResource(R.string.akma_empty_copy_title), stringResource(R.string.akma_empty_copy_body))
            AkmaButton(stringResource(R.string.akma_got_it), {})
        }
    }
}

@Preview(name = "Setup (22:275)", widthDp = 360, heightDp = 800)
@Composable
private fun SetupPreview() {
    AkmaTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(AkmaTheme.colors.bgApp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PreviewBadge()
            LogoLockup()
            Column(verticalArrangement = Arrangement.spacedBy(AkmaSpacing.sm)) {
                Text("Two quick steps", style = AkmaTheme.type.display, color = AkmaTheme.colors.textPrimary)
                Text("Akma needs to sit on top of your chat apps. You only do this once.", style = AkmaTheme.type.bodyL, color = AkmaTheme.colors.textSecondary)
            }
            SetupCard {
                SetupStepCard(1, "Allow Akma over other apps", "So the bubble can sit on top of Viber, Messenger and other chat apps.", active = true) {
                    AkmaButton("Allow", {})
                }
                AkmaDivider()
                SetupStepCard(2, "Switch Akma on", "Available after step 1.", active = false)
            }
            NoteRow(R.drawable.ic_akma_lock, stringResource(R.string.akma_privacy_note))
        }
    }
}

@Preview(name = "Parts", widthDp = 360)
@Composable
private fun PartsPreview() {
    AkmaTheme {
        Column(
            Modifier
                .background(AkmaTheme.colors.bgSurface)
                .padding(AkmaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AkmaSpacing.md),
        ) {
            PreviewBadge()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
                AkmaTag(stringResource(R.string.akma_on_device), TagVariant.Brand, icon = R.drawable.ic_akma_lock)
                AkmaTag("English", TagVariant.Neutral, icon = R.drawable.ic_akma_message)
                AkmaTag(stringResource(R.string.akma_copied), TagVariant.Success)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AkmaSpacing.xs)) {
                RefineButton("Regenerate", {})
                RefineButton("Shorter", {}, selected = true)
                RefineButton("More formal", {}, enabled = false)
            }
            ReplyCard(ReplyUi.Draft(PreviewFixtures.SAMPLE_DRAFT), {})
            AkmaButton(stringResource(R.string.akma_cancel), {}, variant = ButtonVariant.Secondary)
            AkmaButton(stringResource(R.string.akma_copy_reply), {}, enabled = false, icon = R.drawable.ic_akma_copy)
        }
    }
}

// Figma first launch and setup (22:253). Landing's hero is the fixed product illustration.

@Preview(name = "Landing (66:2472)", widthDp = 360, heightDp = 800)
@Composable
private fun LandingPreview() = AkmaTheme { LandingScreen(onGetStarted = {}) }

@Preview(name = "Setup, not allowed (22:275)", widthDp = 360, heightDp = 800)
@Composable
private fun SetupNotAllowedPreview() = AkmaTheme { SetupScreen(overlayGranted = false, bubbleOn = false, onAllow = {}, onBubbleChange = {}, onReplyHere = {}) }

@Preview(name = "Setup, allowed", widthDp = 360, heightDp = 800)
@Composable
private fun SetupAllowedPreview() = AkmaTheme { SetupScreen(overlayGranted = true, bubbleOn = false, onAllow = {}, onBubbleChange = {}, onReplyHere = {}) }

@Preview(name = "Akma is on (22:332)", widthDp = 360, heightDp = 800)
@Composable
private fun HomeOnPreview() = AkmaTheme { HomeScreen(bubbleOn = true, onBubbleChange = {}, onReplyHere = {}) }

@Preview(name = "Akma is off", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun HomeOffPreview() = AkmaTheme { HomeScreen(bubbleOn = false, onBubbleChange = {}, onReplyHere = {}) }
