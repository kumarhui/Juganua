package cvam.dignity.juganua.features.postal

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

data class ScannerHistory(
    val refNumber: String,
    val timestamp: Long
)

@Composable
fun IppbCardQrDialog(
    onDismiss: () -> Unit
) {
    var isCameraOpen by remember {
        mutableStateOf(false)
    }

    Dialog(
        onDismissRequest = {
            if (!isCameraOpen) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !isCameraOpen
        )
    ) {

        BackHandler {
            if (isCameraOpen) {
                isCameraOpen = false
            } else {
                onDismiss()
            }
        }

        if (isCameraOpen) {

            BarcodeScannerOverlay(
                onDetected = {
                    isCameraOpen = false
                },
                onClose = {
                    isCameraOpen = false
                }
            )

        } else {

            IppbQrContent(
                onClose = onDismiss,
                onScan = {
                    isCameraOpen = true
                }
            )
        }
    }
}

@Composable
private fun IppbQrContent(
    onClose: () -> Unit,
    onScan: () -> Unit
) {
    val context = LocalContext.current

    var inputText by remember {
        mutableStateOf("")
    }

    var qrBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var historyList by remember {
        mutableStateOf(
            getScannerHistory(context)
        )
    }

    LaunchedEffect(inputText) {

        if (inputText.length == 16) {

            val payload =
                "{\"ReleaseVersionIndicator\":\"V01.01\",\"QRCodeTypeIndicator\":\"CUSSTA\",\"QRCardRefNumber\":\"$inputText\",\"CardExpDate\":\"03-MAY-2099\",\"FreeField1\":\"\",\"FreeField2\":\"\",\"FreeField3\":\"\"}"

            qrBitmap =
                withContext(Dispatchers.Default) {
                    generateQrCodeInternal(
                        payload
                    )
                }

            saveToScannerHistory(
                context,
                inputText
            )

            historyList =
                getScannerHistory(context)

        } else {
            qrBitmap = null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .fillMaxHeight(0.90f),

        shape =
            RoundedCornerShape(28.dp),

        color =
            MaterialTheme.colorScheme.surface,

        tonalElevation =
            8.dp,

        shadowElevation =
            16.dp
    ) {

        Column(
            modifier =
                Modifier.fillMaxSize()
        ) {

            // Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 22.dp,
                            top = 18.dp,
                            end = 12.dp,
                            bottom = 14.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Surface(
                    modifier =
                        Modifier.size(48.dp),

                    shape =
                        CircleShape,

                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                ) {

                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(
                            Icons.Default.QrCode,
                            null,

                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary,

                            modifier =
                                Modifier.size(27.dp)
                        )
                    }
                }

                Spacer(
                    Modifier.width(14.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "IPPB QR",
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Black
                    )

                    Text(
                        "Generate customer QR",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onClose
                ) {

                    Icon(
                        Icons.Default.Close,
                        contentDescription =
                            "Close"
                    )
                }
            }

            HorizontalDivider(
                modifier =
                    Modifier.alpha(0.5f)
            )

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(22.dp),

                verticalArrangement =
                    Arrangement.spacedBy(18.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                OutlinedTextField(

                    value = inputText,

                    onValueChange = {
                        inputText =
                            it.filter { c ->
                                c.isDigit()
                            }.take(16)
                    },

                    label = {
                        Text(
                            "16 Digit Reference"
                        )
                    },

                    placeholder = {
                        Text(
                            "0000 0000 0000 0000"
                        )
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true,

                    shape =
                        RoundedCornerShape(16.dp),

                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.Number,

                            imeAction =
                                ImeAction.Done
                        ),

                    trailingIcon = {

                        IconButton(
                            onClick = onScan
                        ) {

                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription =
                                    "Scan"
                            )
                        }
                    }
                )

                Text(
                    "${inputText.length}/16 digits",

                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,

                    modifier =
                        Modifier.align(
                            Alignment.End
                        )
                )

                AnimatedVisibility(
                    visible =
                        qrBitmap != null,

                    enter =
                        fadeIn(),

                    exit =
                        fadeOut()
                ) {

                    qrBitmap?.let {

                        ModernIppbCodeCard(
                            bitmap = it,

                            code =
                                inputText
                                    .chunked(4)
                                    .joinToString(" ")
                        )
                    }
                }

                if (
                    qrBitmap == null &&
                    inputText.length < 16
                ) {

                    Surface(
                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(24.dp),

                        color =
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                                .copy(alpha = 0.4f)
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(28.dp),

                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Icon(
                                Icons.Default.QrCode,
                                null,

                                modifier =
                                    Modifier.size(42.dp),

                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            )

                            Spacer(
                                Modifier.height(10.dp)
                            )

                            Text(
                                "QR preview",

                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                "Enter 16 digits or scan a reference",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,

                                textAlign =
                                    TextAlign.Center
                            )
                        }
                    }
                }

                if (historyList.isNotEmpty()) {

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "RECENT GENERATIONS",

                            fontSize =
                                11.sp,

                            fontWeight =
                                FontWeight.Black,

                            letterSpacing =
                                1.sp,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            Modifier.height(10.dp)
                        )

                        historyList
                            .take(5)
                            .forEach { item ->

                                Surface(
                                    onClick = {
                                        inputText =
                                            item.refNumber
                                    },

                                    shape =
                                        RoundedCornerShape(16.dp),

                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant
                                            .copy(alpha = 0.4f),

                                    modifier =
                                        Modifier.fillMaxWidth()
                                ) {

                                    Row(
                                        modifier =
                                            Modifier.padding(
                                                14.dp
                                            ),

                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Surface(
                                            modifier =
                                                Modifier.size(
                                                    40.dp
                                                ),

                                            shape =
                                                CircleShape,

                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .primary
                                                    .copy(
                                                        alpha = 0.1f
                                                    )
                                        ) {

                                            Box(
                                                contentAlignment =
                                                    Alignment.Center
                                            ) {

                                                Icon(
                                                    Icons.Default.History,
                                                    null,

                                                    tint =
                                                        MaterialTheme
                                                            .colorScheme
                                                            .primary
                                                )
                                            }
                                        }

                                        Spacer(
                                            Modifier.width(12.dp)
                                        )

                                        Column {

                                            Text(
                                                item.refNumber
                                                    .chunked(4)
                                                    .joinToString(" "),

                                                fontWeight =
                                                    FontWeight.Bold,

                                                fontFamily =
                                                    FontFamily.Monospace
                                            )

                                            Text(
                                                SimpleDateFormat(
                                                    "dd MMM, hh:mm a",
                                                    Locale.getDefault()
                                                ).format(
                                                    Date(
                                                        item.timestamp
                                                    )
                                                ),

                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelSmall
                                            )
                                        }
                                    }
                                }

                                Spacer(
                                    Modifier.height(8.dp)
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernIppbCodeCard(
    bitmap: Bitmap,
    code: String
) {

    val brush =
        Brush.verticalGradient(
            listOf(
                MaterialTheme
                    .colorScheme
                    .primaryContainer
                    .copy(alpha = 0.5f),

                MaterialTheme
                    .colorScheme
                    .surface
            )
        )

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(28.dp),

        shadowElevation =
            8.dp
    ) {

        Column(
            modifier =
                Modifier
                    .background(brush)
                    .padding(22.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                "GENERATED QR",

                fontSize =
                    11.sp,

                fontWeight =
                    FontWeight.Black,

                letterSpacing =
                    1.sp,

                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )

            Spacer(
                Modifier.height(14.dp)
            )

            Surface(
                modifier =
                    Modifier.size(220.dp),

                color =
                    Color.White,

                shape =
                    RoundedCornerShape(22.dp),

                shadowElevation =
                    3.dp
            ) {

                Image(
                    bitmap.asImageBitmap(),
                    null,

                    modifier =
                        Modifier.padding(16.dp)
                )
            }

            Spacer(
                Modifier.height(16.dp)
            )

            Text(
                "REF: $code",

                fontWeight =
                    FontWeight.Black,

                fontFamily =
                    FontFamily.Monospace,

                color =
                    MaterialTheme
                        .colorScheme
                        .primary,

                fontSize =
                    17.sp
            )
        }
    }
}

@Composable
private fun BarcodeScannerOverlay(
    onDetected: (String) -> Unit,
    onClose: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black)
    ) {

        BarcodeCameraPreview(
            onDetected = onDetected
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(28.dp)
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.35f),
                        RoundedCornerShape(28.dp)
                    )
        )

        Column(
            modifier =
                Modifier.align(
                    Alignment.Center
                ),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                Icons.Default.FilterCenterFocus,
                null,

                modifier =
                    Modifier.size(64.dp),

                tint =
                    Color.White
            )

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                "Align 16-Digit Barcode",

                color =
                    Color.White,

                fontWeight =
                    FontWeight.Bold
            )
        }

        IconButton(
            onClick = onClose,

            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
        ) {

            Icon(
                Icons.Default.Close,
                null,

                modifier =
                    Modifier.size(32.dp),

                tint =
                    Color.White
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun BarcodeCameraPreview(
    onDetected: (String) -> Unit
) {

    val owner =
        LocalLifecycleOwner.current

    val scanner =
        remember {
            BarcodeScanning.getClient()
        }

    AndroidView(

        factory = { ctx ->

            val view =
                PreviewView(ctx)

            val cameraProvider =
                ProcessCameraProvider
                    .getInstance(ctx)

            cameraProvider.addListener({

                try {

                    val provider =
                        cameraProvider.get()

                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis
                                    .STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()

                    analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor()
                    ) { proxy ->

                        proxy.image?.let { image ->

                            val input =
                                InputImage.fromMediaImage(
                                    image,

                                    proxy.imageInfo
                                        .rotationDegrees
                                )

                            scanner
                                .process(input)
                                .addOnSuccessListener { codes ->

                                    codes
                                        .firstOrNull()
                                        ?.rawValue
                                        ?.let { value ->

                                            val digits =
                                                value
                                                    .filter {
                                                        it.isDigit()
                                                    }
                                                    .take(16)

                                            if (
                                                digits.length == 16
                                            ) {
                                                onDetected(
                                                    digits
                                                )
                                            }
                                        }
                                }
                                .addOnCompleteListener {
                                    proxy.close()
                                }

                        } ?: proxy.close()
                    }

                    provider.unbindAll()

                    val preview =
                        Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(
                                    view.surfaceProvider
                                )
                            }

                    provider.bindToLifecycle(
                        owner,

                        CameraSelector
                            .DEFAULT_BACK_CAMERA,

                        preview,

                        analysis
                    )

                } catch (_: Exception) {
                }

            }, ContextCompat.getMainExecutor(ctx))

            view
        },

        modifier =
            Modifier.fillMaxSize()
    )
}

