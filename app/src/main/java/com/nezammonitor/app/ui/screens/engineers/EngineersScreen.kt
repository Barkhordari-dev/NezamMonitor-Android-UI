package com.nezammonitor.app.ui.screens.engineers

import com.nezammonitor.app.api.toPersianChars
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
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
import com.nezammonitor.app.data.SinaEngineerStore
import com.nezammonitor.app.data.formatSinaLevels
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamCountPill
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.screens.reportgen.toPersianDigitsStr
import com.nezammonitor.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EngineersScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit) {
    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Advanced filters (selection-based, from the filter dialog; every value
    // comes from the loaded engineers/cases, never typed or hardcoded)
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterRole by remember { mutableStateOf<String?>(null) }
    var filterEngineer by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Options built from the values actually in the loaded lists. Names are
    // compared in Persian script (toPersianChars) so an Arabic-script value
    // in the data matches the same name picked from the list.
    val disciplineOptions = remember(engineers) {
        engineers.map { toPersianChars(it.discipline) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val roleOptions = remember(engineers) {
        engineers.map { toPersianChars(it.role) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val engineerOptions = remember(engineers) {
        engineers.map { toPersianChars(it.name) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val ownerOptions = remember(cases, engineers) {
        val caseNumbersWithEngineers = engineers.map { it.caseNumber }.toSet()
        cases.filter { it.caseNumber in caseNumbersWithEngineers }
            .map { toPersianChars(it.owner) }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount = listOf(
        filterDiscipline != null, filterRole != null, filterEngineer != null, filterOwner != null
    ).count { it }
    fun clearAllFilters() {
        filterDiscipline = null
        filterRole = null
        filterEngineer = null
        filterOwner = null
    }

    // Case-centric: group engineers by case. A group is kept when at least one
    // of its engineers matches the engineer-level filters; only the matching
    // engineers are shown inside the card (like the reports screen).
    val caseGroups = remember(cases, engineers, searchQuery, filterDiscipline, filterRole, filterEngineer, filterOwner) {
        cases.map { case ->
            val caseEngineers = engineers.filter { it.caseNumber == case.caseNumber }
            val shownEngineers = caseEngineers.filter { eng ->
                (filterDiscipline == null || toPersianChars(eng.discipline) == filterDiscipline) &&
                (filterRole == null || toPersianChars(eng.role) == filterRole) &&
                (filterEngineer == null || toPersianChars(eng.name) == filterEngineer)
            }
            Pair(case, shownEngineers)
        }.filter { (_, engs) -> engs.isNotEmpty() }
         .filter { (case, _) ->
            (filterOwner == null || toPersianChars(case.owner) == filterOwner) &&
            (searchQuery.isBlank() ||
            case.caseNumber.contains(searchQuery, true) ||
            case.owner.contains(searchQuery, true))
        }.sortedBy { it.first.caseNumber }
    }

    if (showFilterDialog) {
        EngineersFilterDialog(
            discipline = filterDiscipline,
            role = filterRole,
            engineer = filterEngineer,
            owner = filterOwner,
            disciplineOptions = disciplineOptions,
            roleOptions = roleOptions,
            engineerOptions = engineerOptions,
            ownerOptions = ownerOptions,
            onApply = { discipline, role, engineer, owner ->
                filterDiscipline = discipline
                filterRole = role
                filterEngineer = engineer
                filterOwner = owner
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

        // Active filters, each removable on its own
        if (activeFilterCount > 0) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filterDiscipline != null) FilterChip(selected = true, onClick = { filterDiscipline = null }, label = { Text("رشته: ${filterDiscipline} ✕", fontSize = 11.sp) })
                if (filterRole != null) FilterChip(selected = true, onClick = { filterRole = null }, label = { Text("نقش: ${filterRole} ✕", fontSize = 11.sp) })
                if (filterEngineer != null) FilterChip(selected = true, onClick = { filterEngineer = null }, label = { Text("مهندس: ${filterEngineer} ✕", fontSize = 11.sp) })
                if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${filterOwner} ✕", fontSize = 11.sp) })
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        NezamCountPill(
            text = "${caseGroups.size} پرونده دارای مهندس",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (caseGroups.isEmpty()) {
                item {
                    NezamEmptyState(
                        emoji = "👷",
                        title = if (engineers.isEmpty()) "هنوز مهندسی ثبت نشده" else "موردی یافت نشد",
                        subtitle = if (engineers.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید"
                            else if (searchQuery.isNotBlank() || activeFilterCount > 0) "جستجو یا فیلتر را تغییر دهید"
                            else "جستجو را تغییر دهید"
                    )
                }
            }
            items(caseGroups, key = { it.first.caseNumber }) { (case, caseEngineers) ->
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCaseClick(case.caseNumber) },
                    copyText = "${case.caseNumber} — ${toPersianChars(case.owner)}\n" + caseEngineers.joinToString("\n") { "${toPersianChars(it.name)} (${toPersianChars(it.discipline)})" },
                    extraItems = listOf(
                        "👁 مشاهده جزئیات" to { onCaseClick(case.caseNumber) }
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(toPersianChars(case.owner), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("پرونده: ${case.caseNumber}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                Text(
                                    "${caseEngineers.size} مهندس",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        caseEngineers.take(4).forEach { eng ->
                            val sina = remember(eng.name) { SinaEngineerStore.findByName(context, eng.name) }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("• ${toPersianChars(eng.name)} (${toPersianChars(eng.discipline)})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (sina != null) Text("  ${formatSinaLevels(sina)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                val phone = sina?.phone?.ifBlank { null } ?: eng.phone.ifBlank { null }
                                if (!phone.isNullOrBlank()) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = "تماس",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp).clickable {
                                            val c = phone.replace(Regex("[^0-9+]"), "")
                                            try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$c"))) } catch (_: Exception) {}
                                        }
                                    )
                                }
                            }
                        }
                        if (caseEngineers.size > 4) {
                            Text("+ ${caseEngineers.size - 4} مهندس دیگر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Advanced filter dialog for the engineers menu (same pattern as the reports
 *  and cases filters): every filter is a selection from the values actually
 *  in the list, never typed. Draft state is applied only on «اعمال فیلتر». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EngineersFilterDialog(
    discipline: String?,
    role: String?,
    engineer: String?,
    owner: String?,
    disciplineOptions: List<String>,
    roleOptions: List<String>,
    engineerOptions: List<String>,
    ownerOptions: List<String>,
    onApply: (discipline: String?, role: String?, engineer: String?, owner: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var draftDiscipline by remember { mutableStateOf(discipline) }
    var draftRole by remember { mutableStateOf(role) }
    var draftEngineer by remember { mutableStateOf(engineer) }
    var draftOwner by remember { mutableStateOf(owner) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیلتر پیشرفته", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EngineersFilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                EngineersFilterDropdown(label = "نقش", value = draftRole, options = roleOptions, onSelect = { draftRole = it })
                EngineersFilterDropdown(label = "مهندس", value = draftEngineer, options = engineerOptions, onSelect = { draftEngineer = it })
                EngineersFilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                OutlinedButton(
                    onClick = {
                        draftDiscipline = null
                        draftRole = null
                        draftEngineer = null
                        draftOwner = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پاک کردن همهٔ فیلترها", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(draftDiscipline, draftRole, draftEngineer, draftOwner)
            }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** A selection dropdown for the engineers filter (same pattern as the reports
 *  filter): "همه" plus the values actually in the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineersFilterDropdown(
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
