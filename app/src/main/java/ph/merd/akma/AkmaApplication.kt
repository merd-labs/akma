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
        // Measured on Tecno Pova 2 LE7 / API 30 (CPU, offline): analysis 47.8-62.1 s, draft ~47 s, first cold model load
        // ~91 s native init plus copy and SHA-256 of 1.6 GB. The default 60 s limit turned successful runs into errors.
        timeoutMillis = 240_000, initTimeoutMillis = 900_000,
    ) }
    val overlayStatus = MutableStateFlow(OverlayStatus())
    override fun onCreate() {
        super.onCreate()
        replies.initialize()
    }
}
