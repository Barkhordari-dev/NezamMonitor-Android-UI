package com.nezammonitor.app.ui.screens.cases

import com.nezammonitor.app.api.toPersianChars
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.data.CaseEntity
import com.nezammonitor.app.data.EngineerEntity
import com.nezammonitor.app.data.FeeEntity
import com.nezammonitor.app.data.ReportEntity
import com.nezammonitor.app.data.SpecificationEntity
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamCountPill
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.screens.reportgen.buildingGroupLetter
import com.nezammonitor.app.viewmodel.MainViewModel

/** Convert Persian/Arabic digits to English */
private fun String.normalizeDigits(): String {
    return this
        .replace("۰","0").replace("۱","1").replace("۲","2")
        .replace("۳","3").replace("۴","4").replace("۵","5")
        .replace("۶","6").replace("۷","7").replace("۸","8")
        .replace("۹","9")
        .replace("٠","0").replace("١","1").replace("٢","2")
        .replace("٣","3").replace("٤","4").replace("٥","5")
        .replace("٦","6").replace("٧","7").replace("٨","8")
        .replace("٩","9")
}

/** Normalizes text for search so Persian and Arabic letter forms match:
 *  ي/ك -> ی/ک (via toPersianChars), ة -> ه, ؤ -> و, أ/إ/آ -> ا, tatweel
 *  and ZWNJ removed, digits unified. The data comes from the API in Arabic
 *  script while the user types in Persian — without this, some Persian
 *  letters never match. */
private fun normalizeSearchText(s: String): String = toPersianChars(s.normalizeDigits())
    .replace('ة', 'ه').replace('ؤ', 'و').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    .replace("ـ", "").replace("\u200C", "").replace("\u200D", "")
    .trim()

