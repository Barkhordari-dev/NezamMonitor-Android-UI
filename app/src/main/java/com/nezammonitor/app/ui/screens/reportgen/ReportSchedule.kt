package com.nezammonitor.app.ui.screens.reportgen

/** Jalali (Shamsi) date helpers for the report schedule.
 *  Due dates are computed from the building permit issue date (تاریخ صدور پروانه),
 *  which the API sends as a Jalali date string ("1403/05/12"). */

data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    fun format(): String = String.format("%04d/%02d/%02d", year, month, day)
    fun toPersianDigits(): String = format()
        .replace("0", "۰").replace("1", "۱").replace("2", "۲").replace("3", "۳")
        .replace("4", "۴").replace("5", "۵").replace("6", "۶").replace("7", "۷")
        .replace("8", "۸").replace("9", "۹")
}

/** Persian/Arabic digits to ASCII (for date sorting and search). Internal so
 *  the history tab can normalize query and row text the same way. */
internal fun normalizeDigits(s: String): String = s
    .replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3").replace("۴", "4")
    .replace("۵", "5").replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
    .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3").replace("٤", "4")
    .replace("٥", "5").replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")

/** Jalali leap years follow a 33-year cycle (years 1, 5, 9, 13, 17, 22, 26, 30). */
fun isJalaliLeap(year: Int): Boolean {
    val pos = ((year - 1) % 33) + 1
    return pos == 1 || pos == 5 || pos == 9 || pos == 13 || pos == 17 || pos == 22 || pos == 26 || pos == 30
}

fun jalaliDaysInMonth(year: Int, month: Int): Int = when (month) {
    in 1..6 -> 31
    in 7..11 -> 30
    12 -> if (isJalaliLeap(year)) 30 else 29
    else -> 30
}

/** Parses a stored permit/visit date in any format the API or an import may use:
 *  Jalali "1403/05/12" (any separator, Persian/Arabic digits, optional time suffix),
 *  Gregorian "2024-07-02" (converted to Jalali), or 8-digit "14030512".
 *  Null when unparseable. */
fun parseAnyDate(raw: String): JalaliDate? {
    val t = normalizeDigits(raw.trim())
    if (t.isBlank()) return null
    // Drop any trailing time part ("1403/05/12 00:00:00", "2024-07-02T00:00:00")
    val datePart = t.split(' ', 'T').firstOrNull()?.trim() ?: return null
    if (datePart.length == 8 && datePart.all { it.isDigit() }) {
        val y = datePart.substring(0, 4).toIntOrNull() ?: return null
        val m = datePart.substring(4, 6).toIntOrNull() ?: return null
        val d = datePart.substring(6, 8).toIntOrNull() ?: return null
        return jalaliOrNull(y, m, d)
    }
    val parts = datePart.split('/', '-', '.').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    return jalaliOrNull(y, m, d)
}

private fun jalaliOrNull(y: Int, m: Int, d: Int): JalaliDate? {
    return when {
        y in 1300..1500 && m in 1..12 && d in 1..31 -> JalaliDate(y, m, d.coerceAtMost(jalaliDaysInMonth(y, m)))
        y in 1900..2100 && m in 1..12 && d in 1..31 -> gregorianToJalali(y, m, d)
        else -> null
    }
}

/** Parses "1403/05/12", "1403-05-12" (ASCII or Persian digits) into a JalaliDate, or null. */
fun parseJalaliDate(raw: String): JalaliDate? {
    val t = normalizeDigits(raw.trim())
    if (t.isBlank()) return null
    val parts = t.split('/', '-', '.').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (y < 1300 || y > 1500 || m !in 1..12 || d !in 1..31) return null
    return JalaliDate(y, m, d.coerceAtMost(jalaliDaysInMonth(y, m)))
}

/** Adds whole Jalali months, clamping the day to the target month's length. */
fun JalaliDate.addMonths(months: Int): JalaliDate {
    val total = (year * 12 + (month - 1)) + months
    val y = total / 12
    val m = total % 12 + 1
    return JalaliDate(y, m, day.coerceAtMost(jalaliDaysInMonth(y, m)))
}

