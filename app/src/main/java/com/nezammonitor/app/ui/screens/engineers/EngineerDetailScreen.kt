package com.nezammonitor.app.ui.screens.engineers

import com.nezammonitor.app.api.toPersianChars
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
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineerDetailScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val case by viewModel.caseDetail.collectAsState()
    val engineers by viewModel.caseEngineers.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("مهندسین پرونده", fontSize = 16.sp)
                        Text(case?.caseNumber ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Case header
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مالک: ${toPersianChars(case?.owner ?: "")}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("پرونده: ${case?.caseNumber ?: ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        if (case?.ownerMobile?.isNotBlank() == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("همراه: ${case?.ownerMobile}", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "تماس",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        val cleaned = case?.ownerMobile?.replace(Regex("[^0-9+]"), "")
                                        val intent = Intent(Intent.ACTION_DIAL)
                                        intent.data = Uri.parse("tel:$cleaned")
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Engineers
            if (engineers.isNotEmpty()) {
                item {
                    SectionTitle("مهندسین (${engineers.size})")
                }
                items(engineers) { eng ->
                    val sina = remember(eng.name) { SinaEngineerStore.findByName(context, eng.name) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(toPersianChars(eng.name), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("رشته: ${toPersianChars(eng.discipline)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (eng.role.isNotBlank()) Text("نقش: ${eng.role}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (sina != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(formatSinaLevels(sina), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                                    } else if (eng.phone.isNotBlank() || eng.designLevel.isNotBlank()) {
                                        val fb = listOfNotNull(
                                            if (eng.phone.isNotBlank()) "همراه: ${eng.phone}" else null,
                                            if (eng.designLevel.isNotBlank()) "طراحی:${eng.designLevel}" else null,
                                            if (eng.supervisionLevel.isNotBlank()) "نظارت:${eng.supervisionLevel}" else null,
                                            if (eng.executionLevel.isNotBlank()) "اجرا:${eng.executionLevel}" else null
                                        ).joinToString(" | ")
                                        if (fb.isNotBlank()) { Spacer(modifier = Modifier.height(4.dp)); Text(fb, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                                val phone = when { sina?.phone?.isNotBlank() == true -> sina.phone; eng.phone.isNotBlank() -> eng.phone; else -> "" }
                                if (phone.isNotBlank()) {
                                    IconButton(onClick = {
                                        val c = phone.replace(Regex("[^0-9+]"), "")
                                        try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$c"))) } catch (_: Exception) {}
                                    }) { Icon(Icons.Default.Phone, contentDescription = "تماس", tint = MaterialTheme.colorScheme.primary) }
                                }
                            }
                            if (sina?.phone?.isNotBlank() == true) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                    val c = sina.phone.replace(Regex("[^0-9+]"), "")
                                    try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$c"))) } catch (_: Exception) {}
                                }) {
                                    Text(sina.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp)); Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text("مهندسی یافت نشد", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
