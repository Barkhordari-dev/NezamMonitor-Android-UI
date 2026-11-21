package com.nezammonitor.app.ui.screens.directory

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
import com.nezammonitor.app.ui.components.NezamLoadingState
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.ui.screens.cases.DetailRow
import com.nezammonitor.app.viewmodel.MainViewModel

/**
 * Office detail page (v3.10.0): opened by tapping an office number
 * wherever it already appears (case list / case detail). NOT reachable
 * from any menu — there is no directory list screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryOfficeDetailScreen(
    viewModel: MainViewModel,
    officeName: String,
    onBack: () -> Unit,
    onEngineerClick: (engineerName: String) -> Unit
) {
    val detail by viewModel.directoryOffice.collectAsState()
    val loading by viewModel.directoryLoading.collectAsState()
    val message by viewModel.directoryMessage.collectAsState()
    // v3.11.6: read the delegated state ONCE into a plain local. Every
    // read of `detail` re-reads the StateFlow, so `detail!!` inside the
    // LazyColumn items could hit null after the branch check (a reload
    // or a back-press nulls the state mid-composition) — that was the
    // phone crash at :148. The captured local cannot change under us.
    val currentDetail = detail
    val context = LocalContext.current

    LaunchedEffect(officeName) {
        viewModel.loadDirectoryOfficeByName(officeName)
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
                title = { Text(currentDetail?.office?.name ?: officeName, fontSize = 16.sp) },
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
            loading && currentDetail == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    NezamLoadingState("در حال دریافت اطلاعات دفتر…")
                }
            }
            message != null && currentDetail == null -> {
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
            currentDetail != null -> {
                val office = currentDetail.office
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionTitle("اطلاعات دفتر")
                                Spacer(modifier = Modifier.height(8.dp))
                                // v3.11.6: no blank rows — a missing value
                                // shows muted «ثبت نشده» (DetailRow
                                // showPlaceholder), never an empty line.
                                DetailRow("نام دفتر", office.name, showPlaceholder = true)
                                DetailRow("شماره مجوز", office.licence, showPlaceholder = true)
                                DetailRow("مسئول دفتر", office.manager, showPlaceholder = true)
                                if (office.mobile.isBlank()) {
                                    Text(
                                        "همراه مسئول: ثبت نشده",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("همراه مسئول: ${office.mobile}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Icon(
                                            Icons.Default.Phone,
                                            contentDescription = "تماس",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { dialPhone(office.mobile) }
                                        )
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                DetailRow("رابط دفتر", office.contact, showPlaceholder = true)
                                DetailRow("آدرس", office.address, showPlaceholder = true)
                                DetailRow("تلفن", office.phone, showPlaceholder = true)
                                DetailRow("شهرستان", office.city, showPlaceholder = true)
                                DetailRow("استان صادرکننده", office.province, showPlaceholder = true)
                                if (office.updatedAt.isNotBlank()) {
                                    DetailRow("آخرین بروزرسانی", office.updatedAt)
                                }
                            }
                        }
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SectionTitle("اعضای دفتر (${currentDetail.members.size})")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "برای دیدن پروفایل هر مهندس، روی نامش بزنید.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (currentDetail.members.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "این دفتر عضوی ندارد",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(currentDetail.members) { member ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEngineerClick(
                                            "${member.name} ${member.family}".trim()
                                        )
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${member.name} ${member.family}".trim(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    // Discipline always comes from the fetched
                                    // data — never a literal in code.
                                    Text("رشته: ${member.discipline}", fontSize = 13.sp)
                                }
                                if (member.phone.isNotBlank()) {
                                    IconButton(onClick = { dialPhone(member.phone) }) {
                                        Icon(Icons.Default.Phone, contentDescription = "تماس", tint = MaterialTheme.colorScheme.primary)
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
