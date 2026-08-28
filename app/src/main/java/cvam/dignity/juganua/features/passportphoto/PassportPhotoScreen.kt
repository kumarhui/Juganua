package cvam.dignity.juganua.features.passportphoto

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportPhotoScreen(
    initialUris: List<Uri>? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageSlots = remember { mutableStateMapOf<Int, Bitmap>() }
    val sourceUris = remember { mutableStateMapOf<Int, Uri>() }

    var selectedSlotIndex by remember { mutableIntStateOf(-1) }
    var gridBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    var paperSize by remember { mutableStateOf(PhotoPaperSize.A4) }
    var photosPerRow by remember { mutableIntStateOf(6) }
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
            initialUris.take(6).forEachIndexed { index, uri ->
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

    LaunchedEffect(pageSlots.toMap(), photosPerRow, hasBorder, paperSize) {
        if (pageSlots.isEmpty()) { gridBitmap = null; return@LaunchedEffect }
        isGenerating = true
        delay(400)
        gridBitmap = PassportPhotoLogic.createMultiPhotoGrid(
            pageSlots.toMap(),
            PhotoGridConfig(photosPerRow, hasBorder, AndroidColor.BLACK, paperSize)
        )
        isGenerating = false
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {

            TopAppBar(

                title = {
                    Text(
                        text = "Passport Photo"
                    )
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,

                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Text info at the top to describe the tool context
            Column {
                Text("Digital Grid Studio", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("Pack multiple photos into one sheet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            // Grid Slots Card
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
                Box(Modifier.padding(20.dp)) {
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
                                    val res = PassportPhotoLogic.saveBitmapToDownloads(context, bmp, "Passport_Individual_${idx}_${System.currentTimeMillis()}.png")
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
                                    val center = Offset(draggedRect.left + draggedRect.width/2 + dragOffset.x, draggedRect.top + draggedRect.height/2 + dragOffset.y)
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

            // Density Slider (1-6)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Print Settings", fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Photos Per Row: ", fontSize = 14.sp)
                            Text(photosPerRow.toString(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = photosPerRow.toFloat(),
                            onValueChange = { photosPerRow = it.toInt() },
                            valueRange = 1f..6f,
                            steps = 4
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hasBorder = !hasBorder }) {
                        Checkbox(checked = hasBorder, onCheckedChange = { hasBorder = it })
                        Text("Include Cut Borders", fontSize = 14.sp)
                    }
                }
            }

            // Preview & Unified Action Suite
            if (pageSlots.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LAYOUT PREVIEW", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))

                        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            if (isGenerating) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(44.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("Updating Sheet...", fontSize = 12.sp, color = Color.Gray)
                                }
                            } else {
                                gridBitmap?.let { bmp ->
                                    Image(bitmap = bmp.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Unified Action Row
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // SAVE Button
                            Button(
                                onClick = {
                                    gridBitmap?.let { bmp ->
                                        scope.launch {
                                            val uri = PassportPhotoLogic.saveBitmapToDownloads(context, bmp, "Passport_Sheet_${System.currentTimeMillis()}.png")
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

                            // SHARE Button
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

                            // PRINT Button (NokoPrint Integration)
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
}

