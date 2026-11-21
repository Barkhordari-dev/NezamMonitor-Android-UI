package com.nezammonitor.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.11.3: the bundled directory snapshot
 * (app/src/main/assets/directory-snapshot.json, generated 2026-11-20)
 * seeds the Room directory tables on first run with no network. The
 * REAL snapshot file is parsed here (its counts are the ground truth
 * from the scrape), and the seeding rules are checked against an
 * in-memory fake DAO:
 *  - a newer snapshot REPLACES the whole dataset (wipe + re-seed);
 *    local tampering is gone after a re-seed (user decision 2026-11-20
 *    — no in-app update, no per-record merge);
 *  - v3.11.4: the seeded data is PERSIAN — the snapshot's Arabic
 *    script is converted at seed time (names below are Persian now);
 *  - ghost offices (104, 116, 23, 29, 37, 44, 64, 87) exist only as
 *    office_name labels on list-only engineers, never as office rows;
 *  - offices 78 and 89 do not exist anywhere.
 */
class DirectorySnapshotSeederTest {

    private class FakeDirectoryDao : DirectoryDao {
        val offices = mutableMapOf<Int, DirectoryOfficeEntity>()
        val engineers = mutableMapOf<Int, DirectoryEngineerEntity>()
        val education = mutableMapOf<Int, MutableList<DirectoryEducationEntity>>()
        val history = mutableMapOf<Int, MutableList<DirectoryLicenceHistoryEntity>>()

        override fun getAllOffices(): Flow<List<DirectoryOfficeEntity>> = flowOf(offices.values.toList())
        override suspend fun getAllOfficesList(): List<DirectoryOfficeEntity> = offices.values.toList()
        override suspend fun getOffice(siteId: Int): DirectoryOfficeEntity? = offices[siteId]
        override suspend fun getOfficeCount(): Int = offices.size
        override suspend fun insertOffices(offices: List<DirectoryOfficeEntity>) {
            offices.forEach { this.offices[it.siteId] = it }
        }
        override suspend fun insertOffice(office: DirectoryOfficeEntity) {
            offices[office.siteId] = office
        }
        override suspend fun getEngineer(siteId: Int): DirectoryEngineerEntity? = engineers[siteId]
        override suspend fun getEngineersForOffice(officeId: Int): List<DirectoryEngineerEntity> =
            engineers.values.filter { it.officeId == officeId }
        override suspend fun getAllEngineersList(): List<DirectoryEngineerEntity> = engineers.values.toList()
        override suspend fun getEngineerCount(): Int = engineers.size
        override suspend fun insertEngineers(engineers: List<DirectoryEngineerEntity>) {
            engineers.forEach { this.engineers[it.siteId] = it }
        }
        override suspend fun insertEngineer(engineer: DirectoryEngineerEntity) {
            engineers[engineer.siteId] = engineer
        }
        override suspend fun getEducation(engineerId: Int): List<DirectoryEducationEntity> =
            education[engineerId] ?: emptyList()
        override suspend fun deleteEducation(engineerId: Int) { education.remove(engineerId) }
        override suspend fun insertEducation(rows: List<DirectoryEducationEntity>) {
            rows.forEach { education.getOrPut(it.engineerId) { mutableListOf() } += it }
        }
        override suspend fun getLicenceHistory(engineerId: Int): List<DirectoryLicenceHistoryEntity> =
            history[engineerId] ?: emptyList()
        override suspend fun deleteLicenceHistory(engineerId: Int) { history.remove(engineerId) }
        override suspend fun insertLicenceHistory(rows: List<DirectoryLicenceHistoryEntity>) {
            rows.forEach { history.getOrPut(it.engineerId) { mutableListOf() } += it }
        }
        override suspend fun deleteAllOffices() { offices.clear() }
        override suspend fun deleteAllEngineers() { engineers.clear() }
        override suspend fun deleteAllEducation() { education.clear() }
        override suspend fun deleteAllLicenceHistory() { history.clear() }
    }

    companion object {
        private val snapshotFile: File by lazy {
            val candidates = listOf(
                File("src/main/assets/directory-snapshot.json"),
                File(System.getProperty("user.dir"), "src/main/assets/directory-snapshot.json")
            )
            candidates.firstOrNull { it.exists() }
                ?: error("directory-snapshot.json not found from ${System.getProperty("user.dir")}")
        }
        private val parsed: DirectorySnapshotSeeder.SnapshotFile by lazy {
            DirectorySnapshotSeeder.parseSnapshot(snapshotFile.readText(Charsets.UTF_8))
        }
    }

