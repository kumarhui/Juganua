package cvam.dignity.juganua.features.postal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

data class ScanResult(val code: String, val timestamp: Long = System.currentTimeMillis())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val articleRegex = remember { Regex("[A-Z]{2}[0-9]{9}[A-Z]{2}") }
    val scanHistory = remember { mutableStateListOf<ScanResult>() }
    val barcodeCache = remember { mutableStateMapOf<String, Bitmap>() }
    var lastScannedCode by remember { mutableStateOf("") }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showCamera by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }

    val addResults: (List<String>) -> Unit = { codes ->
        scope.launch {
            codes.forEach { code ->
                val upperCode = code.uppercase()
                if (upperCode != lastScannedCode && scanHistory.none { it.code == upperCode }) {
                    scanHistory.add(ScanResult(upperCode))
                    lastScannedCode = upperCode
                    withContext(Dispatchers.Default) { generateBarcode(upperCode)?.let { barcodeCache[upperCode] = it } }
                }
            }
            if (scanHistory.isNotEmpty()) currentIndex = scanHistory.size - 1
        }
    }

    val deleteItem: (Int) -> Unit = { index ->
        if (index in scanHistory.indices) {
            val code = scanHistory[index].code
            scanHistory.removeAt(index)
            barcodeCache.remove(code)
            if (currentIndex >= scanHistory.size && scanHistory.isNotEmpty()) currentIndex = scanHistory.size - 1
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isProcessing = true
            performGalleryOcr(context, it, articleRegex) { res -> addResults(res); isProcessing = false }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioSmallCard(Modifier.weight(1f), "Live Cam", Icons.Default.PhotoCamera, MaterialTheme.colorScheme.primary) { showCamera = true }
                StudioSmallCard(Modifier.weight(1f), "Gallery", Icons.Default.Collections, Color(0xFF00C853)) { galleryLauncher.launch("image/*") }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(value = manualInput, onValueChange = { if (it.length <= 13) manualInput = it.uppercase() }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Enter 13-digit code") }, leadingIcon = { Icon(Icons.Outlined.QrCode, null) }, shape = RoundedCornerShape(20.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            AnimatedVisibility(visible = manualInput.length == 13) { ManualBarcodePreview(manualInput) { addResults(listOf(manualInput)); manualInput = "" } }
            if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
            if (scanHistory.isNotEmpty()) {
                ArticleResultViewer(scanHistory, currentIndex, barcodeCache, { currentIndex++ }, { currentIndex-- }, { deleteItem(currentIndex) }) { scanHistory.clear(); barcodeCache.clear(); currentIndex = 0 }
            }
            Spacer(Modifier.height(40.dp))
        }
        if (showCamera) CameraOverlayView(articleRegex, { addResults(listOf(it)) }, { showCamera = false }, scanHistory.size)
    }
}

@Composable
fun ManualBarcodePreview(code: String, onAdd: () -> Unit) {
    var preview by remember(code) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(code) { withContext(Dispatchers.Default) { preview = generateBarcode(code) } }
    Card(Modifier.padding(top = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.3f))) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (preview != null) Image(preview!!.asImageBitmap(), null, Modifier.height(80.dp).fillMaxWidth().background(Color.White).padding(8.dp))
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("SAVE TO HISTORY") }
        }
    }
}

@Composable
fun ArticleResultViewer(res: List<ScanResult>, idx: Int, cache: Map<String, Bitmap>, onN: () -> Unit, onP: () -> Unit, onD: () -> Unit, onR: () -> Unit) {
    val item = res[idx]
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = MaterialTheme.colorScheme.primary.copy(0.1f), shape = CircleShape) { Text("${idx + 1} / ${res.size}", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontWeight = FontWeight.Bold) }
        Card(Modifier.padding(top = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box {
                IconButton(onClick = onD, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.6f)) }
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    cache[item.code]?.let { Image(it.asImageBitmap(), null, Modifier.height(110.dp).fillMaxWidth()) }
                    Text(item.code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onP, enabled = idx > 0, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ChevronLeft, null) }
            Button(onClick = onN, enabled = idx < res.size - 1, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ChevronRight, null) }
        }
        TextButton(onClick = onR, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("CLEAR ALL") }
    }
}

fun generateBarcode(t: String): Bitmap? = try {
    val m = MultiFormatWriter().encode(t, BarcodeFormat.CODE_128, 600, 240)
    val b = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
    for (x in 0 until 600) for (y in 0 until 240) b.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    b
} catch (_: Exception) { null }

@Composable
fun StudioSmallCard(m: Modifier, t: String, i: ImageVector, c: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = m.height(100.dp), shape = RoundedCornerShape(24.dp), color = c.copy(0.08f), border = BorderStroke(1.dp, c.copy(0.2f))) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(i, null, Modifier.size(32.dp), c); Text(t, fontWeight = FontWeight.ExtraBold, color = c, fontSize = 14.sp)
        }
    }
}

private fun performGalleryOcr(ctx: Context, uri: Uri, reg: Regex, onC: (List<String>) -> Unit) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    (ctx as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
        try {
            val bmp = if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            else MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
            recognizer.process(InputImage.fromBitmap(bmp, 0)).addOnSuccessListener { vt ->
                val d = mutableSetOf<String>(); vt.textBlocks.forEach { b -> reg.findAll(b.text.replace(Regex("[\\s\\.\\-]"), "").uppercase()).forEach { d.add(it.value) } }; onC(d.toList())
            }
        } catch (_: Exception) { onC(emptyList()) }
    }
}

@Composable
fun CameraOverlayView(regex: Regex, onD: (String) -> Unit, onC: () -> Unit, count: Int) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraTextPreview(regex, onD)
        Box(Modifier.fillMaxSize().border(2.dp, Color.White.copy(0.3f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Text("Align Barcode Here", color = Color.White)
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$count ITEMS SCANNED", fontWeight = FontWeight.Black); Button(onClick = onC) { Text("SAVE") }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraTextPreview(regex: Regex, onD: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    AndroidView(factory = { ctx ->
        val view = PreviewView(ctx)
        ProcessCameraProvider.getInstance(ctx).addListener({
            val p = ProcessCameraProvider.getInstance(ctx).get()
            val analysis = ImageAnalysis.Builder().setTargetResolution(Size(1280, 720)).build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                proxy.image?.let { img ->
                    recognizer.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { vt ->
                        vt.textBlocks.forEach { b -> regex.find(b.text.replace(Regex("[\\s\\.\\-]"), "").uppercase())?.let { onD(it.value) } }
                    }.addOnCompleteListener { proxy.close() }
                } ?: proxy.close()
            }
            p.unbindAll(); p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().apply { setSurfaceProvider(view.surfaceProvider) }, analysis)
        }, ContextCompat.getMainExecutor(ctx))
        view
    }, Modifier.fillMaxSize())
}