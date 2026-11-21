package com.nezammonitor.app.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * v3.11.4 — THE proof test. In 3.11.3 the directory shipped but NEITHER
 * detail page showed anything on the phone, and the unit tests did not
 * catch it: they seeded a FAKE DAO and never tried the values the UI
 * really sends. This test uses a REAL in-memory Room database and the
 * REAL snapshot file, and drives the EXACT values the click sites send:
 *
 *  - office: the raw `daf_name` from the case API — a bare number
 *    ("۱۹۴"), a messy label ("شماره 194 يزد", "دفتر221 يزد"), and the
 *    ghost/missing numbers ("۸۹", "۱۰۴") that must stay «یافت نشد»;
 *  - engineer: the case API's full name in Arabic script
 *    ("اميرحسين كريمي"), the same with a "مهندس" title, and a
 *    family-only query ("موسوي نسل خامنه").
 *
 * It also proves the seed itself into a real Room database (not a fake
 * DAO) and that the seeded data is Persian.
 *
 * Count truth (resolved from the actual file, v3.11.4): 203 offices,
 * 1174 full engineers + 3905 list-only = 5079 distinct engineers.
 * The earlier "5,078" figure was the site's engineers-list page count;
 * it misses engineer id 4183 (محمدجواد آخوندی), who is in the snapshot
 * as a full engineer via office membership. Overlap full∩list-only = 0.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectoryRealRoomProofTest {

    private lateinit var db: NezamDatabase
    private lateinit var dao: DirectoryDao
    private lateinit var parsed: DirectorySnapshotSeeder.SnapshotFile

    companion object {
        private val snapshotFile: File by lazy {
            val candidates = listOf(
                File("src/main/assets/directory-snapshot.json"),
                File(System.getProperty("user.dir"), "src/main/assets/directory-snapshot.json")
            )
            candidates.firstOrNull { it.exists() }
                ?: error("directory-snapshot.json not found from ${System.getProperty("user.dir")}")
        }
    }

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                NezamDatabase::class.java
            ).allowMainThreadQueries().build()
            dao = db.directoryDao()
            parsed = DirectorySnapshotSeeder.parseSnapshot(snapshotFile.readText(Charsets.UTF_8))
            DirectorySnapshotSeeder.seedFromParsed(parsed, dao)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedIntoRealRoom_hasTrueCounts() = runBlocking {
        // The snapshot itself: 1174 full + 3905 list-only, no overlap.
        assertEquals(1174, parsed.engineers.size)
        assertEquals(3905, parsed.engineersListOnly.size)
        assertTrue(
            "full and list-only ids must be disjoint",
            parsed.engineers.keys.map { it.toInt() }.toSet()
                .intersect(parsed.engineersListOnly.map { it.id }.toSet()).isEmpty()
        )
        // The 5,078 figure was the site list-page count: engineer 4183
        // (محمدجواد آخوندی) is a full engineer here, missing from the list.
        assertNotNull(parsed.engineers["4183"])
        assertTrue(parsed.engineersListOnly.none { it.id == 4183 })

        // The real Room tables after seeding.
        assertEquals(203, dao.getOfficeCount())
        assertEquals(1174 + 3905, dao.getEngineerCount())
        // Education (1511) and licence-history (3134) row totals are
        // counted per engineer in seedIntoRealRoom_educationAndHistoryCounts.
    }

    @Test
    fun seedIntoRealRoom_educationAndHistoryCounts() = runBlocking {
        // Count education/history rows across all engineers via the DAO.
        var education = 0
        var history = 0
        for (engineer in dao.getAllEngineersList()) {
            education += dao.getEducation(engineer.siteId).size
            history += dao.getLicenceHistory(engineer.siteId).size
        }
        assertEquals(1511, education)
        assertEquals(3134, history)
    }

    @Test
    fun seededDataIsPersian() = runBlocking {
        // No stored directory text may contain Arabic ي / ك anymore.
        val hasArabic = { text: String -> text.contains('ي') || text.contains('ك') }
        for (office in dao.getAllOfficesList()) {
            assertTrue("office ${office.siteId} name is Persian", !hasArabic(office.name))
            assertTrue("office ${office.siteId} city is Persian", !hasArabic(office.city))
            assertTrue("office ${office.siteId} address is Persian", !hasArabic(office.address))
            assertTrue("office ${office.siteId} manager is Persian", !hasArabic(office.manager))
        }
        for (engineer in dao.getAllEngineersList()) {
            assertTrue("engineer ${engineer.siteId} name is Persian", !hasArabic(engineer.name))
            assertTrue("engineer ${engineer.siteId} family is Persian", !hasArabic(engineer.family))
            assertTrue("engineer ${engineer.siteId} officeName is Persian", !hasArabic(engineer.officeName))
        }
        // Spot check: the snapshot's Arabic "اميرحسين كريمي" is stored Persian.
        val karimi = dao.getEngineer(3320)
        assertEquals("امیرحسین", karimi?.name)
        assertEquals("کریمی", karimi?.family)
    }

    @Test
    fun officeLookup_withExactUiValues() = runBlocking {
        val offices = dao.getAllOfficesList()
        // Bare number, exactly as the UI sends case.office when it is
        // just a number: "۱۹۴" must open دفتر 194 (site id 433).
        assertEquals(433, DirectoryParser.resolveOffice("۱۹۴", offices)?.siteId)
        // Messy daf_name values from the real cases (Arabic script).
        assertEquals(433, DirectoryParser.resolveOffice("شماره 194 يزد", offices)?.siteId)
        assertEquals(433, DirectoryParser.resolveOffice("شماره194يزد", offices)?.siteId)
        assertEquals(482, DirectoryParser.resolveOffice("دفتر221 يزد", offices)?.siteId)
        assertEquals(464, DirectoryParser.resolveOffice("شماره205يزد", offices)?.siteId)
        assertEquals(464, DirectoryParser.resolveOffice("دفتر 205", offices)?.siteId)
        // Missing offices stay «یافت نشد» — no substitution, ever.
        assertNull(DirectoryParser.resolveOffice("۸۹", offices))
        assertNull(DirectoryParser.resolveOffice("دفتر شماره 89", offices))
        assertNull(DirectoryParser.resolveOffice("۱۰۴", offices))
        assertNull(DirectoryParser.resolveOffice("دفتر شماره78", offices))
        assertNull(DirectoryParser.resolveOffice("", offices))

        // This is the regression the 3.11.3 tests missed: the OLD plain
        // comparison never matched a bare number against "دفتر 194".
        val key = DirectoryParser.normalizeForCompare("۱۹۴")
        assertTrue(
            "old plain comparison must NOT match (that was the 3.11.3 bug)",
            offices.none { DirectoryParser.normalizeForCompare(it.name) == key }
        )
    }

    @Test
    fun engineerLookup_withExactUiValues() = runBlocking {
        val engineers = dao.getAllEngineersList()
        // Full name in Arabic script, exactly as the case API sends it.
        assertEquals(3320, DirectoryParser.resolveEngineer("اميرحسين كريمي", engineers)?.siteId)
        // The same name with a "مهندس" title in front.
        assertEquals(3320, DirectoryParser.resolveEngineer("مهندس اميرحسين كريمي", engineers)?.siteId)
        assertEquals(3320, DirectoryParser.resolveEngineer("مهندس‌اميرحسين كريمي", engineers)?.siteId)
        // Family alone (unique family "موسوي نسل خامنه", engineer id 12).
        assertEquals(12, DirectoryParser.resolveEngineer("موسوي نسل خامنه", engineers)?.siteId)
        // First name alone resolves to an engineer with that first name.
        val leila = DirectoryParser.resolveEngineer("ليلا", engineers)
        assertNotNull(leila)
        assertEquals("لیلا", leila?.name)
        // Unknown names stay «یافت نشد».
        assertNull(DirectoryParser.resolveEngineer("مهندس خیالی نیست", engineers))
        assertNull(DirectoryParser.resolveEngineer("", engineers))
    }

    @Test
    fun officeDetailFills_fromRealRoom() = runBlocking {
        // The whole detail-page path: resolve -> office row -> members.
        val offices = dao.getAllOfficesList()
        val office = DirectoryParser.resolveOffice("۱۹۴", offices)
        assertNotNull(office)
        assertEquals("دفتر 194", office?.name)
        val members = dao.getEngineersForOffice(office!!.siteId)
        assertEquals(10, members.size)
        assertTrue(members.all { it.officeId == office.siteId })
        // Every member's name opens their profile.
        val first = members.first()
        val profile = DirectoryParser.resolveEngineer("${first.name} ${first.family}", dao.getAllEngineersList())
        assertEquals(first.siteId, profile?.siteId)
    }

    @Test
    fun engineerDetailFills_fromRealRoom() = runBlocking {
        // The whole profile path: resolve -> engineer + education + history.
        val engineer = DirectoryParser.resolveEngineer("اميرحسين كريمي", dao.getAllEngineersList())
        assertNotNull(engineer)
        assertEquals(3320, engineer?.siteId)
        assertEquals("امیرحسین", engineer?.name)
        assertEquals("کریمی", engineer?.family)
        assertEquals(1, dao.getEducation(3320).size)
        assertEquals(3, dao.getLicenceHistory(3320).size)
        assertTrue(engineer!!.photo.isNotBlank())
    }
}
