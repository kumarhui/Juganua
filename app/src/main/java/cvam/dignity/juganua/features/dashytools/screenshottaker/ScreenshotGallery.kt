package cvam.dignity.juganua.features.dashytools.screenshottaker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val BrandPurple = Color(0xFF8E24AA)
private val TextDark = Color(0xFF0F172A)
private val BorderColor = Color(0xFFE2E8F0)

/**
 * Dedicated Screenshot Gallery Composable.
 * Features 1:1 square thumbnails, single-tap full-screen image preview,
 * long-press selection mode, drag selection across grid cells, and range selection.
 */
@Composable
fun ScreenshotGallery(
    galleryItems: List<ScreenshotItem>,
    onDeleteSelected: (List<ScreenshotItem>) -> Unit,
    onToggleSelectAll: (Boolean) -> Unit
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var anchorIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var isRangeMode by rememberSaveable { mutableStateOf(false) }

    var previewItem by remember { mutableStateOf<ScreenshotItem?>(null) }

    // Map storing bounding boxes of items relative to the parent LazyVerticalGrid for drag-selection
    val itemBoundsMap = remember { mutableStateMapOf<Int, Rect>() }

    fun getStableId(item: ScreenshotItem): String = item.file.absolutePath

    fun toggleSelection(item: ScreenshotItem, index: Int) {
        val id = getStableId(item)
        val currentSelected = selectedIds.toMutableSet()
        if (currentSelected.contains(id)) {
            currentSelected.remove(id)
        } else {
            currentSelected.add(id)
            anchorIndex = index
        }
        selectedIds = currentSelected
        if (currentSelected.isEmpty()) {
            selectionMode = false
            isRangeMode = false
        }
    }

    fun handleLongPress(item: ScreenshotItem, index: Int) {
        if (!selectionMode) {
            selectionMode = true
        }
        val id = getStableId(item)
        val currentSelected = selectedIds.toMutableSet()
        if (!currentSelected.contains(id)) {
            currentSelected.add(id)
        }
        selectedIds = currentSelected
        anchorIndex = index
    }

    fun handleRangeSelect(targetIndex: Int) {
        val start = anchorIndex ?: targetIndex
        val minIdx = minOf(start, targetIndex)
        val maxIdx = maxOf(start, targetIndex)

        val currentSelected = selectedIds.toMutableSet()
        for (i in minIdx..maxIdx) {
            if (i in galleryItems.indices) {
                currentSelected.add(getStableId(galleryItems[i]))
            }
        }
        selectedIds = currentSelected
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size} Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { isRangeMode = !isRangeMode },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isRangeMode) BrandPurple.copy(alpha = 0.25f) else Color.Transparent
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (isRangeMode) "Cancel Range" else "Range",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (selectedIds.size == galleryItems.size) {
                                        selectedIds = emptySet()
                                        selectionMode = false
                                        isRangeMode = false
                                    } else {
                                        selectedIds = galleryItems.map { getStableId(it) }.toSet()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val itemsToDelete = galleryItems.filter { selectedIds.contains(getStableId(it)) }
                                    onDeleteSelected(itemsToDelete)
                                    selectedIds = emptySet()
                                    selectionMode = false
                                    isRangeMode = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                    anchorIndex = null
                                    isRangeMode = false
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection Mode", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (isRangeMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BrandPurple.copy(alpha = 0.15f))
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Range Mode Active: Tap target item to select range",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurple
                            )
                        }
                    }
                }
            }
        }

        if (galleryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No screenshots captured yet.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(selectionMode, galleryItems) {
                        if (selectionMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val hitIndex = itemBoundsMap.entries.firstOrNull { it.value.contains(offset) }?.key
                                    if (hitIndex != null && hitIndex in galleryItems.indices) {
                                        val id = getStableId(galleryItems[hitIndex])
                                        val set = selectedIds.toMutableSet()
                                        set.add(id)
                                        selectedIds = set
                                        anchorIndex = hitIndex
                                    }
                                },
                                onDrag = { change, _ ->
                                    val offset = change.position
                                    val hitIndex = itemBoundsMap.entries.firstOrNull { it.value.contains(offset) }?.key
                                    if (hitIndex != null && hitIndex in galleryItems.indices) {
                                        val id = getStableId(galleryItems[hitIndex])
                                        if (!selectedIds.contains(id)) {
                                            val set = selectedIds.toMutableSet()
                                            set.add(id)
                                            selectedIds = set
                                        }
                                    }
                                },
                                onDragEnd = { }
                            )
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp)
            ) {
                itemsIndexed(galleryItems, key = { _, item -> getStableId(item) }) { index, item ->
                    val isSelected = selectedIds.contains(getStableId(item))

                    SquareThumbnailCard(
                        item = item,
                        index = index,
                        isSelected = isSelected,
                        selectionMode = selectionMode,
                        onPositionedInGrid = { bounds ->
                            itemBoundsMap[index] = bounds
                        },
                        onClick = {
                            if (isRangeMode) {
                                handleRangeSelect(index)
                                isRangeMode = false
                            } else if (selectionMode) {
                                toggleSelection(item, index)
                            } else {
                                previewItem = item
                            }
                        },
                        onLongClick = {
                            handleLongPress(item, index)
                        }
                    )
                }
            }
        }
    }

    if (previewItem != null) {
        CroppedImagePreviewDialog(
            item = previewItem!!,
            onDismiss = { previewItem = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareThumbnailCard(
    item: ScreenshotItem,
    index: Int,
    isSelected: Boolean,
    selectionMode: Boolean,
    onPositionedInGrid: (Rect) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(item.file.absolutePath) {
        withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val rawBmp = BitmapFactory.decodeFile(item.file.absolutePath, options)
            if (rawBmp != null) {
                val cropBounds = ScreenshotManager.getActiveCropBounds(context)
                bmp = ScreenshotManager.cropBitmap(rawBmp, cropBounds)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // 1:1 Square Thumbnail
            .onGloballyPositioned { coordinates ->
                val pos = coordinates.positionInParent()
                val size = coordinates.size
                val rect = Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
                onPositionedInGrid(rect)
            }
            .border(
                BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) BrandPurple else BorderColor),
                RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bmp != null) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.LightGray))
            }

            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(java.util.Locale.getDefault(), "%03d", index + 1),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CroppedImagePreviewDialog(
    item: ScreenshotItem,
    onDismiss: () -> Unit
) {
    var fullCroppedBmp by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(item.file.absolutePath) {
        withContext(Dispatchers.IO) {
            val rawBmp = BitmapFactory.decodeFile(item.file.absolutePath)
            if (rawBmp != null) {
                val cropBounds = ScreenshotManager.getActiveCropBounds(context)
                fullCroppedBmp = ScreenshotManager.cropBitmap(rawBmp, cropBounds)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                contentAlignment = Alignment.Center
            ) {
                if (fullCroppedBmp != null) {
                    Image(
                        bitmap = fullCroppedBmp!!.asImageBitmap(),
                        contentDescription = item.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, BorderColor, RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}
