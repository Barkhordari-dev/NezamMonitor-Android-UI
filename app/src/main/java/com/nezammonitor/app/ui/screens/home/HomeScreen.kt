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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.R
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
    // Logged-in user, from the API (MainViewModel). Blank = never logged in.
    val userName by viewModel.userName.collectAsState()
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
        // Hero Greeting Banner
        item(span = { GridItemSpan(2) }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "خوش آمدید",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "ناظر فعال یزد",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                userName.ifBlank { "کاربر گرامی" },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "سامانه جامع پایش پرونده‌ها و خدمات نظارت",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "تولید و توسعه: مهندس محمد مهدی برخورداری",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // App icon (the launcher icon) instead of the "MB" monogram.
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "آیکون برنامه",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
        }

        // Section Title: عملیات سریع
        item(span = { GridItemSpan(2) }) {
            Text(
                "عملیات سریع داده‌ها",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Row 1: Import + Export
        item {
            HeroActionCard(
                emoji = if (isImporting) "⏳" else "📥",
                title = "ورود اطلاعات",
                subtitle = if (isImporting) "در حال واردسازی…" else "فایل .nzmdata",
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
                title = "خروجی ویندوز",
                subtitle = "فایل اکسل گزارش",
                onClick = onNavigateToExport,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item(span = { GridItemSpan(2) }) {
            HeroActionCard(
                emoji = "🔄",
                title = "بروزرسانی مستقیم از سامانه یزد",
                subtitle = "اتصال به API و دریافت پرونده‌های جدید",
                onClick = onNavigateToUpdate,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Import result notification
        item(span = { GridItemSpan(2) }) {
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
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (importResult?.success == true) "✅ ${importResult?.message}" else "❌ ${importResult?.message}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(onClick = { viewModel.clearImportResult() }) {
                            Text("بستن پیام")
                        }
                    }
                }
            }
        }

        // Section Title: آمار و اطلاعات
        item(span = { GridItemSpan(2) }) {
            Text(
                "داشبورد آمار پرونده‌ها",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Stats Cards
        item {
            StatCard("📋 پرونده‌ها", cases.size, MaterialTheme.colorScheme.primary, onClick = onNavigateToCases)
        }
        item {
            StatCard("👷 مهندسین", engineers.size, MaterialTheme.colorScheme.secondary, onClick = onNavigateToEngineers)
        }
        item {
            StatCard("💰 حق‌الزحمه", fees.size, MaterialTheme.colorScheme.tertiary, onClick = onNavigateToFees)
        }
        item {
            StatCard("📄 گزارش‌ها", reports.size, MaterialTheme.colorScheme.error, onClick = onNavigateToReports)
        }

        // Section Title: دسترسی سریع
        item(span = { GridItemSpan(2) }) {
            Text(
                "بخش‌ها و گزارشات",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Quick Links
        item(span = { GridItemSpan(2) }) {
            MenuLinkCard(
                emoji = "📌",
                title = "پیگیری‌ها و یادداشت‌های پرونده",
                subtitle = "مدیریت یادداشت‌ها و فیلدهای پیگیری ناظر",
                onClick = onNavigateToFollowUp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item(span = { GridItemSpan(2) }) {
            MenuLinkCard(
                emoji = "📊",
                title = "تولید گزارش مهندسی",
                subtitle = "ساخت فایل Word گزارش ها",
                onClick = onNavigateToReportGenerator,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item(span = { GridItemSpan(2) }) {
            MenuLinkCard(
                emoji = "📦",
                title = "سابقه گزارش‌های تولیدشده",
                subtitle = "مشاهده و اشتراک‌گذاری گزارشات ذخیره‌شده",
                onClick = onNavigateToReportHistory,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item(span = { GridItemSpan(2) }) {
            MenuLinkCard(
                emoji = "⚙️",
                title = "تنظیمات و سفارشی‌سازی ظاهر",
                subtitle = "۹ تم اختصاصی، سبک کارت، اندازه فونت و چینش",
                onClick = onNavigateToSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    count: Int,
    color: Color,
    onClick: () -> Unit = {}
) {
    NezamCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color, color.copy(alpha = 0.2f))
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "$count",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "لمس جهت مشاهده",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
