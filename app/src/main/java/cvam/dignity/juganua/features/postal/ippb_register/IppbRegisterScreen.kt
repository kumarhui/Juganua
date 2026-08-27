package cvam.dignity.juganua.features.postal.ippb_register

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * IPPB Studio Router (V7.0)
 * Logic: Automatically requests necessary permissions on screen entry.
 */
@Composable
fun IppbRegisterScreen(
    viewModel: IppbRegisterViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf("DASHBOARD") }
    val isEditing by viewModel.editingId.collectAsState()

    // --- Permission Management ---
    var isCameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isCameraPermissionGranted = isGranted
        if (isGranted) {
            Toast.makeText(context, "Camera access ready", Toast.LENGTH_SHORT).show()
        }
    }

    // NEW: Automated Permission Request on Open
    LaunchedEffect(Unit) {
        if (!isCameraPermissionGranted) {
            // A 500ms delay ensures the navigation transition between the Dashboard
            // and the tool finishes completely. This prevents the "Transition Record Mismatch"
            // error that causes the system to automatically deny permission dialogs.
            delay(500)
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handle external triggers (like Editing from Vault)
    LaunchedEffect(isEditing) {
        if (isEditing != null && currentScreen != "FORM") {
            currentScreen = "FORM"
        }
    }

    BackHandler {
        when (currentScreen) {
            "FORM" -> {
                viewModel.saveDraft()
                currentScreen = "DASHBOARD"
                viewModel.clearForm()
            }
            "VAULT", "SETTINGS" -> currentScreen = "DASHBOARD"
            else -> onBack()
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        label = "IppbNavTransition",
        transitionSpec = {
            (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f))
                .togetherWith(fadeOut(animationSpec = tween(300)))
        }
    ) { screen ->
        when (screen) {
            "FORM" -> {
                IppbEntryForm(viewModel) {
                    currentScreen = "DASHBOARD"
                }
            }
            "VAULT" -> {
                IppbDataViewerScreen(viewModel) {
                    currentScreen = "DASHBOARD"
                }
            }
            "SETTINGS" -> {
                IppbSettingsScreen(viewModel) {
                    currentScreen = "DASHBOARD"
                }
            }
            else -> {
                IppbRegisterDashboard(
                    onFillForm = { currentScreen = "FORM" },
                    onViewVault = { currentScreen = "VAULT" },
                    onOpenSettings = { currentScreen = "SETTINGS" },
                    isCameraPermissionGranted = isCameraPermissionGranted,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }
}