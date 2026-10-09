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
    val replies by lazy { ReplyCoordinator(LiteRtReplyEngine(this), scope) }
    val overlayStatus = MutableStateFlow(OverlayStatus())
    override fun onCreate() {
        super.onCreate()
        replies.initialize()
    }
}
