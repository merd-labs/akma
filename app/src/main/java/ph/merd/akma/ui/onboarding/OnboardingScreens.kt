package ph.merd.akma.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ph.merd.akma.R
import ph.merd.akma.ui.components.AkmaButton
import ph.merd.akma.ui.components.AkmaDivider
import ph.merd.akma.ui.components.AkmaIcon
import ph.merd.akma.ui.components.AkmaTag
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.components.LogoLockup
import ph.merd.akma.ui.components.LogoTile
import ph.merd.akma.ui.components.SetupStepCard
import ph.merd.akma.ui.components.TagVariant
import ph.merd.akma.ui.theme.AkmaRadius
import ph.merd.akma.ui.theme.AkmaTheme

/**
 * Figma B Landing (66:2472). The hero is a fixed, hand-written illustration of the idea
 * (message, choice, reply, copied). It is never model output and never changes at runtime.
 */
@Composable
fun LandingScreen(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AkmaTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bgApp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        LandingHero()
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.akma_landing_title), style = AkmaTheme.type.display, color = colors.textPrimary)
            Text(stringResource(R.string.akma_landing_body), style = AkmaTheme.type.bodyL, color = colors.textSecondary)
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AkmaTag(stringResource(R.string.akma_on_device), TagVariant.Brand, icon = R.drawable.ic_akma_lock)
                AkmaTag(stringResource(R.string.akma_offline), TagVariant.Neutral, icon = R.drawable.ic_akma_wifi_off)
                AkmaTag(stringResource(R.string.akma_taglish), TagVariant.Neutral, icon = R.drawable.ic_akma_message)
            }
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AkmaButton(stringResource(R.string.akma_get_started), onGetStarted)
                Text(
                    stringResource(R.string.akma_get_started_caption),
                    style = AkmaTheme.type.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LandingHero() {
    val colors = AkmaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = AkmaRadius.xl, bottomEnd = AkmaRadius.xl))
            .background(colors.bgBrand)
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 30.dp),
    ) {
        Row(
            Modifier.padding(start = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.akma_logo_mark), null, Modifier.size(28.dp, 25.9.dp), tint = Color.Unspecified)
            Text(stringResource(R.string.app_name), style = AkmaTheme.type.wordmarkL, color = colors.textOnBrand)
        }
        Box(
            Modifier
                .padding(start = 24.dp, end = 88.dp, top = 24.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .background(colors.bgSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.akma_landing_example_message), style = AkmaTheme.type.bodyM, color = colors.textPrimary)
        }
        Row(
            Modifier
                .padding(start = 44.dp, top = 14.dp)
                .shadow(3.dp, AkmaRadius.full, ambientColor = colors.textPrimary, spotColor = colors.textPrimary)
                .clip(AkmaRadius.full)
                .background(colors.bgSurface)
                .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoTile(32.dp)
            Text(stringResource(R.string.akma_landing_example_action), style = AkmaTheme.type.labelM, color = colors.textPrimary, maxLines = 1)
            Box(Modifier.width(1.dp).height(16.dp).background(colors.bgMuted))
            Text(stringResource(R.string.akma_landing_example_tone), style = AkmaTheme.type.labelM, color = colors.textSecondary, maxLines = 1)
        }
        Box(
            Modifier
                .padding(start = 72.dp, end = 24.dp, top = 22.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(colors.bgBrandSubtle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.akma_landing_example_reply), style = AkmaTheme.type.bodyM, color = colors.textPrimary)
        }
        Box(Modifier.fillMaxWidth().padding(end = 24.dp, top = 10.dp), contentAlignment = Alignment.CenterEnd) {
            AkmaTag(stringResource(R.string.akma_copied), TagVariant.Success, icon = R.drawable.ic_akma_check)
        }
    }
}

/**
 * Figma C Set up Akma (22:275). Step 1 opens the Android permission screen; step 2's switch
 * unlocks after the permission is granted and starts the bubble.
 */
