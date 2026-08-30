
package cvam.dignity.juganua.features.screenshottaker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cvam.dignity.juganua.features.settings.JuganuaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

data class ScreenshotItem(
    val file: File,
    val uri: Uri,
    val name: String,
    val isSelected: Boolean = true
)

object ScreenshotManager {

    private const val PREFS_NAME =
        "screenshot_taker_prefs"

    const val KEY_AUTO_CROP =
        "auto_crop_enabled"

    const val KEY_CROP_TOP_PCT =
        "crop_top_pct"

    const val KEY_CROP_BOTTOM_PCT =
        "crop_bottom_pct"

    const val KEY_AUTO_ADVANCE =
        "auto_advance_enabled"

    const val KEY_TAP_X_PCT =
        "tap_x_pct"

    const val KEY_TAP_Y_PCT =
        "tap_y_pct"

    const val KEY_DELAY_MS =
        "delay_ms"

    const val KEY_DEFAULT_SHOT_COUNT =
        "default_shot_count"

    // ---------------------------------------------------------
    // Screenshot sequence limits
    // ---------------------------------------------------------

    const val MIN_SHOT_COUNT = 1
    const val MAX_SHOT_COUNT = 25

    // New default.
    const val DEFAULT_SHOT_COUNT = 25

    const val DEFAULT_TAP_X = 1125f
    const val DEFAULT_TAP_Y = 2527f

    const val DEFAULT_DELAY_MS = 300L

    const val DEFAULT_CROP_TOP = 500f
    const val DEFAULT_CROP_BOTTOM = 2050f

    const val DEFAULT_DPI = 300
    const val DEFAULT_PADDING = 8
    const val DEFAULT_BORDER_SIZE = 2
    const val DEFAULT_BORDER_COLOR = "black"

    const val DEFAULT_THUMB_WIDTH = 100
    const val DEFAULT_THUMB_HEIGHT = 150

    private fun getStorageDir(
        context: Context
    ): File {

        val dir = File(
            context.getExternalFilesDir(null),
            "ScreenshotTaker"
        )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun getActiveCropBounds(
        context: Context
    ): RectF {

        val activeBounds =
            JuganuaAccessibilityService
                .getNormalizedCropBounds()

        if (
            activeBounds != null &&
            activeBounds.width() > 0f &&
            activeBounds.height() > 0f
        ) {
            return activeBounds
        }

        return RectF(
            0f,
            500f / 2400f,
            1f,
            2050f / 2400f
        )
    }

    fun cropBitmap(
        rawBmp: Bitmap,
        normBounds: RectF?
    ): Bitmap {

        if (
            normBounds == null ||
            normBounds.width() <= 0f ||
            normBounds.height() <= 0f
        ) {
            return rawBmp
        }

        val cropL =
            (normBounds.left * rawBmp.width)
                .toInt()
                .coerceIn(
                    0,
                    rawBmp.width - 1
                )

        val cropT =
            (normBounds.top * rawBmp.height)
                .toInt()
                .coerceIn(
                    0,
                    rawBmp.height - 1
                )

        val cropR =
            (normBounds.right * rawBmp.width)
                .toInt()
                .coerceIn(
                    cropL + 1,
                    rawBmp.width
                )

        val cropB =
            (normBounds.bottom * rawBmp.height)
                .toInt()
                .coerceIn(
                    cropT + 1,
                    rawBmp.height
                )

        val cropped =
            Bitmap.createBitmap(
                rawBmp,
                cropL,
                cropT,
                cropR - cropL,
                cropB - cropT
            )

        if (cropped != rawBmp) {
            rawBmp.recycle()
        }

        return cropped
    }

    fun getSavedScreenshots(
        context: Context
    ): List<ScreenshotItem> {

        val dir =
            getStorageDir(context)

        val files =
            dir.listFiles {
                    f ->
                f.extension.lowercase() in
                        listOf(
                            "png",
                            "jpg",
                            "jpeg",
                            "webp"
                        )
            } ?: emptyArray()

        files.sortBy {
            it.name
        }

        return files.map { f ->

            ScreenshotItem(
                file = f,
                uri = Uri.fromFile(f),
                name = f.name,
                isSelected = true
            )
        }
    }

    suspend fun saveScreenshotBitmap(
        context: Context,
        bitmap: Bitmap
    ): ScreenshotItem =
        withContext(Dispatchers.IO) {

            val dir =
                getStorageDir(context)

            val count =
                (dir.listFiles()?.size ?: 0) + 1

            var fileName =
                String.format(
                    Locale.getDefault(),
                    "%03d.png",
                    count
                )

            var file =
                File(
                    dir,
                    fileName
                )

            var idx = count

            while (file.exists()) {

                idx++

                fileName =
                    String.format(
                        Locale.getDefault(),
                        "%03d.png",
                        idx
                    )

                file =
                    File(
                        dir,
                        fileName
                    )
            }

            FileOutputStream(file).use {
                    out ->

                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    out
                )
            }

            ScreenshotItem(
                file = file,
                uri = Uri.fromFile(file),
                name = file.name,
                isSelected = true
            )
        }

