package com.nezammonitor.app.ui.screens.reportgen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.CaseEntity
import com.nezammonitor.app.data.EngineerEntity
import com.nezammonitor.app.data.FollowUpEditEntity
import com.nezammonitor.app.data.SpecificationEntity
import com.nezammonitor.app.ui.components.FolderBrowserDialog
import com.nezammonitor.app.ui.components.stageNameForNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

// ==================== STEP-BY-STEP REPORT WIZARD (v3.11.0) ====================
// Opened from the long-press menu of a case row in the report generator.
// Four stages: (1) template fields, (2) the template's checkbox table,
// (3) the numbered توضیحات items, (4) output (LTR / PDF / share / generate).
// Generation reuses generateReports / generateReportPdfs, so the produced
// file has the same name and the same history row (with Word/PDF badges)
// as a normal generation — the only difference is the wizard's edits.

/** Stage 1 fields, in display order: placeholder key → label. Only these
 *  14 are editable; the other placeholders («ناظر_معماری» …) keep their
 *  auto values at generation time. */
private val WIZARD_STAGE1_FIELDS = listOf(
    "«کد_نوسازی»" to "کد نوسازی",
    "«تاریخ_گزارش»" to "تاریخ گزارش",
    "«شماره_پرونده»" to "شماره پرونده",
    "«عنوان_بلوک»" to "عنوان بلوک",
    "«شماره_پروانه»" to "شماره پروانه",
    "«تاریخ_صدور_پروانه»" to "تاریخ صدور پروانه",
    "«تعداد_بلوک»" to "تعداد بلوک",
    "«نوع_کاربری»" to "نوع کاربری",
    "«نوع_سازه»" to "نوع سازه",
    "«گروه_ساختمانی»" to "گروه ساختمانی",
    "«تعداد_طبقات»" to "تعداد طبقات",
    "«مالک»" to "مالک",
    "«نام ناظر»" to "مهندس ناظر",
    "«هماهنگکننده»" to "هماهنگ‌کننده"
)

private val WIZARD_STEP_TITLES = listOf("۱. فیلدها", "۲. چک‌باکس‌ها", "۳. توضیحات", "۴. خروجی")

