package com.nezammonitor.app.ui.screens.reports

import android.content.Intent
import android.net.Uri
import com.nezammonitor.app.data.SinaEngineerStore
import com.nezammonitor.app.data.formatSinaLevels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamCountPill
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.components.reportStageLabel
import com.nezammonitor.app.ui.components.stageNameForNumber
import com.nezammonitor.app.ui.components.stageNumberFromTitle
import com.nezammonitor.app.ui.screens.reportgen.toPersianDigitsStr
import com.nezammonitor.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportsScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit = {}) {
    val cases by viewModel.cases.collectAsState()
    val reports by viewModel.reports.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Advanced filters (selection-based, from the filter dialog; every value
    // comes from the loaded reports/cases, never typed or hardcoded)
    var filterEngineer by remember { mutableStateOf<String?>(null) }
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterReportType by remember { mutableStateOf<String?>(null) }
    var filterStage by remember { mutableIntStateOf(0) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var filterDate by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Options built from the values actually in the loaded lists. Names are
    // compared in Persian script (toPersianChars) so an Arabic-script value
    // in the data matches the same name picked from the list.
    val engineerOptions = remember(reports) {
        reports.map { toPersianChars(it.engineer) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val disciplineOptions = remember(reports) {
        reports.map { toPersianChars(it.discipline) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val reportTypeOptions = remember(reports) {
        reports.map { toPersianChars(it.reportType) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val stageOptions = remember(reports) {
        reports.map { stageNumberFromTitle(it.stage) }.filter { it in 1..4 }.distinct().sorted()
    }
    val ownerOptions = remember(cases, reports) {
        val caseNumbersWithReports = reports.map { it.caseNumber }.toSet()
        cases.filter { it.caseNumber in caseNumbersWithReports }
            .map { toPersianChars(it.owner) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val dateOptions = remember(reports) {
        reports.map { it.visitDate }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount = listOf(
        filterEngineer != null, filterDiscipline != null, filterReportType != null,
        filterStage != 0, filterOwner != null, filterDate != null
    ).count { it }
    fun clearAllFilters() {
        filterEngineer = null
        filterDiscipline = null
        filterReportType = null
        filterStage = 0
        filterOwner = null
        filterDate = null
    }

    val groups = remember(cases, reports, searchQuery, filterEngineer, filterDiscipline, filterReportType, filterStage, filterOwner, filterDate) {
        cases.map { case ->
            val caseReports = reports.filter { rep ->
                rep.caseNumber == case.caseNumber &&
                (filterEngineer == null || toPersianChars(rep.engineer) == filterEngineer) &&
                (filterDiscipline == null || toPersianChars(rep.discipline) == filterDiscipline) &&
                (filterReportType == null || toPersianChars(rep.reportType) == filterReportType) &&
                (filterStage == 0 || stageNumberFromTitle(rep.stage) == filterStage) &&
                (filterDate == null || rep.visitDate == filterDate)
            }
            Pair(case, caseReports)
        }.filter { (_, reps) -> reps.isNotEmpty() }
         .filter { (case, _) ->
             (filterOwner == null || toPersianChars(case.owner) == filterOwner) &&
             (searchQuery.isBlank() || case.caseNumber.contains(searchQuery, true) || case.owner.contains(searchQuery, true))
         }
         .sortedBy { it.first.caseNumber }
    }
    val shownReportCount = remember(groups) { groups.sumOf { it.second.size } }

    if (showFilterDialog) {
        ReportsFilterDialog(
            engineer = filterEngineer,
            discipline = filterDiscipline,
            reportType = filterReportType,
            stage = filterStage,
            owner = filterOwner,
            date = filterDate,
            engineerOptions = engineerOptions,
            disciplineOptions = disciplineOptions,
            reportTypeOptions = reportTypeOptions,
            stageOptions = stageOptions,
            ownerOptions = ownerOptions,
            dateOptions = dateOptions,
            onApply = { engineer, discipline, reportType, stage, owner, date ->
                filterEngineer = engineer
                filterDiscipline = discipline
                filterReportType = reportType
                filterStage = stage
                filterOwner = owner
                filterDate = date
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NezamSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = "جستجوی پرونده / مالک..."
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        Spacer(modifier = Modifier.height(8.dp))

        NezamCountPill(
            text = "${groups.size} پرونده | $shownReportCount گزارش",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        // Active filters, each removable on its own
        if (activeFilterCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filterEngineer != null) FilterChip(selected = true, onClick = { filterEngineer = null }, label = { Text("مهندس: ${filterEngineer} ✕", fontSize = 11.sp) })
                if (filterDiscipline != null) FilterChip(selected = true, onClick = { filterDiscipline = null }, label = { Text("رشته: ${filterDiscipline} ✕", fontSize = 11.sp) })
                if (filterReportType != null) FilterChip(selected = true, onClick = { filterReportType = null }, label = { Text("نوع گزارش: ${filterReportType} ✕", fontSize = 11.sp) })
                if (filterStage != 0) FilterChip(selected = true, onClick = { filterStage = 0 }, label = { Text("مرحله ${stageNameForNumber(filterStage)} ✕", fontSize = 11.sp) })
                if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${filterOwner} ✕", fontSize = 11.sp) })
                if (filterDate != null) FilterChip(selected = true, onClick = { filterDate = null }, label = { Text("تاریخ: ${toPersianDigitsStr(filterDate ?: "")} ✕", fontSize = 11.sp) })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    NezamEmptyState(
                        emoji = "📄",
                        title = if (reports.isEmpty()) "هنوز گزارشی ثبت نشده" else "موردی یافت نشد",
                        subtitle = if (reports.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید"
                            else if (searchQuery.isNotBlank() || activeFilterCount > 0) "جستجو یا فیلتر را تغییر دهید"
                            else "جستجو را تغییر دهید"
                    )
                }
            }
            items(groups, key = { it.first.caseNumber }) { (case, caseReports) ->
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCaseClick(case.caseNumber) },
                    copyText = "${case.caseNumber} — ${toPersianChars(case.owner)}\n" + caseReports.joinToString("\n") { "${toPersianChars(it.reportType)} — ${toPersianChars(it.stage)} (${it.visitDate})" },
                    extraItems = listOf(
                        "👁 مشاهده جزئیات" to { onCaseClick(case.caseNumber) }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(toPersianChars(case.owner), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("پرونده: ${case.caseNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Text("${caseReports.size} گزارش", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        caseReports.take(2).forEach { rep ->
                            val stageLabel = reportStageLabel(rep.reportType, rep.stage)
                            Text("• ${toPersianChars(rep.reportType)}${if (stageLabel.isNotBlank()) " ($stageLabel)" else ""} - ${rep.visitDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (caseReports.size > 2) Text("+ ${caseReports.size - 2} گزارش دیگر", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Advanced filter dialog for the reports menu (same pattern as the history
 *  filter and the cases filter): every filter is a selection from the values
 *  actually in the list, never typed. Draft state is applied only on
 *  «اعمال فیلتر». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReportsFilterDialog(
    engineer: String?,
    discipline: String?,
    reportType: String?,
    stage: Int,
    owner: String?,
    date: String?,
    engineerOptions: List<String>,
    disciplineOptions: List<String>,
    reportTypeOptions: List<String>,
    stageOptions: List<Int>,
    ownerOptions: List<String>,
    dateOptions: List<String>,
    onApply: (engineer: String?, discipline: String?, reportType: String?, stage: Int, owner: String?, date: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var draftEngineer by remember { mutableStateOf(engineer) }
    var draftDiscipline by remember { mutableStateOf(discipline) }
    var draftReportType by remember { mutableStateOf(reportType) }
    var draftStage by remember { mutableIntStateOf(stage) }
    var draftOwner by remember { mutableStateOf(owner) }
    var draftDate by remember { mutableStateOf(date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیلتر پیشرفته", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("مرحله", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftStage == 0,
                        onClick = { draftStage = 0 },
                        label = { Text("همه مراحل", fontSize = 12.sp) }
                    )
                    stageOptions.forEach { n ->
                        FilterChip(
                            selected = draftStage == n,
                            onClick = { draftStage = n },
                            label = { Text("مرحله ${stageNameForNumber(n)}", fontSize = 12.sp) }
                        )
                    }
                }
                ReportsFilterDropdown(label = "مهندس", value = draftEngineer, options = engineerOptions, onSelect = { draftEngineer = it })
                ReportsFilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                ReportsFilterDropdown(label = "نوع گزارش", value = draftReportType, options = reportTypeOptions, onSelect = { draftReportType = it })
                ReportsFilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                ReportsFilterDropdown(label = "تاریخ بازدید", value = draftDate, options = dateOptions, onSelect = { draftDate = it }, displayValue = { toPersianDigitsStr(it) })
                OutlinedButton(
                    onClick = {
                        draftEngineer = null
                        draftDiscipline = null
                        draftReportType = null
                        draftStage = 0
                        draftOwner = null
                        draftDate = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پاک کردن همهٔ فیلترها", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(draftEngineer, draftDiscipline, draftReportType, draftStage, draftOwner, draftDate)
            }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** A selection dropdown for the reports filter (same pattern as the history
 *  filter and the cases filter): "همه" plus the values actually in the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsFilterDropdown(
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
