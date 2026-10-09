package ph.merd.akma

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import ph.merd.akma.domain.ReplyCoordinator

data class OverlayStatus(val open: Boolean = false, val error: String? = null)

class AkmaApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Debug builds use the temporary demo engine; release builds use the on-device model (see ReplyEngines.kt).
    private val engineChoice by lazy { replyEngine(this) }
    val demoMode: Boolean get() = engineChoice.demo
    val languageLabel: (String) -> String? get() = engineChoice.languageLabel
    val replies by lazy { ReplyCoordinator(
        engineChoice.engine, scope,
        // Provisioning, full hashing and cold CPU initialization need a separate bounded deadline.
        // These conservative limits are not measured performance or a device-acceptance claim.
        timeoutMillis = 240_000, initTimeoutMillis = 900_000,
    ) }
    val overlayStatus = MutableStateFlow(OverlayStatus())
    override fun onCreate() {
        super.onCreate()
        replies.initialize()
    }
}
