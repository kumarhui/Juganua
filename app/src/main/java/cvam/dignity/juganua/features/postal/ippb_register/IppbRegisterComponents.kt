package cvam.dignity.juganua.features.postal.ippb_register

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScanField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    isMandatory: Boolean = false,
    onScan: (() -> Unit)? = null,
    onQrGenerate: (() -> Unit)? = null
) {
    Column {
        Text(text = buildAnnotatedString { append(label); if (isMandatory) withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) { append(" *") } }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused && value.isEmpty()) onValueChange("") },
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(icon, null, tint = Color(0xFF0D47A1)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onQrGenerate != null) IconButton(onClick = onQrGenerate) { Icon(Icons.Default.QrCode, null, tint = Color(0xFF0D47A1)) }
                    if (onScan != null) IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, null, tint = Color(0xFF0D47A1)) }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0D47A1), unfocusedBorderColor = Color.LightGray)
        )
    }
}

@Composable
fun RecordListCard(item: RegistrationData, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), CircleShape, color = Color(0xFF0D47A1).copy(alpha = 0.1f)) { Icon(Icons.Default.Person, null, Modifier.padding(10.dp), tint = Color(0xFF0D47A1)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name.uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${item.transactionType} • ₹${item.amount}", fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

@Composable
fun DetailedRecordDialog(
    item: RegistrationData,
    onAadhaarClick: (String) -> Unit,
    onEditClick: (RegistrationData) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                HorizontalDivider()
                DetailItem("Full Name", item.name.uppercase(), Icons.Default.Badge)
                DetailItem("Aadhaar", item.aadhaar, Icons.Default.VpnKey, color = Color.Blue, textDecoration = TextDecoration.Underline, onClick = { if(item.aadhaar.length == 12) onAadhaarClick(item.aadhaar) })
                DetailItem("Mobile", item.mobile, Icons.Default.Phone)
                DetailItem("Account", item.account, Icons.Default.Dialpad)
                DetailItem("Transaction", item.transactionType, Icons.Default.Payments)
                DetailItem("Amount", "₹${item.amount}", Icons.Default.CurrencyRupee, color = if(item.transactionType == "Withdraw") Color.Red else Color(0xFF1B5E20))

                Spacer(Modifier.height(8.dp))
                Button(onClick = { onEditClick(item); onDismiss() }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("EDIT ENTRY")
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, icon: ImageVector, color: Color = Color.Black, textDecoration: TextDecoration = TextDecoration.None, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if(onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.Gray)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(if(value.isEmpty()) "—" else value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if(value.isEmpty()) Color.LightGray else color, textDecoration = textDecoration)
        }
    }
}

@Composable
fun AadhaarQrPopup(uid: String, onDismiss: () -> Unit) {
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uid) {
        withContext(Dispatchers.Default) {
            val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><PrintLetterBarcodeData uid=\"$uid\"/>"
            qrBitmap = try {
                val bitMatrix = QRCodeWriter().encode(xml, BarcodeFormat.QR_CODE, 512, 512)
                val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
                for (x in 0 until 512) for (y in 0 until 512) bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                bmp
            } catch (e: Exception) { null }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Aadhaar Digital QR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (qrBitmap != null) Image(bitmap = qrBitmap!!.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
                else Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Text(uid.chunked(4).joinToString(" "), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("CLOSE") }
            }
        }
    }
}

@Composable
fun ColorfulFormCard(title: String, icon: ImageVector, color: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, color.copy(alpha = 0.2f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(32.dp), CircleShape, color.copy(alpha = 0.1f)) { Icon(icon, null, Modifier.padding(6.dp), color) }
                Spacer(Modifier.width(12.dp)); Text(title, fontWeight = FontWeight.Black, color = color, fontSize = 15.sp)
            }
            content()
        }
    }
}

@Composable
fun ModernDashboardCard(title: String, sub: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)), border = BorderStroke(1.dp, color.copy(alpha = 0.2f))) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), CircleShape, color.copy(alpha = 0.1f)) { Icon(icon, null, Modifier.padding(14.dp), color) }
            Spacer(Modifier.width(20.dp)); Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color); Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = color.copy(alpha = 0.3f))
        }
    }
}