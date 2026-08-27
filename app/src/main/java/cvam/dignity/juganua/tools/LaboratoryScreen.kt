package cvam.dignity.juganua.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cvam.dignity.juganua.ToolAction

@Composable
fun LaboratoryScreen(
    onBack: () -> Unit, // FIXED: Added to match MainActivity call site
    onToolClick: (String) -> Unit,
    onToolLongClick: (ToolAction) -> Unit
) {
    // UI is now a clean placeholder for future experimental features
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "COMING SOON",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Experimental features are being developed.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(24.dp))

            // Optional visual element
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}