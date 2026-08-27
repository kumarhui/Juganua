package cvam.dignity.juganua.tools

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cvam.dignity.juganua.ModernToolTile
import cvam.dignity.juganua.ToolAction
import cvam.dignity.juganua.common.UsageTracker

@Composable
fun IdentityUtilsScreen(
    onBack: () -> Unit,
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolAction) -> Unit
) {
    val context = LocalContext.current
    val accentColor = Color(0xFFB71C1C)

    // Only keeping functional tools (Internal Scanner and Portal links)
    val tools = remember {
        listOf(
            ToolAction("Aadhaar QR", "Internal Scan", Icons.Default.QrCodeScanner, UsageTracker.ID_AADHAAR_QR, accentColor),
            ToolAction("Check Status", "Enrollment", Icons.Default.AssignmentInd, UsageTracker.ID_AADHAAR_STATUS, accentColor, url = "https://myaadhaar.uidai.gov.in/CheckAadhaarStatus/en"),
            ToolAction("UIDAI Login", "Web Portal", Icons.Default.Login, UsageTracker.ID_AADHAAR_LOGIN, accentColor, url = "https://myaadhaar.uidai.gov.in/")
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
                if (tool.url != null) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tool.url)))
                } else if (tool.uniqueKey != null) {
                    onToolClick(tool.uniqueKey)
                }
            }
        }
    }
}