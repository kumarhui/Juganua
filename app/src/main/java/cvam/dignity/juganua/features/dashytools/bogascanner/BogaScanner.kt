package cvam.dignity.juganua.features.dashytools.bogascanner

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * BogaScanner handles intent launcher configuration and ML Kit Document Scanner integration.
 */
object BogaScanner {
    private const val TAG = "BogaScanner"

    private enum class ScanMode {
        INITIAL_FRONT, INITIAL_BACK, RESCAN_FRONT, RESCAN_BACK, NORMAL_SCAN
    }

    private var currentMode: ScanMode = ScanMode.INITIAL_FRONT

    private fun launchScanner(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        isIdCard: Boolean
    ) {
        val optionsBuilder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(!isIdCard)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .setScannerMode(SCANNER_MODE_FULL)

        if (isIdCard) {
            optionsBuilder.setPageLimit(1)
        }

        val options = optionsBuilder.build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to launch scanner", e)
            }
    }

    fun startInitialFrontScan(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        currentMode = ScanMode.INITIAL_FRONT
        launchScanner(activity, launcher, isIdCard = true)
    }

    fun startInitialBackScan(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        currentMode = ScanMode.INITIAL_BACK
        launchScanner(activity, launcher, isIdCard = true)
    }

    fun rescanFront(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        currentMode = ScanMode.RESCAN_FRONT
        launchScanner(activity, launcher, isIdCard = true)
    }

    fun rescanBack(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        currentMode = ScanMode.RESCAN_BACK
        launchScanner(activity, launcher, isIdCard = true)
    }

    fun startNormalScan(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        currentMode = ScanMode.NORMAL_SCAN
        launchScanner(activity, launcher, isIdCard = false)
    }

    fun processResult(
        result: ActivityResult,
        onScanComplete: (uris: List<Uri>, isFront: Boolean, isInitial: Boolean, isIdCard: Boolean) -> Unit
    ) {
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult: GmsDocumentScanningResult? = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val uris = pages.map { it.imageUri }
                    val isIdCard = currentMode != ScanMode.NORMAL_SCAN

                    when (currentMode) {
                        ScanMode.INITIAL_FRONT -> onScanComplete(uris, true, true, isIdCard)
                        ScanMode.INITIAL_BACK -> onScanComplete(uris, false, true, isIdCard)
                        ScanMode.RESCAN_FRONT -> onScanComplete(uris, true, false, isIdCard)
                        ScanMode.RESCAN_BACK -> onScanComplete(uris, false, false, isIdCard)
                        ScanMode.NORMAL_SCAN -> onScanComplete(uris, true, true, isIdCard)
                    }
                }
            }
        } else {
            Log.d(TAG, "Scanner cancelled by user.")
            val isFront = currentMode == ScanMode.INITIAL_FRONT || currentMode == ScanMode.RESCAN_FRONT
            val isInitial = currentMode == ScanMode.INITIAL_FRONT || currentMode == ScanMode.INITIAL_BACK || currentMode == ScanMode.NORMAL_SCAN
            val isIdCard = currentMode != ScanMode.NORMAL_SCAN
            onScanComplete(emptyList(), isFront, isInitial, isIdCard)
        }
    }
}
