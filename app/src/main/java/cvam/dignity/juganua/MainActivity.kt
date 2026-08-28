package cvam.dignity.juganua

import cvam.dignity.juganua.features.settings.JuganuaToolsSettingsScreen
import cvam.dignity.juganua.features.passportphoto.PassportPhotoScreen
import cvam.dignity.juganua.features.backgroundremover.BackgroundRemoverScreen

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cvam.dignity.juganua.common.UsageTracker
import cvam.dignity.juganua.features.neonpen.NeonPenScreen
import cvam.dignity.juganua.features.whatsappchecker.WhatsappCheckerScreen
import cvam.dignity.juganua.features.bogascanner.BogaScannerScreen
import cvam.dignity.juganua.features.screenshottaker.ScreenshotTakerScreen
import cvam.dignity.juganua.features.mergepdf.MergePdfScreen
import cvam.dignity.juganua.features.pdfunlocker.PdfUnlockerScreen
import cvam.dignity.juganua.features.idcardsplitter.ExtractIdCardScreen
import cvam.dignity.juganua.features.ippbcardqr.IppbCardQrDialog
import cvam.dignity.juganua.ui.theme.JuganuaTheme

data class ToolAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val uniqueKey: String,
    val accentColor: Color = Color(0xFF0D47A1)
)

class MainActivity : ComponentActivity() {

    private val internalTargetTool =
        mutableStateOf<String?>(null)

