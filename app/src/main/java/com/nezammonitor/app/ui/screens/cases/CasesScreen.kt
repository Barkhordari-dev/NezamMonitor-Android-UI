package com.nezammonitor.app.ui.screens.cases

import android.content.Intent
import android.net.Uri
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
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamCountPill
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.viewmodel.MainViewModel

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
fun CasesScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit) {
    val cases by viewModel.cases.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filtered = remember(cases, searchQuery) {
        if (searchQuery.isBlank()) cases
        else cases.filter {
            it.caseNumber.contains(searchQuery, true) ||
            it.owner.contains(searchQuery, true) ||
            it.ownerMobile.contains(searchQuery, true) ||
            it.serial.contains(searchQuery, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NezamSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = "جستجوی شماره پرونده / مالک / موبایل..."
        )

        NezamCountPill(
            text = "${filtered.size} پرونده",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

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
                        subtitle = if (cases.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید" else "جستجو را تغییر دهید"
                    )
                }
            }
            items(filtered, key = { it.caseNumber }) { case ->
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCaseClick(case.caseNumber) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(case.caseNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("مالک: ${case.owner}", fontSize = 14.sp)
                            }
                            if (case.ownerMobile.isNotBlank()) {
                                IconButton(onClick = { dialPhone(context, case.ownerMobile) }) {
                                    Icon(Icons.Default.Phone, contentDescription = "تماس", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (case.ownerMobile.isNotBlank()) {
                            Text("همراه: ${case.ownerMobile}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (case.serial.isNotBlank()) {
                            Text("سریال: ${case.serial}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (case.office.isNotBlank()) {
                                Text("دفتر: ${case.office}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (case.responsibility.isNotBlank()) {
                                Text(case.responsibility, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
