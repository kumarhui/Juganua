package cvam.dignity.juganua.features.dashytools.neonpen

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
private val StatusRequiredBg = Color(0xFFFEE2E2)
private val StatusRequiredText = Color(0xFFB91C1C)

private const val PREFS_NAME = "neon_pen_prefs"
private const val KEY_DURATION = "disappear_duration_sec"
private const val KEY_DURATION_MS = "disappear_duration_ms"
private const val KEY_AUTO_DISAPPEAR = "auto_disappear_enabled"

private const val MIN_MS = 500L
private const val STEP_MS = 500L
private const val MAX_PROGRESS = 11f

/**
 * Dedicated settings screen for Neon Pen Writer in DashyHub.
 * Manages Display over other apps, Accessibility service, and drawing duration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonPenSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceRunning by remember { mutableStateOf(NeonOverlayService.isServiceRunning) }
    var hasOverlayPermission by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var hasAccessibilityPermission by remember {
        mutableStateOf(PermissionUtils.isAccessibilityServiceEnabled(context, NeonAccessibilityService::class.java))
    }

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = NeonOverlayService.isServiceRunning
                hasOverlayPermission = PermissionUtils.hasOverlayPermission(context)
                hasAccessibilityPermission = PermissionUtils.isAccessibilityServiceEnabled(context, NeonAccessibilityService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Neon Pen Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionCard(
                    modifier = Modifier.weight(1f),
                    title = "Display Over Apps",
                    isGranted = hasOverlayPermission,
                    onActionClick = { PermissionUtils.openOverlaySettings(context) }
                )
                PermissionCard(
                    modifier = Modifier.weight(1f),
                    title = "Accessibility",
                    isGranted = hasAccessibilityPermission,
                    onActionClick = { PermissionUtils.openAccessibilitySettings(context) }
                )
            }

            DurationCard(prefs = prefs)

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isServiceRunning) {
                        NeonOverlayService.stopService(context)
                    } else if (hasOverlayPermission) {
                        NeonOverlayService.startService(context)
                    }
                    isServiceRunning = NeonOverlayService.isServiceRunning
                },
                enabled = isServiceRunning || hasOverlayPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) Color(0xFF0F172A) else NeonPink,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isServiceRunning) "Stop Floating Pen Service" else "Start Floating Pen Service",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    modifier: Modifier = Modifier,
    title: String,
    isGranted: Boolean,
    onActionClick: () -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isGranted) StatusActiveBg else StatusRequiredBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isGranted) "Enabled âœ“" else "Required",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) StatusActiveText else StatusRequiredText
                    )
                }
            }

            OutlinedButton(
                onClick = onActionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isGranted) "Manage" else "Allow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DurationCard(prefs: SharedPreferences) {
    var isAutoDisappear by remember {
        mutableStateOf(prefs.getBoolean(KEY_AUTO_DISAPPEAR, true))
    }

    var currentMs by remember {
        mutableStateOf(
            if (prefs.contains(KEY_DURATION_MS)) {
                prefs.getLong(KEY_DURATION_MS, 1000L)
            } else if (prefs.contains(KEY_DURATION)) {
                prefs.getInt(KEY_DURATION, 1) * 1000L
            } else {
                1000L
            }
        )
    }

    var sliderProgress by remember {
        mutableFloatStateOf(msToProgressFloat(currentMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Auto-erase delay",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isAutoDisappear) formatDurationMs(currentMs) else "OFF",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAutoDisappear) NeonPink else TextMuted,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Switch(
                        checked = isAutoDisappear,
                        onCheckedChange = { checked ->
                            isAutoDisappear = checked
                            prefs.edit().putBoolean(KEY_AUTO_DISAPPEAR, checked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonPink
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Text(
                text = "Delay before trailing stroke disappears, or disable to keep drawing visible.",
                fontSize = 11.sp,
                color = TextMuted
            )

            Slider(
                value = sliderProgress,
                onValueChange = { progress ->
                    sliderProgress = progress
                    currentMs = progressToMsLong(progress)
                    if (isAutoDisappear) {
                        prefs.edit().putLong(KEY_DURATION_MS, currentMs).apply()
                    }
                },
                valueRange = 0f..MAX_PROGRESS,
                steps = (MAX_PROGRESS - 1).toInt(),
                enabled = isAutoDisappear,
                colors = SliderDefaults.colors(
                    thumbColor = NeonPink,
                    activeTrackColor = NeonPink
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "0.5s", fontSize = 11.sp, color = TextMuted)
                Text(text = "6.0s", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

private fun progressToMsLong(progress: Float): Long {
    val step = progress.coerceIn(0f, MAX_PROGRESS).toInt()
    return MIN_MS + (step * STEP_MS)
}

private fun msToProgressFloat(ms: Long): Float {
    val clampedMs = ms.coerceIn(MIN_MS, MIN_MS + (MAX_PROGRESS.toLong() * STEP_MS))
    return ((clampedMs - MIN_MS) / STEP_MS).toFloat()
}

private fun formatDurationMs(ms: Long): String {
    return if (ms % 1000L == 0L) {
        val sec = ms / 1000L
        if (sec == 1L) "1 second" else "$sec seconds"
    } else {
        val secFloat = ms / 1000.0
        "${secFloat}s"
    }
}
