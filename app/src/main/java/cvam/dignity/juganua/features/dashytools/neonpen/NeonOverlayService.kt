package cvam.dignity.juganua.features.dashytools.neonpen

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager

/**
 * Foreground service managing overlay views for FloatingPenView (Tools Launcher) and NeonDrawingView.
 */
class NeonOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "neon_overlay_service_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        var onServiceStateChanged: ((Boolean) -> Unit)? = null

        fun startService(context: Context) {
            if (isServiceRunning) return
            val intent = Intent(context, NeonOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NeonOverlayService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingPenView: FloatingPenView? = null
    private var neonDrawingView: NeonDrawingView? = null

    private var lastActiveTool: FloatingPenView.ActiveTool = FloatingPenView.ActiveTool.PEN

    private lateinit var floatingParams: WindowManager.LayoutParams
    private lateinit var drawingParams: WindowManager.LayoutParams

    private val collapsedButtonSizeDp = 52
    private val expandedPanelWidthDp = 272
    private val expandedPanelHeightDp = 70

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        onServiceStateChanged?.invoke(true)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        setupOverlayViews()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Neon Pen Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating neon tools button visible above other apps."
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Neon Pen Active")
                .setContentText("Tap floating pen to toggle drawing, or hold to open tools.")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayViews() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        drawingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val density = resources.displayMetrics.density
        val buttonSizePx = (collapsedButtonSizeDp * density).toInt()

        floatingParams = WindowManager.LayoutParams(
            buttonSizePx,
            buttonSizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = (200 * density).toInt()
        }

        neonDrawingView = NeonDrawingView(this)
        floatingPenView = FloatingPenView(this)

        floatingPenView?.setOnTouchListener { _, event ->
            floatingPenView?.handleTouch(event, windowManager, floatingParams) ?: false
        }

        floatingPenView?.onRequestResizeListener = { isExpanded ->
            updateFloatingWindowSize(isExpanded)
        }

        floatingPenView?.onToolSelectedListener = { selectedTool ->
            updateActiveToolState(selectedTool)
        }

        neonDrawingView?.onClosePaperListener = {
            floatingPenView?.setActiveToolExternal(FloatingPenView.ActiveTool.NONE)
            floatingPenView?.collapsePanel()
            updateActiveToolState(FloatingPenView.ActiveTool.NONE)
        }

        val toggleAction = {
            val currentTool = floatingPenView?.activeTool ?: FloatingPenView.ActiveTool.NONE
            if (currentTool != FloatingPenView.ActiveTool.NONE) {
                lastActiveTool = currentTool
                floatingPenView?.setActiveToolExternal(FloatingPenView.ActiveTool.NONE)
                floatingPenView?.collapsePanel()
                updateActiveToolState(FloatingPenView.ActiveTool.NONE)
            } else {
                val targetTool = if (lastActiveTool != FloatingPenView.ActiveTool.NONE) lastActiveTool else FloatingPenView.ActiveTool.PEN
                floatingPenView?.setActiveToolExternal(targetTool)
                updateActiveToolState(targetTool)
            }
        }

        neonDrawingView?.onTwoFingerHoldToggleListener = toggleAction
        floatingPenView?.onTwoFingerHoldToggleListener = toggleAction

        try {
            windowManager.addView(neonDrawingView, drawingParams)
            windowManager.addView(floatingPenView, floatingParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateActiveToolState(tool: FloatingPenView.ActiveTool) {
        val view = neonDrawingView ?: return

        if (tool != FloatingPenView.ActiveTool.NONE) {
            lastActiveTool = tool
        }

        when (tool) {
            FloatingPenView.ActiveTool.PEN -> {
                drawingParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                view.setDrawingMode(NeonDrawingView.DrawingMode.SCREEN_PEN)
            }
            FloatingPenView.ActiveTool.PAPER -> {
                drawingParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                view.setDrawingMode(NeonDrawingView.DrawingMode.PAPER)
            }
            FloatingPenView.ActiveTool.ERASER -> {
                drawingParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                view.setDrawingMode(NeonDrawingView.DrawingMode.PAPER_ERASER)
            }
            FloatingPenView.ActiveTool.NONE -> {
                drawingParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                view.setDrawingMode(NeonDrawingView.DrawingMode.DISABLED)
            }
        }

        try {
            windowManager.updateViewLayout(view, drawingParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFloatingWindowSize(isExpanded: Boolean) {
        val view = floatingPenView ?: return
        val density = resources.displayMetrics.density

        if (isExpanded) {
            floatingParams.width = (expandedPanelWidthDp * density).toInt()
            floatingParams.height = (expandedPanelHeightDp * density).toInt()
        } else {
            floatingParams.width = (collapsedButtonSizeDp * density).toInt()
            floatingParams.height = (collapsedButtonSizeDp * density).toInt()
        }

        try {
            windowManager.updateViewLayout(view, floatingParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        onServiceStateChanged?.invoke(false)

        neonDrawingView?.resetPreviousStroke()

        neonDrawingView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
            }
        }
        floatingPenView?.let {
            if (it.isAttachedToWindow) {
                try {
                    windowManager.removeView(it)
                } catch (_: Exception) {}
            }
        }
        neonDrawingView = null
        floatingPenView = null
    }
}
