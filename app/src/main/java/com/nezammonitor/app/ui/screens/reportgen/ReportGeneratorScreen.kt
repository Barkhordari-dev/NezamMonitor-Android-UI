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
fun ReportGeneratorScreen(viewModel: MainViewModel, initialTab: Int = 0, onBack: () -> Unit, onNavigateToUpdate: () -> Unit = {}, onNavigateToSchedule: () -> Unit = {}, onNavigateToHelp: () -> Unit = {}, onNavigateToPaths: () -> Unit = {}) {
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
    // PDF options dialog (hierarchical: explanation, then size limit).
    var showPdfOptionsDialog by remember { mutableStateOf(false) }

    // Paths (changeable in the "مسیرها" section below) and the signed-report
    // switch: when on, the signed-templates folder replaces the normal one
    // for both Word and PDF generation.
    var reportPaths by remember { mutableStateOf(loadReportPaths(context)) }
    var signedReport by remember { mutableStateOf(loadSignedReport(context)) }
    val templatesDir = File(if (signedReport) reportPaths.signedTemplatesPath else reportPaths.templatesPath)
    val outputsDir = File(reportPaths.outputsPath)
    val templatesExist = templatesDir.exists() && (templatesDir.listFiles()?.any { it.name.endsWith(".docx") } == true)
    // Advanced filter for the case list (same pattern as the history
    // filter): selection-based, from the values actually in the list.
    var filterGroup by remember { mutableStateOf<String?>(null) }
    var filterDiscipline by remember { mutableStateOf<String?>(null) }
    var filterOwner by remember { mutableStateOf<String?>(null) }
    var filterOffice by remember { mutableStateOf<String?>(null) }
    var filterState by remember { mutableStateOf<String?>(null) }
    var filterWorkType by remember { mutableStateOf<String?>(null) }
    // "فقط گزارش‌های تولید نشده": keep only cases whose report for the
    // selected stage has not been generated yet (no local record and no
    // own server report); with no stage selected, only cases with no
    // generated stage at all.
    var filterOnlyNotGenerated by remember { mutableStateOf(false) }
    // Sort by remaining time: 0 = default order, 1 = remaining ascending
    // (least remaining / most overdue first), 2 = remaining descending
    // (most remaining first).
    var generatorSort by remember { mutableStateOf(0) }
    var showGeneratorFilterDialog by remember { mutableStateOf(false) }
    // When stage 4 is selected, show only the rows that actually have a
    // 4th stage, so the user chooses among those.
    val visibleCases = remember(selectedStage, cases, specs, engineers, fees, reports, scheduleConfig) {
        if (selectedStage == 4) cases.filter { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
            scheduleConfig.stageCount(g, d) >= 4
        } else cases
    }
    // Filter options, from the values actually in the visible list.
    val groupOptions = remember(visibleCases, specs, engineers, fees, reports) {
        visibleCases.map { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            buildingGroupLetter(resolveCaseGroupDiscipline(c, spec, engineers, fees, reports).first)
        }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val disciplineOptions = remember(visibleCases, specs, engineers, fees, reports) {
        visibleCases.map { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            toPersianChars(resolveCaseGroupDiscipline(c, spec, engineers, fees, reports).second)
        }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val ownerOptions = remember(visibleCases) { visibleCases.map { it.owner }.filter { it.isNotBlank() }.distinct().sorted() }
    val officeOptions = remember(visibleCases) { visibleCases.map { it.office }.filter { it.isNotBlank() }.distinct().sorted() }
    val stateOptions = remember(visibleCases) { visibleCases.map { it.stateTitle }.filter { it.isNotBlank() }.distinct().sorted() }
    val workTypeOptions = remember(visibleCases) { visibleCases.map { it.workType }.filter { it.isNotBlank() }.distinct().sorted() }
    val filteredCases = remember(visibleCases, filterGroup, filterDiscipline, filterOwner, filterOffice, filterState, filterWorkType, filterOnlyNotGenerated, selectedStage, localHistory, reports, userName) {
        visibleCases.filter { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
            (filterGroup == null || buildingGroupLetter(g) == filterGroup) &&
                (filterDiscipline == null || toPersianChars(d) == toPersianChars(filterDiscipline ?: "")) &&
                (filterOwner == null || c.owner == filterOwner) &&
                (filterOffice == null || c.office == filterOffice) &&
                (filterState == null || c.stateTitle == filterState) &&
                (filterWorkType == null || c.workType == filterWorkType) &&
                (!filterOnlyNotGenerated || isCaseNotGenerated(c, selectedStage, localHistory, reports, userName))
        }
    }
    // Sort by remaining time. The sort key is the due date of the selected
    // stage (or the earliest due date when no stage is selected); comparing
    // due dates IS comparing remaining time, since "today" is the same for
    // all rows. Cases without a due date (no permit date) sort last in both
    // directions; the sort is stable, so their relative order is kept.
    // (JalaliDate is not Comparable — compareTo is a plain extension — so
    // the comparison is written out explicitly here.)
    val sortedCases = remember(filteredCases, generatorSort, selectedStage, specs, engineers, fees, reports, scheduleConfig, userName, today) {
        if (generatorSort == 0) return@remember filteredCases
        fun sortKey(c: CaseEntity): JalaliDate? {
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
            val stageCount = scheduleConfig.stageCount(g, d)
            val permitRaw = spec?.permitDate?.ifBlank { c.permitDate } ?: c.permitDate
            val registeredStages = reports.filter { it.caseNumber == c.caseNumber && isOwnServerReport(it, userName) }
                .mapNotNull { stageNumberFromTitle(it.stage).takeIf { n -> n > 0 } }.toSet()
            val schedule = scheduleForCase(permitRaw, g, stageCount, registeredStages, today, monthsOverride = scheduleConfig.months(g))
            return if (selectedStage > 0) schedule.firstOrNull { it.stage == selectedStage }?.dueDate
            else schedule.minWithOrNull(Comparator { a, b -> a.dueDate.compareTo(b.dueDate) })?.dueDate
        }
        val descending = generatorSort == 2
        filteredCases.sortedWith(Comparator { x, y ->
            val a = sortKey(x)
            val b = sortKey(y)
            if (a == null && b == null) 0
            else if (a == null) 1
            else if (b == null) -1
            else if (descending) b.compareTo(a) else a.compareTo(b)
        })
    }
    val activeGeneratorFilterCount = listOf(
        filterGroup != null, filterDiscipline != null, filterOwner != null,
        filterOffice != null, filterState != null, filterWorkType != null,
        filterOnlyNotGenerated, generatorSort != 0
    ).count { it }
    fun clearGeneratorFilters() {
        filterGroup = null
        filterDiscipline = null
        filterOwner = null
        filterOffice = null
        filterState = null
        filterWorkType = null
        filterOnlyNotGenerated = false
        generatorSort = 0
    }

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
                        specs = specs,
                        stage = selectedStage,
                        useLtr = useLtr,
                        templatesDir = templatesDir,
                        outputsDir = outputsDir,
                        userName = userName,
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

    // Generator advanced filter dialog.
    if (showGeneratorFilterDialog) {
        GeneratorFilterDialog(
            group = filterGroup,
            discipline = filterDiscipline,
            owner = filterOwner,
            office = filterOffice,
            state = filterState,
            workType = filterWorkType,
            onlyNotGenerated = filterOnlyNotGenerated,
            sort = generatorSort,
            groupOptions = groupOptions,
            disciplineOptions = disciplineOptions,
            ownerOptions = ownerOptions,
            officeOptions = officeOptions,
            stateOptions = stateOptions,
            workTypeOptions = workTypeOptions,
            onApply = { g, d, o, of, s, w, notGen, sort ->
                filterGroup = g
                filterDiscipline = d
                filterOwner = o
                filterOffice = of
                filterState = s
                filterWorkType = w
                filterOnlyNotGenerated = notGen
                generatorSort = sort
                showGeneratorFilterDialog = false
            },
            onDismiss = { showGeneratorFilterDialog = false }
        )
    }

    // PDF options (hierarchical): explanation first, then the size limit,
    // then confirm. Nothing is generated until "تایید و تولید".
    if (showPdfOptionsDialog) {
        PdfOptionsDialog(
            currentBytes = pdfMaxBytes,
            onConfirm = { bytes ->
                pdfMaxBytes = bytes
                savePdfMaxBytes(context, bytes)
                showPdfOptionsDialog = false
                doGeneratePdf()
            },
            onDismiss = { showPdfOptionsDialog = false }
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
            // Signed report + LTR in one compact row: when signed is on,
            // Word and PDF generation use the signed-templates folder.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = signedReport, onCheckedChange = { signedReport = it; saveSignedReport(context, it) })
                        Column {
                            Text("تولید گزارش امضا شده", fontSize = 13.sp)
                            Text("از پوشهٔ تمپلیت‌های امضاشده استفاده می‌شود", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useLtr, onCheckedChange = { useLtr = it })
                        Text("تولید چپ‌چین (LTR)", fontSize = 13.sp)
                    }
                }
            }

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

            // PDF export: the SAME Word report the Word button builds
            // (same template, same placeholders), converted to PDF — so
            // the PDF looks like the template. Needs the templates, and
            // the Word file is kept next to it in the same folder.
            item {
                OutlinedButton(
                    onClick = { showPdfOptionsDialog = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isGenerating && selectedStage > 0 && selectedCases.isNotEmpty() && templatesExist
                ) {
                    if (isGenerating) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)) }
                    Text("📄 تولید گزارش PDF", fontSize = 15.sp)
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


            // Paths live on their own page now (in-app folder browser,
            // no typing): one small button opens it.
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onNavigateToPaths,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Text("📂 مسیرها", fontSize = 12.sp)
                    }
                }
            }

            // Advanced filter for the case list (selection-based, like the
            // history filter). It narrows the same list alongside the
            // stage-4 rule above.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { showGeneratorFilterDialog = true }, modifier = Modifier.weight(1f)) {
                        Text(if (activeGeneratorFilterCount > 0) "🔍 فیلتر پیشرفته (${toPersianDigits(activeGeneratorFilterCount.toString())})" else "🔍 فیلتر پیشرفته", fontSize = 13.sp)
                    }
                    if (activeGeneratorFilterCount > 0) {
                        OutlinedButton(onClick = { clearGeneratorFilters() }) {
                            Text("پاک کردن", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (activeGeneratorFilterCount > 0) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (filterGroup != null) FilterChip(selected = true, onClick = { filterGroup = null }, label = { Text("گروه $filterGroup ✕", fontSize = 11.sp) })
                        if (filterDiscipline != null) FilterChip(selected = true, onClick = { filterDiscipline = null }, label = { Text("رشته: ${toPersianChars(filterDiscipline ?: "")} ✕", fontSize = 11.sp) })
                        if (filterOwner != null) FilterChip(selected = true, onClick = { filterOwner = null }, label = { Text("مالک: ${toPersianChars(filterOwner ?: "")} ✕", fontSize = 11.sp) })
                        if (filterOffice != null) FilterChip(selected = true, onClick = { filterOffice = null }, label = { Text("دفتر: ${toPersianChars(filterOffice ?: "")} ✕", fontSize = 11.sp) })
                        if (filterState != null) FilterChip(selected = true, onClick = { filterState = null }, label = { Text("وضعیت: ${toPersianChars(filterState ?: "")} ✕", fontSize = 11.sp) })
                        if (filterWorkType != null) FilterChip(selected = true, onClick = { filterWorkType = null }, label = { Text("نوع کار: ${toPersianChars(filterWorkType ?: "")} ✕", fontSize = 11.sp) })
                        if (filterOnlyNotGenerated) FilterChip(selected = true, onClick = { filterOnlyNotGenerated = false }, label = { Text("فقط تولید نشده ✕", fontSize = 11.sp) })
                        if (generatorSort == 1) FilterChip(selected = true, onClick = { generatorSort = 0 }, label = { Text("زمان باقی‌مانده: صعودی ↑ ✕", fontSize = 11.sp) })
                        if (generatorSort == 2) FilterChip(selected = true, onClick = { generatorSort = 0 }, label = { Text("زمان باقی‌مانده: نزولی ↓ ✕", fontSize = 11.sp) })
                    }
                }
            }

            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selectAll, onCheckedChange = { selectAll = it; selectedCases = if (it) sortedCases.filter { c -> val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }; val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports); selectedStage == 0 || selectedStage <= scheduleConfig.stageCount(g, d) }.map { c -> c.caseNumber }.toSet() else emptySet() }); Text("انتخاب همه (${sortedCases.size})", fontSize = 14.sp) } }

            items(sortedCases) { case ->
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
    val persianDate = toPersianDate(now)
    val timeNow = SimpleDateFormat("HH:mm", Locale.US).format(now)
    val englishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    var count = 0

    for ((index, case) in cases.withIndex()) {
        try {
            val outputFile = generateSingleWordReport(
                context = context, case = case, index = index,
                engineers = engineers, specs = specs, stage = stage,
                useLtr = useLtr, templateFile = templateFile,
                outputsDir = outputsDir, userName = userName,
                persianDate = persianDate, timeNow = timeNow, englishDate = englishDate
            )
            files.add(outputFile)
            count++
        } catch (e: Exception) { return GenerateResult(files, "❌ خطا: ${case.owner}: ${e.message}") }
    }
    return GenerateResult(files, "✅ $count گزارش مرحله $stageName تولید شد\n📁 ${outputsDir.absolutePath}")
}

