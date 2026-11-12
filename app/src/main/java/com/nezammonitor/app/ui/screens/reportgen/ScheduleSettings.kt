package com.nezammonitor.app.ui.screens.reportgen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.api.toPersianChars

/** User-editable report schedule, stored in SharedPreferences ("nezam_schedule").
 *  Defaults are the initial rules the user stated: groups ب/ج/د need 4 stages
 *  for عمران/معماری/برق/مکانیک and نقشه‌برداری, شهرسازی only for ج/د, group الف
 *  needs 3 stages; months الف [2,6,10], ب [3,6,12,20], ج [3,8,16,24],
 *  د [3,8,16,30] — all counted from the permit issue date (تاریخ صدور).
 *  The pure functions in ReportSchedule.kt ARE the defaults; this class only
 *  layers the user's overrides on top, so a reset restores exactly them. */
class ScheduleConfig(context: Context) {
    private val prefs = context.getSharedPreferences("nezam_schedule", Context.MODE_PRIVATE)

    companion object {
        val GROUPS = listOf("الف", "ب", "ج", "د")
        val DISCIPLINES = listOf("عمران", "معماری", "برق", "مکانیک", "نقشه‌برداری", "شهرسازی")
    }

    private fun disciplineKey(discipline: String): String =
        toPersianChars(discipline.trim()).replace("\u200C", "").replace(" ", "")

    /** Stage count for a (group, discipline) pair: the user's override when
     *  one exists, otherwise the initial rule. */
    fun stageCount(groupRaw: String, discipline: String): Int {
        val g = buildingGroupLetter(groupRaw)
        if (g.isEmpty()) return 3
        val key = "stages_${g}_${disciplineKey(discipline)}"
        if (prefs.contains(key)) return prefs.getInt(key, 3).coerceIn(3, 4)
        return stageCountForCase(groupRaw, discipline)
    }

    /** Month table for a group: the user's override when one exists,
     *  otherwise the initial table. */
    fun months(groupRaw: String): List<Int> {
        val g = buildingGroupLetter(groupRaw)
        val raw = prefs.getString("months_$g", null) ?: return stageMonthsForGroup(g)
        val list = raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..60 }
        return if (list.isEmpty()) stageMonthsForGroup(g) else list
    }

    fun saveStageCount(group: String, discipline: String, count: Int) {
        prefs.edit().putInt("stages_${group}_${disciplineKey(discipline)}", count.coerceIn(3, 4)).apply()
    }

    fun saveMonths(group: String, months: List<Int>) {
        val clean = months.filter { it in 1..60 }
        if (clean.isEmpty()) prefs.edit().remove("months_$group").apply()
        else prefs.edit().putString("months_$group", clean.joinToString(",")).apply()
    }

    /** Back to the initial settings: drop every override. */
    fun resetAll() {
        prefs.edit().clear().apply()
    }
}

/** The "تغییر زمان‌بندی" page: pick a group + discipline, change how many
 *  stages that pair needs and the group's month table. Everything is saved
 *  immediately; "بازگشت به تنظیمات اولیه" drops all overrides. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { ScheduleConfig(context) }
    var selectedGroup by remember { mutableStateOf("ب") }
    var selectedDiscipline by remember { mutableStateOf("عمران") }
    // Bump to re-read the config after a reset.
    var version by remember { mutableIntStateOf(0) }

    val stageCount = config.stageCount(selectedGroup, selectedDiscipline)
    val months = config.months(selectedGroup)
    // Month editor: four text fields (one per stage), prefilled from the
    // group's current table; a trailing empty field means "no month".
    var monthTexts by remember(selectedGroup, version) {
        mutableStateOf((0..3).map { i -> months.getOrNull(i)?.toString() ?: "" })
    }
    var savedNote by remember { mutableStateOf(false) }
    fun markSaved() { savedNote = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تغییر زمان‌بندی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "زمان‌بندی گزارش‌ها بر اساس گروه ساختمانی و رشته تعیین می‌شود. ماه‌ها از تاریخ صدور پروانه حساب می‌شوند.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Group picker
            Text("گروه ساختمانی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleConfig.GROUPS.forEach { g ->
                    FilterChip(
                        selected = selectedGroup == g,
                        onClick = { selectedGroup = g },
                        label = { Text("گروه $g", fontSize = 13.sp) }
                    )
                }
            }

            // Discipline picker
            Text("رشته", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleConfig.DISCIPLINES.forEach { d ->
                    FilterChip(
                        selected = selectedDiscipline == d,
                        onClick = { selectedDiscipline = d },
                        label = { Text(d, fontSize = 13.sp) }
                    )
                }
            }

            // Stage count for the selected pair
            Text("تعداد مراحل گزارش برای گروه $selectedGroup — $selectedDiscipline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = stageCount == 3,
                    onClick = { config.saveStageCount(selectedGroup, selectedDiscipline, 3); version++; markSaved() },
                    label = { Text("۳ مرحله", fontSize = 13.sp) }
                )
                FilterChip(
                    selected = stageCount == 4,
                    onClick = { config.saveStageCount(selectedGroup, selectedDiscipline, 4); version++; markSaved() },
                    label = { Text("۴ مرحله", fontSize = 13.sp) }
                )
            }
            if (stageCount == 4 && months.size < 4) {
                Text(
                    "⚠️ این ترکیب ۴ مرحله نیاز دارد ولی برای گروه $selectedGroup فقط ${toPersianDigitsStr(months.size.toString())} ماه ثبت شده است؛ ماه مرحلهٔ چهارم را پایین وارد کن.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Month table for the group
            Text("ماه‌های سررسید مراحل (از تاریخ صدور پروانه) — گروه $selectedGroup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..3).forEach { i ->
                    OutlinedTextField(
                        value = monthTexts[i],
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(2)
                            val newTexts = monthTexts.toMutableList().also { it[i] = digits }
                            monthTexts = newTexts
                            config.saveMonths(selectedGroup, newTexts.mapNotNull { it.toIntOrNull() })
                            markSaved()
                        },
                        label = { Text("مرحله ${toPersianDigitsStr((i + 1).toString())}", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                "فعلی: ${months.joinToString("، ") { "ماه ${toPersianDigitsStr(it.toString())}" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (savedNote) {
                Text("✓ ذخیره شد", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    config.resetAll()
                    version++
                    monthTexts = (0..3).map { i -> config.months(selectedGroup).getOrNull(i)?.toString() ?: "" }
                    markSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("↩️ بازگشت به تنظیمات اولیه", fontSize = 14.sp)
            }
            Text(
                "تنظیم اولیه: گروه‌های «ب»، «ج» و «د» در رشته‌های عمران، معماری، برق، مکانیک و نقشه‌برداری چهار مرحله دارند؛ شهرسازی فقط در «ج» و «د» چهار مرحله دارد و گروه «الف» سه مرحله دارد. ماه‌ها: الف [۲، ۶، ۱۰]، ب [۳، ۶، ۱۲، ۲۰]، ج [۳، ۸، ۱۶، ۲۴]، د [۳، ۸، ۱۶، ۳۰].",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