@Composable
fun SetupScreen(
    overlayGranted: Boolean,
    bubbleOn: Boolean,
    onAllow: () -> Unit,
    onBubbleChange: (Boolean) -> Unit,
    onReplyHere: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val colors = AkmaTheme.colors
    OnboardingColumn(modifier) {
        LogoLockup()
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.akma_setup_title), style = AkmaTheme.type.display, color = colors.textPrimary)
            Text(stringResource(R.string.akma_setup_body), style = AkmaTheme.type.bodyL, color = colors.textSecondary)
        }
        OnboardingCard(Modifier.padding(top = 24.dp), spacing = 20.dp) {
            SetupStepCard(
                number = 1,
                title = stringResource(R.string.akma_setup_allow_title),
                body = stringResource(if (overlayGranted) R.string.akma_setup_allowed else R.string.akma_setup_allow_body),
                active = !overlayGranted,
                trailing = if (overlayGranted) {
                    { AkmaIcon(R.drawable.ic_akma_check, 24.dp, colors.textSuccess) }
                } else null,
                action = if (overlayGranted) null else {
                    { AkmaButton(stringResource(R.string.akma_setup_allow), onAllow) }
                },
            )
            AkmaDivider()
            SetupStepCard(
                number = 2,
                title = stringResource(R.string.akma_setup_switch_title),
                body = stringResource(if (overlayGranted) R.string.akma_setup_switch_ready else R.string.akma_setup_switch_locked),
                active = overlayGranted,
                trailing = {
                    AkmaSwitch(bubbleOn, onBubbleChange, stringResource(R.string.akma_switch_label), enabled = overlayGranted)
                },
            )
        }
        notice?.let { Text(it, style = AkmaTheme.type.bodyM, color = colors.textSecondary, modifier = Modifier.padding(top = 16.dp)) }
        PrivacyNote(Modifier.padding(top = 24.dp))
        ReplyHereLink(onReplyHere, Modifier.padding(top = 24.dp))
    }
}

/** Figma D Akma is on (22:332), also used while the switch is off. */
@Composable
fun HomeScreen(
    bubbleOn: Boolean,
    onBubbleChange: (Boolean) -> Unit,
    onReplyHere: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val colors = AkmaTheme.colors
    OnboardingColumn(modifier) {
        LogoLockup()
        OnboardingCard(Modifier.padding(top = 40.dp), spacing = 6.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (bubbleOn) R.string.akma_home_on else R.string.akma_home_off),
                    style = AkmaTheme.type.titleL,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                AkmaSwitch(bubbleOn, onBubbleChange, stringResource(R.string.akma_switch_label))
            }
            Text(
                stringResource(if (bubbleOn) R.string.akma_home_on_body else R.string.akma_home_off_body),
                style = AkmaTheme.type.bodyM,
                color = colors.textSecondary,
            )
            notice?.let { Text(it, style = AkmaTheme.type.bodyM, color = colors.textPrimary) }
        }
        OnboardingCard(Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.akma_home_how_title), style = AkmaTheme.type.titleS, color = colors.textPrimary)
            HowToStep(1, stringResource(R.string.akma_home_how_1))
            HowToStep(2, stringResource(R.string.akma_home_how_2))
            HowToStep(3, stringResource(R.string.akma_home_how_3))
            HowToStep(4, stringResource(R.string.akma_home_how_4))
        }
        OnboardingCard(Modifier.padding(top = 16.dp), background = colors.bgBrandSubtle, elevated = false) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgBrandMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    AkmaIcon(R.drawable.ic_akma_shield, 22.dp, colors.textBrandStrong)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.akma_home_privacy_title), style = AkmaTheme.type.bodyLStrong, color = colors.textPrimary)
                    Text(stringResource(R.string.akma_home_privacy_body), style = AkmaTheme.type.bodyM, color = colors.textSecondary)
                }
            }
        }
        ReplyHereLink(onReplyHere, Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun OnboardingColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(AkmaTheme.colors.bgApp)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
    ) { content() }
}

@Composable
private fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        AkmaIcon(R.drawable.ic_akma_lock, 20.dp, AkmaTheme.colors.textBrandStrong)
        Text(stringResource(R.string.akma_privacy_note), style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary, modifier = Modifier.weight(1f))
    }
}

/** Not in Figma: the Activity fallback (ADR) for phones where the bubble is off or unavailable. */
@Composable
private fun ReplyHereLink(onReplyHere: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AkmaButton(stringResource(R.string.akma_reply_here), onReplyHere, variant = ButtonVariant.Secondary)
        Text(
            stringResource(R.string.akma_reply_here_hint),
            style = AkmaTheme.type.caption,
            color = AkmaTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