/** Builds one case's Word report from the template — the same file the
 *  Word button produces — and records it in the generation history.
 *  Shared by the Word button, the PDF export (which converts this file)
 *  and print-regeneration. Throws on failure. */
private fun generateSingleWordReport(
    context: Context,
    case: CaseEntity,
    index: Int,
    engineers: List<EngineerEntity>,
    specs: List<SpecificationEntity>,
    stage: Int,
    useLtr: Boolean,
    templateFile: File,
    outputsDir: File,
    userName: String,
    persianDate: String,
    timeNow: String,
    englishDate: String
): File {
    val stageName = stageNameForNumber(stage)
    val rowNum = String.format("%02d", index + 1)
    val reversedCase = reverseCaseNumber(case.caseNumber)
    val safeOwner = case.owner.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    val folderNum = toPersianDigits(rowNum)
    val folderName = "$folderNum - $safeOwner - $reversedCase"
    val folder = File(outputsDir, folderName)
    folder.mkdirs()
    val caseEngineers = engineers.filter { it.caseNumber == case.caseNumber }
    val spec = specs.firstOrNull { it.caseNumber == case.caseNumber }
    val reportDate = if (useLtr) englishDate else persianDate
    val ltrSuffix = if (useLtr) " - LTR" else ""
    val fileName = "گزارش مرحله $stageName $safeOwner - $reportDate$ltrSuffix.docx"
    val outputFile = File(folder, fileName)
    if (!buildWordReportToFile(templateFile, case, spec, caseEngineers, persianDate, useLtr, userName, outputFile)) {
        throw IllegalStateException("خطا در ساخت فایل Word")
    }
    saveReportHistory(context, listOf(GeneratedReportRecord(
        caseNumber = case.caseNumber, owner = case.owner, stage = stage,
        createdAt = persianDate, createdTime = timeNow, filePath = outputFile.absolutePath,
        folderPath = folder.absolutePath, fileExists = true
    )))
    return outputFile
}