private fun dialPhone(context: android.content.Context, phone: String) {
    try {
        val cleaned = phone.normalizeDigits().replace(Regex("[^0-9+]"), "")
        if (cleaned.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$cleaned")
        context.startActivity(intent)
    } catch (_: Exception) {}
}

/** Resolved (building group, discipline) for a case: the case row itself
 *  often has both blank, so fall back to the specification (group) and the
 *  case's engineers/fees/reports (discipline) — the same sources the case
 *  detail screen shows. */
private fun resolveCaseGroupDiscipline(
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CasesScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit) {
    val cases by viewModel.cases.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Advanced filters (selection-based, from the filter page)
    var filterGroup by remember { mutableStateOf<String?>(null) }
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var filterOffice by remember { mutableStateOf<String?>(null) }
    var filterState by remember { mutableStateOf<String?>(null) }
    var filterWorkType by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Resolved group/discipline per case (for the advanced filter)
    val resolvedByCase = remember(cases, specs, engineers, fees, reports) {
        cases.associate { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            c.caseNumber to resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
        }
    }
    val groupOptions = remember(resolvedByCase) {
        resolvedByCase.values.mapNotNull { buildingGroupLetter(it.first).ifBlank { null } }.distinct().sorted()
    }
    val disciplineOptions = remember(resolvedByCase) {
        resolvedByCase.values.map { toPersianChars(it.second) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val ownerOptions = remember(cases) { cases.map { it.owner }.filter { it.isNotBlank() }.distinct().sorted() }
    val officeOptions = remember(cases) { cases.map { it.office }.filter { it.isNotBlank() }.distinct().sorted() }
    val stateOptions = remember(cases) { cases.map { it.stateTitle }.filter { it.isNotBlank() }.distinct().sorted() }
    val workTypeOptions = remember(cases) { cases.map { it.workType }.filter { it.isNotBlank() }.distinct().sorted() }

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

    val filtered = remember(cases, searchQuery, filterGroup, filterDiscipline, filterOwner, filterOffice, filterState, filterWorkType, resolvedByCase) {
        val q = normalizeSearchText(searchQuery)
        cases.filter { c ->
            (q.isBlank() ||
                normalizeSearchText(c.caseNumber).contains(q) ||
                normalizeSearchText(c.owner).contains(q) ||
                normalizeSearchText(c.ownerMobile).contains(q) ||
                normalizeSearchText(c.serial).contains(q)) &&
            (filterGroup == null || buildingGroupLetter(resolvedByCase[c.caseNumber]?.first ?: "") == filterGroup) &&
            (filterDiscipline == null || normalizeSearchText(toPersianChars(resolvedByCase[c.caseNumber]?.second ?: "")) == normalizeSearchText(filterDiscipline ?: "")) &&
            (filterOwner == null || c.owner == filterOwner) &&
            (filterOffice == null || c.office == filterOffice) &&
            (filterState == null || c.stateTitle == filterState) &&
            (filterWorkType == null || c.workType == filterWorkType)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NezamSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = "جستجوی شماره پرونده / مالک / موبایل..."
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { showFilterDialog = true }, modifier = Modifier.weight(1f)) {
                Text(if (activeFilterCount > 0) "🔍 فیلتر پیشرفته ($activeFilterCount)" else "🔍 فیلتر پیشرفته", fontSize = 13.sp)
            }
            if (activeFilterCount > 0) {
                OutlinedButton(onClick = { clearAllFilters() }) {
                    Text("پاک کردن", fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        NezamCountPill(
            text = "${filtered.size} پرونده",
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
                if (filterGroup != null) FilterChip(selected = true, onClick = { filterGroup = null }, label = { Text("گروه ${filterGroup} ✕", fontSize = 11.sp) })
                if (filterDiscipline != null) FilterChip(selected = true, onClick = { filterDiscipline = null }, label = { Text("رشته: ${toPersianChars(filterDiscipline ?: "")} ✕", fontSize = 11.sp) })
                if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${toPersianChars(filterOwner ?: "")} ✕", fontSize = 11.sp) })
                if (filterOffice != null) FilterChip(selected = true, onClick = { filterOffice = null }, label = { Text("دفتر: ${filterOffice} ✕", fontSize = 11.sp) })
                if (filterState != null) FilterChip(selected = true, onClick = { filterState = null }, label = { Text("وضعیت: ${filterState} ✕", fontSize = 11.sp) })
                if (filterWorkType != null) FilterChip(selected = true, onClick = { filterWorkType = null }, label = { Text("نوع کار: ${filterWorkType} ✕", fontSize = 11.sp) })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    NezamEmptyState(
                        emoji = "📋",
                        title = if (cases.isEmpty()) "هنوز پرونده‌ای نیست" else "موردی یافت نشد",
                        subtitle = if (cases.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید" else "جستجو یا فیلتر را تغییر دهید"
                    )
                }
            }
            items(filtered, key = { it.caseNumber }) { case ->
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCaseClick(case.caseNumber) },
                    copyText = "${case.caseNumber}\n${toPersianChars(case.owner)}${if (case.ownerMobile.isNotBlank()) "\n${case.ownerMobile}" else ""}",
                    extraItems = listOf(
                        "👁 مشاهده جزئیات" to { onCaseClick(case.caseNumber) },
                        "📦 ارسال به بایگانی" to {
                            viewModel.archiveCases(listOf(case.caseNumber))
                            android.widget.Toast.makeText(context, "✅ «${toPersianChars(case.owner)}» به بایگانی ارسال شد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        case.caseNumber,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    toPersianChars(case.owner),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                            if (case.ownerMobile.isNotBlank()) {
                                IconButton(
                                    onClick = { dialPhone(context, case.ownerMobile) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = "تماس با مالک",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (case.ownerMobile.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("📱 ${case.ownerMobile}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (case.serial.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("سریال: ${case.serial}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (case.office.isNotBlank() || case.responsibility.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (case.office.isNotBlank()) {
                                    Text("دفتر: ${case.office}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (case.responsibility.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            case.responsibility,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        CasesFilterDialog(
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
            onClearAll = {
                clearAllFilters()
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

/** The advanced filter page for the Cases menu: every filter as a selection
 *  (group as chips, the rest as dropdowns built from the values actually in
 *  the list). Draft state lives inside the dialog; nothing changes until
 *  "اعمال فیلتر". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CasesFilterDialog(
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
    onClearAll: () -> Unit,
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
        title = { Text("فیلتر پیشرفته") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                CasesFilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                CasesFilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                CasesFilterDropdown(label = "دفتر", value = draftOffice, options = officeOptions, onSelect = { draftOffice = it })
                CasesFilterDropdown(label = "وضعیت", value = draftState, options = stateOptions, onSelect = { draftState = it })
                CasesFilterDropdown(label = "نوع کار", value = draftWorkType, options = workTypeOptions, onSelect = { draftWorkType = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draftGroup, draftDiscipline, draftOwner, draftOffice, draftState, draftWorkType) }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClearAll) { Text("پاک کردن همه", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("لغو") }
            }
        }
    )
}

/** A selection dropdown for the cases filter (same pattern as the history
 *  filter): "همه" plus the values actually in the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CasesFilterDropdown(
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
