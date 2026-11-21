package com.nezammonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests for the Yazd design-offices directory (v3.10.0), using
 * real HTML pages fetched from service.yazdnezam.ir:8033 on 1405/08/29
 * (fixtures in src/test/resources/directory/).
 */
class DirectoryParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("directory/$name")!!
            .bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun listPage_parsesTwentyRowsWithIds() {
        val rows = DirectoryParser.parseOfficeListPage(fixture("office-list-page1.html"))
        assertEquals(20, rows.size)
        val first = rows[0]
        assertEquals(500, first.siteId)
        assertEquals("دفتر 233", first.name)
        assertEquals("5-31-0005031-00233", first.licence)
        assertEquals("مریم چاوش پور", first.manager)
        assertEquals("یزد", first.city)
        assertEquals("یزد", first.province)
        assertTrue(rows.all { it.siteId > 0 && it.name.isNotBlank() })
    }

    @Test
    fun officeDetail_205_nineMembers() {
        val office = DirectoryParser.parseOfficeDetail(fixture("office-464.html"), 464)!!
        assertEquals("دفتر 205", office.name)
        assertEquals("5-31-0054301-00205", office.licence)
        assertEquals("وجیهه جعفری ندوشن", office.manager)
        assertEquals("09130650400", office.mobile)
        assertEquals(9, office.members.size)
        val first = office.members[0]
        assertEquals(1170, first.engineerId)
        assertEquals("احسان اله ذوالفقاری", first.name)
        assertEquals("مکانیک", first.discipline)
        // Every member has an engineer id and a discipline from the data.
        assertTrue(office.members.all { it.engineerId != null && it.discipline.isNotBlank() })
    }

    @Test
    fun officeDetail_zeroMembers() {
        val office70 = DirectoryParser.parseOfficeDetail(fixture("office-57-zero-members.html"), 57)!!
        assertEquals("دفتر 70", office70.name)
        assertEquals("مهدی فلاح علی آبادی", office70.manager)
        assertTrue(office70.members.isEmpty())

        val office240 = DirectoryParser.parseOfficeDetail(fixture("office-501.html"), 501)!!
        assertEquals("دفتر 240", office240.name)
        assertTrue(office240.members.isEmpty())
    }

    @Test
    fun engineerDetail_5896_fullProfile() {
        val eng = DirectoryParser.parseEngineerDetail(fixture("engineer-5896.html"), 5896)!!
        assertEquals("ابوالفضل", eng.name)
        assertEquals("خادم زاده فیروزآبادی", eng.family)
        assertEquals("برق", eng.discipline)
        assertEquals("09138583616", eng.phone)
        assertEquals("3", eng.designGrade)
        assertEquals("3", eng.supervisionGrade)
        // The page has no "پایه اجرا" label at all for this engineer —
        // the grade is empty, not an error.
        assertEquals("", eng.executionGrade)
        assertEquals("1398/09/02", eng.licenceIssue)
        assertEquals("1406/03/17", eng.licenceValidity)
        assertEquals(
            "http://service.yazdnezam.ir:8033/resume/Resume/public/uploads/img/ftpscanmohandesin/Pic/31-5-3-02134.jpg",
            eng.photo
        )
        // Licence history: Latin-digit "پايه 1" headers must be found.
        assertEquals(2, eng.history.size)
        assertEquals("پایه طراحی", eng.history[0].kind)
        assertEquals("_", eng.history[0].grade1)
        assertEquals("_", eng.history[0].grade2)
        assertEquals("1398/09/02", eng.history[0].grade3)
        assertEquals("پایه نظارت", eng.history[1].kind)
        // Education: 9 cells per row (no row-number cell in the body).
        assertEquals(1, eng.education.size)
        val edu = eng.education[0]
        assertEquals("کارشناسی", edu.degree)
        assertEquals("برق", edu.field)
        assertEquals("قدرت", edu.orientation)
        assertEquals("آزاد اسلامی", edu.universityType)
        assertEquals("آزاد", edu.university)
        assertEquals("اشکذر", edu.city)
        assertEquals("ایران", edu.country)
        assertEquals("1300/01/01", edu.start)
        assertEquals("1390/09/23", edu.end)
    }

    @Test
    fun engineerDetail_167_arshadGrades() {
        val eng = DirectoryParser.parseEngineerDetail(fixture("engineer-167-arshad.html"), 167)!!
        assertEquals("محمد", eng.name)
        assertEquals("افخمی اردکانی", eng.family)
        assertEquals("عمران", eng.discipline)
        assertEquals("1", eng.designGrade)
        // Grades can be the word "ارشد", not just a number.
        assertEquals("1 ارشد", eng.supervisionGrade)
        assertEquals("1", eng.executionGrade)
        assertEquals(3, eng.history.size)
    }

    @Test
    fun engineerDetail_emptyPage_returnsNull() {
        assertNull(DirectoryParser.parseEngineerDetail("", 999))
        assertNull(DirectoryParser.parseEngineerDetail("<html><body>Server Error</body></html>", 999))
        assertNull(DirectoryParser.parseOfficeDetail("", 999))
    }

    @Test
    fun normalizeForCompare_foldsArabicAndDigits() {
        val a = DirectoryParser.normalizeForCompare("وجيهه جعفري ندوشن")
        val b = DirectoryParser.normalizeForCompare("وجیهه جعفری ندوشن")
        assertEquals(a, b)
        assertEquals(
            DirectoryParser.normalizeForCompare("دفتر ۲۰۵"),
            DirectoryParser.normalizeForCompare("دفتر 205")
        )
        // ZWNJ is folded, so a ZWNJ-joined spelling matches the plain
        // joined spelling. (Plain spaces are kept: "محمد مهدی" and
        // "محمدمهدی" are different keys on purpose — the app stores
        // names spaced, and matching is done on the joined form only
        // where personNameKey is used.)
        assertEquals(
            DirectoryParser.normalizeForCompare("محمد‌مهدی"),
            DirectoryParser.normalizeForCompare("محمدمهدی")
        )
    }

    // v3.11.6: uniform display — a missing value is blank, "_" or "-"
    // (list-only engineers store "_" for grades: 3,290 of 3,905), and
    // it must render as «ثبت نشده», never blank and never "_".
    @Test
    fun missingValue_blankUnderscoreDash_areMissing() {
        assertTrue(DirectoryParser.isMissingDirectoryValue(""))
        assertTrue(DirectoryParser.isMissingDirectoryValue("   "))
        assertTrue(DirectoryParser.isMissingDirectoryValue("_"))
        assertTrue(DirectoryParser.isMissingDirectoryValue("-"))
        assertTrue(!DirectoryParser.isMissingDirectoryValue("2"))
        assertTrue(!DirectoryParser.isMissingDirectoryValue("ارشد"))
        assertEquals("ثبت نشده", DirectoryParser.directoryDisplayValue("_"))
        assertEquals("ثبت نشده", DirectoryParser.directoryDisplayValue(""))
        assertEquals("2", DirectoryParser.directoryDisplayValue("2"))
    }

    // v3.11.6: licence-grade dates — only a real yyyy/mm/dd with a sane
    // year counts as obtained. The snapshot's garbage dates (203 of
    // 3,134 history rows) must not render as real dates.
    @Test
    fun licenceGradeDate_onlyPlausibleDatesCount() {
        assertEquals("1391/12/23", DirectoryParser.licenceGradeDate("1391/12/23"))
        assertNull(DirectoryParser.licenceGradeDate("_"))
        assertNull(DirectoryParser.licenceGradeDate(""))
        // Year 1278 — the site's garbage/placeholder date (e.g. engineer
        // 3320, «پایه اجرا»).
        assertNull(DirectoryParser.licenceGradeDate("1278/10/11"))
        // Five-digit year — unparseable garbage (engineer 336).
        assertNull(DirectoryParser.licenceGradeDate("13285/02/0"))
        assertNull(DirectoryParser.licenceGradeDate("not-a-date"))
        assertTrue(DirectoryParser.isPlausibleJalaliDate("1399/06/28"))
        assertTrue(!DirectoryParser.isPlausibleJalaliDate("1399/13/01"))
    }

    @Test
    fun persianDigits_convertsLatinDigits() {
        assertEquals("۱۳۹۱/۱۲/۲۳", DirectoryParser.toPersianDigits("1391/12/23"))
        assertEquals("پایه ۳", DirectoryParser.toPersianDigits("پایه 3"))
    }

    // v3.11.6: education dates — 1300/01/01 is the site's placeholder
    // for an unknown date (1,190 of 1,511 education rows) and must never
    // render as a real year-1300 date.
    @Test
    fun educationDateText_placeholderIsMissing() {
        // Real row: engineer 3320, start 1300/01/01, end 1381/10/20.
        assertEquals("تا 1381/10/20", DirectoryParser.educationDateText("1300/01/01", "1381/10/20"))
        assertEquals("1380/06/31 تا 1384/03/10", DirectoryParser.educationDateText("1380/06/31", "1384/03/10"))
        assertEquals("از 1380/06/31", DirectoryParser.educationDateText("1380/06/31", "1300/01/01"))
        assertEquals("تاریخ ثبت نشده", DirectoryParser.educationDateText("1300/01/01", "1300/01/01"))
        assertEquals("تاریخ ثبت نشده", DirectoryParser.educationDateText("", ""))
    }
}