    suspend fun importImageUri(
        context: Context,
        uri: Uri
    ): ScreenshotItem? =
        withContext(Dispatchers.IO) {

            try {

                val inputStream =
                    context.contentResolver
                        .openInputStream(uri)
                        ?: return@withContext null

                val bitmap =
                    BitmapFactory
                        .decodeStream(inputStream)

                inputStream.close()

                if (bitmap == null) {
                    return@withContext null
                }

                val item =
                    saveScreenshotBitmap(
                        context,
                        bitmap
                    )

                bitmap.recycle()

                item

            } catch (e: Exception) {

                e.printStackTrace()

                null
            }
        }

    fun deleteScreenshots(
        items: List<ScreenshotItem>
    ) {

        items.forEach { item ->

            if (item.file.exists()) {
                item.file.delete()
            }
        }
    }

    suspend fun generateTempPdf(
        context: Context,
        items: List<ScreenshotItem>,
        isLandscape: Boolean,
        cols: Int,
        rows: Int
    ): File? =
        withContext(Dispatchers.IO) {

            if (items.isEmpty()) {
                return@withContext null
            }

            val pdfDocument =
                PdfDocument()

            val a4Width =
                if (isLandscape) 842 else 595

            val a4Height =
                if (isLandscape) 595 else 842

            val itemsPerPage =
                cols * rows

            val totalPages =
                (
                        items.size +
                                itemsPerPage -
                                1
                        ) / itemsPerPage

            val margin =
                DEFAULT_PADDING.toFloat()

            val cellW =
                (
                        a4Width -
                                margin * 2
                        ) / cols

            val cellH =
                (
                        a4Height -
                                margin * 2
                        ) / rows

            val paint =
                Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }

            val borderPaint =
                Paint().apply {
                    style =
                        Paint.Style.STROKE

                    strokeWidth =
                        DEFAULT_BORDER_SIZE.toFloat()

                    color =
                        Color.BLACK
                }

            val activeNormBounds =
                getActiveCropBounds(
                    context
                )

            for (p in 0 until totalPages) {

                val pageInfo =
                    PdfDocument.PageInfo
                        .Builder(
                            a4Width,
                            a4Height,
                            p + 1
                        )
                        .create()

                val page =
                    pdfDocument
                        .startPage(pageInfo)

                val canvas: Canvas =
                    page.canvas

                val startIdx =
                    p * itemsPerPage

                val endIdx =
                    minOf(
                        startIdx +
                                itemsPerPage,
                        items.size
                    )

                for (i in startIdx until endIdx) {

                    val item =
                        items[i]

                    val localIdx =
                        i - startIdx

                    val r =
                        localIdx / cols

                    val c =
                        localIdx % cols

                    val cellLeft =
                        margin +
                                c * cellW

                    val cellTop =
                        margin +
                                r * cellH

                    val rawBmp =
                        BitmapFactory
                            .decodeFile(
                                item.file
                                    .absolutePath
                            )
                            ?: continue

                    val bmp =
                        cropBitmap(
                            rawBmp,
                            activeNormBounds
                        )

                    val availW =
                        cellW -
                                DEFAULT_PADDING * 2f

                    val availH =
                        cellH -
                                DEFAULT_PADDING * 2f

                    val scale =
                        min(
                            availW /
                                    bmp.width.toFloat(),
                            availH /
                                    bmp.height.toFloat()
                        )

                    val drawW =
                        bmp.width * scale

                    val drawH =
                        bmp.height * scale

                    val drawLeft =
                        cellLeft +
                                (
                                        cellW -
                                                drawW
                                        ) / 2f

                    val drawTop =
                        cellTop +
                                (
                                        cellH -
                                                drawH
                                        ) / 2f

                    val matrix =
                        Matrix().apply {

                            postScale(
                                scale,
                                scale
                            )

                            postTranslate(
                                drawLeft,
                                drawTop
                            )
                        }

                    canvas.drawBitmap(
                        bmp,
                        matrix,
                        paint
                    )

                    canvas.drawRect(
                        RectF(
                            drawLeft,
                            drawTop,
                            drawLeft + drawW,
                            drawTop + drawH
                        ),
                        borderPaint
                    )

                    bmp.recycle()
                }

                pdfDocument
                    .finishPage(page)
            }

