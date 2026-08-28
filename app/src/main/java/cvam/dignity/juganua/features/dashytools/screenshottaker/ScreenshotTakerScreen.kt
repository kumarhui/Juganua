package cvam.dignity.juganua.features.dashytools.screenshottaker

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
import cvam.dignity.juganua.features.dashytools.settings.JuganuaAccessibilityService
import cvam.dignity.juganua.features.dashytools.common.SharedPermissionManager
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
            onDismiss = { showPdfConfigDialog = false },
            onGenerate = { isLandscape, cols, rows ->
                showPdfConfigDialog = false
                scope.launch {
                    val tempFile = ScreenshotManager.generateTempPdf(context, galleryItems, isLandscape, cols, rows)
                    if (tempFile != null && tempFile.exists()) {
                        previewPdfFile = tempFile
                        previewPdfLandscape = isLandscape
                        showPdfPreviewDialog = true
                    } else {
                        Toast.makeText(context, "Failed to compile PDF preview", Toast.LENGTH_SHORT).show()
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
                        "${context.packageName}.fileprovider",
                        previewPdfFile!!
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open PDF in..."))
                } catch (e: Exception) {
                    Toast.makeText(context, "No PDF viewer app found", Toast.LENGTH_SHORT).show()
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
                        text = if (isAccessibilityEnabled) "Service active & ready" else "Enable in Dashboard Ã¢â€ â€™ Settings",
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
                        Text("Ready Ã¢Å“â€œ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusActiveText)
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
    onDismiss: () -> Unit,
    onGenerate: (isLandscape: Boolean, cols: Int, rows: Int) -> Unit
) {
    var isLandscape by remember { mutableStateOf(false) }
    var selectedGridOption by remember { mutableStateOf("2x3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate A4 PDF ($selectedCount images)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Page Orientation:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            isLandscape = false
                            selectedGridOption = "2x3"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!isLandscape) BrandPurple.copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) { Text("Portrait") }

                    OutlinedButton(
                        onClick = {
                            isLandscape = true
                            selectedGridOption = "4x2"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isLandscape) BrandPurple.copy(alpha = 0.15f) else Color.Transparent
                        )
                    ) { Text("Landscape") }
                }

                Text("Grid Layout:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isLandscape) {
                        OutlinedButton(onClick = { selectedGridOption = "2x3" }) { Text("6/page (2x3)") }
                        OutlinedButton(onClick = { selectedGridOption = "3x3" }) { Text("9/page (3x3)") }
                    } else {
                        OutlinedButton(onClick = { selectedGridOption = "4x2" }) { Text("8/page (4x2)") }
                        OutlinedButton(onClick = { selectedGridOption = "4x3" }) { Text("12/page (4x3)") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (cols, rows) = when (selectedGridOption) {
                        "2x3" -> 2 to 3
                        "3x3" -> 3 to 3
                        "4x2" -> 4 to 2
                        "4x3" -> 4 to 3
                        else -> 2 to 3
                    }
                    onGenerate(isLandscape, cols, rows)
                }
            ) { Text("Preview PDF") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenInOtherApp) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open in Other App", fontSize = 11.sp)
                }
                Button(
                    onClick = onSavePdf,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) { Text("Save PDF", fontSize = 11.sp) }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

