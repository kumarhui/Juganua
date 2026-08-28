package cvam.dignity.juganua.features.idcardsplitter

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun IdCompositionGrid(
    isStacked: Boolean,
    slots: Map<PrintPosition, SlotData>,
    draggingPos: PrintPosition?,
    dragOffset: Offset,
    slotBounds: MutableMap<PrintPosition, Rect>,
    currentHoverTarget: PrintPosition?,
    onSlotClick: (PrintPosition) -> Unit,
    onClearSlot: (PrintPosition) -> Unit
) {
    val positions = if (isStacked) {
        listOf(PrintPosition.POS_1, PrintPosition.POS_2, PrintPosition.POS_3, PrintPosition.POS_4, PrintPosition.POS_5)
    } else {
        PrintPosition.entries
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("ID COMPOSITION GRID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)

        val rows = (positions.size + 1) / 2
        repeat(rows) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(2) { colIndex ->
                    val index = rowIndex * 2 + colIndex
                    if (index < positions.size) {
                        val pos = positions[index]
                        val isBeingDragged = draggingPos == pos
                        val isHovered = currentHoverTarget == pos

                        IdSlotItem(
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { slotBounds[pos] = it.boundsInWindow() }
                                .zIndex(if (isBeingDragged) 10f else 1f)
                                .alpha(if (isBeingDragged) 0.1f else 1f),
                            pos = pos,
                            data = slots[pos],
                            isHovered = isHovered,
                            isStacked = isStacked,
                            onClick = { if (draggingPos == null) onSlotClick(pos) },
                            onClear = { if (draggingPos == null) onClearSlot(pos) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun IdSlotItem(
    modifier: Modifier,
    pos: PrintPosition,
    data: SlotData?,
    isHovered: Boolean,
    isStacked: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    val tintColor by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        label = "hover"
    )

    Surface(
        modifier = modifier
            .aspectRatio(2.5f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isHovered) 2.dp else 1.dp,
            color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.background(tintColor)) {
            if (data != null) {
                Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.Center) {
                    Image(data.front.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(4.dp))
                    Image(data.back.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp).background(Color.White.copy(0.8f), CircleShape)
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isHovered) Icons.Default.MoveToInbox else Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = if (isHovered) MaterialTheme.colorScheme.primary else Color.LightGray
                    )
                    val label = when {
                        isStacked && pos == PrintPosition.POS_3 -> "CENTER"
                        isStacked -> "QUAD ${pos.ordinal + 1}"
                        else -> "ROW ${pos.ordinal + 1}"
                    }
                    Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (isHovered) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }
        }
    }
}

@Composable
fun FinalPreviewCard(
    bitmap: Bitmap?,
    isGenerating: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LIVE PAGE PREVIEW", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, color = Color.Gray)

            Spacer(Modifier.height(16.dp))

            // Realistic Paper Shadow Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .shadow(4.dp, RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(0.5.dp, Color.LightGray.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                } else if (bitmap != null) {
                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Layers, null, Modifier.size(40.dp), Color.LightGray)
                        Text("Preview will appear here", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = bitmap != null && !isGenerating,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SAVE")
                }
                Button(
                    onClick = onShare,
                    enabled = bitmap != null && !isGenerating,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SHARE")
                }
            }
        }
    }
}

@Composable
fun ResultItem(modifier: Modifier, label: String, bitmap: Bitmap?) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().aspectRatio(1.58f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(8.dp)) }
            }
        }
    }
}

@Composable
fun FlipControls(onHorizontalFlip: () -> Unit, onVerticalFlip: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        IconButton(onClick = onHorizontalFlip, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(40.dp)) {
            Icon(Icons.Default.Flip, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onVerticalFlip, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(40.dp)) {
            Icon(Icons.Default.Flip, null, modifier = Modifier.size(20.dp).graphicsLayer(rotationZ = 90f), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PreviewCard(bitmap: Bitmap?, loading: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.58f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            bitmap?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit) }
        }
    }
}