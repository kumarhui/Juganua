package cvam.dignity.juganua

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cvam.dignity.juganua.common.UsageTracker
import cvam.dignity.juganua.common.PinnedToolInfo
import cvam.dignity.juganua.tools.*
import cvam.dignity.juganua.features.pdf.*
import cvam.dignity.juganua.features.pdf.id_card_splitter.*
import cvam.dignity.juganua.features.postal.*
import cvam.dignity.juganua.features.other.*
import cvam.dignity.juganua.features.photo.PassportPhotoScreen
import cvam.dignity.juganua.features.rpli.RpliCalculatorScreen
import cvam.dignity.juganua.ui.theme.JuganuaTheme
import kotlinx.coroutines.launch

// --- Core Data Models ---
data class ToolAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val uniqueKey: String? = null,
    val accentColor: Color = Color(0xFF0D47A1),
    val url: String? = null,
    val isReady: Boolean = true
)

data class CategoryGroup(
    val category: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
)

class MainActivity : ComponentActivity() {
    private val internalTargetTool = mutableStateOf<String?>(null)
    private val internalSharedUris = mutableStateOf<List<Uri>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)

        // --- COMPOSABLE REGISTRY ---
        UsageTracker.composableRegistry[UsageTracker.ID_OFFLINE_SHARE] = { _, allUris, onBack, _ ->
            OfflineShareScreen(initialUris = allUris as? List<Uri> ?: emptyList(), onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_PASSPORT_PHOTO] = { _, allUris, onBack, _ ->
            PassportPhotoScreen(initialUris = allUris as? List<Uri>, onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_PDF_UNLOCKER] = { uri, _, onBack, onNavigate ->
            PdfUnlockerScreen(
                initialUri = uri as? Uri,
                onBack = onBack,
                onNavigateToTool = { id, data -> onNavigate(id, data) }
            )
        }
        UsageTracker.composableRegistry[UsageTracker.ID_MERGE_PDF] = { uri, uris, onBack, _ ->
            MergePdfScreen(initialUri = uri as? Uri, initialUris = uris as? List<Uri>, onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_ID_CARD_SPLITTER] = { uri, uris, onBack, _ ->
            ExtractIdCardScreen(initialUri = uri as? Uri, initialUris = uris as? List<Uri>, onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_ARTICLE_SCAN] = { _, _, onBack, _ ->
            ArticleScannerScreen(onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_RPLI_CALC] = { _, _, _, _ ->
            RpliCalculatorScreen()
        }
        UsageTracker.composableRegistry[UsageTracker.ID_AADHAAR_QR] = { _, _, onBack, _ ->
            AadhaarStudioScreen(onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_IPPB_CARD_QR] = { _, _, onBack, _ ->
            IppbCardQrScreen(onBack = onBack)
        }
        UsageTracker.composableRegistry[UsageTracker.ID_WA_CHECKER] = { _, _, _, _ ->
            WhatsappCheckerScreen()
        }

        setContent {
            JuganuaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        internalTargetTool.value = intent?.getStringExtra("TARGET_TOOL")
        internalSharedUris.value = intent?.getParcelableArrayListExtra("SHARED_URIS")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuganuaAppShell(requestedTool: String?, requestedUris: List<Uri>?, onHandled: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(1) }
    var activeCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var activeTool by rememberSaveable { mutableStateOf<String?>(null) }
    var sharedUris by remember { mutableStateOf<List<Uri>?>(null) }
    var toolToPin by remember { mutableStateOf<ToolAction?>(null) }

    LaunchedEffect(requestedTool, requestedUris) {
        if (requestedTool != null) {
            activeTool = requestedTool
            sharedUris = requestedUris
            onHandled()
        }
    }

    val isOnRoot = activeCategory == null && activeTool == null

    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            activeTool != null -> { activeTool = null; sharedUris = null }
            activeCategory != null -> activeCategory = null
            selectedTab != 1 -> selectedTab = 1
            else -> (context as? ComponentActivity)?.finish()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = isOnRoot,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text("JUGANUA STUDIO", modifier = Modifier.padding(24.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                NavigationDrawerItem(label = { Text("Toolkit Home") }, selected = selectedTab == 1 && activeCategory == null, onClick = { selectedTab = 1; activeCategory = null; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.GridView, null) })
                NavigationDrawerItem(label = { Text("Pinned Tools") }, selected = selectedTab == 0, onClick = { selectedTab = 0; activeCategory = null; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.PushPin, null) })
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                NavigationDrawerItem(label = { Text("App Settings") }, selected = selectedTab == 2, onClick = { selectedTab = 2; activeCategory = null; scope.launch { drawerState.close() } }, icon = { Icon(Icons.Default.Settings, null) })
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        val titleText = when {
                            activeTool != null -> activeTool!!
                            activeCategory != null -> activeCategory!!
                            selectedTab == 0 -> "Pinned"
                            selectedTab == 2 -> "Settings"
                            else -> "Juganua"
                        }
                        Text(titleText.uppercase(), fontWeight = FontWeight.Black, fontSize = 16.sp)
                    },
                    navigationIcon = {
                        if (isOnRoot) IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null) }
                        else IconButton(onClick = { if (activeTool != null) { activeTool = null; sharedUris = null } else activeCategory = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    }
                )
            },
            bottomBar = {
                if (isOnRoot) {
                    NavigationBar(modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                        NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Pinned") }, icon = { Icon(if (selectedTab == 0) Icons.Filled.PushPin else Icons.Outlined.PushPin, null) })
                        NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Toolkit") }, icon = { Icon(if (selectedTab == 1) Icons.Filled.GridView else Icons.Outlined.GridView, null) })
                        NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("Settings") }, icon = { Icon(if (selectedTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings, null) })
                    }
                }
            }
        ) { p ->
            Box(Modifier.padding(p).fillMaxSize()) {
                AnimatedContent(targetState = activeTool ?: activeCategory ?: selectedTab.toString(), label = "ZAxisNavigation") { target ->
                    when {
                        UsageTracker.composableRegistry.containsKey(target) -> {
                            UsageTracker.composableRegistry[target]?.invoke(
                                sharedUris?.firstOrNull(),
                                sharedUris,
                                { activeTool = null; sharedUris = null },
                                { toolId, data ->
                                    activeTool = toolId
                                    sharedUris = when(data) {
                                        is Uri -> listOf(data)
                                        is List<*> -> data.filterIsInstance<Uri>()
                                        else -> null
                                    }
                                }
                            )
                        }
                        target == "0" -> FavoritesGrid(onToolSelect = { activeTool = it })
                        target == "1" -> CategoryList(onCategoryClick = { activeCategory = it })
                        target == "DOCUMENT OPS" -> DocumentOpsScreen(onBack = { activeCategory = null }, onToolClick = { activeTool = it }, onToolLongClick = { toolToPin = it })
                        target == "IMAGE STUDIO" -> ImageStudioScreen(onBack = { activeCategory = null }, onToolClick = { activeTool = it }, onToolLongClick = { toolToPin = it })
                        target == "IDENTITY UTILS" -> IdentityUtilsScreen(onBack = { activeCategory = null }, onToolClick = { activeTool = it }, onToolLongClick = { toolToPin = it })
                        target == "POSTAL WORK" -> PostalWorkScreen(onBack = { activeCategory = null }, onToolClick = { activeTool = it }, onToolLongClick = { toolToPin = it })
                        target == "LABORATORY" -> LaboratoryScreen(onBack = { activeCategory = null }, onToolClick = { activeTool = it }, onToolLongClick = { toolToPin = it })
                        target == "OTHER TOOLS" -> OtherToolsScreen(onBack = { activeCategory = null }, onToolClick = { activeTool = it }, onToolLongClick = { toolToPin = it })
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Coming Soon") }
                    }
                }
            }
        }
    }

    // Pin Confirmation Dialog
    if (toolToPin != null) {
        val toolKey = toolToPin!!.uniqueKey ?: toolToPin!!.title
        val isPinned = UsageTracker.isPinned(context, toolKey)
        AlertDialog(
            onDismissRequest = { toolToPin = null },
            title = { Text(toolToPin!!.title) },
            text = { Text(if (isPinned) "Remove from favorites?" else "Add this tool to your Pinned tab?") },
            confirmButton = {
                TextButton(onClick = {
                    if (isPinned) UsageTracker.unpinFavorite(context, toolKey)
                    else UsageTracker.pinFavorite(context, PinnedToolInfo(toolToPin!!.title, toolKey))
                    toolToPin = null
                }) { Text(if (isPinned) "REMOVE" else "PIN") }
            },
            dismissButton = { TextButton(onClick = { toolToPin = null }) { Text("CANCEL") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernToolTile(tool: ToolAction, delayIndex: Int = 0, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(dampingRatio = 0.7f), label = "press")

    Surface(
        modifier = Modifier.aspectRatio(1f).scale(scale).combinedClickable(interactionSource, LocalIndication.current, onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(24.dp),
        color = tool.accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, tool.accentColor.copy(alpha = 0.2f))
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tool.icon, null, tint = tool.accentColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(tool.title, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}

@Composable
fun CategoryList(onCategoryClick: (String) -> Unit) {
    val categories = remember {
        listOf(
            CategoryGroup("DOCUMENT OPS", "PDF & OCR", Icons.Default.Description, Color(0xFF1B5E20)),
            CategoryGroup("IMAGE STUDIO", "AI Photo Editing", Icons.Default.AutoAwesome, Color(0xFF4A148C)),
            CategoryGroup("IDENTITY UTILS", "Official ID Portals", Icons.Default.Badge, Color(0xFFB71C1C)),
            CategoryGroup("POSTAL WORK", "Post & Banking", Icons.Default.LocalPostOffice, Color(0xFF0D47A1)),
            CategoryGroup("LABORATORY", "Beta Features", Icons.Default.Science, Color(0xFFE65100)),
            CategoryGroup("OTHER TOOLS", "Miscellaneous", Icons.Default.Category, Color(0xFF455A64))
        )
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        itemsIndexed(categories) { index, group ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onCategoryClick(group.category) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(group.icon, null, tint = group.accentColor, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.category, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(group.subtitle, fontSize = 12.sp, color = Color.Gray)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun FavoritesGrid(onToolSelect: (String) -> Unit) {
    val context = LocalContext.current
    val favoritesList = remember { UsageTracker.getFavorites(context).values.toList() }

    if (favoritesList.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Long press tools to pin them here", color = Color.Gray) }
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(favoritesList) { index, info ->
                ModernToolTile(tool = ToolAction(info.title, info.subtitle, Icons.Default.Star, info.key), delayIndex = index) { onToolSelect(info.key) }
            }
        }
    }
}

@Composable
fun SettingsScreen(currentAnim: String, onAnimChange: (String) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Settings Coming Soon") }
}