@file:OptIn(ExperimentalMaterial3Api::class)

package cvam.dignity.juganua.features.postal.ippb_register

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

@Composable
fun IppbEntryForm(viewModel: IppbRegisterViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var scanTarget by remember { mutableStateOf(ScanTarget.NONE) }
    var qrPopupUid by remember { mutableStateOf<String?>(null) }

    val name by viewModel.name.collectAsState()
    val dob by viewModel.dob.collectAsState()
    val aadhaar by viewModel.aadhaar.collectAsState()
    val mobile by viewModel.mobile.collectAsState()
    val account by viewModel.account.collectAsState()
    val cif by viewModel.cif.collectAsState()
    val transType by viewModel.transactionType.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val isEditing by viewModel.editingId.collectAsState()

    // FIXED: Permission request flow. initialized at top level.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Permission Granted! Click again to scan.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    val requestScan = { target: ScanTarget ->
        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (status == PackageManager.PERMISSION_GRANTED) {
            scanTarget = target
        } else {
            // Trigger actual system request
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) { viewModel.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    // Intercept back only if scanner is open to prevent form exit
    BackHandler(enabled = scanTarget != ScanTarget.NONE) { scanTarget = ScanTarget.NONE }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFF1F5F9)))).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if(isEditing != null) "UPDATE CUSTOMER" else "CUSTOMER FORM", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF0D47A1))
                IconButton(onClick = { requestScan(ScanTarget.ALL) }) { Icon(Icons.Default.DocumentScanner, "Scan", tint = Color(0xFF0D47A1)) }
            }

            ColorfulFormCard(title = "Identity", icon = Icons.Default.Person, color = Color(0xFF0D47A1)) {
                ScanField("Full Name", name, { viewModel.name.value = it.uppercase(); viewModel.saveDraft() }, Icons.Default.Badge, true, onScan = { requestScan(ScanTarget.NAME) })
                ScanField("Date of Birth", dob, { viewModel.dob.value = it; viewModel.saveDraft() }, Icons.Default.Cake, onScan = { requestScan(ScanTarget.DOB) })
                ScanField(
                    label = "Aadhaar Number", value = aadhaar,
                    onValueChange = { if(it.length <= 12) viewModel.aadhaar.value = it; viewModel.saveDraft() },
                    icon = Icons.Default.VpnKey, onScan = { requestScan(ScanTarget.AADHAAR) },
                    onQrGenerate = { if(aadhaar.length == 12) qrPopupUid = aadhaar else Toast.makeText(context, "Enter 12 digit Aadhaar", Toast.LENGTH_SHORT).show() }
                )
                ScanField("Mobile Number", mobile, { if(it.length <= 10) viewModel.mobile.value = it; viewModel.saveDraft() }, Icons.Default.Phone, onScan = { requestScan(ScanTarget.MOBILE) })
            }

            ColorfulFormCard(title = "Account Details", icon = Icons.Default.AccountBalance, color = Color(0xFF2E7D32)) {
                ScanField("Account No", account, { if(it.length <= 12) viewModel.account.value = it; viewModel.saveDraft() }, Icons.Default.Dialpad, onScan = { requestScan(ScanTarget.ACCOUNT) })
                ScanField("CIF ID", cif, { if(it.length <= 10) viewModel.cif.value = it; viewModel.saveDraft() }, Icons.Default.Fingerprint, onScan = { requestScan(ScanTarget.CIF) })
            }

            ColorfulFormCard(title = "Transaction", icon = Icons.Default.Payments, color = Color(0xFFE65100)) {
                val transOptions = listOf("New Account", "Deposit", "Withdraw", "CELC")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    transOptions.forEachIndexed { i, l ->
                        SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index = i, count = 4), onClick = { viewModel.transactionType.value = l; viewModel.saveDraft() }, selected = transType == l, label = { Text(l, fontSize = 9.sp) })
                    }
                }
                ScanField("Amount", amount, { viewModel.amount.value = it; viewModel.saveDraft() }, Icons.Default.CurrencyRupee)
            }

            Button(onClick = viewModel::submitRegistration, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))) {
                Text(if(isEditing != null) "SAVE CHANGES" else "SUBMIT ENTRY", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(40.dp))
        }

        if (scanTarget != ScanTarget.NONE) {
            MLKitScannerOverlay(target = scanTarget, onDetected = { target, value ->
                when(target) {
                    ScanTarget.NAME -> viewModel.name.value = value.uppercase()
                    ScanTarget.AADHAAR -> viewModel.aadhaar.value = value
                    ScanTarget.MOBILE -> viewModel.mobile.value = value
                    ScanTarget.ACCOUNT -> viewModel.account.value = value
                    ScanTarget.CIF -> viewModel.cif.value = value
                    else -> {}
                }
                viewModel.saveDraft()
                if (scanTarget != ScanTarget.ALL) scanTarget = ScanTarget.NONE
            }, onClose = { scanTarget = ScanTarget.NONE })
        }
        if (qrPopupUid != null) AadhaarQrPopup(uid = qrPopupUid!!) { qrPopupUid = null }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun MLKitScannerOverlay(target: ScanTarget, onDetected: (ScanTarget, String) -> Unit, onClose: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val capturedFields = remember { mutableStateMapOf<ScanTarget, Boolean>() }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx)
            ProcessCameraProvider.getInstance(ctx).addListener({
                val provider = ProcessCameraProvider.getInstance(ctx).get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setTargetResolution(Size(1280, 720)).build()
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                    proxy.image?.let { img ->
                        recognizer.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { visionText ->
                            for (block in visionText.textBlocks) {
                                for (line in block.lines) {
                                    val text = line.text.uppercase(); val digits = text.replace(Regex("\\D"), "")
                                    fun emit(t: ScanTarget, v: String) { if (target == ScanTarget.ALL || target == t) { onDetected(t, v); capturedFields[t] = true } }
                                    if (digits.length == 10) {
                                        if (text.contains("CIF") || text.contains("CUST")) emit(ScanTarget.CIF, digits)
                                        else if (digits[0] in '6'..'9') emit(ScanTarget.MOBILE, digits)
                                    } else if (digits.length == 12) {
                                        if (text.contains("ACC") || text.contains("A/C")) emit(ScanTarget.ACCOUNT, digits)
                                        else if (digits[0] in '2'..'9') emit(ScanTarget.AADHAAR, digits)
                                    }
                                    if (target == ScanTarget.NAME && !text.any { it.isDigit() } && text.length > 5) emit(ScanTarget.NAME, line.text)
                                }
                            }
                        }.addOnCompleteListener { proxy.close() }
                    } ?: proxy.close()
                }
                provider.unbindAll(); provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx)); previewView
        }, Modifier.fillMaxSize())

        IconButton(onClick = onClose, Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(0.4f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White) }

        if (target == ScanTarget.ALL) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(), color = Color.Black.copy(0.6f), shape = RoundedCornerShape(20.dp)) {
                Button(onClick = onClose, Modifier.padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Green, contentColor = Color.Black)) { Text("FINISH BATCH SCAN") }
            }
        }
    }
}