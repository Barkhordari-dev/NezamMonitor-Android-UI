package com.nezammonitor.app.ui.screens.reporthistory

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.core.content.FileProvider
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.screens.reportgen.GeneratedReportRecord
import com.nezammonitor.app.ui.screens.reportgen.deleteReportHistoryItem
import com.nezammonitor.app.ui.screens.reportgen.loadReportHistory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(loadReportHistory(context)) }
    var filterStage by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableIntStateOf(-1) }

    val filteredHistory = if (filterStage == 0) history
        else history.filter { it.stage == filterStage }

    val totalCount = history.size
    val existingCount = history.count { File(it.filePath).exists() }
    val deletedCount = totalCount - existingCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("سابقه گزارش\u200Cها") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Stats
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalCount", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("کل", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$existingCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("موجود", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$deletedCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("حذف شده", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Refresh button
            item {
                Button(
                    onClick = {
                        history = loadReportHistory(context).map { record ->
                            record.copy(fileExists = File(record.filePath).exists())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔄 بروزرسانی وضعیت")
                }
            }

            // Filter chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "همه", 1 to "مرحله اول", 2 to "مرحله دوم", 3 to "مرحله سوم").forEach { (num, name) ->
                        FilterChip(
                            selected = filterStage == num,
                            onClick = { filterStage = num },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // History items
            if (filteredHistory.isEmpty()) {
                item {
                    NezamEmptyState(
                        emoji = "📦",
                        title = "سابقه‌ای وجود ندارد",
                        subtitle = if (history.isEmpty()) "هنوز گزارشی تولید نشده است" else "با این فیلتر موردی نیست"
                    )
                }
            }

            itemsIndexed(filteredHistory) { index, record ->
                val fileExists = File(record.filePath).exists()
                val stageName = when(record.stage) { 1 -> "اول"; 2 -> "دوم"; 3 -> "سوم"; else -> "?" }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (fileExists) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (fileExists) "✅" else "⚠️",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${record.owner} - مرحله $stageName", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(record.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("📅 ${record.createdAt}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!fileExists) {
                                    Text("⚠️ فایل حذف شده", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (fileExists) {
                                TextButton(onClick = {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(record.filePath))
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }) { Text("📁 باز کردن", fontSize = 12.sp) }

                                TextButton(onClick = {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(record.filePath))
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "اشتراک\u200Cگذاری"))
                                    } catch (_: Exception) {}
                                }) { Text("📤 اشتراک", fontSize = 12.sp) }
                            }

                            TextButton(onClick = { showDeleteConfirm = index }) {
                                Text("🗑 حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm >= 0) {
        val record = filteredHistory.getOrNull(showDeleteConfirm)
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = -1 },
            title = { Text("حذف گزارش") },
            text = { Text("آیا از حذف گزارش ${record?.owner} مطمئنید؟\nفایل نیز از حافظه حذف خواهد شد.") },
            confirmButton = {
                TextButton(onClick = {
                    val originalIndex = history.indexOf(filteredHistory[showDeleteConfirm])
                    if (originalIndex >= 0) {
                        deleteReportHistoryItem(context, originalIndex)
                        history = loadReportHistory(context)
                    }
                    showDeleteConfirm = -1
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = -1 }) { Text("لغو") }
            }
        )
    }
}