            val tempFile =
                File(
                    context.cacheDir,
                    "preview_temp.pdf"
                )

            FileOutputStream(tempFile)
                .use { out ->

                    pdfDocument.writeTo(out)
                }

            pdfDocument.close()

            tempFile
        }

    suspend fun exportTempPdfToDownloads(
        context: Context,
        tempFile: File,
        isLandscape: Boolean
    ): Uri? =
        withContext(Dispatchers.IO) {

            if (!tempFile.exists()) {
                return@withContext null
            }

            val timeStamp =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
                ).format(Date())

            val fileName =
                "Stitched_${
                    if (isLandscape)
                        "Landscape"
                    else
                        "Portrait"
                }_$timeStamp.pdf"

            val pdfUri =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    val values =
                        ContentValues().apply {

                            put(
                                MediaStore.MediaColumns.DISPLAY_NAME,
                                fileName
                            )

                            put(
                                MediaStore.MediaColumns.MIME_TYPE,
                                "application/pdf"
                            )

                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS +
                                        "/DashyHub"
                            )
                        }

                    context.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        values
                    )

                } else {

                    val downloadsDir =
                        Environment
                            .getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS
                            )

                    val dashyDir =
                        File(
                            downloadsDir,
                            "DashyHub"
                        ).apply {
                            if (!exists())
                                mkdirs()
                        }

                    val file =
                        File(
                            dashyDir,
                            fileName
                        )

                    Uri.fromFile(file)
                }

            if (pdfUri != null) {

                try {

                    context.contentResolver
                        .openOutputStream(pdfUri)
                        ?.use { out ->

                            tempFile
                                .inputStream()
                                .copyTo(out)
                        }

                } catch (e: Exception) {

                    e.printStackTrace()
                }
            }

            pdfUri
        }

    suspend fun performOcrOnItems(
        context: Context,
        items: List<ScreenshotItem>,
        onProgress: (current: Int, total: Int) -> Unit
    ): String =
        withContext(Dispatchers.IO) {

            if (items.isEmpty()) {
                return@withContext "No screenshots selected for OCR."
            }

            val recognizer =
                TextRecognition
                    .getClient(
                        TextRecognizerOptions
                            .DEFAULT_OPTIONS
                    )

            val sb =
                StringBuilder()

            for (idx in items.indices) {

                val item =
                    items[idx]

                onProgress(
                    idx + 1,
                    items.size
                )

                try {

                    val inputImage =
                        InputImage.fromFilePath(
                            context,
                            item.uri
                        )

                    val result =
                        suspendCoroutine<String> {
                                continuation ->

                            recognizer
                                .process(inputImage)
                                .addOnSuccessListener {
                                        visionText ->

                                    continuation
                                        .resume(
                                            visionText.text
                                        )
                                }
                                .addOnFailureListener {
                                        e ->

                                    continuation
                                        .resume(
                                            "[OCR Error: ${e.localizedMessage}]"
                                        )
                                }
                        }

                    sb.append(
                        "--- Screenshot ${
                            idx + 1
                        } (${item.name}) ---\n"
                    )

                    sb.append(
                        result.ifBlank {
                            "[No text detected]"
                        }
                    )

                    sb.append("\n\n")

                } catch (e: Exception) {

                    sb.append(
                        "--- Screenshot ${
                            idx + 1
                        } (${item.name}) ---\n"
                    )

                    sb.append(
                        "[Failed to process image: ${
                            e.localizedMessage
                        }]\n\n"
                    )
                }
            }

            recognizer.close()

            sb.toString().trim()
        }
}