    @Test
    fun realSnapshotHasExpectedCounts() {
        assertEquals("2026-11-20", parsed.meta.generated)
        assertEquals(203, parsed.offices.size)
        assertEquals(1174, parsed.engineers.size)
        assertEquals(3905, parsed.engineersListOnly.size)
        assertEquals(1511, parsed.engineers.values.sumOf { it.education.size })
        assertEquals(3134, parsed.engineers.values.sumOf { it.licenceHistory.size })
    }

    @Test
    fun versionReadFromRawBytes() {
        assertEquals(
            "2026-11-20",
            DirectorySnapshotSeeder.readSnapshotVersion(snapshotFile.readText(Charsets.UTF_8).take(4096))
        )
    }

    @Test
    fun seedFillsAllTables() = runBlocking {
        val dao = FakeDirectoryDao()
        val result = DirectorySnapshotSeeder.seedFromParsed(parsed, dao)
        assertEquals(203, result.offices)
        assertEquals(1174, result.engineers)
        assertEquals(3905, result.listOnly)
        assertEquals(1511, result.educationRows)
        assertEquals(3134, result.historyRows)
        assertEquals(203, dao.getOfficeCount())
        assertEquals(1174 + 3905, dao.getEngineerCount())
        // A known office and engineer resolve by id.
        assertTrue(dao.getOffice(32)?.name?.contains("4") == true)
        val karimi = dao.getEngineer(3320)
        assertEquals("امیرحسین", karimi?.name)
        assertEquals("کریمی", karimi?.family)
        assertTrue(dao.getEducation(3320).isNotEmpty() || dao.getLicenceHistory(3320).isNotEmpty())
    }

    @Test
    fun reseedReplacesTheWholeDataset() = runBlocking {
        val dao = FakeDirectoryDao()
        DirectorySnapshotSeeder.seedFromParsed(parsed, dao)
        // Local tampering: rename an engineer, delete an office, add a
        // row that is NOT in the snapshot at all.
        val tampered = dao.getEngineer(3320)!!.copy(name = "نام-دستکاری‌شده")
        dao.insertEngineer(tampered)
        dao.deleteAllOffices()
        dao.insertEngineer(
            DirectoryEngineerEntity(siteId = 999999, name = "مهندس خیالی", family = "نیست")
        )
        assertEquals("نام-دستکاری‌شده", dao.getEngineer(3320)?.name)
        assertEquals(0, dao.getOfficeCount())

        // Re-seeding with the same snapshot restores everything exactly:
        // the tampered name is gone, the deleted offices are back, and
        // the phantom row is gone — a newer snapshot simply replaces
        // the whole dataset (no per-record merge, v3.11.3 scope change).
        val result = DirectorySnapshotSeeder.seedFromParsed(parsed, dao)
        assertEquals(203, result.offices)
        assertEquals(1174, result.engineers)
        assertEquals(3905, result.listOnly)
        assertEquals("امیرحسین", dao.getEngineer(3320)?.name)
        assertEquals(203, dao.getOfficeCount())
        assertEquals(1174 + 3905, dao.getEngineerCount())
        assertEquals(null, dao.getEngineer(999999))
    }

    @Test
    fun ghostOfficesExistOnlyAsListOnlyLabels() {
        // 104, 116, 23, 29, 37, 44, 64, 87 have no office row…
        for (ghost in listOf("104", "116", "23", "29", "37", "44", "64", "87")) {
            assertTrue(
                "ghost office $ghost must not be an office row",
                parsed.offices.none { it.name == "دفتر $ghost" }
            )
        }
        // …but they survive as office_name labels on list-only engineers.
        assertEquals(
            setOf("دفتر 104", "دفتر 116", "دفتر 23", "دفتر 29", "دفتر 37", "دفتر 44", "دفتر 64", "دفتر 87"),
            parsed.engineersListOnly.mapNotNull { it.officeName }.toSet()
        )
    }

    @Test
    fun offices78And89DoNotExist() {
        assertTrue(parsed.offices.none { it.name == "دفتر 78" || it.name == "دفتر 89" })
        // Only 178 and 189 contain those digits — they are different offices.
        assertEquals(
            setOf("دفتر 178", "دفتر 189"),
            parsed.offices.map { it.name }.filter { it.contains("78") || it.contains("89") }.toSet()
        )
    }

    @Test
    fun listOnlyGradesUnderscoreBecomesEmpty() = runBlocking {
        val dao = FakeDirectoryDao()
        DirectorySnapshotSeeder.seedFromParsed(parsed, dao)
        val withUnderscore = parsed.engineersListOnly.firstOrNull {
            it.designGrade == "_" || it.supervisionGrade == "_" || it.executionGrade == "_"
        }
        assertTrue(withUnderscore != null)
        val seeded = dao.getEngineer(withUnderscore!!.id)!!
        assertFalse(seeded.designGrade == "_")
        assertFalse(seeded.supervisionGrade == "_")
        assertFalse(seeded.executionGrade == "_")
    }
}
