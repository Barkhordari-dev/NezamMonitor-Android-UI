package com.nezammonitor.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the v3.11.0 directory-search fix: exact-only office matching
 * (no loose/contains fallback), the direct engineer search, and the
 * office-name query built from the case's messy daf_name text.
 *
 * Fixtures are REAL search pages fetched from service.yazdnezam.ir:8033
 * on 1405/08/29 (src/test/resources/directory/):
 * - eng-search-karimi.html: ?name=امیرحسین&family=کریمی → 1 row (id 3320)
 * - eng-search-akhoundi.html: ?family=آخوندی → 16 rows, incl. محمدجواد
 *   آخوندی (id 4183, دفتر 73) and حمیدرضا آخوندی (id 8009, no office)
 * - off-search-194.html: ?name=دفتر 194 → 1 row (id 433)
 * - off-search-178.html / off-search-189.html: 1 row each (ids 387 / 439)
 * - off-search-78.html / off-search-104.html: NO table (not found)
 * - off-search-bare78.html: ?name=78 → 1 row, دفتر 178 — the row the
 *   old loose fallback used to return for "دفتر 78".
 */
class DirectorySearchTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("directory/$name")!!
            .bufferedReader(Charsets.UTF_8).readText()

    // ---------- Engineer search parsing ----------

    @Test
    fun engineerSearch_parsesSingleRowWithOffice() {
        val rows = DirectoryParser.parseEngineerSearchPage(fixture("eng-search-karimi.html"))
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals(3320, row.siteId)
        assertEquals("امیرحسین", row.name)
        assertEquals("کریمی", row.family)
        assertEquals("برق", row.discipline)
        assertEquals("3", row.designGrade)
        assertEquals("3", row.supervisionGrade)
        assertEquals("3", row.executionGrade)
        assertEquals("دفتر 195", row.officeName)
        assertEquals(438, row.officeId)
    }

    @Test
    fun engineerSearch_parsesSixteenRowsIncludingOfficeless() {
        val rows = DirectoryParser.parseEngineerSearchPage(fixture("eng-search-akhoundi.html"))
        assertEquals(16, rows.size)
        val mohammadJavad = rows.first { it.siteId == 4183 }
        assertEquals("محمدجواد", mohammadJavad.name)
        assertEquals("آخوندی", mohammadJavad.family)
        assertEquals("برق", mohammadJavad.discipline)
        assertEquals("دفتر 73", mohammadJavad.officeName)
        assertEquals(59, mohammadJavad.officeId)
        // An engineer without an office: "_" in the office cell.
        val hamidReza = rows.first { it.siteId == 8009 }
        assertEquals("حمیدرضا", hamidReza.name)
        assertEquals("آخوندی", hamidReza.family)
        assertEquals("", hamidReza.officeName)
        assertNull(hamidReza.officeId)
        // A compound family name stays whole.
        val amir = rows.first { it.siteId == 6035 }
        assertEquals("آخوندی هریسکی", amir.family)
    }

    // ---------- Office query from the case's messy daf_name ----------

    @Test
    fun officeQueryName_extractsNumberFromRealCaseValues() {
        // The exact daf_name values of the 77 cases in the .nzmdata sample.
        assertEquals("دفتر 194", DirectoryParser.officeQueryName("شماره 194 يزد"))
        assertEquals("دفتر 89", DirectoryParser.officeQueryName("دفتر شماره 89"))
        assertEquals("دفتر 205", DirectoryParser.officeQueryName("شماره205يزد"))
        assertEquals("دفتر 221", DirectoryParser.officeQueryName("دفتر221 يزد"))
        assertEquals("دفتر 78", DirectoryParser.officeQueryName("دفتر شماره78"))
        assertEquals("دفتر 258", DirectoryParser.officeQueryName("شماره258"))
        assertEquals("دفتر 267", DirectoryParser.officeQueryName("دفتر طراحي267"))
        // A bare number means office N.
        assertEquals("دفتر 194", DirectoryParser.officeQueryName("194"))
        assertEquals("دفتر 194", DirectoryParser.officeQueryName("۱۹۴"))
        // A clean name and a company name pass through unchanged.
        assertEquals("دفتر 194", DirectoryParser.officeQueryName("دفتر 194"))
        assertEquals("سازياران بافت شهر يزد", DirectoryParser.officeQueryName("سازياران بافت شهر يزد"))
        assertEquals("", DirectoryParser.officeQueryName(""))
        assertEquals("", DirectoryParser.officeQueryName("   "))
    }

    // ---------- Exact-only office selection (no substitution) ----------

    @Test
    fun selectExactOfficeRow_resolvesExistingOffices() {
        val rows194 = DirectoryParser.parseOfficeListPage(fixture("off-search-194.html"))
        assertEquals(1, rows194.size)
        assertEquals(433, DirectoryParser.selectExactOfficeRow(rows194, "شماره 194 يزد")?.siteId)
        assertEquals(433, DirectoryParser.selectExactOfficeRow(rows194, "دفتر ۱۹۴")?.siteId)
        assertEquals(433, DirectoryParser.selectExactOfficeRow(rows194, "194")?.siteId)
        val rows178 = DirectoryParser.parseOfficeListPage(fixture("off-search-178.html"))
        assertEquals(387, DirectoryParser.selectExactOfficeRow(rows178, "دفتر 178")?.siteId)
        val rows189 = DirectoryParser.parseOfficeListPage(fixture("off-search-189.html"))
        assertEquals(1, rows189.size)
        assertEquals(439, DirectoryParser.selectExactOfficeRow(rows189, "دفتر 189")?.siteId)
    }

    @Test
    fun selectExactOfficeRow_missingOfficesAreNotFound() {
        // 78 and the ghost 104: the search returns no table at all.
        assertTrue(DirectoryParser.parseOfficeListPage(fixture("off-search-78.html")).isEmpty())
        assertTrue(DirectoryParser.parseOfficeListPage(fixture("off-search-104.html")).isEmpty())
        assertNull(DirectoryParser.selectExactOfficeRow(emptyList(), "دفتر 78"))
        assertNull(DirectoryParser.selectExactOfficeRow(emptyList(), "دفتر 104"))
        assertNull(DirectoryParser.selectExactOfficeRow(emptyList(), "دفتر 87"))
        assertNull(DirectoryParser.selectExactOfficeRow(emptyList(), ""))
    }

    @Test
    fun selectExactOfficeRow_neverSubstitutesANearOffice() {
        // The bare "78" search DOES return دفتر 178 — the old loose
        // fallback (contains) picked it for "دفتر 78". Exact-only must not.
        val rows = DirectoryParser.parseOfficeListPage(fixture("off-search-bare78.html"))
        assertEquals(1, rows.size)
        assertEquals("دفتر 178", rows[0].name)
        assertNull(DirectoryParser.selectExactOfficeRow(rows, "دفتر 78"))
        assertNull(DirectoryParser.selectExactOfficeRow(rows, "دفتر شماره78"))
        assertNull(DirectoryParser.selectExactOfficeRow(rows, "78"))
        assertNull(DirectoryParser.selectExactOfficeRow(rows, "دفتر 87"))
        // ...but the real 178 still resolves from the same rows.
        assertEquals(387, DirectoryParser.selectExactOfficeRow(rows, "دفتر 178")?.siteId)
    }

    // ---------- Exact-only engineer selection ----------

    @Test
    fun selectExactEngineerRow_resolvesByFullName() {
        val karimi = DirectoryParser.parseEngineerSearchPage(fixture("eng-search-karimi.html"))
        assertEquals(3320, DirectoryParser.selectExactEngineerRow(karimi, "امیرحسین کریمی")?.siteId)
        // Persian/Arabic script differences in the query do not matter.
        assertEquals(3320, DirectoryParser.selectExactEngineerRow(karimi, "اميرحسين كريمي")?.siteId)
        val akhoundi = DirectoryParser.parseEngineerSearchPage(fixture("eng-search-akhoundi.html"))
        assertEquals(4183, DirectoryParser.selectExactEngineerRow(akhoundi, "محمدجواد آخوندی")?.siteId)
        // An engineer without an office is found the same way — and an
        // office hint never blocks a single exact match.
        assertEquals(8009, DirectoryParser.selectExactEngineerRow(akhoundi, "حمیدرضا آخوندی")?.siteId)
        assertEquals(8009, DirectoryParser.selectExactEngineerRow(akhoundi, "حمیدرضا آخوندی", officeName = "دفتر 73")?.siteId)
    }

    @Test
    fun selectExactEngineerRow_rejectsPartialFamilyMatches() {
        val akhoundi = DirectoryParser.parseEngineerSearchPage(fixture("eng-search-akhoundi.html"))
        // "امیر آخوندی" is NOT "امیر آخوندی هریسکی"; "حسین آخوندی" is NOT
        // "حسین آخوندی دهج" — a contains match would have picked them.
        assertNull(DirectoryParser.selectExactEngineerRow(akhoundi, "امیر آخوندی"))
        assertNull(DirectoryParser.selectExactEngineerRow(akhoundi, "حسین آخوندی"))
        assertNull(DirectoryParser.selectExactEngineerRow(akhoundi, "کسی که نیست"))
        assertNull(DirectoryParser.selectExactEngineerRow(akhoundi, ""))
        assertNull(DirectoryParser.selectExactEngineerRow(emptyList(), "محمدجواد آخوندی"))
    }

    @Test
    fun selectExactEngineerRow_tieBreaksByDisciplineThenOffice() {
        val rows = listOf(
            DirectoryParser.EngineerSearchRow(1, "تست", "تستی", "مکانیک", "3", "3", "3", "دفتر 194", 433),
            DirectoryParser.EngineerSearchRow(2, "تست", "تستی", "برق", "3", "3", "3", "دفتر 73", 59),
            DirectoryParser.EngineerSearchRow(3, "تست", "تستی", "عمران", "3", "3", "3", "", null)
        )
        // No hints → the first exact row.
        assertEquals(1, DirectoryParser.selectExactEngineerRow(rows, "تست تستی")?.siteId)
        // Discipline wins over office.
        assertEquals(2, DirectoryParser.selectExactEngineerRow(rows, "تست تستی", setOf("برق"), "دفتر 194")?.siteId)
        // Office wins when the discipline does not match any row.
        assertEquals(1, DirectoryParser.selectExactEngineerRow(rows, "تست تستی", setOf("معماری"), "دفتر 194")?.siteId)
        // An office hint matching no row falls back to the first exact row.
        assertEquals(1, DirectoryParser.selectExactEngineerRow(rows, "تست تستی", officeName = "دفتر 999")?.siteId)
    }

    // ---------- Name splitting ----------

    @Test
    fun splitEngineerName_splitsOnFirstSpace() {
        assertEquals("محمدجواد" to "آخوندی", DirectoryParser.splitEngineerName("محمدجواد آخوندی"))
        assertEquals("امیر" to "آخوندی هریسکی", DirectoryParser.splitEngineerName("امیر آخوندی هریسکی"))
        assertEquals("" to "تک‌نام", DirectoryParser.splitEngineerName("تک‌نام"))
        assertEquals("" to "", DirectoryParser.splitEngineerName("   "))
    }
}
