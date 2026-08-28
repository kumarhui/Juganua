package cvam.dignity.juganua.features.dashytools.screenshottaker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Transparent debugging overlay that displays a visual click ripple and coordinate badge
 * at the exact coordinates (X, Y) immediately AFTER screenshot capture.
 */
class ClickIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var markerX = -1f
    private var markerY = -1f
    private var isMarkerVisible = false
    private var rippleRadius = 0f

    private val density = context.resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val targetCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF0055")
    }

    private val targetBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.WHITE
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.parseColor("#00E5FF")
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E60F172A")
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = dp(11f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private var pulseAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun showMarker(x: Float, y: Float) {
        markerX = x
        markerY = y
        isMarkerVisible = true

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(dp(10f), dp(36f)).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                rippleRadius = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    fun hideMarker() {
        isMarkerVisible = false
        pulseAnimator?.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isMarkerVisible || markerX < 0f || markerY < 0f) return

        // 1. Draw Expanding Neon Ripple
        ripplePaint.alpha = ((1f - (rippleRadius / dp(36f))) * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(markerX, markerY, rippleRadius, ripplePaint)

        // 2. Draw Target Center Circle
        canvas.drawCircle(markerX, markerY, dp(8f), targetCenterPaint)
        canvas.drawCircle(markerX, markerY, dp(8f), targetBorderPaint)

        // 3. Draw Coordinate Text Badge
        val label = "Tap (${markerX.toInt()}, ${markerY.toInt()})"
        val textWidth = badgeTextPaint.measureText(label)
        val badgeW = textWidth + dp(16f)
        val badgeH = dp(22f)

        val badgeLeft = (markerX - badgeW / 2f).coerceIn(dp(8f), width - badgeW - dp(8f))
        val badgeTop = if (markerY > dp(60f)) markerY - dp(38f) else markerY + dp(16f)

        val badgeRect = RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH)
        canvas.drawRoundRect(badgeRect, dp(6f), dp(6f), badgeBgPaint)
        canvas.drawText(label, badgeRect.centerX(), badgeRect.centerY() + dp(4f), badgeTextPaint)
    }
}