/** Copies the template to [outputFile] and fills its placeholders.
 *  Returns false when the copy fails (placeholder replacement itself
 *  never throws — it swallows its own errors, as before). */
private fun buildWordReportToFile(
    templateFile: File,
    case: CaseEntity,
    spec: SpecificationEntity?,
    caseEngineers: List<EngineerEntity>,
    persianDate: String,
    useLtr: Boolean,
    userName: String,
    outputFile: File
): Boolean {
    return try {
        templateFile.copyTo(outputFile, overwrite = true)
        replacePlaceholders(outputFile, case, spec, caseEngineers, persianDate, useLtr, userName)
        true
    } catch (_: Exception) {
        false
    }
}

/** Generation history per stage for one case: local file (with generation
 *  date+time) and/or server report (with registration date). Server reports
 *  are filtered to the current user — a stage counts as registered only when
 *  THIS user submitted its report, not another engineer on the same case. */
/** True when the case has no generated report for [stage]: no local record
 *  and no own server report. With stage == 0 (no stage selected) true only
 *  when no stage has been generated at all. */
private fun isCaseNotGenerated(
    case: CaseEntity,
    stage: Int,
    localHistory: List<GeneratedReportRecord>,
    reports: List<ReportEntity>,
    userName: String
): Boolean {
    val byStage = historyByStageFor(case, localHistory, reports, userName)
    return if (stage > 0) byStage[stage] == null else byStage.isEmpty()
}

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
 *  (generated/registered/remaining). Used only by the print fallback when
 *  no template exists — the PDF export now converts the Word file. */
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
            "مرحله ${stageNameForNumber(entry.stage)}: سررسید ${entry.dueDate.toPersianDigits()} — $status"
        }))
    }
    return sections
}

