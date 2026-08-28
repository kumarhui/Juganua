package cvam.dignity.juganua.features.dashytools.whatsappchecker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsappCheckerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var phoneNumber by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val brush = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    )

    Scaffold(
        topBar = { }    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(brush)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color(0xFF25D366).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Whatsapp, null, tint = Color(0xFF25D366), modifier = Modifier.size(60.dp))
            }

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Direct Message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ModernPhoneField(value = phoneNumber, onValueChange = { if (it.length <= 10) phoneNumber = it.filter { c -> c.isDigit() } }, focusRequester = focusRequester)
                    Text("Open a chat without saving the contact.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { openWhatsAppWithFallback(context, phoneNumber) }, modifier = Modifier.fillMaxWidth().height(64.dp), enabled = phoneNumber.length == 10, shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366), contentColor = Color.White)) {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(12.dp)); Text("OPEN CHAT", fontWeight = FontWeight.Black)
                }
                if (phoneNumber.isNotEmpty()) {
                    TextButton(onClick = { phoneNumber = ""; scope.launch { focusRequester.requestFocus() } }, modifier = Modifier.fillMaxWidth()) {
                        Text("CLEAR NUMBER", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ModernPhoneField(value: String, onValueChange: (String) -> Unit, focusRequester: FocusRequester) {
    var isFocused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Phone Number", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.weight(1f)); Text("${value.length}/10", style = MaterialTheme.typography.labelSmall)
        }
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).border(2.dp, if (isFocused) Color(0xFF25D366) else Color.Transparent, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+91 ", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline))
                BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused })
            }
        }
    }
}

private fun openWhatsAppWithFallback(context: Context, phoneNumber: String) {
    if (phoneNumber.length != 10) return
    val uri = Uri.parse("https://wa.me/91$phoneNumber")
    val intent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.whatsapp") }
    try { context.startActivity(intent) } catch (e: Exception) { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

