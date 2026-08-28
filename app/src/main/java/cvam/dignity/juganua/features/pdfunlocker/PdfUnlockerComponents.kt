package cvam.dignity.juganua.features.pdfunlocker

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cvam.dignity.juganua.common.UsageTracker

@Composable
fun PdfActionCard(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(0.7f))
            }
        }
    }
}

@Composable
fun PdfInputField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isNumeric: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SuccessDashboard(
    password: String,
    unlockedUri: Uri,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onNavigateToTool: (String, Uri) -> Unit
) {
    var showContext by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = Color(0xFF4CAF50).copy(0.1f), border = BorderStroke(2.dp, Color(0xFF4CAF50))) {
            Box {
                Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("SUCCESSFULLY UNLOCKED", fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                    Text("Key: $password", style = MaterialTheme.typography.labelLarge)
                }

                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    IconButton(onClick = { showContext = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color(0xFF2E7D32))
                    }
                    DropdownMenu(expanded = showContext, onDismissRequest = { showContext = false }) {
                        Text("Cross-Tool Actions", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        DropdownMenuItem(
                            text = { Text("Merge with others") },
                            leadingIcon = { Icon(Icons.Default.MergeType, null) },
                            onClick = { showContext = false; onNavigateToTool(UsageTracker.ID_MERGE_PDF, unlockedUri) }
                        )
                        DropdownMenuItem(
                            text = { Text("Open in ID Splitter") },
                            leadingIcon = { Icon(Icons.Default.FilterFrames, null) },
                            onClick = { showContext = false; onNavigateToTool(UsageTracker.ID_ID_CARD_SPLITTER, unlockedUri) }
                        )
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Auto Save Path", style = MaterialTheme.typography.labelSmall)
                    Text("Downloads/Juganua", fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.Check, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onPreview, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("VIEW") }
            Button(onClick = onDownload, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("SAVE") }
        }
    }
}

@Composable
fun PdfModeTab(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = CircleShape,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}