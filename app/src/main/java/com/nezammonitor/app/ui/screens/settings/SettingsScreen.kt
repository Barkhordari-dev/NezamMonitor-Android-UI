package com.nezammonitor.app.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.BuildConfig
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamSegmentedControl
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.ui.components.ThemeOptionRow
import com.nezammonitor.app.ui.toPersianDigits
import com.nezammonitor.app.ui.theme.AppTheme
import com.nezammonitor.app.ui.theme.CardStyle
import com.nezammonitor.app.ui.theme.CornerStyle
import com.nezammonitor.app.ui.theme.VisualEffects
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

data class AppPreferences(
    val themeMode: Int = 0, // 0=Light, 1=Dark, 2=System
    val appTheme: AppTheme = AppTheme.LIGHT_PREMIUM,
    val isRtl: Boolean = true,
    val fontScale: Float = 1.0f,
    val cardStyle: CardStyle = CardStyle.GLASS,
    val cornerStyle: CornerStyle = CornerStyle.MODERN,
    val visualEffects: VisualEffects = VisualEffects.DYNAMIC
) {
    companion object {
        fun load(context: Context): AppPreferences {
            val prefs = context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
            return AppPreferences(
                themeMode = prefs.getInt("theme", 0),
                appTheme = AppTheme.fromName(prefs.getString("app_theme", null)),
                isRtl = prefs.getBoolean("rtl", true),
                fontScale = prefs.getFloat("font_scale", 1.0f),
                cardStyle = CardStyle.fromName(prefs.getString("card_style", null)),
                cornerStyle = CornerStyle.fromName(prefs.getString("corner_style", null)),
                visualEffects = VisualEffects.fromName(prefs.getString("visual_effects", null))
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE).edit()
            .putInt("theme", themeMode)
            .putString("app_theme", appTheme.name)
            .putBoolean("rtl", isRtl)
            .putFloat("font_scale", fontScale)
            .putString("card_style", cardStyle.name)
            .putString("corner_style", cornerStyle.name)
            .putString("visual_effects", visualEffects.name)
            .apply()
    }
}

@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateToHiddenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf(AppPreferences.load(context)) }

    val cases by viewModel.cases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()

    // Hidden menu unlock: 10 taps in a row on the About card (no visible
    // hint anywhere). The counter resets if more than 3s pass between taps.
    var aboutTapCount by remember { mutableStateOf(0) }
    var lastAboutTapAt by remember { mutableStateOf(0L) }
    var showHiddenPasswordDialog by remember { mutableStateOf(false) }
    var hiddenPassword by remember { mutableStateOf("") }
    var hiddenPasswordError by remember { mutableStateOf("") }
    var hiddenPasswordChecking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("تنظیمات و سفارشی‌سازی", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "شخصی‌سازی پیشرفته ظاهر و عملکرد برنامه",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 1. Theme Mode & Palettes
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("پوسته و تم رنگی (Themes)")
                Spacer(modifier = Modifier.height(12.dp))

                Text("حالت نمایش روشنایی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                NezamSegmentedControl(
                    options = listOf("روشن", "تاریک", "سیستمی"),
                    selectedIndex = prefs.themeMode,
                    onSelect = { index ->
                        prefs = prefs.copy(themeMode = index)
                        prefs.save(context)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تم‌های رنگی (۹ تم حرفه‌ای)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "${AppTheme.values().size} تم فعال",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "تغییر آنی بدون بستن برنامه و با ماندگاری کامل در حافظه.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
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
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Advanced Visual Customization (Card Style, Corner Radius, Motion)
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("سفارشی‌سازی رابط کاربری (UI Customization)")
                Spacer(modifier = Modifier.height(12.dp))

                // Card Style
                Text("سبک طراحی کارت‌ها", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                NezamSegmentedControl(
                    options = CardStyle.values().map { it.persianName },
                    selectedIndex = CardStyle.values().indexOf(prefs.cardStyle),
                    onSelect = { index ->
                        val selected = CardStyle.values()[index]
                        prefs = prefs.copy(cardStyle = selected)
                        prefs.save(context)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    prefs.cardStyle.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Corner Radius
                Text("شعاع انحنای گوشه‌ها", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                NezamSegmentedControl(
                    options = CornerStyle.values().map { it.persianName },
                    selectedIndex = CornerStyle.values().indexOf(prefs.cornerStyle),
                    onSelect = { index ->
                        val selected = CornerStyle.values()[index]
                        prefs = prefs.copy(cornerStyle = selected)
                        prefs.save(context)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Visual Effects / Motion
                Text("جلوه‌های بصری و انیمیشن", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                NezamSegmentedControl(
                    options = VisualEffects.values().map { it.persianName },
                    selectedIndex = VisualEffects.values().indexOf(prefs.visualEffects),
                    onSelect = { index ->
                        val selected = VisualEffects.values()[index]
                        prefs = prefs.copy(visualEffects = selected)
                        prefs.save(context)
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    prefs.visualEffects.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Card
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "پیش‌نمایش زنده: ${prefs.appTheme.persianName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${prefs.cardStyle.persianName} | ${prefs.cornerStyle.persianName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "کارت‌ها، دکمه‌ها و عناصر برنامه بر اساس تنظیمات بالا بی‌درنگ به‌روزرسانی می‌شوند.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Direction (RTL)
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("جهت صفحه و چینش")
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("راست به چپ (فارسی استاندارد)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("چینش متون و کنترل‌ها متناسب با زبان فارسی", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = prefs.isRtl, onCheckedChange = {
                        prefs = prefs.copy(isRtl = it)
                        prefs.save(context)
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Font Size
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("اندازه قلم (Typography)")
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("کوچک (۸۵٪)", fontSize = 12.sp)
                    Text("عادی (۱۰۰٪)", fontSize = 14.sp)
                    Text("بزرگ (۱۳۰٪)", fontSize = 16.sp)
                }
                Slider(
                    value = prefs.fontScale,
                    onValueChange = { prefs = prefs.copy(fontScale = it) },
                    onValueChangeFinished = { prefs.save(context) },
                    valueRange = 0.85f..1.3f,
                    steps = 2
                )
                Text(
                    "پیش‌نمایش اندازه: سامانه نظارت و مانیتورینگ مهندسی یزد",
                    fontSize = (13 * prefs.fontScale).sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Data Statistics
        NezamCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("اطلاعات پایگاه داده")
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatMetricItem("📋 پرونده‌ها", cases.size.toString())
                    StatMetricItem("👷 مهندسین", engineers.size.toString())
                    StatMetricItem("💰 حق‌الزحمه", fees.size.toString())
                    StatMetricItem("📄 گزارش‌ها", reports.size.toString())
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 6. App Info — 10 taps in a row here open the hidden menu's
        // password dialog (no visible hint that it exists).
        NezamCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val now = System.currentTimeMillis()
                aboutTapCount = if (now - lastAboutTapAt > 3000L) 1 else aboutTapCount + 1
                lastAboutTapAt = now
                if (aboutTapCount >= 10) {
                    aboutTapCount = 0
                    showHiddenPasswordDialog = true
                }
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionTitle("درباره برنامه")
                Spacer(modifier = Modifier.height(8.dp))
                Text("NezamMonitor Pro — نسخه ${toPersianDigits(BuildConfig.VERSION_NAME)} (UI Redesign)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("شناسه بسته: com.nezammonitor.app", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("تولید و توسعه: مهندس محمد مهدی برخورداری", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("طراحی شده برای مهندسین نظام مهندسی", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))
                Text("دریافت نسخه‌های جدید", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                GitHubLinkRow(
                    label = "نسخه اندروید در گیت‌هاب",
                    url = "https://github.com/Barkhordari-dev/NezamMonitor-Android-UI",
                    onClick = { openWebLink(context, "https://github.com/Barkhordari-dev/NezamMonitor-Android-UI") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                GitHubLinkRow(
                    label = "نسخه ویندوز در گیت‌هاب",
                    url = "https://github.com/Barkhordari-dev/NezamMonitor",
                    onClick = { openWebLink(context, "https://github.com/Barkhordari-dev/NezamMonitor") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "برای دریافت بروزرسانی‌های بعدی برنامه، به پیوندهای گیت‌هاب بالا مراجعه کنید.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "این برنامه با هدف بهره‌مندی عمومی و به‌صورت عام‌المنفعه تهیه شده است؛ فروش آن و هرگونه استفادهٔ نامتعارف، مورد رضایت توسعه‌دهنده نیست.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Hidden menu password dialog (opened by 10 taps on the About card).
    if (showHiddenPasswordDialog) {
        HiddenPasswordDialog(
            password = hiddenPassword,
            onPasswordChange = { hiddenPassword = it },
            error = hiddenPasswordError,
            checking = hiddenPasswordChecking,
            onConfirm = {
                hiddenPasswordChecking = true
                hiddenPasswordError = ""
                scope.launch {
                    val result = checkHiddenMenuPassword(context, hiddenPassword)
                    hiddenPasswordChecking = false
                    when (result) {
                        HiddenPasswordResult.OK -> {
                            HiddenMenuSession.unlocked = true
                            showHiddenPasswordDialog = false
                            hiddenPassword = ""
                            onNavigateToHiddenSettings()
                        }
                        HiddenPasswordResult.WRONG -> hiddenPasswordError = "رمز اشتباه است"
                        HiddenPasswordResult.NO_NETWORK -> hiddenPasswordError = "دسترسی به اینترنت نیست"
                    }
                }
            },
            onDismiss = {
                if (!hiddenPasswordChecking) {
                    showHiddenPasswordDialog = false
                    hiddenPassword = ""
                    hiddenPasswordError = ""
                }
            }
        )
    }
}

/** Password dialog for the hidden menu: the password is the name of the
 *  user's Google Drive folder, checked live (or against the last cached
 *  name when offline). */
@Composable
private fun HiddenPasswordDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String,
    checking: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("منوی مخفی", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("رمز عبور") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checking
                )
                if (error.isNotBlank()) {
                    Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال بررسی...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !checking) { Text("تأیید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checking) { Text("انصراف") }
        }
    )
}

@Composable
private fun StatMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One GitHub link row in the About section: label + the full URL under it. */
@Composable
private fun GitHubLinkRow(label: String, url: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Text(
            url,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    }
}

/** Opens a URL in the browser; silently does nothing if no browser is available. */
private fun openWebLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // No browser available — the URL is shown as text, so it can be copied.
    }
}