    private val internalSharedUris =
        mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            JuganuaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    JuganuaAppShell(
                        requestedTool = internalTargetTool.value,
                        requestedUris = internalSharedUris.value,
                        onHandled = {
                            internalTargetTool.value = null
                            internalSharedUris.value = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        internalTargetTool.value =
            intent?.getStringExtra("TARGET_TOOL")

        internalSharedUris.value =
            intent?.getParcelableArrayListExtra<Uri>(
                "SHARED_URIS"
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuganuaAppShell(
    requestedTool: String?,
    requestedUris: List<Uri>?,
    onHandled: () -> Unit
) {
    var activeTool by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var sharedUris by remember {
        mutableStateOf<List<Uri>?>(null)
    }

    var showIppbQr by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(
        requestedTool,
        requestedUris
    ) {
        if (requestedTool != null) {

            if (
                requestedTool ==
                UsageTracker.ID_IPPB_CARD_QR
            ) {
                showIppbQr = true
            } else {
                activeTool = requestedTool
                sharedUris = requestedUris
            }

            onHandled()
        }
    }

    BackHandler {
        when {
            showIppbQr -> {
                showIppbQr = false
            }

            activeTool != null -> {
                activeTool = null
                sharedUris = null
            }

            else -> {
                // Let Activity handle exit.
            }
        }
    }

    val isDashboard =
        activeTool == null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text =
                            if (isDashboard) {
                                "JUGANUA"
                            } else {
                                getToolTitle(activeTool)
                            },
                        fontWeight =
                            FontWeight.Black,
                        fontSize = 16.sp
                    )
                },

                navigationIcon = {
                    if (!isDashboard) {
                        IconButton(
                            onClick = {
                                activeTool = null
                                sharedUris = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {

            AnimatedContent(
                targetState = activeTool,
                label = "ToolNavigation"
            ) { targetTool ->

                if (targetTool == null) {

                    DashboardGrid(
                        onToolSelect = { toolId ->

                            if (
                                toolId ==
                                UsageTracker.ID_IPPB_CARD_QR
                            ) {
                                showIppbQr = true
                            } else {
                                activeTool = toolId
                                sharedUris = null
                            }
                        }
                    )

                } else {

                    ToolScreen(
                        toolId = targetTool,
                        sharedUris = sharedUris,

                        onBack = {
                            activeTool = null
                            sharedUris = null
                        },

                        onNavigate = { toolId, data ->

                            activeTool = toolId

                            sharedUris =
                                when (data) {
                                    is Uri -> listOf(data)

                                    is List<*> ->
                                        data.filterIsInstance<Uri>()

                                    else -> null
                                }
                        }
                    )
                }
            }
        }
    }

    if (showIppbQr) {
        IppbCardQrDialog(
            onDismiss = {
                showIppbQr = false
            }
        )
    }
}

@Composable
private fun ToolScreen(
    toolId: String,
    sharedUris: List<Uri>?,
    onBack: () -> Unit,
    onNavigate: (String, Any?) -> Unit
) {
    when (toolId) {

        UsageTracker.ID_PDF_UNLOCKER -> {
            PdfUnlockerScreen(
                initialUri =
                    sharedUris?.firstOrNull(),
                onBack = onBack,
                onNavigateToTool = onNavigate
            )
        }

        UsageTracker.ID_MERGE_PDF -> {
            MergePdfScreen(
                initialUri =
                    sharedUris?.firstOrNull(),
                initialUris =
                    sharedUris,
                onBack = onBack
            )
        }

        UsageTracker.ID_ID_CARD_SPLITTER -> {
            ExtractIdCardScreen(
                initialUri =
                    sharedUris?.firstOrNull(),
                initialUris =
                    sharedUris,
                onBack = onBack
            )
        }
        UsageTracker.ID_PASSPORT_PHOTO_MAKER -> {
            PassportPhotoScreen(
                initialUris = sharedUris,
                onBack = onBack
            )
        }

        UsageTracker.ID_BACKGROUND_REMOVER -> {
            BackgroundRemoverScreen(
                initialUri = sharedUris?.firstOrNull(),
                onBack = onBack
            )
        }

        UsageTracker.ID_NEON_PEN -> {
            NeonPenScreen(
                onBack = onBack,
                onNavigateToSettings = {
                    onNavigate(
                        UsageTracker.ID_NEON_PEN_SETTINGS,
                        null
                    )
                }
            )
        }

        UsageTracker.ID_SCREENSHOT_TAKER -> {
            ScreenshotTakerScreen(
                onBack = onBack
            )
        }

        UsageTracker.ID_BOGA_SCANNER -> {
            BogaScannerScreen(
                onBack = onBack
            )
        }

        UsageTracker.ID_WHATSAPP_CHECKER -> {
            WhatsappCheckerScreen()
        }
        UsageTracker.ID_TOOLS_SETTINGS -> {
            JuganuaToolsSettingsScreen(
                onBack = onBack
            )
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Tool unavailable")
            }
        }
    }
}

private fun getToolTitle(
    toolId: String?
): String {
    return when (toolId) {

        UsageTracker.ID_PDF_UNLOCKER ->
            "PDF UNLOCKER"

        UsageTracker.ID_MERGE_PDF ->
            "MERGE PDF"

        UsageTracker.ID_ID_CARD_SPLITTER ->
            "ID SPLITTER"

        UsageTracker.ID_PASSPORT_PHOTO_MAKER ->
            "PASSPORT PHOTO"

        UsageTracker.ID_BACKGROUND_REMOVER ->
            "BACKGROUND REMOVER"

        UsageTracker.ID_NEON_PEN ->
            "NEON PEN"

        UsageTracker.ID_SCREENSHOT_TAKER ->
            "SCREENSHOT"

        UsageTracker.ID_BOGA_SCANNER ->
            "BOGA SCANNER"

        UsageTracker.ID_WHATSAPP_CHECKER ->
            "WHATSAPP CHECKER"

        UsageTracker.ID_TOOLS_SETTINGS ->
            "SETTINGS"

        else ->
            "JUGANUA"
    }
}

@Composable
private fun DashboardGrid(
    onToolSelect: (String) -> Unit
) {
    val tools = remember {

        listOf(

            ToolAction(
                title = "PDF Unlocker",
                subtitle = "Security Removal",
                icon = Icons.Default.LockOpen,
                uniqueKey =
                    UsageTracker.ID_PDF_UNLOCKER,
                accentColor =
                    Color(0xFF1565C0)
            ),

            ToolAction(
                title = "Merge PDF",
                subtitle = "Join Files",
                icon = Icons.Default.MergeType,
                uniqueKey =
                    UsageTracker.ID_MERGE_PDF,
                accentColor =
                    Color(0xFF2E7D32)
            ),

            ToolAction(
                title = "ID Splitter",
                subtitle = "Crop & Print",
                icon = Icons.Default.FilterFrames,
                uniqueKey =
                    UsageTracker.ID_ID_CARD_SPLITTER,
                accentColor =
                    Color(0xFF6A1B9A)
            ),

            ToolAction(
                title = "IPPB QR",
                subtitle = "Card Reference",
                icon = Icons.Default.QrCode,
                uniqueKey =
                    UsageTracker.ID_IPPB_CARD_QR,
                accentColor =
                    Color(0xFFEF6C00)
            ),
            ToolAction(
                title = "Passport Photo",
                subtitle = "Photo Maker",
                icon = Icons.Default.PhotoCamera,
                uniqueKey =
                    UsageTracker.ID_PASSPORT_PHOTO_MAKER,
                accentColor =
                    Color(0xFF00897B)
            ),

            ToolAction(
                title = "Background Remover",
                subtitle = "Remove & Replace",
                icon = Icons.Default.PhotoCamera,
                uniqueKey =
                    UsageTracker.ID_BACKGROUND_REMOVER,
                accentColor =
                    Color(0xFF5C6BC0)
            ),

            ToolAction(
                title = "Neon Pen",
                subtitle = "Screen Drawing",
                icon = Icons.Default.Draw,
                uniqueKey =
                    UsageTracker.ID_NEON_PEN,
                accentColor =
                    Color(0xFFD81B60)
            ),

            ToolAction(
                title = "Screenshot",
                subtitle = "Capture & Crop",
                icon = Icons.Default.Screenshot,
                uniqueKey =
                    UsageTracker.ID_SCREENSHOT_TAKER,
                accentColor =
                    Color(0xFF5E35B1)
            ),

            ToolAction(
                title = "Boga Scanner",
                subtitle = "ID & Documents",
                icon = Icons.Default.DocumentScanner,
                uniqueKey =
                    UsageTracker.ID_BOGA_SCANNER,
                accentColor =
                    Color(0xFF3949AB)
            ),

            ToolAction(
                title = "WhatsApp Checker",
                subtitle = "Check Number",
                icon = Icons.Default.Phone,
                uniqueKey =
                    UsageTracker.ID_WHATSAPP_CHECKER,
                accentColor =
                    Color(0xFF2E7D32)
            ),
            ToolAction(
                title = "Settings",
                subtitle = "Tool Permissions",
                icon = Icons.Default.Settings,
                uniqueKey =
                    UsageTracker.ID_TOOLS_SETTINGS,
                accentColor =
                    Color(0xFF455A64)
            )
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement =
            Arrangement.spacedBy(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(22.dp)
    ) {

        itemsIndexed(tools) { _, tool ->

            CircularDashboardTool(
                tool = tool,
                onClick = {
                    onToolSelect(
                        tool.uniqueKey
                    )
                }
            )
        }
    }
}

@Composable
fun CircularDashboardTool(
    tool: ToolAction,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Surface(
            modifier =
                Modifier.size(76.dp),

            shape =
                CircleShape,

            color =
                tool.accentColor
                    .copy(alpha = 0.12f),

            border =
                BorderStroke(
                    1.dp,
                    tool.accentColor
                        .copy(alpha = 0.25f)
                )
        ) {

            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        tool.icon,

                    contentDescription =
                        tool.title,

                    tint =
                        tool.accentColor,

                    modifier =
                        Modifier.size(34.dp)
                )
            }
        }

        Spacer(
            Modifier.height(8.dp)
        )

        Text(
            text = tool.title,

            fontWeight =
                FontWeight.Bold,

            fontSize =
                11.sp,

            textAlign =
                TextAlign.Center,

            maxLines = 2
        )
    }
}







