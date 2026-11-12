package com.nezammonitor.app.ui.screens.reportgen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.*
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.copyToClipboard
import com.nezammonitor.app.ui.components.takeScreenshot
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.ui.components.personNameKey
import com.nezammonitor.app.ui.components.reportStageLabel
import com.nezammonitor.app.ui.components.stageNameForNumber
import com.nezammonitor.app.ui.components.stageNumberFromTitle
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReportGeneratorScreen(viewModel: MainViewModel, initialTab: Int = 0, onBack: () -> Unit, onNavigateToUpdate: () -> Unit = {}, onNavigateToSchedule: () -> Unit = {}, onNavigateToHelp: () -> Unit = {}) {
    val context = LocalContext.current
    val scheduleConfig = remember { ScheduleConfig(context) }
    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val scope = rememberCoroutineScope()
    val today = remember { todayJalali() }
    // The Home card "سابقه گزارش‌ها" opens this screen on the history tab,
    // like "دریافتی‌ها" opens the fees screen on its second tab. There is no
    // separate drawer item for the history anymore — it lives only here.
    var activeTab by remember { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) { activeTab = initialTab }
    // Logged-in user, for the own-reports filter: the server returns every
    // engineer's reports for a case, but the history must show only this
    // user's own reports.
    val userName by viewModel.userName.collectAsState()
    // If the name was never saved (logged in before 3.6.1, or only imported
    // .nzmdata since), the own-reports filter has no identity and shows
    // everybody's reports. Fetch it once from the API with the saved
    // credentials so the history filters itself; if there are no saved
    // credentials the history tab shows a hint instead.
    var nameFetchTried by remember { mutableStateOf(false) }
    LaunchedEffect(userName) {
        if (userName.isBlank() && !nameFetchTried) {
            nameFetchTried = true
            val updatePrefs = context.getSharedPreferences("nezam_update", Context.MODE_PRIVATE)
            val savedUser = updatePrefs.getString("username", "") ?: ""
            val savedPass = updatePrefs.getString("password", "") ?: ""
            if (updatePrefs.getBoolean("save_creds", false) && savedUser.isNotBlank() && savedPass.isNotBlank()) {
                val api = NezamApi()
                val res = withContext(Dispatchers.IO) { api.login(savedUser, savedPass) }
                if (res.isSuccess && api.userName.isNotBlank()) {
                    context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
                        .edit().putString("api_user_name", api.userName).apply()
                }
            }
        }
    }
    // Stage 4 exists only if at least one case needs it (groups ب/ج/د by discipline),
    // using the same resolved group/discipline as the case rows below and the
    // user's schedule settings (which may override the default rule).
    val anyFourStage = cases.any { c ->
        val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
        val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
        scheduleConfig.stageCount(g, d) == 4
    }

    var selectedStage by remember { mutableIntStateOf(0) }
    var useLtr by remember { mutableStateOf(false) }
    var selectedCases by remember { mutableStateOf(setOf<String>()) }
    var selectAll by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var resultFiles by remember { mutableStateOf(listOf<File>()) }
    var showDuplicateWarning by remember { mutableStateOf(false) }
    // Long-press archive menu: which case's menu is open (null = none).
    var archiveMenuCase by remember { mutableStateOf<String?>(null) }
    // Local generation history (SharedPreferences) — refreshed after each generation
    var localHistory by remember { mutableStateOf(loadReportHistory(context)) }
    // PDF size limit (0 = no limit, full quality). Saved in nezam_settings.
    var pdfMaxBytes by remember { mutableStateOf(loadPdfMaxBytes(context)) }

    val templatesDir = File("/storage/emulated/0/NezamMonitor/templates")
    val outputsDir = File("/storage/emulated/0/NezamMonitor/outputs")
    val templatesExist = templatesDir.exists() && (templatesDir.listFiles()?.any { it.name.endsWith(".docx") } == true)

    fun doGenerate() {
        if (selectedStage == 0 || selectedCases.isEmpty()) {
            statusText = "لطفاً مرحله و حداقل یک پرونده را انتخاب کنید"
            return
        }
        isGenerating = true
        statusText = ""
        resultFiles = emptyList()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    generateReports(
                        context = context,
                        cases = cases.filter { it.caseNumber in selectedCases },
                        allCases = cases,
                        engineers = engineers,
                        specs = specs,
                        stage = selectedStage,
                        useLtr = useLtr,
                        templatesDir = templatesDir,
                        outputsDir = outputsDir,
                        userName = userName
                    )
                }
                resultFiles = result.files
                statusText = result.message
                localHistory = loadReportHistory(context)
            } catch (e: Exception) {
                // Never leave the button stuck: an exception used to kill
                // this coroutine, leaving isGenerating=true and no message.
                statusText = "❌ خطا در تولید گزارش: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isGenerating = false
            }
        }
    }

    fun doGeneratePdf() {
        if (selectedStage == 0 || selectedCases.isEmpty()) {
            statusText = "لطفاً مرحله و حداقل یک پرونده را انتخاب کنید"
            return
        }
        isGenerating = true
        statusText = ""
        resultFiles = emptyList()
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    generateReportPdfs(
                        context = context,
                        cases = cases.filter { it.caseNumber in selectedCases },
                        engineers = engineers,
                        fees = fees,
                        specs = specs,
                        reports = reports,
                        localHistory = localHistory,
                        stage = selectedStage,
                        userName = userName,
                        outputsDir = outputsDir,
                        maxBytes = pdfMaxBytes
                    )
                }
                resultFiles = result.files
                statusText = result.message
                localHistory = loadReportHistory(context)
            } catch (e: Exception) {
                // Same guard as doGenerate: never leave the PDF button dead
                // with isGenerating stuck on true and no message shown.
                statusText = "❌ خطا در تولید PDF: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isGenerating = false
            }
        }
    }

    if (showDuplicateWarning) {
        AlertDialog(
            onDismissRequest = { showDuplicateWarning = false },
            title = { Text("⚠️ هشدار") },
            text = { Text("برخی گزارش‌ها قبلاً تولید شده‌اند. آیا مایلید دوباره تولید کنید؟\nفایل‌های قبلی بازنویسی خواهند شد.") },
            confirmButton = { TextButton(onClick = { showDuplicateWarning = false; doGenerate() }) { Text("تولید مجدد", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDuplicateWarning = false }) { Text("لغو") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("تولید گزارش") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("تولید گزارش", fontSize = 13.sp) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("سابقهٔ گزارش‌ها", fontSize = 13.sp) })
            }
            if (activeTab == 0) {
        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Generate button at top
            item {
                Button(
                    onClick = {
                        if (selectedStage == 0 || selectedCases.isEmpty()) { statusText = "لطفاً مرحله و حداقل یک پرونده را انتخاب کنید"; return@Button }
                        val stageName = stageNameForNumber(selectedStage)
                        val hasDuplicates = cases.filter { it.caseNumber in selectedCases }.any { case ->
                            val idx = cases.indexOf(case)
                            val rowNum = String.format("%02d", idx + 1)
                            val reversedCase = reverseCaseNumber(case.caseNumber)
                            val safeOwner = case.owner.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                            val folder = File(outputsDir, "$rowNum - $safeOwner - $reversedCase")
                            folder.exists() && folder.listFiles()?.any { it.name.contains("مرحله $stageName") } == true
                        }
                        if (hasDuplicates) showDuplicateWarning = true else doGenerate()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isGenerating && selectedStage > 0 && selectedCases.isNotEmpty() && templatesExist
                ) {
                    if (isGenerating) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(modifier = Modifier.width(8.dp)) }
                    Text("📊 تولید گزارش", fontSize = 15.sp)
                }
            }

            // Schedule settings + help, right under the generate button
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNavigateToSchedule, modifier = Modifier.weight(1f)) {
                        Text("⏱️ تغییر زمان‌بندی", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onNavigateToHelp, modifier = Modifier.weight(1f)) {
                        Text("❓ راهنما", fontSize = 13.sp)
                    }
                }
            }

            // PDF export: same report data as the Word file, rendered as a
            // vector PDF (no template needed, small file, high quality).
            item {
                OutlinedButton(
                    onClick = { doGeneratePdf() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isGenerating && selectedStage > 0 && selectedCases.isNotEmpty()
                ) {
                    if (isGenerating) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)) }
                    Text("📄 تولید گزارش PDF", fontSize = 15.sp)
                }
            }

            // PDF size limit: the app shrinks the text (fewer pages, smaller
            // file) only as far as needed to fit the chosen limit; "بدون
            // محدودیت" always generates at full quality (fontScale 1.0).
            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("حداکثر حجم فایل PDF:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pdfSizeOptions.forEach { (bytes, label) ->
                            FilterChip(
                                selected = pdfMaxBytes == bytes,
                                onClick = {
                                    pdfMaxBytes = bytes
                                    savePdfMaxBytes(context, bytes)
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (pdfMaxBytes == 0L) {
                        Text("بدون محدودیت: خروجی با کیفیت کامل و بدون تغییر تولید می‌شود.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("اگر فایل از این حد بزرگ‌تر شود، اندازهٔ متن کوچک‌تر می‌شود تا حجم در حد بماند.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (!templatesExist) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("⚠️ فایل تمپلیت یافت نشد!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                            Text("فایل‌ها را در ${templatesDir.absolutePath} قرار دهید", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                SectionTitle("انتخاب مرحله")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val stages = if (anyFourStage) listOf(1, 2, 3, 4) else listOf(1, 2, 3)
                    stages.forEach { num ->
                        FilterChip(
                            selected = selectedStage == num,
                            onClick = {
                                selectedStage = num
                                if (num == 4) {
                                    // keep only cases that actually need a 4th stage
                                    selectedCases = selectedCases.filter { cn ->
                                        val c = cases.firstOrNull { it.caseNumber == cn }
                                        if (c == null) false else {
                                            val spec = specs.firstOrNull { it.caseNumber == cn }
                                            val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
                                            scheduleConfig.stageCount(g, d) >= 4
                                        }
                                    }.toSet()
                                }
                            },
                            label = { Text("مرحله ${stageNameForNumber(num)}", fontSize = 13.sp) }
                        )
                    }
                }
            }

            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = useLtr, onCheckedChange = { useLtr = it }); Text("تولید چپ‌چین (LTR)", fontSize = 14.sp) } }

            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📂 تمپلیت‌ها: ${templatesDir.absolutePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("📂 خروجی: ${outputsDir.absolutePath}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // When stage 4 is selected, show only the rows that actually have a
            // 4th stage, so the user chooses among those.
            val visibleCases = if (selectedStage == 4) cases.filter { c ->
                val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
                val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
                scheduleConfig.stageCount(g, d) >= 4
            } else cases

            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selectAll, onCheckedChange = { selectAll = it; selectedCases = if (it) visibleCases.filter { c -> val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }; val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports); selectedStage == 0 || selectedStage <= scheduleConfig.stageCount(g, d) }.map { c -> c.caseNumber }.toSet() else emptySet() }); Text("انتخاب همه (${visibleCases.size})", fontSize = 14.sp) } }

            items(visibleCases) { case ->
                val spec = specs.firstOrNull { it.caseNumber == case.caseNumber }
                // The case row itself often lacks group/discipline/permit (the cases
                // endpoint leaves mas_title/bg_name blank): fall back to the
                // specification (group + permit, like the case detail screen) and to
                // the case's engineers/fees/reports (discipline, like the case
                // detail's engineer/fee sections).
                val (resolvedGroupRaw, resolvedDiscipline) = resolveCaseGroupDiscipline(case, spec, engineers, fees, reports)
                val stageCount = scheduleConfig.stageCount(resolvedGroupRaw, resolvedDiscipline)
                val eligible = selectedStage == 0 || selectedStage <= stageCount
                val groupLetter = buildingGroupLetter(resolvedGroupRaw)
                // The permit date is the spec's "تاریخ صدور" (shown in the case detail
                // right after "شماره پروانه") — that is the source; the case row's own
                // permit date is only a fallback.
                val permitRaw = spec?.permitDate?.ifBlank { case.permitDate } ?: case.permitDate
                val registeredStages = remember(reports, case.caseNumber, userName) {
                    reports.filter { it.caseNumber == case.caseNumber && isOwnServerReport(it, userName) }
                        .mapNotNull { stageNumberFromTitle(it.stage).takeIf { n -> n > 0 } }.toSet()
                }
                val schedule = scheduleForCase(permitRaw, resolvedGroupRaw, stageCount, registeredStages, today, monthsOverride = scheduleConfig.months(resolvedGroupRaw))
                // Generation history per stage: local file (with generation date+time)
                // and/or server report (with registration date) for this case.
                // Server reports are filtered to the current user — a stage counts
                // as registered only when THIS user submitted its report, not
                // another engineer on the same case.
                val historyByStage = remember(case, localHistory, reports, userName) {
                    historyByStageFor(case, localHistory, reports, userName)
                }
                // Long-press a row for the archive menu (combinedClickable
                // needs ExperimentalFoundationApi in this Compose version).
                Box(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().combinedClickable(enabled = eligible, onClick = { selectedCases = if (case.caseNumber in selectedCases) selectedCases - case.caseNumber else selectedCases + case.caseNumber }, onLongClick = { archiveMenuCase = case.caseNumber })) {
                    Checkbox(checked = case.caseNumber in selectedCases, enabled = eligible, onCheckedChange = { if (eligible) selectedCases = if (case.caseNumber in selectedCases) selectedCases - case.caseNumber else selectedCases + case.caseNumber })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(toPersianChars(case.owner), fontSize = 14.sp)
                        Text(case.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val groupText = if (groupLetter.isNotBlank()) "گروه $groupLetter" else "گروه نامشخص"
                        val discText = resolvedDiscipline.ifBlank { "رشته نامشخص" }
                        Text("$groupText • $discText • ${toPersianDigits(stageCount.toString())} مرحله", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        if (!eligible) {
                            Text("این پرونده ${toPersianDigits(stageCount.toString())} مرحله دارد", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                        // Schedule: due date per stage from the permit issue date,
                        // plus generation history (on device / in the system) per stage.
                        schedule.forEach { entry ->
                            val (localRec, serverRep) = historyByStage[entry.stage] ?: (null to null)
                            val history = generationHistoryText(localRec, serverRep)
                            val status = if (history.isNotBlank()) history else remainingText(entry.dueDate, today)
                            Text(
                                "مرحله ${stageNameForNumber(entry.stage)}: ${entry.dueDate.toPersianDigits()} ($status)",
                                fontSize = 11.sp,
                                color = if (history.isNotBlank() || entry.isRegistered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (schedule.isEmpty()) {
                            if (historyByStage.isEmpty()) {
                                Text("تاریخ صدور پروانه ثبت نشده است", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                historyByStage.toSortedMap().forEach { (stage, pair) ->
                                    val history = generationHistoryText(pair.first, pair.second)
                                    if (history.isNotBlank()) {
                                        Text(
                                            "مرحله ${stageNameForNumber(stage)}: $history",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                DropdownMenu(
                    expanded = archiveMenuCase == case.caseNumber,
                    onDismissRequest = { archiveMenuCase = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("📋 کپی کردن", fontSize = 13.sp) },
                        onClick = {
                            archiveMenuCase = null
                            copyToClipboard(context, "${case.caseNumber}\n${toPersianChars(case.owner)}")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📸 اسکرین شات", fontSize = 13.sp) },
                        onClick = {
                            archiveMenuCase = null
                            takeScreenshot(context)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📦 ارسال به بایگانی", fontSize = 13.sp) },
                        onClick = {
                            archiveMenuCase = null
                            selectedCases = selectedCases - case.caseNumber
                            viewModel.archiveCases(listOf(case.caseNumber))
                            statusText = "✅ «${toPersianChars(case.owner)}» به بایگانی ارسال شد"
                        }
                    )
                }
                }
            }

            if (statusText.isNotEmpty()) {
                item { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (statusText.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) { Text(statusText, modifier = Modifier.padding(12.dp), fontSize = 13.sp) } }
            }

            if (resultFiles.isNotEmpty()) {
                item { Text("فایل‌های تولید شده:", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                items(resultFiles) { file ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { try { val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(uri, mimeFor(file.name)); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }) } catch (_: Exception) {} }) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text("📄 ${file.name}", fontSize = 13.sp); Text(file.parentFile?.name ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("باز کردن →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
            } else {
                HistoryTab(
                    viewModel = viewModel,
                    localHistory = localHistory,
                    onHistoryChanged = { localHistory = it },
                    userName = userName,
                    onNavigateToUpdate = onNavigateToUpdate,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ==================== GENERATION LOGIC ====================

private data class GenerateResult(val files: List<File>, val message: String)

private fun generateReports(context: Context, cases: List<CaseEntity>, allCases: List<CaseEntity>, engineers: List<EngineerEntity>, specs: List<SpecificationEntity>, stage: Int, useLtr: Boolean, templatesDir: File, outputsDir: File, userName: String = ""): GenerateResult {
    val files = mutableListOf<File>()
    val stageName = stageNameForNumber(stage)
    // Template preference: "<discipline> مرحله X.docx" for the disciplines of the
    // selected cases (taken from each case's own data, never hardcoded), then generic.
    val disciplines = cases.map { c ->
        c.discipline.ifBlank {
            engineers.firstOrNull { it.caseNumber == c.caseNumber }?.discipline ?: ""
        }
    }.map { toPersianChars(it.trim()) }.filter { it.isNotBlank() }.distinct()
    val templateFile = findTemplate(templatesDir, stageName, disciplines) ?: return GenerateResult(emptyList(), "❌ فایل تمپلیت مرحله $stageName یافت نشد\nفایل‌ها را در ${templatesDir.absolutePath} قرار دهید")

    outputsDir.mkdirs()
    val now = Date()
    val persianDate = toPersianDate(now).replace("/", "-") // Fix: replace / with -
    val timeNow = SimpleDateFormat("HH:mm", Locale.US).format(now)
    val englishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    var count = 0

    for ((index, case) in cases.withIndex()) {
        val rowNum = String.format("%02d", index + 1)
        val reversedCase = reverseCaseNumber(case.caseNumber)
        val safeOwner = case.owner.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val folderNum = toPersianDigits(rowNum)
        val folderCase = reversedCase
        val folderName = "$folderNum - $safeOwner - $folderCase"
        val folder = File(outputsDir, folderName)
        folder.mkdirs()

        val caseEngineers = engineers.filter { it.caseNumber == case.caseNumber }
        val spec = specs.firstOrNull { it.caseNumber == case.caseNumber }

        val reportDate = if (useLtr) englishDate else persianDate
        val ltrSuffix = if (useLtr) " - LTR" else ""
        val fileName = "گزارش مرحله $stageName $safeOwner - $reportDate$ltrSuffix.docx"
        val outputFile = File(folder, fileName)

        try {
            templateFile.copyTo(outputFile, overwrite = true)
            replacePlaceholders(outputFile, case, spec, caseEngineers, persianDate, useLtr, userName)
            files.add(outputFile)
            count++

            // Save to history
            saveReportHistory(context, listOf(GeneratedReportRecord(
                caseNumber = case.caseNumber, owner = case.owner, stage = stage,
                createdAt = persianDate, createdTime = timeNow, filePath = outputFile.absolutePath,
                folderPath = folder.absolutePath, fileExists = true
            )))
        } catch (e: Exception) { return GenerateResult(files, "❌ خطا: ${case.owner}: ${e.message}") }
    }
    return GenerateResult(files, "✅ $count گزارش مرحله $stageName تولید شد\n📁 ${outputsDir.absolutePath}")
}

/** Generation history per stage for one case: local file (with generation
 *  date+time) and/or server report (with registration date). Server reports
 *  are filtered to the current user — a stage counts as registered only when
 *  THIS user submitted its report, not another engineer on the same case. */
private fun historyByStageFor(
    case: CaseEntity,
    localHistory: List<GeneratedReportRecord>,
    reports: List<ReportEntity>,
    userName: String
): Map<Int, Pair<GeneratedReportRecord?, ReportEntity?>> {
    val map = mutableMapOf<Int, Pair<GeneratedReportRecord?, ReportEntity?>>()
    localHistory.filter { it.caseNumber == case.caseNumber }.forEach { rec ->
        val cur = map[rec.stage]
        map[rec.stage] = rec to cur?.second
    }
    reports.filter { it.caseNumber == case.caseNumber && isOwnServerReport(it, userName) }.forEach { rep ->
        val n = stageNumberFromTitle(rep.stage)
        if (n > 0) {
            val cur = map[n]
            map[n] = cur?.first to rep
        }
    }
    return map
}

/** The PDF sections for one case's report: case details, the supervisor's
 *  name, the engineers, and the stage schedule with each stage's status
 *  (generated/registered/remaining). Shared by the PDF export and by
 *  printing a local history report. */
private fun buildReportPdfSections(
    case: CaseEntity,
    spec: SpecificationEntity?,
    caseEngineers: List<EngineerEntity>,
    group: String,
    discipline: String,
    schedule: List<StageSchedule>,
    historyByStage: Map<Int, Pair<GeneratedReportRecord?, ReportEntity?>>,
    today: JalaliDate,
    userName: String
): List<PdfSection> {
    val details = mutableListOf<String>()
    fun add(label: String, value: String) {
        if (value.isNotBlank()) details.add("$label: ${toPersianChars(value)}")
    }
    add("شماره پرونده", case.caseNumber)
    add("مالک", case.owner)
    add("شماره پروانه", spec?.permitNumber?.ifBlank { case.permitNumber } ?: case.permitNumber)
    add("تاریخ صدور پروانه", spec?.permitDate?.ifBlank { case.permitDate } ?: case.permitDate)
    add("گروه ساختمانی", buildingGroupLetter(group))
    add("رشته", discipline)
    add("نشانی", case.address)
    add("دفتر", case.office)
    add("نوع کاربری", spec?.usageType ?: "")
    add("نوع سازه", spec?.structureType ?: "")
    add("تعداد طبقات", spec?.floors ?: "")
    add("تعداد واحد", spec?.units ?: "")
    val sections = mutableListOf(PdfSection("مشخصات پرونده", details))
    if (userName.isNotBlank()) {
        sections.add(PdfSection("نام ناظر", listOf(toPersianChars(userName))))
    }
    if (caseEngineers.isNotEmpty()) {
        sections.add(PdfSection("ناظران", caseEngineers.map { e ->
            val role = e.role.trim()
            "${toPersianChars(e.name)} — ${toPersianChars(e.discipline)}" + if (role.isNotBlank()) " ($role)" else ""
        }))
    }
    if (schedule.isNotEmpty()) {
        sections.add(PdfSection("زمان‌بندی مراحل", schedule.map { entry ->
            val (localRec, serverRep) = historyByStage[entry.stage] ?: (null to null)
            val status = generationHistoryText(localRec, serverRep)
                .ifBlank { remainingText(entry.dueDate, today) }
            "مرحله ${stageNameForNumber(entry.stage)}: سررسید ${entry.dueDate} — $status"
        }))
    }
    return sections
}

/** Generate a PDF report for each selected case (same data as the Word
 *  report, rendered as vector text on A4 pages — sharp at any zoom and
 *  tens of kilobytes, far under the 2 MB limit). No Word template is
 *  needed. The PDF goes into the same per-case folder as its Word file
 *  and is recorded in the generation history (fileType "pdf") so it
 *  shows in the history tab next to the Word file.
 *
 *  [maxBytes] is the per-file size limit (0 = no limit, full quality).
 *  When a limit is set and a file comes out over it, the report is
 *  regenerated with a smaller font (fewer pages, smaller file) until it
 *  fits or the floor (0.6) is reached. With no limit the first,
 *  full-quality file is always kept — the output is never changed.
 *
 *  Each case is guarded on its own: one bad case no longer aborts the
 *  whole batch (that used to kill the coroutine and leave the PDF
 *  button dead with isGenerating stuck on). */
private fun generateReportPdfs(
    context: Context,
    cases: List<CaseEntity>,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    specs: List<SpecificationEntity>,
    reports: List<ReportEntity>,
    localHistory: List<GeneratedReportRecord>,
    stage: Int,
    userName: String,
    outputsDir: File,
    maxBytes: Long = 0L
): GenerateResult {
    val files = mutableListOf<File>()
    val stageName = stageNameForNumber(stage)
    val scheduleConfig = ScheduleConfig(context)
    val today = todayJalali()
    outputsDir.mkdirs()
    val persianDate = toPersianDate(Date()).replace("/", "-")
    val timeNow = SimpleDateFormat("HH:mm", Locale.US).format(Date())
    var count = 0
    var totalBytes = 0L
    var reducedCount = 0
    var failedCount = 0
    for ((index, case) in cases.withIndex()) {
        try {
            val rowNum = String.format("%02d", index + 1)
            val reversedCase = reverseCaseNumber(case.caseNumber)
            val safeOwner = case.owner.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val folderNum = toPersianDigits(rowNum)
            val folder = File(outputsDir, "$folderNum - $safeOwner - $reversedCase")
            folder.mkdirs()
            val spec = specs.firstOrNull { it.caseNumber == case.caseNumber }
            val caseEngineers = engineers.filter { it.caseNumber == case.caseNumber }
            val (resolvedGroupRaw, resolvedDiscipline) = resolveCaseGroupDiscipline(case, spec, engineers, fees, reports)
            val stageCount = scheduleConfig.stageCount(resolvedGroupRaw, resolvedDiscipline)
            val permitRaw = spec?.permitDate?.ifBlank { case.permitDate } ?: case.permitDate
            val registeredStages = reports.filter { it.caseNumber == case.caseNumber && isOwnServerReport(it, userName) }
                .mapNotNull { stageNumberFromTitle(it.stage).takeIf { n -> n > 0 } }.toSet()
            val schedule = scheduleForCase(permitRaw, resolvedGroupRaw, stageCount, registeredStages, today, monthsOverride = scheduleConfig.months(resolvedGroupRaw))
            val historyByStage = historyByStageFor(case, localHistory, reports, userName)
            val sections = buildReportPdfSections(case, spec, caseEngineers, resolvedGroupRaw, resolvedDiscipline, schedule, historyByStage, today, userName)
            val fileName = "گزارش مرحله $stageName $safeOwner - $persianDate.pdf"
            val outputFile = File(folder, fileName)
            var pdf: File? = null
            var usedScale = 1f
            for (scale in listOf(1f, 0.85f, 0.7f, 0.6f)) {
                val attempt = generateReportPdf(
                    title = "گزارش مرحله $stageName",
                    subtitle = "مانیتورینگ نظام مهندسی",
                    sections = sections,
                    footer = "تاریخ گزارش: $persianDate",
                    outputFile = outputFile,
                    fontScale = scale
                ) ?: break
                pdf = attempt
                usedScale = scale
                if (maxBytes <= 0L || attempt.length() <= maxBytes) break
            }
            if (pdf != null) {
                files.add(pdf)
                totalBytes += pdf.length()
                count++
                if (usedScale < 1f) reducedCount++
                saveReportHistory(context, listOf(GeneratedReportRecord(
                    caseNumber = case.caseNumber, owner = case.owner, stage = stage,
                    createdAt = persianDate, createdTime = timeNow, filePath = pdf.absolutePath,
                    folderPath = folder.absolutePath, fileExists = true, fileType = "pdf"
                )))
            } else {
                failedCount++
            }
        } catch (_: Exception) {
            failedCount++
        }
    }
    val sizeText = if (totalBytes < 1024 * 1024) "${totalBytes / 1024} کیلوبایت" else "${"%.1f".format(totalBytes / 1024.0 / 1024.0)} مگابایت"
    val message = when {
        count > 0 -> buildString {
            append("✅ $count گزارش PDF مرحله $stageName تولید شد ($sizeText)\n📁 ${outputsDir.absolutePath}")
            if (reducedCount > 0) append("\nℹ️ اندازهٔ متن $reducedCount گزارش برای رعایت حد حجم کوچک‌تر شد.")
            if (failedCount > 0) append("\n⚠️ $failedCount گزارش تولید نشد.")
        }
        else -> "❌ تولید PDF ناموفق بود"
    }
    return GenerateResult(files, message)
}

private fun findTemplate(templatesDir: File, stageName: String, disciplines: List<String> = emptyList()): File? {
    if (!templatesDir.exists()) return null
    // Old naming: "<discipline> مرحله <ordinal>.docx" / "مرحله <ordinal>.docx".
    // New naming: the stage number itself, "1.docx".."4.docx" (Latin or Persian digits).
    val stageNum = when (stageName) {
        "اول" -> 1
        "دوم" -> 2
        "سوم" -> 3
        "چهارم" -> 4
        else -> 0
    }
    val persianNum = when (stageNum) {
        1 -> "۱"
        2 -> "۲"
        3 -> "۳"
        4 -> "۴"
        else -> ""
    }
    val candidates = disciplines.map { "$it مرحله $stageName.docx" } +
        "مرحله $stageName.docx" +
        (if (stageNum > 0) listOf("$stageNum.docx", "$persianNum.docx") else emptyList())
    for (p in candidates) { val f = File(templatesDir, p); if (f.exists()) return f }
    return templatesDir.listFiles { f -> f.name.endsWith(".docx") }?.firstOrNull { it.name.contains(stageName) }
}

private fun replacePlaceholders(docxFile: File, case: CaseEntity, spec: SpecificationEntity?, engineers: List<EngineerEntity>, persianDate: String, useLtr: Boolean, userName: String = "") {
    try {
        val doc = XWPFDocument(FileInputStream(docxFile))
        for (para in doc.paragraphs) replaceInParagraph(para, case, spec, engineers, persianDate, useLtr, userName)
        for (table in doc.tables) for (row in table.rows) for (cell in row.tableCells) for (para in cell.paragraphs) replaceInParagraph(para, case, spec, engineers, persianDate, useLtr, userName)
        FileOutputStream(docxFile).use { doc.write(it) }; doc.close()
    } catch (_: Exception) {}
}

private fun replaceInParagraph(para: org.apache.poi.xwpf.usermodel.XWPFParagraph, case: CaseEntity, spec: SpecificationEntity?, engineers: List<EngineerEntity>, persianDate: String, useLtr: Boolean, userName: String = "") {
    val runs = para.runs; if (runs.isEmpty()) return
    var fullText = runs.joinToString("") { it.text() }

    val rawReplacements = mapOf(
        "«کد_نوسازی»" to (spec?.renovationCode ?: ""), "«تاریخ_گزارش»" to persianDate,
        "«شماره_پرونده»" to case.caseNumber, "«عنوان_بلوک»" to (spec?.blockTitle ?: ""),
        "«شماره_پروانه»" to (spec?.permitNumber ?: ""), "«تاریخ_صدور_پروانه»" to (spec?.permitDate ?: ""),
        "«تعداد_بلوک»" to (spec?.blockCount ?: ""), "«نوع_کاربری»" to (spec?.usageType ?: ""),
        "«نوع_سازه»" to (spec?.structureType ?: ""), "«گروه_ساختمانی»" to (spec?.buildingGroup ?: ""),
        "«تعداد_طبقات»" to (spec?.floors ?: ""), "«مالک»" to case.owner,
        "«نام ناظر»" to toPersianChars(userName),
        "«هماهنگکننده»" to (engineers.firstOrNull { it.discipline.contains("هماهنگ") }?.name ?: ""),
        "«ناظر_معماری»" to (engineers.firstOrNull { it.discipline.contains("معمار") }?.name ?: ""),
        "«ناظر_عمران»" to (engineers.firstOrNull { it.discipline.contains("عمران") }?.name ?: ""),
        "«ناظر_مکانیک»" to (engineers.firstOrNull { it.discipline.contains("مکانیک") || it.discipline.contains("مكانيك") }?.name ?: ""),
        "«ناظر_برق»" to (engineers.firstOrNull { it.discipline.contains("برق") }?.name ?: "")
    )

    val replacements = rawReplacements.mapValues { (key, value) ->
        val isNumeric = key in listOf("«شماره_پرونده»", "«شماره_پروانه»", "«تاریخ_گزارش»", "«تاریخ_صدور_پروانه»", "«کد_نوسازی»", "«تعداد_بلوک»", "«تعداد_طبقات»")
        val processed = if (isNumeric && useLtr) toEnglishDigits(reverseSlash(toAsciiDigits(value))) else if (isNumeric) toPersianDigits(value) else value
        processed
    }

    for ((key, value) in replacements) {
        fullText = fullText.replace(key, value)
        fullText = fullText.replace(key.replace("»", " »"), value) // Handle trailing space
    }

    if (runs.isNotEmpty()) { runs[0].setText(fullText, 0); for (i in 1 until runs.size) runs[i].setText("", 0) }
}

/** True when a server report was written by the current user (by engineer
 *  name). The server returns every engineer's reports for a case, so without
 *  this filter the history shows other engineers' reports too. When the
 *  user's identity is unknown (never logged in), nothing is filtered out.
 *  Names are compared with personNameKey (spaces removed): the API returns
 *  the user spaced ("محمد مهدی برخورداری") while reports store it joined
 *  ("محمدمهدي برخورداري") — the spaced comparison never matched, so the
 *  user's own reports were excluded (and with a blank name, everything
 *  showed). */
internal fun isOwnServerReport(report: ReportEntity, userName: String): Boolean {
    val target = personNameKey(userName)
    if (target.isEmpty()) return true
    val eng = personNameKey(report.engineer)
    if (eng.isEmpty()) return false
    return eng == target || (target.length >= 5 && (eng.contains(target) || target.contains(eng)))
}

/** Resolved (building group, discipline) for a case in the report generator.
 *  The case row itself often has both blank (the cases endpoint leaves
 *  mas_title/bg_name empty), so fall back to the same sources the case detail
 *  screen shows: the specification for the group, and the case's engineers,
 *  fees or reports for the discipline. */
private fun resolveCaseGroupDiscipline(
    case: CaseEntity,
    spec: SpecificationEntity?,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    reports: List<ReportEntity>
): Pair<String, String> {
    val group = case.buildingGroup.ifBlank { spec?.buildingGroup ?: "" }
    val disc = case.discipline.ifBlank {
        engineers.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: fees.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: reports.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: ""
    }
    return group to disc
}

/** Generation history of one stage, with Shamsi dates and time when available:
 *  "روی سیستم، تولید ۱۴۰۵/۰۸/۲۰ ساعت ۱۴:۳۰" / "در سامانه، ثبت ۱۴۰۳/۰۶/۱۵"
 *  (both joined with " + " when the report exists in both places). "" when neither. */
private fun generationHistoryText(localRec: GeneratedReportRecord?, serverRep: ReportEntity?): String {
    val parts = mutableListOf<String>()
    if (localRec != null) {
        val date = displayShamsiDate(localRec.createdAt)
        val time = localRec.createdTime.trim()
        var s = "روی سیستم، تولید"
        if (date.isNotBlank()) s += " $date"
        if (time.isNotBlank()) s += " ساعت ${toPersianDigitsStr(time)}"
        parts.add(s)
    }
    if (serverRep != null) {
        val date = displayShamsiDate(serverRep.visitDate)
        var s = "در سامانه، ثبت"
        if (date.isNotBlank()) s += " $date"
        parts.add(s)
    }
    return parts.joinToString(" + ")
}

// ==================== HISTORY TAB (merged into the generator) ====================

/** One row in the merged history: a report generated on this device, or a
 *  report already registered in the system (synced from the server). */
private sealed interface HistoryItem {
    val date: String
    val stageNum: Int
    // A local report is a GROUP of the files generated for one case+stage:
    // a Word file and/or a PDF file. They share one row, with the file type
    // (Word / PDF) shown next to «روی سیستم» and open/share/delete/print
    // actions for each file separately. [indices] are the records' positions
    // in the full localHistory list, so deleting one file removes only it.
    data class Local(val records: List<GeneratedReportRecord>, val indices: List<Int>) : HistoryItem {
        // Representative record (the newest file) for date/owner display.
        val record: GeneratedReportRecord get() = records.maxByOrNull { normalizeDigits(it.createdAt + it.createdTime) } ?: records.first()
        override val date: String get() = record.createdAt
        override val stageNum: Int get() = record.stage
    }
    data class Server(val report: ReportEntity, val owner: String) : HistoryItem {
        override val date: String get() = report.visitDate
        override val stageNum: Int get() = stageNumberFromTitle(report.stage)
    }
}

private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "doc" -> "application/msword"
    else -> "*/*"
}

/** The history tab of the report generator: every report the current user
 *  generated on this device, plus their own reports registered in the system.
 *  Server reports are filtered to the logged-in user — the server returns
 *  every engineer's reports for a case, and the other engineers' reports must
 *  not appear here. Filters are selection-only (a filter button opens the
 *  filter page); nothing is typed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryTab(
    viewModel: MainViewModel,
    localHistory: List<GeneratedReportRecord>,
    onHistoryChanged: (List<GeneratedReportRecord>) -> Unit,
    userName: String,
    onNavigateToUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serverReports by viewModel.reports.collectAsState()
    val cases by viewModel.cases.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val ownerByCase = remember(cases) { cases.associate { it.caseNumber to it.owner } }

    // Selection filters (no typing): source + stage + engineer + discipline +
    // owner + date. All are chosen from the filter page (dialog) that opens
    // with the "فیلتر" button.
    var filterStage by remember { mutableIntStateOf(0) }
    var filterSource by remember { mutableIntStateOf(0) } // 0 همه، 1 در سامانه، 2 روی سیستم
    var filterEngineer by remember { mutableStateOf<String?>(null) }
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var filterDate by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableIntStateOf(-1) }
    var downloadingId by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf("") }
    // Printing: server reports ask first (they are registered in the system),
    // local reports print straight away.
    var printWarningItem by remember { mutableStateOf<HistoryItem.Server?>(null) }
    var printingId by remember { mutableStateOf<Long?>(null) }
    var printError by remember { mutableStateOf("") }

    // Only the current user's server reports — other engineers' reports on the
    // same cases are excluded. Local reports were generated on this phone, so
    // they are the user's by definition.
    val items: List<HistoryItem> = remember(localHistory, serverReports, ownerByCase, userName) {
        // Local files are grouped by case+stage: a Word file and its PDF
        // share one row (see HistoryItem.Local).
        val local = localHistory.mapIndexed { i, r -> i to r }
            .groupBy { (_, r) -> r.caseNumber to r.stage }
            .values.map { pairs ->
                val sorted = pairs.sortedBy { (i, _) -> i }
                HistoryItem.Local(records = sorted.map { it.second }, indices = sorted.map { it.first })
            }
        val server = serverReports
            .filter { isOwnServerReport(it, userName) }
            .map { HistoryItem.Server(it, ownerByCase[it.caseNumber] ?: "") }
        (local + server).sortedByDescending { normalizeDigits(it.date).filter { c -> c.isDigit() } }
    }
    // Discipline of a case's local reports, from its server reports (local
    // records carry no discipline of their own).
    val disciplineByCase = remember(serverReports) {
        serverReports.groupBy { it.caseNumber }
            .mapValues { (_, reps) -> reps.firstOrNull()?.discipline ?: "" }
    }
    fun engineerOf(item: HistoryItem): String = when (item) {
        is HistoryItem.Local -> userName
        is HistoryItem.Server -> item.report.engineer
    }
    fun disciplineOf(item: HistoryItem): String = when (item) {
        is HistoryItem.Local -> disciplineByCase[item.record.caseNumber] ?: ""
        is HistoryItem.Server -> item.report.discipline
    }
    fun ownerOf(item: HistoryItem): String = when (item) {
        is HistoryItem.Local -> item.record.owner
        is HistoryItem.Server -> item.owner
    }
    fun dateOf(item: HistoryItem): String = when (item) {
        is HistoryItem.Local -> item.record.createdAt
        is HistoryItem.Server -> item.report.visitDate
    }
    // Distinct values for the selection filters, from the user's own items.
    val engineerOptions = remember(items) { items.map { engineerOf(it) }.filter { it.isNotBlank() }.distinct().sorted() }
    val disciplineOptions = remember(items) { items.map { disciplineOf(it) }.filter { it.isNotBlank() }.distinct().sorted() }
    val ownerOptions = remember(items) { items.map { ownerOf(it) }.filter { it.isNotBlank() }.distinct().sorted() }
    val dateOptions = remember(items) { items.map { dateOf(it) }.filter { it.isNotBlank() }.distinct().sortedDescending() }
    val filteredItems = items.filter { item ->
        (filterStage == 0 || item.stageNum == filterStage) &&
            when (filterSource) {
                1 -> item is HistoryItem.Server
                2 -> item is HistoryItem.Local
                else -> true
            } &&
            (filterEngineer == null || personNameKey(engineerOf(item)) == personNameKey(filterEngineer ?: "")) &&
            (filterDiscipline == null || toPersianChars(disciplineOf(item)) == toPersianChars(filterDiscipline ?: "")) &&
            (filterOwner == null || ownerOf(item) == filterOwner) &&
            (filterDate == null || dateOf(item) == filterDate)
    }
    val activeFilterCount = listOf(
        filterSource != 0, filterStage != 0,
        filterEngineer != null, filterDiscipline != null,
        filterOwner != null, filterDate != null
    ).count { it }
    fun clearAllFilters() {
        filterSource = 0
        filterStage = 0
        filterEngineer = null
        filterDiscipline = null
        filterOwner = null
        filterDate = null
    }

    val serverCount = items.count { it is HistoryItem.Server }
    val localCount = items.count { it is HistoryItem.Local }

    // Download a server report's file into the cache; null on failure.
    // Shared by "دانلود و باز کردن" and by printing.
    suspend fun downloadServerFile(report: ReportEntity): File? = withContext(Dispatchers.IO) {
        try {
            val bytes = NezamApi().downloadFile(report.imageName).getOrNull() ?: return@withContext null
            val safeName = report.imageName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val dir = File(context.cacheDir, "server_reports").apply { mkdirs() }
            val file = File(dir, safeName)
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun downloadAndOpen(report: ReportEntity) {
        if (downloadingId != null || report.imageName.isBlank()) return
        downloadingId = report.id
        downloadError = ""
        scope.launch {
            val file = downloadServerFile(report)
            downloadingId = null
            if (file != null) {
                try {
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

    // Print a local (on-device) report: rebuild the report as a PDF from the
    // case's own data (same sections as the PDF export) and send it to the
    // printer. The .docx itself cannot be printed directly.
    fun printLocalReport(record: GeneratedReportRecord) {
        if (printingId != null) return
        printingId = -1L
        printError = ""
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    val case = cases.firstOrNull { it.caseNumber == record.caseNumber }
                    val spec = specs.firstOrNull { it.caseNumber == record.caseNumber }
                    val caseEngineers = engineers.filter { it.caseNumber == record.caseNumber }
                    val (group, discipline) = if (case != null) {
                        resolveCaseGroupDiscipline(case, spec, engineers, fees, serverReports)
                    } else "" to ""
                    val scheduleConfig = ScheduleConfig(context)
                    val today = todayJalali()
                    val stageCount = scheduleConfig.stageCount(group, discipline)
                    val permitRaw = spec?.permitDate?.ifBlank { case?.permitDate } ?: case?.permitDate ?: ""
                    val registeredStages = serverReports.filter { it.caseNumber == record.caseNumber && isOwnServerReport(it, userName) }
                        .mapNotNull { stageNumberFromTitle(it.stage).takeIf { n -> n > 0 } }.toSet()
                    val schedule = scheduleForCase(permitRaw, group, stageCount, registeredStages, today, monthsOverride = scheduleConfig.months(group))
                    val historyByStage = if (case != null) historyByStageFor(case, localHistory, serverReports, userName) else emptyMap()
                    val sections = if (case != null) {
                        buildReportPdfSections(case, spec, caseEngineers, group, discipline, schedule, historyByStage, today, userName)
                    } else {
                        listOf(PdfSection("مشخصات پرونده", listOf(
                            "شماره پرونده: ${record.caseNumber}",
                            "مالک: ${toPersianChars(record.owner)}"
                        )))
                    }
                    val dir = File(context.cacheDir, "print").apply { mkdirs() }
                    val stageName = stageNameForNumber(record.stage)
                    val out = File(dir, "گزارش مرحله $stageName ${record.owner}.pdf")
                    generateReportPdf(
                        title = "گزارش مرحله $stageName",
                        subtitle = "مانیتورینگ نظام مهندسی",
                        sections = sections,
                        footer = "تاریخ گزارش: ${displayShamsiDate(record.createdAt)}",
                        outputFile = out
                    )
                } catch (_: Exception) {
                    null
                }
            }
            printingId = null
            if (file != null) {
                try {
                    printPdfFile(context, file, "گزارش ${toPersianChars(record.owner)}")
                } catch (_: Exception) {
                    printError = "خطا در ارسال به پرینتر"
                }
            } else {
                printError = "خطا در آماده‌سازی فایل چاپ"
            }
        }
    }

    // Print an already-generated PDF file directly (no rebuild needed).
    // Used for a PDF record in the history; a Word record still goes
    // through printLocalReport (rebuilt as a PDF from the case's data).
    fun printLocalPdfFile(record: GeneratedReportRecord) {
        if (printingId != null) return
        printingId = -1L
        printError = ""
        scope.launch {
            val file = File(record.filePath)
            if (!file.exists()) {
                printingId = null
                printError = "فایل PDF حذف شده است"
                return@launch
            }
            try {
                printPdfFile(context, file, "گزارش ${toPersianChars(record.owner)}")
            } catch (_: Exception) {
                printError = "خطا در ارسال به پرینتر"
            }
            printingId = null
        }
    }

    // Print a server report: download it, then print the PDF as-is or the
    // image as one A4 page. A Word file cannot be printed directly.
    fun printServerReport(report: ReportEntity) {
        if (printingId != null || report.imageName.isBlank()) return
        printingId = report.id
        printError = ""
        scope.launch {
            val file = downloadServerFile(report)
            printingId = null
            if (file == null) {
                printError = "خطا در دانلود فایل از سامانه"
                return@launch
            }
            try {
                when (file.name.substringAfterLast('.', "").lowercase()) {
                    "pdf" -> printPdfFile(context, file, "گزارش ${toPersianChars(report.engineer)}")
                    "jpg", "jpeg", "png" -> printImageFile(context, file, "گزارش ${toPersianChars(report.engineer)}")
                    else -> printError = "چاپ مستقیم این نوع فایل (${file.name.substringAfterLast('.', "")}) ممکن نیست؛ ابتدا آن را دانلود کنید"
                }
            } catch (_: Exception) {
                printError = "خطا در ارسال به پرینتر"
            }
        }
    }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Whose reports these are — the history shows only the logged-in
        // user's reports. When the name is unknown, nothing is filtered out,
        // so say so plainly instead of silently showing everybody's reports.
        item {
            if (userName.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠️ نام شما ثبت نشده است", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "سابقه در حال حاضر گزارش‌های همهٔ مهندسین را نشان می‌دهد. برای اینکه فقط گزارش‌های خودتان نمایش داده شود، یک‌بار از منوی بروزرسانی وارد شوید.",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNavigateToUpdate, modifier = Modifier.fillMaxWidth()) {
                            Text("رفتن به بروزرسانی و ورود", fontSize = 13.sp)
                        }
                    }
                }
            } else {
                Text(
                    "گزارش‌های: ${toPersianChars(userName)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Filter button (opens the filter page) + clear-all
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showFilterDialog = true }, modifier = Modifier.weight(1f)) {
                    Text(if (activeFilterCount > 0) "🔍 فیلتر ($activeFilterCount)" else "🔍 فیلتر", fontSize = 13.sp)
                }
                if (activeFilterCount > 0) {
                    OutlinedButton(onClick = { clearAllFilters() }) {
                        Text("پاک کردن", fontSize = 13.sp)
                    }
                }
            }
        }

        // Active filters, each removable on its own
        if (activeFilterCount > 0) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (filterSource != 0) FilterChip(
                        selected = true,
                        onClick = { filterSource = 0 },
                        label = { Text(if (filterSource == 1) "در سامانه ✕" else "روی سیستم ✕", fontSize = 11.sp) }
                    )
                    if (filterStage != 0) FilterChip(
                        selected = true,
                        onClick = { filterStage = 0 },
                        label = { Text("مرحله ${stageNameForNumber(filterStage)} ✕", fontSize = 11.sp) }
                    )
                    if (filterEngineer != null) FilterChip(
                        selected = true,
                        onClick = { filterEngineer = null },
                        label = { Text("مهندس: ${toPersianChars(filterEngineer ?: "")} ✕", fontSize = 11.sp) }
                    )
                    if (filterDiscipline != null) FilterChip(
                        selected = true,
                        onClick = { filterDiscipline = null },
                        label = { Text("رشته: ${toPersianChars(filterDiscipline ?: "")} ✕", fontSize = 11.sp) }
                    )
                    if (filterOwner != null) FilterChip(
                        selected = true,
                        onClick = { filterOwner = null },
                        label = { Text("مالک: ${toPersianChars(filterOwner ?: "")} ✕", fontSize = 11.sp) }
                    )
                    if (filterDate != null) FilterChip(
                        selected = true,
                        onClick = { filterDate = null },
                        label = { Text("تاریخ: ${toPersianDigitsStr(filterDate ?: "")} ✕", fontSize = 11.sp) }
                    )
                }
            }
        }

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
                    onHistoryChanged(loadReportHistory(context).map { record ->
                        record.copy(fileExists = File(record.filePath).exists())
                    })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔄 بروزرسانی وضعیت")
            }
        }

        if (downloadError.isNotBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(downloadError, modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (printError.isNotBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(printError, modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
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

        items(filteredItems) { item ->
            when (item) {
                is HistoryItem.Local -> {
                    val record = item.record
                    val stageName = stageNameForNumber(record.stage)
                    val hasWord = item.records.any { it.fileType != "pdf" && !it.filePath.lowercase().endsWith(".pdf") }
                    val hasPdf = item.records.any { it.fileType == "pdf" || it.filePath.lowercase().endsWith(".pdf") }
                    val anyExists = item.records.any { File(it.filePath).exists() }
                    NezamCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (anyExists) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        copyText = "${record.caseNumber}\n${toPersianChars(record.owner)}\nگزارش مرحله $stageName — ${displayShamsiDate(record.createdAt + " " + record.createdTime)}",
                        extraItems = listOf(
                            "🖨️ چاپ" to {
                                if (hasPdf) {
                                    val pdfRec = item.records.firstOrNull { it.fileType == "pdf" || it.filePath.lowercase().endsWith(".pdf") }
                                    if (pdfRec != null) printLocalPdfFile(pdfRec) else printLocalReport(record)
                                } else printLocalReport(record)
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (anyExists) "✅" else "⚠️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(toPersianChars(record.owner), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("گزارش مرحله $stageName", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(record.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("📅 ${displayShamsiDate(record.createdAt + " " + record.createdTime)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                        Text("روی سیستم", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (hasWord) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                            Text("Word", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                        if (hasPdf) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                                            Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                            }

                            // One row per file (Word and/or PDF) with its own
                            // open / share / delete / print actions.
                            item.records.forEachIndexed { pos, rec ->
                                val recIndex = item.indices[pos]
                                val isPdf = rec.fileType == "pdf" || rec.filePath.lowercase().endsWith(".pdf")
                                val exists = File(rec.filePath).exists()
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (isPdf) "📄" else "📝", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(if (isPdf) "فایل PDF" else "فایل Word", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(File(rec.filePath).name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!exists) {
                                            Text("⚠️ فایل حذف شده", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (exists) {
                                        TextButton(onClick = {
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(rec.filePath))
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, mimeFor(rec.filePath))
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }) { Text("📁 باز کردن", fontSize = 12.sp) }

                                        TextButton(onClick = {
                                            try {
                                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(rec.filePath))
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = mimeFor(rec.filePath)
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
                                            } catch (_: Exception) {}
                                        }) { Text("📤 اشتراک", fontSize = 12.sp) }
                                    }

                                    TextButton(onClick = { showDeleteConfirm = recIndex }) {
                                        Text("🗑 حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }

                                    if (exists) {
                                        if (printingId == -1L) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Text("در حال آماده‌سازی...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            TextButton(onClick = {
                                                if (isPdf) printLocalPdfFile(rec) else printLocalReport(rec)
                                            }) {
                                                Text("🖨️ چاپ", fontSize = 12.sp)
                                            }
                                        }
                                    }
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
                    NezamCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface,
                        copyText = "${report.caseNumber}\n${toPersianChars(item.owner)}\n$title${if (stageLabel.isNotBlank()) " - $stageLabel" else ""} — ${displayShamsiDate(report.visitDate)}",
                        extraItems = listOf(
                            "🖨️ چاپ" to { printWarningItem = item },
                            "⬇️ دانلود و باز کردن" to { downloadAndOpen(report) }
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌐", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.owner.isNotBlank()) {
                                        Text(toPersianChars(item.owner), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("$title${if (stageLabel.isNotBlank()) " - $stageLabel" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text("$title${if (stageLabel.isNotBlank()) " - $stageLabel" else ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(report.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    // A server report is registered in the system: warn
                                    // before printing (the warning dialog asks first).
                                    if (printingId == report.id) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("در حال دانلود...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        TextButton(onClick = { printWarningItem = item }) {
                                            Text("🖨️ چاپ", fontSize = 12.sp)
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

    // Delete confirmation dialog (local items only). showDeleteConfirm now
    // holds the record's index in the full localHistory list (per-file
    // delete: removing the Word file leaves the PDF, and vice versa).
    if (showDeleteConfirm >= 0) {
        val rec = localHistory.getOrNull(showDeleteConfirm)
        val isPdf = rec?.fileType == "pdf" || (rec?.filePath ?: "").lowercase().endsWith(".pdf")
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = -1 },
            title = { Text("حذف گزارش") },
            text = { Text("آیا از حذف ${if (isPdf) "فایل PDF" else "فایل Word"} گزارش ${toPersianChars(rec?.owner ?: "")} مطمئنید؟\nفایل نیز از حافظه حذف خواهد شد.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteReportHistoryItem(context, showDeleteConfirm)
                    onHistoryChanged(loadReportHistory(context))
                    showDeleteConfirm = -1
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = -1 }) { Text("لغو") }
            }
        )
    }

    // Print warning (server items only): this report is registered in the
    // system, so ask before sending it to the printer.
    if (printWarningItem != null) {
        val item = printWarningItem
        AlertDialog(
            onDismissRequest = { printWarningItem = null },
            title = { Text("چاپ گزارش سامانه") },
            text = { Text("این گزارش در سامانه ثبت شده است. از چاپ آن مطمئن هستید؟") },
            confirmButton = {
                TextButton(onClick = {
                    printWarningItem = null
                    if (item != null) printServerReport(item.report)
                }) { Text("بله", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { printWarningItem = null }) { Text("خیر") }
            }
        )
    }

    // Filter page (dialog): every filter as a selection
    if (showFilterDialog) {
        HistoryFilterDialog(
            source = filterSource,
            stage = filterStage,
            engineer = filterEngineer,
            discipline = filterDiscipline,
            owner = filterOwner,
            date = filterDate,
            engineerOptions = engineerOptions,
            disciplineOptions = disciplineOptions,
            ownerOptions = ownerOptions,
            dateOptions = dateOptions,
            onApply = { s, st, e, d, o, dt ->
                filterSource = s
                filterStage = st
                filterEngineer = e
                filterDiscipline = d
                filterOwner = o
                filterDate = dt
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

/** The history filter page: every filter as a selection — source and stage
 *  as chips, engineer / discipline / owner / date as dropdowns built from
 *  the values actually in the history. Draft state lives inside the dialog;
 *  nothing changes until "اعمال فیلتر". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HistoryFilterDialog(
    source: Int,
    stage: Int,
    engineer: String?,
    discipline: String?,
    owner: String?,
    date: String?,
    engineerOptions: List<String>,
    disciplineOptions: List<String>,
    ownerOptions: List<String>,
    dateOptions: List<String>,
    onApply: (source: Int, stage: Int, engineer: String?, discipline: String?, owner: String?, date: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var draftSource by remember { mutableIntStateOf(source) }
    var draftStage by remember { mutableIntStateOf(stage) }
    var draftEngineer by remember { mutableStateOf(engineer) }
    var draftDiscipline by remember { mutableStateOf(discipline) }
    var draftOwner by remember { mutableStateOf(owner) }
    var draftDate by remember { mutableStateOf(date) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیلتر سابقه گزارش‌ها", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("منبع", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "همه", 1 to "در سامانه", 2 to "روی سیستم").forEach { (num, name) ->
                        FilterChip(
                            selected = draftSource == num,
                            onClick = { draftSource = num },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
                Text("مرحله", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "همه مراحل", 1 to "مرحله اول", 2 to "مرحله دوم", 3 to "مرحله سوم", 4 to "مرحله چهارم").forEach { (num, name) ->
                        FilterChip(
                            selected = draftStage == num,
                            onClick = { draftStage = num },
                            label = { Text(name, fontSize = 12.sp) }
                        )
                    }
                }
                FilterDropdown(label = "مهندس", value = draftEngineer, options = engineerOptions, onSelect = { draftEngineer = it })
                FilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                FilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                FilterDropdown(label = "تاریخ", value = draftDate, options = dateOptions, onSelect = { draftDate = it }, displayValue = { toPersianDigitsStr(it) })
                OutlinedButton(
                    onClick = {
                        draftSource = 0
                        draftStage = 0
                        draftEngineer = null
                        draftDiscipline = null
                        draftOwner = null
                        draftDate = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پاک کردن همهٔ فیلترها", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(draftSource, draftStage, draftEngineer, draftDiscipline, draftOwner, draftDate)
            }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** One dropdown row of the filter page: the value is chosen from the list,
 *  never typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
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

// ==================== DIGIT UTILS ====================

private fun toPersianDigits(input: String): String = input.replace("0","۰").replace("1","۱").replace("2","۲").replace("3","۳").replace("4","۴").replace("5","۵").replace("6","۶").replace("7","۷").replace("8","۸").replace("9","۹")
private fun toEnglishDigits(input: String): String = input.replace("۰","0").replace("۱","1").replace("۲","2").replace("۳","3").replace("۴","4").replace("۵","5").replace("۶","6").replace("۷","7").replace("۸","8").replace("۹","9")
private fun toAsciiDigits(input: String): String = toEnglishDigits(input)
private fun reverseSlash(input: String): String { if (!input.contains("/")) return input; return input.split("/").reversed().joinToString("/") }
private fun reverseCaseNumber(cn: String): String { if (!cn.contains("/")) return cn; return cn.split("/").reversed().joinToString("-") }
private fun toPersianDate(date: Date): String {
    val cal = java.util.Calendar.getInstance()
    cal.time = date
    val gy = cal.get(java.util.Calendar.YEAR)
    val gm = cal.get(java.util.Calendar.MONTH) + 1
    val gd = cal.get(java.util.Calendar.DAY_OF_MONTH)
    val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    var jm: Int
    var jd: Int
    if (days < 186) { jm = 1 + (days / 31); jd = 1 + (days % 31) }
    else { jm = 7 + ((days - 186) / 30); jd = 1 + ((days - 186) % 30) }
    return String.format("%d/%02d/%02d", jy, jm, jd)
}

// ==================== HISTORY ====================

data class GeneratedReportRecord(val caseNumber: String = "", val owner: String = "", val stage: Int = 0, val createdAt: String = "", val createdTime: String = "", val filePath: String = "", val folderPath: String = "", val fileExists: Boolean = true, val fileType: String = "")

/** PDF size-limit options: max bytes per file (0 = no limit, full quality). */
internal val pdfSizeOptions: List<Pair<Long, String>> = listOf(
    0L to "بدون محدودیت",
    2L * 1024 * 1024 to "۲ مگابایت",
    1L * 1024 * 1024 to "۱ مگابایت",
    512L * 1024 to "۵۰۰ کیلوبایت"
)

/** Max PDF file size in bytes (0 = no limit, full quality). Stored in nezam_settings. */
internal fun loadPdfMaxBytes(context: Context): Long =
    try { context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE).getLong("pdf_max_bytes", 0L) } catch (_: Exception) { 0L }

internal fun savePdfMaxBytes(context: Context, bytes: Long) {
    try { context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE).edit().putLong("pdf_max_bytes", bytes).apply() } catch (_: Exception) {}
}

internal fun saveReportHistory(context: Context, records: List<GeneratedReportRecord>) {
    try { val prefs = context.getSharedPreferences("nezam_report_history", Context.MODE_PRIVATE); val existing = loadReportHistory(context).toMutableList(); existing.addAll(records); prefs.edit().putString("history", com.google.gson.Gson().toJson(existing)).apply() } catch (_: Exception) {}
}
@Suppress("UNNECESSARY_ELVIS")
internal fun loadReportHistory(context: Context): List<GeneratedReportRecord> {
    return try {
        val prefs = context.getSharedPreferences("nezam_report_history", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]") ?: "[]"
        val arr = com.google.gson.Gson().fromJson(json, Array<GeneratedReportRecord>::class.java)
        if (arr != null) arr.toList().map { r ->
            // Records saved before createdTime existed deserialize with null
            // fields: Gson allocates the data class without calling its
            // constructor, so the = "" defaults do NOT apply. An old record
            // with a null createdTime crashed the generator on .trim() after
            // updating from 3.7.0 — sanitize every field here so no caller
            // can hit a NullPointerException.
            r.copy(
                caseNumber = r.caseNumber ?: "",
                owner = r.owner ?: "",
                createdAt = r.createdAt ?: "",
                createdTime = r.createdTime ?: "",
                filePath = r.filePath ?: "",
                folderPath = r.folderPath ?: "",
                // fileType was added in 3.7.12: old records have null here
                // (Gson bypasses the constructor default) — derive it from
                // the file extension so old Word records still show "Word".
                fileType = r.fileType?.ifBlank { null } ?: if ((r.filePath ?: "").lowercase().endsWith(".pdf")) "pdf" else "word"
            )
        } else emptyList()
    } catch (_: Exception) { emptyList() }
}
internal fun deleteReportHistoryItem(context: Context, index: Int) {
    try {
        val h = loadReportHistory(context).toMutableList()
        if (index in h.indices) {
            val item = h.removeAt(index)
            try { File(item.filePath).delete() } catch (_: Exception) {}
            try { File(item.folderPath).delete() } catch (_: Exception) {}
            context.getSharedPreferences("nezam_report_history", Context.MODE_PRIVATE)
                .edit().putString("history", com.google.gson.Gson().toJson(h)).apply()
        }
    } catch (_: Exception) {}
}
