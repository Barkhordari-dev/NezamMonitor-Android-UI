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
import com.nezammonitor.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineersScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit) {
    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Case-centric: group engineers by case
    val caseGroups = remember(cases, engineers, searchQuery) {
        cases.map { case ->
            val caseEngineers = engineers.filter { it.caseNumber == case.caseNumber }
            Pair(case, caseEngineers)
        }.filter { (_, engs) -> engs.isNotEmpty() }
         .filter { (case, _) ->
            searchQuery.isBlank() ||
            case.caseNumber.contains(searchQuery, true) ||
            case.owner.contains(searchQuery, true)
        }.sortedBy { it.first.caseNumber }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        NezamSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            label = "جستجوی پرونده / مالک..."
        )

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
                        subtitle = if (engineers.isEmpty()) "فایل .nzmdata را وارد کنید یا بروزرسانی کنید" else "جستجو را تغییر دهید"
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
