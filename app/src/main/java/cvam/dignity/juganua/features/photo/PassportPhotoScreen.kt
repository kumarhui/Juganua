package cvam.dignity.juganua.features.photo

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class PhotoPaperSize { A4, A6 }

data class PhotoGridConfig(
    val photosPerSlot: Int = 3,
    val hasBorder: Boolean = true,
    val borderColor: Int = android.graphics.Color.BLACK,
    val paperSize: PhotoPaperSize = PhotoPaperSize.A4
)

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

    /**
     * Generates a printable grid canvas.
     * For A4: 12 total slots arranged in a 2x6 grid (2 columns x 6 rows).
     * For A6: 6 total slots arranged in a 2x3 grid.
     * Each slot renders [config.photosPerSlot] images (default 3 per slot).
     */
    suspend fun createMultiPhotoGrid(
        slots: Map<Int, Bitmap>,
        config: PhotoGridConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        val dpi = 300
        val mmToPx = dpi / 25.4f

        val paperW = ((if (config.paperSize == PhotoPaperSize.A4) 210 else 105) * mmToPx).toInt()
        val paperH = ((if (config.paperSize == PhotoPaperSize.A4) 297 else 148) * mmToPx).toInt()

        // Passport photo standard dimensions (30mm x 40mm)
        val photoW = (30f * mmToPx).toInt()
        val photoH = (40f * mmToPx).toInt()

        val gridBitmap = Bitmap.createBitmap(paperW, paperH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gridBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f * mmToPx
            color = config.borderColor
        }

        // Layout Configuration: 2 Columns x 6 Rows for A4 (12 slots)
        val columns = 2
        val rows = if (config.paperSize == PhotoPaperSize.A4) 6 else 3
        val totalSlots = columns * rows

        val slotWidth = paperW / columns.toFloat()
        val slotHeight = paperH / rows.toFloat()

        for (slotIdx in 0 until totalSlots) {
            val bitmap = slots[slotIdx] ?: continue
            val scaled = Bitmap.createScaledBitmap(bitmap, photoW, photoH, true)

            val col = slotIdx % columns
            val row = slotIdx / columns

            val slotX = col * slotWidth
            val slotY = row * slotHeight

            val spacingPx = 2f * mmToPx
            val photosInRow = config.photosPerSlot
            val rowWidth = (photosInRow * photoW) + ((photosInRow - 1) * spacingPx)

            // Center the photos horizontally and vertically within each 2x6 grid cell
            val startX = slotX + (slotWidth - rowWidth) / 2f
            val y = slotY + (slotHeight - photoH) / 2f

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
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            uri
        } catch (e: Exception) { null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportPhotoScreen(
    initialUris: List<Uri>? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 12 slot state maps for the 2x6 layout
    val pageSlots = remember { mutableStateMapOf<Int, Bitmap>() }
    val sourceUris = remember { mutableStateMapOf<Int, Uri>() }

    var selectedSlotIndex by remember { mutableIntStateOf(-1) }
    var gridBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    var paperSize by remember { mutableStateOf(PhotoPaperSize.A4) }
    var photosPerSlot by remember { mutableIntStateOf(3) } // 3 images per slot
    var hasBorder by remember { mutableStateOf(true) }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { UCrop.getOutput(it) }
            uri?.let {
                scope.launch {
                    val bmp = PassportPhotoLogic.loadBitmapInternal(context, it)
                    if (bmp != null && selectedSlotIndex != -1) {
                        pageSlots[selectedSlotIndex] = bmp
                    }
                }
            }
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            if (selectedSlotIndex != -1) {
                sourceUris[selectedSlotIndex] = it
                val dest = Uri.fromFile(File(context.cacheDir, "pass_${System.currentTimeMillis()}.png"))
                cropLauncher.launch(UCrop.of(it, dest).withAspectRatio(30f, 40f).getIntent(context))
            }
        }
    }

    LaunchedEffect(initialUris) {
        if (!initialUris.isNullOrEmpty()) {
            initialUris.take(12).forEachIndexed { index, uri ->
                scope.launch {
                    val bmp = PassportPhotoLogic.loadBitmapInternal(context, uri)
                    if (bmp != null) {
                        pageSlots[index] = bmp
                        sourceUris[index] = uri
                    }
                }
            }
        }
    }

    LaunchedEffect(pageSlots.toMap(), photosPerSlot, hasBorder, paperSize) {
        if (pageSlots.isEmpty()) { gridBitmap = null; return@LaunchedEffect }
        isGenerating = true
        delay(300)
        gridBitmap = PassportPhotoLogic.createMultiPhotoGrid(
            pageSlots.toMap(),
            PhotoGridConfig(photosPerSlot, hasBorder, android.graphics.Color.BLACK, paperSize)
        )
        isGenerating = false
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text("Digital Grid Studio", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text("12-Slot 2x6 Passport Page Layout (3 Photos Per Slot)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        // 2x6 Interactive Composition Overview Card (12 Slots Total)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(Modifier.padding(16.dp)) {
                PassportCompositionOverview(
                    paperSize = paperSize,
                    slots = pageSlots.toMap(),
                    draggingIndex = draggingIndex,
                    dragOffset = dragOffset,
                    onSlotClick = { idx ->
                        selectedSlotIndex = idx
                        val existing = sourceUris[idx]
                        if (existing != null) {
                            val dest = Uri.fromFile(File(context.cacheDir, "recrop_${System.currentTimeMillis()}.png"))
                            cropLauncher.launch(UCrop.of(existing, dest).withAspectRatio(30f, 40f).getIntent(context))
                        } else {
                            pickerLauncher.launch("image/*")
                        }
                    },
                    onClearSlot = { idx ->
                        pageSlots.remove(idx)
                        sourceUris.remove(idx)
                    },
                    onDownloadSlot = { idx ->
                        pageSlots[idx]?.let { bmp ->
                            scope.launch {
                                val res = PassportPhotoLogic.saveBitmapToDownloads(context, bmp, "Passport_Individual_${idx + 1}_${System.currentTimeMillis()}.png")
                                if (res != null) Toast.makeText(context, "Saved image to Downloads", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPositionCalculated = { idx, rect -> slotBounds[idx] = rect },
                    onDragStart = { idx -> draggingIndex = idx },
                    onDrag = { offset -> dragOffset += offset },
                    onDragEnd = {
                        if (draggingIndex != -1) {
                            val draggedRect = slotBounds[draggingIndex]
                            if (draggedRect != null) {
                                val center = Offset(draggedRect.left + draggedRect.width / 2 + dragOffset.x, draggedRect.top + draggedRect.height / 2 + dragOffset.y)
                                val target = slotBounds.entries.find { it.key != draggingIndex && it.value.contains(center) }?.key
                                if (target != null) {
                                    val b1 = pageSlots[draggingIndex]; val b2 = pageSlots[target]
                                    if (b1 != null) pageSlots[target] = b1 else pageSlots.remove(target)
                                    if (b2 != null) pageSlots[draggingIndex] = b2 else pageSlots.remove(draggingIndex)
                                    val u1 = sourceUris[draggingIndex]; val u2 = sourceUris[target]
                                    if (u1 != null) sourceUris[target] = u1 else sourceUris.remove(target)
                                    if (u2 != null) sourceUris[draggingIndex] = u2 else sourceUris.remove(draggingIndex)
                                }
                            }
                        }
                        draggingIndex = -1; dragOffset = Offset.Zero
                    }
                )
            }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Print & Grid Configuration", fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Photos Per Slot: ", fontSize = 14.sp)
                        Text(photosPerSlot.toString(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = photosPerSlot.toFloat(),
                        onValueChange = { photosPerSlot = it.toInt() },
                        valueRange = 1f..4f,
                        steps = 2
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hasBorder = !hasBorder }) {
                    Checkbox(checked = hasBorder, onCheckedChange = { hasBorder = it })
                    Text("Include Cut Borders around Photos", fontSize = 14.sp)
                }
            }
        }

        if (pageSlots.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FINAL A4 LAYOUT PREVIEW (2x6 GRID)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))

                    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        if (isGenerating) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Building 12-Slot Sheet...", fontSize = 12.sp, color = Color.Gray)
                            }
                        } else {
                            gridBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Passport Grid Canvas",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                gridBitmap?.let { bmp ->
                                    scope.launch {
                                        val uri = PassportPhotoLogic.saveBitmapToDownloads(context, bmp, "Passport_Sheet_12Slot_${System.currentTimeMillis()}.png")
                                        if (uri != null) Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.5f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isGenerating && gridBitmap != null
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SAVE", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                gridBitmap?.let { bmp ->
                                    scope.launch {
                                        val uri = PassportPhotoLogic.saveBitmapToDownloads(context, bmp, "Temp_Share.png")
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Sheet"))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isGenerating && gridBitmap != null
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                gridBitmap?.let { bmp ->
                                    scope.launch {
                                        val uri = PassportPhotoLogic.saveBitmapToDownloads(context, bmp, "Temp_Print.png")
                                        if (uri != null) {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/*")
                                                setPackage("com.nokoprint")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "NokoPrint app not found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isGenerating && gridBitmap != null
                        ) {
                            Text("PRINT", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun PassportCompositionOverview(
    paperSize: PhotoPaperSize,
    slots: Map<Int, Bitmap>,
    draggingIndex: Int,
    dragOffset: Offset,
    onSlotClick: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onDownloadSlot: (Int) -> Unit,
    onPositionCalculated: (Int, Rect) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    // Render 6 rows x 2 columns = 12 grid slots corresponding to the A4 page layout
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { rowIdx ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { colIdx ->
                    val idx = (rowIdx * 2) + colIdx
                    val isDragging = draggingIndex == idx

                    PassportSlotBox(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { onPositionCalculated(idx, it.boundsInWindow()) }
                            .zIndex(if (isDragging) 10f else 1f)
                            .offset {
                                if (isDragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                else IntOffset.Zero
                            }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart(idx) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            },
                        bitmap = slots[idx],
                        label = "Slot ${idx + 1}",
                        isDragging = isDragging,
                        onClick = { if (draggingIndex == -1) onSlotClick(idx) },
                        onClear = { if (draggingIndex == -1) onClearSlot(idx) },
                        onDownload = { onDownloadSlot(idx) }
                    )
                }
            }
        }
    }
}

@Composable
fun PassportSlotBox(
    modifier: Modifier = Modifier,
    bitmap: Bitmap?,
    label: String,
    isDragging: Boolean = false,
    onClick: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1.3f)
            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isDragging) 0.6f else 1.0f
            )

            if (!isDragging) {
                Box(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clickable { onDownload() },
                        color = Color.White.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("DL", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.White.copy(alpha = 0.85f), CircleShape)
                            .size(22.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(12.dp))
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}