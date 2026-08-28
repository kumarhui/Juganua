package cvam.dignity.juganua.features.screenshottaker

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import cvam.dignity.juganua.features.settings.JuganuaAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

/**
 * Floating Overlay View for Testbook Screenshot Taker.
 * Features a compact icon-only Ã¢â€“Â¶/Ã¢â€“Â  Start/Stop button, Crop toggle,
 * count controls, and auto-capture sequence execution with 0.3s delay.
 */
class FloatingScreenshotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onRequestResizeListener: ((isExpanded: Boolean) -> Unit)? = null
    var onRequestHideListener: (() -> Unit)? = null

    var isPanelExpanded: Boolean = false
        private set

    var isCapturingSequence: Boolean = false
        private set

    var isCropToolActive: Boolean = false
        private set

    private val prefs = context.getSharedPreferences("screenshot_taker_prefs", Context.MODE_PRIVATE)
    private var targetCount = prefs.getInt(ScreenshotManager.KEY_DEFAULT_SHOT_COUNT, ScreenshotManager.DEFAULT_SHOT_COUNT)
    private var currentProgress = 0
    private var statusText = "Ready"

    private var captureJob: Job? = null
    private val viewScope = CoroutineScope(Dispatchers.Main)

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var isDragging = false

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val brandPurple = Color.parseColor("#8E24AA")
    private val darkBg = Color.parseColor("#0F172A")
    private val accentRed = Color.parseColor("#E53935")
    private val accentGreen = Color.parseColor("#10B981")
    private val cropActiveBg = Color.parseColor("#0284C7")

    private val launcherBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = brandPurple
    }

    private val launcherGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.parseColor("#808E24AA")
        maskFilter = BlurMaskFilter(dp(6f), BlurMaskFilter.Blur.NORMAL)
    }

    private val launcherBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }

    private val panelShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33000000")
        maskFilter = BlurMaskFilter(dp(8f), BlurMaskFilter.Blur.NORMAL)
    }

    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = darkBg
    }

    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        color = Color.parseColor("#334155")
    }

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = brandPurple
    }

    private val stopBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accentRed
    }

    private val smallBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E293B")
    }

    private val cropBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = cropActiveBg
    }

    private val textWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val textMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = dp(9.5f)
        textAlign = Paint.Align.CENTER
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val fillIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cropBtnRect = RectF()
    private val minusBtnRect = RectF()
    private val plusBtnRect = RectF()
    private val actionBtnRect = RectF()
    private val hideBtnRect = RectF()
    private val tempPath = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isPanelExpanded) {
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(cx, cy) - dp(4f)

            canvas.drawCircle(cx, cy, radius, launcherGlowPaint)
            canvas.drawCircle(cx, cy, radius, launcherBgPaint)
            canvas.drawCircle(cx, cy, radius, launcherBorderPaint)
            drawCameraIcon(canvas, cx, cy, radius * 0.45f)

            if (isCapturingSequence) {
                val dotCx = cx + radius * 0.55f
                val dotCy = cy - radius * 0.55f
                canvas.drawCircle(dotCx, dotCy, dp(4f), launcherGlowPaint)
                canvas.drawCircle(dotCx, dotCy, dp(3f), Paint().apply { color = accentGreen })
            }
        } else {
            val shadowRect = RectF(dp(2f), dp(3f), width - dp(2f), height - dp(2f))
            val panelRect = RectF(dp(1f), dp(1f), width - dp(1f), height - dp(2f))
            val cornerRadius = dp(14f)

            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, panelShadowPaint)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, panelBgPaint)
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, panelBorderPaint)

            val sectionW = width / 5f

            // Section 1: Crop Tool Toggle Button
            cropBtnRect.set(dp(6f), dp(6f), sectionW - dp(4f), height - dp(6f))
            val cBg = if (isCropToolActive) cropBtnBgPaint else smallBtnBgPaint
            canvas.drawRoundRect(cropBtnRect, dp(6f), dp(6f), cBg)
            canvas.drawText("Crop", cropBtnRect.centerX(), cropBtnRect.centerY() + dp(3.5f), textWhitePaint)

            // Section 2: Count Controls [-] Count [+]
            minusBtnRect.set(sectionW, dp(6f), sectionW + dp(22f), height - dp(6f))
            plusBtnRect.set(sectionW * 2f - dp(6f), dp(6f), sectionW * 2f + dp(16f), height - dp(6f))

            canvas.drawRoundRect(minusBtnRect, dp(5f), dp(5f), smallBtnBgPaint)
            canvas.drawText("-", minusBtnRect.centerX(), minusBtnRect.centerY() + dp(3.5f), textWhitePaint)

            val countTextX = (minusBtnRect.right + plusBtnRect.left) / 2f
            val labelToDraw = if (isCapturingSequence) "$currentProgress/$targetCount" else "$targetCount"
            canvas.drawText(labelToDraw, countTextX, minusBtnRect.centerY() - dp(1f), textWhitePaint)
            canvas.drawText("Shots", countTextX, minusBtnRect.centerY() + dp(9f), textMutedPaint)

            canvas.drawRoundRect(plusBtnRect, dp(5f), dp(5f), smallBtnBgPaint)
            canvas.drawText("+", plusBtnRect.centerX(), plusBtnRect.centerY() + dp(3.5f), textWhitePaint)

            // Section 3: Icon-Only Action Button ([Ã¢â€“Â¶] / [Ã¢â€“Â ])
            actionBtnRect.set(sectionW * 2.1f + dp(18f), dp(6f), sectionW * 3.5f, height - dp(6f))
            val btnPaint = if (isCapturingSequence) stopBtnBgPaint else btnBgPaint
            canvas.drawRoundRect(actionBtnRect, dp(6f), dp(6f), btnPaint)

            if (isCapturingSequence) {
                contentDescription = "Stop screenshot capture"
                val sSize = dp(6f)
                val sRect = RectF(
                    actionBtnRect.centerX() - sSize,
                    actionBtnRect.centerY() - sSize,
                    actionBtnRect.centerX() + sSize,
                    actionBtnRect.centerY() + sSize
                )
                canvas.drawRoundRect(sRect, dp(2f), dp(2f), fillIconPaint)
            } else {
                contentDescription = "Start screenshot capture"
                tempPath.reset()
                val pSize = dp(6f)
                tempPath.moveTo(actionBtnRect.centerX() - pSize * 0.7f, actionBtnRect.centerY() - pSize)
                tempPath.lineTo(actionBtnRect.centerX() + pSize * 1.1f, actionBtnRect.centerY())
                tempPath.lineTo(actionBtnRect.centerX() - pSize * 0.7f, actionBtnRect.centerY() + pSize)
                tempPath.close()
                canvas.drawPath(tempPath, fillIconPaint)
            }

            // Section 4: Hide Button [Ã¢Å“â€¢]
            hideBtnRect.set(width - dp(28f), dp(6f), width - dp(6f), height - dp(6f))
            canvas.drawRoundRect(hideBtnRect, dp(5f), dp(5f), smallBtnBgPaint)
            canvas.drawText("Ã¢Å“â€¢", hideBtnRect.centerX(), hideBtnRect.centerY() + dp(3.5f), textMutedPaint)
        }
    }

    private fun drawCameraIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        tempPath.reset()
        tempPath.addRoundRect(
            RectF(cx - size, cy - size * 0.6f, cx + size, cy + size * 0.8f),
            dp(3f), dp(3f),
            Path.Direction.CW
        )
        tempPath.moveTo(cx - size * 0.4f, cy - size * 0.6f)
        tempPath.lineTo(cx - size * 0.2f, cy - size * 0.9f)
        tempPath.lineTo(cx + size * 0.2f, cy - size * 0.9f)
        tempPath.lineTo(cx + size * 0.4f, cy - size * 0.6f)

        canvas.drawPath(tempPath, iconPaint)
        canvas.drawCircle(cx, cy + size * 0.1f, size * 0.35f, iconPaint)
    }

    fun setCropToolState(isActive: Boolean) {
        isCropToolActive = isActive
        invalidate()
    }

    fun handleTouch(event: MotionEvent, windowManager: WindowManager, params: WindowManager.LayoutParams): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
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
                if (!isDragging) {
                    val x = event.x
                    val y = event.y

                    if (!isPanelExpanded) {
                        expandPanel()
                    } else {
                        when {
                            cropBtnRect.contains(x, y) -> {
                                toggleCropTool()
                            }
                            minusBtnRect.contains(x, y) -> {
                                if (targetCount > 1) targetCount--
                                invalidate()
                            }
                            plusBtnRect.contains(x, y) -> {
                                if (targetCount < 100) targetCount++
                                invalidate()
                            }
                            actionBtnRect.contains(x, y) -> {
                                if (isCapturingSequence) {
                                    stopCaptureSequence()
                                } else {
                                    startCaptureSequence()
                                }
                            }
                            hideBtnRect.contains(x, y) -> {
                                collapsePanel()
                                onRequestHideListener?.invoke()
                            }
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private fun toggleCropTool() {
        isCropToolActive = !isCropToolActive
        invalidate()
        if (isCropToolActive) {
            JuganuaAccessibilityService.showCropOverlay(context)
        } else {
            JuganuaAccessibilityService.hideCropOverlay()
        }
    }

    fun expandPanel() {
        if (isPanelExpanded) return
        targetCount = prefs.getInt(ScreenshotManager.KEY_DEFAULT_SHOT_COUNT, ScreenshotManager.DEFAULT_SHOT_COUNT)
        isPanelExpanded = true
        onRequestResizeListener?.invoke(true)
        invalidate()
    }

    fun collapsePanel() {
        if (!isPanelExpanded) return
        isPanelExpanded = false
        onRequestResizeListener?.invoke(false)
        invalidate()
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

    fun triggerSingleManualAreaCapture() {
        val service = JuganuaAccessibilityService.instance ?: return
        viewScope.launch(Dispatchers.IO) {
            var done = false
            withContext(Dispatchers.Main) {
                service.captureScreen(
                    onSuccess = { bmp ->
                        viewScope.launch(Dispatchers.IO) {
                            ScreenshotManager.saveScreenshotBitmap(context, bmp)
                            done = true
                        }
                    },
                    onError = { err ->
                        done = true
                        viewScope.launch(Dispatchers.Main) {
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    private fun startCaptureSequence() {
        val service = JuganuaAccessibilityService.instance
        if (service == null) {
            statusText = "No Service"
            invalidate()
            return
        }

        if (isCropToolActive) {
            isCropToolActive = false
            JuganuaAccessibilityService.hideCropOverlay()
        }

        isCapturingSequence = true
        currentProgress = 0
        statusText = "0/$targetCount"
        invalidate()

        val autoAdvance = prefs.getBoolean(ScreenshotManager.KEY_AUTO_ADVANCE, true)
        val tapX = ScreenshotManager.DEFAULT_TAP_X
        val tapY = ScreenshotManager.DEFAULT_TAP_Y
        val delayMs = prefs.getLong(ScreenshotManager.KEY_DELAY_MS, ScreenshotManager.DEFAULT_DELAY_MS) // 0.3s default

        captureJob = viewScope.launch(Dispatchers.IO) {
            for (i in 1..targetCount) {
                if (!isCapturingSequence) break

                withContext(Dispatchers.Main) {
                    currentProgress = i
                    invalidate()
                }

                var stepDone = false
                var stepError: String? = null

                // Step 1: Capture Screenshot FIRST
                withContext(Dispatchers.Main) {
                    service.captureScreen(
                        onSuccess = { bmp ->
                            viewScope.launch(Dispatchers.IO) {
                                ScreenshotManager.saveScreenshotBitmap(context, bmp)
                                stepDone = true
                            }
                        },
                        onError = { err ->
                            stepError = err
                            stepDone = true
                        }
                    )
                }

                while (!stepDone && isCapturingSequence) {
                    delay(50)
                }

                if (stepError != null) {
                    withContext(Dispatchers.Main) {
                        statusText = "Error"
                        isCapturingSequence = false
                        invalidate()
                    }
                    break
                }

                // Step 2: Show Visual Click Marker AFTER screenshot is safely captured
                if (autoAdvance && i < targetCount && isCapturingSequence) {
                    withContext(Dispatchers.Main) {
                        service.showClickIndicator(tapX, tapY)
                        service.dispatchTap(tapX, tapY) {
                            viewScope.launch(Dispatchers.Main) {
                                delay(200)
                                service.hideClickIndicator()
                            }
                        }
                    }
                }

                delay(delayMs)
            }

            withContext(Dispatchers.Main) {
                service.hideClickIndicator()
                if (isCapturingSequence) {
                    statusText = "Done!"
                    isCapturingSequence = false
                    try {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } catch (_: Exception) {}
                } else {
                    statusText = "Stopped"
                }
                invalidate()
            }
        }
    }

    private fun stopCaptureSequence() {
        isCapturingSequence = false
        captureJob?.cancel()
        captureJob = null
        JuganuaAccessibilityService.instance?.hideClickIndicator()
        statusText = "Stopped"
        invalidate()
    }
}

