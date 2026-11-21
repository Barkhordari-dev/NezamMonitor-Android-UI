package com.nezammonitor.app.ui.screens.directory

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nezammonitor.app.data.DirectoryParser
import com.nezammonitor.app.ui.components.NezamLoadingState
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.ui.screens.cases.DetailRow
import com.nezammonitor.app.viewmodel.MainViewModel

/**
 * Engineer profile page (v3.10.0): opened by tapping an engineer name
 * wherever it already appears (case detail, engineers screen, reports,
 * office member lists). NOT reachable from any menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryEngineerProfileScreen(
    viewModel: MainViewModel,
    engineerName: String,
    onBack: () -> Unit,
    onOfficeClick: (officeName: String) -> Unit
) {
    val profile by viewModel.directoryEngineer.collectAsState()
    val loading by viewModel.directoryLoading.collectAsState()
    val message by viewModel.directoryMessage.collectAsState()
    // v3.11.3: the photo URL shown here — the stored (snapshot) URL
    // first, replaced by the freshly fetched one when the detail-page
    // fetch succeeds. Null = initials fallback.
    val livePhoto by viewModel.directoryEngineerPhotoUrl.collectAsState()
    // v3.11.6: read the delegated state ONCE into a plain local (same
    // crash as the office screen: `profile!!` inside the LazyColumn
    // items could hit null after the branch check — the phone crash at
    // :199). The captured local cannot change under us.
    val currentProfile = profile
    val context = LocalContext.current

    LaunchedEffect(engineerName) {
        viewModel.loadDirectoryEngineerByName(engineerName)
    }

    fun dialPhone(number: String) {
        if (number.isNotBlank()) {
            val cleaned = number.replace(Regex("[^0-9+]"), "")
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:$cleaned")
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentProfile?.let { "${it.engineer.name} ${it.engineer.family}".trim() } ?: engineerName,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearDirectoryDetail()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading && currentProfile == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    NezamLoadingState("در حال دریافت اطلاعات مهندس…")
                }
            }
            message != null && currentProfile == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        message ?: "یافت نشد",
                        modifier = Modifier.padding(24.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            currentProfile != null -> {
                val eng = currentProfile.engineer
                val fullName = "${eng.name} ${eng.family}".trim()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Photo: the stored URL shows at once,
                                // then the freshly fetched URL replaces it
                                // when the detail-page fetch succeeds. The
                                // initials box stays behind it, so a
                                // missing/failed photo still shows
                                // something sensible.
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        fullName.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                            .take(2).joinToString("") { it.take(1) }.ifBlank { "؟" },
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!livePhoto.isNullOrBlank()) {
                                        AsyncImage(
                                            model = livePhoto,
                                            contentDescription = "عکس $fullName",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                // Discipline always comes from the fetched
                                // data — never a literal in code. A missing
                                // one shows muted «ثبت نشده», never blank.
                                if (eng.discipline.isBlank()) {
                                    Text("رشته: ثبت نشده", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("رشته: ${eng.discipline}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (eng.officeName.isBlank()) {
                                    Text(
                                        "دفتر: ثبت نشده",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        "دفتر: ${eng.officeName}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { onOfficeClick(eng.officeName) }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionTitle("مشخصات پروانه")
                                Spacer(modifier = Modifier.height(8.dp))
                                // v3.11.6: no blank rows — a missing grade
                                // (list-only engineers store "_") or date
                                // shows muted «ثبت نشده».
                                DetailRow("پایه طراحی", eng.designGrade, showPlaceholder = true)
                                DetailRow("پایه نظارت", eng.supervisionGrade, showPlaceholder = true)
                                DetailRow("پایه اجرا", eng.executionGrade, showPlaceholder = true)
                                DetailRow("تاریخ صدور پروانه", eng.licenceIssue, showPlaceholder = true)
                                DetailRow("تاریخ اعتبار پروانه", eng.licenceValidity, showPlaceholder = true)
                                if (eng.phone.isBlank()) {
                                    Text(
                                        "همراه: ثبت نشده",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("همراه: ${eng.phone}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = "تماس",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { dialPhone(eng.phone) }
                                        )
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                if (eng.updatedAt.isNotBlank()) {
                                    DetailRow("آخرین بروزرسانی", eng.updatedAt)
                                }
                            }
                        }
                    }
                    // v3.11.6: this card ALWAYS renders — for a list-only
                    // engineer it used to vanish, so the page changed
                    // shape. The section explains itself: the values are
                    // the DATES each licence grade was obtained. Only the
                    // grades actually obtained are listed, each fully
                    // labeled («پایه ۳: ۱۳۹۱/۱۲/۲۳»); "_" or a garbage
                    // date (e.g. year 1278) means "not obtained" and is
                    // not listed. A row with no obtained grade shows
                    // «ثبت نشده».
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionTitle("تاریخ اخذ پروانه")
                                Text(
                                    "تاریخ اخذ هر پایهٔ پروانه",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (currentProfile.history.isEmpty()) {
                                    Text(
                                        "موردی ثبت نشده است",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    currentProfile.history.forEach { row ->
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text(row.kind, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            val obtained = listOf(
                                                "پایه ۱" to DirectoryParser.licenceGradeDate(row.grade1),
                                                "پایه ۲" to DirectoryParser.licenceGradeDate(row.grade2),
                                                "پایه ۳" to DirectoryParser.licenceGradeDate(row.grade3)
                                            ).mapNotNull { (label, date) -> date?.let { label to it } }
                                            if (obtained.isEmpty()) {
                                                Text(
                                                    "ثبت نشده",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                obtained.forEach { (label, date) ->
                                                    Text(
                                                        "$label: ${DirectoryParser.toPersianDigits(date)}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                    // v3.11.6: this card ALWAYS renders too — an empty
                    // education list shows «موردی ثبت نشده است» instead
                    // of vanishing. The site's 1300/01/01 placeholder
                    // for an unknown date is never shown as a real date.
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionTitle("مدارک تحصیلی")
                                Spacer(modifier = Modifier.height(8.dp))
                                if (currentProfile.education.isEmpty()) {
                                    Text(
                                        "موردی ثبت نشده است",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    currentProfile.education.forEach { edu ->
                                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                            val degreeLine = "${edu.degree} ${edu.field}".trim()
                                            Text(
                                                if (degreeLine.isBlank()) "ثبت نشده" else degreeLine,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (degreeLine.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (edu.orientation.isNotBlank()) {
                                                Text("گرایش: ${edu.orientation}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(
                                                "${edu.universityType} ${edu.university} — ${edu.city}، ${edu.country}".trim(),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                DirectoryParser.educationDateText(edu.start, edu.end),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                    if (message != null) {
                        item {
                            Text(
                                message ?: "",
                                modifier = Modifier.padding(8.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
