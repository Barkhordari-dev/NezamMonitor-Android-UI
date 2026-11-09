package com.nezammonitor.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.data.ImportEngine
import com.nezammonitor.app.ui.components.HeroActionCard
import com.nezammonitor.app.ui.components.MenuLinkCard
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToImport: () -> Unit = {},
    onNavigateToCases: () -> Unit = {},
    onNavigateToEngineers: () -> Unit = {},
    onNavigateToFees: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToFollowUp: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {},
    onNavigateToReportGenerator: () -> Unit = {},
    onNavigateToReportHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                viewModel.setImporting(true)
                val engine = ImportEngine(context)
                val result = withContext(Dispatchers.IO) {
                    engine.importFromUri(it)
                }
                viewModel.importData(result)
                viewModel.setImporting(false)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Import + Export
        item {
            HeroActionCard(
                emoji = if (isImporting) "⏳" else "📥",
                title = "ورود اطلاعات",
                subtitle = if (isImporting) "در حال ورود…" else "فایل .nzmdata",
                enabled = !isImporting,
                onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            HeroActionCard(
                emoji = "📤",
                title = "خروجی برای ویندوز",
                subtitle = "فایل اکسل",
                onClick = onNavigateToExport,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            HeroActionCard(
                emoji = "🔄",
                title = "بروزرسانی",
                subtitle = "از سامانه یزد",
                onClick = onNavigateToUpdate,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Import result — animates in/out, same message and dismiss action.
        item {
            AnimatedVisibility(
                visible = importResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (importResult?.success == true)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (importResult?.success == true) "✅ ${importResult?.message}" else "❌ ${importResult?.message}",
                            fontSize = 12.sp
                        )
                        TextButton(onClick = { viewModel.clearImportResult() }) {
                            Text("بستن")
                        }
                    }
                }
            }
        }

        // Row 2: Cases (clickable)
        item {
            StatCard("📋 پرونده\u200Cها", cases.size, MaterialTheme.colorScheme.primary, onClick = onNavigateToCases)
        }
        item {
            StatCard("👷 مهندسین", engineers.size, MaterialTheme.colorScheme.secondary, onClick = onNavigateToEngineers)
        }
        item {
            StatCard("💰 حق\u200Cالزحمه", fees.size, MaterialTheme.colorScheme.tertiary, onClick = onNavigateToFees)
        }
        item {
            StatCard("📄 گزارش\u200Cها", reports.size, MaterialTheme.colorScheme.error, onClick = onNavigateToReports)
        }

        // Row 3: FollowUp quick link
        item {
            MenuLinkCard(
                emoji = "📌",
                title = "پیگیری\u200Cها",
                onClick = { onNavigateToFollowUp() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Row 4: Report Generator
        item {
            MenuLinkCard(
                emoji = "📊",
                title = "تولید گزارش",
                onClick = { onNavigateToReportGenerator() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Row 5: Report History
        item {
            MenuLinkCard(
                emoji = "📦",
                title = "سابقه گزارش\u200Cها",
                onClick = { onNavigateToReportHistory() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Row 6: Settings
        item {
            MenuLinkCard(
                emoji = "⚙️",
                title = "تنظیمات",
                onClick = { onNavigateToSettings() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit = {}
) {
    NezamCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient accent bar (accent → transparent), UAC-style.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color, color.copy(alpha = 0.15f))
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Small muted label + big semi-bold number (reference rhythm).
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("$count", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}
