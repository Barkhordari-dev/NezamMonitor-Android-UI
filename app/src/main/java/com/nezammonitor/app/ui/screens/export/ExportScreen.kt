package com.nezammonitor.app.ui.screens.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
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
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val specs by viewModel.specifications.collectAsState()
    val edits by viewModel.followUpEdits.collectAsState()
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var lastExportedFile by remember { mutableStateOf<File?>(null) }

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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NezamCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("اطلاعات خروجی", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("پرونده\u200Cها: ${cases.size}", fontSize = 14.sp)
                    Text("مهندسین: ${engineers.size}", fontSize = 14.sp)
                    Text("حق\u200Cالزحمه: ${fees.size}", fontSize = 14.sp)
                    Text("گزارش\u200Cها: ${reports.size}", fontSize = 14.sp)
                    Text("مشخصات: ${specs.size}", fontSize = 14.sp)
                    Text("تغییرات پیگیری: ${edits.size}", fontSize = 14.sp)
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
                            lastExportedFile = File(result.second)
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

            if (lastExportedFile != null) {
                Button(
                    onClick = {
                        val file = lastExportedFile ?: return@Button
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setPackage(null)
                        }
                        context.startActivity(Intent.createChooser(intent, "اشتراک\u200Cگذاری فایل اکسل"))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("📱 اشتراک\u200Cگذاری (بلوتوث، تلگرام، ایتا...)", fontSize = 14.sp)
                }
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

        // Auto-size columns
        for (ws in listOf(ws1, ws2, ws3, ws4, ws5)) {
            for (i in 0 until (ws.getRow(0)?.lastCellNum?.toInt() ?: 0)) {
                ws.autoSizeColumn(i)
            }
        }

        val out = FileOutputStream(file)
        wb.write(out)
        out.close()
        wb.close()

        Pair(true, file.absolutePath)
    } catch (e: Exception) {
        Pair(false, "خطا: ${e.message}")
    }
}
