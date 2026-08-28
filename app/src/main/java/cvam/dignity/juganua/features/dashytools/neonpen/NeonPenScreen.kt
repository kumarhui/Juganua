package cvam.dignity.juganua.features.dashytools.neonpen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val NeonPink = Color(0xFFFF007F)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)
private val StatusActiveBg = Color(0xFFD1FAE5)
private val StatusActiveText = Color(0xFF047857)

/**
 * Main Jetpack Compose screen for Neon Pen Writer in DashyHub.
 * Displays service status, quick action toggles, and connects to settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonPenScreen(
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceRunning by remember { mutableStateOf(NeonOverlayService.isServiceRunning) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = NeonOverlayService.isServiceRunning
                hasOverlayPermission = PermissionUtils.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        NeonOverlayService.onServiceStateChanged = { running ->
            isServiceRunning = running
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            NeonOverlayService.onServiceStateChanged = null
        }
    }

    Scaffold(
        topBar = { }   ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(isServiceRunning = isServiceRunning)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Floating Overlay Features",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextDark
                    )
                    Text(
                        text = "â€¢ Draw on top of any app with glowing neon strokes\n" +
                                "â€¢ Launch a floating paper card to write notes\n" +
                                "â€¢ Auto-disappearing trail with custom duration\n" +
                                "â€¢ Single-tap to toggle drawing, hold to open tools palette",
                        fontSize = 13.sp,
                        color = TextMuted,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isServiceRunning) {
                        NeonOverlayService.stopService(context)
                    } else {
                        if (hasOverlayPermission) {
                            NeonOverlayService.startService(context)
                        } else {
                            onNavigateToSettings()
                        }
                    }
                    isServiceRunning = NeonOverlayService.isServiceRunning
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Color(0xFF0F172A) else NeonPink,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isServiceRunning) "Stop Floating Pen" else if (hasOverlayPermission) "Start Floating Pen" else "Grant Permissions in Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusCard(isServiceRunning: Boolean) {
    val borderColor by animateColorAsState(
        targetValue = if (isServiceRunning) Color(0xFFA7F3D0) else BorderColor,
        animationSpec = tween(300),
        label = "statusBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Floating Pen Status",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isServiceRunning) StatusActiveBg else Color(0xFFF1F5F9))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isServiceRunning) "Active â—" else "Stopped",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) StatusActiveText else TextMuted
                    )
                }
            }

            Text(
                text = if (isServiceRunning)
                    "Floating tool button is currently visible above other apps."
                else
                    "Floating overlay service is not running. Tap Start Floating Pen to activate.",
                fontSize = 13.sp,
                color = TextMuted
            )
        }
    }
}
