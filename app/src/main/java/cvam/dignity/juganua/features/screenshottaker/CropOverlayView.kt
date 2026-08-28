package cvam.dignity.juganua.features.screenshottaker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * Draggable & resizable crop rectangle overlay.
 * Displays live screen coordinates for all four corners, a Copy Coordinates button,
 * a [Ã°Å¸â€œÂ¸ Capture Area] manual capture button, and a prominent [Ã¢Å“â€¢ Exit Crop] button.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class TouchMode {
        NONE, BODY, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    var onCloseCropListener: (() -> Unit)? = null
    var onCaptureAreaRequestedListener: (() -> Unit)? = null

    private var activeMode = TouchMode.NONE
    private var isCropVisible = true

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val minSizePx = dp(80f)
    private val handleRadiusPx = dp(16f)

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    val cropRect = RectF()

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#55000000")
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.parseColor("#00E5FF")
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8E24AA")
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = Color.WHITE
    }

    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC0F172A")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11f)
        isFakeBoldText = true
    }

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8E24AA")
    }

    private val captureBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10B981")
    }

    private val closeBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E53935")
    }

    private val copyBtnRect = RectF()
    private val captureAreaBtnRect = RectF()
    private val exitBtnRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (cropRect.isEmpty && w > 0 && h > 0) {
            val marginHoriz = w * 0.08f
            val topPx = (ScreenshotManager.DEFAULT_CROP_TOP * (h / 2400f)).coerceIn(dp(60f), h * 0.3f)
            val bottomPx = (ScreenshotManager.DEFAULT_CROP_BOTTOM * (h / 2400f)).coerceIn(topPx + minSizePx, h - dp(60f))
            cropRect.set(marginHoriz, topPx, w - marginHoriz, bottomPx)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isCropVisible) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw dimmed background surrounding crop area
        canvas.drawRect(0f, 0f, w, cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, w, h, dimPaint)

        // 2. Draw neon crop border
        canvas.drawRect(cropRect, borderPaint)

        // 3. Draw 4 Corner Handles
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)

        // 4. Draw Corner Coordinate Badges
        val x1 = cropRect.left.toInt()
        val y1 = cropRect.top.toInt()
        val x2 = cropRect.right.toInt()
        val y2 = cropRect.top.toInt()
        val x3 = cropRect.left.toInt()
        val y3 = cropRect.bottom.toInt()
        val x4 = cropRect.right.toInt()
        val y4 = cropRect.bottom.toInt()

        drawCoordBadge(canvas, "TL ($x1,$y1)", cropRect.left, max(dp(20f), cropRect.top - dp(10f)), alignLeft = true)
        drawCoordBadge(canvas, "TR ($x2,$y2)", cropRect.right, max(dp(20f), cropRect.top - dp(10f)), alignLeft = false)
        drawCoordBadge(canvas, "BL ($x3,$y3)", cropRect.left, min(h - dp(10f), cropRect.bottom + dp(22f)), alignLeft = true)
        drawCoordBadge(canvas, "BR ($x4,$y4)", cropRect.right, min(h - dp(10f), cropRect.bottom + dp(22f)), alignLeft = false)

        // 5. Draw "Copy Coordinates" Pill Button
        val copyW = dp(120f)
        val copyH = dp(28f)
        val copyX = cropRect.centerX() - copyW - dp(6f)
        val copyY = cropRect.centerY() - (copyH / 2f)
        copyBtnRect.set(copyX, copyY, copyX + copyW, copyY + copyH)

        canvas.drawRoundRect(copyBtnRect, dp(14f), dp(14f), btnBgPaint)
        val copyText = "Ã°Å¸â€œâ€¹ Copy Coords"
        val copyTextWidth = textPaint.measureText(copyText)
        canvas.drawText(copyText, copyBtnRect.centerX() - (copyTextWidth / 2f), copyBtnRect.centerY() + dp(3.5f), textPaint)

        // 6. Draw "Capture This Area" Button
        val capW = dp(130f)
        val capH = dp(28f)
        val capX = cropRect.centerX() + dp(6f)
        val capY = cropRect.centerY() - (capH / 2f)
        captureAreaBtnRect.set(capX, capY, capX + capW, capY + capH)

        canvas.drawRoundRect(captureAreaBtnRect, dp(14f), dp(14f), captureBtnBgPaint)
        val capText = "Ã°Å¸â€œÂ¸ Capture Area"
        val capTextWidth = textPaint.measureText(capText)
        canvas.drawText(capText, captureAreaBtnRect.centerX() - (capTextWidth / 2f), captureAreaBtnRect.centerY() + dp(3.5f), textPaint)

        // 7. Draw Explicit [Ã¢Å“â€¢ Exit Crop] Pill Button at Top Right
        val exitW = dp(90f)
        val exitH = dp(28f)
        val exitX = cropRect.right - exitW
        val exitY = max(dp(8f), cropRect.top - exitH - dp(8f))
        exitBtnRect.set(exitX, exitY, exitX + exitW, exitY + exitH)

        canvas.drawRoundRect(exitBtnRect, dp(14f), dp(14f), closeBtnBgPaint)
        val exitText = "Ã¢Å“â€¢ Exit Crop"
        val exitTextWidth = textPaint.measureText(exitText)
        canvas.drawText(exitText, exitBtnRect.centerX() - (exitTextWidth / 2f), exitBtnRect.centerY() + dp(3.5f), textPaint)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadiusPx, handleFillPaint)
        canvas.drawCircle(cx, cy, handleRadiusPx, handleStrokePaint)
    }

    private fun drawCoordBadge(canvas: Canvas, text: String, anchorX: Float, anchorY: Float, alignLeft: Boolean) {
        val padding = dp(4f)
        val textWidth = textPaint.measureText(text)
        val rectW = textWidth + (padding * 2)
        val rectH = dp(18f)

        val rectLeft = if (alignLeft) anchorX else anchorX - rectW
        val rectTop = anchorY - (rectH / 2f)

        val badgeRect = RectF(rectLeft, rectTop, rectLeft + rectW, rectTop + rectH)
        canvas.drawRoundRect(badgeRect, dp(4f), dp(4f), textBgPaint)
        canvas.drawText(text, rectLeft + padding, rectTop + rectH - dp(4f), textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isCropVisible) return false

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (exitBtnRect.contains(x, y)) {
                    hideCropUi()
                    onCloseCropListener?.invoke()
                    return true
                }

                if (copyBtnRect.contains(x, y)) {
                    copyCoordinatesToClipboard()
                    return true
                }

                if (captureAreaBtnRect.contains(x, y)) {
                    onCaptureAreaRequestedListener?.invoke()
                    return true
                }

                activeMode = when {
                    isNear(x, y, cropRect.left, cropRect.top) -> TouchMode.TOP_LEFT
                    isNear(x, y, cropRect.right, cropRect.top) -> TouchMode.TOP_RIGHT
                    isNear(x, y, cropRect.left, cropRect.bottom) -> TouchMode.BOTTOM_LEFT
                    isNear(x, y, cropRect.right, cropRect.bottom) -> TouchMode.BOTTOM_RIGHT
                    cropRect.contains(x, y) -> TouchMode.BODY
                    else -> TouchMode.NONE
                }

                lastTouchX = x
                lastTouchY = y
                return activeMode != TouchMode.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                val w = width.toFloat()
                val h = height.toFloat()

                when (activeMode) {
                    TouchMode.BODY -> {
                        var newLeft = cropRect.left + dx
                        var newTop = cropRect.top + dy
                        var newRight = cropRect.right + dx
                        var newBottom = cropRect.bottom + dy

                        if (newLeft < 0f) {
                            newRight -= newLeft
                            newLeft = 0f
                        }
                        if (newRight > w) {
                            newLeft -= (newRight - w)
                            newRight = w
                        }
                        if (newTop < 0f) {
                            newBottom -= newTop
                            newTop = 0f
                        }
                        if (newBottom > h) {
                            newTop -= (newBottom - h)
                            newBottom = h
                        }

                        cropRect.set(newLeft, newTop, newRight, newBottom)
                    }

                    TouchMode.TOP_LEFT -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSizePx)
                        cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSizePx)
                    }

                    TouchMode.TOP_RIGHT -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSizePx, w)
                        cropRect.top = (cropRect.top + dy).coerceIn(0f, cropRect.bottom - minSizePx)
                    }

                    TouchMode.BOTTOM_LEFT -> {
                        cropRect.left = (cropRect.left + dx).coerceIn(0f, cropRect.right - minSizePx)
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSizePx, h)
                    }

                    TouchMode.BOTTOM_RIGHT -> {
                        cropRect.right = (cropRect.right + dx).coerceIn(cropRect.left + minSizePx, w)
                        cropRect.bottom = (cropRect.bottom + dy).coerceIn(cropRect.top + minSizePx, h)
                    }

                    TouchMode.NONE -> {}
                }

                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeMode = TouchMode.NONE
                return true
            }
        }
        return false
    }

    private fun isNear(tx: Float, ty: Float, cx: Float, cy: Float): Boolean {
        val r = handleRadiusPx * 1.8f
        return tx >= cx - r && tx <= cx + r && ty >= cy - r && ty <= cy + r
    }

    fun getCropRectNormalized(): RectF {
        if (width <= 0 || height <= 0) return RectF(0f, 0f, 1f, 1f)
        return RectF(
            (cropRect.left / width).coerceIn(0f, 1f),
            (cropRect.top / height).coerceIn(0f, 1f),
            (cropRect.right / width).coerceIn(0f, 1f),
            (cropRect.bottom / height).coerceIn(0f, 1f)
        )
    }

    fun hideCropUi() {
        isCropVisible = false
        invalidate()
    }

    fun showCropUi() {
        isCropVisible = true
        invalidate()
    }

    private fun copyCoordinatesToClipboard() {
        val text = """
            Top Left: (${cropRect.left.toInt()}, ${cropRect.top.toInt()})
            Top Right: (${cropRect.right.toInt()}, ${cropRect.top.toInt()})
            Bottom Left: (${cropRect.left.toInt()}, ${cropRect.bottom.toInt()})
            Bottom Right: (${cropRect.right.toInt()}, ${cropRect.bottom.toInt()})
        """.trimIndent()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crop Coordinates", text))
        Toast.makeText(context, "Coordinates copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
