package com.nezammonitor.app.ui.screens.archive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.CaseEntity
import com.nezammonitor.app.data.EngineerEntity
import com.nezammonitor.app.data.FeeEntity
import com.nezammonitor.app.data.ReportEntity
import com.nezammonitor.app.data.SpecificationEntity
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.screens.reportgen.buildingGroupLetter
import com.nezammonitor.app.ui.screens.reportgen.toPersianDigitsStr
import com.nezammonitor.app.viewmodel.MainViewModel

/** Resolved (building group, discipline) for an archived case: same sources
 *  as the Cases filter — the case row itself, then its specification
 *  (group) and its engineers/fees/reports (discipline). */
private fun resolveArchiveGroupDiscipline(
    case: CaseEntity,
    spec: SpecificationEntity?,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    reports: List<ReportEntity>
): Pair<String, String> {
    val group = case.buildingGroup.ifBlank { spec?.buildingGroup ?: "" }
    val disc = case.discipline.ifBlank {
        engineers.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: fees.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: reports.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: ""
    }
    return group to disc
}

/** The Archive menu: cases the user sent here show nowhere else in the app.
 *  Tap a card to see its full details; select one or more and use
 *  "بیرون بردن از بایگانی" to bring them back. The "+" button archives
 *  more cases. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArchiveScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val archivedCases by viewModel.archivedCases.collectAsState()
    val cases by viewModel.cases.collectAsState()
    // Related records, only to resolve each archived case's group/discipline
    // for the advanced filter (same sources as the Cases filter).
    val specs by viewModel.specifications.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showPicker by remember { mutableStateOf(false) }
    var showUnarchiveConfirm by remember { mutableStateOf(false) }

    // Advanced filters (selection-based, from the filter dialog; every
    // value comes from the archived list itself, never typed or hardcoded).
    var filterGroup by remember { mutableStateOf<String?>(null) }
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var filterOffice by remember { mutableStateOf<String?>(null) }
    var filterState by remember { mutableStateOf<String?>(null) }
    var filterWorkType by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val resolvedByCase = remember(archivedCases, specs, engineers, fees, reports) {
        archivedCases.associate { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            c.caseNumber to resolveArchiveGroupDiscipline(c, spec, engineers, fees, reports)
        }
    }
    val groupOptions = remember(resolvedByCase) {
        resolvedByCase.values.mapNotNull { buildingGroupLetter(it.first).ifBlank { null } }.distinct().sorted()
    }
    val disciplineOptions = remember(resolvedByCase) {
        resolvedByCase.values.map { toPersianChars(it.second) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val ownerOptions = remember(archivedCases) {
        archivedCases.map { toPersianChars(it.owner) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val officeOptions = remember(archivedCases) {
        archivedCases.map { it.office }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val stateOptions = remember(archivedCases) {
        archivedCases.map { it.stateTitle }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val workTypeOptions = remember(archivedCases) {
        archivedCases.map { it.workType }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount = listOf(
        filterGroup != null, filterDiscipline != null, filterOwner != null,
        filterOffice != null, filterState != null, filterWorkType != null
    ).count { it }
    fun clearAllFilters() {
        filterGroup = null
        filterDiscipline = null
        filterOwner = null
        filterOffice = null
        filterState = null
        filterWorkType = null
    }

    val filteredArchived = remember(archivedCases, filterGroup, filterDiscipline, filterOwner, filterOffice, filterState, filterWorkType, resolvedByCase) {
        archivedCases.filter { c ->
            (filterGroup == null || buildingGroupLetter(resolvedByCase[c.caseNumber]?.first ?: "") == filterGroup) &&
            (filterDiscipline == null || toPersianChars(resolvedByCase[c.caseNumber]?.second ?: "") == filterDiscipline) &&
            (filterOwner == null || toPersianChars(c.owner) == filterOwner) &&
            (filterOffice == null || c.office == filterOffice) &&
            (filterState == null || c.stateTitle == filterState) &&
            (filterWorkType == null || c.workType == filterWorkType)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بایگانی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (activeFilterCount > 0) "${filteredArchived.size} از ${archivedCases.size} پرونده بایگانی‌شده"
                    else "${archivedCases.size} پرونده بایگانی‌شده",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                Button(onClick = { showPicker = true }) {
                    Text("➕ بایگانی پرونده", fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "پرونده‌های بایگانی‌شده در هیچ‌جای دیگر برنامه نشان داده نمی‌شوند. برای دیدن جزئیات کامل روی هر پرونده بزن.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (archivedCases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { showFilterDialog = true }, modifier = Modifier.weight(1f)) {
                        Text(
                            if (activeFilterCount > 0) "🔍 فیلتر پیشرفته (${toPersianDigitsStr(activeFilterCount.toString())})"
                            else "🔍 فیلتر پیشرفته",
                            fontSize = 13.sp
                        )
                    }
                    if (activeFilterCount > 0) {
                        OutlinedButton(onClick = { clearAllFilters() }) {
                            Text("پاک کردن", fontSize = 13.sp)
                        }
                    }
                }

                // Active filters, each removable on its own
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filterGroup != null) FilterChip(selected = true, onClick = { filterGroup = null }, label = { Text("گروه ${filterGroup} ✕", fontSize = 11.sp) })
                        if (filterDiscipline != null) FilterChip(selected = true, onClick = { filterDiscipline = null }, label = { Text("رشته: ${toPersianChars(filterDiscipline ?: "")} ✕", fontSize = 11.sp) })
                        if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${toPersianChars(filterOwner ?: "")} ✕", fontSize = 11.sp) })
                        if (filterOffice != null) FilterChip(selected = true, onClick = { filterOffice = null }, label = { Text("دفتر: ${filterOffice} ✕", fontSize = 11.sp) })
                        if (filterState != null) FilterChip(selected = true, onClick = { filterState = null }, label = { Text("وضعیت: ${filterState} ✕", fontSize = 11.sp) })
                        if (filterWorkType != null) FilterChip(selected = true, onClick = { filterWorkType = null }, label = { Text("نوع کار: ${filterWorkType} ✕", fontSize = 11.sp) })
                    }
                }
            }

            if (archivedCases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Select-all acts on the visible (filtered) rows only;
                    // with no filter active this is exactly the old behavior.
                    val visibleNumbers = filteredArchived.map { it.caseNumber }.toSet()
                    val allSelected = visibleNumbers.isNotEmpty() && visibleNumbers.all { it in selected }
                    TextButton(onClick = {
                        selected = if (allSelected) selected - visibleNumbers else selected + visibleNumbers
                    }) {
                        Text(if (allSelected) "انتخاب هیچ" else "انتخاب همه", fontSize = 13.sp)
                    }
                    if (selected.isNotEmpty()) {
                        Button(
                            onClick = { showUnarchiveConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("↩️ بیرون بردن از بایگانی (${selected.size})", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (archivedCases.isEmpty()) {
                NezamEmptyState(
                    emoji = "🗄️",
                    title = "بایگانی خالی است",
                    subtitle = "با دکمهٔ «بایگانی پرونده» پرونده‌ها را به اینجا بفرست"
                )
            } else if (filteredArchived.isEmpty()) {
                NezamEmptyState(
                    emoji = "🔍",
                    title = "موردی یافت نشد",
                    subtitle = "فیلتر را تغییر دهید"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredArchived, key = { it.caseNumber }) { case ->
                        NezamCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onCaseClick(case.caseNumber) },
                            copyText = "${case.caseNumber}\n${toPersianChars(case.owner)}${if (case.office.isNotBlank()) "\nدفتر: ${case.office}" else ""}",
                            extraItems = listOf(
                                "👁 مشاهده جزئیات" to { onCaseClick(case.caseNumber) },
                                "↩️ بیرون بردن از بایگانی" to {
                                    viewModel.unarchiveCases(listOf(case.caseNumber))
                                    android.widget.Toast.makeText(context, "✅ «${toPersianChars(case.owner)}» از بایگانی بیرون برده شد", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = case.caseNumber in selected,
                                    onCheckedChange = {
                                        selected = if (it) selected + case.caseNumber else selected - case.caseNumber
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(toPersianChars(case.owner), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(case.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (case.office.isNotBlank()) {
                                        Text("دفتر: ${case.office}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        ArchivePickerDialog(
            cases = cases,
            onArchive = { picked ->
                viewModel.archiveCases(picked)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("بیرون بردن از بایگانی") },
            text = { Text("آیا ${selected.size} پروندهٔ انتخاب‌شده از بایگانی بیرون برده شوند؟ دوباره در همهٔ منوها دیده می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unarchiveCases(selected.toList())
                    selected = emptySet()
                    showUnarchiveConfirm = false
                }) { Text("بیرون بردن") }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) { Text("لغو") }
            }
        )
    }

    if (showFilterDialog) {
        ArchiveFilterDialog(
            group = filterGroup,
            discipline = filterDiscipline,
            owner = filterOwner,
            office = filterOffice,
            state = filterState,
            workType = filterWorkType,
            groupOptions = groupOptions,
            disciplineOptions = disciplineOptions,
            ownerOptions = ownerOptions,
            officeOptions = officeOptions,
            stateOptions = stateOptions,
            workTypeOptions = workTypeOptions,
            onApply = { g, d, o, of, s, w ->
                filterGroup = g
                filterDiscipline = d
                filterOwner = o
                filterOffice = of
                filterState = s
                filterWorkType = w
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

/** Pick non-archived cases to send to the archive (search + multi-select). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivePickerDialog(
    cases: List<CaseEntity>,
    onArchive: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(cases, query) {
        if (query.isBlank()) cases
        else cases.filter {
            it.caseNumber.contains(query, true) || it.owner.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بایگانی پرونده") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NezamSearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "جستجوی شماره پرونده / مالک..."
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.caseNumber }) { case ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = case.caseNumber in picked,
                                onCheckedChange = {
                                    picked = if (it) picked + case.caseNumber else picked - case.caseNumber
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(toPersianChars(case.owner), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(case.caseNumber, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Text("موردی یافت نشد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onArchive(picked.toList()) },
                enabled = picked.isNotEmpty()
            ) { Text("بایگانی (${picked.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        }
    )
}

/** The advanced filter dialog for the Archive menu (same pattern as the
 *  cases and reports filters): group as chips, the rest as dropdowns built
 *  from the values actually in the archived list. Draft state lives inside
 *  the dialog; nothing changes until "اعمال فیلتر". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ArchiveFilterDialog(
    group: String?,
    discipline: String?,
    owner: String?,
    office: String?,
    state: String?,
    workType: String?,
    groupOptions: List<String>,
    disciplineOptions: List<String>,
    ownerOptions: List<String>,
    officeOptions: List<String>,
    stateOptions: List<String>,
    workTypeOptions: List<String>,
    onApply: (group: String?, discipline: String?, owner: String?, office: String?, state: String?, workType: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var draftGroup by remember { mutableStateOf(group) }
    var draftDiscipline by remember { mutableStateOf(discipline) }
    var draftOwner by remember { mutableStateOf(owner) }
    var draftOffice by remember { mutableStateOf(office) }
    var draftState by remember { mutableStateOf(state) }
    var draftWorkType by remember { mutableStateOf(workType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیلتر پیشرفته", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("گروه ساختمانی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draftGroup == null,
                        onClick = { draftGroup = null },
                        label = { Text("همه", fontSize = 12.sp) }
                    )
                    groupOptions.forEach { g ->
                        FilterChip(
                            selected = draftGroup == g,
                            onClick = { draftGroup = g },
                            label = { Text("گروه $g", fontSize = 12.sp) }
                        )
                    }
                }
                ArchiveFilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                ArchiveFilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                ArchiveFilterDropdown(label = "دفتر", value = draftOffice, options = officeOptions, onSelect = { draftOffice = it })
                ArchiveFilterDropdown(label = "وضعیت", value = draftState, options = stateOptions, onSelect = { draftState = it })
                ArchiveFilterDropdown(label = "نوع کار", value = draftWorkType, options = workTypeOptions, onSelect = { draftWorkType = it })
                OutlinedButton(
                    onClick = {
                        draftGroup = null
                        draftDiscipline = null
                        draftOwner = null
                        draftOffice = null
                        draftState = null
                        draftWorkType = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پاک کردن همهٔ فیلترها", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draftGroup, draftDiscipline, draftOwner, draftOffice, draftState, draftWorkType) }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** A selection dropdown for the archive filter (same pattern as the cases
 *  and reports filters): "همه" plus the values actually in the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveFilterDropdown(
    label: String,
    value: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    displayValue: (String) -> String = { toPersianChars(it) }
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.let { displayValue(it) } ?: "همه",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("همه", fontSize = 13.sp) },
                onClick = { onSelect(null); expanded = false }
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(displayValue(opt), fontSize = 13.sp) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}
