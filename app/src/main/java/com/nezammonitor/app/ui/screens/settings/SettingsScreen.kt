package com.nezammonitor.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamSegmentedControl
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.ui.components.ThemeOptionRow
import com.nezammonitor.app.ui.theme.AppTheme
import com.nezammonitor.app.viewmodel.MainViewModel

data class AppPreferences(
    val themeMode: Int = 0, // 0=Light, 1=Dark, 2=System
    val appTheme: AppTheme = AppTheme.LIGHT_PREMIUM,
    val isRtl: Boolean = true,
    val fontScale: Float = 1.0f
) {
    companion object {
        fun load(context: Context): AppPreferences {
            val prefs = context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
            return AppPreferences(
                themeMode = prefs.getInt("theme", 0),
                // New key; older installs without it fall back to LIGHT_PREMIUM.
                appTheme = AppTheme.fromName(prefs.getString("app_theme", null)),
                isRtl = prefs.getBoolean("rtl", true),
                fontScale = prefs.getFloat("font_scale", 1.0f)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE).edit()
            .putInt("theme", themeMode)
            .putString("app_theme", appTheme.name)
            .putBoolean("rtl", isRtl)
            .putFloat("font_scale", fontScale)
            .apply()
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(AppPreferences.load(context)) }

    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("تنظیمات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))

        // Theme — mode + 5 colour themes. Saving writes SharedPreferences,
        // MainActivity listens to it and applies the theme instantly.
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("پوسته (Theme)")
                Spacer(modifier = Modifier.height(12.dp))

                Text("حالت نمایش", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                NezamSegmentedControl(
                    options = listOf("روشن", "تاریک", "سیستمی"),
                    selectedIndex = prefs.themeMode,
                    onSelect = { index ->
                        prefs = prefs.copy(themeMode = index)
                        prefs.save(context)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("تم رنگی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "بدون نیاز به راه‌اندازی مجدد اعمال می‌شود.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.values().forEach { theme ->
                        ThemeOptionRow(
                            theme = theme,
                            selected = prefs.appTheme == theme,
                            onClick = {
                                prefs = prefs.copy(appTheme = theme)
                                prefs.save(context)
                            }
                        )
                    }
                }

                // Live preview in the currently selected theme
                Spacer(modifier = Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "پیش‌نمایش: ${prefs.appTheme.persianName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "این متن و رنگ‌ها با تم انتخاب‌شده نمایش داده می‌شوند.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // RTL
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("جهت صفحه")
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("راست به چپ (فارسی)", fontSize = 14.sp)
                    Switch(checked = prefs.isRtl, onCheckedChange = {
                        prefs = prefs.copy(isRtl = it)
                        prefs.save(context)
                    })
                }
                Text("تأیید: ${if (prefs.isRtl) "راست به چپ ✅" else "چپ به راست ✅"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Font Size
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("اندازه متن")
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("کوچک", fontSize = 12.sp); Text("عادی", fontSize = 14.sp); Text("بزرگ", fontSize = 16.sp)
                }
                Slider(
                    value = prefs.fontScale,
                    onValueChange = { prefs = prefs.copy(fontScale = it) },
                    onValueChangeFinished = { prefs.save(context) },
                    valueRange = 0.85f..1.3f,
                    steps = 2
                )
                Text("اندازه فعلی: ${when {
                    prefs.fontScale < 0.92f -> "کوچک"
                    prefs.fontScale < 1.1f -> "عادی"
                    else -> "بزرگ"
                }}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Preview
                Spacer(modifier = Modifier.height(8.dp))
                Text("پیش‌نمایش: ${"این متن با اندازه تغییر یافته نمایش داده می‌شود."}", fontSize = (14 * prefs.fontScale).sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Data Info
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("اطلاعات داده")
                Spacer(modifier = Modifier.height(8.dp))
                Text("پرونده\u200Cها: ${cases.size}", fontSize = 14.sp)
                Text("مهندسین: ${engineers.size}", fontSize = 14.sp)
                Text("حق\u200Cالزحمه: ${fees.size}", fontSize = 14.sp)
                Text("گزارش\u200Cها: ${reports.size}", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // App Info
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("درباره برنامه")
                Spacer(modifier = Modifier.height(8.dp))
                Text("نسخه: 3.4.0", fontSize = 14.sp)
                Text("پکیج: com.nezammonitor.app", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("ساخته شده توسط مهندس برخورداری", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
