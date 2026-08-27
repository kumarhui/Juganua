package cvam.dignity.juganua.features.postal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

data class ScannerHistory(val refNumber: String, val timestamp: Long)

@Composable
fun IppbCardQrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isCameraOpen by remember { mutableStateOf(false) }
    var historyList by remember { mutableStateOf(getScannerHistory(context)) }

    BackHandler { if (isCameraOpen) isCameraOpen = false else onBack() }

    LaunchedEffect(inputText) {
        if (inputText.length == 16) {
            val payload = "{\"ReleaseVersionIndicator\":\"V01.01\",\"QRCodeTypeIndicator\":\"CUSSTA\",\"QRCardRefNumber\":\"$inputText\",\"CardExpDate\":\"03-MAY-2099\",\"FreeField1\":\"\",\"FreeField2\":\"\",\"FreeField3\":\"\"}"
            qrBitmap = withContext(Dispatchers.Default) { generateQrCodeInternal(payload) }
            saveToScannerHistory(context, inputText)
            historyList = getScannerHistory(context)
        } else qrBitmap = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // FIXED: Corrected positional argument order (verticalArrangement then horizontalAlignment)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = inputText, onValueChange = { if (it.length <= 16) inputText = it },
                label = { Text("16 Digit Reference") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                trailingIcon = { IconButton(onClick = { isCameraOpen = true }) { Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary) } }
            )

            AnimatedVisibility(visible = qrBitmap != null) {
                qrBitmap?.let { ModernIppbCodeCard(it, inputText.chunked(4).joinToString(" ")) }
            }

            if (historyList.isNotEmpty()) {
                Text("RECENT GENERATIONS", fontWeight = FontWeight.Black, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
                historyList.forEach { item ->
                    Surface(onClick = { inputText = item.refNumber }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp));
                            Column {
                                Text(item.refNumber.chunked(4).joinToString(" "), fontWeight = FontWeight.Bold)
                                Text(SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(item.timestamp)), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        if (isCameraOpen) BarcodeScannerOverlay(onDetected = { inputText = it.filter { c -> c.isDigit() }.take(16); isCameraOpen = false }, onClose = { isCameraOpen = false })
    }
}

@Composable
fun ModernIppbCodeCard(bitmap: Bitmap, code: String) {
    val brush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(0.5f), Color.White))
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), shadowElevation = 12.dp) {
        Column(modifier = Modifier.background(brush).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(modifier = Modifier.size(220.dp), color = Color.White, shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
                Image(bitmap.asImageBitmap(), null, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text("REF: $code", fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        }
    }
}

@Composable
fun BarcodeScannerOverlay(onDetected: (String) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        BarcodeCameraPreview(onDetected)
        // FIXED: Corrected positional argument order
        Box(modifier = Modifier.fillMaxSize().border(2.dp, Color.White.copy(0.3f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.FilterCenterFocus, null, modifier = Modifier.size(64.dp), tint = Color.White)
                Text("Align 16-Digit Barcode", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(32.dp), tint = Color.White) }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun BarcodeCameraPreview(onDetected: (String) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val scanner = remember { BarcodeScanning.getClient() }
    AndroidView(factory = { ctx ->
        val view = PreviewView(ctx)
        ProcessCameraProvider.getInstance(ctx).addListener({
            val p = ProcessCameraProvider.getInstance(ctx).get()
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                proxy.image?.let { img ->
                    scanner.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                        codes.firstOrNull()?.rawValue?.let { onDetected(it) }
                    }.addOnCompleteListener { proxy.close() }
                } ?: proxy.close()
            }
            p.unbindAll(); p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().apply { setSurfaceProvider(view.surfaceProvider) }, analysis)
        }, ContextCompat.getMainExecutor(ctx))
        view
    }, modifier = Modifier.fillMaxSize())
}

private fun saveToScannerHistory(c: Context, r: String) {
    val p = c.getSharedPreferences("ippb_history", Context.MODE_PRIVATE)
    val list = p.getString("scans", "")?.split(";")?.toMutableList() ?: mutableListOf()
    list.removeAll { it.startsWith(r) }; list.add(0, "$r|${System.currentTimeMillis()}")
    p.edit().putString("scans", list.take(50).joinToString(";")).apply()
}

private fun getScannerHistory(c: Context): List<ScannerHistory> {
    val s = c.getSharedPreferences("ippb_history", Context.MODE_PRIVATE).getString("scans", "") ?: ""
    return if (s.isEmpty()) emptyList() else s.split(";").mapNotNull { val parts = it.split("|"); if (parts.size == 2) ScannerHistory(parts[0], parts[1].toLongOrNull() ?: 0L) else null }
}

private fun generateQrCodeInternal(t: String): Bitmap? = try {
    val m = QRCodeWriter().encode(t, BarcodeFormat.QR_CODE, 512, 512)
    val b = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
    for (x in 0 until 512) for (y in 0 until 512) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    b
} catch (e: Exception) { null }