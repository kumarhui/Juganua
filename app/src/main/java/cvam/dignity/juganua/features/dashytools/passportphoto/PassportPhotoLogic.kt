package cvam.dignity.juganua.features.dashytools.passportphoto

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PassportPhotoLogic {

    suspend fun loadBitmapInternal(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) { null }
    }

    suspend fun createMultiPhotoGrid(
        slots: Map<Int, Bitmap>,
        config: PhotoGridConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        val dpi = 300
        val mmToPx = dpi / 25.4f

        val paperW = ((if (config.paperSize == PhotoPaperSize.A4) 210 else 105) * mmToPx).toInt()
        val paperH = ((if (config.paperSize == PhotoPaperSize.A4) 297 else 148) * mmToPx).toInt()

        val photoW = (30f * mmToPx).toInt()
        val photoH = (40f * mmToPx).toInt()

        val gridBitmap = Bitmap.createBitmap(paperW, paperH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gridBitmap)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f * mmToPx
            color = config.borderColor
        }

        val totalSlots = if (config.paperSize == PhotoPaperSize.A4) 6 else 3
        val slotHeight = paperH / totalSlots.toFloat()

        for (slotIdx in 0 until totalSlots) {
            val bitmap = slots[slotIdx] ?: continue
            val scaled = Bitmap.createScaledBitmap(bitmap, photoW, photoH, true)
            val spacingPx = 2f * mmToPx
            val photosInRow = config.photosPerSlot
            val rowWidth = (photosInRow * photoW) + ((photosInRow - 1) * spacingPx)
            val startX = (paperW - rowWidth) / 2f
            val y = (slotIdx * slotHeight) + (slotHeight / 2f) - (photoH / 2f)

            for (i in 0 until photosInRow) {
                val x = startX + i * (photoW + spacingPx)
                canvas.drawBitmap(scaled, x, y, null)
                if (config.hasBorder) canvas.drawRect(x, y, x + photoW, y + photoH, borderPaint)
            }
        }
        gridBitmap
    }

    fun saveBitmapToDownloads(context: Context, bitmap: Bitmap, filename: String): Uri? {
        return try {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = context.contentResolver.insert(collection, cv)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            uri
        } catch (e: Exception) { null }
    }
}

