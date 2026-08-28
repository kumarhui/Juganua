package cvam.dignity.juganua.features.dashytools.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cvam.dignity.juganua.features.dashytools.common.SharedPermissionManager

private val EnabledGreen = Color(0xFF16A34A)
private val DisabledRed = Color(0xFFDC2626)

@Composable
fun JuganuaToolsSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityEnabled by remember {
        mutableStateOf(false)
    }

    var overlayEnabled by remember {
        mutableStateOf(false)
    }

    fun refreshPermissions() {
        accessibilityEnabled =
            SharedPermissionManager.isAccessibilityServiceEnabled(context)

        overlayEnabled =
            SharedPermissionManager.hasOverlayPermission(context)
    }

    DisposableEffect(lifecycleOwner) {

        refreshPermissions()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Tool Permissions",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Permissions required by Juganua's floating tools.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        PermissionCard(
            title = "Accessibility Service",
            description = "Required by Neon Pen and Screenshot Taker.",
            enabled = accessibilityEnabled,
            icon = {
                Icon(
                    Icons.Default.Accessibility,
                    contentDescription = null
                )
            },
            onEnable = {
                SharedPermissionManager.openAccessibilitySettings(context)
            }
        )

        PermissionCard(
            title = "Display over other apps",
            description = "Required for floating Neon Pen and Screenshot controls.",
            enabled = overlayEnabled,
            icon = {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null
                )
            },
            onEnable = {
                SharedPermissionManager.openOverlaySettings(context)
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Text(
                    text = "Tool Requirements",
                    style = MaterialTheme.typography.titleMedium
                )

                RequirementRow(
                    "Neon Pen",
                    "Accessibility + Overlay"
                )

                RequirementRow(
                    "Screenshot Taker",
                    "Accessibility + Overlay"
                )

                RequirementRow(
                    "Passport Photo",
                    "No special permission"
                )

                RequirementRow(
                    "Boga Scanner",
                    "Camera / Documents"
                )

                RequirementRow(
                    "WhatsApp Checker",
                    "Internet"
                )
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    enabled: Boolean,
    icon: @Composable () -> Unit,
    onEnable: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                icon()

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector =
                        if (enabled) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Warning
                        },
                    contentDescription = null,
                    tint =
                        if (enabled) {
                            EnabledGreen
                        } else {
                            DisabledRed
                        }
                )
            }

            Text(
                text = if (enabled) "Enabled" else "Not enabled",
                color =
                    if (enabled) {
                        EnabledGreen
                    } else {
                        DisabledRed
                    },
                style = MaterialTheme.typography.labelLarge
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onEnable
            ) {
                Text(
                    if (enabled) {
                        "Open Settings"
                    } else {
                        "Enable"
                    }
                )
            }
        }
    }
}

@Composable
private fun RequirementRow(
    name: String,
    requirement: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name)

        Text(
            requirement,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}