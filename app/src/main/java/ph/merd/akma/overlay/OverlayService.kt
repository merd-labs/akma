package ph.merd.akma.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ph.merd.akma.AkmaApplication
import ph.merd.akma.MainActivity
import ph.merd.akma.OverlayStatus
import ph.merd.akma.R

/** User-started only. No clipboard listener, background restart, or automatic message capture. */
class OverlayService : Service() {
    private val session get() = application as AkmaApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windows: WindowManager
    private var panel: View? = null

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Akma overlay", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val close = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_CLOSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Akma overlay is open")
                .setContentText("User-controlled session. Tap Close to stop.")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Open Akma", open).build())
                .addAction(Notification.Action.Builder(null, "Close", close).build())
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, notification)
            if (!Settings.canDrawOverlays(this)) {
                fail("Overlay permission required. Continue in the Activity.")
            } else if (panel == null) {
                val view = OverlayPanel(this, session.replies, ::stopSelf)
                val width = minOf((340 * resources.displayMetrics.density).toInt(), resources.displayMetrics.widthPixels - 32)
                val params = WindowManager.LayoutParams(
                    width,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (48 * resources.displayMetrics.density).toInt()
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }
                windows.addView(view, params)
                panel = view
                session.overlayStatus.value = OverlayStatus(open = true)
                scope.launch { session.replies.state.collect { view.render(it) } }
            }
        } catch (_: RuntimeException) {
            fail("Overlay unavailable. Continue in the Activity and retry.")
        }
        return START_NOT_STICKY
    }

    private fun fail(message: String) {
        session.overlayStatus.value = OverlayStatus(error = message)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        panel?.let { view ->
            try { windows.removeView(view) } catch (_: RuntimeException) { /* Already detached by Android. */ }
        }
        panel = null
        session.replies.cancel()
        session.overlayStatus.value = session.overlayStatus.value.copy(open = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "akma_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CLOSE = "ph.merd.akma.CLOSE_OVERLAY"
    }
}
