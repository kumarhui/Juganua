package cvam.dignity.juganua.features.screenshottaker

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cvam.dignity.juganua.features.settings.JuganuaAccessibilityService
import cvam.dignity.juganua.features.common.SharedPermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val BrandPurple = Color(0xFF8E24AA)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)
private val StatusActiveBg = Color(0xFFD1FAE5)
private val StatusActiveText = Color(0xFF047857)

/**
 * Main Jetpack Compose Screen for Testbook Shot Taker.
 * Displays accessibility status, delegates gallery rendering to ScreenshotGallery,
 * and handles PDF export and OCR tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotTakerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityEnabled by remember {
        mutableStateOf(SharedPermissionManager.isScreenshotAccessibilityEnabled(context))
    }
    var isFloatingShowing by remember {
        mutableStateOf(JuganuaAccessibilityService.isScreenshotControlVisible())
    }

    var galleryItems by remember { mutableStateOf<List<ScreenshotItem>>(emptyList()) }

    var showPdfConfigDialog by remember { mutableStateOf(false) }
    var showPdfPreviewDialog by remember { mutableStateOf(false) }
    var isPdfGenerating by remember { mutableStateOf(false) }
    var previewPdfFile by remember { mutableStateOf<File?>(null) }
    var previewPdfLandscape by remember { mutableStateOf(false) }

    var showOcrDialog by remember { mutableStateOf(false) }
    var isOcrRunning by remember { mutableStateOf(false) }
    var ocrResultText by remember { mutableStateOf("") }
    var ocrProgressText by remember { mutableStateOf("") }

    fun refreshGallery() {
        galleryItems = ScreenshotManager.getSavedScreenshots(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = SharedPermissionManager.isScreenshotAccessibilityEnabled(context)
                isFloatingShowing = JuganuaAccessibilityService.isScreenshotControlVisible()
                refreshGallery()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { refreshGallery() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val item = ScreenshotManager.importImageUri(context, it)
                if (item != null) {
                    refreshGallery()
                    Toast.makeText(context, "Image imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to import image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusBanner(
                isAccessibilityEnabled = isAccessibilityEnabled,
                isFloatingShowing = isFloatingShowing,
                onToggleFloating = {
                    if (isFloatingShowing) {
                        JuganuaAccessibilityService.hideScreenshotControl()
                        isFloatingShowing = false
                    } else {
                        if (SharedPermissionManager.hasOverlayPermission(context)) {
                            val shown = JuganuaAccessibilityService.showScreenshotControl(context)
                            if (shown) {
                                isFloatingShowing = true
                                Toast.makeText(context, "Floating control enabled over other apps", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please enable Accessibility Service in Settings", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            SharedPermissionManager.openOverlaySettings(context)
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gallery (${galleryItems.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { importLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Add, contentDescription = "Import", tint = BrandPurple)
                    }
                    IconButton(
                        onClick = {
                            if (galleryItems.isEmpty()) {
                                Toast.makeText(context, "No screenshots available for PDF", Toast.LENGTH_SHORT).show()
                            } else {
                                showPdfConfigDialog = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color(0xFFE53935))
                    }
                    IconButton(
                        onClick = {
                            if (galleryItems.isEmpty()) {
                                Toast.makeText(context, "No screenshots available for OCR", Toast.LENGTH_SHORT).show()
                            } else {
                                isOcrRunning = true
                                showOcrDialog = true
                                scope.launch {
                                    ocrResultText = ScreenshotManager.performOcrOnItems(context, galleryItems) { cur, tot ->
                                        ocrProgressText = "Processing $cur / $tot..."
                                    }
                                    isOcrRunning = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Description, contentDescription = "OCR", tint = Color(0xFF1E88E5))
                    }
                }
            }

            // Delegated to ScreenshotGallery.kt
            ScreenshotGallery(
                galleryItems = galleryItems,
                onDeleteSelected = { itemsToDelete ->
                    ScreenshotManager.deleteScreenshots(itemsToDelete)
                    refreshGallery()
                    Toast.makeText(context, "Deleted ${itemsToDelete.size} items", Toast.LENGTH_SHORT).show()
                },
                onToggleSelectAll = { selectAll ->
                    // Selection managed within ScreenshotGallery
                }
            )
        }
    }

    if (showPdfConfigDialog) {
        PdfGenerateDialog(
            selectedCount = galleryItems.size,
            isGenerating = isPdfGenerating,
            onDismiss = { showPdfConfigDialog = false },
            onGenerate = { isLandscape, cols, rows ->
                isPdfGenerating = true

                scope.launch {
                    val tempFile = ScreenshotManager.generateTempPdf(
                        context,
                        galleryItems,
                        isLandscape,
                        cols,
                        rows
                    )

                    isPdfGenerating = false

                    if (tempFile != null && tempFile.exists()) {
                        previewPdfFile = tempFile
                        previewPdfLandscape = isLandscape
                        showPdfConfigDialog = false
                        showPdfPreviewDialog = true
                    } else {
                        Toast.makeText(
                            context,
                            "Failed to compile PDF preview",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    if (showPdfPreviewDialog && previewPdfFile != null) {
        PdfPreviewModalDialog(
            pdfFile = previewPdfFile!!,
            onDismiss = { showPdfPreviewDialog = false },
            onOpenInOtherApp = {
                try {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        "cvam.dignity.juganua.provider",
                        previewPdfFile!!
                    )

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newRawUri("PDF", contentUri)
                    }

                    context.startActivity(
                        Intent.createChooser(intent, "Open PDF in...")
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Couldn't share PDF",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onSavePdf = {
                scope.launch {
                    val savedUri = ScreenshotManager.exportTempPdfToDownloads(context, previewPdfFile!!, previewPdfLandscape)
                    if (savedUri != null) {
                        Toast.makeText(context, "PDF saved to Downloads!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showOcrDialog) {
        AlertDialog(
            onDismissRequest = { if (!isOcrRunning) showOcrDialog = false },
            title = { Text("Extracted OCR Text", fontWeight = FontWeight.Bold) },
            text = {
                if (isOcrRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(ocrProgressText, fontSize = 13.sp, color = BrandPurple)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = ocrResultText.ifBlank { "No text recognized." },
                            fontSize = 12.sp,
                            color = TextDark,
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (!isOcrRunning && ocrResultText.isNotBlank()) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("OCR Text", ocrResultText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("Copy Text") }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOcrDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun StatusBanner(
    isAccessibilityEnabled: Boolean,
    isFloatingShowing: Boolean,
    onToggleFloating: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isAccessibilityEnabled) Color(0xFFA7F3D0) else BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Accessibility Service Status", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(
                        text = if (isAccessibilityEnabled) "Service active & ready" else "Enable in Dashboard → Settings",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                if (isAccessibilityEnabled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusActiveBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Ready ✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusActiveText)
                    }
                }
            }

            if (isAccessibilityEnabled) {
                Button(
                    onClick = onToggleFloating,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFloatingShowing) Color(0xFF0F172A) else BrandPurple
                    )
                ) {
                    Text(if (isFloatingShowing) "Hide Floating Toolbar" else "Show Floating Toolbar", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PdfGenerateDialog(
    selectedCount: Int,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (isLandscape: Boolean, cols: Int, rows: Int) -> Unit
) {
    var isLandscape by remember { mutableStateOf(false) }
    var selectedGridOption by remember { mutableStateOf(6) }

    AlertDialog(
        onDismissRequest = {
            if (!isGenerating) onDismiss()
        },
        title = {
            Text(
                "Generate A4 PDF ($selectedCount images)",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Select Page Orientation",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                SegmentedChoiceRow(
                    options = listOf("Portrait", "Landscape"),
                    selectedIndex = if (isLandscape) 1 else 0,
                    enabled = !isGenerating,
                    onSelected = { isLandscape = it == 1 }
                )

                Text(
                    "Grid Layout",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                SegmentedChoiceRow(
                    options = listOf("6 per page", "9 per page"),
                    selectedIndex = if (selectedGridOption == 6) 0 else 1,
                    enabled = !isGenerating,
                    onSelected = {
                        selectedGridOption = if (it == 0) 6 else 9
                    }
                )

                if (isGenerating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Preparing PDF preview…",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandPurple
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isGenerating,
                onClick = {
                    val cols: Int
                    val rows: Int

                    if (selectedGridOption == 6) {
                        if (isLandscape) {
                            cols = 3
                            rows = 2
                        } else {
                            cols = 2
                            rows = 3
                        }
                    } else {
                        cols = 3
                        rows = 3
                    }

                    onGenerate(isLandscape, cols, rows)
                }
            ) {
                Text("Preview PDF")
            }
        },
        dismissButton = {
            OutlinedButton(
                enabled = !isGenerating,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SegmentedChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex

            OutlinedButton(
                onClick = { onSelected(index) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = when (index) {
                    0 -> RoundedCornerShape(
                        topStart = 10.dp,
                        bottomStart = 10.dp,
                        topEnd = 2.dp,
                        bottomEnd = 2.dp
                    )
                    options.lastIndex -> RoundedCornerShape(
                        topStart = 2.dp,
                        bottomStart = 2.dp,
                        topEnd = 10.dp,
                        bottomEnd = 10.dp
                    )
                    else -> RoundedCornerShape(2.dp)
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor =
                        if (selected) BrandPurple.copy(alpha = 0.14f)
                        else Color.Transparent,
                    contentColor =
                        if (selected) BrandPurple
                        else TextDark
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) BrandPurple else BorderColor
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp
                )
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight =
                        if (selected) FontWeight.Bold
                        else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PdfPreviewModalDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onOpenInOtherApp: () -> Unit,
    onSavePdf: () -> Unit
) {
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pages = mutableListOf<Bitmap>()

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pages.add(bitmap)
                }

                renderer.close()
                pfd.close()
                pdfPages = pages
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF Preview", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                if (pdfPages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(pdfPages) { pageIdx, bmp ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Page ${pageIdx + 1}", fontSize = 10.sp, color = TextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "PDF Page ${pageIdx + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onOpenInOtherApp) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Open PDF in another app",
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = onSavePdf) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save PDF",
                        modifier = Modifier.size(22.dp),
                        tint = BrandPurple
                    )
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}