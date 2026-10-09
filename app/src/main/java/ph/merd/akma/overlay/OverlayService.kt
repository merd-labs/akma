package ph.merd.akma.overlay

import android.app.AppOpsManager
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycle = OverlaySession(::attachPanel, ::releasePanel)
    private var watchingPermission = false
    private val permissionListener = AppOpsManager.OnOpChangedListener { operation, changedPackage ->
        if (operation == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && changedPackage == packageName) {
            mainHandler.post {
                if (!lifecycle.closed && !Settings.canDrawOverlays(this)) {
                    fail("Overlay permission revoked. Continue in the Activity.")
                }
            }
        }
    }
    private val attachmentListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) {
            if (!lifecycle.closed) fail("Overlay closed by Android. Open it again from the Activity.")
        }
    }

    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (lifecycle.closed || !isOverlayShowRequest(intent != null, intent?.action)) {
            closeOverlay()
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
            if (!lifecycle.show(Settings.canDrawOverlays(this))) {
                fail("Overlay permission required. Continue in the Activity.")
            }
        } catch (_: RuntimeException) {
            fail("Overlay unavailable. Continue in the Activity and retry.")
        }
        return START_NOT_STICKY
    }

    private fun attachPanel() {
        val view = OverlayPanel(this, session.replies, ::closeOverlay)
        // Record ownership before addView so partial attachment failures also get cleaned up.
        panel = view
        view.addOnAttachStateChangeListener(attachmentListener)
        val density = resources.displayMetrics.density
        val width = minOf((340 * density).toInt(), resources.displayMetrics.widthPixels - (32 * density).toInt())
        val params = WindowManager.LayoutParams(
            width.coerceAtLeast(1),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (48 * density).toInt()
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        windows.addView(view, params)
        getSystemService(AppOpsManager::class.java).startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, permissionListener,
        )
        watchingPermission = true
        // Cover a revocation racing with attachment/watcher registration.
        check(Settings.canDrawOverlays(this))
        session.overlayStatus.value = OverlayStatus(open = true)
        scope.launch { session.replies.state.collect { view.render(it) } }
    }

    private fun fail(message: String) {
        session.overlayStatus.value = OverlayStatus(error = message)
        closeOverlay()
    }

    private fun closeOverlay() {
        lifecycle.close()
        stopSelf()
    }

    private fun releasePanel() {
        mainHandler.removeCallbacksAndMessages(null)
        if (watchingPermission) {
            watchingPermission = false
            try { getSystemService(AppOpsManager::class.java).stopWatchingMode(permissionListener) }
            catch (_: RuntimeException) { /* Android may already have removed the watcher. */ }
        }
        scope.cancel()
        val view = panel
        panel = null
        view?.let {
            it.removeOnAttachStateChangeListener(attachmentListener)
            try {
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0)
                it.clearFocus()
            } catch (_: RuntimeException) { /* Input window may already be gone. */ }
            try { windows.removeViewImmediate(it) }
            catch (_: RuntimeException) { /* Already detached, or addView failed. */ }
            session.replies.cancel()
        }
        session.overlayStatus.value = session.overlayStatus.value.copy(open = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        lifecycle.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "akma_overlay"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CLOSE = "ph.merd.akma.CLOSE_OVERLAY"
    }
}

/** A non-null, actionless Intent is the existing Activity's explicit Open request. */
internal fun isOverlayShowRequest(hasIntent: Boolean, action: String?): Boolean = hasIntent && action == null

/** Main-thread ownership gate: a stopped service instance can never reopen its window. */
internal class OverlaySession(private val attach: () -> Unit, private val release: () -> Unit) {
    var closed = false
        private set
    private var attached = false

    fun show(permissionGranted: Boolean): Boolean {
        if (closed) return false
        if (!permissionGranted) {
            close()
            return false
        }
        if (!attached) {
            try {
                attach()
                attached = true
            } catch (exception: RuntimeException) {
                close()
                throw exception
            }
        }
        return true
    }

    fun close() {
        if (closed) return
        closed = true
        release()
    }
}
