package cvam.dignity.juganua.features.postal.ippb_register

import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IppbDataViewerScreen(viewModel: IppbRegisterViewModel, onBack: () -> Unit) {
    val filteredData by viewModel.filteredData.collectAsState()
    val dateStr by viewModel.selectedDateString.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val nextDisabled by viewModel.isNextDisabled.collectAsState()

    var detailedRecord by remember { mutableStateOf<RegistrationData?>(null) }
    var qrPopupUid by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search vault...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { viewModel.navigateDate(-1) }) { Icon(Icons.Default.ChevronLeft, null, tint = Color(0xFF0D47A1)) }
                    Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = { viewModel.navigateDate(1) }, enabled = !nextDisabled) { Icon(Icons.Default.ChevronRight, null, tint = if(nextDisabled) Color.LightGray else Color(0xFF0D47A1)) }
                }
            }
        }

        if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (filteredData.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Vault is empty for this date", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(filteredData) { _, item ->
                    RecordListCard(item, onClick = { detailedRecord = item })
                }
            }
        }
    }

    if (detailedRecord != null) {
        DetailedRecordDialog(
            item = detailedRecord!!,
            onAadhaarClick = { uid: String -> qrPopupUid = uid },
            onEditClick = { record: RegistrationData -> viewModel.loadForEdit(record) },
            onDismiss = { detailedRecord = null }
        )
    }
    if (qrPopupUid != null) AadhaarQrPopup(uid = qrPopupUid!!) { qrPopupUid = null }
}