/** Generate a PDF report for each selected case — by building the SAME Word
 *  report the Word button produces (same template, same placeholders) and
 *  converting that .docx to PDF, so the PDF looks exactly like the
 *  template. The PDF goes into the same per-case folder as its Word file
 *  and is recorded in the generation history (fileType "pdf") so it shows
 *  in the history tab next to the Word file. The Word file is kept too:
 *  pressing PDF produces both files.
 *
 *  [maxBytes] is the per-file size limit (0 = no limit, full quality).
 *  When a limit is set and a file comes out over it, the .docx is
 *  re-converted with a smaller font (fewer pages, smaller file) until it
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
    specs: List<SpecificationEntity>,
    stage: Int,
    useLtr: Boolean,
    templatesDir: File,
    outputsDir: File,
    userName: String,
    maxBytes: Long = 0L
): GenerateResult {
    val files = mutableListOf<File>()
    val stageName = stageNameForNumber(stage)
    // The disciplines of the selected cases (from each case's own data,
    // never hardcoded) pick the template, exactly like the Word button.
    val disciplines = cases.map { c ->
        c.discipline.ifBlank {
            engineers.firstOrNull { it.caseNumber == c.caseNumber }?.discipline ?: ""
        }
    }.map { toPersianChars(it.trim()) }.filter { it.isNotBlank() }.distinct()
    val templateFile = findTemplate(templatesDir, stageName, disciplines)
        ?: return GenerateResult(emptyList(), "❌ فایل تمپلیت مرحله $stageName یافت نشد\nفایل‌ها را در ${templatesDir.absolutePath} قرار دهید")
    outputsDir.mkdirs()
    val now = Date()
    val persianDate = toPersianDate(now)
    val timeNow = SimpleDateFormat("HH:mm", Locale.US).format(now)
    val englishDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
    var count = 0
    var totalBytes = 0L
    var reducedCount = 0
    var failedCount = 0
    for ((index, case) in cases.withIndex()) {
        try {
            // The Word file first — identical to the Word button's output —
            // then the PDF is converted from it.
            val docx = generateSingleWordReport(
                context = context, case = case, index = index,
                engineers = engineers, specs = specs, stage = stage,
                useLtr = useLtr, templateFile = templateFile,
                outputsDir = outputsDir, userName = userName,
                persianDate = persianDate, timeNow = timeNow, englishDate = englishDate
            )
            val pdfFile = File(docx.parentFile, docx.nameWithoutExtension + ".pdf")
            var pdfOk = false
            var usedScale = 1f
            for (scale in listOf(1f, 0.85f, 0.7f, 0.6f)) {
                if (convertDocxToPdf(context, docx, pdfFile, scale)) {
                    pdfOk = true
                    usedScale = scale
                    if (maxBytes <= 0L || pdfFile.length() <= maxBytes) break
                } else {
                    break
                }
            }
            if (pdfOk) {
                files.add(pdfFile)
                totalBytes += pdfFile.length()
                count++
                if (usedScale < 1f) reducedCount++
                saveReportHistory(context, listOf(GeneratedReportRecord(
                    caseNumber = case.caseNumber, owner = case.owner, stage = stage,
                    createdAt = persianDate, createdTime = timeNow, filePath = pdfFile.absolutePath,
                    folderPath = docx.parentFile?.absolutePath ?: outputsDir.absolutePath, fileExists = true, fileType = "pdf"
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
            append("\nℹ️ فایل Word هر گزارش هم در همان پوشه ساخته شد.")
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

/** Span-aware placeholder replacement. The old code joined every run
 *  into one string and dumped the result into runs[0], which destroyed
 *  the per-run formatting (text that had lived in other runs lost its
 *  own size/bold/font). This version replaces each placeholder ONLY
 *  inside the run(s) it spans: a placeholder inside one run is replaced
 *  in that run alone; one split across runs puts the replacement text in
 *  the FIRST run of the span and empties only the other runs of that
 *  span. Every other run stays byte-for-byte identical (same text, same
 *  properties). Runs that received replacement text get their font set
 *  to "B Nazanin" (ascii + hAnsi + cs, set directly — XWPFRun
 *  .setFontFamily only fills cs when it is not already set, which would
 *  leave a preset font in place for the Persian text; verified against
 *  the POI 5.2.5 bytecode).
 *
 *  Nothing else in the document changes: no sectPr/paragraph/table
 *  change, no size/bold/color change on any run. The replacement map
 *  and the digit handling (Persian/English digits, useLtr) are exactly
 *  as before, including the variant with a space before the closing
 *  guillemet («مالک »). */
