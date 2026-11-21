package com.nezammonitor.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.*
import com.nezammonitor.app.ui.components.personNameKey
import com.nezammonitor.app.ui.screens.reportgen.nowJalaliTimestamp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = NezamDatabase.getDatabase(application)
    private val dao = db.nezamDao()

    // The caseNumbers of the archived cases, kept as a flow so the other
    // lists can be filtered against it reactively.
    // MUST stay declared before the `init` block below: `loadAllData()` (called
    // from `init`) passes this flow to `combine(...)`, and a property declared
    // after `init` is still null when `init` runs — that null flow made every
    // version since 3.7.6 crash at launch (NullPointerException in
    // combineInternal). Do not move this below `init`.
    private val archivedNumbersFlow = MutableStateFlow<Set<String>>(emptySet())

    // ---- Yazd design-offices directory (v3.10.0; read-only since v3.11.3) ----
    // These two MUST stay declared before the `init` block below. The
    // init launch seeds the snapshot through them, and a property
    // declared after `init` is still null when `init` runs: in 3.11.4
    // they lived below `init` (with a comment wrongly claiming nothing
    // in init touched them) and the seed died with a
    // NullPointerException in Lazy.getValue() — every office and
    // engineer detail showed the failure reason instead of data. Same
    // initializer-order rule as archivedNumbersFlow (the 3.7.6 launch
    // crash): anything init touches lives above init.
    private val directoryDao = db.directoryDao()
    private val directoryRepository by lazy { DirectoryRepository(getApplication(), directoryDao) }

    /** v3.11.4: the directory seed is VISIBLE now. In 3.11.3 the seed ran
     *  inside a runCatching whose result was discarded — if the tables
     *  stayed empty, nothing anywhere said why, and both detail pages
     *  showed a bare «یافت نشد». This flow carries the outcome (plus the
     *  live table counts) for the Settings diagnostics card and for the
     *  detail pages' empty-table message.
     *  MUST stay declared before the `init` block below, like
     *  archivedNumbersFlow: the init launch writes it before its first
     *  suspension point. */
    data class DirectorySeedStatus(
        val version: String = "",
        /** "unknown" | "seeding" | "seeded" | "already" | "failed" */
        val state: String = "unknown",
        val offices: Int = 0,
        val engineers: Int = 0,
        val reason: String = ""
    )
    private val _directorySeedStatus = MutableStateFlow(DirectorySeedStatus())
    val directorySeedStatus: StateFlow<DirectorySeedStatus> = _directorySeedStatus.asStateFlow()

    // Cases — non-archived only. Archived cases live in _archivedCases and
    // show nowhere else: their engineers/fees/reports/specs/follow-up edits
    // are filtered out here too, so every screen that reads these flows
    // automatically hides archived cases.
    private val _cases = MutableStateFlow<List<CaseEntity>>(emptyList())
    val cases: StateFlow<List<CaseEntity>> = _cases.asStateFlow()

    private val _archivedCases = MutableStateFlow<List<CaseEntity>>(emptyList())
    val archivedCases: StateFlow<List<CaseEntity>> = _archivedCases.asStateFlow()

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

    // Snapshots (Changes menu): one row per sync/import.
    private val _snapshots = MutableStateFlow<List<SnapshotEntity>>(emptyList())
    val snapshots: StateFlow<List<SnapshotEntity>> = _snapshots.asStateFlow()

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
        // v3.11.4: seed the directory tables from the bundled snapshot
        // asset (no network; a no-op when this version was already
        // seeded). The outcome is KEPT this time — in 3.11.3 it was
        // discarded inside runCatching, so a silent seed failure left
        // both detail pages empty with no explanation anywhere. Runs in
        // the background; the detail screens read from the local tables,
        // so they pick the data up as it lands.
        viewModelScope.launch {
            _directorySeedStatus.value = DirectorySeedStatus(state = "seeding")
            val outcome = runCatching { directoryRepository.ensureSnapshotSeeded(getApplication()) }
                .getOrElse { DirectorySnapshotSeeder.SeedOutcome.Failed(it.message ?: it.javaClass.simpleName) }
            val offices = runCatching { directoryDao.getOfficeCount() }.getOrDefault(0)
            val engineers = runCatching { directoryDao.getEngineerCount() }.getOrDefault(0)
            val version = settingsPrefs.getString(DirectorySnapshotSeeder.PREF_VERSION_KEY, "") ?: ""
            _directorySeedStatus.value = when (outcome) {
                is DirectorySnapshotSeeder.SeedOutcome.Seeded ->
                    DirectorySeedStatus(outcome.version, "seeded", offices, engineers)
                is DirectorySnapshotSeeder.SeedOutcome.AlreadySeeded ->
                    DirectorySeedStatus(version, "already", offices, engineers)
                is DirectorySnapshotSeeder.SeedOutcome.Failed ->
                    DirectorySeedStatus(version, "failed", offices, engineers, outcome.reason)
            }
        }
    }

    override fun onCleared() {
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onCleared()
    }

    private fun refreshUserRole() {
        _userRole.value = deriveUserRole(_engineers.value, _userName.value)
    }

    private fun deriveUserRole(engineers: List<EngineerEntity>, userName: String): String {
        val target = personNameKey(userName)
        if (target.isEmpty()) return ""
        val match = engineers.firstOrNull { eng ->
            val n = personNameKey(eng.name)
            n.isNotEmpty() && (n == target ||
                (target.length >= 5 && (n.contains(target) || target.contains(n))))
        } ?: return ""
        return listOf(match.role, match.discipline)
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun loadAllData() {
        // One collector per Room flow: Room re-emits on every table change,
        // so the filtered flows stay current after a sync or an import
        // without re-starting collectors. (Previously importData()
        // re-started them all.) The archived set comes from the cases
        // flow; each list flow combines with it and filters itself.
        viewModelScope.launch {
            dao.getAllCases().distinctUntilChanged().collect { cases ->
                _cases.value = cases.filter { !it.archived }
                _archivedCases.value = cases.filter { it.archived }
                archivedNumbersFlow.value = cases.filter { it.archived }.map { it.caseNumber }.toSet()
            }
        }
        viewModelScope.launch {
            combine(dao.getAllEngineers(), archivedNumbersFlow) { list, archived ->
                _engineers.value = list.filter { it.caseNumber !in archived }
                refreshUserRole()
            }.collect()
        }
        viewModelScope.launch {
            combine(dao.getAllFees(), archivedNumbersFlow) { list, archived ->
                _fees.value = list.filter { it.caseNumber !in archived }
            }.collect()
        }
        viewModelScope.launch {
            combine(dao.getAllReports(), archivedNumbersFlow) { list, archived ->
                _reports.value = list.filter { it.caseNumber !in archived }
            }.collect()
        }
        viewModelScope.launch {
            combine(dao.getAllSpecifications(), archivedNumbersFlow) { list, archived ->
                _specifications.value = list.filter { it.caseNumber !in archived }
            }.collect()
        }
        viewModelScope.launch {
            combine(dao.getAllFollowUpEdits(), archivedNumbersFlow) { list, archived ->
                _followUpEdits.value = list.filter { it.caseNumber !in archived }
            }.collect()
        }
        viewModelScope.launch {
            dao.getAllFeeReceipts().distinctUntilChanged().collect { _feeReceipts.value = it }
        }
        viewModelScope.launch {
            dao.getAllSnapshots().distinctUntilChanged().collect { _snapshots.value = it }
        }
    }

    fun getSpecificationForCase(caseNumber: String): SpecificationEntity? {
        return _specifications.value.find { it.caseNumber == caseNumber }
    }

    fun loadCaseDetail(caseNumber: String, includeArchived: Boolean = false) {
        viewModelScope.launch {
            val case = if (includeArchived) {
                (_cases.value + _archivedCases.value).find { it.caseNumber == caseNumber }
            } else {
                _cases.value.find { it.caseNumber == caseNumber }
            }
            _caseDetail.value = case
            _caseSpec.value = dao.getSpecification(caseNumber)
            _caseEngineers.value = dao.getEngineersForCase(caseNumber)
            _caseFees.value = dao.getFeesForCase(caseNumber)
            _caseReports.value = dao.getReportsForCase(caseNumber)
        }
    }

    /** Send cases to the archive (they disappear from every other menu). */
    fun archiveCases(caseNumbers: List<String>) {
        viewModelScope.launch {
            caseNumbers.forEach { dao.setCaseArchived(it, true) }
        }
    }

    /** Bring cases back out of the archive. */
    fun unarchiveCases(caseNumbers: List<String>) {
        viewModelScope.launch {
            caseNumbers.forEach { dao.setCaseArchived(it, false) }
        }
    }

    /** Save a snapshot of the current dataset (called after a sync or an
     *  import). Delegates to SnapshotStore. */
    suspend fun saveSnapshot(label: String, createdAt: String): SnapshotEntity? {
        return SnapshotStore.saveSnapshot(dao, label, createdAt)
    }

    /** Delete snapshots by id. */
    fun deleteSnapshots(ids: List<Long>) {
        viewModelScope.launch {
            if (ids.isNotEmpty()) dao.deleteSnapshots(ids)
        }
    }

    // Merge helpers: a snapshot field only overwrites when it is non-blank —
    // incomplete new info never replaces good info with empty.
    private fun mergeCase(cur: CaseEntity, snap: CaseEntity): CaseEntity = cur.copy(
        serial = snap.serial.ifBlank { cur.serial },
        owner = snap.owner.ifBlank { cur.owner },
        ownerMobile = snap.ownerMobile.ifBlank { cur.ownerMobile },
        responsibility = snap.responsibility.ifBlank { cur.responsibility },
        capacityDate = snap.capacityDate.ifBlank { cur.capacityDate },
        office = snap.office.ifBlank { cur.office },
        reportDate1 = snap.reportDate1.ifBlank { cur.reportDate1 },
        reportDate2 = snap.reportDate2.ifBlank { cur.reportDate2 },
        reportDate3 = snap.reportDate3.ifBlank { cur.reportDate3 },
        ownerFather = snap.ownerFather.ifBlank { cur.ownerFather },
        ownerNationalCode = snap.ownerNationalCode.ifBlank { cur.ownerNationalCode },
        ownerAddress = snap.ownerAddress.ifBlank { cur.ownerAddress },
        ownerZip = snap.ownerZip.ifBlank { cur.ownerZip },
        ownerTel = snap.ownerTel.ifBlank { cur.ownerTel },
        ownerBirthLoc = snap.ownerBirthLoc.ifBlank { cur.ownerBirthLoc },
        permitNumber = snap.permitNumber.ifBlank { cur.permitNumber },
        permitDate = snap.permitDate.ifBlank { cur.permitDate },
        renovationCode = snap.renovationCode.ifBlank { cur.renovationCode },
        address = snap.address.ifBlank { cur.address },
        blockNumber = snap.blockNumber.ifBlank { cur.blockNumber },
        landArea = snap.landArea.ifBlank { cur.landArea },
        municipalArea = snap.municipalArea.ifBlank { cur.municipalArea },
        effectiveArea = snap.effectiveArea.ifBlank { cur.effectiveArea },
        floors = snap.floors.ifBlank { cur.floors },
        units = snap.units.ifBlank { cur.units },
        buildingGroup = snap.buildingGroup.ifBlank { cur.buildingGroup },
        structureType = snap.structureType.ifBlank { cur.structureType },
        usageType = snap.usageType.ifBlank { cur.usageType },
        planZone = snap.planZone.ifBlank { cur.planZone },
        issuer = snap.issuer.ifBlank { cur.issuer },
        stateTitle = snap.stateTitle.ifBlank { cur.stateTitle },
        workType = snap.workType.ifBlank { cur.workType },
        discipline = snap.discipline.ifBlank { cur.discipline },
        unitTitle = snap.unitTitle.ifBlank { cur.unitTitle },
        orderPart = snap.orderPart.ifBlank { cur.orderPart },
        executionDate = snap.executionDate.ifBlank { cur.executionDate },
        dbId = snap.dbId.ifBlank { cur.dbId }
    )

    private fun mergeSpec(cur: SpecificationEntity, snap: SpecificationEntity): SpecificationEntity = cur.copy(
        address = snap.address.ifBlank { cur.address },
        buildingGroup = snap.buildingGroup.ifBlank { cur.buildingGroup },
        renovationCode = snap.renovationCode.ifBlank { cur.renovationCode },
        planInstructionNo = snap.planInstructionNo.ifBlank { cur.planInstructionNo },
        planInstructionType = snap.planInstructionType.ifBlank { cur.planInstructionType },
        planInstructionDate = snap.planInstructionDate.ifBlank { cur.planInstructionDate },
        landArea = snap.landArea.ifBlank { cur.landArea },
        parafArea = snap.parafArea.ifBlank { cur.parafArea },
        capacityArea = snap.capacityArea.ifBlank { cur.capacityArea },
        structureType = snap.structureType.ifBlank { cur.structureType },
        blockTitle = snap.blockTitle.ifBlank { cur.blockTitle },
        blockCount = snap.blockCount.ifBlank { cur.blockCount },
        floors = snap.floors.ifBlank { cur.floors },
        units = snap.units.ifBlank { cur.units },
        issuer = snap.issuer.ifBlank { cur.issuer },
        permitNumber = snap.permitNumber.ifBlank { cur.permitNumber },
        permitDate = snap.permitDate.ifBlank { cur.permitDate },
        releaseDate = snap.releaseDate.ifBlank { cur.releaseDate },
        planZone = snap.planZone.ifBlank { cur.planZone },
        usageType = snap.usageType.ifBlank { cur.usageType }
    )

    private fun mergeEngineer(cur: EngineerEntity, snap: EngineerEntity): EngineerEntity = cur.copy(
        discipline = snap.discipline.ifBlank { cur.discipline },
        name = snap.name.ifBlank { cur.name },
        role = snap.role.ifBlank { cur.role },
        phone = snap.phone.ifBlank { cur.phone },
        designLevel = snap.designLevel.ifBlank { cur.designLevel },
        supervisionLevel = snap.supervisionLevel.ifBlank { cur.supervisionLevel },
        executionLevel = snap.executionLevel.ifBlank { cur.executionLevel }
    )

    private fun mergeFee(cur: FeeEntity, snap: FeeEntity): FeeEntity = cur.copy(
        discipline = snap.discipline.ifBlank { cur.discipline },
        serviceType = snap.serviceType.ifBlank { cur.serviceType },
        stage = snap.stage.ifBlank { cur.stage },
        startDate = snap.startDate.ifBlank { cur.startDate },
        endDate = snap.endDate.ifBlank { cur.endDate },
        amount = snap.amount.ifBlank { cur.amount },
        payStatus = snap.payStatus.ifBlank { cur.payStatus },
        confirmStatus = snap.confirmStatus.ifBlank { cur.confirmStatus },
        amountType = snap.amountType.ifBlank { cur.amountType },
        description = snap.description.ifBlank { cur.description }
    )

    private fun mergeReport(cur: ReportEntity, snap: ReportEntity): ReportEntity = cur.copy(
        rowNo = snap.rowNo.ifBlank { cur.rowNo },
        reportType = snap.reportType.ifBlank { cur.reportType },
        stage = snap.stage.ifBlank { cur.stage },
        engineer = snap.engineer.ifBlank { cur.engineer },
        discipline = snap.discipline.ifBlank { cur.discipline },
        visitDate = snap.visitDate.ifBlank { cur.visitDate },
        ceilingCount = snap.ceilingCount.ifBlank { cur.ceilingCount },
        indicator = snap.indicator.ifBlank { cur.indicator },
        hasFile = if (snap.imageName.isNotBlank()) snap.hasFile else cur.hasFile,
        imageName = snap.imageName.ifBlank { cur.imageName }
    )

    /** Merge several snapshots into the live data, oldest first so the newest
     *  wins. A snapshot field only overwrites when it is non-blank. Archived
     *  cases are never touched (they are excluded from updates, like in
     *  sync/import). Returns a short summary for the status message. */
    suspend fun mergeSnapshots(snapshots: List<SnapshotEntity>): String {
        val ordered = snapshots.sortedBy { it.id }
        val archived = dao.getArchivedCaseNumbers().toSet()
        var casesMerged = 0
        var specsMerged = 0
        var engineersMerged = 0
        var feesMerged = 0
        var reportsMerged = 0
        return try {
            val curCases = dao.getAllCases().first().associateBy { it.caseNumber }.toMutableMap()
            val curSpecs = dao.getAllSpecifications().first().associateBy { it.caseNumber }.toMutableMap()
            val curEngineers = dao.getAllEngineers().first().toMutableList()
            val curFees = dao.getAllFees().first().toMutableList()
            val curReports = dao.getAllReports().first().toMutableList()
            for (snap in ordered) {
                val data = SnapshotStore.fromJson(snap.dataJson) ?: continue
                for (sc in data.cases) {
                    if (sc.caseNumber.isBlank() || sc.caseNumber in archived) continue
                    val cur = curCases[sc.caseNumber]
                    if (cur == null) {
                        dao.insertCases(listOf(sc.copy(id = 0, archived = false)))
                        curCases[sc.caseNumber] = sc.copy(archived = false)
                        casesMerged++
                    } else {
                        dao.insertCases(listOf(mergeCase(cur, sc)))
                        curCases[sc.caseNumber] = mergeCase(cur, sc)
                        casesMerged++
                    }
                }
                for (ss in data.specs) {
                    if (ss.caseNumber.isBlank() || ss.caseNumber in archived) continue
                    val cur = curSpecs[ss.caseNumber]
                    if (cur == null) {
                        dao.insertSpecifications(listOf(ss.copy(id = 0)))
                        curSpecs[ss.caseNumber] = ss
                        specsMerged++
                    } else {
                        dao.insertSpecifications(listOf(mergeSpec(cur, ss)))
                        curSpecs[ss.caseNumber] = mergeSpec(cur, ss)
                        specsMerged++
                    }
                }
                for (se in data.engineers) {
                    if (se.caseNumber.isBlank() || se.caseNumber in archived) continue
                    val idx = curEngineers.indexOfFirst { it.caseNumber == se.caseNumber && it.name == se.name && it.discipline == se.discipline }
                    if (idx < 0) {
                        dao.insertEngineers(listOf(se.copy(id = 0)))
                        curEngineers.add(se)
                        engineersMerged++
                    } else {
                        val merged = mergeEngineer(curEngineers[idx], se)
                        dao.insertEngineers(listOf(merged))
                        curEngineers[idx] = merged
                        engineersMerged++
                    }
                }
                for (sf in data.fees) {
                    if (sf.caseNumber.isBlank() || sf.caseNumber in archived) continue
                    val idx = curFees.indexOfFirst { it.caseNumber == sf.caseNumber && it.stage == sf.stage && it.amountType == sf.amountType && it.serviceType == sf.serviceType }
                    if (idx < 0) {
                        dao.insertFees(listOf(sf.copy(id = 0)))
                        curFees.add(sf)
                        feesMerged++
                    } else {
                        val merged = mergeFee(curFees[idx], sf)
                        dao.insertFees(listOf(merged))
                        curFees[idx] = merged
                        feesMerged++
                    }
                }
                for (sr in data.reports) {
                    if (sr.caseNumber.isBlank() || sr.caseNumber in archived) continue
                    val idx = curReports.indexOfFirst { it.caseNumber == sr.caseNumber && it.reportType == sr.reportType && it.stage == sr.stage && it.visitDate == sr.visitDate }
                    if (idx < 0) {
                        dao.insertReports(listOf(sr.copy(id = 0)))
                        curReports.add(sr)
                        reportsMerged++
                    } else {
                        val merged = mergeReport(curReports[idx], sr)
                        dao.insertReports(listOf(merged))
                        curReports[idx] = merged
                        reportsMerged++
                    }
                }
            }
            "✅ ادغام انجام شد: $casesMerged پرونده، $specsMerged مشخصات، $engineersMerged مهندس، $feesMerged حق‌الزحمه، $reportsMerged گزارش"
        } catch (e: Exception) {
            "❌ خطا در ادغام: ${e.message}"
        }
    }

    fun searchCases(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                dao.getAllCases().first().let { _cases.value = it.filter { c -> !c.archived } }
            } else {
                dao.searchCases(query).first().let { _cases.value = it.filter { c -> !c.archived } }
            }
        }
    }

    fun importData(result: ImportResult) {
        // The Room flows in loadAllData() re-emit on their own when the
        // import writes to the database — no need to re-start collectors.
        _importResult.value = result
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    fun setImporting(value: Boolean) {
        _isImporting.value = value
    }

    /** Saves a follow-up edit, stamping the v2 timestamps: createdAt is
     *  kept from the existing row (or set now for a first edit) and
     *  updatedAt is always now — the import's last-writer-wins rule reads
     *  these (Windows writes the same Jalali "yyyy/MM/dd HH:mm" format). */
    fun saveFollowUpEdit(edit: FollowUpEditEntity) {
        viewModelScope.launch {
            val now = nowJalaliTimestamp()
            val existing = dao.getFollowUpEdit(edit.caseNumber)
            dao.insertFollowUpEdit(
                edit.copy(
                    createdAt = existing?.createdAt?.takeIf { it.isNotBlank() }
                        ?: edit.createdAt.takeIf { it.isNotBlank() } ?: now,
                    updatedAt = now
                )
            )
        }
    }

    // ---- Yazd design-offices directory (v3.10.0, read-only since v3.11.3) ----
    // No directory list screen and NO in-app update anymore (user
    // decision 2026-11-20): the complete directory ships inside the app
    // as a bundled snapshot (DirectorySnapshotSeeder) and the user
    // replaces the snapshot itself in a future version. These screens
    // only ever read the local Room tables — a tap on an office number
    // or an engineer name opens the stored record, or «یافت نشد» when
    // the snapshot has no such record. Nothing here touches the network.
    // (directoryDao / directoryRepository live at the top of this class,
    // above `init` — the init launch seeds through them; see there.)

    private val _directoryOffice = MutableStateFlow<DirectoryOfficeDetail?>(null)
    val directoryOffice: StateFlow<DirectoryOfficeDetail?> = _directoryOffice.asStateFlow()

    private val _directoryEngineer = MutableStateFlow<DirectoryEngineerProfile?>(null)
    val directoryEngineer: StateFlow<DirectoryEngineerProfile?> = _directoryEngineer.asStateFlow()

    /** The photo URL currently shown on the engineer profile: the
     *  stored (snapshot) URL first, replaced by the freshly fetched
     *  one when the detail-page fetch succeeds (v3.11.3). Null = no
     *  photo yet — the screen shows the initials fallback. Session-only:
     *  never written back to the directory tables. */
    private val _directoryEngineerPhotoUrl = MutableStateFlow<String?>(null)
    val directoryEngineerPhotoUrl: StateFlow<String?> = _directoryEngineerPhotoUrl.asStateFlow()

    private val _directoryLoading = MutableStateFlow(false)
    val directoryLoading: StateFlow<Boolean> = _directoryLoading.asStateFlow()

    private val _directoryMessage = MutableStateFlow<String?>(null)
    val directoryMessage: StateFlow<String?> = _directoryMessage.asStateFlow()

    fun clearDirectoryMessage() {
        _directoryMessage.value = null
    }

    fun clearDirectoryDetail() {
        _directoryOffice.value = null
        _directoryEngineer.value = null
        _directoryEngineerPhotoUrl.value = null
        _directoryMessage.value = null
    }

    private suspend fun loadOfficeFromDb(siteId: Int) {
        val office = directoryDao.getOffice(siteId) ?: return
        val members = directoryDao.getEngineersForOffice(siteId)
        _directoryOffice.value = DirectoryOfficeDetail(office, members)
    }

    private suspend fun loadEngineerFromDb(siteId: Int) {
        val engineer = directoryDao.getEngineer(siteId) ?: return
        _directoryEngineer.value = DirectoryEngineerProfile(
            engineer,
            directoryDao.getEducation(siteId),
            directoryDao.getLicenceHistory(siteId)
        )
    }

    /** v3.11.4: when the directory tables are empty, the detail page
     *  must show the REAL reason (seed failed / still seeding), never
     *  a bare «یافت نشد». */
    private fun directoryEmptyMessage(what: String, searched: String): String {
        val status = _directorySeedStatus.value
        return when (status.state) {
            "failed" -> "اطلاعات $what بارگذاری نشده است: ${status.reason} (جستجو: «$searched»)"
            "seeding" -> "اطلاعات $what هنوز در حال بارگذاری است — چند ثانیه دیگر دوباره تلاش کنید (جستجو: «$searched»)"
            else -> "اطلاعات $what در برنامه نیست (جستجو: «$searched»)"
        }
    }

    /** Opens an office by its displayed name (tapped in the existing UI).
     *  Local snapshot data ONLY (v3.11.3): no site fetch. Shows
     *  «یافت نشد» when the bundled snapshot has no such office.
     *  v3.11.4: the clicked value is the raw `daf_name` from the case
     *  API ("شماره 194 يزد", "دفتر شماره 89", a bare number "۱۹۴") —
     *  never the stored "دفتر 194" — so the lookup resolves it first
     *  (DirectoryParser.resolveOffice); the 3.11.3 plain comparison
     *  never matched and every office answered «یافت نشد». */
    fun loadDirectoryOfficeByName(name: String) {
        viewModelScope.launch {
            _directoryLoading.value = true
            _directoryMessage.value = null
            // v3.11.6: do NOT clear the shown detail here. The old code
            // set _directoryOffice to null first, so a reload (same
            // screen, new name — e.g. office -> member -> profile ->
            // office link) or a back-press nulling the state could land
            // while the LazyColumn was mid-composition and the screen's
            // `detail!!` re-reads crashed (phone NPE at
            // DirectoryOfficeDetailScreen.kt:148). The previous detail
            // stays visible while the new one loads; it is replaced on
            // success and cleared only when the lookup really fails.
            try {
                val offices = directoryDao.getAllOfficesList()
                val local = DirectoryParser.resolveOffice(name, offices)
                if (local != null) {
                    loadOfficeFromDb(local.siteId)
                } else if (offices.isEmpty()) {
                    _directoryOffice.value = null
                    _directoryMessage.value = directoryEmptyMessage("دفتر", name)
                } else {
                    _directoryOffice.value = null
                    val wanted = DirectoryParser.officeQueryName(name)
                    _directoryMessage.value = if (wanted.isNotBlank() && wanted != name)
                        "دفتر «$name» (جستجو: $wanted) یافت نشد"
                    else
                        "دفتر «$name» یافت نشد"
                }
            } catch (e: Exception) {
                _directoryOffice.value = null
                _directoryMessage.value = "خطا در خواندن اطلاعات دفتر: ${e.message}"
            } finally {
                _directoryLoading.value = false
            }
        }
    }

    /** Opens an engineer profile by full name ("first + family").
     *  Local snapshot data ONLY (v3.11.3): no site fetch. Shows
     *  «یافت نشد» when the bundled snapshot has no such engineer.
     *  v3.11.4: the name may carry a "مهندس" title and matches the
     *  full name, the first name, or the family separately
     *  (DirectoryParser.resolveEngineer).
     *  The photo is display-only: the stored URL shows immediately,
     *  then one detail-page fetch (session-only, never saved) replaces
     *  it with a fresh URL when one is found; failures stay silent. */
    fun loadDirectoryEngineerByName(fullName: String) {
        viewModelScope.launch {
            _directoryLoading.value = true
            _directoryMessage.value = null
            // v3.11.6: same as loadDirectoryOfficeByName — the old code
            // nulled _directoryEngineer (and the photo URL) first, so a
            // reload while the profile LazyColumn was composed crashed
            // the screen's `profile!!` re-reads (phone NPE at
            // DirectoryEngineerProfileScreen.kt:199). Keep the previous
            // profile/photo visible while loading; replace on success,
            // clear only when the lookup really fails.
            try {
                val engineers = directoryDao.getAllEngineersList()
                val local = DirectoryParser.resolveEngineer(fullName, engineers)
                if (local != null) {
                    loadEngineerFromDb(local.siteId)
                    // Show the stored photo at once (fast, works offline),
                    // then try one fresh fetch for the display.
                    _directoryEngineerPhotoUrl.value = local.photo.takeIf { it.isNotBlank() }
                    viewModelScope.launch {
                        val fresh = DirectoryPhotoLoader.fetchPhotoUrl(local.siteId)
                        // Only apply if this profile is still open — the
                        // user may have opened another engineer meanwhile.
                        if (fresh != null && _directoryEngineer.value?.engineer?.siteId == local.siteId) {
                            _directoryEngineerPhotoUrl.value = fresh
                        }
                    }
                } else if (engineers.isEmpty()) {
                    _directoryEngineer.value = null
                    _directoryEngineerPhotoUrl.value = null
                    _directoryMessage.value = directoryEmptyMessage("مهندس", fullName)
                } else {
                    _directoryEngineer.value = null
                    _directoryEngineerPhotoUrl.value = null
                    _directoryMessage.value = "مهندس «$fullName» یافت نشد"
                }
            } catch (e: Exception) {
                _directoryEngineer.value = null
                _directoryEngineerPhotoUrl.value = null
                _directoryMessage.value = "خطا در خواندن اطلاعات مهندس: ${e.message}"
            } finally {
                _directoryLoading.value = false
            }
        }
    }
}

/** An office with its member engineers, for the office detail screen. */
data class DirectoryOfficeDetail(
    val office: DirectoryOfficeEntity,
    val members: List<DirectoryEngineerEntity>
)

/** An engineer with education + licence history, for the profile screen. */
data class DirectoryEngineerProfile(
    val engineer: DirectoryEngineerEntity,
    val education: List<DirectoryEducationEntity>,
    val history: List<DirectoryLicenceHistoryEntity>
)
