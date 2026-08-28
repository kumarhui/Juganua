package cvam.dignity.juganua.features.pdfunlocker

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfUnlockerLogic {

    /**
     * Resolves filename for both content:// and file:// URIs with advanced fallbacks.
     * Fixes the "Unknown file" bug by checking multiple metadata sources and
     * handling the Hub redirection scheme.
     */
    fun getFileName(context: Context, uri: Uri): String {
        // Fallback 1: Extract from URI path if possible
        val pathSegment = uri.lastPathSegment ?: ""

        // If it's a file scheme (common in redirections), return the filename
        if (uri.scheme == "file") {
            return pathSegment.ifEmpty { "Shared_Document.pdf" }
        }

        var name: String? = null
        try {
            // Standard Content Resolver Query for Display Name
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index != -1) {
                    name = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            // Log or ignore
        }

        // Final result: Resolved name -> Path segment -> Default
        val result = name ?: pathSegment
        return if (result.isEmpty() || result.contains("hub_shared")) "PDF_Document.pdf" else result
    }

    /**
     * Checks if the PDF is actually encrypted.
     * Returns:
     * - UnlockResult if the PDF is NOT protected (Instant Unlock)
     * - null if the PDF IS protected (Needs Password)
     * Throws Exception only for actual corruption or accessibility issues.
     */
    suspend fun checkProtectionStatus(context: Context, uri: Uri): UnlockResult? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Attempt to load without password to check encryption status
                PDDocument.load(inputStream).use { document ->
                    if (!document.isEncrypted) {
                        // Create a clean copy without any metadata baggage
                        val tempFile = File(context.cacheDir, "instant_ready_${System.currentTimeMillis()}.pdf")
                        document.save(tempFile)

                        // FIX: Authority must match exactly what is in your AndroidManifest.xml
                        val authority = "${context.packageName}.provider"
                        val unlockedUri = FileProvider.getUriForFile(context, authority, tempFile)

                        return@withContext UnlockResult(unlockedUri, "No Password", isAlreadyUnprotected = true)
                    }
                }
            }
        } catch (e: InvalidPasswordException) {
            // Document is genuinely encrypted - trigger password UI
            return@withContext null
        } catch (e: Exception) {
            // Actual file error
            throw Exception("Unable to process: ${e.localizedMessage ?: "File is unreadable"}")
        }
        null
    }

    /**
     * Attempts to unlock the PDF using the provided password.
     */
    suspend fun attemptUnlock(context: Context, uri: Uri, password: String): UnlockResult? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream, password).use { document ->
                    val tempFile = File(context.cacheDir, "unlocked_out_${System.currentTimeMillis()}.pdf")
                    document.setAllSecurityToBeRemoved(true)
                    document.save(tempFile)

                    val authority = "${context.packageName}.provider"
                    val unlockedUri = FileProvider.getUriForFile(context, authority, tempFile)

                    return@withContext UnlockResult(unlockedUri, password)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves the unlocked PDF directly to Downloads/Juganua using MediaStore.
     */
    suspend fun saveToDownloads(context: Context, uri: Uri, originalName: String) = withContext(Dispatchers.IO) {
        try {
            val baseName = originalName.substringBeforeLast(".").ifEmpty { "unlocked_doc" }
            val fileName = "Unlocked_${baseName}_${System.currentTimeMillis()}.pdf"

            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Juganua")
                }
            }

            val destination = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            destination?.let { destUri ->
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.copyTo(out)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Downloads/Juganua", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Save Failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Triggers a system chooser to view the PDF.
     */
    fun previewPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }
}