/** Full months + remaining days from [from] to [to] (to >= from). */
fun monthsDaysBetween(from: JalaliDate, to: JalaliDate): Pair<Int, Int> {
    var months = (to.year - from.year) * 12 + (to.month - from.month)
    val days = if (to.day >= from.day) {
        to.day - from.day
    } else {
        months -= 1
        jalaliDaysInMonth(from.year, from.month) - from.day + to.day
    }
    return months to days
}

/** Compares two Jalali dates: negative if this < other, 0 if equal, positive if greater. */
fun JalaliDate.compareTo(other: JalaliDate): Int {
    if (year != other.year) return year - other.year
    if (month != other.month) return month - other.month
    return day - other.day
}

/** Today's date as a JalaliDate (Gregorian -> Jalali). */
fun todayJalali(): JalaliDate {
    val cal = java.util.Calendar.getInstance()
    return gregorianToJalali(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

/** Gregorian date -> Jalali date (same algorithm as the app's toPersianDate). */
fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
    val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1]
    var jy = -1595 + (33 * (days / 12053))
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
    val jm: Int
    val jd: Int
    if (days < 186) { jm = 1 + (days / 31); jd = 1 + (days % 31) } else { jm = 7 + ((days - 186) / 30); jd = 1 + ((days - 186) % 30) }
    return JalaliDate(jy, jm, jd)
}

fun toPersianDigitsStr(input: String): String = normalizeDigits(input)
    .replace("0", "۰").replace("1", "۱").replace("2", "۲").replace("3", "۳")
    .replace("4", "۴").replace("5", "۵").replace("6", "۶").replace("7", "۷")
    .replace("8", "۸").replace("9", "۹")

/** Displays any stored date as Shamsi (Jalali) with Persian digits.
 *  Jalali input ("1403/05/12") is shown as-is; a Gregorian date ("2026-11-11")
 *  is converted. A trailing time part ("HH:mm") is preserved after «ساعت». */
fun displayShamsiDate(raw: String): String {
    val t = normalizeDigits(raw.trim())
    if (t.isBlank()) return ""
    val chunks = t.split(' ').filter { it.isNotBlank() }
    if (chunks.isEmpty()) return ""
    val datePart = chunks[0]
    val timePart = chunks.drop(1).joinToString(" ")
    val d = datePart.split('/', '-', '.').map { it.trim() }.filter { it.isNotEmpty() }
    if (d.size != 3) return toPersianDigitsStr(t)
    val y = d[0].toIntOrNull() ?: return toPersianDigitsStr(t)
    val m = d[1].toIntOrNull() ?: return toPersianDigitsStr(t)
    val day = d[2].toIntOrNull() ?: return toPersianDigitsStr(t)
    val jalali = when {
        y in 1300..1500 && m in 1..12 -> JalaliDate(y, m, day.coerceAtMost(jalaliDaysInMonth(y, m)))
        y in 1900..2100 && m in 1..12 && day in 1..31 -> gregorianToJalali(y, m, day)
        else -> return toPersianDigitsStr(t)
    }
    val out = jalali.toPersianDigits()
    return if (timePart.isNotBlank()) "$out ساعت ${toPersianDigitsStr(timePart)}" else out
}

private fun toPersianDigitsNum(n: Int): String = n.toString()
    .replace("0", "۰").replace("1", "۱").replace("2", "۲").replace("3", "۳")
    .replace("4", "۴").replace("5", "۵").replace("6", "۶").replace("7", "۷")
    .replace("8", "۸").replace("9", "۹")

/** Human text for the time between today and a due date:
 *  "۲ ماه و ۵ روز مانده" / "۳ ماه گذشته" / "امروز". */
fun remainingText(due: JalaliDate, today: JalaliDate): String {
    val cmp = due.compareTo(today)
    if (cmp == 0) return "امروز"
    return if (cmp > 0) {
        val (m, d) = monthsDaysBetween(today, due)
        formatMonthsDays(m, d) + " مانده"
    } else {
        val (m, d) = monthsDaysBetween(due, today)
        formatMonthsDays(m, d) + " گذشته"
    }
}

