package com.nezammonitor.app.ui.screens.fees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeeDetailScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val case by viewModel.caseDetail.collectAsState()
    val fees by viewModel.caseFees.collectAsState()
    var selectedFees by remember { mutableStateOf(setOf<Long>()) }

    val totalAmount = remember(fees) {
        fees.sumOf {
            it.amount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
        }
    }
    val selectedAmount = remember(fees, selectedFees) {
        fees.filter { it.id in selectedFees }.sumOf {
            it.amount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
        }
    }
    val paidAmount = remember(fees) {
        fees.filter { it.payStatus.contains("پرداخت") }.sumOf {
            it.amount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
        }
    }
    val confirmedAmount = remember(fees) {
        fees.filter { it.payStatus.contains("تایید") || it.confirmStatus.contains("تایید") }.sumOf {
            it.amount.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
        }
    }
    val pendingAmount = remember(fees, paidAmount, confirmedAmount) { totalAmount - paidAmount - confirmedAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("حق\\u200Cالزحمه پرونده", fontSize = 16.sp)
                        Text(case?.caseNumber ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Case header
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مالک: ${case?.owner ?: ""}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("پرونده: ${case?.caseNumber ?: ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Fee summary
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("خلاصه حق\\u200Cالزحمه", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("جمع کل:", fontSize = 13.sp); Text("%,d ریال".format(totalAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("پرداخت:", fontSize = 13.sp); Text("%,d ریال".format(paidAmount), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تأیید:", fontSize = 13.sp); Text("%,d ریال".format(confirmedAmount), color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("در انتظار:", fontSize = 13.sp); Text("%,d ریال".format(pendingAmount), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                        Text("${fees.size} ردیف", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

                        if (selectedFees.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("جمع انتخاب‌شده (${selectedFees.size} ردیف):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("%,d ریال".format(selectedAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Select all
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("انتخاب همه", fontSize = 13.sp)
                    Checkbox(
                        checked = selectedFees.size == fees.size && fees.isNotEmpty(),
                        onCheckedChange = { checked ->
                            selectedFees = if (checked) fees.map { it.id }.toSet() else emptySet()
                        }
                    )
                }
            }

            // Fee details
            items(fees) { fee ->
                val isSelected = fee.id in selectedFees
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedFees = if (checked) selectedFees + fee.id else selectedFees - fee.id
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${fee.discipline} - ${fee.amountType}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(fee.amount, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("مرحله: ${fee.stage}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (fee.startDate.isNotBlank() || fee.endDate.isNotBlank()) {
                                Text("تاریخ: ${fee.startDate.ifBlank { "_" }} → ${fee.endDate.ifBlank { "_" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("پرداخت: ${fee.payStatus}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (fee.confirmStatus.isNotBlank()) Text("تأیید: ${fee.confirmStatus}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }

            if (fees.isEmpty()) {
                item { Text("حق\\u200Cالزحمه‌ای یافت نشد", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
