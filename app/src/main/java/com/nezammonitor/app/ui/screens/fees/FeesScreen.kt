@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nezammonitor.app.ui.screens.fees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nezammonitor.app.ui.components.parseAmount
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

    val caseOwnerMap = remember(cases) { cases.associate { it.caseNumber to it.owner } }
    val feesByCase = remember(fees) { fees.groupBy { it.caseNumber } }

    val filtered = remember(fees, searchQuery, payFilter, feesByCase) {
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
                "تایید" -> f.payStatus == "تایید شده"
                else -> f.payStatus.contains(pf, true)
            }
            matchSearch && matchPay
        }
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
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
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
                                    AssistChip(onClick = {}, label = { Text(fee.payStatus.ifBlank { "—" }, fontSize = 10.sp) }, modifier = Modifier.height(22.dp))
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
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
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