private fun saveToScannerHistory(
    context: Context,
    refNumber: String
) {

    val prefs =
        context.getSharedPreferences(
            "ippb_history",
            Context.MODE_PRIVATE
        )

    val list =
        prefs
            .getString("scans", "")
            ?.split(";")
            ?.filter {
                it.isNotBlank()
            }
            ?.toMutableList()
            ?: mutableListOf()

    list.removeAll {
        it.startsWith("$refNumber|")
    }

    list.add(
        0,
        "$refNumber|${System.currentTimeMillis()}"
    )

    prefs.edit()
        .putString(
            "scans",
            list.take(50)
                .joinToString(";")
        )
        .apply()
}

private fun getScannerHistory(
    context: Context
): List<ScannerHistory> {

    val stored =
        context
            .getSharedPreferences(
                "ippb_history",
                Context.MODE_PRIVATE
            )
            .getString(
                "scans",
                ""
            ) ?: ""

    if (stored.isEmpty()) {
        return emptyList()
    }

    return stored
        .split(";")
        .mapNotNull { item ->

            val parts =
                item.split("|")

            if (parts.size == 2) {

                ScannerHistory(
                    refNumber = parts[0],
                    timestamp =
                        parts[1]
                            .toLongOrNull()
                            ?: 0L
                )

            } else {
                null
            }
        }
}

private fun generateQrCodeInternal(
    text: String
): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            512,
            512
        )

        val bitmap = Bitmap.createBitmap(
            512,
            512,
            Bitmap.Config.ARGB_8888
        )

        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(
                    x,
                    y,
                    if (matrix[x, y]) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                )
            }
        }

        bitmap
    } catch (_: Exception) {
        null
    }
}