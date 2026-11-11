package com.nezammonitor.app.ui.screens.followup

import com.nezammonitor.app.api.toPersianChars
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val items = remember(cases, edits, reports, specs, searchQuery, sortField, sortDirection) {
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
                reportCount = reportCount
            )
        }.filter { item ->
            searchQuery.isBlank() ||
            item.caseNumber.contains(searchQuery, true) ||
            item.owner.contains(searchQuery, true) ||
            item.mobile.contains(searchQuery, true) ||
            item.description.contains(searchQuery, true) ||
            item.address.contains(searchQuery, true)
        }.let { result ->
            when (sortField) {
                SortField.CASE_NUMBER -> result.sortedBy { it.caseNumber }
                SortField.OWNER -> result.sortedBy { it.owner }
                SortField.ADDRESS -> result.sortedBy { it.address }
                SortField.REPORT_COUNT -> result.sortedBy { it.reportCount }
            }.let { if (sortDirection == SortDirection.DESC) it.reversed() else it }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NezamSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = "جستجوی پرونده / مالک / توضیحات..."
        )

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
                        subtitle = if (cases.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید" else "جستجو یا مرتب‌سازی را تغییر دهید"
                    )
                }
            }
            items(items, key = { it.originalCaseNumber }) { item ->
                var editingDescription by remember { mutableStateOf(false) }
                var editedDesc by remember(item.description) { mutableStateOf(item.description) }

                NezamCard(modifier = Modifier.fillMaxWidth()) {
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
    val mobile: String, val address: String, val description: String, val reportCount: Int
)
