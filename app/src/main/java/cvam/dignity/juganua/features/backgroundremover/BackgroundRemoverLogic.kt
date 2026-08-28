package cvam.dignity.juganua.features.backgroundremover

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackgroundRemoverLogic {

    private fun createSegmenter(): SubjectSegmenter {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()

        return SubjectSegmentation.getClient(options)
    }

    fun loadBitmap(
        context: Context,
        uri: Uri
    ): Bitmap? {
        return try {
            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
        } catch (_: Exception) {
            null
        }
    }

    fun removeBackground(
        bitmap: Bitmap,
        onSuccess: (Bitmap) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val segmenter = createSegmenter()

        val inputImage = InputImage.fromBitmap(
            bitmap,
            0
        )

        segmenter.process(inputImage)
            .addOnSuccessListener { result ->

                val foreground = result.foregroundBitmap

                if (foreground != null) {
                    onSuccess(foreground)
                } else {
                    onFailure(
                        IllegalStateException(
                            "ML Kit did not return a foreground bitmap."
                        )
                    )
                }

                segmenter.close()
            }
            .addOnFailureListener { exception ->

                onFailure(exception)
                segmenter.close()
            }
    }

    fun createResultBitmap(
        foreground: Bitmap,
        style: BackgroundStyle
    ): Bitmap {

        val output = Bitmap.createBitmap(
            foreground.width,
            foreground.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)

        val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        if (style.endColor == null) {

            backgroundPaint.color =
                style.startColor

        } else {

            backgroundPaint.shader =
                LinearGradient(
                    0f,
                    0f,
                    output.width.toFloat(),
                    output.height.toFloat(),
                    style.startColor,
                    style.endColor,
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

    fun saveBitmap(
        context: Context,
        bitmap: Bitmap
    ): Uri? {

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

        val fileName =
            "Juganua_BG_$timestamp.jpg"

        return try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                val values = ContentValues().apply {

                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        fileName
                    )

                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                    )

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/Juganua"
                    )

                    put(
                        MediaStore.Images.Media.IS_PENDING,
                        1
                    )
                }

                val resolver =
                    context.contentResolver

                val uri =
                    resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    ) ?: return null

                try {

                    resolver.openOutputStream(uri)
                        ?.use { output ->

                            bitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                95,
                                output
                            )
                        }

                    val completedValues =
                        ContentValues().apply {
                            put(
                                MediaStore.Images.Media.IS_PENDING,
                                0
                            )
                        }

                    resolver.update(
                        uri,
                        completedValues,
                        null,
                        null
                    )

                    uri

                } catch (_: Exception) {

                    resolver.delete(
                        uri,
                        null,
                        null
                    )

                    null
                }

            } else {

                @Suppress("DEPRECATION")
                val directory =
                    android.os.Environment
                        .getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES
                        )

                val juganuaDirectory =
                    File(
                        directory,
                        "Juganua"
                    )

                if (!juganuaDirectory.exists()) {
                    juganuaDirectory.mkdirs()
                }

                val file =
                    File(
                        juganuaDirectory,
                        fileName
                    )

                FileOutputStream(file).use { output ->

                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        output
                    )
                }

                android.media.MediaScannerConnection
                    .scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("image/jpeg"),
                        null
                    )

                Uri.fromFile(file)
            }

        } catch (_: Exception) {
            null
        }
    }

    fun createShareUri(
        context: Context,
        bitmap: Bitmap
    ): Uri? {

        return try {

            val directory =
                File(
                    context.cacheDir,
                    "background_remover"
                )

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file =
                File(
                    directory,
                    "juganua_background_removed.jpg"
                )

            FileOutputStream(file).use { output ->

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    output
                )
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

        } catch (_: Exception) {
            null
        }
    }
}