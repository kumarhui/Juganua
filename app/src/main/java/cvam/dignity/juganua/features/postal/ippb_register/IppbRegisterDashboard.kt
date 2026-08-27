package cvam.dignity.juganua.features.postal.ippb_register

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IppbRegisterDashboard(
    onFillForm: () -> Unit,
    onViewVault: () -> Unit,
    onOpenSettings: () -> Unit,
    isCameraPermissionGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Setup Section (Hides when granted) ---
        if (!isCameraPermissionGranted) {
            ModernDashboardCard(
                title = "Setup Scanner",
                sub = "Grant camera permission to enable OCR",
                icon = Icons.Default.PhotoCamera,
                color = Color(0xFFE91E63),
                onClick = onRequestPermission
            )
        }

        // --- Main Tools ---
        ModernDashboardCard(
            title = "New Registration",
            sub = "Fill digital onboarding form",
            icon = Icons.Default.AppRegistration,
            color = Color(0xFF0D47A1),
            onClick = onFillForm
        )

        ModernDashboardCard(
            title = "Records Vault",
            sub = "Browse search database",
            icon = Icons.Default.Storage,
            color = Color(0xFF2E7D32),
            onClick = onViewVault
        )

        ModernDashboardCard(
            title = "Tool Settings",
            sub = "Configure tool & backups",
            icon = Icons.Default.Settings,
            color = Color(0xFF455A64),
            onClick = onOpenSettings
        )

        Spacer(Modifier.weight(1f))
        Text("Drafts are saved automatically as you type.", fontSize = 10.sp, color = Color.Gray)
    }
}