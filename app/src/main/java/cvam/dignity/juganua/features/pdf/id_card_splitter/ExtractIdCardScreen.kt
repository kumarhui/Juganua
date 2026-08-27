package cvam.dignity.juganua.features.pdf.id_card_splitter

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractIdCardScreen(
    initialUri: Uri? = null,
    initialUris: List<Uri>? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State Management ---
    val pageSlots = remember { mutableStateMapOf<PrintPosition, SlotData>() }
    var selectedSlot by remember { mutableStateOf<PrintPosition?>(null) }
    var paperSize by remember { mutableStateOf(PaperSize.A4) }
    var isStacked by remember { mutableStateOf(false) }

    var flowState by remember { mutableStateOf(FlowState.PAGE_OVERVIEW) }
    var isProcessing by remember { mutableStateOf(false) }
    var isGeneratingPreview by remember { mutableStateOf(false) }

    var currentSourceUri by remember { mutableStateOf<Uri?>(null) }
    var workspaceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var splitFront by remember { mutableStateOf<Bitmap?>(null) }
    var splitBack by remember { mutableStateOf<Bitmap?>(null) }
    var finalPrintBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // --- Drag & Drop UI State ---
    var draggingPos by remember { mutableStateOf<PrintPosition?>(null) }
    var dragFingerOffset by remember { mutableStateOf(Offset.Zero) }
    val slotBounds = remember { mutableStateMapOf<PrintPosition, Rect>() }
    var currentHoverTarget by remember { mutableStateOf<PrintPosition?>(null) }

    // --- REAL-TIME AUTO REFRESH PREVIEW ---
    LaunchedEffect(pageSlots.toMap(), paperSize, isStacked) {
        if (pageSlots.isEmpty()) {
            finalPrintBitmap = null
            return@LaunchedEffect
        }
        isGeneratingPreview = true
        delay(500) // Debounce rapid slot changes
        try {
            finalPrintBitmap = IdStudioLogic.createMultiPrintLayout(context, pageSlots.toMap(), paperSize, isStacked)
        } finally {
            isGeneratingPreview = false
        }
    }

    // --- FASTER SELECTION LOGIC ---
    val handleFileSelection = { uri: Uri ->
        // Jump to next UI immediately so user knows the action triggered
        flowState = FlowState.PREVIEW_READY
        isProcessing = true
        scope.launch {
            try {
                workspaceBitmap = null
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val isPdf = mimeType.contains("pdf") || uri.toString().lowercase().endsWith(".pdf")

                // loadBitmapInternal now includes SampleSize downscaling for high performance
                val bitmap = if (isPdf) IdStudioLogic.renderPdfFirstPageInternal(context, uri)
                else IdStudioLogic.loadBitmapInternal(context, uri)

                workspaceBitmap = bitmap
                workspaceBitmap?.let {
                    currentSourceUri = IdStudioLogic.saveBitmapToTempInternal(context, it)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading file", Toast.LENGTH_SHORT).show()
                flowState = FlowState.PAGE_OVERVIEW
            } finally { isProcessing = false }
        }
    }

    LaunchedEffect(initialUri, initialUris) {
        val target = initialUris?.firstOrNull() ?: initialUri
        target?.let { selectedSlot = PrintPosition.POS_1; handleFileSelection(it) }
    }

    val selectLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { handleFileSelection(it) }
    }

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let { uri ->
                scope.launch {
                    isProcessing = true
                    workspaceBitmap = IdStudioLogic.loadBitmapInternal(context, uri)
                    flowState = FlowState.CROPPED
                    isProcessing = false
                }
            }
        }
    }

    BackHandler { if (flowState == FlowState.PAGE_OVERVIEW) onBack() else flowState = FlowState.PAGE_OVERVIEW }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = flowState, label = "Flow") { state ->
            when (state) {
                FlowState.PAGE_OVERVIEW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val hit = slotBounds.entries.find { it.value.contains(offset) }?.key
                                        if (hit != null && pageSlots.containsKey(hit)) {
                                            draggingPos = hit
                                            dragFingerOffset = offset
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragFingerOffset += amount
                                        currentHoverTarget = slotBounds.entries.find {
                                            it.key != draggingPos && it.value.contains(dragFingerOffset)
                                        }?.key
                                    },
                                    onDragEnd = {
                                        if (draggingPos != null && currentHoverTarget != null) {
                                            val fromData = pageSlots[draggingPos!!]
                                            val toData = pageSlots[currentHoverTarget!!]
                                            if (fromData != null) {
                                                if (toData != null) {
                                                    pageSlots[draggingPos!!] = toData
                                                    pageSlots[currentHoverTarget!!] = fromData
                                                } else {
                                                    pageSlots[currentHoverTarget!!] = fromData
                                                    pageSlots.remove(draggingPos!!)
                                                }
                                            }
                                        }
                                        draggingPos = null; currentHoverTarget = null
                                    },
                                    onDragCancel = { draggingPos = null; currentHoverTarget = null }
                                )
                            }
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Options Card
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Paper Size", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                            PaperSize.entries.forEachIndexed { i, s ->
                                                SegmentedButton(selected = paperSize == s, onClick = { paperSize = s }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2), label = { Text(s.name, fontSize = 10.sp) })
                                            }
                                        }
                                    }
                                    Column(Modifier.weight(1.2f)) {
                                        Text("Layout Style", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                            SegmentedButton(selected = !isStacked, onClick = { isStacked = false }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2), label = { Text("6 Horizontal", fontSize = 9.sp) })
                                            SegmentedButton(selected = isStacked, onClick = { isStacked = true }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2), label = { Text("5 Stacked", fontSize = 9.sp) })
                                        }
                                    }
                                }
                            }
                        }

                        // Grid Slots
                        IdCompositionGrid(
                            isStacked = isStacked,
                            slots = pageSlots.toMap(),
                            draggingPos = draggingPos,
                            dragOffset = dragFingerOffset,
                            slotBounds = slotBounds,
                            currentHoverTarget = currentHoverTarget,
                            onSlotClick = { pos -> selectedSlot = pos; selectLauncher.launch(arrayOf("image/*", "application/pdf")) },
                            onClearSlot = { pageSlots.remove(it) }
                        )

                        // Real-time Final Preview Card (Popup removed, now integrated here)
                        FinalPreviewCard(
                            bitmap = finalPrintBitmap,
                            isGenerating = isGeneratingPreview,
                            onSave = { finalPrintBitmap?.let { IdStudioLogic.saveToDownloadsInternal(context, it, "ID_Compose") } },
                            onShare = { finalPrintBitmap?.let { IdStudioLogic.shareImageInternal(context, it) } }
                        )

                        Spacer(Modifier.height(40.dp))
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PreviewCard(workspaceBitmap, isProcessing)
                    if (state == FlowState.PREVIEW_READY) Button(onClick = { currentSourceUri?.let { uri -> val dest = Uri.fromFile(File(context.cacheDir, "crop_${System.currentTimeMillis()}.png")); cropLauncher.launch(UCrop.of(uri, dest).withAspectRatio(3.1f, 1f).getIntent(context)) } }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), enabled = !isProcessing) { Icon(Icons.Default.Crop, null); Spacer(Modifier.width(8.dp)); Text("CROP ID AREA") }
                    if (state == FlowState.CROPPED) Button(onClick = { workspaceBitmap?.let { bmp -> splitFront = Bitmap.createBitmap(bmp, 0, 0, bmp.width / 2, bmp.height); splitBack = Bitmap.createBitmap(bmp, bmp.width / 2, 0, bmp.width / 2, bmp.height); flowState = FlowState.SLICED } }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), enabled = !isProcessing) { Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(8.dp)); Text("EXTRACT SIDES") }
                    if (state == FlowState.SLICED) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) { ResultItem(Modifier.fillMaxWidth(), "Front", splitFront); FlipControls({ splitFront = IdStudioLogic.flipBitmap(splitFront!!, true) }, { splitFront = IdStudioLogic.flipBitmap(splitFront!!, false) }) }
                            Column(Modifier.weight(1f)) { ResultItem(Modifier.fillMaxWidth(), "Back", splitBack); FlipControls({ splitBack = IdStudioLogic.flipBitmap(splitBack!!, true) }, { splitBack = IdStudioLogic.flipBitmap(splitBack!!, false) }) }
                        }
                        Button(onClick = { selectedSlot?.let { pos -> pageSlots[pos] = SlotData(splitFront!!, splitBack!!) }; flowState = FlowState.PAGE_OVERVIEW }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), shape = RoundedCornerShape(16.dp), enabled = !isProcessing) {
                            Icon(Icons.Default.Check, null); Spacer(Modifier.width(8.dp)); Text("CONFIRM SLOT")
                        }
                    }
                }
            }
        }

        // Animated Drag Ghost
        draggingPos?.let { pos ->
            pageSlots[pos]?.let { data ->
                Surface(
                    modifier = Modifier
                        .size(140.dp, 44.dp)
                        .offset { IntOffset(dragFingerOffset.x.roundToInt() - 200, dragFingerOffset.y.roundToInt() - 70) }
                        .graphicsLayer { rotationZ = -2f; scaleX = 1.1f; scaleY = 1.1f }
                        .shadow(24.dp, RoundedCornerShape(8.dp))
                        .alpha(0.85f),
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(Modifier.fillMaxSize().padding(4.dp)) {
                        Image(data.front.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                        Image(data.back.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }

        if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
    }
}