@Composable
internal fun ReportWizardDialog(
    case: CaseEntity,
    spec: SpecificationEntity?,
    engineers: List<EngineerEntity>,
    specs: List<SpecificationEntity>,
    stage: Int,
    /** v3.11.1: how many stages this case has (for the stage picker),
     *  the suggested stage (nearest unregistered one, 0 = none), and
     *  the callback the picker uses. stage == 0 means "not picked yet"
     *  — the wizard shows the picker as its first step. */
    stageCount: Int = 4,
    suggestedStage: Int = 0,
    onStageSelected: (Int) -> Unit = {},
    templateFile: File?,
    templatesDir: File,
    /** v3.11.2: the other templates folder (signed vs unsigned) — the
     *  file picker lists its .docx files too, and generation tries it
     *  when the chosen folder has no template for the stage. */
    fallbackTemplatesDir: File? = null,
    outputsDir: File,
    allCases: List<CaseEntity>,
    userName: String,
    persianDate: String,
    initialUseLtr: Boolean,
    pdfMaxBytes: Long,
    followUpEdit: FollowUpEditEntity?,
    onSaveFollowUpEdit: (FollowUpEditEntity) -> Unit,
    onGenerated: (GenerateResult) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val caseEngineers = remember(case.caseNumber, engineers) {
        engineers.filter { it.caseNumber == case.caseNumber }
    }

    var step by remember { mutableIntStateOf(1) }
    var useLtr by remember { mutableStateOf(initialUseLtr) }
    var makePdf by remember { mutableStateOf(false) }

    // Stage 1: only the user's edits are stored. Anything unedited shows
    // the auto value (recomputed when LTR changes) and is computed fresh
    // at generation time with the final LTR setting — so an edited value is
    // used verbatim, and an unedited one always matches the LTR choice.
    var editedFields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val autoFields = remember(case, spec, caseEngineers, persianDate, useLtr, userName) {
        reportFieldValues(case, spec, caseEngineers, persianDate, useLtr, userName)
    }
    fun fieldValue(key: String): String = editedFields[key] ?: autoFields[key] ?: ""

    // v3.11.2: the file the wizard actually uses — the one the user
    // picked with «انتخاب فایل تمپلیت», or the one the generator found.
    // Picking copies the file into the templates folder as «N.docx», so
    // generation finds it through the normal path too.
    var pickedTemplateFile by remember { mutableStateOf<File?>(null) }
    val effectiveTemplateFile = pickedTemplateFile ?: templateFile
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showTemplateFolderPicker by remember { mutableStateOf(false) }
    var pickerExtraDir by remember { mutableStateOf<File?>(null) }
    var templatePickStatus by remember { mutableStateOf("") }

    // The .docx files actually present in the searched folders — shown
    // in the "no template" diagnosis and in the file picker, so a
    // name/folder mismatch is visible on the phone instead of guessed.
    val presentTemplateFiles = remember(templatesDir, fallbackTemplatesDir, pickerExtraDir, templatePickStatus) {
        listOfNotNull(templatesDir, fallbackTemplatesDir, pickerExtraDir).flatMap { dir ->
            dir.listFiles { f -> f.isFile && f.name.lowercase().endsWith(".docx") }
                ?.map { it to dir } ?: emptyList()
        }.sortedBy { it.first.name.lowercase() }
    }

    fun pickTemplateFile(source: File) {
        // Copy into the app's templates folder under the stage name, so
        // the wizard AND the normal generation both find it from now on.
        // «N.docx» cannot already exist here: findTemplate would have
        // returned it and the picker would never have been offered.
        val dest = File(templatesDir, "$stage.docx")
        val chosen = try {
            templatesDir.mkdirs()
            if (source.canonicalPath == dest.canonicalPath) source
            else {
                source.copyTo(dest, overwrite = false)
                dest
            }
        } catch (_: Exception) {
            source
        }
        pickedTemplateFile = chosen
        templatePickStatus = if (chosen != source) {
            "✅ «${source.name}» در پوشهٔ تمپلیت‌ها با نام «$stage.docx» کپی شد."
        } else {
            "✅ تمپلیت «${source.name}» انتخاب شد."
        }
        showTemplatePicker = false
    }

    // Template data (checkbox table + توضیحات items), read once on IO.
    var templateData by remember { mutableStateOf<WizardTemplateData?>(null) }
    var templateLoading by remember { mutableStateOf(true) }

    // Stage 2 selections: row index → chosen column (radio rule).
    var checkboxSelections by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    // Stage 3 items, initialized from the template (or the standard items
    // when the template's are empty). Null = the template has no توضیحات
    // table at all, so the stage is skipped — and generation leaves the
    // template's توضیحات untouched.
    var descriptionItems by remember { mutableStateOf<List<String>?>(null) }
    var selectedDescriptionRows by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(effectiveTemplateFile) {
        // v3.11.2: a new template means new rows and new items — the old
        // selections/items belonged to the previous template (picking
        // stage 1 then stage 2 used to leave stage 1's items in stage 3
        // and stage 1's ticks in stage 2). Reset first, then load; the
        // templateData effect below re-initializes the items from the
        // new template.
        checkboxSelections = emptyMap()
        descriptionItems = null
        selectedDescriptionRows = emptySet()
        templateLoading = true
        templateData = withContext(Dispatchers.IO) {
            if (effectiveTemplateFile == null) null else loadWizardTemplateData(effectiveTemplateFile)
        }
        templateLoading = false
    }
    LaunchedEffect(templateData) {
        if (templateData != null && descriptionItems == null) {
            descriptionItems = templateData!!.descriptionItems?.let { initialDescriptionItems(it) }
        }
    }
    var followUpStatus by remember { mutableStateOf("") }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    // The follow-up description as the wizard knows it (its own adds are
    // applied locally right away, so a second add cannot append twice to a
    // stale value while the database flow is still catching up). Initialized
    // once from the prop at open; the prop is not re-read afterwards.
    var localFollowUpDescription by remember {
        mutableStateOf(followUpEdit?.description ?: "")
    }

    // Stage 4 generation.
    var isGenerating by remember { mutableStateOf(false) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var resultMessage by remember { mutableStateOf("") }
    var marksVerified by remember { mutableStateOf<String?>(null) }

    fun doWizardGenerate() {
        if (isGenerating) return
        isGenerating = true
        resultMessage = ""
        resultFiles = emptyList()
        marksVerified = null
        scope.launch {
            try {
                val items = descriptionItems
                val result = withContext(Dispatchers.IO) {
                    if (makePdf) {
                        generateReportPdfs(
                            context = context, cases = listOf(case), engineers = engineers,
                            specs = specs, stage = stage, useLtr = useLtr,
                            templatesDir = templatesDir,
                            fallbackTemplatesDir = fallbackTemplatesDir,
                            outputsDir = outputsDir,
                            userName = userName, maxBytes = pdfMaxBytes,
                            fieldOverrides = editedFields,
                            checkboxSelections = checkboxSelections,
                            descriptionItems = items
                        )
                    } else {
                        generateReports(
                            context = context, cases = listOf(case), allCases = allCases,
                            engineers = engineers, specs = specs, stage = stage,
                            useLtr = useLtr, templatesDir = templatesDir,
                            fallbackTemplatesDir = fallbackTemplatesDir,
                            outputsDir = outputsDir, userName = userName,
                            fieldOverrides = editedFields,
                            checkboxSelections = checkboxSelections,
                            descriptionItems = items
                        )
                    }
                }
                resultFiles = result.files
                resultMessage = result.message
                onGenerated(result)
                // Verify the checkbox marks actually landed in the Word
                // file (the PDF result lists only the PDF; the Word file
                // sits next to it with the same name).
                if (checkboxSelections.isNotEmpty()) {
                    val wordFile = if (makePdf) {
                        result.files.firstOrNull()
                            ?.let { File(it.parentFile, it.nameWithoutExtension + ".docx") }
                    } else {
                        result.files.firstOrNull { it.name.endsWith(".docx") }
                    }
                    marksVerified = withContext(Dispatchers.IO) {
                        try {
                            if (wordFile == null || !wordFile.exists()) {
                                "⚠️ فایل Word برای بررسی تیک‌ها پیدا نشد"
                            } else {
                                val doc = XWPFDocument(FileInputStream(wordFile))
                                val count = countMarkedCheckboxes(doc)
                                doc.close()
                                if (count == checkboxSelections.size) {
                                    "✅ ${toPersianDigitsStr(count.toString())} تیک در فایل ثبت شد"
                                } else {
                                    "⚠️ فقط ${toPersianDigitsStr(count.toString())} تیک از " +
                                        "${toPersianDigitsStr(checkboxSelections.size.toString())} تیک در فایل ثبت شد — فایل را بررسی کنید"
                                }
                            }
                        } catch (_: Exception) {
                            "⚠️ بررسی تیک‌ها انجام نشد"
                        }
                    }
                }
            } catch (e: Exception) {
                resultMessage = "❌ خطا در تولید گزارش: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isGenerating = false
            }
        }
    }

    // Follow-up import (stage 3): the case's follow-up description plus its
    // 4 edit fields, appended as new items unless already present.
    fun importFromFollowUp() {
        val items = descriptionItems ?: return
        val values = listOfNotNull(
            localFollowUpDescription,
            followUpEdit?.caseNumberEdit,
            followUpEdit?.ownerEdit,
            followUpEdit?.addressEdit,
            followUpEdit?.ownerMobileEdit
        ).map { it.trim() }.filter { it.isNotBlank() }
        val fresh = values.filter { v -> items.none { it.trim() == v } }
        if (fresh.isEmpty()) {
            followUpStatus = if (values.isEmpty()) "در پیگیری این پرونده توضیحی ثبت نشده است"
                else "همهٔ موارد پیگیری قبلاً در توضیحات هست"
        } else {
            descriptionItems = items + fresh
            followUpStatus = "✅ ${toPersianDigitsStr(fresh.size.toString())} مورد از پیگیری اضافه شد"
        }
    }

    // Follow-up add (stage 3): append the selected items to the case's
    // follow-up description, each with the current Jalali date+time.
    // Only the description changes — the other 4 edit fields are kept
    // exactly as they are (last-writer-wins by updatedAt is untouched:
    // saveFollowUpEdit stamps the new updatedAt).
    fun addSelectedToFollowUp() {
        val items = descriptionItems ?: return
        val selected = selectedDescriptionRows.sorted().mapNotNull { items.getOrNull(it) }
            .map { it.trim() }.filter { it.isNotBlank() }
        if (selected.isEmpty()) return
        val stamp = nowJalaliTimestamp()
        val lines = selected.map { "$stamp — $it" }
        val base = localFollowUpDescription.trimEnd()
        val newDescription = (if (base.isBlank()) "" else "$base\n") + lines.joinToString("\n")
        localFollowUpDescription = newDescription
        val updated = (followUpEdit ?: FollowUpEditEntity(caseNumber = case.caseNumber))
            .copy(description = newDescription)
        onSaveFollowUpEdit(updated)
        selectedDescriptionRows = emptySet()
        followUpStatus = "✅ ${toPersianDigitsStr(selected.size.toString())} ردیف به توضیحات پیگیری اضافه شد ($stamp)"
    }

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📝 صدور مرحله‌ای گزارش", fontSize = 16.sp)
                        Text(
                            "${if (stage > 0) toPersianDigitsStr(stage.toString()) else "انتخاب مرحله"} — ${toPersianChars(case.owner)} — ${case.caseNumber}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { if (!isGenerating) onDismiss() }) { Text("✕ بستن", fontSize = 13.sp) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Step indicator (hidden until a stage is picked).
                if (stage > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WIZARD_STEP_TITLES.forEachIndexed { index, title ->
                            val n = index + 1
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (n == step) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    title, fontSize = 11.sp,
                                    color = if (n == step) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (templatePickStatus.isNotBlank()) {
                    Text(templatePickStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Content
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (stage == 0) {
                        // v3.11.1: stage picker — the wizard's first step
                        // when no stage was picked on the generator
                        // screen. The checkbox table and the template both
                        // depend on the stage, so nothing else can run
                        // before it. Tapping a stage opens step 1 with it.
                        Text(
                            "مرحلهٔ گزارش را انتخاب کنید:",
                            fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "جدول چک‌باکس و تمپلیت به مرحله وابسته‌اند؛ بعد از انتخاب، صدور مرحله‌ای از مرحلهٔ ۱ شروع می‌شود.",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        for (n in 1..stageCount) {
                            Button(
                                onClick = { onStageSelected(n) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(50.dp)
                            ) {
                                Text(
                                    "مرحلهٔ ${stageNameForNumber(n)}" +
                                        if (n == suggestedStage) " — پیشنهاد" else "",
                                    fontSize = 14.sp
                                )
                            }
                        }
                        if (suggestedStage > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "پیشنهاد: مرحلهٔ ${stageNameForNumber(suggestedStage)} — نزدیک‌ترین مرحله‌ای که هنوز برای این پرونده ثبت نشده است.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else when (step) {
                        1 -> {
                            Text(
                                "مقدارها به‌صورت خودکار پر شده‌اند؛ هر کدام را که می‌خواهید تغییر دهید.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            for ((key, label) in WIZARD_STAGE1_FIELDS) {
                                OutlinedTextField(
                                    value = fieldValue(key),
                                    onValueChange = { editedFields = editedFields + (key to it) },
                                    label = { Text(label, fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = useLtr, onCheckedChange = { useLtr = it })
                                Text("تولید چپ‌چین (LTR)", fontSize = 13.sp)
                            }
                        }
                        2 -> {
                            if (templateLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("در حال خواندن تمپلیت...", fontSize = 13.sp)
                                }
                            } else if (effectiveTemplateFile == null) {
                                // v3.11.2: say WHICH folders were searched
                                // AND which .docx files are actually there,
                                // so a name/folder mismatch is visible on
                                // the phone — plus a button to pick a
                                // template file (it is copied into the
                                // templates folder as «N.docx»).
                                TemplateMissingContent(
                                    stage = stage,
                                    templatesDir = templatesDir,
                                    fallbackTemplatesDir = fallbackTemplatesDir,
                                    presentFiles = presentTemplateFiles,
                                    onPickTemplate = { showTemplatePicker = true }
                                )
                            } else if (templateData == null) {
                                Text(
                                    "فایل تمپلیت «${effectiveTemplateFile.name}» خوانده نشد (پوشه: ${templatesDir.absolutePath}).",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (templateData?.checkboxTable == null) {
                                Text(
                                    "در تمپلیت «${effectiveTemplateFile.name}» جدول چک‌باکس نیست؛ این مرحله رد می‌شود و گزارش بدون تیک تولید می‌شود.",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val table = templateData!!.checkboxTable!!
                                Text(
                                    "در هر ردیف فقط یک چک‌باکس قابل انتخاب است؛ زدن دومی، اولی را برمی‌دارد. چک‌باکس انتخاب‌شده در گزارش نهایی تیک مشکی می‌خورد.",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                for (row in table.rows) {
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "${toPersianDigitsStr((row.index + 1).toString())}. ${row.description}",
                                                fontSize = 13.sp
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                // v3.11.2: the labels are
                                                // the file's own header
                                                // cells (table.headers),
                                                // not a fixed list — a
                                                // fourth status column
                                                // shows up here as-is.
                                                table.headers.forEachIndexed { col, colLabel ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = checkboxSelections[row.index] == col,
                                                            onCheckedChange = {
                                                                checkboxSelections = selectCheckbox(checkboxSelections, row.index, col)
                                                            }
                                                        )
                                                        Text(colLabel, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            if (templateLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("در حال خواندن تمپلیت...", fontSize = 13.sp)
                                }
                            } else if (effectiveTemplateFile == null) {
                                // v3.11.2: same diagnosis as stage 2 — the
                                // searched folders, the .docx files in
                                // them, and the pick button.
                                TemplateMissingContent(
                                    stage = stage,
                                    templatesDir = templatesDir,
                                    fallbackTemplatesDir = fallbackTemplatesDir,
                                    presentFiles = presentTemplateFiles,
                                    onPickTemplate = { showTemplatePicker = true }
                                )
                            } else if (templateData == null) {
                                Text(
                                    "فایل تمپلیت «${effectiveTemplateFile.name}» خوانده نشد (پوشه: ${templatesDir.absolutePath}).",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (descriptionItems == null) {
                                Text(
                                    "در تمپلیت «${effectiveTemplateFile.name}» بخش توضیحات نیست؛ این مرحله رد می‌شود و توضیحات تمپلیت دست‌نخورده می‌ماند.",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val items = descriptionItems!!
                                Text(
                                    "ردیف‌های شماره‌دار توضیحات. ردیف‌ها را ویرایش کنید، کم یا زیاد کنید؛ در گزارش نهایی به همین ترتیب شماره‌گذاری می‌شوند.",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                items.forEachIndexed { index, item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = index in selectedDescriptionRows,
                                            onCheckedChange = {
                                                selectedDescriptionRows = if (index in selectedDescriptionRows) {
                                                    selectedDescriptionRows - index
                                                } else {
                                                    selectedDescriptionRows + index
                                                }
                                            }
                                        )
                                        OutlinedTextField(
                                            value = item,
                                            onValueChange = { newValue ->
                                                descriptionItems = items.toMutableList().also { it[index] = newValue }
                                            },
                                            label = { Text("ردیف ${toPersianDigitsStr((index + 1).toString())}", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                                        )
                                        TextButton(onClick = {
                                            descriptionItems = items.toMutableList().also { it.removeAt(index) }
                                            selectedDescriptionRows = selectedDescriptionRows
                                                .filter { it != index }
                                                .map { if (it > index) it - 1 else it }.toSet()
                                        }) { Text("🗑", fontSize = 14.sp) }
                                    }
                                }
                                OutlinedButton(onClick = {
                                    descriptionItems = items + ""
                                    followUpStatus = ""
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("＋ افزودن ردیف", fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("پیگیری‌های همین پرونده:", fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { importFromFollowUp() }) {
                                        Text("📥 ورود از پیگیری", fontSize = 12.sp)
                                    }
                                    OutlinedButton(onClick = { showFollowUpDialog = true }) {
                                        Text("👁 نمایش توضیحات پیگیری", fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { addSelectedToFollowUp() },
                                    enabled = selectedDescriptionRows.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("➕ افزودن ردیف‌های انتخاب‌شده به پیگیری", fontSize = 12.sp)
                                }
                                if (followUpStatus.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(followUpStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        4 -> {
                            Text(
                                "خروجی با همان مسیر تولید معمولی ساخته می‌شود؛ مقدارهای ویرایش‌شده جای مقدارهای خودکار را می‌گیرند و گزارش در سابقه می‌آید.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = useLtr, onCheckedChange = { useLtr = it })
                                Text("تولید چپ‌چین (LTR)", fontSize = 13.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = makePdf, onCheckedChange = { makePdf = it })
                                Column {
                                    Text("تبدیل به PDF هم انجام شود", fontSize = 13.sp)
                                    Text(
                                        "فایل Word همیشه ساخته می‌شود؛ PDF هم در همان پوشه ساخته می‌شود.",
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { doWizardGenerate() },
                                enabled = !isGenerating,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                if (isGenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("📊 تولید", fontSize = 15.sp)
                            }
                            if (resultMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (resultMessage.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(resultMessage, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                                }
                            }
                            if (marksVerified != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(marksVerified!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            if (resultFiles.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("فایل‌های تولید شده:", fontSize = 14.sp)
                                for (file in resultFiles) {
                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("📄 ${file.name}", fontSize = 13.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(onClick = { openFileWithViewer(context, file) }) {
                                                    Text("📁 باز کردن", fontSize = 12.sp)
                                                }
                                                TextButton(onClick = { shareFile(context, file) }) {
                                                    Text("📤 اشتراک‌گذاری", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Navigation (hidden until a stage is picked — the picker
                // buttons above confirm the stage directly).
                if (stage > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (step > 1) {
                            OutlinedButton(
                                onClick = { step-- },
                                enabled = !isGenerating,
                                modifier = Modifier.weight(1f)
                            ) { Text("مرحلهٔ قبل", fontSize = 13.sp) }
                        }
                        if (step < 4) {
                            Button(
                                onClick = { step++ },
                                enabled = !isGenerating,
                                modifier = Modifier.weight(1f)
                            ) { Text("مرحلهٔ بعد", fontSize = 13.sp) }
                        } else {
                            OutlinedButton(
                                onClick = { if (!isGenerating) onDismiss() },
                                modifier = Modifier.weight(1f)
                            ) { Text("بستن", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }

    // Follow-up viewer (stage 3): the case's follow-up description and its
    // 4 edit fields, read-only.
    if (showFollowUpDialog) {
        AlertDialog(
            onDismissRequest = { showFollowUpDialog = false },
            title = { Text("توضیحات پیگیری همین پرونده", fontSize = 15.sp) },
            text = {
                Column {
                    val fields = listOf(
                        "توضیحات" to localFollowUpDescription,
                        "شماره پرونده" to (followUpEdit?.caseNumberEdit ?: ""),
                        "مالک" to (followUpEdit?.ownerEdit ?: ""),
                        "آدرس" to (followUpEdit?.addressEdit ?: ""),
                        "همراه مالک" to (followUpEdit?.ownerMobileEdit ?: "")
                    ).filter { it.second.isNotBlank() }
                    if (fields.isEmpty()) {
                        Text("برای این پرونده توضیح پیگیری ثبت نشده است", fontSize = 13.sp)
                    } else {
                        fields.forEach { (label, value) ->
                            Text("$label: $value", fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFollowUpDialog = false }) { Text("بستن") }
            }
        )
    }

    // v3.11.2: template file picker — the .docx files in the searched
    // folders (plus an extra folder picked with the shared folder
    // browser). Tapping a file copies it into the templates folder as
    // «N.docx» and the wizard loads it at once.
    if (showTemplatePicker) {
        Dialog(
            onDismissRequest = { showTemplatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize().padding(8.dp), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text("📁 انتخاب فایل تمپلیت", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "مرحلهٔ ${stageNameForNumber(stage)} — فایل انتخاب‌شده در پوشهٔ تمپلیت‌ها با نام «$stage.docx» کپی می‌شود و از همین‌جا استفاده می‌شود.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        if (presentTemplateFiles.isEmpty()) {
                            Text(
                                "در این پوشه‌ها هیچ فایل Word نیست.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            for ((file, dir) in presentTemplateFiles) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .clickable { pickTemplateFile(file) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("📄 ${file.name}", fontSize = 13.sp)
                                        Text(
                                            dir.absolutePath,
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showTemplateFolderPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📁 پوشهٔ دیگر…", fontSize = 13.sp) }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { showTemplatePicker = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بستن") }
                }
            }
        }
    }
    if (showTemplateFolderPicker) {
        FolderBrowserDialog(
            title = "پوشهٔ تمپلیت را انتخاب کنید",
            initialPath = templatesDir.absolutePath,
            onPick = { path ->
                pickerExtraDir = File(path)
                showTemplateFolderPicker = false
            },
            onDismiss = { showTemplateFolderPicker = false }
        )
    }
}

/** v3.11.2: what wizard stages 2/3 show when no template file was
 *  found for the stage — the searched folders, the .docx files actually
 *  present in them (so a name/folder mismatch is visible on the phone),
 *  and a button to pick a template file. Nothing here is hardcoded: the
 *  rows and items always come from the picked template file. */
@Composable
private fun TemplateMissingContent(
    stage: Int,
    templatesDir: File,
    fallbackTemplatesDir: File?,
    presentFiles: List<Pair<File, File>>,
    onPickTemplate: () -> Unit
) {
    Text(
        "برای مرحلهٔ ${toPersianDigitsStr(stage.toString())} تمپلیتی پیدا نشد.",
        fontSize = 13.sp
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "پوشه‌های جستجو:\n• ${templatesDir.absolutePath}" +
            (fallbackTemplatesDir?.let { "\n• ${it.absolutePath}" } ?: ""),
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (presentFiles.isEmpty()) {
        Text(
            "در این پوشه‌ها هیچ فایل Word نیست — فایل مرحله (مثلاً «$stage.docx») را در یکی از همین پوشه‌ها بگذارید، یا از دکمهٔ زیر انتخاب کنید.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            "فایل‌های Word موجود در این پوشه‌ها:",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        for ((file, dir) in presentFiles) {
            Text(
                "• ${file.name}  (${dir.name})",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "هیچ‌کدام با مرحلهٔ ${toPersianDigitsStr(stage.toString())} جور نیست — اگر فایل درست همین‌جاست و نام دیگری دارد، از دکمهٔ زیر انتخابش کنید.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(onClick = onPickTemplate, modifier = Modifier.fillMaxWidth()) {
        Text("📁 انتخاب فایل تمپلیت", fontSize = 13.sp)
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "این مرحله رد می‌شود و گزارش بدون آن تولید می‌شود.",
        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
