package cvam.dignity.juganua.features.neonpen

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.min

/**
 * Floating Tools Launcher View.
 * - Single Tap: Toggles Pen Mode ON/OFF directly.
 * - Hold (Long-Press): Expands the 4-tool floating palette (Pen, Paper, Eraser, Close).
 */
class FloatingPenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ActiveTool {
        NONE,
        PEN,
        PAPER,
        ERASER
    }

    var onToolSelectedListener: ((ActiveTool) -> Unit)? = null
    var onRequestResizeListener: ((isExpanded: Boolean) -> Unit)? = null
    var onTwoFingerHoldToggleListener: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isLongPressTriggered = false
    private val longPressTimeoutMs = 450L

    private val longPressRunnable = Runnable {
        isLongPressTriggered = true
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {}
        if (!isPanelExpanded) {
            expandPanel()
        }
    }

    private val twoFingerHoldHandler = Handler(Looper.getMainLooper())
    private val twoFingerHoldRunnable = Runnable {
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {}
        onTwoFingerHoldToggleListener?.invoke()
    }

    var isPanelExpanded: Boolean = false
        private set

    var activeTool: ActiveTool = ActiveTool.NONE
        private set

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging = false

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val launcherBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF0055")
    }

    private val launcherInactiveBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#334155")
    }

    private val launcherGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        color = Color.parseColor("#99FF0055")
        maskFilter = BlurMaskFilter(dp(8f), BlurMaskFilter.Blur.NORMAL)
    }

    private val launcherInactiveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.parseColor("#20000000")
    }

    private val launcherBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }

    private val launcherInactiveBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.parseColor("#64748B")
    }

    private val launcherIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val launcherInactiveIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#94A3B8")
    }

    private val activeBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10B981")
    }

    private val activeBadgeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8010B981")
        maskFilter = BlurMaskFilter(dp(3f), BlurMaskFilter.Blur.NORMAL)
    }

    private val panelShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#26000000")
        maskFilter = BlurMaskFilter(dp(8f), BlurMaskFilter.Blur.NORMAL)
    }

    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = Color.parseColor("#FFE2EC")
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFF0F6")
    }

    private val badgeActiveBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF0055")
    }

    private val iconInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#FF0055")
    }

    private val iconActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val textInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        textSize = dp(10.5f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val textActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        textSize = dp(10.5f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val penOptionRect = RectF()
    private val paperOptionRect = RectF()
    private val eraserOptionRect = RectF()
    private val closeOptionRect = RectF()
    private val tempPath = Path()
    private val tempRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isPanelExpanded) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(cx, cy) - dp(4f)

            val isToolActive = activeTool != ActiveTool.NONE

            if (isToolActive) {
                canvas.drawCircle(cx, cy, radius, launcherGlowPaint)
                canvas.drawCircle(cx, cy, radius, launcherBgPaint)
                canvas.drawCircle(cx, cy, radius, launcherBorderPaint)
                drawToolsLauncherIcon(canvas, cx, cy, radius * 0.45f, isToolActive = true)

                val dotCx = cx + radius * 0.55f
                val dotCy = cy - radius * 0.55f
                canvas.drawCircle(dotCx, dotCy, dp(4.5f), activeBadgeGlowPaint)
                canvas.drawCircle(dotCx, dotCy, dp(3.5f), activeBadgePaint)
            } else {
                canvas.drawCircle(cx, cy, radius, launcherInactiveBgPaint)
                canvas.drawCircle(cx, cy, radius, launcherInactiveBorderPaint)
                drawToolsLauncherIcon(canvas, cx, cy, radius * 0.45f, isToolActive = false)
            }
        } else {
            val shadowRect = RectF(dp(3f), dp(4f), width - dp(3f), height - dp(2f))
            val panelRect = RectF(dp(2f), dp(2f), width - dp(2f), height - dp(3f))
            val cornerRadius = panelRect.height() / 2f

            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, panelShadowPaint)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, panelBgPaint)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, panelBorderPaint)

            val sectionWidth = width / 4f
            penOptionRect.set(0f, 0f, sectionWidth, height.toFloat())
            paperOptionRect.set(sectionWidth, 0f, sectionWidth * 2, height.toFloat())
            eraserOptionRect.set(sectionWidth * 2, 0f, sectionWidth * 3, height.toFloat())
            closeOptionRect.set(sectionWidth * 3, 0f, width.toFloat(), height.toFloat())

            drawToolItem(
                canvas = canvas,
                rect = penOptionRect,
                label = "Pen",
                isActive = activeTool == ActiveTool.PEN,
                drawIconLambda = { c, cx, cy, active -> drawPenIcon(c, cx, cy, active) }
            )

            drawToolItem(
                canvas = canvas,
                rect = paperOptionRect,
                label = "Paper",
                isActive = activeTool == ActiveTool.PAPER,
                drawIconLambda = { c, cx, cy, active -> drawPaperIcon(c, cx, cy, active) }
            )

            drawToolItem(
                canvas = canvas,
                rect = eraserOptionRect,
                label = "Eraser",
                isActive = activeTool == ActiveTool.ERASER,
                drawIconLambda = { c, cx, cy, active -> drawEraserIcon(c, cx, cy, active) }
            )

            drawToolItem(
                canvas = canvas,
                rect = closeOptionRect,
                label = "Close",
                isActive = false,
                drawIconLambda = { c, cx, cy, active -> drawCloseIcon(c, cx, cy, active) }
            )
        }
    }

    private fun drawToolItem(
        canvas: Canvas,
        rect: RectF,
        label: String,
        isActive: Boolean,
        drawIconLambda: (Canvas, Float, Float, Boolean) -> Unit
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY() - dp(6f)
        val badgeRadius = dp(20f)

        tempRect.set(cx - badgeRadius, cy - badgeRadius, cx + badgeRadius, cy + badgeRadius)
        canvas.drawRoundRect(tempRect, badgeRadius, badgeRadius, if (isActive) badgeActiveBgPaint else badgeBgPaint)

        drawIconLambda(canvas, cx, cy, isActive)

        val textY = rect.bottom - dp(5f)
        canvas.drawText(label, cx, textY, if (isActive) textActivePaint else textInactivePaint)
    }

    private fun drawToolsLauncherIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, isToolActive: Boolean = true) {
        tempPath.reset()
        tempPath.moveTo(cx - size * 0.45f, cy + size * 0.55f)
        tempPath.lineTo(cx + size * 0.45f, cy - size * 0.45f)
        tempPath.lineTo(cx + size * 0.65f, cy - size * 0.25f)
        tempPath.lineTo(cx - size * 0.25f, cy + size * 0.65f)
        tempPath.close()

        tempPath.moveTo(cx - size * 0.2f, cy - size * 0.5f)
        tempPath.lineTo(cx - size * 0.5f, cy - size * 0.2f)

        val paintToUse = if (isToolActive) launcherIconPaint else launcherInactiveIconPaint
        canvas.drawPath(tempPath, paintToUse)
    }

    private fun drawPenIcon(canvas: Canvas, cx: Float, cy: Float, isActive: Boolean) {
        tempPath.reset()
        val s = dp(12.5f)
        tempPath.moveTo(cx - s, cy + s)
        tempPath.lineTo(cx + s * 0.7f, cy - s * 0.9f)
        tempPath.lineTo(cx + s, cy - s * 0.4f)
        tempPath.lineTo(cx - s * 0.4f, cy + s)
        tempPath.close()
        canvas.drawPath(tempPath, if (isActive) iconActivePaint else iconInactivePaint)
    }

    private fun drawPaperIcon(canvas: Canvas, cx: Float, cy: Float, isActive: Boolean) {
        tempPath.reset()
        val w = dp(10f)
        val h = dp(13f)
        tempPath.addRoundRect(
            RectF(cx - w, cy - h, cx + w, cy + h),
            dp(3f), dp(3f),
            Path.Direction.CW
        )
        tempPath.moveTo(cx - w * 0.5f, cy - h * 0.35f)
        tempPath.lineTo(cx + w * 0.5f, cy - h * 0.35f)
        tempPath.moveTo(cx - w * 0.5f, cy + h * 0.15f)
        tempPath.lineTo(cx + w * 0.3f, cy + h * 0.15f)

        canvas.drawPath(tempPath, if (isActive) iconActivePaint else iconInactivePaint)
    }

    private fun drawEraserIcon(canvas: Canvas, cx: Float, cy: Float, isActive: Boolean) {
        tempPath.reset()
        val s = dp(12f)
        tempPath.moveTo(cx - s * 0.8f, cy + s * 0.3f)
        tempPath.lineTo(cx - s * 0.2f, cy - s * 0.9f)
        tempPath.lineTo(cx + s * 0.9f, cy - s * 0.2f)
        tempPath.lineTo(cx + s * 0.3f, cy + s * 0.9f)
        tempPath.close()

        tempPath.moveTo(cx - s * 0.4f, cy - s * 0.3f)
        tempPath.lineTo(cx + s * 0.1f, cy + s * 0.7f)

        canvas.drawPath(tempPath, if (isActive) iconActivePaint else iconInactivePaint)
    }

    private fun drawCloseIcon(canvas: Canvas, cx: Float, cy: Float, isActive: Boolean) {
        tempPath.reset()
        val s = dp(11f)
        tempPath.moveTo(cx - s, cy - s)
        tempPath.lineTo(cx + s, cy + s)
        tempPath.moveTo(cx + s, cy - s)
        tempPath.lineTo(cx - s, cy + s)
        canvas.drawPath(tempPath, if (isActive) iconActivePaint else iconInactivePaint)
    }

    fun handleTouch(event: MotionEvent, windowManager: WindowManager, params: WindowManager.LayoutParams): Boolean {
        if (event.pointerCount >= 2) {
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                mainHandler.removeCallbacks(longPressRunnable)
                twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
                twoFingerHoldHandler.postDelayed(twoFingerHoldRunnable, 500L)
            } else if (event.actionMasked == MotionEvent.ACTION_POINTER_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isLongPressTriggered = false

                if (!isPanelExpanded) {
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.postDelayed(longPressRunnable, longPressTimeoutMs)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                    twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
                }

                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    clampBounds(params, windowManager)
                    try {
                        windowManager.updateViewLayout(this, params)
                    } catch (_: Exception) {}
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)

                if (!isDragging) {
                    val localX = event.x
                    val localY = event.y

                    if (isLongPressTriggered) {
                        isLongPressTriggered = false
                        return true
                    }

                    if (!isPanelExpanded) {
                        if (activeTool != ActiveTool.NONE) {
                            selectTool(ActiveTool.NONE)
                        } else {
                            selectTool(ActiveTool.PEN)
                        }
                    } else {
                        if (penOptionRect.contains(localX, localY)) {
                            if (activeTool == ActiveTool.PEN) {
                                collapsePanelKeepActiveTool()
                                dockToEdge(params, windowManager)
                            } else {
                                selectTool(ActiveTool.PEN)
                            }
                        } else if (paperOptionRect.contains(localX, localY)) {
                            if (activeTool == ActiveTool.PAPER) {
                                collapsePanelKeepActiveTool()
                                dockToEdge(params, windowManager)
                            } else {
                                selectTool(ActiveTool.PAPER)
                            }
                        } else if (eraserOptionRect.contains(localX, localY)) {
                            if (activeTool == ActiveTool.ERASER) {
                                collapsePanelKeepActiveTool()
                                dockToEdge(params, windowManager)
                            } else {
                                selectTool(ActiveTool.ERASER)
                            }
                        } else if (closeOptionRect.contains(localX, localY)) {
                            collapsePanel()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
            }
        }
        return false
    }

    fun expandPanel() {
        if (isPanelExpanded) return
        isPanelExpanded = true
        if (activeTool == ActiveTool.NONE) {
            activeTool = ActiveTool.PEN
        }
        onRequestResizeListener?.invoke(true)
        onToolSelectedListener?.invoke(activeTool)
        invalidate()
    }

    fun collapsePanel() {
        isPanelExpanded = false
        activeTool = ActiveTool.NONE
        onRequestResizeListener?.invoke(false)
        onToolSelectedListener?.invoke(ActiveTool.NONE)
        invalidate()
    }

    fun collapsePanelKeepActiveTool() {
        if (!isPanelExpanded) return
        isPanelExpanded = false
        onRequestResizeListener?.invoke(false)
        onToolSelectedListener?.invoke(activeTool)
        invalidate()
    }

    fun selectTool(tool: ActiveTool) {
        activeTool = tool
        invalidate()
        onToolSelectedListener?.invoke(activeTool)
    }

    fun setActiveToolExternal(tool: ActiveTool) {
        activeTool = tool
        invalidate()
    }

    private fun dockToEdge(params: WindowManager.LayoutParams, windowManager: WindowManager) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val cx = params.x + width / 2
        if (cx < screenWidth / 2) {
            params.x = 0
        } else {
            params.x = screenWidth - width
        }
        clampBounds(params, windowManager)
        try {
            windowManager.updateViewLayout(this, params)
        } catch (_: Exception) {}
    }

    private fun clampBounds(params: WindowManager.LayoutParams, windowManager: WindowManager) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        params.x = params.x.coerceIn(0, screenWidth - width)
        val minY = (32 * displayMetrics.density).toInt()
        val maxY = screenHeight - height - (48 * displayMetrics.density).toInt()
        params.y = params.y.coerceIn(minY, maxY)
    }
}
