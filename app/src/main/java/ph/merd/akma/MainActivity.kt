package ph.merd.akma

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ReplyValidation
import ph.merd.akma.overlay.OverlayService
import ph.merd.akma.overlay.clearOverlayReplySession
import ph.merd.akma.ui.AkmaScreen
import ph.merd.akma.ui.CopyUi
import ph.merd.akma.ui.JourneyCallbacks
import ph.merd.akma.ui.JourneyPanel
import ph.merd.akma.ui.LiveJourneyPanel
import ph.merd.akma.ui.ProtectedAkmaButton
import ph.merd.akma.ui.back
import ph.merd.akma.ui.canChooseDraft
import ph.merd.akma.ui.canStartProcessing
import ph.merd.akma.ui.cancelDisplayedDraft
import ph.merd.akma.ui.cancelDisplayedProcessing
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.confirmDisplayedDraft
import ph.merd.akma.ui.confirmationButton
import ph.merd.akma.ui.copyButton
import ph.merd.akma.ui.copyDraft
import ph.merd.akma.ui.onboarding.HomeScreen
import ph.merd.akma.ui.onboarding.LandingScreen
import ph.merd.akma.ui.onboarding.SetupScreen
import ph.merd.akma.ui.retryLocalModel
import ph.merd.akma.ui.selectDraft
import ph.merd.akma.ui.startScreen
import ph.merd.akma.ui.theme.AkmaTheme
import ph.merd.akma.ui.theme.AkmaTokens
import ph.merd.akma.ui.toPanelUi

class MainActivity : ComponentActivity() {
    private val session get() = application as AkmaApplication
    private var overlayGranted by mutableStateOf(false)
    private var notificationGranted by mutableStateOf(true)
    private var pendingOverlayStart = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) launchOverlay()
        else pendingOverlayStart = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        refreshPermissions()
        setContent {
            val overlay by session.overlayStatus.collectAsStateWithLifecycle()
            var onboarded by remember { mutableStateOf(readFlag(KEY_ONBOARDED)) }
            var setupDone by remember { mutableStateOf(readFlag(KEY_SETUP_DONE)) }
            var screen by remember { mutableStateOf(startScreen(onboarded, setupDone)) }
            LaunchedEffect(screen) { applySystemBars(screen) }
            screen.back(setupDone)?.let { previous -> BackHandler {
                clearOverlayReplySession(session.replies)
                screen = previous
            } }
            val bubbleOn = overlayGranted && overlay.open
            val notice = overlay.error ?: if (!notificationGranted) stringResource(R.string.akma_notifications_off) else null
            AkmaTheme {
                when (screen) {
                    AkmaScreen.Landing -> LandingScreen(onGetStarted = {
                        onboarded = true
                        writeFlag(KEY_ONBOARDED)
                        screen = startScreen(onboarded, setupDone)
                    })
                    AkmaScreen.Setup -> SetupScreen(
                        overlayGranted = overlayGranted,
                        bubbleOn = bubbleOn,
                        onAllow = ::requestOverlayPermission,
                        onBubbleChange = { on ->
                            setBubble(on)
                            if (on) {
                                setupDone = true
                                writeFlag(KEY_SETUP_DONE)
                                screen = AkmaScreen.Home
                            }
                        },
                        onReplyHere = { screen = AkmaScreen.Reply },
                        notice = notice,
                    )
                    AkmaScreen.Home -> HomeScreen(
                        bubbleOn = bubbleOn,
                        onBubbleChange = ::setBubble,
                        onReplyHere = { screen = AkmaScreen.Reply },
                        notice = notice,
                    )
                    AkmaScreen.Reply -> ReplyScreen(onClose = { screen = AkmaScreen.Reply.back(setupDone) ?: AkmaScreen.Home })
                }
            }
        }
    }

    /** The reply journey inside the Activity, for when the bubble is off or unavailable. */
    @Composable
    private fun ReplyScreen(onClose: () -> Unit) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AkmaTheme.colors.bgSurface)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            LiveJourneyPanel(session.replies, session.demoMode, session.languageLabel, onClose)
        }
    }

    /** Landing has a brand hero under the status bar, so it needs light icons; other screens are light. */
    private fun applySystemBars(screen: AkmaScreen) {
        val light = SystemBarStyle.light(AkmaTokens.BG_SURFACE.toInt(), AkmaTokens.TEXT_PRIMARY.toInt())
        enableEdgeToEdge(
            statusBarStyle = if (screen == AkmaScreen.Landing) SystemBarStyle.dark(Color.TRANSPARENT) else light,
            navigationBarStyle = light,
        )
    }

    /** The switch shows or hides the bubble. Without the permission, it opens the permission screen. */
    private fun setBubble(on: Boolean) {
        when {
            !on -> stopService(Intent(this, OverlayService::class.java))
            !overlayGranted -> requestOverlayPermission()
            else -> requestOverlayStart()
        }
    }

    private fun readFlag(key: String): Boolean = try {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false)
    } catch (_: RuntimeException) {
        false
    }

    private fun writeFlag(key: String) {
        try {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(key, true) }
        } catch (_: RuntimeException) {
            // Losing the flag only shows Landing or Setup again on the next launch.
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        if (pendingOverlayStart) {
            pendingOverlayStart = false
            launchOverlay()
        }
    }

    private fun refreshPermissions() {
        overlayGranted = Settings.canDrawOverlays(this)
        notificationGranted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestOverlayPermission() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: RuntimeException) {
            session.overlayStatus.value = OverlayStatus(error = "Overlay settings unavailable. Continue in the Activity.")
        }
    }

    private fun requestOverlayStart() {
        if (!Settings.canDrawOverlays(this)) { refreshPermissions(); return }
        if (Build.VERSION.SDK_INT >= 33 && !notificationGranted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else launchOverlay()
    }

    private fun launchOverlay() {
        if (!Settings.canDrawOverlays(this)) { refreshPermissions(); return }
        try {
            startForegroundService(Intent(this, OverlayService::class.java))
        } catch (_: RuntimeException) {
            session.overlayStatus.value = OverlayStatus(error = "Overlay could not start. Continue in the Activity and retry.")
        }
    }

    private companion object {
        const val PREFS = "akma_ui"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_SETUP_DONE = "setup_done"
    }
}
