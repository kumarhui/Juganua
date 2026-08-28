package cvam.dignity.juganua.features.dashytools.bogascanner

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BogaScreen {
    HOME, PREVIEW, GALLERY_PREVIEW
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BogaScannerScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val scope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(BogaScreen.HOME) }

    var galleryItems by remember { mutableStateOf<List<BogaPdfUtils.GalleryItem>>(emptyList()) }
    var selectedGalleryItems by remember { mutableStateOf<Set<BogaPdfUtils.GalleryItem>>(emptySet()) }
    val selectionActive by remember { derivedStateOf { selectedGalleryItems.isNotEmpty() } }
    var previewGalleryItem by remember { mutableStateOf<BogaPdfUtils.GalleryItem?>(null) }

    var frontUri by remember { mutableStateOf<Uri?>(null) }
    var backUri by remember { mutableStateOf<Uri?>(null) }
    var normalUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isIdCardMode by remember { mutableStateOf(true) }

    var showDocTypeDialog by remember { mutableStateOf(false) }
    var showBackScanDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isVerticalLayout by remember { mutableStateOf(false) }

    var previewBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    fun loadGallery() {
        scope.launch(Dispatchers.IO) {
            val items = BogaPdfUtils.getSavedImages(context)
            withContext(Dispatchers.Main) {
                galleryItems = items
            }
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == BogaScreen.HOME) {
            loadGallery()
        }
    }

    fun resetPreviewState() {
        frontUri = null
        backUri = null
        normalUris = emptyList()
        previewBitmaps.forEach { bmp -> bmp.recycle() }
        previewBitmaps = emptyList()
    }

    BackHandler {
        when {
            showBackScanDialog -> showBackScanDialog = false
            showDocTypeDialog -> showDocTypeDialog = false
            selectionActive -> selectedGalleryItems = emptySet()
            currentScreen == BogaScreen.PREVIEW -> {
                resetPreviewState()
                currentScreen = BogaScreen.HOME
            }
            currentScreen == BogaScreen.GALLERY_PREVIEW -> {
                previewGalleryItem = null
                currentScreen = BogaScreen.HOME
            }
            else -> {
                onBack?.invoke()
            }
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        BogaScanner.processResult(result) { uris: List<Uri>, isFront: Boolean, isInitial: Boolean, isIdCard: Boolean ->
            if (uris.isNotEmpty()) {
                isIdCardMode = isIdCard
                if (isIdCard) {
                    if (isInitial) {
                        if (isFront) {
                            frontUri = uris.first()
                            showBackScanDialog = true
                        } else {
                            backUri = uris.first()
                            currentScreen = BogaScreen.PREVIEW
                        }
                    } else {
                        if (isFront) frontUri = uris.first() else backUri = uris.first()
                        currentScreen = BogaScreen.PREVIEW
                    }
                } else {
                    normalUris = uris
                    currentScreen = BogaScreen.PREVIEW
                }
            }
        }
    }

    LaunchedEffect(frontUri, backUri, normalUris, isVerticalLayout, currentScreen) {
        if (currentScreen == BogaScreen.PREVIEW) {
            isProcessing = true
            if (isIdCardMode) {
                frontUri?.let { front ->
                    val bmp = BogaPdfUtils.generateSinglePageImage(context, front, backUri, isVerticalLayout)
                    previewBitmaps = bmp?.let { bitmap -> listOf(bitmap) } ?: emptyList()
                }
            } else {
                if (normalUris.isNotEmpty()) {
                    previewBitmaps = BogaPdfUtils.generateNormalA4Images(context, normalUris)
                }
            }
            isProcessing = false
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!selectionActive && currentScreen == BogaScreen.HOME) {
                FloatingActionButton(onClick = { showDocTypeDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Document")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                BogaScreen.HOME -> {
                    if (galleryItems.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No scanned sheets", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tap + to create your first sheet.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(items = galleryItems, key = { item -> item.uri.toString() }) { item ->
                                val isSelected = selectedGalleryItems.contains(item)
                                DocumentCard(
                                    item = item,
                                    isSelected = isSelected,
                                    selectionActive = selectionActive,
                                    onClick = {
                                        if (selectionActive) {
                                            val newSet = selectedGalleryItems.toMutableSet()
                                            if (isSelected) newSet.remove(item) else newSet.add(item)
                                            selectedGalleryItems = newSet
                                        } else {
                                            previewGalleryItem = item
                                            currentScreen = BogaScreen.GALLERY_PREVIEW
                                        }
                                    },
                                    onLongClick = {
                                        val newSet = selectedGalleryItems.toMutableSet()
                                        newSet.add(item)
                                        selectedGalleryItems = newSet
                                    },
                                    onAction = { action ->
                                        when (action) {
                                            "Preview" -> {
                                                previewGalleryItem = item
                                                currentScreen = BogaScreen.GALLERY_PREVIEW
                                            }
                                            "Share" -> BogaPdfUtils.shareImages(context, listOf(item.uri))
                                            "Print" -> BogaPdfUtils.printImages(context, listOf(item.uri))
                                            "Delete" -> {
                                                selectedGalleryItems = setOf(item)
                                                showDeleteConfirmDialog = true
                                            }
                                            "Change Layout", "Rescan Front", "Rescan Back" -> {
                                                Toast.makeText(context, "Cannot modify saved documents.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                BogaScreen.PREVIEW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator()
                            Text("Rendering A4 Layout...", modifier = Modifier.padding(top = 8.dp))
                        } else {
                            previewBitmaps.forEach { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "A4 Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(210f / 297f)
                                        .border(1.dp, Color.LightGray),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            if (isIdCardMode) {
                                Button(
                                    onClick = { isVerticalLayout = !isVerticalLayout },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isVerticalLayout) "Switch to Side-by-Side" else "Switch to Vertical")
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        previewBitmaps.forEach { bmp ->
                                            val prefix = if (isIdCardMode) "IDCARD_" else "NORMAL_"
                                            BogaPdfUtils.saveBitmapToGallery(context, bmp, prefix)
                                        }
                                        Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                                        resetPreviewState()
                                        currentScreen = BogaScreen.HOME
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save to Gallery")
                            }

                            if (isIdCardMode && activity != null) {
                                OutlinedButton(
                                    onClick = { BogaScanner.rescanFront(activity, scannerLauncher) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Rescan Front")
                                }
                                if (backUri != null) {
                                    OutlinedButton(
                                        onClick = { BogaScanner.rescanBack(activity, scannerLauncher) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Rescan Back")
                                    }
                                }
                            }
                        }
                    }
                }
                BogaScreen.GALLERY_PREVIEW -> {
                    previewGalleryItem?.let { item ->
                        var bmp by remember { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(item.uri) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    @Suppress("DEPRECATION")
                                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, item.uri))
                                    } else {
                                        MediaStore.Images.Media.getBitmap(context.contentResolver, item.uri)
                                    }
                                    withContext(Dispatchers.Main) {
                                        bmp = bitmap
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (bmp == null) {
                                CircularProgressIndicator()
                            } else {
                                Image(
                                    bitmap = bmp!!.asImageBitmap(),
                                    contentDescription = "Full Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(210f / 297f)
                                        .padding(16.dp)
                                        .border(1.dp, Color.LightGray),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDocTypeDialog && activity != null) {
            AlertDialog(
                onDismissRequest = { showDocTypeDialog = false },
                title = { Text("Add Document") },
                text = { Text("Choose the type of document you want to scan.") },
                confirmButton = {
                    Button(onClick = {
                        showDocTypeDialog = false
                        BogaScanner.startInitialFrontScan(activity, scannerLauncher)
                    }) {
                        Text("ID Card")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showDocTypeDialog = false
                        BogaScanner.startNormalScan(activity, scannerLauncher)
                    }) {
                        Text("Normal Images")
                    }
                }
            )
        }

        if (showBackScanDialog && activity != null) {
            AlertDialog(
                onDismissRequest = { /* Force user choice */ },
                title = { Text("Scan Back Side?") },
                text = { Text("Do you want to scan the back of the ID card?") },
                confirmButton = {
                    Button(onClick = {
                        showBackScanDialog = false
                        BogaScanner.startInitialBackScan(activity, scannerLauncher)
                    }) {
                        Text("Scan Back")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = {
                        showBackScanDialog = false
                        currentScreen = BogaScreen.PREVIEW
                    }) {
                        Text("Skip")
                    }
                }
            )
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete Documents?") },
                text = { Text("Are you sure you want to delete ${selectedGalleryItems.size} selected items? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            BogaPdfUtils.deleteImages(context, selectedGalleryItems.map { it.uri })
                            selectedGalleryItems = emptySet()
                            showDeleteConfirmDialog = false
                            currentScreen = BogaScreen.HOME
                            loadGallery()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentCard(
    item: BogaPdfUtils.GalleryItem,
    isSelected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAction: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(item.uri) {
        val resolver = context.contentResolver
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                resolver.loadThumbnail(item.uri, Size(300, 300), null)
            } catch (e: Exception) { null }
        } else {
            null
        }
        bmp = bitmap
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(0.7f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .border(
                BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                RoundedCornerShape(8.dp)
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.LightGray))
            }

            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.isIdCard) "ID Card" else "Image",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            } else if (!selectionActive) {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More Options",
                            tint = Color.White,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf("Preview", "Share", "Print", "Delete").forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action) },
                                onClick = {
                                    expanded = false
                                    onAction(action)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}