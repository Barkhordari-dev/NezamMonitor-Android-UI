package com.nezammonitor.app.ui.screens.followup

import com.nezammonitor.app.api.toPersianChars
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.data.FollowUpEditEntity
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.screens.reportgen.buildingGroupLetter
import com.nezammonitor.app.ui.screens.reportgen.toPersianDigitsStr
import com.nezammonitor.app.viewmodel.MainViewModel

private enum class SortField { CASE_NUMBER, OWNER, ADDRESS, REPORT_COUNT }
private enum class SortDirection { ASC, DESC }

/** Convert Persian/Arabic digits to English */
private fun String.normalizeDigits(): String {
    return this
        .replace("۰","0").replace("۱","1").replace("۲","2")
        .replace("۳","3").replace("۴","4").replace("۵","5")
        .replace("۶","6").replace("۷","7").replace("۸","8").replace("۹","9")
        .replace("٠","0").replace("١","1").replace("٢","2")
        .replace("٣","3").replace("٤","4").replace("٥","5")
        .replace("٦","6").replace("٧","7").replace("٨","8").replace("٩","9")
}

private fun dialPhone(context: android.content.Context, phone: String) {
    try {
        val cleaned = phone.normalizeDigits().replace(Regex("[^0-9+]"), "")
        if (cleaned.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$cleaned")
        context.startActivity(intent)
    } catch (_: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FollowUpScreen(viewModel: MainViewModel) {
    val cases by viewModel.cases.collectAsState()
    val edits by viewModel.followUpEdits.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var sortField by remember { mutableStateOf(SortField.CASE_NUMBER) }
    var sortDirection by remember { mutableStateOf(SortDirection.ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Advanced filters (selection-based, from the filter dialog; every value
    // comes from the loaded cases/specs, never typed or hardcoded)
    var filterHasDescription by remember { mutableStateOf<Boolean?>(null) }
    var filterHasEdit by remember { mutableStateOf<Boolean?>(null) }
    var filterGroup by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var filterHasReports by remember { mutableStateOf<Boolean?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Base items: one per case, before search/sort/filter. The group comes
    // from the case's own specification (letter only: الف/ب/ج/د).
    val baseItems = remember(cases, edits, reports, specs) {
        cases.map { case ->
            val edit = edits.find { it.caseNumber == case.caseNumber }
            val reportCount = reports.count { it.caseNumber == case.caseNumber }
            val spec = specs.find { it.caseNumber == case.caseNumber }
            val address = edit?.addressEdit?.ifBlank { null }
                ?: spec?.address?.ifBlank { null }
                ?: ""
            FollowUpDisplayItem(
                caseNumber = edit?.caseNumberEdit?.ifBlank { case.caseNumber } ?: case.caseNumber,
                originalCaseNumber = case.caseNumber,
                owner = edit?.ownerEdit?.ifBlank { case.owner } ?: case.owner,
                mobile = edit?.ownerMobileEdit?.ifBlank { case.ownerMobile } ?: case.ownerMobile,
                address = address,
                description = edit?.description ?: "",
                reportCount = reportCount,
                group = spec?.buildingGroup?.let { buildingGroupLetter(it) } ?: "",
                hasEdit = edit != null && listOf(
                    edit.description, edit.caseNumberEdit, edit.ownerEdit,
                    edit.addressEdit, edit.ownerMobileEdit
                ).any { it.isNotBlank() }
            )
        }
    }

    // Options built from the values actually in the loaded list.
    val groupOptions = remember(baseItems) {
        baseItems.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val ownerOptions = remember(baseItems) {
        baseItems.map { toPersianChars(it.owner) }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount = listOf(
        filterHasDescription != null, filterHasEdit != null, filterGroup != null,
        filterOwner != null, filterHasReports != null
    ).count { it }
    fun clearAllFilters() {
        filterHasDescription = null
        filterHasEdit = null
        filterGroup = null
        filterOwner = null
        filterHasReports = null
    }

    val items = remember(baseItems, searchQuery, sortField, sortDirection, filterHasDescription, filterHasEdit, filterGroup, filterOwner, filterHasReports) {
        baseItems.filter { item ->
            (filterHasDescription == null || item.description.isNotBlank() == filterHasDescription) &&
            (filterHasEdit == null || item.hasEdit == filterHasEdit) &&
            (filterGroup == null || item.group == filterGroup) &&
            (filterOwner == null || toPersianChars(item.owner) == filterOwner) &&
            (filterHasReports == null || (item.reportCount > 0) == filterHasReports) &&
            (searchQuery.isBlank() ||
            item.caseNumber.contains(searchQuery, true) ||
            item.owner.contains(searchQuery, true) ||
            item.mobile.contains(searchQuery, true) ||
            item.description.contains(searchQuery, true) ||
            item.address.contains(searchQuery, true))
        }.let { result ->
            when (sortField) {
                SortField.CASE_NUMBER -> result.sortedBy { it.caseNumber }
                SortField.OWNER -> result.sortedBy { it.owner }
                SortField.ADDRESS -> result.sortedBy { it.address }
                SortField.REPORT_COUNT -> result.sortedBy { it.reportCount }
            }.let { if (sortDirection == SortDirection.DESC) it.reversed() else it }
        }
    }

    if (showFilterDialog) {
        FollowUpFilterDialog(
            hasDescription = filterHasDescription,
            hasEdit = filterHasEdit,
            group = filterGroup,
            owner = filterOwner,
            hasReports = filterHasReports,
            groupOptions = groupOptions,
            ownerOptions = ownerOptions,
            onApply = { hasDescription, hasEdit, group, owner, hasReports ->
                filterHasDescription = hasDescription
                filterHasEdit = hasEdit
                filterGroup = group
                filterOwner = owner
                filterHasReports = hasReports
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
            label = "جستجوی پرونده / مالک / توضیحات..."
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
                if (filterHasDescription != null) FilterChip(selected = true, onClick = { filterHasDescription = null }, label = { Text(if (filterHasDescription == true) "دارای توضیحات ✕" else "بدون توضیحات ✕", fontSize = 11.sp) })
                if (filterHasEdit != null) FilterChip(selected = true, onClick = { filterHasEdit = null }, label = { Text(if (filterHasEdit == true) "ویرایش‌شده ✕" else "بدون ویرایش ✕", fontSize = 11.sp) })
                if (filterGroup != null) FilterChip(selected = true, onClick = { filterGroup = null }, label = { Text("گروه ${filterGroup} ✕", fontSize = 11.sp) })
                if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${filterOwner} ✕", fontSize = 11.sp) })
                if (filterHasReports != null) FilterChip(selected = true, onClick = { filterHasReports = null }, label = { Text(if (filterHasReports == true) "دارای گزارش ✕" else "بدون گزارش ✕", fontSize = 11.sp) })
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Sort controls
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { showSortMenu = true }) {
                    Text("مرتب‌سازی: ${when(sortField) {
                        SortField.CASE_NUMBER -> "شماره پرونده"
                        SortField.OWNER -> "نام مالک"
                        SortField.ADDRESS -> "آدرس"
                        SortField.REPORT_COUNT -> "تعداد گزارش"
                    }}", fontSize = 12.sp)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(text = { Text("شماره پرونده") }, onClick = { sortField = SortField.CASE_NUMBER; showSortMenu = false })
                    DropdownMenuItem(text = { Text("نام مالک") }, onClick = { sortField = SortField.OWNER; showSortMenu = false })
                    DropdownMenuItem(text = { Text("آدرس") }, onClick = { sortField = SortField.ADDRESS; showSortMenu = false })
                    DropdownMenuItem(text = { Text("تعداد گزارش") }, onClick = { sortField = SortField.REPORT_COUNT; showSortMenu = false })
                }
            }
            OutlinedButton(onClick = { sortDirection = if (sortDirection == SortDirection.ASC) SortDirection.DESC else SortDirection.ASC }) {
                Text(if (sortDirection == SortDirection.ASC) "↑ صعودی" else "↓ نزولی", fontSize = 12.sp)
            }
        }

        NezamCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${items.size} پرونده", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("با توضیحات: ${items.count { it.description.isNotBlank() }}", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    NezamEmptyState(
                        emoji = "📌",
                        title = if (cases.isEmpty()) "هنوز پرونده‌ای برای پیگیری نیست" else "موردی یافت نشد",
                        subtitle = if (cases.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید"
                            else if (searchQuery.isNotBlank() || activeFilterCount > 0) "جستجو یا فیلتر را تغییر دهید"
                            else "جستجو یا مرتب‌سازی را تغییر دهید"
                    )
                }
            }
            items(items, key = { it.originalCaseNumber }) { item ->
                var editingDescription by remember { mutableStateOf(false) }
                var editedDesc by remember(item.description) { mutableStateOf(item.description) }

                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    copyText = "${item.caseNumber}\nمالک: ${toPersianChars(item.owner)}${if (item.mobile.isNotBlank()) "\nهمراه: ${item.mobile}" else ""}${if (item.description.isNotBlank()) "\nتوضیحات: ${item.description}" else ""}"
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.caseNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("مالک: ${toPersianChars(item.owner)}", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                    Text("${item.reportCount} گزارش", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (item.mobile.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { dialPhone(context, item.mobile) }) {
                                        Icon(Icons.Default.Phone, contentDescription = "تماس", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        if (item.mobile.isNotBlank()) Text("همراه: ${item.mobile}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.address.isNotBlank()) Text("آدرس: ${item.address}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(4.dp))
                        if (editingDescription) {
                            OutlinedTextField(value = editedDesc, onValueChange = { editedDesc = it }, modifier = Modifier.fillMaxWidth(), label = { Text("توضیحات") }, minLines = 2)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { editingDescription = false; editedDesc = item.description }) { Text("لغو") }
                                TextButton(onClick = {
                                    viewModel.saveFollowUpEdit(FollowUpEditEntity(
                                        caseNumber = item.originalCaseNumber, description = editedDesc,
                                        caseNumberEdit = item.caseNumber, ownerEdit = item.owner,
                                        addressEdit = item.address, ownerMobileEdit = item.mobile
                                    ))
                                    editingDescription = false
                                }) { Text("ذخیره") }
                            }
                        } else if (item.description.isNotBlank()) {
                            Card(modifier = Modifier.clickable { editingDescription = true }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))) {
                                Text("📝 ${item.description}", modifier = Modifier.padding(8.dp), fontSize = 13.sp)
                            }
                        } else {
                            TextButton(onClick = { editingDescription = true }) { Text("+ افزودن توضیحات", fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

data class FollowUpDisplayItem(
    val caseNumber: String, val originalCaseNumber: String, val owner: String,
    val mobile: String, val address: String, val description: String, val reportCount: Int,
    /** Building group letter (الف/ب/ج/د) from the case's own specification. */
    val group: String,
    /** True when a follow-up edit exists with at least one non-blank field. */
    val hasEdit: Boolean
)

/** Advanced filter dialog for the follow-up menu (same pattern as the reports
 *  and cases filters): every filter is a selection from the values actually
 *  in the list, never typed. Draft state is applied only on «اعمال فیلتر». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FollowUpFilterDialog(
    hasDescription: Boolean?,
    hasEdit: Boolean?,
    group: String?,
    owner: String?,
    hasReports: Boolean?,
    groupOptions: List<String>,
    ownerOptions: List<String>,
    onApply: (hasDescription: Boolean?, hasEdit: Boolean?, group: String?, owner: String?, hasReports: Boolean?) -> Unit,
    onDismiss: () -> Unit
) {
    var draftHasDescription by remember { mutableStateOf(hasDescription) }
    var draftHasEdit by remember { mutableStateOf(hasEdit) }
    var draftGroup by remember { mutableStateOf(group) }
    var draftOwner by remember { mutableStateOf(owner) }
    var draftHasReports by remember { mutableStateOf(hasReports) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیلتر پیشرفته", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("توضیحات", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftHasDescription == null,
                        onClick = { draftHasDescription = null },
                        label = { Text("همه", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftHasDescription == true,
                        onClick = { draftHasDescription = true },
                        label = { Text("دارای توضیحات", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftHasDescription == false,
                        onClick = { draftHasDescription = false },
                        label = { Text("بدون توضیحات", fontSize = 12.sp) }
                    )
                }
                Text("ویرایش", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftHasEdit == null,
                        onClick = { draftHasEdit = null },
                        label = { Text("همه", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftHasEdit == true,
                        onClick = { draftHasEdit = true },
                        label = { Text("ویرایش‌شده", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftHasEdit == false,
                        onClick = { draftHasEdit = false },
                        label = { Text("بدون ویرایش", fontSize = 12.sp) }
                    )
                }
                Text("گزارش", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftHasReports == null,
                        onClick = { draftHasReports = null },
                        label = { Text("همه", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftHasReports == true,
                        onClick = { draftHasReports = true },
                        label = { Text("دارای گزارش", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftHasReports == false,
                        onClick = { draftHasReports = false },
                        label = { Text("بدون گزارش", fontSize = 12.sp) }
                    )
                }
                Text("گروه ساختمانی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                FollowUpFilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                OutlinedButton(
                    onClick = {
                        draftHasDescription = null
                        draftHasEdit = null
                        draftGroup = null
                        draftOwner = null
                        draftHasReports = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پاک کردن همهٔ فیلترها", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(draftHasDescription, draftHasEdit, draftGroup, draftOwner, draftHasReports)
            }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** A selection dropdown for the follow-up filter (same pattern as the reports
 *  filter): "همه" plus the values actually in the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowUpFilterDropdown(
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
