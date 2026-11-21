package com.nezammonitor.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Parser for the Yazd design-offices directory
 * (service.yazdnezam.ir:8033/resume/daftar). The site has no JSON API:
 * every page is server-rendered HTML and the data is parsed out of it.
 *
 * Proven parse pitfalls (all covered here and in the unit tests):
 * - Names, licences and dates use LATIN digits in the page.
 * - Labels use ARABIC letters (ي / ك): "فعاليت", "صادركننده", "پايه".
 *   Label keys are normalized to Persian before matching.
 * - The licence-history table headers use the LATIN digit "پايه 1"
 *   (not Persian "۱") — detected by "starts with پایه" after normalizing.
 * - The members table is MALFORMED: its rows miss their </tr> tags, so
 *   each <tr> nests the following rows (27, 24, 21… cells). Only the
 *   FIRST two cells and the FIRST "more" link of each row belong to that
 *   row — reading all cells of a row duplicates members.
 * - A missing office/engineer id returns HTTP 500, not 404 (handled by
 *   the repository: non-200 = null).
 * - The photo placeholder "images.png" means "no photo".
 * - The work-history modal in the engineer page is empty — no data.
 */
object DirectoryParser {

    const val BASE_URL = "http://service.yazdnezam.ir:8033"
    const val PLACEHOLDER_PHOTO = "images.png"

    // ---- Normalization (same rules as the app's Cases search) ----

    /** Arabic-script letters to Persian. */
    fun toPersianChars(input: String): String =
        input.replace('ي', 'ی').replace('ك', 'ک')