private fun formatMonthsDays(months: Int, days: Int): String = when {
    months > 0 && days > 0 -> "${toPersianDigitsNum(months)} ماه و ${toPersianDigitsNum(days)} روز"
    months > 0 -> "${toPersianDigitsNum(months)} ماه"
    days > 0 -> "${toPersianDigitsNum(days)} روز"
    else -> "امروز"
}

// ==================== STAGE RULES ====================

/** Months after the permit issue date when each stage is due, per building group. */
fun stageMonthsForGroup(groupLetter: String): List<Int> = when (groupLetter) {
    "الف" -> listOf(2, 6, 10)
    "ب" -> listOf(3, 6, 12, 20)
    "ج" -> listOf(3, 8, 16, 24)
    "د" -> listOf(3, 8, 16, 30)
    else -> listOf(2, 6, 10)
}

/** Normalized building-group letter ("الف"/"ب"/"ج"/"د") from a raw bg_name value.
 *  Tolerant: the letter is extracted from any token of the string, so
 *  "ب", "گروه ب", "گروه‌ب", "B", "2", "گروه 2", "ب (گروه دوم)" all work.
 *  "" when no group letter can be found. */
fun buildingGroupLetter(raw: String): String = normalizeGroup(raw)

private fun normalizeGroup(raw: String): String {
    val s = raw.trim()
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace("\u200C", " ").replace("\u00A0", " ")
    val norm = normalizeDigits(s)
    val tokens = norm.split(' ', '-', '(', ')', '،', ',', ':', ';', '/')
        .map { it.trim() }.filter { it.isNotEmpty() }
    for (t in tokens) {
        when (t.removePrefix("گروه")) {
            "الف", "A", "a", "1" -> return "الف"
            "ب", "B", "b", "2" -> return "ب"
            "ج", "C", "c", "3" -> return "ج"
            "د", "D", "d", "4" -> return "د"
        }
    }
    // Glued form with no separator, e.g. "گروهب"
    val glued = norm.replace(" ", "")
    return when (glued.removePrefix("گروه")) {
        "الف", "A", "a", "1" -> "الف"
        "ب", "B", "b", "2" -> "ب"
        "ج", "C", "c", "3" -> "ج"
        "د", "D", "d", "4" -> "د"
        else -> ""
    }
}

private fun normalizeDiscipline(raw: String): String {
    return raw.trim()
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace("\u200C", "").replace(" ", "")
}

/** How many report stages a case needs, from its building group + discipline.
 *  Groups ب/ج/د: 4 stages for عمران، معماری، برق، مکانیک and نقشه‌برداری.
 *  شهرسازی: 4 stages only for groups ج/د. Group الف: 3 stages. Everything else: 3. */
fun stageCountForCase(buildingGroup: String, discipline: String): Int {
    val group = normalizeGroup(buildingGroup)
    if (group.isEmpty()) return 3
    val disc = normalizeDiscipline(discipline)
    val fourStageDisciplines = setOf("عمران", "معماری", "برق", "مکانیک", "نقشهبرداری")
    return when {
        group == "الف" -> 3
        disc == "شهرسازی" -> if (group == "ج" || group == "د") 4 else 3
        disc in fourStageDisciplines -> 4
        else -> 3
    }
}

/** One stage's schedule entry: due date + remaining/elapsed text, or null if no permit date. */
data class StageSchedule(
    val stage: Int,
    val dueDate: JalaliDate,
    val isRegistered: Boolean
)

/** Due dates for a case's stages, from its permit issue date + the group's month table.
 *  Empty only when the permit date is missing/unparseable; an unknown group still
 *  gets a schedule with the default (الف) month table. */
fun scheduleForCase(permitDateRaw: String, buildingGroupRaw: String, stageCount: Int, registeredStages: Set<Int>, today: JalaliDate = todayJalali()): List<StageSchedule> {
    val permit = parseAnyDate(permitDateRaw) ?: return emptyList()
    val group = normalizeGroup(buildingGroupRaw)
    val months = stageMonthsForGroup(group).take(stageCount)
    return months.mapIndexed { i, m ->
        StageSchedule(stage = i + 1, dueDate = permit.addMonths(m), isRegistered = (i + 1) in registeredStages)
    }
}
