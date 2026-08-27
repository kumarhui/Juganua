package cvam.dignity.juganua.features.postal.ippb_register

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun IppbSettingsScreen(viewModel: IppbRegisterViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val records by viewModel.allData.collectAsState()

    // Activity Result for EXPORT
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(viewModel.getExportData().toByteArray())
                }
                Toast.makeText(context, "Data Exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "Export Error", Toast.LENGTH_SHORT).show() }
        }
    }

    // Activity Result for IMPORT
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val json = input.bufferedReader().use { r -> r.readText() }
                    viewModel.importData(json)
                }
            } catch (e: Exception) { Toast.makeText(context, "Import Failed", Toast.LENGTH_SHORT).show() }
        }
    }

    // Activity Result for FOLDER Path
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.saveExportLocation(it.toString())
            Toast.makeText(context, "Default path updated", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Studio Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Export Database") },
            supportingContent = { Text("${records.size} entries will be converted to JSON") },
            leadingContent = { Icon(Icons.Default.Backup, null) },
            trailingContent = { TextButton(onClick = { exportLauncher.launch("ippb_backup_${System.currentTimeMillis()}.json") }) { Text("EXPORT") } }
        )

        ListItem(
            headlineContent = { Text("Import Backup") },
            supportingContent = { Text("Merge records from external JSON file") },
            leadingContent = { Icon(Icons.Default.UploadFile, null) },
            trailingContent = { TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("IMPORT") } }
        )

        ListItem(
            headlineContent = { Text("Backup Location") },
            supportingContent = { Text("Link a directory for automated exports") },
            leadingContent = { Icon(Icons.Default.FolderOpen, null) },
            trailingContent = { TextButton(onClick = { folderLauncher.launch(null) }) { Text("CHOOSE") } }
        )

        Spacer(Modifier.weight(1f))
        Button(onClick = onBack, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))) { Text("RETURN") }
    }
}