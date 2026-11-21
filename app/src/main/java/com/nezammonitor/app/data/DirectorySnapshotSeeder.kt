package com.nezammonitor.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ==================== DIRECTORY SNAPSHOT SEEDER (v3.11.3) ====================
// The complete Yazd design-offices directory ships INSIDE the app as
// app/src/main/assets/directory-snapshot.json (no official API exists,
// the server rejects many parallel requests, and one-by-one is too
// slow). On first run — and whenever a newer snapshot arrives — the
// Room directory tables are seeded from it with NO network requests,
// so tapping an office number or an engineer name opens instantly from
// local data. Photos are links only, never bundled.
//
// There is NO in-app update anymore (user decision 2026-11-20): the
// user replaces the snapshot itself in a future version. So a newer
// snapshot simply REPLACES the whole directory dataset: the tables are
// wiped and re-seeded. No per-record merge, no "user-freshened"
// tracking — nothing in the app can freshen a record anymore.
// Ghost offices (104, 116, 23, 29, 37, 44, 64, 87) and 78/89 are not
// in the snapshot at all (they have no detail page), so they still
// answer «یافت نشد» — no substitution anywhere.
//
// v3.11.4: the snapshot is in the site's ARABIC script (most engineer
// names, and the offices' city/address/manager fields). ALL directory
// text is converted to Persian AT SEED TIME, so the Room tables
// themselves are Persian — display, search and lookups all use one
// script. Fixed template text is untouched; only directory data. The
// snapshot JSON itself stays as-is in assets; the conversion happens
// while seeding into Room. URLs (detailUrl, photo) are never converted.

object DirectorySnapshotSeeder {

    const val ASSET_NAME = "directory-snapshot.json"

    /** Settings key for the seeded snapshot's version (meta.generated). */
    const val PREF_VERSION_KEY = "directory_snapshot_version"

    // ---- Snapshot JSON shapes (Gson; snake_case keys as in the file) ----

    data class SnapshotMeta(
        val source: String = "",
        val generated: String = ""
    )

    data class SnapshotOffice(
        val id: Int = 0,
        val name: String = "",
        val licence: String = "",
        val manager: String = "",
        val city: String = "",
        val province: String = "",
        @SerializedName("detail_url") val detailUrl: String = "",
        val mobile: String = "",
        val contact: String = "",
        val address: String = "",
        val phone: String = "",
        val fax: String = "",
        val hours: String = "",
        val members: List<Int> = emptyList()
    )

    data class SnapshotHistory(
        val kind: String = "",
        val grade1: String = "",
        val grade2: String = "",
        val grade3: String = ""
    )

    data class SnapshotEducation(
        val degree: String = "",
        val field: String = "",
        val orientation: String = "",
        @SerializedName("university_type") val universityType: String = "",
        val university: String = "",
        val city: String = "",
        val country: String = "",
        val start: String = "",
        val end: String = ""
    )

    data class SnapshotEngineer(
        val id: Int = 0,
        val name: String = "",
        val family: String = "",
        val discipline: String = "",
        val phone: String = "",
        @SerializedName("design_grade") val designGrade: String = "",
        @SerializedName("supervision_grade") val supervisionGrade: String = "",
        @SerializedName("execution_grade") val executionGrade: String = "",
        @SerializedName("licence_issue") val licenceIssue: String = "",
        @SerializedName("licence_validity") val licenceValidity: String = "",
        val photo: String = "",
        @SerializedName("licence_history") val licenceHistory: List<SnapshotHistory> = emptyList(),
        val education: List<SnapshotEducation> = emptyList(),
        @SerializedName("office_id") val officeId: Int = 0,
        @SerializedName("office_name") val officeName: String = ""
    )

    data class SnapshotListEngineer(
        val id: Int = 0,
        val name: String = "",
        val family: String = "",
        val discipline: String = "",
        @SerializedName("design_grade") val designGrade: String = "",
        @SerializedName("supervision_grade") val supervisionGrade: String = "",
        @SerializedName("execution_grade") val executionGrade: String = "",
        @SerializedName("office_name") val officeName: String? = null
    )

