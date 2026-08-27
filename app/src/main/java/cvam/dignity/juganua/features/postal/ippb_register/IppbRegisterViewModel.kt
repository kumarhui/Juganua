package cvam.dignity.juganua.features.postal.ippb_register

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*

class IppbRegisterViewModel(private val context: Context) : ViewModel() {
    private val FILE_NAME = "ippb_studio_vault_v11.bin"
    private val DRAFT_FILE = "ippb_onboarding_draft.json"
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())

    // Form State
    val name = MutableStateFlow("")
    val dob = MutableStateFlow("")
    val aadhaar = MutableStateFlow("")
    val mobile = MutableStateFlow("")
    val account = MutableStateFlow("")
    val cif = MutableStateFlow("")
    val transactionType = MutableStateFlow("New Account")
    val amount = MutableStateFlow("200")
    val editingId = MutableStateFlow<String?>(null)
    private var originalTimestamp: Long? = null

    // Vault State
    val isProcessing = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val sortCriteria = MutableStateFlow(SortCriteria.DATE_DESC)
    private val _selectedDateMs = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis)

    private val _allData = MutableStateFlow<List<RegistrationData>>(emptyList())
    val allData: StateFlow<List<RegistrationData>> = _allData

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    val selectedDateString = _selectedDateMs.map { if (it == 0L) "All Records" else dateFormat.format(Date(it)) }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val isNextDisabled = _selectedDateMs.map {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        it >= today && it != 0L
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val filteredData = combine(_allData, _selectedDateMs, searchQuery, sortCriteria) { all, date, query, sort ->
        var list = all
        if (date != 0L) list = list.filter { it.timestamp in date..(date + 86399999L) }
        if (query.isNotEmpty()) list = list.filter { it.name.contains(query, true) || it.mobile.contains(query) || it.aadhaar.contains(query) }
        when(sort) {
            SortCriteria.DATE_DESC -> list.sortedByDescending { it.timestamp }
            SortCriteria.DATE_ASC -> list.sortedBy { it.timestamp }
            SortCriteria.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortCriteria.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { loadData(); loadDraft() }

    private fun loadData() = viewModelScope.launch {
        isProcessing.value = true
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) { isProcessing.value = false; return@launch }
        try {
            val encrypted = FileInputStream(file).bufferedReader().use { it.readText() }
            val decrypted = String(Base64.decode(encrypted, Base64.DEFAULT))
            val type = object : TypeToken<List<RegistrationData>>() {}.type
            _allData.value = gson.fromJson(decrypted, type)
        } catch (e: Exception) { } finally { isProcessing.value = false }
    }

    private fun saveDataInternal() {
        val json = gson.toJson(_allData.value)
        val encrypted = Base64.encodeToString(json.toByteArray(), Base64.DEFAULT)
        File(context.filesDir, FILE_NAME).writeText(encrypted)
    }

    private fun loadDraft() {
        try {
            val file = File(context.filesDir, DRAFT_FILE)
            if (file.exists()) {
                val draft = gson.fromJson(file.readText(), RegistrationData::class.java)
                name.value = draft.name; dob.value = draft.dob; aadhaar.value = draft.aadhaar
                mobile.value = draft.mobile; account.value = draft.account; cif.value = draft.cif
                transactionType.value = draft.transactionType; amount.value = draft.amount
            }
        } catch (e: Exception) {}
    }

    fun saveDraft() {
        viewModelScope.launch {
            val draft = RegistrationData(
                name = name.value, dob = dob.value, aadhaar = aadhaar.value,
                mobile = mobile.value, account = account.value, cif = cif.value,
                transactionType = transactionType.value, amount = amount.value
            )
            File(context.filesDir, DRAFT_FILE).writeText(gson.toJson(draft))
        }
    }

    fun submitRegistration() = viewModelScope.launch {
        if (name.value.isEmpty()) { _events.emit("Error: Name required"); return@launch }
        val currentList = _allData.value.toMutableList()
        val record = RegistrationData(
            id = editingId.value ?: UUID.randomUUID().toString(),
            name = name.value, dob = dob.value, aadhaar = aadhaar.value, mobile = mobile.value,
            account = account.value, cif = cif.value, transactionType = transactionType.value,
            amount = amount.value, timestamp = originalTimestamp ?: System.currentTimeMillis()
        )
        if (editingId.value != null) {
            val idx = currentList.indexOfFirst { it.id == editingId.value }
            if (idx != -1) currentList[idx] = record
        } else currentList.add(record)

        _allData.value = currentList
        saveDataInternal()
        _events.emit("Entry Saved Successfully")
        clearForm()
    }

    fun clearForm() {
        name.value = ""; dob.value = ""; aadhaar.value = ""; mobile.value = ""; account.value = ""; cif.value = ""
        transactionType.value = "New Account"; amount.value = "200"
        editingId.value = null; originalTimestamp = null
        File(context.filesDir, DRAFT_FILE).delete()
    }

    fun loadForEdit(record: RegistrationData) {
        editingId.value = record.id; name.value = record.name; dob.value = record.dob
        aadhaar.value = record.aadhaar; mobile.value = record.mobile; account.value = record.account
        cif.value = record.cif; transactionType.value = record.transactionType; amount.value = record.amount
        originalTimestamp = record.timestamp
    }

    // --- Import / Export / Backup Logic ---
    fun getExportData(): String = gson.toJson(_allData.value)

    fun importData(json: String) = viewModelScope.launch {
        try {
            val type = object : TypeToken<List<RegistrationData>>() {}.type
            val imported: List<RegistrationData> = gson.fromJson(json, type)
            val current = _allData.value.toMutableList()
            imported.forEach { item -> if (current.none { it.id == item.id }) current.add(item) }
            _allData.value = current
            saveDataInternal()
            _events.emit("Imported ${imported.size} records")
        } catch (e: Exception) { _events.emit("Import Failed: File invalid") }
    }

    fun saveExportLocation(uri: String) {
        context.getSharedPreferences("ippb_prefs", Context.MODE_PRIVATE).edit().putString("backup_uri", uri).apply()
    }

    fun navigateDate(days: Int) {
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val current = if (_selectedDateMs.value == 0L) today else _selectedDateMs.value
        val target = current + (days * 86400000L)
        if (target <= today) _selectedDateMs.value = target
    }

    fun showAll() { _selectedDateMs.value = 0L }
    fun setDate(y: Int, m: Int, d: Int) { val cal = Calendar.getInstance(); cal.set(y, m, d, 0, 0, 0); _selectedDateMs.value = cal.timeInMillis }
}