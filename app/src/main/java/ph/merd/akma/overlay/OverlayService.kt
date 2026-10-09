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
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.ImageView
import android.widget.FrameLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ph.merd.akma.AkmaApplication
import ph.merd.akma.MainActivity
import ph.merd.akma.OverlayStatus
import ph.merd.akma.R
import ph.merd.akma.domain.ReplyCoordinator
import ph.merd.akma.ui.theme.AkmaTokens

/** User-started only. No clipboard listener, background restart, or automatic message capture. */
class OverlayService : Service() {
    private val session get() = application as AkmaApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windows: WindowManager
    private var panel: OverlayPanel? = null
    private var host: FrameLayout? = null
    private var panelCollector: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lifecycle = OverlaySession(::attachBubble, ::releaseWindow, ::displayPanel, ::displayBubble, ::clearReplySession)
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
            // The Activity starts this service with startForegroundService(); Android requires startForeground()
            // within seconds even when we are about to stop, otherwise ForegroundServiceDidNotStartInTimeException.
            if (intent != null && intent.action == null) {
                try { startAsForeground() } catch (_: RuntimeException) { /* stopping anyway */ }
            }
            closeOverlay()
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
            if (!lifecycle.show(Settings.canDrawOverlays(this))) {
                fail("Overlay permission required. Continue in the Activity.")
            }
        } catch (_: RuntimeException) {
            fail("Overlay unavailable. Continue in the Activity and retry.")
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Akma overlay", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val close = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_CLOSE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Akma assistant is on")
            .setContentText("Tap the bubble to paste. Close stops the assistant.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Open Akma", open).build())
            .addAction(Notification.Action.Builder(null, "Close", close).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun attachBubble() {
        val root = FrameLayout(this).apply {
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        host = root
        root.addOnAttachStateChangeListener(attachmentListener)
        root.addView(bubble())
        windows.addView(root, bubbleParams())
        getSystemService(AppOpsManager::class.java).startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, permissionListener,
        )
        watchingPermission = true
        check(Settings.canDrawOverlays(this))
        session.overlayStatus.value = OverlayStatus(open = true)
    }

    /** Figma Akma bubble (18:117): 56dp bg/brand circle with the white mark, Elevation/4, in a 72dp window. */
    private fun bubble() = FrameLayout(this).apply {
        val circle = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AkmaTokens.BG_BRAND.toInt())
            }
            foreground = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFFFFFFF.toInt()) })
            elevation = dp(8).toFloat()
            outlineSpotShadowColor = AkmaTokens.BG_BRAND_STRONG.toInt()
            outlineAmbientShadowColor = AkmaTokens.TEXT_PRIMARY.toInt()
            contentDescription = "Open Akma panel"
            isClickable = true
            setOnClickListener {
                try { lifecycle.expand() }
                catch (_: RuntimeException) { fail("Panel unavailable. Continue in the Activity.") }
            }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.akma_logo_mark)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dp(30), (dp(30) * 0.923f).toInt(), Gravity.CENTER))
        }
        addView(circle, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun bubbleParams() = WindowManager.LayoutParams(
        dp(72), dp(72), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Figma: right edge, a little below centre (y 520 of 800).
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        x = dp(4)
        y = dp(156)
    }

    private fun panelParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Figma "Akma panel": a bottom sheet over the chat app.
        gravity = Gravity.BOTTOM
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
    }

    private fun displayPanel() {
        check(Settings.canDrawOverlays(this))
        val root = checkNotNull(host)
        val view = OverlayPanel(this, session.replies) {
            try { lifecycle.collapse() }
            catch (_: RuntimeException) { fail("Panel unavailable. Continue in the Activity.") }
        }
        panel = view
        root.removeAllViews()
        root.addView(view)
        windows.updateViewLayout(root, panelParams())
        panelCollector = scope.launch { session.replies.state.collect { view.render(it) } }
    }

    private fun displayBubble() {
        val root = checkNotNull(host)
        panelCollector?.cancel()
        panelCollector = null
        hideKeyboard(root)
        panel = null
        root.removeAllViews()
        root.addView(bubble())
        windows.updateViewLayout(root, bubbleParams())
    }

    /** Both existing APIs are main-thread operations; cancel invalidates pending results first. */
    private fun clearReplySession() {
        clearOverlayReplySession(session.replies)
    }

    private fun hideKeyboard(view: View) {
        try {
            getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        } catch (_: RuntimeException) { /* Input window may already be gone. */ }
    }

    private fun fail(message: String) {
        session.overlayStatus.value = OverlayStatus(error = message)
        closeOverlay()
    }

    private fun closeOverlay() {
        lifecycle.close()
        stopSelf()
    }

    private fun releaseWindow() {
        mainHandler.removeCallbacksAndMessages(null)
        if (watchingPermission) {
            watchingPermission = false
            try { getSystemService(AppOpsManager::class.java).stopWatchingMode(permissionListener) }
            catch (_: RuntimeException) { /* Android may already have removed the watcher. */ }
        }
        scope.cancel()
        panelCollector = null
        panel = null
        val view = host
        host = null
        view?.let {
            clearReplySession()
            it.removeOnAttachStateChangeListener(attachmentListener)
            hideKeyboard(it)
            try { windows.removeViewImmediate(it) }
            catch (_: RuntimeException) { /* Already detached, or addView failed. */ }
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
internal class OverlaySession(
    private val attach: () -> Unit,
    private val release: () -> Unit,
    private val displayPanel: () -> Unit = {},
    private val displayBubble: () -> Unit = {},
    private val clearPanel: () -> Unit = {},
) {
    var closed = false
        private set
    private var attached = false
    var expanded = false
        private set

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

    fun expand() {
        if (closed || !attached || expanded) return
        try {
            displayPanel()
            expanded = true
        } catch (exception: RuntimeException) {
            close()
            throw exception
        }
    }

    fun collapse() {
        if (closed || !expanded) return
        try {
            clearPanel()
            displayBubble()
            expanded = false
        } catch (exception: RuntimeException) {
            close()
            throw exception
        }
    }

    fun close() {
        if (closed) return
        closed = true
        expanded = false
        release()
    }
}

/** Clearing after cancellation also resets analysis/draft and preserves initialized readiness. */
internal fun clearOverlayReplySession(replies: ReplyCoordinator) {
    replies.cancel()
    replies.setMessage("")
}
