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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cvam.dignity.juganua.CircularDashboardTool
import cvam.dignity.juganua.MainActivity
import cvam.dignity.juganua.ToolAction
import java.io.File
import java.io.FileOutputStream

class IntentReceiveActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUris = mutableListOf<Uri>()

        try {
            when (intent.action) {

                Intent.ACTION_SEND -> {
                    intent.getParcelableExtra<Uri>(
                        Intent.EXTRA_STREAM
                    )?.let { uri ->
                        sharedUris.add(
                            getSafeUriFromIntent(
                                this,
                                uri,
                                0
                            )
                        )
                    }
                }

                Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(
                        Intent.EXTRA_STREAM
                    )?.let { list ->

                        list.forEachIndexed { index, uri ->
                            sharedUris.add(
                                getSafeUriFromIntent(
                                    this,
                                    uri,
                                    index
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        if (sharedUris.isEmpty()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {

                Surface(
                    color =
                        MaterialTheme.colorScheme.background
                ) {

                    IntentHubScreen(
                        sharedUris = sharedUris,
                        mimeType = intent.type,

                        onBack = {
                            finish()
                        },

                        onToolSelected = { tool ->
                            handleToolNavigation(
                                tool,
                                sharedUris
                            )
                        }
                    )
                }
            }
        }
    }

    private fun handleToolNavigation(
        tool: ToolAction,
        uris: List<Uri>
    ) {
        val toolKey =
            tool.uniqueKey

        if (toolKey.isBlank()) {
            return
        }

        val mainIntent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                putExtra(
                    "TARGET_TOOL",
                    toolKey
                )

                putParcelableArrayListExtra(
                    "SHARED_URIS",
                    ArrayList(uris)
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
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

    val firstUriString =
        sharedUris
            .firstOrNull()
            ?.toString()
            ?.lowercase()
            ?: ""

    val isPdf =
        mimeType?.contains("pdf") == true ||
                firstUriString.endsWith(".pdf")

    /*
     * Keep these tools because they are still useful
     * for Android Share / Open With flows.
     *
     * They do NOT appear on the main dashboard.
     */
    val tools = remember(isPdf) {

        if (isPdf) {

            listOf(

                ToolAction(
                    title = "PDF Unlocker",
                    subtitle = "Security Removal",
                    icon = Icons.Default.LockOpen,
                    uniqueKey =
                        UsageTracker.ID_PDF_UNLOCKER
                ),

                ToolAction(
                    title = "Merge PDF",
                    subtitle = "Combine Files",
                    icon = Icons.Default.MergeType,
                    uniqueKey =
                        UsageTracker.ID_MERGE_PDF
                ),

                ToolAction(
                    title = "ID Splitter",
                    subtitle = "Crop UIDAI PDF",
                    icon = Icons.Default.FilterFrames,
                    uniqueKey =
                        UsageTracker.ID_ID_CARD_SPLITTER
                )
            )

        } else {

            listOf(

                ToolAction(
                    title = "Passport Photo",
                    subtitle = "Print Sheet",
                    icon = Icons.Default.Portrait,
                    uniqueKey =
                        UsageTracker.ID_PASSPORT_PHOTO_MAKER
                ),

                ToolAction(
                    title = "Background Remover",
                    subtitle = "Remove & Replace",
                    icon = Icons.Default.PersonRemove,
                    uniqueKey =
                        UsageTracker.ID_BACKGROUND_REMOVER
                ),

                ToolAction(
                    title = "ID Splitter",
                    subtitle = "Slice ID Card",
                    icon = Icons.Default.FilterFrames,
                    uniqueKey =
                        UsageTracker.ID_ID_CARD_SPLITTER
                ),

                ToolAction(
                    title = "Offline Share",
                    subtitle = "File Server",
                    icon = Icons.Default.WifiTethering,
                    uniqueKey =
                        UsageTracker.ID_OFFLINE_SHARE
                )
            )
        }
    }

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "STUDIO HUB",
                            fontWeight =
                                FontWeight.Black
                        )

                        Text(
                            text = "RECEIVED FILES",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.Close,
                            contentDescription =
                                "Close"
                        )
                    }
                }
            )
        }

    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {

            Text(
                text =
                    "FILES TO PROCESS (${sharedUris.size})",

                style =
                    MaterialTheme
                        .typography
                        .labelMedium,

                fontWeight =
                    FontWeight.Bold,

                color =
                    MaterialTheme
                        .colorScheme
                        .outline,

                modifier =
                    Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 12.dp
                    )
            )

            Box(
                modifier =
                    Modifier.weight(0.35f)
            ) {

                LazyColumn(

                    contentPadding =
                        PaddingValues(
                            horizontal = 20.dp
                        ),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp),

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    items(sharedUris) { uri ->
                        FileItemRow(uri)
                    }
                }
            }

            HorizontalDivider(
                modifier =
                    Modifier
                        .padding(vertical = 8.dp)
                        .alpha(0.3f)
            )

            Text(
                text = "SELECT TARGET TOOL",

                style =
                    MaterialTheme
                        .typography
                        .labelMedium,

                fontWeight =
                    FontWeight.Bold,

                color =
                    MaterialTheme
                        .colorScheme
                        .outline,

                modifier =
                    Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 8.dp
                    )
            )

            Box(
                modifier =
                    Modifier.weight(0.65f)
            ) {

                LazyVerticalGrid(

                    columns =
                        GridCells.Fixed(3),

                    contentPadding =
                        PaddingValues(16.dp),

                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    items(tools) { tool ->

                        CircularDashboardTool(
                            tool = tool,

                            onClick = {
                                onToolSelected(tool)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItemRow(
    uri: Uri
) {

    val context =
        LocalContext.current

    val fileName =
        remember(uri) {
            getFileName(
                context,
                uri
            )
        }

    val isPdf =
        fileName
            .lowercase()
            .endsWith(".pdf")

    Surface(

        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(16.dp),

        color =
            MaterialTheme
                .colorScheme
                .surfaceVariant
                .copy(alpha = 0.4f)
    ) {

        Row(

            modifier =
                Modifier.padding(12.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Surface(

                shape =
                    CircleShape,

                color =
                    MaterialTheme
                        .colorScheme
                        .primary
                        .copy(alpha = 0.1f),

                modifier =
                    Modifier.size(36.dp)
            ) {

                val icon =
                    if (isPdf) {
                        Icons.Default.PictureAsPdf
                    } else {
                        Icons.Default.FilePresent
                    }

                Icon(

                    imageVector = icon,

                    contentDescription =
                        null,

                    tint =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    modifier =
                        Modifier.padding(8.dp)
                )
            }

            Spacer(
                Modifier.width(12.dp)
            )

            Text(

                text = fileName,

                fontWeight =
                    FontWeight.Medium,

                fontSize =
                    13.sp,

                maxLines = 1,

                overflow =
                    TextOverflow.Ellipsis,

                modifier =
                    Modifier.weight(1f)
            )
        }
    }
}

private fun getFileName(
    context: Context,
    uri: Uri
): String {

    var name =
        "Unknown File"

    try {

        if (uri.scheme == "file") {
            return uri.lastPathSegment ?: "File"
        }

        context.contentResolver
            .query(
                uri,
                null,
                null,
                null,
                null
            )
            ?.use { cursor ->

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (
                    cursor.moveToFirst() &&
                    index != -1
                ) {

                    name =
                        cursor.getString(index)
                }
            }

    } catch (_: Exception) {
    }

    return name
}

private fun getSafeUriFromIntent(
    context: Context,
    originalUri: Uri,
    index: Int
): Uri {

    if (originalUri.scheme == "file") {
        return originalUri
    }

    return try {

        val contentResolver =
            context.contentResolver

        val inputStream =
            contentResolver.openInputStream(
                originalUri
            )

        val mimeType =
            contentResolver.getType(
                originalUri
            )

        val ext =
            MimeTypeMap
                .getSingleton()
                .getExtensionFromMimeType(
                    mimeType
                )
                ?: getFileName(
                    context,
                    originalUri
                ).substringAfterLast(
                    '.',
                    "dat"
                )

        val tempFile =
            File(
                context.cacheDir,
                "hub_shared_${index}_${System.currentTimeMillis()}.$ext"
            )

        inputStream?.use { input ->

            FileOutputStream(tempFile)
                .use { output ->

                    input.copyTo(output)
                }
        }

        Uri.fromFile(tempFile)

    } catch (_: Exception) {

        originalUri
    }
}