package cvam.dignity.juganua.features.pdfunlocker

import android.net.Uri

/**
 * Encapsulates the unlocking result state.
 */
data class UnlockResult(
    val unlockedUri: Uri,
    val password: String,
    val isAlreadyUnprotected: Boolean = false
)

/**
 * Modes for the unlocker UI.
 */
enum class UnlockMode {
    SINGLE_PASS,
    AADHAAR_FORCE
}