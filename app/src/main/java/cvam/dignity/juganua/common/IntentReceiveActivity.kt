package cvam.dignity.juganua.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cvam.dignity.juganua.MainActivity
import cvam.dignity.juganua.ModernToolTile
import cvam.dignity.juganua.ToolAction
import java.io.File
import java.io.FileOutputStream

class IntentReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUris = mutableListOf<Uri>()
        try {
            if (intent.action == Intent.ACTION_SEND) {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {
                    sharedUris.add(getSafeUriFromIntent(this, it, 0))
                }
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { list ->
                    list.forEachIndexed { index, uri ->
                        sharedUris.add(getSafeUriFromIntent(this, uri, index))
                    }
                }
            }
        } catch (e: Exception) {}

        if (sharedUris.isEmpty()) { finish(); return }

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    IntentHubScreen(
                        sharedUris = sharedUris,
                        mimeType = intent.type,
                        onBack = { finish() },
                        onToolSelected = { tool -> handleToolNavigation(tool, sharedUris) }
                    )
                }
            }
        }
    }

    private fun handleToolNavigation(tool: ToolAction, uris: List<Uri>) {
        val toolKey = tool.uniqueKey ?: return
        // FIXED: Using FLAG_ACTIVITY_SINGLE_TOP so MainActivity picks up the new files
        // without creating a duplicate activity instance.
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("TARGET_TOOL", toolKey)
            putParcelableArrayListExtra("SHARED_URIS", ArrayList(uris))
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(mainIntent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntentHubScreen(
    sharedUris: List<Uri>,
    mimeType: String?,
    onBack: () -> Unit,
    onToolSelected: (ToolAction) -> Unit
) {
    val firstUriString = sharedUris.firstOrNull()?.toString()?.lowercase() ?: ""
    val isPdf = mimeType?.contains("pdf") == true || firstUriString.endsWith(".pdf")

    val tools = remember(isPdf) {
        if (isPdf) {
            listOf(
                ToolAction("PDF Unlocker", "Security Removal", Icons.Default.LockOpen, UsageTracker.ID_PDF_UNLOCKER),
                ToolAction("Merge PDF", "Combine Files", Icons.Default.MergeType, UsageTracker.ID_MERGE_PDF),
                ToolAction("ID Splitter", "Crop UIDAI PDF", Icons.Default.FilterFrames, UsageTracker.ID_ID_CARD_SPLITTER)
            )
        } else {
            listOf(
                ToolAction("Passport Photo", "Print Sheet", Icons.Default.Portrait, UsageTracker.ID_PASSPORT_PHOTO),
                ToolAction("ID Splitter", "Slice ID Card", Icons.Default.FilterFrames, UsageTracker.ID_ID_CARD_SPLITTER),
                ToolAction("Offline Share", "File Server", Icons.Default.WifiTethering, UsageTracker.ID_OFFLINE_SHARE)
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("STUDIO HUB", fontWeight = FontWeight.Black)
                        Text("RECEIVED FILES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, null) } }
            )
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).fillMaxSize()) {
            Text(
                "FILES TO PROCESS (${sharedUris.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            Box(modifier = Modifier.weight(0.35f)) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sharedUris) { uri ->
                        FileItemRow(uri)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp).alpha(0.3f))

            Text(
                "SELECT TARGET TOOL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Box(modifier = Modifier.weight(0.65f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tools) { tool ->
                        ModernToolTile(tool = tool) { onToolSelected(tool) }
                    }
                }
            }
        }
    }
}

@Composable
fun FileItemRow(uri: Uri) {
    val context = LocalContext.current
    val fileName = remember(uri) { getFileName(context, uri) }
    val isPdf = fileName.lowercase().endsWith(".pdf")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                val icon = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.FilePresent
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = fileName,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "Unknown File"
    try {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "File"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index != -1) name = cursor.getString(index)
        }
    } catch (e: Exception) {}
    return name
}

private fun getSafeUriFromIntent(context: Context, originalUri: Uri, index: Int): Uri {
    if (originalUri.scheme == "file") return originalUri
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(originalUri)
        val mimeType = contentResolver.getType(originalUri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?: getFileName(context, originalUri).substringAfterLast('.', "dat")

        val tempFile = File(context.cacheDir, "hub_shared_${index}_${System.currentTimeMillis()}.$ext")
        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        Uri.fromFile(tempFile)
    } catch (e: Exception) { originalUri }
}