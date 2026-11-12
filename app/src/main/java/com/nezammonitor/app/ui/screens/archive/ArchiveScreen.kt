package com.nezammonitor.app.ui.screens.archive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.CaseEntity
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.viewmodel.MainViewModel

/** The Archive menu: cases the user sent here show nowhere else in the app.
 *  Tap a card to see its full details; select one or more and use
 *  "بیرون بردن از بایگانی" to bring them back. The "+" button archives
 *  more cases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(viewModel: MainViewModel, onCaseClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val archivedCases by viewModel.archivedCases.collectAsState()
    val cases by viewModel.cases.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showPicker by remember { mutableStateOf(false) }
    var showUnarchiveConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بایگانی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${archivedCases.size} پرونده بایگانی‌شده", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { showPicker = true }) {
                    Text("➕ بایگانی پرونده", fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "پرونده‌های بایگانی‌شده در هیچ‌جای دیگر برنامه نشان داده نمی‌شوند. برای دیدن جزئیات کامل روی هر پرونده بزن.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (archivedCases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val allSelected = selected.size == archivedCases.size
                    TextButton(onClick = {
                        selected = if (allSelected) emptySet() else archivedCases.map { it.caseNumber }.toSet()
                    }) {
                        Text(if (allSelected) "انتخاب هیچ" else "انتخاب همه", fontSize = 13.sp)
                    }
                    if (selected.isNotEmpty()) {
                        Button(
                            onClick = { showUnarchiveConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("↩️ بیرون بردن از بایگانی (${selected.size})", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (archivedCases.isEmpty()) {
                NezamEmptyState(
                    emoji = "🗄️",
                    title = "بایگانی خالی است",
                    subtitle = "با دکمهٔ «بایگانی پرونده» پرونده‌ها را به اینجا بفرست"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(archivedCases, key = { it.caseNumber }) { case ->
                        NezamCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onCaseClick(case.caseNumber) },
                            copyText = "${case.caseNumber}\n${toPersianChars(case.owner)}${if (case.office.isNotBlank()) "\nدفتر: ${case.office}" else ""}",
                            extraItems = listOf(
                                "👁 مشاهده جزئیات" to { onCaseClick(case.caseNumber) },
                                "↩️ بیرون بردن از بایگانی" to {
                                    viewModel.unarchiveCases(listOf(case.caseNumber))
                                    android.widget.Toast.makeText(context, "✅ «${toPersianChars(case.owner)}» از بایگانی بیرون برده شد", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = case.caseNumber in selected,
                                    onCheckedChange = {
                                        selected = if (it) selected + case.caseNumber else selected - case.caseNumber
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(toPersianChars(case.owner), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(case.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (case.office.isNotBlank()) {
                                        Text("دفتر: ${case.office}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        ArchivePickerDialog(
            cases = cases,
            onArchive = { picked ->
                viewModel.archiveCases(picked)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }

    if (showUnarchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showUnarchiveConfirm = false },
            title = { Text("بیرون بردن از بایگانی") },
            text = { Text("آیا ${selected.size} پروندهٔ انتخاب‌شده از بایگانی بیرون برده شوند؟ دوباره در همهٔ منوها دیده می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unarchiveCases(selected.toList())
                    selected = emptySet()
                    showUnarchiveConfirm = false
                }) { Text("بیرون بردن") }
            },
            dismissButton = {
                TextButton(onClick = { showUnarchiveConfirm = false }) { Text("لغو") }
            }
        )
    }
}

/** Pick non-archived cases to send to the archive (search + multi-select). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivePickerDialog(
    cases: List<CaseEntity>,
    onArchive: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val filtered = remember(cases, query) {
        if (query.isBlank()) cases
        else cases.filter {
            it.caseNumber.contains(query, true) || it.owner.contains(query, true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بایگانی پرونده") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                NezamSearchField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "جستجوی شماره پرونده / مالک..."
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.caseNumber }) { case ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = case.caseNumber in picked,
                                onCheckedChange = {
                                    picked = if (it) picked + case.caseNumber else picked - case.caseNumber
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(toPersianChars(case.owner), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(case.caseNumber, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Text("موردی یافت نشد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onArchive(picked.toList()) },
                enabled = picked.isNotEmpty()
            ) { Text("بایگانی (${picked.size})") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("لغو") }
        }
    )
}
