package cvam.dignity.juganua.features.dashytools.neonpen

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Custom overlay view supporting single-finger neon drawing & modern paper surface card.
 */
class NeonDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class DrawingMode {
        DISABLED,
        SCREEN_PEN,
        PAPER,
        PAPER_ERASER
    }

    companion object {
        private const val DEFAULT_DISAPPEAR_DELAY_MS = 1000L
        private const val TRAILING_ERASE_ANIM_DURATION_MS = 380L
        private const val TOUCH_TOLERANCE = 4f
        private const val ERASER_RADIUS_PX = 46f
        private const val PREFS_NAME = "neon_pen_prefs"
        private const val KEY_DURATION = "disappear_duration_sec"
        private const val KEY_DURATION_MS = "disappear_duration_ms"
        private const val KEY_AUTO_DISAPPEAR = "auto_disappear_enabled"
    }

    var currentMode: DrawingMode = DrawingMode.DISABLED
        private set

    var onClosePaperListener: (() -> Unit)? = null
    var onTwoFingerHoldToggleListener: (() -> Unit)? = null

    private val twoFingerHoldHandler = Handler(Looper.getMainLooper())
    private var isTwoFingerHoldTriggered = false
    private var twoFingerStartX1 = 0f
    private var twoFingerStartY1 = 0f
    private var twoFingerStartX2 = 0f
    private var twoFingerStartY2 = 0f

    private val twoFingerHoldRunnable = Runnable {
        isTwoFingerHoldTriggered = true
        try {
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {}
        onTwoFingerHoldToggleListener?.invoke()
    }

    private val outerGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF0055")
        alpha = 90
        strokeWidth = 44f
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }

    private val midGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF007F")
        alpha = 180
        strokeWidth = 22f
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val innerGlowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF1493")
        strokeWidth = 10f
    }

    private val corePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
        strokeWidth = 4f
    }

    private val paperBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFFFFF")
    }

    private val paperShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#2B000000")
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    }

    private val paperHeaderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F1F5F9")
    }

    private val paperHeaderBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#E2E8F0")
    }

    private val paperHeaderTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A")
        textSize = 38f
        isFakeBoldText = true
    }

    private val paperGridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#E0E6ED")
    }

    private val paperHeaderBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FFF0F6")
    }

    private val paperHeaderBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#FFCCE0")
    }

    private val paperHeaderBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        textSize = 32f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val eraserCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        color = Color.parseColor("#FF0055")
    }

    private val eraserCursorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#26FF0055")
    }

    private val screenPath = Path()
    private val screenRenderPath = Path()
    private val pathMeasure = PathMeasure()

    private val paperPaths = mutableListOf<Path>()
    private var currentPaperPath = Path()

    private val paperCardRect = RectF()
    private val paperHeaderRect = RectF()
    private val paperCloseBtnRect = RectF()
    private val paperClearBtnRect = RectF()

    private var lastX = 0f
    private var lastY = 0f
    private var isErasingActive = false

    private var isMultiTouchActive = false
    private var lastMultiTouchX = 0f
    private var lastMultiTouchY = 0f
    private var lastMultiTouchDist = 0f

    private var paperPanX = 0f
    private var paperPanY = 0f
    private var paperScale = 1.0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var eraseAnimator: ValueAnimator? = null
    private var eraseProgress = 0f
    private var isStrokeActive = false

    private val disappearRunnable = Runnable {
        startTrailingEraseAnimation()
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setDrawingMode(mode: DrawingMode) {
        if (currentMode == mode) return
        resetPreviousStroke()
        currentMode = mode
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePaperBounds(w, h)
    }

    private fun updatePaperBounds(w: Int, h: Int) {
        val marginHoriz = w * 0.05f
        val marginTop = h * 0.09f
        val marginBottom = h * 0.11f

        paperCardRect.set(marginHoriz, marginTop, w - marginHoriz, h - marginBottom)
        paperHeaderRect.set(paperCardRect.left, paperCardRect.top, paperCardRect.right, paperCardRect.top + 110f)

        val btnWidth = 125f
        val btnHeight = 65f
        val btnTop = paperHeaderRect.top + 22f

        paperCloseBtnRect.set(paperHeaderRect.right - btnWidth - 24f, btnTop, paperHeaderRect.right - 24f, btnTop + btnHeight)
        paperClearBtnRect.set(paperCloseBtnRect.left - btnWidth - 18f, btnTop, paperCloseBtnRect.left - 18f, btnTop + btnHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (currentMode) {
            DrawingMode.DISABLED -> return

            DrawingMode.SCREEN_PEN -> {
                if (screenPath.isEmpty) return
                val saveCount = canvas.save()

                val pathToDraw = if (eraseProgress > 0f) {
                    screenRenderPath.reset()
                    pathMeasure.setPath(screenPath, false)
                    val totalLength = pathMeasure.length
                    val startDistance = eraseProgress * totalLength
                    pathMeasure.getSegment(startDistance, totalLength, screenRenderPath, true)
                    screenRenderPath
                } else {
                    screenPath
                }

                canvas.drawPath(pathToDraw, outerGlowPaint)
                canvas.drawPath(pathToDraw, midGlowPaint)
                canvas.drawPath(pathToDraw, innerGlowPaint)
                canvas.drawPath(pathToDraw, corePaint)

                canvas.restoreToCount(saveCount)
            }

            DrawingMode.PAPER, DrawingMode.PAPER_ERASER -> {
                val saveCanvas = canvas.save()

                canvas.translate(paperPanX, paperPanY)
                canvas.scale(paperScale, paperScale, paperCardRect.centerX(), paperCardRect.centerY())

                canvas.drawRoundRect(
                    paperCardRect.left + 2f, paperCardRect.top + 12f,
                    paperCardRect.right + 2f, paperCardRect.bottom + 20f,
                    36f, 36f, paperShadowPaint
                )
                canvas.drawRoundRect(paperCardRect, 36f, 36f, paperBgPaint)

                val lineSpacing = 48f
                var currentY = paperHeaderRect.bottom + lineSpacing
                while (currentY < paperCardRect.bottom - 20f) {
                    canvas.drawLine(
                        paperCardRect.left + 24f, currentY,
                        paperCardRect.right - 24f, currentY,
                        paperGridLinePaint
                    )
                    currentY += lineSpacing
                }

                canvas.drawRoundRect(
                    paperHeaderRect.left, paperHeaderRect.top,
                    paperHeaderRect.right, paperHeaderRect.bottom,
                    36f, 36f, paperHeaderBgPaint
                )
                canvas.drawRect(
                    paperHeaderRect.left, paperHeaderRect.bottom - 20f,
                    paperHeaderRect.right, paperHeaderRect.bottom,
                    paperHeaderBgPaint
                )
                canvas.drawLine(
                    paperHeaderRect.left, paperHeaderRect.bottom,
                    paperHeaderRect.right, paperHeaderRect.bottom,
                    paperHeaderBorderPaint
                )

                val headerTitle = if (currentMode == DrawingMode.PAPER_ERASER) "Neon Paper (Eraser)" else "Neon Paper"
                canvas.drawText(headerTitle, paperHeaderRect.left + 32f, paperHeaderRect.top + 68f, paperHeaderTitlePaint)

                canvas.drawRoundRect(paperClearBtnRect, 18f, 18f, paperHeaderBtnBgPaint)
                canvas.drawRoundRect(paperClearBtnRect, 18f, 18f, paperHeaderBtnBorderPaint)
                val clearTextY = paperClearBtnRect.centerY() - ((paperHeaderBtnTextPaint.descent() + paperHeaderBtnTextPaint.ascent()) / 2)
                canvas.drawText("Clear", paperClearBtnRect.centerX(), clearTextY, paperHeaderBtnTextPaint)

                canvas.drawRoundRect(paperCloseBtnRect, 18f, 18f, paperHeaderBtnBgPaint)
                canvas.drawRoundRect(paperCloseBtnRect, 18f, 18f, paperHeaderBtnBorderPaint)
                val closeTextY = paperCloseBtnRect.centerY() - ((paperHeaderBtnTextPaint.descent() + paperHeaderBtnTextPaint.ascent()) / 2)
                canvas.drawText("âœ•", paperCloseBtnRect.centerX(), closeTextY, paperHeaderBtnTextPaint)

                val clipSave = canvas.save()
                canvas.clipRect(
                    paperCardRect.left, paperHeaderRect.bottom,
                    paperCardRect.right, paperCardRect.bottom
                )

                for (p in paperPaths) {
                    canvas.drawPath(p, outerGlowPaint)
                    canvas.drawPath(p, midGlowPaint)
                    canvas.drawPath(p, innerGlowPaint)
                    canvas.drawPath(p, corePaint)
                }

                if (!currentPaperPath.isEmpty) {
                    canvas.drawPath(currentPaperPath, outerGlowPaint)
                    canvas.drawPath(currentPaperPath, midGlowPaint)
                    canvas.drawPath(currentPaperPath, innerGlowPaint)
                    canvas.drawPath(currentPaperPath, corePaint)
                }

                if (currentMode == DrawingMode.PAPER_ERASER && isErasingActive) {
                    canvas.drawCircle(lastX, lastY, ERASER_RADIUS_PX, eraserCursorFillPaint)
                    canvas.drawCircle(lastX, lastY, ERASER_RADIUS_PX, eraserCursorPaint)
                }

                canvas.restoreToCount(clipSave)
                canvas.restoreToCount(saveCanvas)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (currentMode == DrawingMode.DISABLED) return false

        if (event.pointerCount > 1) {
            if (isStrokeActive) {
                isStrokeActive = false
                currentPaperPath.reset()
                screenPath.reset()
                invalidate()
            }

            val x0 = event.getX(0)
            val y0 = event.getY(0)
            val x1 = event.getX(1)
            val y1 = event.getY(1)

            val currentCenterX = (x0 + x1) / 2f
            val currentCenterY = (y0 + y1) / 2f
            val currentDist = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                    isMultiTouchActive = true
                    lastMultiTouchX = currentCenterX
                    lastMultiTouchY = currentCenterY
                    lastMultiTouchDist = currentDist

                    if (event.pointerCount == 2) {
                        twoFingerStartX1 = x0
                        twoFingerStartY1 = y0
                        twoFingerStartX2 = x1
                        twoFingerStartY2 = y1
                        isTwoFingerHoldTriggered = false
                        twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
                        twoFingerHoldHandler.postDelayed(twoFingerHoldRunnable, 500L)
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = currentCenterX - lastMultiTouchX
                    val dy = currentCenterY - lastMultiTouchY

                    if (event.pointerCount == 2 && !isTwoFingerHoldTriggered) {
                        val mDx1 = abs(x0 - twoFingerStartX1)
                        val mDy1 = abs(y0 - twoFingerStartY1)
                        val mDx2 = abs(x1 - twoFingerStartX2)
                        val mDy2 = abs(y1 - twoFingerStartY2)
                        if (mDx1 > 30f || mDy1 > 30f || mDx2 > 30f || mDy2 > 30f) {
                            twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
                        }
                    }

                    if (isTwoFingerHoldTriggered) {
                        return true
                    }

                    if (currentMode == DrawingMode.PAPER || currentMode == DrawingMode.PAPER_ERASER) {
                        paperPanX += dx
                        paperPanY += dy

                        if (lastMultiTouchDist > 10f && currentDist > 10f) {
                            val scaleFactor = currentDist / lastMultiTouchDist
                            paperScale = (paperScale * scaleFactor).coerceIn(0.5f, 3.0f)
                        }
                        invalidate()
                    } else if (currentMode == DrawingMode.SCREEN_PEN) {
                        if (abs(dy) > 10f || abs(dx) > 10f) {
                            NeonAccessibilityService.instance?.dispatchTwoFingerScroll(
                                lastMultiTouchX, lastMultiTouchY,
                                currentCenterX, currentCenterY
                            )
                        } else if (abs(currentDist - lastMultiTouchDist) > 15f) {
                            NeonAccessibilityService.instance?.dispatchPinchZoom(
                                currentCenterX, currentCenterY,
                                lastMultiTouchDist, currentDist
                            )
                        }
                    }

                    lastMultiTouchX = currentCenterX
                    lastMultiTouchY = currentCenterY
                    lastMultiTouchDist = currentDist
                    return true
                }
            }
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
            isMultiTouchActive = true
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            twoFingerHoldHandler.removeCallbacks(twoFingerHoldRunnable)
            isMultiTouchActive = false
        }

        if (isMultiTouchActive) return true

        val rawTouchX = event.x
        val rawTouchY = event.y

        if (currentMode == DrawingMode.PAPER || currentMode == DrawingMode.PAPER_ERASER) {
            val x = (rawTouchX - paperPanX - paperCardRect.centerX()) / paperScale + paperCardRect.centerX()
            val y = (rawTouchY - paperPanY - paperCardRect.centerY()) / paperScale + paperCardRect.centerY()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (paperCloseBtnRect.contains(x, y)) {
                        onClosePaperListener?.invoke()
                        return true
                    }
                    if (paperClearBtnRect.contains(x, y)) {
                        clearPaperStrokes()
                        return true
                    }

                    if (y > paperHeaderRect.bottom && paperCardRect.contains(x, y)) {
                        lastX = x
                        lastY = y

                        if (currentMode == DrawingMode.PAPER_ERASER) {
                            isErasingActive = true
                            erasePaperStrokesNear(x, y)
                        } else {
                            currentPaperPath = Path().apply { moveTo(x, y) }
                            isStrokeActive = true
                        }
                        invalidate()
                        return true
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (currentMode == DrawingMode.PAPER_ERASER) {
                        if (isErasingActive) {
                            lastX = x
                            lastY = y
                            erasePaperStrokesNear(x, y)
                            invalidate()
                        }
                        return true
                    }

                    if (!isStrokeActive) return false
                    val dx = abs(x - lastX)
                    val dy = abs(y - lastY)
                    if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                        currentPaperPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
                        lastX = x
                        lastY = y
                        invalidate()
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentMode == DrawingMode.PAPER_ERASER) {
                        isErasingActive = false
                        invalidate()
                        return true
                    }

                    if (!isStrokeActive) return false
                    currentPaperPath.lineTo(lastX, lastY)
                    paperPaths.add(Path(currentPaperPath))
                    currentPaperPath.reset()
                    isStrokeActive = false
                    invalidate()
                    return true
                }
            }
            return true
        }

        val x = rawTouchX
        val y = rawTouchY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resetPreviousStroke()
                screenPath.moveTo(x, y)
                lastX = x
                lastY = y
                isStrokeActive = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isStrokeActive) return false
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    screenPath.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isStrokeActive) return false
                screenPath.lineTo(lastX, lastY)
                invalidate()
                isStrokeActive = false
                scheduleDisappearance()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun erasePaperStrokesNear(touchX: Float, touchY: Float) {
        val iterator = paperPaths.iterator()
        val tempMeasure = PathMeasure()

        while (iterator.hasNext()) {
            val path = iterator.next()
            tempMeasure.setPath(path, false)
            val length = tempMeasure.length
            var step = 0f
            var hit = false
            val pos = FloatArray(2)

            while (step <= length) {
                tempMeasure.getPosTan(step, pos, null)
                val dist = hypot((touchX - pos[0]).toDouble(), (touchY - pos[1]).toDouble())
                if (dist <= ERASER_RADIUS_PX) {
                    hit = true
                    break
                }
                step += 18f
            }

            if (hit) {
                iterator.remove()
            }
        }
    }

    private fun scheduleDisappearance() {
        mainHandler.removeCallbacks(disappearRunnable)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val autoDisappearEnabled = prefs.getBoolean(KEY_AUTO_DISAPPEAR, true)
        if (!autoDisappearEnabled) return

        val delayMs = if (prefs.contains(KEY_DURATION_MS)) {
            prefs.getLong(KEY_DURATION_MS, DEFAULT_DISAPPEAR_DELAY_MS)
        } else if (prefs.contains(KEY_DURATION)) {
            prefs.getInt(KEY_DURATION, 1) * 1000L
        } else {
            DEFAULT_DISAPPEAR_DELAY_MS
        }

        mainHandler.postDelayed(disappearRunnable, delayMs)
    }

    private fun startTrailingEraseAnimation() {
        eraseAnimator?.cancel()
        eraseAnimator = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
            duration = TRAILING_ERASE_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                eraseProgress = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    clearStroke()
                }
            })
            start()
        }
    }

    fun resetPreviousStroke() {
        mainHandler.removeCallbacks(disappearRunnable)
        eraseAnimator?.cancel()
        eraseAnimator = null
        eraseProgress = 0f
        screenPath.reset()
        screenRenderPath.reset()
        invalidate()
    }

    fun clearPaperStrokes() {
        paperPaths.clear()
        currentPaperPath.reset()
        paperPanX = 0f
        paperPanY = 0f
        paperScale = 1.0f
        invalidate()
    }

    private fun clearStroke() {
        eraseProgress = 0f
        screenPath.reset()
        screenRenderPath.reset()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.removeCallbacks(disappearRunnable)
        eraseAnimator?.cancel()
    }
}
