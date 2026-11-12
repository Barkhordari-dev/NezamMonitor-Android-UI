@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.nezammonitor.app.ui.screens.fees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.FeeEntity
import com.nezammonitor.app.data.FeeReceiptEntity
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.components.NezamSegmentedControl
import com.nezammonitor.app.ui.components.feeTitle
import com.nezammonitor.app.ui.components.feeTypeName
import com.nezammonitor.app.ui.components.isFeeConfirmed
import com.nezammonitor.app.ui.components.parseAmount
import com.nezammonitor.app.ui.components.payStatusDisplay
import com.nezammonitor.app.ui.components.stageNameForNumber
import com.nezammonitor.app.ui.screens.reportgen.toPersianDigitsStr
import com.nezammonitor.app.viewmodel.MainViewModel

private fun normalizeDigits(s: String): String = s
    .replace("۰","0").replace("۱","1").replace("۲","2").replace("۳","3").replace("۴","4")
    .replace("۵","5").replace("۶","6").replace("۷","7").replace("۸","8").replace("۹","9")
    .replace("٠","0").replace("١","1").replace("٢","2").replace("٣","3").replace("٤","4")
    .replace("٥","5").replace("٦","6").replace("٧","7").replace("٨","8").replace("٩","9")
private fun formatAmount(amount: Long): String = String.format(java.util.Locale.US, "%,d", amount) + " ریال"
private fun formatAmountShort(amount: Long): String = String.format(java.util.Locale.US, "%,d", amount)
private fun formatAmountDisplay(raw: String): String {
    if (raw.isBlank()) return "۰ ریال"
    val t2 = raw.trim()
    var num = t2.replace("ریال","").replace(",","").replace("٬","").replace("،","").replace(" ","").trim()
    num = normalizeDigits(num)
    val v = num.toLongOrNull() ?: return raw
    return String.format(java.util.Locale.US, "%,d", v) + " ریال"
}

/** The fee's own discipline for filtering (same fallback as feeTitle: a
 *  sibling fee of the same case when the fee itself has none) — never a
 *  hardcoded value. Compared in Persian script. */
private fun feeDisciplineOf(fee: FeeEntity, allOfCase: List<FeeEntity>): String = toPersianChars(
    fee.discipline.ifBlank {
        allOfCase.firstOrNull { it.discipline.isNotBlank() }?.discipline ?: ""
    }.trim()
)

/** Label for a fee stage value in the filter: "0" is a non-stage fee
 *  (بیمه، حسن انجام کار، …), the rest are report stages. */
