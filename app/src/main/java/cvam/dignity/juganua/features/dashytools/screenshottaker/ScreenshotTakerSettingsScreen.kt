package cvam.dignity.juganua.features.dashytools.screenshottaker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cvam.dignity.juganua.features.dashytools.settings.JuganuaAccessibilityService
import cvam.dignity.juganua.features.dashytools.common.SharedPermissionManager

private val BrandPurple = Color(0xFF8E24AA)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val BorderColor = Color(0xFFE2E8F0)
private val StatusActiveText = Color(0xFF047857)
private val StatusRequiredText = Color(0xFFB91C1C)

private const val PREFS_NAME = "screenshot_taker_prefs"

/**
 * Settings Screen for Testbook Screenshot Taker.
 * Configures Default Shot Count (3), Auto-Crop Cut-off, and Auto-Advance Delay (0.3s).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotTakerSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var isAccessibilityEnabled by remember {
        mutableStateOf(JuganuaAccessibilityService.isServiceEnabled(context))
    }
    var hasOverlayPermission by remember {
        mutableStateOf(SharedPermissionManager.hasOverlayPermission(context))
    }
    var isFloatingShowing by remember {
        mutableStateOf(JuganuaAccessibilityService.isScreenshotControlVisible())
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = JuganuaAccessibilityService.isServiceEnabled(context)
                hasOverlayPermission = SharedPermissionManager.hasOverlayPermission(context)
                isFloatingShowing = JuganuaAccessibilityService.isScreenshotControlVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screenshot Taker Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accessibility Service", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Text(
                            text = if (isAccessibilityEnabled) "âœ“ Enabled" else "âœ• Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isAccessibilityEnabled) StatusActiveText else StatusRequiredText
                        )
                    }

                    Text(
                        text = "Allows Desi Hub to take full screen captures and perform tap gestures during multi-capture sequences.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Button(
                        onClick = { SharedPermissionManager.openAccessibilitySettings(context) },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                    ) {
                        Text(if (isAccessibilityEnabled) "Manage Accessibility" else "Enable Accessibility", fontSize = 12.sp)
                    }
                }
            }

            DefaultShotCountCard(context, prefs)

            AutoCropSettingsCard(prefs)

            AutoAdvanceSettingsCard(prefs)
        }
    }
}

@Composable
private fun DefaultShotCountCard(context: Context, prefs: SharedPreferences) {
    var defaultShotsInput by remember {
        mutableStateOf(prefs.getInt(ScreenshotManager.KEY_DEFAULT_SHOT_COUNT, ScreenshotManager.DEFAULT_SHOT_COUNT).toString())
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Default Number of Shots", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
            Text("Set initial screenshot count loaded when opening floating toolbar (Default: 3)", fontSize = 11.sp, color = TextMuted)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = defaultShotsInput,
                    onValueChange = { defaultShotsInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(50.dp)
                )

                Button(
                    onClick = {
                        val count = defaultShotsInput.toIntOrNull()
                        if (count != null && count in 1..100) {
                            prefs.edit().putInt(ScreenshotManager.KEY_DEFAULT_SHOT_COUNT, count).apply()
                            Toast.makeText(context, "Default shots saved: $count", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Enter a valid count (1-100)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text("Save", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AutoCropSettingsCard(prefs: SharedPreferences) {
    var isAutoCrop by remember { mutableStateOf(prefs.getBoolean(ScreenshotManager.KEY_AUTO_CROP, true)) }
    var topPct by remember { mutableFloatStateOf(prefs.getFloat(ScreenshotManager.KEY_CROP_TOP_PCT, 15f)) }
    var bottomPct by remember { mutableFloatStateOf(prefs.getFloat(ScreenshotManager.KEY_CROP_BOTTOM_PCT, 15f)) }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Crop Screenshots", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Switch(
                    checked = isAutoCrop,
                    onCheckedChange = {
                        isAutoCrop = it
                        prefs.edit().putBoolean(ScreenshotManager.KEY_AUTO_CROP, it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = BrandPurple)
                )
            }

            if (isAutoCrop) {
                Text("Top Crop Cut-off: ${topPct.toInt()}%", fontSize = 11.sp, color = TextMuted)
                Slider(
                    value = topPct,
                    onValueChange = {
                        topPct = it
                        prefs.edit().putFloat(ScreenshotManager.KEY_CROP_TOP_PCT, it).apply()
                    },
                    valueRange = 0f..40f,
                    colors = SliderDefaults.colors(thumbColor = BrandPurple, activeTrackColor = BrandPurple)
                )

                Text("Bottom Crop Cut-off: ${bottomPct.toInt()}%", fontSize = 11.sp, color = TextMuted)
                Slider(
                    value = bottomPct,
                    onValueChange = {
                        bottomPct = it
                        prefs.edit().putFloat(ScreenshotManager.KEY_CROP_BOTTOM_PCT, it).apply()
                    },
                    valueRange = 0f..40f,
                    colors = SliderDefaults.colors(thumbColor = BrandPurple, activeTrackColor = BrandPurple)
                )
            }
        }
    }
}

@Composable
private fun AutoAdvanceSettingsCard(prefs: SharedPreferences) {
    var isAutoAdvance by remember { mutableStateOf(prefs.getBoolean(ScreenshotManager.KEY_AUTO_ADVANCE, true)) }
    var tapYPct by remember { mutableFloatStateOf(prefs.getFloat(ScreenshotManager.KEY_TAP_Y_PCT, 80f)) }
    var delayMs by remember { mutableFloatStateOf(prefs.getLong(ScreenshotManager.KEY_DELAY_MS, ScreenshotManager.DEFAULT_DELAY_MS).toFloat()) }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Advance Tap Gesture", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Switch(
                    checked = isAutoAdvance,
                    onCheckedChange = {
                        isAutoAdvance = it
                        prefs.edit().putBoolean(ScreenshotManager.KEY_AUTO_ADVANCE, it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = BrandPurple)
                )
            }

            if (isAutoAdvance) {
                Text("Tap Y Height Position: ${tapYPct.toInt()}%", fontSize = 11.sp, color = TextMuted)
                Slider(
                    value = tapYPct,
                    onValueChange = {
                        tapYPct = it
                        prefs.edit().putFloat(ScreenshotManager.KEY_TAP_Y_PCT, it).apply()
                    },
                    valueRange = 10f..90f,
                    colors = SliderDefaults.colors(thumbColor = BrandPurple, activeTrackColor = BrandPurple)
                )

                Text("Multi-Capture Delay: ${delayMs.toInt()} ms (Default: 300 ms / 0.3s)", fontSize = 11.sp, color = TextMuted)
                Slider(
                    value = delayMs,
                    onValueChange = {
                        delayMs = it
                        prefs.edit().putLong(ScreenshotManager.KEY_DELAY_MS, it.toLong()).apply()
                    },
                    valueRange = 100f..3000f,
                    steps = 29,
                    colors = SliderDefaults.colors(thumbColor = BrandPurple, activeTrackColor = BrandPurple)
                )
            }
        }
    }
}

