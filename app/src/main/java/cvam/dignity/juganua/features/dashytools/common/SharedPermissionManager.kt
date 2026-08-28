package cvam.dignity.juganua.features.dashytools.common

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import cvam.dignity.juganua.features.dashytools.settings.JuganuaAccessibilityService

/**
 * Central permission manager for all Juganua floating/accessibility tools.
 *
 * Shared by:
 * - Neon Pen
 * - Screenshot Taker
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
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService> =
            JuganuaAccessibilityService::class.java
    ): Boolean {

        val expectedComponent =
            "${context.packageName}/${serviceClass.canonicalName}"

        val enabledServices =
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

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)

        while (splitter.hasNext()) {
            if (
                splitter.next().equals(
                    expectedComponent,
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
        return isAccessibilityServiceEnabled(context)
    }

    fun isNeonAccessibilityEnabled(
        context: Context
    ): Boolean {
        return isAccessibilityServiceEnabled(context)
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun areAllRequiredPermissionsGranted(
        context: Context
    ): Boolean {
        return hasOverlayPermission(context) &&
                isAccessibilityServiceEnabled(context)
    }
}