private fun feeStageFilterLabel(stage: String): String {
    if (stage.trim() == "0") return "بدون مرحله"
    val n = stage.trim().toIntOrNull() ?: return "مرحله $stage"
    return "مرحله ${stageNameForNumber(n)}"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeesScreen(viewModel: MainViewModel, initialTab: Int = 0, onCaseClick: (String) -> Unit = {}) {
    var activeTab by remember { mutableStateOf(initialTab) }
    // sync if navigated via Receipts route with initialTab=1
    LaunchedEffect(initialTab) { activeTab = initialTab }

    val fees by viewModel.fees.collectAsState()
    val receipts by viewModel.feeReceipts.collectAsState()
    val cases by viewModel.cases.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Tab bar — exactly like Windows BtnTabFee / BtnTabReceipt,
        // now an animated segmented control (same two tabs, same clicks) ----
        NezamSegmentedControl(
            options = listOf("حق‌الزحمه", "دریافتی‌ها"),
            selectedIndex = activeTab,
            onSelect = { activeTab = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when (activeTab) {
            0 -> FeesTab(fees = fees, cases = cases)
            1 -> ReceiptsTab(receipts = receipts)
        }
    }
}

// ======================= Fees Tab (Windows PanelFees) =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeesTab(fees: List<FeeEntity>, cases: List<com.nezammonitor.app.data.CaseEntity>) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var payFilter by remember { mutableStateOf<String?>(null) }

    // Advanced filters (selection-based, from the filter dialog; every value
    // comes from the loaded fees/cases, never typed or hardcoded)
    var filterStage by remember { mutableStateOf<String?>(null) }
    var filterFeeType by remember { mutableStateOf<String?>(null) }
    var filterPayStatus by remember { mutableStateOf<String?>(null) }
    var filterConfirmed by remember { mutableStateOf<Boolean?>(null) }
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    val caseOwnerMap = remember(cases) { cases.associate { it.caseNumber to it.owner } }
    val feesByCase = remember(fees) { fees.groupBy { it.caseNumber } }

    // Options built from the values actually in the loaded list.
    val stageOptions = remember(fees) {
        fees.map { it.stage.trim() }.filter { it.isNotEmpty() }.distinct()
            .sortedBy { it.toIntOrNull() ?: 99 }
    }
    val feeTypeOptions = remember(fees, feesByCase) {
        fees.map { feeTypeName(it, feesByCase[it.caseNumber] ?: emptyList()) }
            .filter { it.isNotBlank() }.distinct().sorted()
    }
    val payStatusOptions = remember(fees) {
        fees.map { payStatusDisplay(it) }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val disciplineOptions = remember(fees, feesByCase) {
        fees.map { feeDisciplineOf(it, feesByCase[it.caseNumber] ?: emptyList()) }
            .filter { it.isNotBlank() }.distinct().sorted()
    }
    val ownerOptions = remember(fees, caseOwnerMap) {
        fees.mapNotNull { caseOwnerMap[it.caseNumber] }.map { toPersianChars(it) }
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    val activeFilterCount = listOf(
        filterStage != null, filterFeeType != null, filterPayStatus != null,
        filterConfirmed != null, filterDiscipline != null, filterOwner != null
    ).count { it }
    fun clearAllFilters() {
        filterStage = null
        filterFeeType = null
        filterPayStatus = null
        filterConfirmed = null
        filterDiscipline = null
        filterOwner = null
    }

    val filtered = remember(fees, searchQuery, payFilter, feesByCase, filterStage, filterFeeType, filterPayStatus, filterConfirmed, filterDiscipline, filterOwner) {
        fees.filter { f ->
            val q = searchQuery.trim()
            val caseFees = feesByCase[f.caseNumber] ?: emptyList()
            // Search matches what the user sees: the fee title (with the fee's own
            // discipline, Persian script) — Arabic-script stored values must match too.
            val matchSearch = q.isBlank() ||
                f.caseNumber.contains(q, true) ||
                (caseOwnerMap[f.caseNumber]?.contains(q, true) == true) ||
                toPersianChars(f.discipline).contains(toPersianChars(q), true) ||
                feeTitle(f, caseFees).contains(q, true) ||
                f.stage.contains(q, true) ||
                f.payStatus.contains(q, true)
            // «پرداخت شده» = حق‌الزحمه‌ای که تاریخ پایان (پرداخت) دارد.
            // قبلاً روی payStatus جستجوی زیررشته‌ای می‌شد ("پرداخت") که «پرداخت نشده»
            // را هم می‌گرفت، چون آن هم شامل «پرداخت» است — فیلتر عملاً همه را نشان می‌داد.
            val matchPay = when (val pf = payFilter) {
                null -> true
                "پرداخت" -> f.endDate.isNotBlank()
                "تایید" -> payStatusDisplay(f) == "تایید شده"
                else -> f.payStatus.contains(pf, true)
            }
            val matchStage = filterStage == null || f.stage.trim() == filterStage
            val matchFeeType = filterFeeType == null || feeTypeName(f, caseFees) == filterFeeType
            val matchPayStatus = filterPayStatus == null || payStatusDisplay(f) == filterPayStatus
            val matchConfirmed = filterConfirmed == null || isFeeConfirmed(f) == filterConfirmed
            val matchDiscipline = filterDiscipline == null || feeDisciplineOf(f, caseFees) == filterDiscipline
            val matchOwner = filterOwner == null || toPersianChars(caseOwnerMap[f.caseNumber] ?: "") == filterOwner
            matchSearch && matchPay && matchStage && matchFeeType && matchPayStatus &&
                matchConfirmed && matchDiscipline && matchOwner
        }
    }

    if (showFilterDialog) {
        FeesFilterDialog(
            stage = filterStage,
            feeType = filterFeeType,
            payStatus = filterPayStatus,
            confirmed = filterConfirmed,
            discipline = filterDiscipline,
            owner = filterOwner,
            stageOptions = stageOptions,
            feeTypeOptions = feeTypeOptions,
            payStatusOptions = payStatusOptions,
            disciplineOptions = disciplineOptions,
            ownerOptions = ownerOptions,
            onApply = { stage, feeType, payStatus, confirmed, discipline, owner ->
                filterStage = stage
                filterFeeType = feeType
                filterPayStatus = payStatus
                filterConfirmed = confirmed
                filterDiscipline = discipline
                filterOwner = owner
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    val selectedTotal = remember(filtered, selectedIds) {
        filtered.filter { it.id in selectedIds }.sumOf { parseAmount(it.amount) }
    }
    val totalAmount = remember(filtered) { filtered.sumOf { parseAmount(it.amount) } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search + filter chips
        NezamSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = "جستجو: پرونده / مالک / رشته / نوع خدمت / مرحله ..."
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = payFilter == null, onClick = { payFilter = null }, label = { Text("همه", fontSize = 11.sp) })
            FilterChip(selected = payFilter == "پرداخت", onClick = { payFilter = if (payFilter=="پرداخت") null else "پرداخت" }, label = { Text("پرداخت شده", fontSize = 11.sp) })
            FilterChip(selected = payFilter == "تایید", onClick = { payFilter = if (payFilter=="تایید") null else "تایید" }, label = { Text("تایید شده", fontSize = 11.sp) })
        }
        Spacer(modifier = Modifier.height(8.dp))
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
        // Active filters, each removable on its own
        if (activeFilterCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (filterStage != null) FilterChip(selected = true, onClick = { filterStage = null }, label = { Text("${feeStageFilterLabel(filterStage ?: "")} ✕", fontSize = 11.sp) })
                if (filterFeeType != null) FilterChip(selected = true, onClick = { filterFeeType = null }, label = { Text("نوع: ${filterFeeType} ✕", fontSize = 11.sp) })
                if (filterPayStatus != null) FilterChip(selected = true, onClick = { filterPayStatus = null }, label = { Text("پرداخت: ${filterPayStatus} ✕", fontSize = 11.sp) })
                if (filterConfirmed != null) FilterChip(selected = true, onClick = { filterConfirmed = null }, label = { Text(if (filterConfirmed == true) "تایید شده ✕" else "تایید نشده ✕", fontSize = 11.sp) })
                if (filterDiscipline != null) FilterChip(selected = true, onClick = { filterDiscipline = null }, label = { Text("رشته: ${filterDiscipline} ✕", fontSize = 11.sp) })
                if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${filterOwner} ✕", fontSize = 11.sp) })
            }
        }
        // Stats bar — like Windows footer
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} ردیف | جمع: ${formatAmountShort(totalAmount)} ریال", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (selectedIds.isNotEmpty())
                    Text("انتخاب: ${selectedIds.size} | ${formatAmount(selectedTotal)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                else
                    Text("روی هر ردیف بزنید تا انتخاب شود", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Select all row
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet()
            }) { Text(if (selectedIds.size == filtered.size && filtered.isNotEmpty()) "لغو انتخاب همه" else "انتخاب همه", fontSize = 12.sp) }
            if (selectedIds.isNotEmpty())
                TextButton(onClick = { selectedIds = emptySet() }) { Text("پاک کردن", fontSize = 12.sp) }
        }

        if (filtered.isEmpty()) {
            NezamEmptyState(
                emoji = "💰",
                title = if (fees.isEmpty()) "حق‌الزحمه‌ای یافت نشد" else "موردی با این فیلتر یافت نشد",
                subtitle = if (fees.isEmpty()) "فایل .nzmdata را وارد کنید" else "جستجو یا فیلتر را تغییر دهید"
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { fee ->
                    val isSelected = fee.id in selectedIds
                    val owner = caseOwnerMap[fee.caseNumber] ?: ""
                    NezamCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedIds = if (isSelected) selectedIds - fee.id else selectedIds + fee.id },
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        copyText = "${fee.caseNumber} — ${toPersianChars(owner)}\n${feeTitle(fee, feesByCase[fee.caseNumber] ?: emptyList())}\n${formatAmountDisplay(fee.amount)}"
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isSelected, onCheckedChange = { c -> selectedIds = if (c) selectedIds + fee.id else selectedIds - fee.id })
                            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(fee.caseNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    if (owner.isNotBlank()) Text(toPersianChars(owner), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(feeTitle(fee, feesByCase[fee.caseNumber] ?: emptyList()), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                                    AssistChip(onClick = {}, label = { Text(payStatusDisplay(fee), fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
                                }
                                if (fee.startDate.isNotBlank() || fee.endDate.isNotBlank())
                                    Text("شروع: ${fee.startDate.ifBlank { "—" }}  |  پایان: ${fee.endDate.ifBlank { "—" }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatAmountDisplay(fee.amount), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                if (fee.description.isNotBlank()) Text(fee.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Receipts Tab (Windows PanelReceipts — دریافتی) =======================
@Composable
private fun ReceiptsTab(receipts: List<FeeReceiptEntity>) {
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    val totalAmount = remember(receipts) { receipts.sumOf { it.amount } }
    val totalFeeSum = remember(receipts) { receipts.sumOf { it.feeSum } }
    val diff = totalAmount - totalFeeSum
    val selectedTotal = remember(receipts, selectedIds) { receipts.filter { it.id in selectedIds }.sumOf { it.amount } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Stats header — like Windows ReceiptStats
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${receipts.size} دریافتی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (selectedIds.isNotEmpty()) Text("انتخاب: ${selectedIds.size} | ${formatAmount(selectedTotal)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (receipts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("جمع دریافتی: ${formatAmount(totalAmount)}", fontSize = 12.sp)
                    Text("جمع حق‌الزحمه مرتبط: ${formatAmount(totalFeeSum)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val diffText = when {
                        diff == 0L -> "تراز"
                        diff > 0 -> "مازاد: ${formatAmount(diff)}"
                        else -> "کسری: ${formatAmount(-diff)}"
                    }
                    Text(diffText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("هنوز دریافتی ثبت نشده — از ویندوز خروجی .nzmdata بگیرید و Import کنید.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (receipts.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedIds = if (selectedIds.size == receipts.size) emptySet() else receipts.map { it.id }.toSet() }) {
                    Text(if (selectedIds.size == receipts.size) "لغو انتخاب همه" else "انتخاب همه", fontSize = 11.sp)
                }
                if (selectedIds.isNotEmpty()) TextButton(onClick = { selectedIds = emptySet() }) { Text("پاک کردن", fontSize = 11.sp) }
            }
        }

        if (receipts.isEmpty()) {
            NezamEmptyState(
                emoji = "🧾",
                title = "دریافتی یافت نشد",
                subtitle = "هنوز دریافتی ثبت نشده — از ویندوز خروجی .nzmdata بگیرید و Import کنید."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(receipts, key = { it.id }) { r ->
                    val isSelected = r.id in selectedIds
                    val rowDiff = r.amount - r.feeSum
                    NezamCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedIds = if (isSelected) selectedIds - r.id else selectedIds + r.id },
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        copyText = "${r.receiptDate.ifBlank { "بدون تاریخ" }}\n${formatAmount(r.amount)}"
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Checkbox(checked = isSelected, onCheckedChange = { c -> selectedIds = if (c) selectedIds + r.id else selectedIds - r.id })
                                    Text(r.receiptDate.ifBlank { "بدون تاریخ" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (r.isAdvance) {
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                            Text("علی‌الحساب", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                    if (r.isRecentlyUpdated) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(formatAmount(r.amount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // Windows columns: RelatedEndDates, FeeSum, Difference, Changes
                            if (r.relatedEndDatesRaw.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("تاریخ خاتمه مرتبط:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(r.relatedEndDatesRaw.replace(";", " ، "), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (r.feeSum > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("جمع حق‌الزحمه همان تاریخ‌ها: ${formatAmount(r.feeSum)}", fontSize = 11.sp)
                                    val diffText = when {
                                        rowDiff == 0L -> "تراز"
                                        rowDiff > 0 -> "مازاد ${formatAmount(rowDiff)}"
                                        else -> "کسری ${formatAmount(-rowDiff)}"
                                    }
                                    Text(diffText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (rowDiff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                }
                            }
                            if (r.changes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("تغییرات: ${r.changes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.isRecentlyUpdated) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                                    Text("به‌روزرسانی اخیر", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Advanced filter dialog for the fees menu (same pattern as the reports and
 *  cases filters): every filter is a selection from the values actually in
 *  the list, never typed. Draft state is applied only on «اعمال فیلتر». */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FeesFilterDialog(
    stage: String?,
    feeType: String?,
    payStatus: String?,
    confirmed: Boolean?,
    discipline: String?,
    owner: String?,
    stageOptions: List<String>,
    feeTypeOptions: List<String>,
    payStatusOptions: List<String>,
    disciplineOptions: List<String>,
    ownerOptions: List<String>,
    onApply: (stage: String?, feeType: String?, payStatus: String?, confirmed: Boolean?, discipline: String?, owner: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var draftStage by remember { mutableStateOf(stage) }
    var draftFeeType by remember { mutableStateOf(feeType) }
    var draftPayStatus by remember { mutableStateOf(payStatus) }
    var draftConfirmed by remember { mutableStateOf(confirmed) }
    var draftDiscipline by remember { mutableStateOf(discipline) }
    var draftOwner by remember { mutableStateOf(owner) }

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
                        selected = draftStage == null,
                        onClick = { draftStage = null },
                        label = { Text("همه مراحل", fontSize = 12.sp) }
                    )
                    stageOptions.forEach { s ->
                        FilterChip(
                            selected = draftStage == s,
                            onClick = { draftStage = s },
                            label = { Text(feeStageFilterLabel(s), fontSize = 12.sp) }
                        )
                    }
                }
                Text("تایید", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftConfirmed == null,
                        onClick = { draftConfirmed = null },
                        label = { Text("همه", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftConfirmed == true,
                        onClick = { draftConfirmed = true },
                        label = { Text("تایید شده", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftConfirmed == false,
                        onClick = { draftConfirmed = false },
                        label = { Text("تایید نشده", fontSize = 12.sp) }
                    )
                }
                FeesFilterDropdown(label = "نوع حق‌الزحمه", value = draftFeeType, options = feeTypeOptions, onSelect = { draftFeeType = it })
                FeesFilterDropdown(label = "وضعیت پرداخت", value = draftPayStatus, options = payStatusOptions, onSelect = { draftPayStatus = it })
                FeesFilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                FeesFilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                OutlinedButton(
                    onClick = {
                        draftStage = null
                        draftFeeType = null
                        draftPayStatus = null
                        draftConfirmed = null
                        draftDiscipline = null
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
                onApply(draftStage, draftFeeType, draftPayStatus, draftConfirmed, draftDiscipline, draftOwner)
            }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** A selection dropdown for the fees filter (same pattern as the reports
 *  filter): "همه" plus the values actually in the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeesFilterDropdown(
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
