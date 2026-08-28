package cvam.dignity.juganua.features.passportphoto

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun PassportCompositionOverview(
    paperSize: PhotoPaperSize,
    slots: Map<Int, Bitmap>,
    draggingIndex: Int,
    dragOffset: Offset,
    onSlotClick: (Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onDownloadSlot: (Int) -> Unit,
    onPositionCalculated: (Int, Rect) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) { rowIdx ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { colIdx ->
                    val idx = (rowIdx * 3) + colIdx
                    val isDragging = draggingIndex == idx

                    PassportSlotBox(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { onPositionCalculated(idx, it.boundsInWindow()) }
                            .zIndex(if (isDragging) 10f else 1f)
                            .offset {
                                if (isDragging) IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                else IntOffset.Zero
                            }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart(idx) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            },
                        bitmap = slots[idx],
                        label = "Slot ${idx + 1}",
                        isDragging = isDragging,
                        onClick = { if (draggingIndex == -1) onSlotClick(idx) },
                        onClear = { if (draggingIndex == -1) onClearSlot(idx) },
                        onDownload = { onDownloadSlot(idx) }
                    )
                }
            }
        }
    }
}

@Composable
fun PassportSlotBox(
    modifier: Modifier = Modifier,
    bitmap: Bitmap?,
    label: String,
    isDragging: Boolean = false,
    onClick: () -> Unit,
    onClear: () -> Unit,
    onDownload: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (isDragging) 0.6f else 1.0f
            )

            if (!isDragging) {
                Box(Modifier.fillMaxSize()) {
                    // Small Download Label (Top Left)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clickable { onDownload() },
                        color = Color.White.copy(0.8f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("DL", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    // Delete Slot Icon (Top Right)
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.White.copy(0.8f), CircleShape)
                            .size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Add, null, tint = Color.LightGray)
                Text(label, fontSize = 10.sp, color = Color.LightGray, fontWeight = FontWeight.Black)
            }
        }
    }
}