    /** Persian/Arabic-Indic digits to Latin digits. */
    fun normalizeDigits(input: String): String {
        val out = StringBuilder(input.length)
        for (c in input) {
            out.append(
                when (c) {
                    in '۰'..'۹' -> ('0' + (c - '۰'))
                    in '٠'..'٩' -> ('0' + (c - '٠'))
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /**
     * Comparison/search key: Persian script, Latin digits, no ZWNJ/ZWJ/
     * tatweel, Arabic letter variants folded. Use on BOTH sides of every
     * name comparison (office names, engineer names, search queries).
     */
    fun normalizeForCompare(input: String): String =
        toPersianChars(normalizeDigits(input))
            .replace('ة', 'ه').replace('ؤ', 'و').replace('أ', 'ا')
            .replace('إ', 'ا').replace('آ', 'ا')
            .replace("ـ", "").replace("\u200C", "").replace("\u200D", "")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Display text: Persian script, collapsed whitespace. */
    fun cleanDisplay(input: String): String =
        toPersianChars(input).replace(Regex("\\s+"), " ").trim()

    /** Licence / date / phone text: Persian script, Latin digits. */
    fun cleanCode(input: String): String =
        normalizeDigits(toPersianChars(input)).replace(Regex("\\s+"), " ").trim()

    // ---- Parsed results (plain data, no Room involved) ----

    data class OfficeListRow(
        val siteId: Int,
        val name: String,
        val licence: String,
        val manager: String,
        val city: String,
        val province: String
    )

    data class OfficeMember(
        val engineerId: Int?,
        val name: String,
        val discipline: String
    )

    data class ParsedOffice(
        val siteId: Int,
        val name: String,
        val licence: String,
        val manager: String,
        val mobile: String,
        val contact: String,
        val province: String,
        val city: String,
        val address: String,
        val phone: String,
        val fax: String,
        val hours: String,
        val members: List<OfficeMember>
    )

    data class ParsedEducation(
        val degree: String,
        val field: String,
        val orientation: String,
        val universityType: String,
        val university: String,
        val city: String,
        val country: String,
        val start: String,
        val end: String
    )

    data class ParsedHistory(
        val kind: String,
        val grade1: String,
        val grade2: String,
        val grade3: String
    )

    data class ParsedEngineer(
        val siteId: Int,
        val name: String,
        val family: String,
        val discipline: String,
        val phone: String,
        val designGrade: String,
        val supervisionGrade: String,
        val executionGrade: String,
        val licenceIssue: String,
        val licenceValidity: String,
        val photo: String,
        val education: List<ParsedEducation>,
        val history: List<ParsedHistory>
    )

    /** One row of the engineer search results
     *  (/resume/engineers/search — same table shape as the engineers
     *  list, with an "عضویت در دفتر" column that links the office). */
    data class EngineerSearchRow(
        val siteId: Int,
        val name: String,
        val family: String,
        val discipline: String,
        val designGrade: String,
        val supervisionGrade: String,
        val executionGrade: String,
        /** "" when the engineer has no office (the cell shows "_"). */
        val officeName: String,
        /** The office's site id from its link; null when there is none. */
        val officeId: Int?
    )

    // ---- Office-name queries and exact-only selection (v3.11.0) ----

    /**
     * Canonical office query from a case's messy office text. The case
     * API's daf_name is free text with the number embedded — "شماره 194
     * يزد", "دفتر شماره 89", "شماره205يزد", "دفتر221 يزد", "دفتر شماره78",
     * "شماره258", "دفتر طراحي267" — so the first number in the text means
     * office N. Text without a number (a company name) is used as-is.
     * The marker check ("دفتر"/"شماره") keeps a company name that happens
     * to contain digits from being rewritten into "دفتر N".
     */
    fun officeQueryName(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return ""
        // A bare number ("194", "۱۹۴") means office N.
        if (Regex("^[0-9۰-۹٠-٩]+$").matches(text)) return "دفتر ${normalizeDigits(text)}"
        val key = normalizeForCompare(text)
        if (!key.contains("دفتر") && !key.contains("شماره")) return text
        val digits = Regex("[0-9۰-۹٠-٩]+").find(text)?.value ?: return text
        return "دفتر ${normalizeDigits(digits)}"
    }

    /**
     * Exact-only office selection: the row whose normalized name equals
     * the normalized query (a bare number in the query means "دفتر N").
     * There is deliberately NO contains/substring fallback — a near
     * office must never be substituted (78 → 178, 87 → 187, 23 → 233,
     * or a bare "78" matching "دفتر 178"). Returns null when the site
     * has no such office — including the "ghost" offices that exist only
     * as a name on some engineers (104, 116, 23, 29, 37, 44, 64, 87).
     */
    fun selectExactOfficeRow(rows: List<OfficeListRow>, query: String): OfficeListRow? {
        val key = normalizeForCompare(officeQueryName(query))
        if (key.isBlank()) return null
        return rows.firstOrNull { normalizeForCompare(it.name) == key }
    }

    /** Split "first family..." into (first name, family). A single token
     *  has no family part ("", token). */
    fun splitEngineerName(fullName: String): Pair<String, String> {
        val trimmed = fullName.trim()
        val space = trimmed.indexOf(' ')
        return if (space < 0) "" to trimmed
        else trimmed.substring(0, space).trim() to trimmed.substring(space + 1).trim()
    }

    /**
     * Exact-only engineer selection from search rows: the row whose
     * normalized "name family" equals the query (a single-token query
     * also matches a row's name or family alone). With several exact
     * matches, prefer the row whose discipline is among [disciplines],
     * then the row whose office matches [officeName]; otherwise the
     * first. Returns null when no row matches exactly.
     */
    fun selectExactEngineerRow(
        rows: List<EngineerSearchRow>,
        fullName: String,
        disciplines: Set<String> = emptySet(),
        officeName: String? = null
    ): EngineerSearchRow? {
        val key = normalizeForCompare(fullName)
        if (key.isBlank()) return null
        val exact = rows.filter {
            normalizeForCompare("${it.name} ${it.family}") == key ||
                normalizeForCompare(it.family) == key ||
                normalizeForCompare(it.name) == key
        }
        if (exact.isEmpty()) return null
        if (exact.size == 1) return exact[0]
        val discKeys = disciplines.map { normalizeForCompare(it) }.filter { it.isNotBlank() }.toSet()
        if (discKeys.isNotEmpty()) {
            exact.firstOrNull { normalizeForCompare(it.discipline) in discKeys }?.let { return it }
        }
        val officeKey = officeName?.let { normalizeForCompare(officeQueryName(it)) }?.takeIf { it.isNotBlank() }
        if (officeKey != null) {
            exact.firstOrNull {
                it.officeName.isNotBlank() && normalizeForCompare(it.officeName) == officeKey
            }?.let { return it }
        }
        return exact[0]
    }

    // ---- Local-directory lookups (v3.11.4) ----
    // The detail screens read the seeded Room tables, but the clicked
    // value is NOT the stored value: an office click sends the raw
    // `daf_name` from the case API ("شماره 194 يزد", "دفتر شماره 89",
    // "دفتر طراحي267", or a bare number "۱۹۴"), and an engineer click
    // sends the case API's full name, which may carry a "مهندس" title.
    // In 3.11.3 the lookup compared the raw value with the stored name
    // and never matched — every office answered «یافت نشد». Both
    // resolvers normalize BOTH sides (Persian script, Latin digits) and
    // never substitute a near match: a bare "78" must never resolve to
    // "دفتر 178", and 89/104 stay «یافت نشد».

    /** The first digit run of a value, as Latin digits, or null. */
    fun extractNumber(raw: String): String? =
        Regex("[0-9۰-۹٠-٩]+").find(raw)?.value?.let { normalizeDigits(it) }

    /** Strip a leading "مهندس" title ("مهندس X", "مهندس‌X") from a name.
     *  Only when a space or ZWNJ follows the title — a name that merely
     *  starts with those letters ("مهندسان مشاور…", a company row in
     *  the snapshot) is NOT stripped. */
    fun stripEngineerTitle(raw: String): String {
        val trimmed = raw.trim()
        val stripped = when {
            trimmed.startsWith("مهندس ") -> trimmed.removePrefix("مهندس ").trim()
            trimmed.startsWith("مهندس‌") -> trimmed.removePrefix("مهندس‌").trim()
            else -> trimmed
        }
        return normalizeForCompare(stripped)
    }

    /** Resolve a clicked office value to its stored row, or null.
     *  1. Exact name match ("دفتر 194"). 2. The query-name form
     *  ("شماره 194 يزد" / bare "۱۹۴" -> "دفتر 194"). 3. Numeric match
     *  — only for a bare number or a value carrying a "دفتر"/"شماره"
     *  marker, so a company name that happens to contain digits never
     *  resolves to an office. */
    fun resolveOffice(raw: String, offices: List<DirectoryOfficeEntity>): DirectoryOfficeEntity? {
        if (raw.isBlank()) return null
        val key = normalizeForCompare(raw)
        offices.firstOrNull { normalizeForCompare(it.name) == key }?.let { return it }
        val queryKey = normalizeForCompare(officeQueryName(raw))
        if (queryKey.isNotBlank() && queryKey != key) {
            offices.firstOrNull { normalizeForCompare(it.name) == queryKey }?.let { return it }
        }
        val isBareNumber = Regex("^[0-9]+$").matches(key)
        val hasMarker = key.contains("دفتر") || key.contains("شماره")
        if (isBareNumber || hasMarker) {
            val number = extractNumber(raw) ?: return null
            offices.firstOrNull { extractNumber(it.name) == number }?.let { return it }
        }
        return null
    }

    /** Resolve a clicked engineer name to its stored row, or null.
     *  Tries the value as-is, then without a leading "مهندس" title;
     *  each form matches the full name ("name family"), then the first
     *  name alone, then the family alone. */
    fun resolveEngineer(raw: String, engineers: List<DirectoryEngineerEntity>): DirectoryEngineerEntity? {
        if (raw.isBlank()) return null
        val keys = listOf(normalizeForCompare(raw), stripEngineerTitle(raw))
            .filter { it.isNotBlank() }.distinct()
        for (key in keys) {
            engineers.firstOrNull { normalizeForCompare("${it.name} ${it.family}") == key }?.let { return it }
            engineers.firstOrNull { normalizeForCompare(it.name) == key }?.let { return it }
            engineers.firstOrNull { normalizeForCompare(it.family) == key }?.let { return it }
        }
        return null
    }

    // ---- Helpers ----

    private fun labelsOf(doc: Document): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (el in doc.select("p.font-weight-bold")) {
            val key = normalizeForCompare(el.text().replace(":", "").replace("：", ""))
            val sib = el.nextElementSibling()
            if (key.isNotEmpty() && sib != null) {
                out[key] = sib.text()
            }
        }
        return out
    }

    private fun officeIdFrom(href: String?): Int? {
        if (href.isNullOrBlank()) return null
        return Regex("/resume/daftar/(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun engineerIdFrom(href: String?): Int? {
        if (href.isNullOrBlank()) return null
        return Regex("/resume/engineers/(\\d+)").find(href)?.groupValues?.get(1)?.toIntOrNull()
    }

    // ---- Office list page (also the search-results page: same table) ----

    fun parseOfficeListPage(html: String): List<OfficeListRow> {
        val doc = Jsoup.parse(html)
        val rows = mutableListOf<OfficeListRow>()
        for (tr in doc.select("table tbody tr")) {
            val tds = tr.select("td")
            if (tds.size < 5) continue
            val id = officeIdFrom(tr.selectFirst("a.more")?.attr("href")) ?: continue
            rows.add(
                OfficeListRow(
                    siteId = id,
                    name = cleanDisplay(tds[0].text()),
                    licence = cleanCode(tds[1].text()),
                    manager = cleanDisplay(tds[2].text()),
                    city = cleanDisplay(tds[3].text()),
                    province = cleanDisplay(tds[4].text())
                )
            )
        }
        return rows
    }

    // ---- Engineer search page (/resume/engineers/search) ----
    // The result table: ردیف | نام | نام خانوادگی | رشته | پایه طراحی |
    // پایه نظارت | پایه اجرا | عضویت در دفتر | اطلاعات بیشتر. The row
    // number is a <th>, then 8 <td>s. The office cell either links the
    // office (/resume/daftar/{id}, text "دفتر N") or shows "_" (the
    // engineer has no office — 3,905 of the 5,078 engineers).

    fun parseEngineerSearchPage(html: String): List<EngineerSearchRow> {
        val doc = Jsoup.parse(html)
        val rows = mutableListOf<EngineerSearchRow>()
        for (tr in doc.select("table tbody tr")) {
            val tds = tr.select("td")
            if (tds.size < 8) continue
            val engineerLink = tds[7].selectFirst("a")
                ?: tr.select("a").firstOrNull { it.attr("href").contains("/resume/engineers/") }
            val id = engineerIdFrom(engineerLink?.attr("href")) ?: continue
            val officeLink = tds[6].selectFirst("a")
            val officeText = cleanDisplay(tds[6].text())
            rows.add(
                EngineerSearchRow(
                    siteId = id,
                    name = cleanDisplay(tds[0].text()),
                    family = cleanDisplay(tds[1].text()),
                    discipline = cleanDisplay(tds[2].text()),
                    designGrade = cleanCode(tds[3].text()),
                    supervisionGrade = cleanCode(tds[4].text()),
                    executionGrade = cleanCode(tds[5].text()),
                    officeName = if (officeText == "_" || officeText.isBlank()) "" else officeText,
                    officeId = officeIdFrom(officeLink?.attr("href"))
                )
            )
        }
        return rows
    }

    // ---- Office detail page ----

    fun parseOfficeDetail(html: String, siteId: Int): ParsedOffice? {
        val doc = Jsoup.parse(html)
        val v = labelsOf(doc)
        // Lookup keys must be normalized the same way as the page labels
        // (normalizeForCompare folds "آ" to "ا", so "آدرس" is stored as "ادرس").
        fun l(label: String): String = v[normalizeForCompare(label)] ?: ""
        val name = cleanDisplay(l("نام دفتر"))
        if (name.isBlank()) return null
        val members = mutableListOf<OfficeMember>()
        for (table in doc.select("table")) {
            val heads = table.select("thead th").map { normalizeForCompare(it.text()) }
            if (heads.any { it.contains("رشته") } && heads.any { it.contains("نام و نام خانوادگی") }) {
                for (tr in table.select("tbody tr")) {
                    val tds = tr.select("td")
                    if (tds.size < 2) continue
                    // MALFORMED table: only the first two cells and the
                    // first link of each row belong to that row.
                    val link = tr.selectFirst("a.more")
                    members.add(
                        OfficeMember(
                            engineerId = engineerIdFrom(link?.attr("href")),
                            name = cleanDisplay(tds[0].text()),
                            discipline = cleanDisplay(tds[1].text())
                        )
                    )
                }
            }
        }
        return ParsedOffice(
            siteId = siteId,
            name = name,
            licence = cleanCode(l("شماره مجوز")),
            manager = cleanDisplay(l("نام و نام خانوادگی مسئول دفتر")),
            mobile = cleanCode(l("شماره همراه مسئول دفتر")),
            contact = cleanDisplay(l("نام و نام خانوادگی رابط")),
            province = cleanDisplay(l("استان صادرکننده مجوز")),
            city = cleanDisplay(l("شهرستان محدوده فعالیت")),
            address = cleanDisplay(l("آدرس")),
            phone = cleanCode(l("شماره تماس")),
            fax = cleanCode(l("فکس")),
            hours = cleanDisplay(l("ساعت کاری")),
            members = members
        )
    }

    // ---- Engineer detail page ----

    fun parseEngineerDetail(html: String, siteId: Int): ParsedEngineer? {
        val doc = Jsoup.parse(html)
        val v = labelsOf(doc)
        fun l(label: String): String = v[normalizeForCompare(label)] ?: ""
        val name = cleanDisplay(l("نام"))
        if (name.isBlank()) return null
        var photo = ""
        val img = doc.selectFirst("img.img-fluid")
        val src = img?.attr("src").orEmpty()
        if (src.isNotBlank() && !src.contains(PLACEHOLDER_PHOTO)) {
            photo = if (src.startsWith("http")) src else BASE_URL + src
        }
        val education = mutableListOf<ParsedEducation>()
        val history = mutableListOf<ParsedHistory>()
        for (table in doc.select("table")) {
            val heads = table.select("thead th").map { normalizeForCompare(it.text()) }
            if (heads.any { it.contains("مدرک") }) {
                for (tr in table.select("tbody tr")) {
                    val tds = tr.select("td")
                    if (tds.size < 9) continue
                    education.add(
                        ParsedEducation(
                            degree = cleanDisplay(tds[0].text()),
                            field = cleanDisplay(tds[1].text()),
                            orientation = cleanDisplay(tds[2].text()),
                            universityType = cleanDisplay(tds[3].text()),
                            university = cleanDisplay(tds[4].text()),
                            city = cleanDisplay(tds[5].text()),
                            country = cleanDisplay(tds[6].text()),
                            start = cleanCode(tds[7].text()),
                            end = cleanCode(tds[8].text())
                        )
                    )
                }
            } else if (heads.any { it.startsWith("پایه") }) {
                for (tr in table.select("tbody tr")) {
                    val tds = tr.select("td")
                    if (tds.size < 4) continue
                    history.add(
                        ParsedHistory(
                            kind = cleanDisplay(tds[0].text()),
                            grade1 = cleanCode(tds[1].text()),
                            grade2 = cleanCode(tds[2].text()),
                            grade3 = cleanCode(tds[3].text())
                        )
                    )
                }
            }
        }
        return ParsedEngineer(
            siteId = siteId,
            name = name,
            family = cleanDisplay(l("نام خانوادگی")),
            discipline = cleanDisplay(l("رشته")),
            phone = cleanCode(l("شماره تماس")),
            designGrade = cleanCode(l("پایه طراحی")),
            supervisionGrade = cleanCode(l("پایه نظارت")),
            executionGrade = cleanCode(l("پایه اجرا")),
            licenceIssue = cleanCode(l("تاریخ صدور پروانه")),
            licenceValidity = cleanCode(l("تاریخ اعتبار پروانه")),
            photo = photo,
            education = education,
            history = history
        )
    }

    // ---- Uniform display (v3.11.6) ----
    // The profile/office pages must look the SAME for every engineer and
    // office — including list-only engineers whose grades are "_" and
    // whose dates/phones are blank. These pure helpers decide what a
    // value means; the screens only render the result.

    /** The site's "nothing recorded" markers: blank, or "_" / "-" placeholders. */
    fun isMissingDirectoryValue(value: String): Boolean {
        val v = value.trim()
        return v.isEmpty() || v == "_" || v == "-"
    }

    /** Display form of a single value: itself, or «ثبت نشده» when missing. */
    fun directoryDisplayValue(value: String): String =
        if (isMissingDirectoryValue(value)) "ثبت نشده" else value.trim()

    /** A Jalali date is plausible when it parses as yyyy/mm/dd with a
     *  sane year (>= 1300) and a real month/day. Anything else (garbage
     *  like "1278/10/11" or "13285/02/0", "_", blank) is not a date. */
    fun isPlausibleJalaliDate(value: String): Boolean {
        val v = value.trim()
        if (isMissingDirectoryValue(v)) return false
        val parts = v.split("/")
        if (parts.size != 3) return false
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        return year >= 1300 && month in 1..12 && day in 1..31
    }

    /** The date a licence grade was obtained, or null when it never was
     *  (or the stored date is garbage). */
    fun licenceGradeDate(value: String): String? =
        if (isPlausibleJalaliDate(value)) value.trim() else null

    /** Latin digits to Persian digits, for display. */
    fun toPersianDigits(value: String): String {
        val out = StringBuilder(value.length)
        for (c in value) {
            out.append(
                when (c) {
                    '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
                    '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
                    else -> c
                }
            )
        }
        return out.toString()
    }

    /** Education date range. The site stores 1300/01/01 for an unknown
     *  date (1,190 of 1,511 education rows) — that is missing, never a
     *  real year-1300 date. The missing side is omitted, not faked. */
    fun educationDateText(start: String, end: String): String {
        val s = educationDateOrNull(start)
        val e = educationDateOrNull(end)
        return when {
            s != null && e != null -> "$s تا $e"
            s == null && e != null -> "تا $e"
            s != null && e == null -> "از $s"
            else -> "تاریخ ثبت نشده"
        }
    }

    private fun educationDateOrNull(value: String): String? {
        val v = value.trim()
        if (v == "1300/01/01" || !isPlausibleJalaliDate(v)) return null
        return v
    }
}