private fun replaceInParagraph(para: org.apache.poi.xwpf.usermodel.XWPFParagraph, case: CaseEntity, spec: SpecificationEntity?, engineers: List<EngineerEntity>, persianDate: String, useLtr: Boolean, userName: String = "") {
    val runs = para.runs; if (runs.isEmpty()) return

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

    // The text of each run, from its <w:t> elements only (XWPFRun.text()
    // walks the run with a cursor and can surface breaks/tabs, which
    // would shift the offsets below; the <w:t> text is exactly what is
    // written back). Runs are never added or removed, so indices stay
    // aligned with the original document.
    val originalTexts = runs.map { runText(it) }
    val runTexts = originalTexts.toMutableList()
    // Which runs received replacement text (their font becomes B Nazanin).
    val touched = BooleanArray(runs.size)
    var changed = false

    for ((key, value) in replacements) {
        // Both variants the old code handled: the key itself, and the key
        // with a space before the closing guillemet.
        for (needle in listOf(key, key.replace("»", " »"))) {
            if (needle.isEmpty()) continue
            // All non-overlapping occurrences in the current text, then
            // apply them right-to-left so earlier offsets stay valid.
            val full = runTexts.joinToString("")
            val occurrences = mutableListOf<Int>()
            var idx = full.indexOf(needle)
            while (idx >= 0) {
                occurrences.add(idx)
                idx = full.indexOf(needle, idx + needle.length)
            }
            for (occ in occurrences.asReversed()) {
                val occEnd = occ + needle.length
                var pos = 0
                for (i in runTexts.indices) {
                    val runStart = pos
                    val runEnd = pos + runTexts[i].length
                    pos = runEnd
                    if (runEnd <= occ || runStart >= occEnd) continue
                    if (runStart <= occ && occ < runEnd) {
                        // The run the placeholder starts in: it keeps the
                        // replacement text (plus whatever of this run lies
                        // after the placeholder, when it ends inside it).
                        val relStart = occ - runStart
                        val relEnd = (occEnd - runStart).coerceAtMost(runTexts[i].length)
                        runTexts[i] = runTexts[i].substring(0, relStart) + value + runTexts[i].substring(relEnd)
                        touched[i] = true
                    } else if (runStart >= occ && runEnd <= occEnd) {
                        // A run fully inside the placeholder span: emptied.
                        runTexts[i] = ""
                    } else {
                        // A run the placeholder ends in: drop its covered prefix.
                        runTexts[i] = runTexts[i].substring(occEnd - runStart)
                    }
                }
                changed = true
            }
        }
    }
    if (!changed) return
    // Write back only the runs whose text changed; untouched runs are
    // never written, so their properties cannot change.
    for (i in runs.indices) {
        if (runTexts[i] != originalTexts[i]) {
            setRunText(runs[i], runTexts[i])
            if (touched[i]) setRunFontBNazanin(runs[i])
        }
    }
}

