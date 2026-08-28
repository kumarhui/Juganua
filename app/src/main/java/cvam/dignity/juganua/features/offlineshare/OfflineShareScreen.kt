package cvam.dignity.juganua.features.offlineshare

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineShareScreen(
    initialUris: List<Uri> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedFiles by remember { mutableStateOf(initialUris) }
    var isServerRunning by remember { mutableStateOf(false) }
    var serverInstance by remember { mutableStateOf<LiteHttpServer?>(null) }
    var currentIp by remember { mutableStateOf("Detecting...") }

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedFiles = (selectedFiles + uris).distinctBy { it.toString() }
            serverInstance?.updateFiles(selectedFiles)
        }
    }

    DisposableEffect(Unit) {
        onDispose { serverInstance?.stop() }
    }

    // Scaffold/Header removed. Using global shell.
    Column(Modifier.fillMaxSize().padding(16.dp)) {

        // Compact action row instead of full header
        if (!isServerRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Select files to start sharing", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                IconButton(onClick = { pickerLauncher.launch("*/*") }) {
                    Icon(Icons.Default.AddCircle, "Add Files", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (isServerRunning) {
            ServerStatusCard(
                url = "http://$currentIp:8080",
                count = selectedFiles.size,
                onStop = {
                    isServerRunning = false
                    serverInstance?.stop()
                    serverInstance = null
                }
            )
            Spacer(Modifier.height(16.dp))
            Text("CURRENTLY SHARING", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        } else {
            Text("Receiver scans QR to download files over WiFi/Hotspot.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(Modifier.height(12.dp))

        if (selectedFiles.isEmpty() && !isServerRunning) {
            EmptyState(onAdd = { pickerLauncher.launch("*/*") })
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(selectedFiles) { index, uri ->
                    ShareFileRow(uri = uri, onDelete = {
                        val newList = selectedFiles.toMutableList().apply { removeAt(index) }
                        selectedFiles = newList
                        serverInstance?.updateFiles(newList)
                    })
                }
            }

            if (!isServerRunning) {
                Button(
                    onClick = {
                        val ip = getLocalIpAddress()
                        if (ip == null) {
                            Toast.makeText(context, "Enable Hotspot or WiFi", Toast.LENGTH_LONG).show()
                        } else {
                            currentIp = ip
                            val server = LiteHttpServer(context, selectedFiles)
                            server.start()
                            serverInstance = server
                            isServerRunning = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.WifiTethering, null)
                    Spacer(Modifier.width(12.dp))
                    Text("START WEB SERVER", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ServerStatusCard(url: String, count: Int, onStop: () -> Unit) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(url) {
        withContext(Dispatchers.Default) {
            qrBitmap = try {
                val bitMatrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 512, 512)
                val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) for (y in 0 until 512) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
                bmp
            } catch (e: Exception) { null }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Server Active", fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                    Text("$count items indexed", fontSize = 11.sp, color = Color.Gray)
                }
                IconButton(onClick = onStop, modifier = Modifier.background(Color.Red.copy(0.1f), CircleShape)) {
                    Icon(Icons.Default.StopCircle, null, tint = Color.Red)
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(modifier = Modifier.size(170.dp), color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp) {
                Box(contentAlignment = Alignment.Center) {
                    if (qrBitmap != null) {
                        Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.padding(10.dp))
                    } else CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.LightGray.copy(0.5f))) {
                Text(url, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
            }

            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) {
                Icon(Icons.Default.Settings, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("HOTSPOT SETTINGS", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun ShareFileRow(uri: Uri, onDelete: () -> Unit) {
    val context = LocalContext.current
    val name = remember(uri) { getFileName(context, uri) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (name.endsWith(".apk")) Icons.Default.Android else Icons.Default.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(name, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Red.copy(0.5f), modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun EmptyState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.FileOpen, null, Modifier.size(64.dp), Color.LightGray)
        Spacer(Modifier.height(12.dp))
        Text("No files selected", fontWeight = FontWeight.Bold, color = Color.Gray)
        Button(onClick = onAdd, modifier = Modifier.padding(top = 16.dp)) { Text("CHOOSE FILES") }
    }
}

class LiteHttpServer(private val context: Context, initialFiles: List<Uri>) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val sharedFiles = Collections.synchronizedList(initialFiles.toMutableList())

    fun updateFiles(newList: List<Uri>) {
        sharedFiles.clear()
        sharedFiles.addAll(newList)
    }

    fun start() {
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(8080, 50, InetAddress.getByName("0.0.0.0"))
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {}
        }.start()
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val line = reader.readLine() ?: return@Thread
                if (line.startsWith("GET")) {
                    val path = line.split(" ")[1]
                    if (path == "/") sendPortal(socket)
                    else if (path.startsWith("/file/")) {
                        val index = path.substringAfterLast("/").toIntOrNull()
                        if (index != null && index in sharedFiles.indices) sendFile(socket, sharedFiles[index])
                        else sendNotFound(socket)
                    } else sendNotFound(socket)
                }
                socket.close()
            } catch (e: Exception) {}
        }.start()
    }

    private fun sendPortal(socket: Socket) {
        val fileCards = sharedFiles.mapIndexed { index, uri ->
            val name = getFileName(context, uri)
            val isImage = name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".jpeg") }
            val isApk = name.lowercase().endsWith(".apk")

            """
            <div class="file-item">
                <div class="thumb-box">
                ${if (isImage) "<img src='/file/$index'>"
            else if (isApk) "<svg class='icon-apk' viewBox='0 0 24 24'><path d='M17.6 9.48l1.84-3.18c.16-.31.04-.69-.26-.85a.611.611 0 0 0-.85.26l-1.89 3.27C15.1 8.33 13.62 8 12 8s-3.1.33-4.44.98L5.67 5.71c-.16-.31-.54-.42-.85-.26a.611.611 0 0 0-.26.85l1.84 3.18C3.81 11.2 2 13.92 2 17h20c0-3.08-1.81-5.8-4.4-7.52zM7 15c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm10 0c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z'/></svg>"
            else "<svg class='icon-generic' viewBox='0 0 24 24'><path d='M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z'/></svg>"}
                </div>
                <div class="info">
                    <span class="name">$name</span>
                    <span class="meta">Available locally</span>
                </div>
                <a href="/file/$index" class="dl-btn" download="$name">DOWNLOAD</a>
            </div>
            """.trimIndent()
        }.joinToString("")

        val html = """
            <!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { font-family: sans-serif; background: #f0f2f5; padding: 20px; }
                .file-item { background: white; padding: 15px; border-radius: 12px; margin-bottom: 10px; display: flex; align-items: center; }
                .thumb-box { width: 50px; height: 50px; margin-right: 15px; display: flex; align-items: center; justify-content: center; }
                img { max-width: 100%; max-height: 100%; border-radius: 4px; }
                .info { flex: 1; }
                .name { display: block; font-weight: bold; font-size: 14px; }
                .meta { font-size: 11px; color: gray; }
                .dl-btn { background: #0D47A1; color: white; text-decoration: none; padding: 8px 12px; border-radius: 6px; font-size: 12px; font-weight: bold; }
            </style></head><body><h3>Studio Share Portal</h3>$fileCards</body></html>
        """.trimIndent()

        try {
            val out = socket.getOutputStream()
            PrintWriter(out).apply {
                println("HTTP/1.1 200 OK")
                println("Content-Type: text/html; charset=UTF-8")
                println("Content-Length: ${html.toByteArray().size}")
                println(); flush()
            }
            out.write(html.toByteArray()); out.flush()
        } catch (e: Exception) {}
    }

    private fun sendFile(socket: Socket, uri: Uri) {
        val fileName = getFileName(context, uri)
        val mimeType = if (fileName.endsWith(".apk")) "application/vnd.android.package-archive"
        else context.contentResolver.getType(uri) ?: "application/octet-stream"
        val out = socket.getOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            PrintWriter(out).apply {
                println("HTTP/1.1 200 OK")
                println("Content-Disposition: inline; filename=\"$fileName\"")
                println("Content-Type: $mimeType")
                println(); flush()
            }
            input.copyTo(out); out.flush()
        }
    }

    private fun sendNotFound(socket: Socket) {
        PrintWriter(socket.getOutputStream()).apply { println("HTTP/1.1 404 Not Found"); println(); flush() }
    }
}

private fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (ni.isLoopback || !ni.isUp) continue
            val addresses = ni.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) return addr.hostAddress
            }
        }
    } catch (e: Exception) {}
    return null
}

private fun getFileName(context: Context, uri: Uri): String {
    var result = ""
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) }
    }
    return result.ifEmpty { uri.path?.substringAfterLast('/') ?: "shared_item" }
}