package com.nezammonitor.app.ui.screens.receipts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.viewmodel.MainViewModel

private fun formatAmount(amount: Long): String = "%,d".format(amount) + " ریال"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(viewModel: MainViewModel) {
    val receipts by viewModel.feeReceipts.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("${receipts.size} دریافتی ثبت شده", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (receipts.isNotEmpty()) {
                    val total = receipts.sumOf { it.amount }
                    val feeTotal = receipts.sumOf { it.feeSum }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("جمع مبلغ دریافتی: ${formatAmount(total)}", fontSize = 12.sp)
                    Text("جمع حق‌الزحمه مرتبط: ${formatAmount(feeTotal)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("هنوز دریافتی‌ای وارد نشده — از ویندوز خروجی .nzmdata بگیرید و Import کنید.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (receipts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("دریافتی یافت نشد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(receipts, key = { it.id }) { r ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(r.receiptDate.ifBlank { "بدون تاریخ" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (r.isAdvance) {
                                    AssistChip(onClick = {}, label = { Text("علی‌الحساب", fontSize = 11.sp) })
                                }
                            }
                            Text(formatAmount(r.amount), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (r.relatedEndDatesRaw.isNotBlank()) {
                            Text("تاریخ خاتمه مرتبط: ${r.relatedEndDatesRaw}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.feeSum > 0) {
                            Text("جمع حق‌الزحمه همان تاریخ: ${formatAmount(r.feeSum)}", fontSize = 12.sp)
                            val diff = r.amount - r.feeSum
                            val diffText = when {
                                diff == 0L -> "تراز"
                                diff > 0 -> "مازاد: ${formatAmount(diff)}"
                                else -> "کسری: ${formatAmount(-diff)}"
                            }
                            Text(diffText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (diff >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        if (r.changes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("تغییرات: ${r.changes}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.isRecentlyUpdated) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("به‌روزرسانی اخیر", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
