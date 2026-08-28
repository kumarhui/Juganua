package cvam.dignity.juganua.features.common

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import cvam.dignity.juganua.features.settings.JuganuaAccessibilityService

/**
 * Shared Permission Manager providing centralized permission checking
 * for Neon Pen and Screenshot Taker.
 */
object SharedPermissionManager {

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
        }
    }

    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService> =
            JuganuaAccessibilityService::class.java
    ): Boolean {

        val expectedComponentName =
            "${context.packageName}/${serviceClass.canonicalName}"

        val enabledServicesSetting =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

        val accessibilityEnabled =
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            )

        if (accessibilityEnabled != 1) {
            return false
        }

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()

            if (componentName.equals(
                    expectedComponentName,
                    ignoreCase = true
                )
            ) {
                return true
            }
        }

        return false
    }

    fun isScreenshotAccessibilityEnabled(
        context: Context
    ): Boolean {
        return JuganuaAccessibilityService.isServiceEnabled(context)
    }

    fun isNeonAccessibilityEnabled(
        context: Context
    ): Boolean {
        return JuganuaAccessibilityService.isServiceEnabled(context)
    }

    fun openAccessibilitySettings(
        context: Context
    ) {
        val intent = Intent(
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun areAllRequiredPermissionsGranted(
        context: Context
    ): Boolean {
        return hasOverlayPermission(context) &&
                isAccessibilityServiceEnabled(context)
    }
}
