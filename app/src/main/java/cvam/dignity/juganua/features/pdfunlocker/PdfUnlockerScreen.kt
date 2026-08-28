package cvam.dignity.juganua.features.pdfunlocker

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfUnlockerScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onNavigateToTool: (String, Uri) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State ---
    var sourcePdfUri by remember { mutableStateOf<Uri?>(null) }
    var unlockedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isPasswordProtected by remember { mutableStateOf<Boolean?>(null) }
    var fileName by remember { mutableStateOf("No file selected") }

    var bruteForceJob by remember { mutableStateOf<Job?>(null) }
    var tryingPassword by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var isUnlocking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var matchedPassword by remember { mutableStateOf("") }

    var unlockMode by remember { mutableStateOf(UnlockMode.SINGLE_PASS) }
    var singlePassword by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var yearStart by remember { mutableStateOf("") }
    var yearEnd by remember { mutableStateOf("") }

    // --- Core Logic: Detection ---
    val runFileCheck = { uri: Uri ->
        scope.launch {
            try {
                sourcePdfUri = uri
                fileName = PdfUnlockerLogic.getFileName(context, uri)
                // Clear state for new file
                unlockedPdfUri = null
                isUnlocked = false
                isUnlocking = false
                errorText = null

                val result = PdfUnlockerLogic.checkProtectionStatus(context, uri)
                if (result != null && result.isAlreadyUnprotected) {
                    // Logic: INSTANT UNLOCK if not encrypted
                    unlockedPdfUri = result.unlockedUri
                    isUnlocked = true
                    matchedPassword = "No Password"
                    isPasswordProtected = false
                } else {
                    // Logic: SHOW UI if encrypted
                    isPasswordProtected = true
                }
            } catch (e: Exception) {
                errorText = e.message
                isPasswordProtected = false
            }
        }
    }

    LaunchedEffect(initialUri) {
        initialUri?.let { runFileCheck(it) }
    }

    BackHandler {
        if (isUnlocking) {
            bruteForceJob?.cancel()
            isUnlocking = false
            tryingPassword = ""
        } else {
            onBack()
        }
    }

    val selectPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { runFileCheck(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- Document Info Card ---
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), shadowElevation = 4.dp) {
            Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFFE91E63), Color(0xFFFF5252)))).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Target Document", color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelSmall)
                        Text(fileName, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (sourcePdfUri != null && !isUnlocking) {
                        IconButton(onClick = { sourcePdfUri = null; isUnlocked = false; isPasswordProtected = null }) {
                            Icon(Icons.Default.DriveFileRenameOutline, null, tint = Color.White)
                        }
                    }
                }
            }
        }

        // --- Content Flow ---
        if (sourcePdfUri == null) {
            PdfActionCard(title = "Select PDF", subtitle = "Choose a file to start", icon = Icons.Default.FileUpload, onClick = { selectPdfLauncher.launch("application/pdf") })
        } else if (isUnlocked) {
            SuccessDashboard(
                password = matchedPassword,
                unlockedUri = unlockedPdfUri!!,
                onPreview = { PdfUnlockerLogic.previewPdf(context, unlockedPdfUri!!) },
                onDownload = { scope.launch { PdfUnlockerLogic.saveToDownloads(context, unlockedPdfUri!!, fileName) } },
                onNavigateToTool = onNavigateToTool
            )
        } else if (isPasswordProtected == true) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f), shape = CircleShape) {
                    Row(Modifier.padding(4.dp)) {
                        PdfModeTab("Manual", unlockMode == UnlockMode.SINGLE_PASS) { unlockMode = UnlockMode.SINGLE_PASS }
                        PdfModeTab("Brute Force", unlockMode == UnlockMode.AADHAAR_FORCE) { unlockMode = UnlockMode.AADHAAR_FORCE }
                    }
                }

                Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 2.dp) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (unlockMode == UnlockMode.SINGLE_PASS) {
                            PdfInputField(value = singlePassword, onValueChange = { singlePassword = it }, label = "Password", icon = Icons.Default.Lock)
                        } else {
                            PdfInputField(value = nameInput, onValueChange = { nameInput = it.uppercase() }, label = "Name Prefix (4 letters)", icon = Icons.Default.Face)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) { PdfInputField(value = yearStart, onValueChange = { yearStart = it }, label = "Start Year", icon = Icons.Default.Event, isNumeric = true) }
                                Box(Modifier.weight(1f)) { PdfInputField(value = yearEnd, onValueChange = { yearEnd = it }, label = "End Year", icon = Icons.Default.Event, isNumeric = true) }
                            }
                        }

                        Button(
                            onClick = {
                                isUnlocking = true; errorText = null
                                if (unlockMode == UnlockMode.SINGLE_PASS) {
                                    scope.launch {
                                        val res = PdfUnlockerLogic.attemptUnlock(context, sourcePdfUri!!, singlePassword)
                                        if (res != null) {
                                            unlockedPdfUri = res.unlockedUri; isUnlocked = true; matchedPassword = res.password; isUnlocking = false
                                        } else {
                                            errorText = "Wrong password"; isUnlocking = false
                                        }
                                    }
                                } else {
                                    val start = yearStart.toIntOrNull() ?: 1900
                                    val end = yearEnd.toIntOrNull() ?: 2025
                                    bruteForceJob = scope.launch {
                                        val range = if (start <= end) start..end else end..start
                                        val total = range.count().toFloat()
                                        var found = false
                                        for ((idx, year) in range.withIndex()) {
                                            val psw = nameInput + year
                                            tryingPassword = psw
                                            progress = (idx + 1) / total
                                            val res = PdfUnlockerLogic.attemptUnlock(context, sourcePdfUri!!, psw)
                                            if (res != null) {
                                                unlockedPdfUri = res.unlockedUri; isUnlocked = true; matchedPassword = res.password; isUnlocking = false
                                                found = true; break
                                            }
                                        }
                                        if (!found) { errorText = "Not found in selected range"; isUnlocking = false }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isUnlocking
                        ) { Text(if (isUnlocking) "ANALYZING..." else "START UNLOCK", fontWeight = FontWeight.Black) }
                    }
                }

                if (isUnlocking) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Testing Password:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(tryingPassword, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                            }
                        }
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape))
                        OutlinedButton(onClick = { bruteForceJob?.cancel(); isUnlocking = false; tryingPassword = "" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                            Icon(Icons.Default.Stop, null); Spacer(Modifier.width(8.dp)); Text("ABORT PROCESS")
                        }
                    }
                }
            }
        }

        errorText?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}