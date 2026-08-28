package cvam.dignity.juganua.features.idcardsplitter

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object IdStudioLogic {

    fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix()
        if (horizontal) matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        else matrix.postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Optimized Bitmap loading with downscaling for faster processing and lower memory footprint.
     */
    suspend fun loadBitmapInternal(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            // Calculate sample size to keep image around 2000px max (sharp for ID but fast)
            val reqSize = 2000
            var inSampleSize = 1
            if (options.outHeight > reqSize || options.outWidth > reqSize) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqSize && halfWidth / inSampleSize >= reqSize) {
                    inSampleSize *= 2
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) { null }
    }

    fun renderPdfFirstPageInternal(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            // Render at approx 200 DPI for speed
                            val targetWidth = 1800
                            val scale = targetWidth.toFloat() / page.width
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    } else null
                }
            }
        } catch (e: Exception) { null }
    }

    /**
     * Now strictly offloaded to IO dispatcher to prevent "Davey" hangs during compression.
     */
    suspend fun saveBitmapToTempInternal(context: Context, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "temp_id_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        Uri.fromFile(file)
    }

    suspend fun createMultiPrintLayout(
        context: Context,
        slots: Map<PrintPosition, SlotData>,
        size: PaperSize,
        stacked: Boolean
    ): Bitmap = withContext(Dispatchers.Default) {
        val dpi = 300
        val mmToPx = dpi / 25.4f
        val paperW = ((if (size == PaperSize.A4) 210 else 105) * mmToPx).toInt()
        val paperH = ((if (size == PaperSize.A4) 297 else 148) * mmToPx).toInt()

        val idW = (85.6f * mmToPx).toInt()
        val idH = (53.98f * mmToPx).toInt()
        val gap = (5 * mmToPx).toInt()

        val page = Bitmap.createBitmap(paperW, paperH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        slots.forEach { (pos, data) ->
            val sFront = Bitmap.createScaledBitmap(data.front, idW, idH, true)
            val sBack = Bitmap.createScaledBitmap(data.back, idW, idH, true)

            val totalW = if (stacked) idW else (idW * 2 + gap)
            val totalH = if (stacked) (idH * 2 + gap) else idH

            var startX = (paperW / 2f - totalW / 2f)
            var startY = 0f

            if (size == PaperSize.A6) {
                startY = (paperH / 2f - totalH / 2f)
            } else {
                if (stacked) {
                    val margin = (15 * mmToPx).toInt()
                    when(pos) {
                        PrintPosition.POS_1 -> { startX = margin.toFloat(); startY = margin.toFloat() }
                        PrintPosition.POS_2 -> { startX = (paperW - totalW - margin).toFloat(); startY = margin.toFloat() }
                        PrintPosition.POS_3 -> { startX = (paperW/2f - totalW/2f); startY = (paperH/2f - totalH/2f) }
                        PrintPosition.POS_4 -> { startX = margin.toFloat(); startY = (paperH - totalH - margin).toFloat() }
                        PrintPosition.POS_5 -> { startX = (paperW - totalW - margin).toFloat(); startY = (paperH - totalH - margin).toFloat() }
                        else -> {}
                    }
                } else {
                    val cellH = paperH / 6f
                    startY = (cellH * pos.ordinal) + (cellH / 2f - totalH / 2f)
                }
            }

            if (stacked) {
                canvas.drawBitmap(sFront, startX, startY, null)
                canvas.drawBitmap(sBack, startX, startY + idH + gap, null)
            } else {
                canvas.drawBitmap(sFront, startX, startY, null)
                canvas.drawBitmap(sBack, startX + idW + gap, startY, null)
            }
            sFront.recycle(); sBack.recycle()
        }
        page
    }

    fun saveToDownloadsInternal(context: Context, bitmap: Bitmap, name: String) {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ID_${System.currentTimeMillis()}_$name.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudioID")
            }
        }
        try {
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            uri?.let { context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
                Toast.makeText(context, "Saved to Pictures/StudioID", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }

    fun shareImageInternal(context: Context, bitmap: Bitmap) {
        try {
            val cachePath = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(cachePath, "export_id.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, "image/png")
                putExtra(Intent.EXTRA_STREAM, contentUri)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ID"))
        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
}