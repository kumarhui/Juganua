package cvam.dignity.juganua.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cvam.dignity.juganua.ModernToolTile
import cvam.dignity.juganua.ToolAction
import cvam.dignity.juganua.common.UsageTracker

@Composable
fun PostalWorkScreen(
    onBack: () -> Unit,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolAction) -> Unit
) {
    val accentColor = Color(0xFF0D47A1)
    val tools = remember {
        listOf(
            ToolAction("IPPB QR", "Barcode Utility", Icons.Default.QrCode, UsageTracker.ID_IPPB_CARD_QR, accentColor),
            // NEW TOOL ADDED
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
            ModernToolTile(tool = tool, delayIndex = index, onLongClick = { onToolLongClick(tool) }) {
                if (tool.uniqueKey != null) onToolClick(tool.uniqueKey)
            }
        }
    }
}
