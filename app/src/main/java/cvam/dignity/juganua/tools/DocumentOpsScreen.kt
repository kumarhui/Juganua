package cvam.dignity.juganua.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cvam.dignity.juganua.ModernToolTile
import cvam.dignity.juganua.ToolAction
import cvam.dignity.juganua.common.UsageTracker

@Composable
fun DocumentOpsScreen(
    onBack: () -> Unit,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolAction) -> Unit
) {
    val accentColor = Color(0xFF1B5E20)
    val tools = remember {
        listOf(
            ToolAction("PDF Unlocker", "Security Removal", Icons.Default.LockOpen, UsageTracker.ID_PDF_UNLOCKER, accentColor),
            ToolAction("Merge PDF", "Join Files", Icons.Default.MergeType, UsageTracker.ID_MERGE_PDF, accentColor),
            // NEW TOOL ADDED HERE
            ToolAction("ID Splitter", "Crop & Print", Icons.Default.FilterFrames, UsageTracker.ID_ID_CARD_SPLITTER, accentColor)
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(tools) { index, tool ->
            ModernToolTile(
                tool = tool,
                delayIndex = index,
                onLongClick = { onToolLongClick(tool) }
            ) {
                if (tool.uniqueKey != null) onToolClick(tool.uniqueKey)
            }
        }
    }
}