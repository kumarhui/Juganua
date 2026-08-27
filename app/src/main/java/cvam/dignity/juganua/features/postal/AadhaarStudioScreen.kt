package cvam.dignity.juganua.features.postal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

data class AadhaarScan(val number: String, val timestamp: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AadhaarStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var uid by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showCam by remember { mutableStateOf(false) }
    var qrBmp by remember { mutableStateOf<Bitmap?>(null) }
    var history by remember { mutableStateOf(getAadhaarHistory(context)) }

    val aadhaarRegex = remember { Regex("[0-9]{12}") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) showCam = true }

    BackHandler { if (showCam) showCam = false else onBack() }

    val runRecognition = { image: InputImage ->
        isProcessing = true
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { vt ->
                findAadhaarNumber(vt, aadhaarRegex)?.let {
                    uid = it; saveToHistory(context, it); history = getAadhaarHistory(context)
                } ?: Toast.makeText(context, "Aadhaar UID not detected", Toast.LENGTH_SHORT).show()
            }.addOnCompleteListener { isProcessing = false }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            if (context.contentResolver.getType(it)?.contains("pdf") == true) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val pfd = context.contentResolver.openFileDescriptor(it, "r")!!
                        val page = PdfRenderer(pfd).openPage(0)
                        val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close(); pfd.close()
                        withContext(Dispatchers.Main) { runRecognition(InputImage.fromBitmap(bmp, 0)) }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            } else runRecognition(InputImage.fromFilePath(context, it))
        }
    }

    LaunchedEffect(uid) {
        qrBmp = if (uid.length == 12) withContext(Dispatchers.Default) { generateAadhaarQrCode("<?xml version=\"1.0\" encoding=\"UTF-8\"?><PrintLetterBarcodeData uid=\"$uid\"/>") } else null
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), Arrangement.spacedBy(24.dp)) {
            if (isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), shadowElevation = 8.dp) {
                Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF4A4AFF), Color(0xFF6C63FF)))).padding(24.dp)) {
                    Text("UIDAI Digital Pass", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(32.dp))
                    // Calling the helper with exact parameter names
                    ModernAadhaarInput(
                        value = uid,
                        onValueChange = { if (it.length <= 12) uid = it },
                        onCopy = {
                            clipboard.setText(AnnotatedString(uid))
                            Toast.makeText(context, "UID Copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AadhaarSmallCard(Modifier.weight(1f), "Live Cam", Icons.Default.PhotoCamera, Color(0xFFE3F2FD), Color(0xFF0D47A1)) { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) showCam = true else permLauncher.launch(Manifest.permission.CAMERA) }
                AadhaarSmallCard(Modifier.weight(1f), "Gallery", Icons.Default.Image, Color(0xFFFFF3E0), Color(0xFFE65100)) { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            AnimatedVisibility(visible = qrBmp != null) { qrBmp?.let { ModernQrDisplay(it, uid) } }
            if (history.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                    Text("RECENT SCANS", fontWeight = FontWeight.Black, color = Color.Gray)
                    history.take(5).forEach { scan -> HistoryCardItem(scan) { uid = scan.number } }
                }
            }
        }
        if (showCam) {
            // Calling the overlay with exact parameter names
            AadhaarCameraOverlay(
                onDetected = { detectedUid ->
                    uid = detectedUid
                    saveToHistory(context, detectedUid)
                    history = getAadhaarHistory(context)
                    showCam = false
                },
                onClose = { showCam = false }
            )
        }
    }
}

private fun findAadhaarNumber(vt: Text, reg: Regex): String? {
    vt.textBlocks.forEach { b -> b.lines.forEach { l -> l.text.replace("\\s".toRegex(), "").let { if (reg.matches(it)) return it } } }
    return null
}

private fun getAadhaarHistory(ctx: Context): List<AadhaarScan> {
    val s = ctx.getSharedPreferences("aadhaar_history", Context.MODE_PRIVATE).getString("scans_v2", "") ?: ""
    return if (s.isEmpty()) emptyList() else s.split(";").mapNotNull {
        val p = it.split("|")
        if (p.size == 2) AadhaarScan(p[0], p[1].toLongOrNull() ?: 0L) else null
    }
}

private fun saveToHistory(ctx: Context, n: String) {
    val p = ctx.getSharedPreferences("aadhaar_history", Context.MODE_PRIVATE)
    val l = p.getString("scans_v2", "")?.split(";")?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
    l.removeAll { it.startsWith(n) }; l.add(0, "$n|${System.currentTimeMillis()}")
    p.edit().putString("scans_v2", l.take(100).joinToString(";")).apply()
}

private fun generateAadhaarQrCode(t: String): Bitmap? = try {
    val m = QRCodeWriter().encode(t, BarcodeFormat.QR_CODE, 512, 512)
    val b = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
    for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    b
} catch (e: Exception) { null }

@Composable
fun ModernAadhaarInput(value: String, onValueChange: (String) -> Unit, onCopy: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(0.1f)).border(1.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).onFocusChanged { isFocused = it.isFocused }
            )
            if (value.length == 12) IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, null, tint = Color.White) }
        }
    }
}

@Composable
fun AadhaarCameraOverlay(onDetected: (String) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AadhaarCameraTextPreview(onDetected)
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun AadhaarCameraTextPreview(onDetected: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val regex = remember { Regex("[0-9]{12}") }
    AndroidView(factory = { ctx ->
        val view = PreviewView(ctx)
        ProcessCameraProvider.getInstance(ctx).addListener({
            val p = ProcessCameraProvider.getInstance(ctx).get()
            val analysis = ImageAnalysis.Builder().build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                proxy.image?.let { img ->
                    val inputImg = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
                    recognizer.process(inputImg).addOnSuccessListener { vt ->
                        vt.textBlocks.forEach { b ->
                            val cleanText = b.text.replace("\\s".toRegex(), "")
                            regex.find(cleanText)?.let { onDetected(it.value) }
                        }
                    }.addOnCompleteListener { proxy.close() }
                } ?: proxy.close()
            }
            p.unbindAll(); p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().apply { setSurfaceProvider(view.surfaceProvider) }, analysis)
        }, ContextCompat.getMainExecutor(ctx))
        view
    }, Modifier.fillMaxSize())
}

@Composable
fun AadhaarSmallCard(modifier: Modifier, title: String, icon: ImageVector, bgColor: Color, tint: Color, onClick: () -> Unit) {
    Surface(modifier = modifier.height(110.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), color = bgColor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)); Text(title, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
fun HistoryCardItem(scan: AadhaarScan, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp))
            Column { Text(scan.number.chunked(4).joinToString(" "), fontWeight = FontWeight.Bold); Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(scan.timestamp)), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun ModernQrDisplay(bitmap: Bitmap, uid: String) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bitmap = bitmap.asImageBitmap(), null, Modifier.size(200.dp)); Spacer(Modifier.height(16.dp)); Text(uid.chunked(4).joinToString(" "), fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}