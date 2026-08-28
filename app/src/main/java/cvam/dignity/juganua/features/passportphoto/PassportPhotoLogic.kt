package cvam.dignity.juganua.features.passportphoto

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object PassportPhotoLogic {

    suspend fun loadBitmapInternal(
        context: Context,
        uri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                val source =
                    android.graphics.ImageDecoder.createSource(
                        context.contentResolver,
                        uri
                    )

                android.graphics.ImageDecoder.decodeBitmap(
                    source
                ) { decoder, _, _ ->

                    decoder.allocator =
                        android.graphics.ImageDecoder
                            .ALLOCATOR_SOFTWARE
                }

            } else {

                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(
                    context.contentResolver,
                    uri
                )
            }

        } catch (_: Exception) {
            null
        }
    }

    suspend fun removeBackground(
        bitmap: Bitmap
    ): Bitmap? = withContext(Dispatchers.Default) {

        try {

            val options =
                SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build()

            val segmenter =
                SubjectSegmentation
                    .getClient(options)

            val input =
                InputImage.fromBitmap(
                    bitmap,
                    0
                )

            val foreground =
                suspendCancellableCoroutine<Bitmap?> { continuation ->

                    segmenter
                        .process(input)
                        .addOnSuccessListener { result ->

                            continuation.resume(
                                result.foregroundBitmap
                            )
                        }
                        .addOnFailureListener {

                            continuation.resume(null)
                        }
                }

            segmenter.close()

            foreground

        } catch (_: Exception) {
            null
        }
    }

    fun createBackgroundResult(
        foreground: Bitmap,
        background: PassportBackground
    ): Bitmap {

        val output =
            Bitmap.createBitmap(
                foreground.width,
                foreground.height,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(output)

        val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        if (background.endColor == null) {

            backgroundPaint.color =
                background.startColor

        } else {

            backgroundPaint.shader =
                LinearGradient(
                    0f,
                    0f,
                    output.width.toFloat(),
                    output.height.toFloat(),
                    background.startColor,
                    background.endColor,
                    Shader.TileMode.CLAMP
                )
        }

        canvas.drawRect(
            0f,
            0f,
            output.width.toFloat(),
            output.height.toFloat(),
            backgroundPaint
        )

        backgroundPaint.shader = null

        canvas.drawBitmap(
            foreground,
            0f,
            0f,
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                        Paint.FILTER_BITMAP_FLAG
            )
        )

        return output
    }

    /**
     * Creates the A4/A6 sheet.
     *
     * One row = one selected passport image.
     * Each selected row automatically contains 6 copies.
     */
    suspend fun createMultiPhotoGrid(
        rows: Map<Int, Bitmap>,
        paperSize: PhotoPaperSize
    ): Bitmap = withContext(Dispatchers.Default) {

        val dpi = 300f
        val mmToPx = dpi / 25.4f

        val widthMm =
            when (paperSize) {
                PhotoPaperSize.A4 -> 210f
                PhotoPaperSize.A6 -> 105f
            }

        val heightMm =
            when (paperSize) {
                PhotoPaperSize.A4 -> 297f
                PhotoPaperSize.A6 -> 148f
            }

        val width =
            (widthMm * mmToPx).toInt()

        val height =
            (heightMm * mmToPx).toInt()

        val sheet =
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(sheet)

        canvas.drawColor(
            android.graphics.Color.WHITE
        )

        /*
         * Six possible row positions.
         */
        val rowCount = 6

        val rowHeight =
            height.toFloat() / rowCount

        /*
         * Passport ratio 30:40.
         *
         * Scale to fit each row.
         */
        val maxPhotoHeight =
            rowHeight * 0.86f

        val photoHeight =
            maxPhotoHeight.toInt()

        val photoWidth =
            (photoHeight * 30f / 40f)
                .toInt()

        val spacing =
            2f * mmToPx

        val copiesPerRow = 6

        val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    0.5f * mmToPx

                color =
                    android.graphics.Color.BLACK
            }

        /*
         * IMPORTANT:
         *
         * Iterate through the actual row map.
         *
         * If the user moves image from row 1 to row 3,
         * row 3 gets the bitmap and this function immediately
         * renders it there.
         */
        rows.forEach { (rowIndex, bitmap) ->

            if (
                rowIndex !in 0 until rowCount
            ) {
                return@forEach
            }

            val scaled =
                Bitmap.createScaledBitmap(
                    bitmap,
                    photoWidth,
                    photoHeight,
                    true
                )

            val totalWidth =
                (
                        copiesPerRow *
                                photoWidth
                        ) +
                        (
                                (copiesPerRow - 1) *
                                        spacing
                                )

            /*
             * A4: reserve a minimum 1 mm margin on both left and right.
             * Center the photo strip inside that safe area.
             * A6 keeps the existing centered behavior.
             */
            val sideMargin =
                if (paperSize == PhotoPaperSize.A4) {
                    1f * mmToPx
                } else {
                    0f
                }

            val safeWidth =
                width.toFloat() -
                        (2f * sideMargin)

            val startX =
                sideMargin +
                        (
                                safeWidth -
                                        totalWidth
                                ) / 2f

            val y =
                (
                        rowIndex * rowHeight
                        ) +
                        (
                                rowHeight -
                                        photoHeight
                                ) / 2f

            repeat(copiesPerRow) { copyIndex ->

                val x =
                    startX +
                            copyIndex *
                            (
                                    photoWidth +
                                            spacing
                                    )

                canvas.drawBitmap(
                    scaled,
                    x,
                    y,
                    null
                )

                canvas.drawRect(
                    x,
                    y,
                    x + photoWidth,
                    y + photoHeight,
                    borderPaint
                )
            }

            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }

        sheet
    }

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String
    ): Uri? {

        return try {

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        filename
                    )

                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/png"
                    )

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                    ) {

                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES +
                                    "/Juganua"
                        )

                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            1
                        )
                    }
                }

            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI,
                    values
                )

            if (uri != null) {

                context.contentResolver
                    .openOutputStream(uri)
                    ?.use { output ->

                        bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            output
                        )
                    }

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    val complete =
                        ContentValues().apply {

                            put(
                                MediaStore.Images.Media.IS_PENDING,
                                0
                            )
                        }

                    context.contentResolver.update(
                        uri,
                        complete,
                        null,
                        null
                    )
                }
            }

            uri

        } catch (_: Exception) {
            null
        }
    }

    fun saveBitmapToDownloads(
        context: Context,
        bitmap: Bitmap,
        filename: String
    ): Uri? {

        return try {

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        filename
                    )

                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        "image/png"
                    )

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                    ) {

                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS
                        )
                    }
                }

            val collection =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    MediaStore.Downloads
                        .EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI
                }

            val uri =
                context.contentResolver.insert(
                    collection,
                    values
                )

            uri?.let {

                context.contentResolver
                    .openOutputStream(it)
                    ?.use { output ->

                        bitmap.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            output
                        )
                    }
            }

            uri

        } catch (_: Exception) {
            null
        }
    }
}