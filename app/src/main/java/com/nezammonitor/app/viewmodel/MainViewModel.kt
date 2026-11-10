package com.nezammonitor.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NezamDatabase.getDatabase(application)
    private val dao = db.nezamDao()

    // Cases
    private val _cases = MutableStateFlow<List<CaseEntity>>(emptyList())
    val cases: StateFlow<List<CaseEntity>> = _cases.asStateFlow()

    private val _caseDetail = MutableStateFlow<CaseEntity?>(null)
    val caseDetail: StateFlow<CaseEntity?> = _caseDetail.asStateFlow()

    private val _caseSpec = MutableStateFlow<SpecificationEntity?>(null)
    val caseSpec: StateFlow<SpecificationEntity?> = _caseSpec.asStateFlow()

    private val _caseEngineers = MutableStateFlow<List<EngineerEntity>>(emptyList())
    val caseEngineers: StateFlow<List<EngineerEntity>> = _caseEngineers.asStateFlow()

    private val _caseFees = MutableStateFlow<List<FeeEntity>>(emptyList())
    val caseFees: StateFlow<List<FeeEntity>> = _caseFees.asStateFlow()

    private val _caseReports = MutableStateFlow<List<ReportEntity>>(emptyList())
    val caseReports: StateFlow<List<ReportEntity>> = _caseReports.asStateFlow()

    // Engineers
    private val _engineers = MutableStateFlow<List<EngineerEntity>>(emptyList())
    val engineers: StateFlow<List<EngineerEntity>> = _engineers.asStateFlow()

    // Fees
    private val _fees = MutableStateFlow<List<FeeEntity>>(emptyList())
    val fees: StateFlow<List<FeeEntity>> = _fees.asStateFlow()

    // Reports
    private val _reports = MutableStateFlow<List<ReportEntity>>(emptyList())
    val reports: StateFlow<List<ReportEntity>> = _reports.asStateFlow()

    // FollowUp
    private val _followUpEdits = MutableStateFlow<List<FollowUpEditEntity>>(emptyList())
    val followUpEdits: StateFlow<List<FollowUpEditEntity>> = _followUpEdits.asStateFlow()

    // Specs - for address lookup
    private val _specifications = MutableStateFlow<List<SpecificationEntity>>(emptyList())
    val specifications: StateFlow<List<SpecificationEntity>> = _specifications.asStateFlow()

    // FeeReceipts v3 — دریافتی‌ها
    private val _feeReceipts = MutableStateFlow<List<FeeReceiptEntity>>(emptyList())
    val feeReceipts: StateFlow<List<FeeReceiptEntity>> = _feeReceipts.asStateFlow()

    // Import
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Logged-in user profile, from the API (not hardcoded).
    // The name comes from /panel/api/user (name + family), fetched at login
    // and saved to SharedPreferences by UpdateScreen. The API returns no
    // role for the user, so the role is derived from the synced engineer
    // record (showNazer), which does carry role + discipline — matched by
    // name. Blank name/role means "never logged in": the UI falls back to
    // a generic greeting and hides the role line.
    private val settingsPrefs =
        application.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)

    // The stored name may have been saved by 3.6.1 in Arabic script
    // (ي / ك) — normalize it here too, so old saved values display
    // in Persian as well.
    private val _userName =
        MutableStateFlow(toPersianChars(settingsPrefs.getString("api_user_name", "") ?: ""))
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userRole = MutableStateFlow("")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "api_user_name") {
            _userName.value = toPersianChars(settingsPrefs.getString("api_user_name", "") ?: "")
            refreshUserRole()
        }
    }

    init {
        settingsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        loadAllData()
    }

    override fun onCleared() {
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    private fun refreshUserRole() {
        _userRole.value = deriveUserRole(_engineers.value, _userName.value)
    }

    private fun deriveUserRole(engineers: List<EngineerEntity>, userName: String): String {
        val target = normalizeName(userName)
        if (target.isEmpty()) return ""
        val match = engineers.firstOrNull { eng ->
            val n = normalizeName(eng.name)
            n.isNotEmpty() && (n == target ||
                (target.length >= 5 && (n.contains(target) || target.contains(n))))
        } ?: return ""
        return listOf(match.role, match.discipline)
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun normalizeName(s: String): String = s.trim()
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace("\u200C", "")
        .replace(Regex("\\s+"), " ")

    private fun loadAllData() {
        viewModelScope.launch {
            dao.getAllCases().distinctUntilChanged().collect { _cases.value = it }
        }
        viewModelScope.launch {
            dao.getAllEngineers().distinctUntilChanged().collect {
                _engineers.value = it
                refreshUserRole()
            }
        }
        viewModelScope.launch {
            dao.getAllFees().distinctUntilChanged().collect { _fees.value = it }
        }
        viewModelScope.launch {
            dao.getAllReports().distinctUntilChanged().collect { _reports.value = it }
        }
        viewModelScope.launch {
            dao.getAllFollowUpEdits().distinctUntilChanged().collect { _followUpEdits.value = it }
        }
        viewModelScope.launch {
            dao.getAllSpecifications().distinctUntilChanged().collect { _specifications.value = it }
        }
        viewModelScope.launch {
            dao.getAllFeeReceipts().distinctUntilChanged().collect { _feeReceipts.value = it }
        }
    }

    fun getSpecificationForCase(caseNumber: String): SpecificationEntity? {
        return _specifications.value.find { it.caseNumber == caseNumber }
    }

    fun loadCaseDetail(caseNumber: String) {
        viewModelScope.launch {
            val case = _cases.value.find { it.caseNumber == caseNumber }
            _caseDetail.value = case
            _caseSpec.value = dao.getSpecification(caseNumber)
            _caseEngineers.value = dao.getEngineersForCase(caseNumber)
            _caseFees.value = dao.getFeesForCase(caseNumber)
            _caseReports.value = dao.getReportsForCase(caseNumber)
        }
    }

    fun searchCases(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                dao.getAllCases().first().let { _cases.value = it }
            } else {
                dao.searchCases(query).first().let { _cases.value = it }
            }
        }
    }

    fun importData(result: ImportResult) {
        _importResult.value = result
        loadAllData()
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun setImporting(value: Boolean) {
        _isImporting.value = value
    }

    fun saveFollowUpEdit(edit: FollowUpEditEntity) {
        viewModelScope.launch {
            dao.insertFollowUpEdit(edit)
        }
    }
}
