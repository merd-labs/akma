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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.merd.akma.domain.ReplyPhase
import ph.merd.akma.domain.ReplyTone
import ph.merd.akma.overlay.OverlayService
import ph.merd.akma.ui.copyDraft
import ph.merd.akma.ui.statusText

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
                            Button(onClick = session.replies::cancel) { Text("Cancel") }
                        }
                        Button(onClick = session.replies::initialize, enabled = !state.busy) { Text("Check local model") }
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
                            onClick = session.replies::analyze,
                            enabled = !state.busy && state.phase != ReplyPhase.ModelUnavailable,
                        ) { Text("Analyze locally") }
                        state.analysis?.let { analysis ->
                            Text(analysis.summary)
                            ReplyTone.entries.forEach { choice ->
                                Button(onClick = { tone = choice }, enabled = !state.busy) {
                                    Text(if (tone == choice) "Selected: ${choice.name}" else choice.name)
                                }
                            }
                            analysis.actions.forEach { action ->
                                Button(onClick = { session.replies.draft(action.id, tone) }, enabled = !state.busy) { Text(action.label) }
                            }
                        }
                        if (state.phase in setOf(ReplyPhase.Editing, ReplyPhase.Copied)) {
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = { copyError = false; session.replies.editDraft(it) },
                                label = { Text("Editable draft") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                            )
                            Button(onClick = {
                                copyError = !copyDraft(this@MainActivity, state)
                                if (!copyError) session.replies.copied()
                            }, enabled = state.canCopy) { Text("Copy draft") }
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
