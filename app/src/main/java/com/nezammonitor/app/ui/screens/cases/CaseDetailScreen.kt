package com.nezammonitor.app.ui.screens.cases

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
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
import com.nezammonitor.app.ui.components.NezamLoadingState
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.ui.components.confirmStatusDisplay
import com.nezammonitor.app.ui.components.feeTitle
import com.nezammonitor.app.ui.components.reportStageLabel
import com.nezammonitor.app.ui.components.stageNameOnly
import com.nezammonitor.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val case by viewModel.caseDetail.collectAsState()
    val spec by viewModel.caseSpec.collectAsState()
    val engineers by viewModel.caseEngineers.collectAsState()
    val fees by viewModel.caseFees.collectAsState()
    val reports by viewModel.caseReports.collectAsState()
    val context = LocalContext.current

    fun dialPhone(number: String) {
        if (number.isNotBlank()) {
            val cleaned = number.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$cleaned")
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    if (case == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NezamLoadingState("در حال بارگذاری جزئیات پرونده…")
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(case?.caseNumber ?: "جزئیات پرونده", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Info
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionTitle("اطلاعات اصلی")
                        Spacer(modifier = Modifier.height(8.dp))
                        DetailRow("شماره پرونده", case?.caseNumber ?: "")
                        DetailRow("سریال", case?.serial ?: "")
                        DetailRow("مالک", case?.owner ?: "")
                        DetailRow("نام پدر", case?.ownerFather ?: "")
                        DetailRow("کدملی", case?.ownerNationalCode ?: "")
                        DetailRow("آدرس مالک", case?.ownerAddress ?: "")
                        DetailRow("کدپستی", case?.ownerZip ?: "")
                        DetailRow("تلفن", case?.ownerTel ?: "")
                        DetailRow("زادگاه", case?.ownerBirthLoc ?: "")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("همراه: ${case?.ownerMobile ?: ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (case?.ownerMobile?.isNotBlank() == true) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "تماس",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { dialPhone(case?.ownerMobile ?: "") }
                                )
                            }
                        }
                        DetailRow("دفتر", case?.office ?: "")
                        DetailRow("مسئولیت", case?.responsibility ?: "")
                        DetailRow("تاریخ کسر ظرفیت", case?.capacityDate ?: "")
                        DetailRow("نوع کار", case?.workType ?: "")
                        DetailRow("رشته", case?.discipline ?: "")
                    }
                }
            }

            // Building Specs
            if (spec != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionTitle("مشخصات ساختمان")
                            Spacer(modifier = Modifier.height(8.dp))
                            DetailRow("آدرس", spec?.address ?: "")
                            DetailRow("گروه ساختمانی", spec?.buildingGroup ?: "")
                            DetailRow("کد نوسازی", spec?.renovationCode ?: "")
                            DetailRow("نوع دستور", spec?.planInstructionType ?: "")
                            DetailRow("شماره دستور", spec?.planInstructionNo ?: "")
                            DetailRow("تاریخ دستور", spec?.planInstructionDate ?: "")
                            DetailRow("نوع سازه", spec?.structureType ?: "")
                            DetailRow("مساحت زمین", spec?.landArea ?: "")
                            DetailRow("متراژ نظارت", spec?.capacityArea ?: "")
                            DetailRow("متراژ شهرداری", case?.municipalArea ?: "")
                            DetailRow("عنوان بلوک", spec?.blockTitle ?: "")
                            DetailRow("تعداد بلوک", spec?.blockCount ?: "")
                            DetailRow("تعداد طبقات", spec?.floors ?: "")
                            DetailRow("تعداد واحد", spec?.units ?: "")
                            DetailRow("صادرکننده", spec?.issuer ?: "")
                            DetailRow("شماره پروانه", spec?.permitNumber ?: "")
                            DetailRow("تاریخ صدور", spec?.permitDate ?: "")
                            DetailRow("تاریخ رفع اثر", spec?.releaseDate ?: "")
                            DetailRow("ناحیه نقشه", spec?.planZone ?: "")
                            DetailRow("نوع کاربری", spec?.usageType ?: "")
                        }
                    }
                }
            }

            // Engineers — با اطلاعات سینا یزد + تماس
            if (engineers.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionTitle("مهندسین (${engineers.size})")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("اطلاعات تکمیلی از بانک اطلاعات مهندسین نظام مهندسی یزد", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(engineers) { eng ->
                    val sina = remember(eng.name) { SinaEngineerStore.findByName(context, eng.name) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(toPersianChars(eng.name), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("رشته: ${toPersianChars(eng.discipline)}", fontSize = 13.sp)
                                    if (eng.role.isNotBlank()) {
                                        Text("نقش: ${eng.role}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (sina != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatSinaLevels(sina), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                                    } else {
                                        // fallback: show phone/levels from case data if available
                                        if (eng.phone.isNotBlank() || eng.designLevel.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val fb = listOfNotNull(
                                                if (eng.phone.isNotBlank()) "همراه: ${eng.phone}" else null,
                                                if (eng.designLevel.isNotBlank()) "طراحی:${eng.designLevel}" else null,
                                                if (eng.supervisionLevel.isNotBlank()) "نظارت:${eng.supervisionLevel}" else null,
                                                if (eng.executionLevel.isNotBlank()) "اجرا:${eng.executionLevel}" else null
                                            ).joinToString(" | ")
                                            if (fb.isNotBlank()) Text(fb, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                // Phone call button — سینا اولویت، بعد فیلد پرونده
                                val phone = when {
                                    sina?.phone?.isNotBlank() == true -> sina.phone
                                    eng.phone.isNotBlank() -> eng.phone
                                    else -> ""
                                }
                                if (phone.isNotBlank()) {
                                    IconButton(onClick = { dialPhone(phone) }) {
                                        Icon(Icons.Default.Phone, contentDescription = "تماس با ${eng.name}", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (sina?.phone?.isNotBlank() == true) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dialPhone(sina.phone) }) {
                                    Text(sina.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Fees
            if (fees.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionTitle("حق\u200Cالزحمه (${fees.size})")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                items(fees) { fee ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(feeTitle(fee, fees), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(fee.amount, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            val confirm = confirmStatusDisplay(fee)
                            Text("پرداخت: ${fee.payStatus}" + if (confirm.isNotBlank()) " | تأیید: $confirm" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (fee.description.isNotBlank()) {
                                Text("توضیحات: ${fee.description}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Reports — نام مهندس با اطلاعات سینا
            if (reports.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionTitle("گزارش\u200Cها (${reports.size})")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                items(reports) { report ->
                    val sinaRep = remember(report.engineer) { SinaEngineerStore.findByName(context, report.engineer) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(toPersianChars(report.reportType), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val stageLabel = reportStageLabel(report.reportType, report.stage)
                                if (stageLabel.isNotBlank()) Text(stageLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("مهندس: ${toPersianChars(report.engineer)} | رشته: ${toPersianChars(report.discipline)}", fontSize = 12.sp)
                                    if (sinaRep != null) {
                                        Text(formatSinaLevels(sinaRep), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                if (sinaRep?.phone?.isNotBlank() == true) {
                                    IconButton(onClick = { dialPhone(sinaRep.phone) }) {
                                        Icon(Icons.Default.Phone, contentDescription = "تماس", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Text("تاریخ: ${report.visitDate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (sinaRep?.phone?.isNotBlank() == true) {
                                Text(sinaRep.phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { dialPhone(sinaRep.phone) })
                            }
                        }
                    }
                }
            }

            // Empty state
            if (engineers.isEmpty() && fees.isEmpty() && reports.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "اطلاعات تکمیلی یافت نشد",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