    data class SnapshotFile(
        val meta: SnapshotMeta = SnapshotMeta(),
        val offices: List<SnapshotOffice> = emptyList(),
        val engineers: Map<String, SnapshotEngineer> = emptyMap(),
        @SerializedName("engineers_list_only") val engineersListOnly: List<SnapshotListEngineer> = emptyList()
    )

    data class SeedResult(
        val offices: Int,
        val engineers: Int,
        val listOnly: Int,
        val educationRows: Int,
        val historyRows: Int
    )

    sealed class SeedOutcome {
        /** This snapshot version was already seeded. */
        data object AlreadySeeded : SeedOutcome()
        data class Seeded(val version: String, val result: SeedResult) : SeedOutcome()
        data class Failed(val reason: String) : SeedOutcome()
    }

    private val gson = Gson()

    /** The snapshot's version ("yyyy-MM-dd") from the first bytes of
     *  the file — the meta block sits at the top, so the whole 1.9 MB
     *  file never needs to be read just to check the version. */
    internal fun readSnapshotVersion(head: String): String? {
        val match = Regex("\"generated\"\\s*:\\s*\"([^\"]+)\"").find(head)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    /** A grade of "_" in the engineers list means "no grade". */
    internal fun cleanGrade(grade: String): String = if (grade.trim() == "_") "" else grade

    /** v3.11.4: Arabic-script letters to Persian (ي→ی, ك→ک) — applied
     *  to every directory text field at seed time (see the header). */
    internal fun persian(text: String): String = DirectoryParser.toPersianChars(text)

    internal fun parseSnapshot(json: String): SnapshotFile =
        gson.fromJson(json, SnapshotFile::class.java)

    /**
     * Seeds the directory tables from parsed snapshot JSON. The whole
     * dataset is REPLACED: the four tables are wiped first, then every
     * snapshot row is inserted. Pure apart from the DAO, so the unit
     * tests drive it with a fake DAO and the real snapshot file. The
     * version is stored by ensureSeeded only after this returns, so a
     * crash mid-seed simply re-seeds on the next start.
     */
    suspend fun seedFromParsed(snapshot: SnapshotFile, dao: DirectoryDao): SeedResult =
        withContext(Dispatchers.IO) {
            dao.deleteAllEducation()
            dao.deleteAllLicenceHistory()
            dao.deleteAllOffices()
            dao.deleteAllEngineers()

            val officesToSeed = snapshot.offices.filter { it.id != 0 }.map { office ->
                DirectoryOfficeEntity(
                    siteId = office.id,
                    name = persian(office.name),
                    licence = persian(office.licence),
                    manager = persian(office.manager),
                    mobile = persian(office.mobile),
                    contact = persian(office.contact),
                    province = persian(office.province),
                    city = persian(office.city),
                    address = persian(office.address),
                    phone = persian(office.phone),
                    fax = persian(office.fax),
                    hours = persian(office.hours),
                    detailUrl = office.detailUrl,
                    memberCount = office.members.size,
                    updatedAt = ""
                )
            }
            if (officesToSeed.isNotEmpty()) dao.insertOffices(officesToSeed)

            val engineersToSeed = mutableListOf<DirectoryEngineerEntity>()
            val educationRows = mutableListOf<DirectoryEducationEntity>()
            val historyRows = mutableListOf<DirectoryLicenceHistoryEntity>()
            val seededEngineerIds = mutableSetOf<Int>()
            for ((_, eng) in snapshot.engineers) {
                if (eng.id == 0) continue
                engineersToSeed += DirectoryEngineerEntity(
                    siteId = eng.id,
                    name = persian(eng.name),
                    family = persian(eng.family),
                    discipline = persian(eng.discipline),
                    phone = persian(eng.phone),
                    designGrade = persian(eng.designGrade),
                    supervisionGrade = persian(eng.supervisionGrade),
                    executionGrade = persian(eng.executionGrade),
                    licenceIssue = persian(eng.licenceIssue),
                    licenceValidity = persian(eng.licenceValidity),
                    photo = eng.photo,
                    officeId = eng.officeId,
                    officeName = persian(eng.officeName),
                    updatedAt = ""
                )
                seededEngineerIds += eng.id
                educationRows += eng.education.map {
                    DirectoryEducationEntity(
                        engineerId = eng.id,
                        degree = persian(it.degree),
                        field = persian(it.field),
                        orientation = persian(it.orientation),
                        universityType = persian(it.universityType),
                        university = persian(it.university),
                        city = persian(it.city),
                        country = persian(it.country),
                        start = persian(it.start),
                        end = persian(it.end)
                    )
                }
                historyRows += eng.licenceHistory.map {
                    DirectoryLicenceHistoryEntity(
                        engineerId = eng.id,
                        kind = persian(it.kind),
                        grade1 = persian(it.grade1),
                        grade2 = persian(it.grade2),
                        grade3 = persian(it.grade3)
                    )
                }
            }
            // List-only engineers: the full record wins when an id is in
            // both (it cannot normally happen — the two sets are disjoint
            // by construction — but the full detail must never be
            // downgraded to a list-only row).
            var listOnlyCount = 0
            for (eng in snapshot.engineersListOnly) {
                if (eng.id == 0 || eng.id in seededEngineerIds) continue
                engineersToSeed += DirectoryEngineerEntity(
                    siteId = eng.id,
                    name = persian(eng.name),
                    family = persian(eng.family),
                    discipline = persian(eng.discipline),
                    phone = "",
                    designGrade = cleanGrade(persian(eng.designGrade)),
                    supervisionGrade = cleanGrade(persian(eng.supervisionGrade)),
                    executionGrade = cleanGrade(persian(eng.executionGrade)),
                    licenceIssue = "",
                    licenceValidity = "",
                    photo = "",
                    officeId = 0,
                    officeName = persian(eng.officeName ?: ""),
                    updatedAt = ""
                )
                listOnlyCount++
            }
            if (engineersToSeed.isNotEmpty()) dao.insertEngineers(engineersToSeed)
            if (educationRows.isNotEmpty()) dao.insertEducation(educationRows)
            if (historyRows.isNotEmpty()) dao.insertLicenceHistory(historyRows)
            SeedResult(
                offices = officesToSeed.size,
                engineers = seededEngineerIds.size,
                listOnly = listOnlyCount,
                educationRows = educationRows.size,
                historyRows = historyRows.size
            )
        }

    /**
     * Seeds from the bundled asset when its version is newer than the
     * one already seeded (or nothing was seeded yet). Never throws and
     * never touches the network. Safe to call on every app start — the
     * version check reads only the first bytes of the asset and returns
     * early when there is nothing to do.
     */
    suspend fun ensureSeeded(context: Context, dao: DirectoryDao, prefs: SharedPreferences): SeedOutcome {
        return try {
            val head = withContext(Dispatchers.IO) {
                context.assets.open(ASSET_NAME).use { stream ->
                    val buffer = ByteArray(4096)
                    val read = stream.read(buffer)
                    if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
                }
            }
            val version = readSnapshotVersion(head) ?: return SeedOutcome.Failed("نسخهٔ اسنپ‌شات خوانده نشد")
            val stored = prefs.getString(PREF_VERSION_KEY, "") ?: ""
            if (stored == version) return SeedOutcome.AlreadySeeded
            // A stored version NEWER than the asset must never be
            // downgraded (e.g. the asset was replaced by an older one).
            if (stored.isNotBlank() && stored > version) return SeedOutcome.AlreadySeeded
            val json = withContext(Dispatchers.IO) {
                context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).readText()
            }
            val result = seedFromParsed(parseSnapshot(json), dao)
            prefs.edit().putString(PREF_VERSION_KEY, version).apply()
            SeedOutcome.Seeded(version, result)
        } catch (e: Exception) {
            SeedOutcome.Failed(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
        }
    }
}
