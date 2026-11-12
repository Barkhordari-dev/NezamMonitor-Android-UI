package com.nezammonitor.app.ui.screens.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.nezammonitor.app.data.*
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.screens.reportgen.gregorianToJalali
import com.nezammonitor.app.ui.screens.reportgen.toPersianDigitsStr
import com.nezammonitor.app.ui.theme.nezamSemantic
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val cases by viewModel.cases.collectAsState()
    val archivedCases by viewModel.archivedCases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    val edits by viewModel.followUpEdits.collectAsState()
    val receipts by viewModel.feeReceipts.collectAsState()
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var isExportingDb by remember { mutableStateOf(false) }
    // v3.8.7: the list of files already in the exports folder (survives
    // restarts, unlike the old one-shot share buttons). Refreshed on open
    // and after every successful creation.
    val exportsDir = remember { File(context.getExternalFilesDir(null), "exports") }
    var exportedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    fun refreshExportedFiles() {
        exportedFiles = exportsDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".xlsx") || it.name.endsWith(".db")) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
    LaunchedEffect(Unit) { refreshExportedFiles() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("خروجی اکسل", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") }
                }
            )
        }
    ) { padding ->
        // v3.8.7: scrollable — without this, everything below the fold
        // (the DB share button and the status card) was cut off on phones
        // and looked like a broken button.
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NezamCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("اطلاعات خروجی", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("پرونده\u200Cها: ${cases.size + archivedCases.size} (بایگانی‌شده: ${archivedCases.size})", fontSize = 14.sp)
                    Text("مهندسین: ${engineers.size}", fontSize = 14.sp)
                    Text("حق\u200Cالزحمه: ${fees.size}", fontSize = 14.sp)
                    Text("گزارش\u200Cها: ${reports.size}", fontSize = 14.sp)
                    Text("مشخصات: ${specs.size}", fontSize = 14.sp)
                    Text("تغییرات پیگیری: ${edits.size}", fontSize = 14.sp)
                    Text("رسیدها: ${receipts.size}", fontSize = 14.sp)
                }
            }

            NezamCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 خروجی اکسل", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("فایل اکسل شامل ۵ شیت: پرونده\u200Cها، ناظرین، حق\u200Cالزحمه، گزارش\u200Cها، پیگیری\u200Cها", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        statusMessage = "در حال ایجاد فایل اکسل..."
                        val result = withContext(Dispatchers.IO) {
                            createExcelExport(context, cases, engineers, fees, reports, specs, edits)
                        }
                        if (result.first) {
                            refreshExportedFiles()
                            statusMessage = "✅ فایل اکسل ایجاد شد"
                        } else {
                            statusMessage = "❌ ${result.second}"
                        }
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isExporting) "⏳ در حال ایجاد..." else "📊 ایجاد فایل اکسل", fontSize = 15.sp)
            }

            // ---- Windows database export (v3.8.6) ----
            NezamCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💾 خروجی دیتابیس ویندوز", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "فایل دیتابیس (.db) با ساختار نسخهٔ قدیمی ویندوز — همهٔ پرونده‌ها (حتی بایگانی‌شده‌ها)، مشخصات، مهندسین، حق‌الزحمه‌ها، گزارش‌ها، پیگیری‌ها و رسیدها. در ویندوز، از قسمت تاریخچه/اسنپ‌شات‌ها گزینهٔ «ورود دیتابیس قدیمی» آن را وارد کن.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isExportingDb = true
                        statusMessage = "در حال ایجاد فایل دیتابیس..."
                        val result = withContext(Dispatchers.IO) {
                            createWindowsDatabaseExport(
                                context,
                                cases + archivedCases,
                                specs, engineers, fees, reports, edits, receipts
                            )
                        }
                        if (result.first) {
                            refreshExportedFiles()
                            statusMessage = "✅ فایل دیتابیس ایجاد شد"
                        } else {
                            statusMessage = "❌ ${result.second}"
                        }
                        isExportingDb = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isExportingDb
            ) {
                if (isExportingDb) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isExportingDb) "⏳ در حال ایجاد..." else "💾 ایجاد فایل دیتابیس ویندوز", fontSize = 15.sp)
            }

            // ---- Output files (v3.8.7): where they are, when they were
            // made, with share + delete for every file. ----
            NezamCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📁 فایل‌های خروجی", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "محل ذخیره: ${exportsDir.absolutePath}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (exportedFiles.isEmpty()) {
                        Text(
                            "هنوز فایلی ساخته نشده است.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        exportedFiles.forEach { file ->
                            ExportedFileRow(
                                file = file,
                                onShare = { shareExportedFile(context, file) },
                                onDelete = { fileToDelete = file }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // Delete confirmation (v3.8.7).
            val doomed = fileToDelete
            if (doomed != null) {
                AlertDialog(
                    onDismissRequest = { fileToDelete = null },
                    title = { Text("حذف فایل", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                    text = { Text("فایل «${doomed.name}» حذف شود؟", fontSize = 14.sp) },
                    confirmButton = {
                        TextButton(onClick = {
                            val ok = try { doomed.delete() } catch (_: Exception) { false }
                            fileToDelete = null
                            if (ok) {
                                refreshExportedFiles()
                                statusMessage = "🗑️ فایل حذف شد"
                            } else {
                                statusMessage = "❌ حذف فایل انجام نشد"
                            }
                        }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { fileToDelete = null }) { Text("انصراف") }
                    }
                )
            }

            if (statusMessage.isNotBlank()) {
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = when {
                        statusMessage.startsWith("✅") -> MaterialTheme.nezamSemantic.successContainer
                        statusMessage.startsWith("❌") -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(statusMessage, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }
    }
}

// ==================== EXCEL GENERATION WITH APACHE POI ====================

private fun createExcelExport(
    context: Context,
    cases: List<CaseEntity>,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    reports: List<ReportEntity>,
    specs: List<SpecificationEntity>,
    edits: List<FollowUpEditEntity>
): Pair<Boolean, String> {
    return try {
        val dir = File(context.getExternalFilesDir(null), "exports")
        dir.mkdirs()
        val fileName = "NezamMonitor_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
        val file = File(dir, fileName)

        val wb = XSSFWorkbook()

        // Sheet 1: پرونده‌ها
        val ws1 = wb.createSheet("پرونده\u200Cها")
        val headers1 = arrayOf("ردیف", "شماره پرونده", "شماره سریال", "نام مالک", "شماره همراه", "وضعیت", "تاریخ کسر", "دفتر",
            "نام پدر", "کدملی", "آدرس", "کدپستی", "تلفن", "زادگاه", "شماره پروانه", "تاریخ پروانه",
            "کد نوسازی", "آدرس پروژه", "بلوک", "متراژ", "متراژ شهرداری", "متراژ نظارت", "طبقات", "واحدها",
            "گروه ساختمان", "نوع سازه", "نوع کاربری", "محدوده طرح", "صدور", "وضعیت", "نوع کار", "رشته", "تاریخ اجرا", "شناسه DB")
        val headerRow1 = ws1.createRow(0)
        headers1.forEachIndexed { i, h -> val cell = headerRow1.createCell(i); cell.setCellValue(h) }
        cases.forEachIndexed { r, c ->
            val row = ws1.createRow(r + 1)
            val values = arrayOf((r + 1).toString(), c.caseNumber, c.serial, c.owner, c.ownerMobile, c.responsibility, c.capacityDate, c.office,
                c.ownerFather, c.ownerNationalCode, c.ownerAddress, c.ownerZip, c.ownerTel, c.ownerBirthLoc,
                c.permitNumber, c.permitDate, c.renovationCode, c.address, c.blockNumber, c.landArea,
                c.municipalArea, c.effectiveArea, c.floors, c.units, c.buildingGroup, c.structureType,
                c.usageType, c.planZone, c.issuer, c.stateTitle, c.workType, c.discipline, c.executionDate, c.dbId)
            values.forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
        }

        // Sheet 2: ناظرین
        val ws2 = wb.createSheet("ناظرین")
        val headers2 = arrayOf("ردیف", "شماره پرونده", "نام مالک", "رشته", "نام ناظر")
        val headerRow2 = ws2.createRow(0)
        headers2.forEachIndexed { i, h -> val cell = headerRow2.createCell(i); cell.setCellValue(h) }
        engineers.forEachIndexed { r, e ->
            val row = ws2.createRow(r + 1)
            val caseOwner = cases.firstOrNull { it.caseNumber == e.caseNumber }?.owner ?: ""
            arrayOf((r + 1).toString(), e.caseNumber, caseOwner, e.discipline, e.name).forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
        }

        // Sheet 3: حق‌الزحمه
        val ws3 = wb.createSheet("حق\u200Cالزحمه")
        val headers3 = arrayOf("ردیف", "شماره پرونده", "نام مالک", "رشته", "مبلغ", "وضعیت پرداخت", "توضیحات")
        val headerRow3 = ws3.createRow(0)
        headers3.forEachIndexed { i, h -> val cell = headerRow3.createCell(i); cell.setCellValue(h) }
        fees.forEachIndexed { r, f ->
            val row = ws3.createRow(r + 1)
            val caseOwner = cases.firstOrNull { it.caseNumber == f.caseNumber }?.owner ?: ""
            arrayOf((r + 1).toString(), f.caseNumber, caseOwner, f.discipline, f.amount, f.payStatus, f.description).forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
        }

        // Sheet 4: گزارش‌ها
        val ws4 = wb.createSheet("گزارش\u200Cها")
        val headers4 = arrayOf("ردیف", "شماره پرونده", "نام مالک", "نوع گزارش", "مرحله", "مهندس", "رشته", "تاریخ", "سقف", "اندیکاتور")
        val headerRow4 = ws4.createRow(0)
        headers4.forEachIndexed { i, h -> val cell = headerRow4.createCell(i); cell.setCellValue(h) }
        reports.forEachIndexed { r, rpt ->
            val row = ws4.createRow(r + 1)
            val caseOwner = cases.firstOrNull { it.caseNumber == rpt.caseNumber }?.owner ?: ""
            arrayOf((r + 1).toString(), rpt.caseNumber, caseOwner, rpt.reportType, rpt.stage, rpt.engineer, rpt.discipline, rpt.visitDate, rpt.ceilingCount, rpt.indicator).forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
        }

        // Sheet 5: پیگیری
        val ws5 = wb.createSheet("پیگیری\u200Cها")
        val headers5 = arrayOf("ردیف", "شماره پرونده", "نام مالک", "آدرس", "همراه", "توضیحات")
        val headerRow5 = ws5.createRow(0)
        headers5.forEachIndexed { i, h -> val cell = headerRow5.createCell(i); cell.setCellValue(h) }
        edits.forEachIndexed { r, e ->
            val row = ws5.createRow(r + 1)
            val case = cases.firstOrNull { it.caseNumber == e.caseNumber }
            arrayOf((r + 1).toString(), e.caseNumber, case?.owner ?: "", case?.address ?: "", case?.ownerMobile ?: "", e.description).forEachIndexed { i, v -> row.createCell(i).setCellValue(v) }
        }

        // Fixed column widths (v3.8.6 — the crash fix): POI's autoSizeColumn
        // needs java.awt (SheetUtil → TextLayout / FontRenderContext),
        // which does not exist on Android. It threw NoClassDefFoundError —
        // an Error, not an Exception, so the old catch never caught it and
        // the app closed the moment the button was tapped. Widths are
        // 1/256 of a character, sized from the header text.
        for (ws in listOf(ws1, ws2, ws3, ws4, ws5)) {
            val headerRow = ws.getRow(0) ?: continue
            for (i in 0 until headerRow.lastCellNum.toInt()) {
                val header = headerRow.getCell(i)?.stringCellValue ?: ""
                val chars = (header.length + 4).coerceIn(12, 30)
                ws.setColumnWidth(i, chars * 256)
            }
        }

        FileOutputStream(file).use { out -> wb.write(out) }
        wb.close()

        Pair(true, file.absolutePath)
    } catch (e: Throwable) {
        // Throwable, not Exception: an Error (like the old AWT
        // NoClassDefFoundError) must show as an error message, never crash.
        Pair(false, "خطا: ${e.message}")
    }
}

// ==================== OUTPUT FILE LIST (v3.8.7) ====================

/** One row in the "فایل‌های خروجی" list: name, Jalali date + time, size,
 *  and share / delete buttons. */
@Composable
private fun ExportedFileRow(
    file: File,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    NezamCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (file.name.endsWith(".xlsx")) "📊 ${file.name}" else "💾 ${file.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📅 ${formatFileDateTime(file.lastModified())}  •  ${formatFileSize(file.length())}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Text("📱 اشتراک‌گذاری", fontSize = 13.sp)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("🗑️ حذف", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Shares one exported file (Excel or database) through the system chooser. */
private fun shareExportedFile(context: Context, file: File) {
    val mime = if (file.name.endsWith(".xlsx")) {
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    } else {
        "application/octet-stream"
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(null)
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری ${file.name}"))
}

/** File timestamp as a Jalali date + time, e.g. "۱۴۰۵/۰۸/۲۱ — ساعت ۱۹:۲۳". */
private fun formatFileDateTime(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val jalali = gregorianToJalali(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
    val time = String.format(
        Locale.US, "%02d:%02d",
        cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
    )
    return "${jalali.toPersianDigits()} — ساعت ${toPersianDigitsStr(time)}"
}

/** File size in Persian digits, e.g. "۲٫۳ مگابایت" / "۱۸ کیلوبایت". */
private fun formatFileSize(bytes: Long): String {
    val text = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f مگابایت", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024} کیلوبایت"
        else -> "$bytes بایت"
    }
    return toPersianDigitsStr(text).replace(".", "٫")
}
