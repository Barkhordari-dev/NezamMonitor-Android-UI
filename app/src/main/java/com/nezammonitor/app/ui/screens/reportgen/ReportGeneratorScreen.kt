package com.nezammonitor.app.ui.screens.reportgen

import android.content.Context
import androidx.compose.foundation.clickable
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
import com.nezammonitor.app.data.*
import com.nezammonitor.app.ui.components.SectionTitle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportGeneratorScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val scope = rememberCoroutineScope()
    val today = remember { todayJalali() }
    // Stage 4 exists only if at least one case needs it (groups ب/ج/د by discipline),
    // using the same resolved group/discipline as the case rows below.
    val anyFourStage = remember(cases, specs, engineers, fees, reports) {
        cases.any { c ->
            val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }
            val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports)
            stageCountForCase(g, d) == 4
        }
    }

    var selectedStage by remember { mutableIntStateOf(0) }
    var useLtr by remember { mutableStateOf(false) }
    var selectedCases by remember { mutableStateOf(setOf<String>()) }
    var selectAll by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var resultFiles by remember { mutableStateOf(listOf<File>()) }
    var showDuplicateWarning by remember { mutableStateOf(false) }
    // Local generation history (SharedPreferences) — refreshed after each generation
    var localHistory by remember { mutableStateOf(loadReportHistory(context)) }

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
                    outputsDir = outputsDir
                )
            }
            resultFiles = result.files
            statusText = result.message
            isGenerating = false
            localHistory = loadReportHistory(context)
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
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        c != null && stageCountForCase(c.buildingGroup, c.discipline) >= 4
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

            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = selectAll, onCheckedChange = { selectAll = it; selectedCases = if (it) cases.filter { c -> val spec = specs.firstOrNull { it.caseNumber == c.caseNumber }; val (g, d) = resolveCaseGroupDiscipline(c, spec, engineers, fees, reports); selectedStage == 0 || selectedStage <= stageCountForCase(g, d) }.map { c -> c.caseNumber }.toSet() else emptySet() }); Text("انتخاب همه (${cases.size})", fontSize = 14.sp) } }

            items(cases) { case ->
                val spec = specs.firstOrNull { it.caseNumber == case.caseNumber }
                // The case row itself often lacks group/discipline/permit (the cases
                // endpoint leaves mas_title/bg_name blank): fall back to the
                // specification (group + permit, like the case detail screen) and to
                // the case's engineers/fees/reports (discipline, like the case
                // detail's engineer/fee sections).
                val (resolvedGroupRaw, resolvedDiscipline) = resolveCaseGroupDiscipline(case, spec, engineers, fees, reports)
                val stageCount = stageCountForCase(resolvedGroupRaw, resolvedDiscipline)
                val eligible = selectedStage == 0 || selectedStage <= stageCount
                val groupLetter = buildingGroupLetter(resolvedGroupRaw)
                // The permit date is the spec's "تاریخ صدور" (shown in the case detail
                // right after "شماره پروانه") — that is the source; the case row's own
                // permit date is only a fallback.
                val permitRaw = spec?.permitDate?.ifBlank { case.permitDate } ?: case.permitDate
                val registeredStages = remember(reports, case.caseNumber) {
                    reports.filter { it.caseNumber == case.caseNumber }
                        .mapNotNull { stageNumberFromTitle(it.stage).takeIf { n -> n > 0 } }.toSet()
                }
                val schedule = remember(case, spec, registeredStages) {
                    scheduleForCase(permitRaw, resolvedGroupRaw, stageCount, registeredStages, today)
                }
                // Generation history per stage: local file (with generation date+time)
                // and/or server report (with registration date) for this case.
                val historyByStage = remember(case, localHistory, reports) {
                    val map = mutableMapOf<Int, Pair<GeneratedReportRecord?, ReportEntity?>>()
                    localHistory.filter { it.caseNumber == case.caseNumber }.forEach { rec ->
                        val cur = map[rec.stage]
                        map[rec.stage] = rec to cur?.second
                    }
                    reports.filter { it.caseNumber == case.caseNumber }.forEach { rep ->
                        val n = stageNumberFromTitle(rep.stage)
                        if (n > 0) {
                            val cur = map[n]
                            map[n] = cur?.first to rep
                        }
                    }
                    map
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(enabled = eligible) { selectedCases = if (case.caseNumber in selectedCases) selectedCases - case.caseNumber else selectedCases + case.caseNumber }) {
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
            }

            if (statusText.isNotEmpty()) {
                item { Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (statusText.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) { Text(statusText, modifier = Modifier.padding(12.dp), fontSize = 13.sp) } }
            }

            if (resultFiles.isNotEmpty()) {
                item { Text("فایل‌های تولید شده:", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                items(resultFiles) { file ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { try { val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }) } catch (_: Exception) {} }) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text("📄 ${file.name}", fontSize = 13.sp); Text(file.parentFile?.name ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("باز کردن →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// ==================== GENERATION LOGIC ====================

private data class GenerateResult(val files: List<File>, val message: String)

private fun generateReports(context: Context, cases: List<CaseEntity>, allCases: List<CaseEntity>, engineers: List<EngineerEntity>, specs: List<SpecificationEntity>, stage: Int, useLtr: Boolean, templatesDir: File, outputsDir: File): GenerateResult {
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
            replacePlaceholders(outputFile, case, spec, caseEngineers, persianDate, useLtr)
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

private fun findTemplate(templatesDir: File, stageName: String, disciplines: List<String> = emptyList()): File? {
    if (!templatesDir.exists()) return null
    val candidates = disciplines.map { "$it مرحله $stageName.docx" } + "مرحله $stageName.docx"
    for (p in candidates) { val f = File(templatesDir, p); if (f.exists()) return f }
    return templatesDir.listFiles { f -> f.name.endsWith(".docx") }?.firstOrNull { it.name.contains(stageName) }
}

private fun replacePlaceholders(docxFile: File, case: CaseEntity, spec: SpecificationEntity?, engineers: List<EngineerEntity>, persianDate: String, useLtr: Boolean) {
    try {
        val doc = XWPFDocument(FileInputStream(docxFile))
        for (para in doc.paragraphs) replaceInParagraph(para, case, spec, engineers, persianDate, useLtr)
        for (table in doc.tables) for (row in table.rows) for (cell in row.tableCells) for (para in cell.paragraphs) replaceInParagraph(para, case, spec, engineers, persianDate, useLtr)
        FileOutputStream(docxFile).use { doc.write(it) }; doc.close()
    } catch (_: Exception) {}
}

private fun replaceInParagraph(para: org.apache.poi.xwpf.usermodel.XWPFParagraph, case: CaseEntity, spec: SpecificationEntity?, engineers: List<EngineerEntity>, persianDate: String, useLtr: Boolean) {
    val runs = para.runs; if (runs.isEmpty()) return
    var fullText = runs.joinToString("") { it.text() }

    val rawReplacements = mapOf(
        "«کد_نوسازی»" to (spec?.renovationCode ?: ""), "«تاریخ_گزارش»" to persianDate,
        "«شماره_پرونده»" to case.caseNumber, "«عنوان_بلوک»" to (spec?.blockTitle ?: ""),
        "«شماره_پروانه»" to (spec?.permitNumber ?: ""), "«تاریخ_صدور_پروانه»" to (spec?.permitDate ?: ""),
        "«تعداد_بلوک»" to (spec?.blockCount ?: ""), "«نوع_کاربری»" to (spec?.usageType ?: ""),
        "«نوع_سازه»" to (spec?.structureType ?: ""), "«گروه_ساختمانی»" to (spec?.buildingGroup ?: ""),
        "«تعداد_طبقات»" to (spec?.floors ?: ""), "«مالک»" to case.owner,
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

data class GeneratedReportRecord(val caseNumber: String = "", val owner: String = "", val stage: Int = 0, val createdAt: String = "", val createdTime: String = "", val filePath: String = "", val folderPath: String = "", val fileExists: Boolean = true)

internal fun saveReportHistory(context: Context, records: List<GeneratedReportRecord>) {
    try { val prefs = context.getSharedPreferences("nezam_report_history", Context.MODE_PRIVATE); val existing = loadReportHistory(context).toMutableList(); existing.addAll(records); prefs.edit().putString("history", com.google.gson.Gson().toJson(existing)).apply() } catch (_: Exception) {}
}
internal fun loadReportHistory(context: Context): List<GeneratedReportRecord> {
    return try {
        val prefs = context.getSharedPreferences("nezam_report_history", Context.MODE_PRIVATE)
        val json = prefs.getString("history", "[]") ?: "[]"
        val arr = com.google.gson.Gson().fromJson(json, Array<GeneratedReportRecord>::class.java)
        if (arr != null) arr.toList() else emptyList()
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
