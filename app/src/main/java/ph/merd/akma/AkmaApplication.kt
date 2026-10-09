package ph.merd.akma

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.domain.UnavailableReplyEngine

data class OverlayStatus(val open: Boolean = false, val error: String? = null)

class AkmaApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Replace only this adapter after real offline inference passes the API 30 phone gate.
    val replies by lazy { ReplyCoordinator(UnavailableReplyEngine(), scope) }
    val overlayStatus = MutableStateFlow(OverlayStatus())
}