/** The text of one run, from its <w:t> elements only. */
private fun runText(run: org.apache.poi.xwpf.usermodel.XWPFRun): String {
    return try {
        run.getCTR().getTList().joinToString("") { it.stringValue ?: "" }
    } catch (_: Exception) {
        try { run.text() } catch (_: Exception) { "" }
    }
}

/** Replace a run's text, keeping its properties: the first <w:t> gets the
 *  new text and any extra <w:t> elements are removed (a run with several
 *  text elements would otherwise keep stale text after the first one).
 *  Breaks/tabs/drawings in the run are untouched (only <w:t> is edited). */
private fun setRunText(run: org.apache.poi.xwpf.usermodel.XWPFRun, text: String) {
    try {
        val ctr = run.getCTR()
        run.setText(text, 0)
        while (ctr.sizeOfTArray() > 1) ctr.removeT(1)
    } catch (_: Exception) {
    }
}

/** Set a run's font to B Nazanin (ascii + hAnsi + cs, unconditionally —
 *  XWPFRun.setFontFamily only fills cs when it is not already set, which
 *  would leave a preset font in place for the Persian text). */
private fun setRunFontBNazanin(run: org.apache.poi.xwpf.usermodel.XWPFRun) {
    try {
        val ctr = run.getCTR()
        val rPr = if (ctr.isSetRPr) ctr.rPr else ctr.addNewRPr()
        val fonts = if (rPr.sizeOfRFontsArray() > 0) rPr.getRFontsArray(0) else rPr.addNewRFonts()
        fonts.ascii = "B Nazanin"
        fonts.hAnsi = "B Nazanin"
        fonts.cs = "B Nazanin"
    } catch (_: Exception) {
    }
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

    // Rebuild a missing Word file from the template into the print cache
    // (no history entry: it is only an intermediate for the print).
    // Tries the normal templates folder, then the signed one.
    fun rebuildWordForPrint(record: GeneratedReportRecord): File? {
        return try {
            val case = cases.firstOrNull { it.caseNumber == record.caseNumber } ?: return null
            val spec = specs.firstOrNull { it.caseNumber == record.caseNumber }
            val caseEngineers = engineers.filter { it.caseNumber == record.caseNumber }
            val disciplines = listOf(
                case.discipline.ifBlank { caseEngineers.firstOrNull()?.discipline ?: "" }
            ).map { toPersianChars(it.trim()) }.filter { it.isNotBlank() }
            val stageName = stageNameForNumber(record.stage)
            val paths = loadReportPaths(context)
            val template = findTemplate(File(paths.templatesPath), stageName, disciplines)
                ?: findTemplate(File(paths.signedTemplatesPath), stageName, disciplines)
                ?: return null
            val dir = File(context.cacheDir, "print").apply { mkdirs() }
            val out = File(dir, "print-${record.caseNumber}.docx")
            val persianDate = toPersianDate(Date())
            if (buildWordReportToFile(template, case, spec, caseEngineers, persianDate, false, userName, out)) out else null
        } catch (_: Exception) {
            null
        }
    }

    // The old simple PDF (case data as a text list): only a fallback when
    // no template exists, so printing never breaks.
    fun buildSimplePrintPdf(record: GeneratedReportRecord): File? {
        return try {
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

    // Print a local (on-device) report: the report's own Word file is
    // converted to PDF exactly like the PDF export, so the print looks
    // like the template. When the Word file is gone it is rebuilt from
    // the template; when no template exists at all, the old simple
    // data-driven PDF is used so printing never breaks. A PDF record
    // prints its own file directly.
    fun printLocalReport(record: GeneratedReportRecord) {
        if (printingId != null) return
        printingId = -1L
        printError = ""
        scope.launch {
            val jobName = "گزارش ${toPersianChars(record.owner)}"
            var printed = false
            // 1. The record itself is a PDF file that still exists.
            val recFile = File(record.filePath)
            if ((record.fileType == "pdf" || record.filePath.lowercase().endsWith(".pdf")) && recFile.exists()) {
                try {
                    printPdfFile(context, recFile, jobName)
                    printed = true
                } catch (_: Exception) {
                }
            }
            // 2. The Word file still exists: convert it and print.
            if (!printed && record.filePath.lowercase().endsWith(".docx") && recFile.exists()) {
                printed = withContext(Dispatchers.IO) { printDocxFile(context, recFile, jobName) }
            }
            // 3. The Word file is gone: rebuild it from the template
            // (normal folder first, then the signed one), convert, print.
            if (!printed) {
                printed = withContext(Dispatchers.IO) {
                    val rebuilt = rebuildWordForPrint(record)
                    rebuilt != null && printDocxFile(context, rebuilt, jobName)
                }
            }
            // 4. Fallback: the simple data-driven PDF, so printing never breaks.
            if (!printed) {
                val file = withContext(Dispatchers.IO) { buildSimplePrintPdf(record) }
                if (file != null) {
                    try {
                        printPdfFile(context, file, jobName)
                        printed = true
                    } catch (_: Exception) {
                    }
                }
            }
            printingId = null
            if (!printed) printError = "خطا در آماده‌سازی فایل چاپ"
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

/** The PDF options, shown hierarchically after tapping "تولید گزارش PDF":
 *  ۱) what the PDF is (the filled Word file, converted), ۲) the maximum
 *  file size (the same 4 options as the old inline chips), then confirm.
 *  Nothing is generated until "تایید و تولید"; "انصراف" closes without
 *  generating. The chosen size is the dialog's draft until confirmed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PdfOptionsDialog(
    currentBytes: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var draftBytes by remember { mutableStateOf(currentBytes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تولید گزارش PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("۱. توضیح", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    "فایل PDF از روی فایل Word ساخته می‌شود و به تمپلیت مرحله نیاز دارد؛ فایل Word هم در همان پوشه ساخته می‌شود.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("۲. حداکثر حجم فایل PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    pdfSizeOptions.forEach { (bytes, label) ->
                        FilterChip(
                            selected = draftBytes == bytes,
                            onClick = { draftBytes = bytes },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
                if (draftBytes == 0L) {
                    Text("بدون محدودیت: خروجی با کیفیت کامل و بدون تغییر تولید می‌شود.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("اگر فایل از این حد بزرگ‌تر شود، اندازهٔ متن کوچک‌تر می‌شود تا حجم در حد بماند.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draftBytes) }) { Text("تایید و تولید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

/** The advanced filter for the generator's case list: same pattern as the
 *  cases filter — group as chips, the rest as dropdowns built from the
 *  values actually in the visible list. Draft state lives inside the
 *  dialog; nothing changes until "اعمال فیلتر". */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GeneratorFilterDialog(
    group: String?,
    discipline: String?,
    owner: String?,
    office: String?,
    state: String?,
    workType: String?,
    onlyNotGenerated: Boolean,
    sort: Int,
    groupOptions: List<String>,
    disciplineOptions: List<String>,
    ownerOptions: List<String>,
    officeOptions: List<String>,
    stateOptions: List<String>,
    workTypeOptions: List<String>,
    onApply: (group: String?, discipline: String?, owner: String?, office: String?, state: String?, workType: String?, onlyNotGenerated: Boolean, sort: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var draftGroup by remember { mutableStateOf(group) }
    var draftDiscipline by remember { mutableStateOf(discipline) }
    var draftOwner by remember { mutableStateOf(owner) }
    var draftOffice by remember { mutableStateOf(office) }
    var draftState by remember { mutableStateOf(state) }
    var draftWorkType by remember { mutableStateOf(workType) }
    var draftOnlyNotGenerated by remember { mutableStateOf(onlyNotGenerated) }
    var draftSort by remember { mutableStateOf(sort) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فیلتر پیشرفته", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("گروه ساختمانی", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftGroup == null,
                        onClick = { draftGroup = null },
                        label = { Text("همه", fontSize = 12.sp) }
                    )
                    groupOptions.forEach { g ->
                        FilterChip(
                            selected = draftGroup == g,
                            onClick = { draftGroup = g },
                            label = { Text("گروه $g", fontSize = 12.sp) }
                        )
                    }
                }
                FilterDropdown(label = "رشته", value = draftDiscipline, options = disciplineOptions, onSelect = { draftDiscipline = it })
                FilterDropdown(label = "مالک", value = draftOwner, options = ownerOptions, onSelect = { draftOwner = it })
                FilterDropdown(label = "دفتر", value = draftOffice, options = officeOptions, onSelect = { draftOffice = it })
                FilterDropdown(label = "وضعیت", value = draftState, options = stateOptions, onSelect = { draftState = it })
                FilterDropdown(label = "نوع کار", value = draftWorkType, options = workTypeOptions, onSelect = { draftWorkType = it })
                // Only cases whose report has not been generated yet: for
                // the selected stage, no local record and no own server
                // report; with no stage selected, no generated stage at all.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draftOnlyNotGenerated, onCheckedChange = { draftOnlyNotGenerated = it })
                    Text("فقط گزارش‌های تولید نشده", fontSize = 13.sp)
                }
                // Sort by remaining time (due date of the selected stage,
                // or the earliest due date with no stage selected):
                // ascending = least remaining / most overdue first.
                Text("مرتب‌سازی بر اساس زمان باقی‌مانده", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draftSort == 0,
                        onClick = { draftSort = 0 },
                        label = { Text("پیش‌فرض", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftSort == 1,
                        onClick = { draftSort = 1 },
                        label = { Text("صعودی ↑ (کمترین زمان اول)", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = draftSort == 2,
                        onClick = { draftSort = 2 },
                        label = { Text("نزولی ↓ (بیشترین زمان اول)", fontSize = 12.sp) }
                    )
                }
                OutlinedButton(
                    onClick = {
                        draftGroup = null
                        draftDiscipline = null
                        draftOwner = null
                        draftOffice = null
                        draftState = null
                        draftWorkType = null
                        draftOnlyNotGenerated = false
                        draftSort = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("پاک کردن همهٔ فیلترها", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(draftGroup, draftDiscipline, draftOwner, draftOffice, draftState, draftWorkType, draftOnlyNotGenerated, draftSort)
            }) { Text("اعمال فیلتر") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
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

/** Paths for the report generator (templates / signed templates / outputs).
 *  Stored in nezam_settings so the user can move them; each can be reset
 *  to its default from the generator's "مسیرها" section. */
internal const val DEFAULT_TEMPLATES_PATH = "/storage/emulated/0/NezamMonitor/templates"
internal const val DEFAULT_SIGNED_TEMPLATES_PATH = "/storage/emulated/0/NezamMonitor/templates_signed"
internal const val DEFAULT_OUTPUTS_PATH = "/storage/emulated/0/NezamMonitor/outputs"
/** Base folder for the Update screen's downloaded server reports:
 *  downloads land in NezamMonitor/downloads/<case>… (v3.8.8). */
internal const val DEFAULT_NEZAM_ROOT = "/storage/emulated/0/NezamMonitor"
/** Folder for the Excel + database exports (v3.8.8). */
internal const val DEFAULT_EXPORTS_PATH = "/storage/emulated/0/NezamMonitor/exports"

/** The exports folder from nezam_settings (key "exports_path"). */
internal fun loadExportsPath(context: Context): String {
    return try {
        context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
            .getString("exports_path", DEFAULT_EXPORTS_PATH) ?: DEFAULT_EXPORTS_PATH
    } catch (_: Exception) {
        DEFAULT_EXPORTS_PATH
    }
}

internal data class ReportPaths(
    val templatesPath: String = DEFAULT_TEMPLATES_PATH,
    val signedTemplatesPath: String = DEFAULT_SIGNED_TEMPLATES_PATH,
    val outputsPath: String = DEFAULT_OUTPUTS_PATH
)

internal fun loadReportPaths(context: Context): ReportPaths {
    return try {
        val prefs = context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
        ReportPaths(
            templatesPath = prefs.getString("templates_path", DEFAULT_TEMPLATES_PATH) ?: DEFAULT_TEMPLATES_PATH,
            signedTemplatesPath = prefs.getString("signed_templates_path", DEFAULT_SIGNED_TEMPLATES_PATH) ?: DEFAULT_SIGNED_TEMPLATES_PATH,
            outputsPath = prefs.getString("outputs_path", DEFAULT_OUTPUTS_PATH) ?: DEFAULT_OUTPUTS_PATH
        )
    } catch (_: Exception) {
        ReportPaths()
    }
}

internal fun saveReportPath(context: Context, key: String, value: String) {
    try {
        context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
            .edit().putString(key, value).apply()
    } catch (_: Exception) {
    }
}

/** "تولید گزارش امضا شده": when on, Word and PDF generation use the
 *  signed-templates folder instead of the normal one. */
internal fun loadSignedReport(context: Context): Boolean =
    try { context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE).getBoolean("signed_report", false) } catch (_: Exception) { false }

internal fun saveSignedReport(context: Context, value: Boolean) {
    try { context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE).edit().putBoolean("signed_report", value).apply() } catch (_: Exception) {}
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
