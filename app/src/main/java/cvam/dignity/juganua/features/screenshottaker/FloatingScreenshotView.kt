package cvam.dignity.juganua.features.screenshottaker

import cvam.dignity.juganua.features.screenshottaker.ScreenshotManager

import android.app.AlertDialog
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
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
 * Modern floating screenshot toolbar.
 *
 * Features:
 * - Crop toggle
 * - Screenshot count: 1..25
 * - Large +/- buttons
 * - Manual count input
 * - Custom 1..25 slider
 * - Default count = 25
 * - Start / Stop capture
 * - Hide toolbar
 */
class FloatingScreenshotView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SHOTS = 1
        private const val MAX_SHOTS = 25
    }

    var onRequestResizeListener: ((isExpanded: Boolean) -> Unit)? = null
    var onRequestHideListener: (() -> Unit)? = null

    var isPanelExpanded: Boolean = false
        private set

    var isCapturingSequence: Boolean = false
        private set

    var isCropToolActive: Boolean = false
        private set

    private val prefs = context.getSharedPreferences(
        "screenshot_taker_prefs",
        Context.MODE_PRIVATE
    )

    private var targetCount = prefs.getInt(
        ScreenshotManager.KEY_DEFAULT_SHOT_COUNT,
        ScreenshotManager.DEFAULT_SHOT_COUNT
    ).coerceIn(MIN_SHOTS, MAX_SHOTS)

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

    // Used when the user is dragging the slider.
    private var isSliderDragging = false

    private val density = context.resources.displayMetrics.density

    private fun dp(value: Float): Float = value * density

    private val brandPurple = Color.parseColor("#8E24AA")
    private val darkBg = Color.parseColor("#0F172A")
    private val darkSurface = Color.parseColor("#1E293B")
    private val sliderInactive = Color.parseColor("#334155")
    private val sliderActive = Color.parseColor("#A855F7")
    private val accentRed = Color.parseColor("#E53935")
    private val accentGreen = Color.parseColor("#10B981")
    private val cropActiveBg = Color.parseColor("#0284C7")
    private val mutedText = Color.parseColor("#94A3B8")

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
        color = Color.parseColor("#55000000")
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
        color = darkSurface
    }

    private val cropBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = cropActiveBg
    }

    private val sliderInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        color = sliderInactive
    }

    private val sliderActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        color = sliderActive
    }

    private val sliderThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val sliderThumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = sliderActive
    }

    private val textWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(11f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(20f)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedText
        textSize = dp(9f)
        textAlign = Paint.Align.CENTER
    }

    private val sliderLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedText
        textSize = dp(8.5f)
        textAlign = Paint.Align.CENTER
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val largeMinusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.8f)
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val largePlusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.8f)
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val fillIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cropBtnRect = RectF()
    private val minusBtnRect = RectF()
    private val countBtnRect = RectF()
    private val plusBtnRect = RectF()
    private val actionBtnRect = RectF()
    private val hideBtnRect = RectF()
    private val sliderRect = RectF()

    private val tempPath = Path()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Screenshot toolbar"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isPanelExpanded) {
            drawLauncher(canvas)
        } else {
            drawExpandedPanel(canvas)
        }
    }

    private fun drawLauncher(canvas: Canvas) {
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

            canvas.drawCircle(
                dotCx,
                dotCy,
                dp(4f),
                launcherGlowPaint
            )

            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentGreen
            }.also {
                canvas.drawCircle(dotCx, dotCy, dp(3f), it)
            }
        }
    }

    private fun drawExpandedPanel(canvas: Canvas) {
        val shadowRect = RectF(
            dp(2f),
            dp(3f),
            width - dp(2f),
            height - dp(2f)
        )

        val panelRect = RectF(
            dp(1f),
            dp(1f),
            width - dp(1f),
            height - dp(2f)
        )

        val cornerRadius = dp(16f)

        canvas.drawRoundRect(
            shadowRect,
            cornerRadius,
            cornerRadius,
            panelShadowPaint
        )

        canvas.drawRoundRect(
            panelRect,
            cornerRadius,
            cornerRadius,
            panelBgPaint
        )

        canvas.drawRoundRect(
            panelRect,
            cornerRadius,
            cornerRadius,
            panelBorderPaint
        )

        /*
         * ---------------------------------------------------------
         * TOP ROW
         * ---------------------------------------------------------
         */

        val top = dp(7f)
        val bottom = dp(52f)

        // Crop
        cropBtnRect.set(
            dp(7f),
            top,
            dp(57f),
            bottom
        )

        val cropPaint =
            if (isCropToolActive) cropBtnBgPaint
            else smallBtnBgPaint

        canvas.drawRoundRect(
            cropBtnRect,
            dp(8f),
            dp(8f),
            cropPaint
        )

        canvas.drawText(
            "Crop",
            cropBtnRect.centerX(),
            cropBtnRect.centerY() + dp(3.5f),
            textWhitePaint
        )

        // Minus
        minusBtnRect.set(
            dp(62f),
            top,
            dp(96f),
            bottom
        )

        canvas.drawRoundRect(
            minusBtnRect,
            dp(9f),
            dp(9f),
            smallBtnBgPaint
        )

        drawMinus(
            canvas,
            minusBtnRect.centerX(),
            minusBtnRect.centerY()
        )

        // Count
        countBtnRect.set(
            dp(100f),
            top,
            dp(154f),
            bottom
        )

        canvas.drawRoundRect(
            countBtnRect,
            dp(9f),
            dp(9f),
            smallBtnBgPaint
        )

        val countText =
            if (isCapturingSequence) {
                "$currentProgress/$targetCount"
            } else {
                targetCount.toString()
            }

        canvas.drawText(
            countText,
            countBtnRect.centerX(),
            countBtnRect.centerY() + dp(1f),
            countPaint
        )

        canvas.drawText(
            "SHOTS",
            countBtnRect.centerX(),
            countBtnRect.bottom - dp(7f),
            labelPaint
        )

        // Plus
        plusBtnRect.set(
            dp(158f),
            top,
            dp(192f),
            bottom
        )

        canvas.drawRoundRect(
            plusBtnRect,
            dp(9f),
            dp(9f),
            smallBtnBgPaint
        )

        drawPlus(
            canvas,
            plusBtnRect.centerX(),
            plusBtnRect.centerY()
        )

        // Start / Stop
        actionBtnRect.set(
            dp(198f),
            top,
            width - dp(35f),
            bottom
        )

        val actionPaint =
            if (isCapturingSequence) stopBtnBgPaint
            else btnBgPaint

        canvas.drawRoundRect(
            actionBtnRect,
            dp(9f),
            dp(9f),
            actionPaint
        )

        if (isCapturingSequence) {
            contentDescription = "Stop screenshot capture"

            val size = dp(7f)

            canvas.drawRoundRect(
                RectF(
                    actionBtnRect.centerX() - size,
                    actionBtnRect.centerY() - size,
                    actionBtnRect.centerX() + size,
                    actionBtnRect.centerY() + size
                ),
                dp(2f),
                dp(2f),
                fillIconPaint
            )
        } else {
            contentDescription = "Start screenshot capture"

            tempPath.reset()

            val pSize = dp(7f)

            tempPath.moveTo(
                actionBtnRect.centerX() - pSize * 0.7f,
                actionBtnRect.centerY() - pSize
            )

            tempPath.lineTo(
                actionBtnRect.centerX() + pSize * 1.1f,
                actionBtnRect.centerY()
            )

            tempPath.lineTo(
                actionBtnRect.centerX() - pSize * 0.7f,
                actionBtnRect.centerY() + pSize
            )

            tempPath.close()

            canvas.drawPath(
                tempPath,
                fillIconPaint
            )
        }

        // Hide
        hideBtnRect.set(
            width - dp(30f),
            top,
            width - dp(7f),
            bottom
        )

        canvas.drawRoundRect(
            hideBtnRect,
            dp(8f),
            dp(8f),
            smallBtnBgPaint
        )

        drawX(
            canvas,
            hideBtnRect.centerX(),
            hideBtnRect.centerY()
        )

        /*
         * ---------------------------------------------------------
         * SLIDER ROW
         * ---------------------------------------------------------
         */

        sliderRect.set(
            dp(17f),
            dp(67f),
            width - dp(17f),
            dp(91f)
        )

        drawSlider(canvas)
    }

    private fun drawSlider(canvas: Canvas) {
        val startX = dp(20f)
        val endX = width - dp(20f)
        val centerY = dp(82f)

        val fraction =
            (targetCount - MIN_SHOTS).toFloat() /
                    (MAX_SHOTS - MIN_SHOTS).toFloat()

        val thumbX =
            startX + (endX - startX) * fraction

        // Track
        canvas.drawLine(
            startX,
            centerY,
            endX,
            centerY,
            sliderInactivePaint
        )

        // Active track
        canvas.drawLine(
            startX,
            centerY,
            thumbX,
            centerY,
            sliderActivePaint
        )

        // End labels
        canvas.drawText(
            "1",
            startX,
            dp(98f),
            sliderLabelPaint
        )

        canvas.drawText(
            "25",
            endX,
            dp(98f),
            sliderLabelPaint
        )

        // Thumb shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55000000")
            style = Paint.Style.FILL
        }

        canvas.drawCircle(
            thumbX,
            centerY + dp(1.5f),
            dp(9f),
            shadowPaint
        )

        // Thumb
        canvas.drawCircle(
            thumbX,
            centerY,
            dp(8f),
            sliderThumbPaint
        )

        canvas.drawCircle(
            thumbX,
            centerY,
            dp(8f),
            sliderThumbBorderPaint
        )
    }

    private fun drawMinus(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        val size = dp(9f)

        canvas.drawLine(
            cx - size,
            cy,
            cx + size,
            cy,
            largeMinusPaint
        )
    }

    private fun drawPlus(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        val size = dp(9f)

        canvas.drawLine(
            cx - size,
            cy,
            cx + size,
            cy,
            largePlusPaint
        )

        canvas.drawLine(
            cx,
            cy - size,
            cx,
            cy + size,
            largePlusPaint
        )
    }

    private fun drawX(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        val size = dp(6f)

        canvas.drawLine(
            cx - size,
            cy - size,
            cx + size,
            cy + size,
            iconPaint
        )

        canvas.drawLine(
            cx + size,
            cy - size,
            cx - size,
            cy + size,
            iconPaint
        )
    }

    private fun drawCameraIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float
    ) {
        tempPath.reset()

        tempPath.addRoundRect(
            RectF(
                cx - size,
                cy - size * 0.6f,
                cx + size,
                cy + size * 0.8f
            ),
            dp(3f),
            dp(3f),
            Path.Direction.CW
        )

        tempPath.moveTo(
            cx - size * 0.4f,
            cy - size * 0.6f
        )

        tempPath.lineTo(
            cx - size * 0.2f,
            cy - size * 0.9f
        )

        tempPath.lineTo(
            cx + size * 0.2f,
            cy - size * 0.9f
        )

        tempPath.lineTo(
            cx + size * 0.4f,
            cy - size * 0.6f
        )

        canvas.drawPath(
            tempPath,
            iconPaint
        )

        canvas.drawCircle(
            cx,
            cy + size * 0.1f,
            size * 0.35f,
            iconPaint
        )
    }

    fun setCropToolState(isActive: Boolean) {
        isCropToolActive = isActive
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return true
    }

    fun handleTouch(
        event: MotionEvent,
        windowManager: WindowManager,
        params: WindowManager.LayoutParams
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                initialX = params.x
                initialY = params.y

                initialTouchX = event.rawX
                initialTouchY = event.rawY

                isDragging = false
                isSliderDragging = false

                if (isPanelExpanded) {
                    val x = event.x
                    val y = event.y

                    if (sliderRect.contains(x, y)) {
                        isSliderDragging = true
                        updateCountFromSlider(x)
                        return true
                    }
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (isSliderDragging && isPanelExpanded) {
                    updateCountFromSlider(event.x)
                    return true
                }

                if (!isDragging &&
                    hypot(
                        dx.toDouble(),
                        dy.toDouble()
                    ) > touchSlop
                ) {
                    isDragging = true
                }

                if (isDragging) {

                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()

                    clampBounds(
                        params,
                        windowManager
                    )

                    try {
                        windowManager.updateViewLayout(
                            this,
                            params
                        )
                    } catch (_: Exception) {
                    }
                }

                return true
            }

            MotionEvent.ACTION_UP -> {

                if (isSliderDragging) {
                    isSliderDragging = false
                    performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    return true
                }

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
                                changeTargetCount(-1)
                            }

                            plusBtnRect.contains(x, y) -> {
                                changeTargetCount(1)
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

            MotionEvent.ACTION_CANCEL -> {
                isSliderDragging = false
                isDragging = false
                return true
            }
        }

        return false
    }

    private fun changeTargetCount(delta: Int) {
        val oldValue = targetCount

        targetCount = (targetCount + delta)
            .coerceIn(MIN_SHOTS, MAX_SHOTS)

        if (targetCount != oldValue) {
            saveTargetCount()
            performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY
            )
            invalidate()
        }
    }

    private fun updateCountFromSlider(x: Float) {

        val startX = dp(20f)
        val endX = width - dp(20f)

        val fraction = (
                (x - startX) /
                        (endX - startX)
                ).coerceIn(0f, 1f)

        val newCount =
            MIN_SHOTS +
                    (fraction * (MAX_SHOTS - MIN_SHOTS))
                        .roundToInt()

        if (newCount != targetCount) {
            targetCount = newCount
            saveTargetCount()
            performHapticFeedback(
                HapticFeedbackConstants.CLOCK_TICK
            )
            invalidate()
        }
    }

    private fun showManualCountDialog() {

        if (isCapturingSequence) return

        val input = EditText(context).apply {

            inputType =
                InputType.TYPE_CLASS_NUMBER

            setSingleLine(true)

            setText(targetCount.toString())

            selectAll()

            hint = "1 - 25"

            setPadding(
                dp(14f).toInt(),
                dp(8f).toInt(),
                dp(14f).toInt(),
                dp(8f).toInt()
            )
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Number of shots")
            .setMessage("Enter a number from 1 to 25")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val entered =
                    input.text
                        .toString()
                        .trim()
                        .toIntOrNull()

                if (entered == null) {

                    input.error =
                        "Enter a number from 1 to 25"

                    return@setOnClickListener
                }

                if (entered !in MIN_SHOTS..MAX_SHOTS) {

                    input.error =
                        "Number must be between 1 and 25"

                    return@setOnClickListener
                }

                targetCount = entered
                saveTargetCount()
                invalidate()

                dialog.dismiss()
            }
        }

        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        dialog.show()
    }

    private fun saveTargetCount() {
        prefs.edit()
            .putInt(
                ScreenshotManager.KEY_DEFAULT_SHOT_COUNT,
                targetCount
            )
            .apply()
    }

    private fun toggleCropTool() {

        isCropToolActive = !isCropToolActive

        invalidate()

        if (isCropToolActive) {
            JuganuaAccessibilityService
                .showCropOverlay(context)
        } else {
            JuganuaAccessibilityService
                .hideCropOverlay()
        }
    }

    fun expandPanel() {

        if (isPanelExpanded) return

        targetCount = prefs.getInt(
            ScreenshotManager.KEY_DEFAULT_SHOT_COUNT,
            ScreenshotManager.DEFAULT_SHOT_COUNT
        ).coerceIn(
            MIN_SHOTS,
            MAX_SHOTS
        )

        isPanelExpanded = true

        onRequestResizeListener?.invoke(true)

        invalidate()
    }

    fun collapsePanel() {

        if (!isPanelExpanded) return

        isPanelExpanded = false
        isSliderDragging = false

        onRequestResizeListener?.invoke(false)

        invalidate()
    }

    private fun clampBounds(
        params: WindowManager.LayoutParams,
        windowManager: WindowManager
    ) {

        val displayMetrics =
            context.resources.displayMetrics

        val screenWidth =
            displayMetrics.widthPixels

        val screenHeight =
            displayMetrics.heightPixels

        params.x = params.x.coerceIn(
            0,
            screenWidth - width
        )

        val minY =
            (32 * displayMetrics.density).toInt()

        val maxY =
            screenHeight -
                    height -
                    (48 * displayMetrics.density).toInt()

        params.y = params.y.coerceIn(
            minY,
            maxY
        )
    }

    fun triggerSingleManualAreaCapture() {

        val service =
            JuganuaAccessibilityService.instance
                ?: return

        viewScope.launch(Dispatchers.IO) {

            var done = false

            withContext(Dispatchers.Main) {

                service.captureScreen(

                    onSuccess = { bmp ->

                        viewScope.launch(Dispatchers.IO) {

                            ScreenshotManager
                                .saveScreenshotBitmap(
                                    context,
                                    bmp
                                )

                            done = true
                        }
                    },

                    onError = { err ->

                        done = true

                        viewScope.launch(
                            Dispatchers.Main
                        ) {
                            Toast.makeText(
                                context,
                                err,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }

    private fun startCaptureSequence() {

        val service =
            JuganuaAccessibilityService.instance

        if (service == null) {

            statusText = "No Service"
            invalidate()

            return
        }

        if (isCropToolActive) {

            isCropToolActive = false

            JuganuaAccessibilityService
                .hideCropOverlay()
        }

        isCapturingSequence = true
        currentProgress = 0
        statusText = "0/$targetCount"

        invalidate()

        val autoAdvance =
            prefs.getBoolean(
                ScreenshotManager.KEY_AUTO_ADVANCE,
                true
            )

        val tapX =
            ScreenshotManager.DEFAULT_TAP_X

        val tapY =
            ScreenshotManager.DEFAULT_TAP_Y

        val delayMs =
            prefs.getLong(
                ScreenshotManager.KEY_DELAY_MS,
                ScreenshotManager.DEFAULT_DELAY_MS
            )

        captureJob =
            viewScope.launch(Dispatchers.IO) {

                for (i in 1..targetCount) {

                    if (!isCapturingSequence)
                        break

                    withContext(Dispatchers.Main) {

                        currentProgress = i

                        invalidate()
                    }

                    var stepDone = false
                    var stepError: String? = null

                    // Capture first.
                    withContext(Dispatchers.Main) {

                        service.captureScreen(

                            onSuccess = { bmp ->

                                viewScope.launch(
                                    Dispatchers.IO
                                ) {

                                    ScreenshotManager
                                        .saveScreenshotBitmap(
                                            context,
                                            bmp
                                        )

                                    stepDone = true
                                }
                            },

                            onError = { err ->

                                stepError = err
                                stepDone = true
                            }
                        )
                    }

                    while (
                        !stepDone &&
                        isCapturingSequence
                    ) {
                        delay(50)
                    }

                    if (stepError != null) {

                        withContext(
                            Dispatchers.Main
                        ) {

                            statusText = "Error"

                            isCapturingSequence =
                                false

                            invalidate()
                        }

                        break
                    }

                    // Auto advance.
                    if (
                        autoAdvance &&
                        i < targetCount &&
                        isCapturingSequence
                    ) {

                        withContext(
                            Dispatchers.Main
                        ) {

                            service.showClickIndicator(
                                tapX,
                                tapY
                            )

                            service.dispatchTap(
                                tapX,
                                tapY
                            ) {

                                viewScope.launch(
                                    Dispatchers.Main
                                ) {

                                    delay(200)

                                    service
                                        .hideClickIndicator()
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
                            performHapticFeedback(
                                HapticFeedbackConstants.LONG_PRESS
                            )
                        } catch (_: Exception) {
                        }

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

        JuganuaAccessibilityService
            .instance
            ?.hideClickIndicator()

        statusText = "Stopped"

        invalidate()
    }

    private fun Float.roundToInt(): Int =
        kotlin.math.round(this).toInt()
}

