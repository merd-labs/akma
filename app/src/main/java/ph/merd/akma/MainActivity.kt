package ph.merd.akma

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ReplyValidation
import ph.merd.akma.overlay.OverlayService
import ph.merd.akma.ui.CopyUi
import ph.merd.akma.ui.JourneyCallbacks
import ph.merd.akma.ui.JourneyPanel
import ph.merd.akma.ui.ProtectedAkmaButton
import ph.merd.akma.ui.canChooseDraft
import ph.merd.akma.ui.canStartProcessing
import ph.merd.akma.ui.cancelDisplayedDraft
import ph.merd.akma.ui.cancelDisplayedProcessing
import ph.merd.akma.ui.components.AkmaButton
import ph.merd.akma.ui.components.ButtonVariant
import ph.merd.akma.ui.components.SetupCard
import ph.merd.akma.ui.components.SetupStepCard
import ph.merd.akma.ui.confirmDisplayedDraft
import ph.merd.akma.ui.confirmationButton
import ph.merd.akma.ui.copyButton
import ph.merd.akma.ui.copyDraft
import ph.merd.akma.ui.retryLocalModel
import ph.merd.akma.ui.selectDraft
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
        super.onCreate(savedInstanceState)
        // Light Akma surfaces need dark system-bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AkmaTokens.BG_SURFACE.toInt(), AkmaTokens.TEXT_PRIMARY.toInt()),
            navigationBarStyle = SystemBarStyle.light(AkmaTokens.BG_SURFACE.toInt(), AkmaTokens.TEXT_PRIMARY.toInt()),
        )
        refreshPermissions()
        setContent {
            val state by session.replies.state.collectAsStateWithLifecycle()
            val overlay by session.overlayStatus.collectAsStateWithLifecycle()
            var tone by remember { mutableStateOf(ReplyTone.PROFESSIONAL) }
            var lastActionId by remember { mutableStateOf<String?>(null) }
            var localNotice by remember { mutableStateOf<String?>(null) }
            var copyError by remember { mutableStateOf(false) }
            // Guards compare against the state this composition displayed, not a later one.
            val displayed = state
            val ui = displayed.toPanelUi(tone, lastActionId).let { it.copy(notice = localNotice ?: it.notice) }
            val pasteEmpty = stringResource(R.string.akma_paste_empty)
            val pasteTooLong = stringResource(R.string.akma_paste_too_long)
            AkmaTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AkmaTheme.colors.bgSurface)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    JourneyPanel(
                        ui = ui,
                        callbacks = JourneyCallbacks(
                            onMessageChange = { localNotice = null; session.replies.setMessage(it) },
                            onPaste = { localNotice = pasteFromClipboard(pasteEmpty, pasteTooLong) },
                            onAnalyze = { if (session.replies.state.value.canStartProcessing) session.replies.analyze() },
                            onSelectAction = { id ->
                                if (session.replies.state.value.canChooseDraft) {
                                    lastActionId = id
                                    session.replies.selectDraft(id, tone)
                                }
                            },
                            onSelectTone = { if (session.replies.state.value.canChooseDraft) tone = it },
                            onCancelConfirmation = {
                                displayed.pendingConfirmation?.let { session.replies.cancelDisplayedDraft(it.id) }
                                lastActionId = null
                            },
                            onCancelProcessing = { session.replies.cancelDisplayedProcessing(displayed) },
                            onStartOver = {
                                localNotice = null
                                lastActionId = null
                                session.replies.setMessage("")
                            },
                            onDraftChange = { copyError = false; session.replies.editDraft(it) },
                            onRetry = { session.replies.retryLocalModel() },
                            onDismissError = session.replies::recover,
                        ),
                        copyButton = { copy ->
                            val copied = copy == CopyUi.Copied
                            ProtectedAkmaButton(
                                factory = { context -> copyButton(context) },
                                text = stringResource(if (copied) R.string.akma_copied else R.string.akma_copy_reply),
                                enabled = copy == CopyUi.Ready || copied,
                                onClick = {
                                    copyError = !copyDraft(this@MainActivity, session.replies.state.value)
                                    if (!copyError) session.replies.copied()
                                },
                                variant = if (copied) ButtonVariant.Success else ButtonVariant.Primary,
                                icon = if (copied) R.drawable.ic_akma_check else R.drawable.ic_akma_copy,
                            )
                            if (copyError) SecondaryText(stringResource(R.string.akma_copy_failed))
                            else if (copied) SecondaryText(stringResource(R.string.akma_copied_hint))
                        },
                        footer = { OverlaySetup(overlay) },
                    ) { confirmation ->
                        // Replacing a token replaces its control, cancelling any in-flight tap.
                        key(confirmation.confirmationId) {
                            ProtectedAkmaButton(
                                factory = { context -> confirmationButton(context) },
                                text = stringResource(R.string.akma_write_reply),
                                enabled = confirmation.valid,
                                onClick = { session.replies.confirmDisplayedDraft(confirmation.confirmationId) },
                            )
                        }
                    }
                }
            }
        }
    }

    /** Reads the clipboard only from an explicit Paste tap while this Activity has focus. */
    private fun pasteFromClipboard(emptyNotice: String, tooLongNotice: String): String? {
        if (!session.replies.state.value.canStartProcessing) return null
        val text = try {
            getSystemService(ClipboardManager::class.java)?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        } catch (_: RuntimeException) {
            ""
        }
        return when {
            text.isBlank() -> emptyNotice
            text.length > ReplyValidation.MAX_TEXT_LENGTH -> tooLongNotice
            else -> { session.replies.setMessage(text); null }
        }
    }

    @Composable
    private fun SecondaryText(text: String) {
        Text(text, style = AkmaTheme.type.bodyM, color = AkmaTheme.colors.textSecondary)
    }

    @Composable
    private fun OverlaySetup(overlay: OverlayStatus) {
        SetupCard {
            SetupStepCard(
                number = 1,
                title = stringResource(R.string.akma_overlay_title),
                body = stringResource(if (overlayGranted) R.string.akma_overlay_ready else R.string.akma_overlay_body),
                active = true,
            )
            if (!overlayGranted) {
                AkmaButton(stringResource(R.string.akma_overlay_grant), ::requestOverlayPermission)
            } else if (overlay.open) {
                AkmaButton(
                    stringResource(R.string.akma_overlay_close),
                    { stopService(Intent(this@MainActivity, OverlayService::class.java)) },
                    variant = ButtonVariant.Secondary,
                )
            } else {
                AkmaButton(stringResource(R.string.akma_overlay_open), ::requestOverlayStart, variant = ButtonVariant.Secondary)
            }
            if (!notificationGranted) SecondaryText(stringResource(R.string.akma_notifications_off))
            overlay.error?.let { SecondaryText(it) }
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
}
