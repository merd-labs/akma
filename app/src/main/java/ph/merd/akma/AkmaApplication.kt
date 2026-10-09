package ph.merd.akma

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.LiteRtReplyEngine

data class OverlayStatus(val open: Boolean = false, val error: String? = null)

class AkmaApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val replies by lazy { ReplyCoordinator(
        LiteRtReplyEngine(this), scope,
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
