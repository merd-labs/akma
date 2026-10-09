package ph.merd.akma

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.domain.ActionCatalog
import ph.merd.akma.overlay.OverlayService
import ph.merd.akma.ui.copyDraft
import ph.merd.akma.ui.statusText
import ph.merd.akma.ui.canStartProcessing
import ph.merd.akma.ui.canChooseDraft
import ph.merd.akma.ui.displayedConfirmation
import ph.merd.akma.ui.selectDraft
import ph.merd.akma.ui.confirmDisplayedDraft
import ph.merd.akma.ui.cancelDisplayedDraft
import ph.merd.akma.ui.confirmationButton
import ph.merd.akma.ui.copyButton

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
        enableEdgeToEdge()
        refreshPermissions()
        setContent {
            val state by session.replies.state.collectAsStateWithLifecycle()
            val overlay by session.overlayStatus.collectAsStateWithLifecycle()
            var tone by remember { mutableStateOf(ReplyTone.PROFESSIONAL) }
            var copyError by remember { mutableStateOf(false) }
            MaterialTheme {
                Scaffold { contentPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(contentPadding)
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.tagline))
                        Text(stringResource(R.string.workflow_intro))
                        Text(if (overlayGranted) "Overlay permission granted" else "Permission required for overlay. Activity works without it.")
                        if (!overlayGranted) {
                            Button(onClick = ::requestOverlayPermission) { Text("Grant overlay permission") }
                        }
                        if (!notificationGranted) Text("Notifications disabled. Use Close in the overlay or Activity to end the session.")
                        Button(onClick = ::requestOverlayStart, enabled = overlayGranted && !overlay.open) { Text("Open overlay") }
                        Button(onClick = {
                            stopService(Intent(this@MainActivity, OverlayService::class.java))
                        }, enabled = overlay.open) { Text("Close overlay") }
                        overlay.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Text(state.statusText(), style = MaterialTheme.typography.titleMedium)
                        state.notice?.let { Text(it) }
                        if (state.busy) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Button(onClick = { if (session.replies.state.value.busy) session.replies.cancel() }) { Text("Cancel") }
                        }
                        if (state.phase in setOf(ReplyPhase.ModelUnavailable, ReplyPhase.Error)) {
                            Button(onClick = {
                                if (session.replies.state.value.canStartProcessing) session.replies.initialize()
                            }, enabled = state.canStartProcessing) { Text("Retry local model") }
                        }
                        if (state.phase == ReplyPhase.Error) {
                            Button(onClick = session.replies::recover) { Text("Dismiss error and retry") }
                        }
                        OutlinedTextField(
                            value = state.message,
                            onValueChange = session.replies::setMessage,
                            label = { Text("Message — use keyboard Paste") },
                            supportingText = { Text("${state.message.length}/1,500 characters") },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                        Button(
                            onClick = { if (session.replies.state.value.canStartProcessing) session.replies.analyze() },
                            enabled = state.canStartProcessing && state.phase != ReplyPhase.ModelUnavailable,
                        ) { Text("Analyze locally") }
                        state.analysis?.let { analysis ->
                            Text(analysis.summary)
                            val displayedTone = state.pendingConfirmation?.request?.tone ?: tone
                            ReplyTone.entries.forEach { choice ->
                                Button(onClick = {
                                    if (session.replies.state.value.canChooseDraft) tone = choice
                                }, enabled = state.canChooseDraft) {
                                    Text(if (displayedTone == choice) "Selected: ${choice.name}" else choice.name)
                                }
                            }
                            analysis.actions.forEach { action ->
                                val canonical = ActionCatalog.action(action.id)
                                Button(onClick = { session.replies.selectDraft(action.id, tone) },
                                    enabled = state.canChooseDraft && canonical == action) {
                                    Text(canonical?.label ?: "Unavailable action")
                                }
                            }
                        }
                        state.pendingConfirmation?.let { displayed ->
                            // Replacing a token replaces its controls, cancelling any in-flight tap.
                            key(displayed.id) {
                                val confirmation = state.displayedConfirmation()
                                if (confirmation != null) {
                                    Text("Review before generating", style = MaterialTheme.typography.titleMedium)
                                    Text("Action: ${confirmation.action.label}")
                                    Text("Tone: ${confirmation.request.tone.name}")
                                    Text("Message context (untrusted copied text):")
                                    Text(confirmation.request.original.message)
                                    if (confirmation.request.original.history.isNotBlank()) {
                                        Text("History (untrusted text):")
                                        Text(confirmation.request.original.history)
                                    }
                                    confirmation.request.original.relationship?.takeIf { it.isNotBlank() }?.let {
                                        Text("Relationship (untrusted text):")
                                        Text(it)
                                    }
                                    if (confirmation.request.userInstruction.isNotBlank()) {
                                        Text("Instruction (untrusted text):")
                                        Text(confirmation.request.userInstruction)
                                    }
                                } else Text("Selection no longer valid. Cancel and choose again.")
                                Text("Generating a draft does not send or accept anything. Cancel to change action or tone.")
                                AndroidView(
                                    factory = { context -> confirmationButton(context) },
                                    update = { button ->
                                        button.isEnabled = confirmation != null
                                        button.setOnClickListener { session.replies.confirmDisplayedDraft(displayed.id) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(onClick = { session.replies.cancelDisplayedDraft(displayed.id) }) { Text("Cancel selection") }
                            }
                        }
                        if (state.phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied)) {
                            Text("Review before copying. Paste and send manually.")
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = { copyError = false; session.replies.editDraft(it) },
                                label = { Text("Editable draft") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                            )
                            AndroidView(
                                factory = { context -> copyButton(context) },
                                update = { button ->
                                    button.isEnabled = state.canCopy
                                    button.setOnClickListener {
                                        copyError = !copyDraft(this@MainActivity, session.replies.state.value)
                                        if (!copyError) session.replies.copied()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (copyError) Text("Copy failed. Select the draft and copy manually.")
                        }
                    }
                }
            }
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
