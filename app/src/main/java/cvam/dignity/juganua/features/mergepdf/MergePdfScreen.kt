package cvam.dignity.juganua.features.mergepdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MergeItem(
    val uri: Uri,
    val name: String,
    val type: String,
    val thumbnail: Bitmap? = null,
    val totalPages: Int = 0,
    val removedPages: Set<Int> = emptySet()
) {
    val effectivePageCount: Int get() = if (type == "pdf") totalPages - removedPages.size else 1
}

@Composable
fun MergePdfScreen(
    initialUri: Uri? = null,
    initialUris: List<Uri>? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedItems = remember { mutableStateListOf<MergeItem>() }
    var isMerging by remember { mutableStateOf(false) }
    var mergedPdfUri by remember { mutableStateOf<Uri?>(null) }

    val addUriToQueue = { uri: Uri ->
        scope.launch(Dispatchers.IO) {
            val type = context.contentResolver.getType(uri) ?: ""
            val name = getFileNameInternal(context, uri)
            val itemType = if (type.contains("pdf")) "pdf" else "image"
            val (thumbnail, pages) = getThumbnailAndPageCountInternal(context, uri, itemType)
            withContext(Dispatchers.Main) {
                if (!selectedItems.any { it.uri == uri }) {
                    selectedItems.add(MergeItem(uri, name, itemType, thumbnail, pages))
                }
            }
        }
    }

    LaunchedEffect(initialUri, initialUris) {
        val incoming = (initialUris ?: emptyList()) + listOfNotNull(initialUri)
        if (incoming.isNotEmpty()) {
            mergedPdfUri = null
            incoming.distinct().forEach { addUriToQueue(it) }
        }
    }

    BackHandler { if (mergedPdfUri != null) mergedPdfUri = null else onBack() }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { addUriToQueue(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AnimatedContent(targetState = mergedPdfUri != null, label = "MergeView") { hasResult ->
                if (hasResult) {
                    ResultDashboard(
                        onDownload = { saveMergedToDownloadsInternal(context, mergedPdfUri!!) },
                        onShare = { shareMergedPdfInternal(context, mergedPdfUri!!) },
                        onView = { viewMergedPdfInternal(context, mergedPdfUri!!) },
                        onReset = { mergedPdfUri = null; selectedItems.clear() }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ModernAddCard { pickerLauncher.launch(arrayOf("application/pdf", "image/*")) }
                        if (selectedItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Queue (${selectedItems.size})", fontWeight = FontWeight.Black)
                                Button(onClick = {
                                    isMerging = true
                                    scope.launch(Dispatchers.IO) {
                                        val result = mergeFilesInternal(context, selectedItems)
                                        withContext(Dispatchers.Main) { mergedPdfUri = result; isMerging = false }
                                    }
                                }) { Icon(Icons.Default.MergeType, null); Spacer(Modifier.width(8.dp)); Text("MERGE") }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(selectedItems) { index, item ->
                                    ModernFileRow(item, index, selectedItems.size, { selectedItems.removeAt(index) })
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Layers, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                                Text("No files selected", fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        if (isMerging) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)), contentAlignment = Alignment.Center) {
                Card(shape = RoundedCornerShape(24.dp)) {
                    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeCap = StrokeCap.Round)
                        Spacer(Modifier.height(16.dp)); Text("Merging...", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ModernFileRow(item: MergeItem, index: Int, total: Int, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) {
                if (item.thumbnail != null) Image(item.thumbnail.asImageBitmap(), null, contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Description, null, Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.type.uppercase()} Ã¢â‚¬Â¢ Pages: ${item.effectivePageCount}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, null, tint = Color.Red.copy(0.5f)) }
        }
    }
}

@Composable
fun ModernAddCard(onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.2f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp)); Text("Add Documents", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ResultDashboard(onDownload: () -> Unit, onShare: () -> Unit, onView: () -> Unit, onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(0.2f)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                Text("MERGE COMPLETE", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
            }
        }
        Button(onClick = onView, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(8.dp)); Text("OPEN PDF") }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f).height(56.dp)) { Icon(Icons.Default.Download, null); Text("SAVE") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f).height(56.dp)) { Icon(Icons.Default.Share, null); Text("SHARE") }
        }
        TextButton(onClick = onReset) { Text("NEW MERGE", fontWeight = FontWeight.Bold) }
    }
}

private suspend fun mergeFilesInternal(context: Context, items: List<MergeItem>): Uri? = withContext(Dispatchers.IO) {
    val doc = PDDocument()
    try {
        val file = File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")
        for (item in items) {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                if (item.type == "pdf") {
                    val sDoc = PDDocument.load(stream)
                    for (i in 0 until sDoc.numberOfPages) if (!item.removedPages.contains(i+1)) doc.addPage(sDoc.getPage(i))
                } else {
                    val bmp = BitmapFactory.decodeStream(stream)
                    val page = PDPage(PDRectangle.A4); doc.addPage(page)
                    val img = LosslessFactory.createFromImage(doc, bmp)
                    PDPageContentStream(doc, page).use { it.drawImage(img, 20f, 20f, PDRectangle.A4.width - 40f, PDRectangle.A4.height - 40f) }
                }
            }
        }
        doc.save(file); Uri.fromFile(file)
    } catch (e: Exception) { null } finally { try { doc.close() } catch (e: Exception) {} }
}

private fun getFileNameInternal(c: Context, u: Uri): String {
    var n = "File"
    c.contentResolver.query(u, null, null, null, null)?.use { if (it.moveToFirst()) n = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) }
    return n
}

private suspend fun getThumbnailAndPageCountInternal(c: Context, u: Uri, t: String): Pair<Bitmap?, Int> = withContext(Dispatchers.IO) {
    try {
        if (t == "pdf") {
            c.contentResolver.openFileDescriptor(u, "r")?.use {
                val r = PdfRenderer(it); val count = r.pageCount
                val b = Bitmap.createBitmap(150, 200, Bitmap.Config.ARGB_8888)
                r.openPage(0).use { p -> p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                r.close(); Pair(b, count)
            } ?: Pair(null, 0)
        } else {
            val b = BitmapFactory.decodeStream(c.contentResolver.openInputStream(u), null, BitmapFactory.Options().apply { inSampleSize = 4 })
            Pair(b, 1)
        }
    } catch (e: Exception) { Pair(null, 0) }
}

private fun saveMergedToDownloadsInternal(c: Context, u: Uri) {
    val cv = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, "Merged_${System.currentTimeMillis()}.pdf"); put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf") }
    c.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)?.let { dest ->
        c.contentResolver.openOutputStream(dest)?.use { out -> c.contentResolver.openInputStream(u)?.use { it.copyTo(out) } }
        Toast.makeText(c, "Saved to Downloads", Toast.LENGTH_SHORT).show()
    }
}

private fun shareMergedPdfInternal(context: Context, uri: Uri) {
    try {
        val file = File(uri.path!!)
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file", Toast.LENGTH_SHORT).show()
    }
}

private fun viewMergedPdfInternal(context: Context, uri: Uri) {
    try {
        val file = File(uri.path!!)
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening file", Toast.LENGTH_SHORT).show()
    }
}