package cvam.dignity.juganua.tools

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cvam.dignity.juganua.ModernToolTile
import cvam.dignity.juganua.ToolAction
import cvam.dignity.juganua.common.UsageTracker

@Composable
fun OtherToolsScreen(
    onBack: () -> Unit,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolAction) -> Unit
) {
    val context = LocalContext.current
    val accentColor = Color(0xFF455A64)

    val tools = remember {
        listOf(
            ToolAction(
                title = "WA Checker",
                subtitle = "Direct Message",
                icon = Icons.Default.Whatsapp,
                uniqueKey = UsageTracker.ID_WA_CHECKER,
                accentColor = accentColor
                // destination removed: WhatsappCheckerScreen is now a Composable
                // handled by uniqueKey in MainActivity
            ),
            ToolAction(
                title = "Offline Share",
                subtitle = "Web File Server",
                icon = Icons.Default.WifiTethering,
                uniqueKey = UsageTracker.ID_OFFLINE_SHARE,
                accentColor = accentColor
            )
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
                val key = tool.uniqueKey ?: ""
                // Use registry callback for both WA Checker and Offline Share
                if (UsageTracker.composableRegistry.containsKey(key)) {
                    onToolClick(key)
                }
            }
        }
    }
}