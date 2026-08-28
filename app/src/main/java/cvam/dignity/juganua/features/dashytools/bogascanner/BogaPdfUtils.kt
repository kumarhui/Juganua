package cvam.dignity.juganua.features.dashytools.bogascanner

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/**
 * Utility functions for rendering scanned images onto A4 canvas, saving to MediaStore,
 * fetching gallery items, sharing, and printing.
 */
object BogaPdfUtils {
    private const val TAG = "BogaPdfUtils"

    // A4 Portrait dimensions at 300 DPI
    private const val A4_WIDTH = 2480
    private const val A4_HEIGHT = 3508

    // Fixed ID card slot dimensions (Approx 85.6mm x 53.98mm scaled to 300 DPI)
    private const val CARD_WIDTH = 1100f
    private const val CARD_HEIGHT = 692f

    data class GalleryItem(val uri: Uri, val isIdCard: Boolean)

    suspend fun generateSinglePageImage(
        context: Context,
        frontUri: Uri?,
        backUri: Uri?,
        isVerticalLayout: Boolean
    ): Bitmap? {
        if (frontUri == null) return null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                val frontImage = loadBitmapFromUri(context, frontUri)
                val backImage = backUri?.let { loadBitmapFromUri(context, it) }

                if (frontImage != null) {
                    drawLayoutOnCanvas(canvas, frontImage, backImage, isVerticalLayout)
                    frontImage.recycle()
                }
                backImage?.recycle()

                return@withContext bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error generating A4 Image", e)
                null
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading image URI: $uri", e)
            null
        } finally {
            inputStream?.close()
        }
    }

    private fun drawLayoutOnCanvas(
        canvas: Canvas,
        frontBitmap: Bitmap,
        backBitmap: Bitmap?,
        isVerticalLayout: Boolean
    ) {
        val cx = A4_WIDTH / 2f
        val cy = A4_HEIGHT / 2f

        if (backBitmap == null) {
            drawCardCentered(canvas, frontBitmap, cx, cy)
        } else {
            if (isVerticalLayout) {
                val spacing = 200f
                val totalHeight = (CARD_HEIGHT * 2) + spacing
                val startY = cy - (totalHeight / 2f) + (CARD_HEIGHT / 2f)

                drawCardCentered(canvas, frontBitmap, cx, startY)
                drawCardCentered(canvas, backBitmap, cx, startY + CARD_HEIGHT + spacing)
            } else {
                val spacing = 150f
                val totalWidth = (CARD_WIDTH * 2) + spacing
                val startX = cx - (totalWidth / 2f) + (CARD_WIDTH / 2f)

                drawCardCentered(canvas, frontBitmap, startX, cy)
                drawCardCentered(canvas, backBitmap, startX + CARD_WIDTH + spacing, cy)
            }
        }
    }

    private fun drawCardCentered(canvas: Canvas, bitmap: Bitmap, centerX: Float, centerY: Float) {
        val left = centerX - (CARD_WIDTH / 2f)
        val top = centerY - (CARD_HEIGHT / 2f)
        val right = centerX + (CARD_WIDTH / 2f)
        val bottom = centerY + (CARD_HEIGHT / 2f)

        val bounds = RectF(left, top, right, bottom)
        val widthRatio = CARD_WIDTH / bitmap.width.toFloat()
        val heightRatio = CARD_HEIGHT / bitmap.height.toFloat()
        val scale = minOf(widthRatio, heightRatio)

        val scaledWidth = bitmap.width * scale
        val scaledHeight = bitmap.height * scale
        val drawLeft = bounds.left + (CARD_WIDTH - scaledWidth) / 2f
        val drawTop = bounds.top + (CARD_HEIGHT - scaledHeight) / 2f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(drawLeft, drawTop)
        }

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    suspend fun generateNormalA4Images(context: Context, uris: List<Uri>): List<Bitmap> {
        return withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                try {
                    val image = loadBitmapFromUri(context, uri) ?: return@mapNotNull null
                    val bitmap = Bitmap.createBitmap(A4_WIDTH, A4_HEIGHT, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    val padding = 150f
                    val availableWidth = A4_WIDTH - (padding * 2)
                    val availableHeight = A4_HEIGHT - (padding * 2)

                    val widthRatio = availableWidth / image.width.toFloat()
                    val heightRatio = availableHeight / image.height.toFloat()
                    val scale = minOf(widthRatio, heightRatio)

                    val scaledWidth = image.width * scale
                    val scaledHeight = image.height * scale

                    val drawLeft = padding + (availableWidth - scaledWidth) / 2f
                    val drawTop = padding + (availableHeight - scaledHeight) / 2f

                    val matrix = Matrix().apply {
                        postScale(scale, scale)
                        postTranslate(drawLeft, drawTop)
                    }

                    val paint = Paint().apply {
                        isAntiAlias = true
                        isFilterBitmap = true
                    }
                    canvas.drawBitmap(image, matrix, paint)
                    image.recycle()
                    bitmap
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating normal A4 image", e)
                    null
                }
            }
        }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, prefix: String = "IDCARD_", isForShare: Boolean = false): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val finalPrefix = if (isForShare) "Share_" else prefix
        val fileName = "$finalPrefix$timeStamp.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            val path = Environment.DIRECTORY_PICTURES + "/Document Scanner"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                return uri
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write Image", e)
                resolver.delete(uri, null, null)
                return null
            }
        }
        return null
    }

    fun getSavedImages(context: Context): List<GalleryItem> {
        val items = mutableListOf<GalleryItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    if (path != null && path.contains("Document Scanner")) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: ""
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val isIdCard = name.startsWith("IDCARD_") || (!name.startsWith("NORMAL_") && name.startsWith("Scan_"))
                        items.add(GalleryItem(contentUri, isIdCard))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching gallery images", e)
        }

        return items
    }

    fun shareImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            val chooser = Intent.createChooser(intent, "Share Document(s)")
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "No app to share image", e)
        }
    }

    fun printImages(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND) else Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.setPackage("com.nokoprint")
        intent.type = "image/*"

        if (uris.size == 1) {
            intent.putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "NokoPrint not installed, falling back.")
            if (uris.size == 1) {
                try {
                    val printHelper = androidx.print.PrintHelper(context)
                    printHelper.scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT

                    @Suppress("DEPRECATION")
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uris.first()))
                    } else {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uris.first())
                    }
                    printHelper.printBitmap("Document Scanner Print", bitmap)
                } catch (printEx: Exception) {
                    Toast.makeText(context, "Printing failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                intent.setPackage(null)
                context.startActivity(Intent.createChooser(intent, "Print using..."))
            }
        }
    }

    fun deleteImages(context: Context, uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete image: $uri", e)
            }
        }
    }
}
