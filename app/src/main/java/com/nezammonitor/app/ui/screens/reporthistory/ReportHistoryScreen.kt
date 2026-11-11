package com.nezammonitor.app.ui.screens.reporthistory

import android.content.Intent
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
import com.nezammonitor.app.api.NezamApi
import com.nezammonitor.app.data.ReportEntity
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.reportStageLabel
import com.nezammonitor.app.ui.components.stageNameForNumber
import com.nezammonitor.app.ui.components.stageNameOnly
import com.nezammonitor.app.ui.components.stageNumberFromTitle
import com.nezammonitor.app.ui.screens.reportgen.displayShamsiDate
import com.nezammonitor.app.ui.screens.reportgen.GeneratedReportRecord
import com.nezammonitor.app.ui.screens.reportgen.deleteReportHistoryItem
import com.nezammonitor.app.ui.screens.reportgen.loadReportHistory
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One row in the merged history: a report generated on this device, or a report
 *  already registered in the system (synced from the server via getGozareshat). */
private sealed interface HistoryItem {
    val date: String
    val stageNum: Int
    data class Local(val record: GeneratedReportRecord, val index: Int) : HistoryItem {
        override val date: String get() = record.createdAt
        override val stageNum: Int get() = record.stage
    }
    data class Server(val report: ReportEntity, val owner: String) : HistoryItem {
        override val date: String get() = report.visitDate
        override val stageNum: Int get() = stageNumberFromTitle(report.stage)
    }
}

private fun normalizeDigits(s: String): String = s
    .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3").replace("۴", "4")
    .replace("۵", "5").replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
    .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3").replace("٤", "4")
    .replace("٥", "5").replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")

private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "doc" -> "application/msword"
    else -> "*/*"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverReports by viewModel.reports.collectAsState()
    val cases by viewModel.cases.collectAsState()
    val ownerByCase = remember(cases) { cases.associate { it.caseNumber to it.owner } }

    var history by remember { mutableStateOf(loadReportHistory(context)) }
    var filterStage by remember { mutableIntStateOf(0) }
    var filterSource by remember { mutableIntStateOf(0) } // 0 همه، 1 در سامانه، 2 روی سیستم
    var showDeleteConfirm by remember { mutableIntStateOf(-1) }
    var downloadingId by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf("") }

    val items: List<HistoryItem> = remember(history, serverReports, ownerByCase) {
        val local = history.mapIndexed { i, r -> HistoryItem.Local(r, i) }
        val server = serverReports.map { HistoryItem.Server(it, ownerByCase[it.caseNumber] ?: "") }
        (local + server).sortedByDescending { normalizeDigits(it.date).filter { c -> c.isDigit() } }
    }
    val filteredItems = items.filter { item ->
        (filterStage == 0 || item.stageNum == filterStage) &&
            when (filterSource) {
                1 -> item is HistoryItem.Server
                2 -> item is HistoryItem.Local
                else -> true
            }
    }

    val serverCount = items.count { it is HistoryItem.Server }
    val localCount = items.count { it is HistoryItem.Local }

    fun downloadAndOpen(report: ReportEntity) {
        if (downloadingId != null || report.imageName.isBlank()) return
        downloadingId = report.id
        downloadError = ""
        scope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                try {
                    val bytes = NezamApi().downloadFile(report.imageName).getOrNull()
                        ?: return@withContext null
                    val safeName = report.imageName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                    val dir = File(context.cacheDir, "server_reports").apply { mkdirs() }
                    val file = File(dir, safeName)
                    file.writeBytes(bytes)
                    file.absolutePath
                } catch (_: Exception) {
                    null
                }
            }
            downloadingId = null
            if (savedPath != null) {
                try {
                    val file = File(savedPath)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeFor(file.name))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    downloadError = "خطا در باز کردن فایل"
                }
            } else {
                downloadError = "خطا در دانلود فایل از سامانه"
            }
        }
    }

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
            // Stats: total / in system / on device
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${items.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("کل", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$serverCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("در سامانه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$localCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Text("روی سیستم", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Refresh button (local files status)
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

            // Source filter
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "همه", 1 to "در سامانه", 2 to "روی سیستم").forEach { (num, name) ->
                        FilterChip(
                            selected = filterSource == num,
                            onClick = { filterSource = num },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Stage filter
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "همه مراحل", 1 to "مرحله اول", 2 to "مرحله دوم", 3 to "مرحله سوم", 4 to "مرحله چهارم").forEach { (num, name) ->
                        FilterChip(
                            selected = filterStage == num,
                            onClick = { filterStage = num },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (downloadError.isNotBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(downloadError, modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // History items
            if (filteredItems.isEmpty()) {
                item {
                    NezamEmptyState(
                        emoji = "📦",
                        title = "سابقه‌ای وجود ندارد",
                        subtitle = if (items.isEmpty()) "هنوز گزارشی تولید یا ثبت نشده است — گزارش تولید کنید یا بروزرسانی کنید" else "با این فیلتر موردی نیست"
                    )
                }
            }

            itemsIndexed(filteredItems) { index, item ->
                when (item) {
                    is HistoryItem.Local -> {
                        val record = item.record
                        val fileExists = File(record.filePath).exists()
                        val stageName = stageNameForNumber(record.stage)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (fileExists) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (fileExists) "✅" else "⚠️", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${toPersianChars(record.owner)} - مرحله $stageName", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(record.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("📅 ${displayShamsiDate(record.createdAt + " " + record.createdTime)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!fileExists) {
                                            Text("⚠️ فایل حذف شده", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                        Text("روی سیستم", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
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

                    is HistoryItem.Server -> {
                        val report = item.report
                        val stageLabel = reportStageLabel(report.reportType, report.stage)
                        val title = toPersianChars(report.reportType.ifBlank { "گزارش" })
                        val isDownloading = downloadingId == report.id
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🌐", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("$title${if (stageLabel.isNotBlank()) " - $stageLabel" else ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(report.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (item.owner.isNotBlank()) Text(toPersianChars(item.owner), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("📅 ${displayShamsiDate(report.visitDate)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("مهندس: ${toPersianChars(report.engineer)} | رشته: ${toPersianChars(report.discipline)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                        Text("در سامانه", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (report.hasFile && report.imageName.isNotBlank()) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Text("در حال دانلود...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            TextButton(onClick = { downloadAndOpen(report) }) {
                                                Text("⬇️ دانلود و باز کردن", fontSize = 12.sp)
                                            }
                                        }
                                    } else if (!report.hasFile) {
                                        Text("فایلی در سامانه ثبت نشده است", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog (local items only)
    if (showDeleteConfirm >= 0) {
        val item = filteredItems.getOrNull(showDeleteConfirm) as? HistoryItem.Local
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = -1 },
            title = { Text("حذف گزارش") },
            text = { Text("آیا از حذف گزارش ${toPersianChars(item?.record?.owner ?: "")} مطمئنید؟\nفایل نیز از حافظه حذف خواهد شد.") },
            confirmButton = {
                TextButton(onClick = {
                    if (item != null) {
                        deleteReportHistoryItem(context, item.index)
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
