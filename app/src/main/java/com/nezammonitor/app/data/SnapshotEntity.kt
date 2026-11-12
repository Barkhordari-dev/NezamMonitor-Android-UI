package com.nezammonitor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import kotlinx.coroutines.flow.first

/**
 * One update snapshot: the full synced dataset (cases, specifications,
 * engineers, fees, reports) as it was right after a sync or an import.
 * The Changes menu (Snapshots) lists these, shows what changed between
 * two of them, merges several of them into the live data, and deletes
 * old ones. Follow-up edits and fee receipts are local/global data, not
 * update data, so they are not snapshotted.
 */
@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Jalali date + time, e.g. "1405/08/21 14:30". */
    val createdAt: String = "",
    /** e.g. "بروزرسانی از سامانه" / "ایمپورت از رایانه". */
    val label: String = "",
    /** SnapshotData as JSON. */
    val dataJson: String = "",
    val caseCount: Int = 0,
    val feeCount: Int = 0,
    val reportCount: Int = 0
)

/** The snapshotted dataset (plain data, serialized with Gson). */
data class SnapshotData(
    val cases: List<CaseEntity> = emptyList(),
    val specs: List<SpecificationEntity> = emptyList(),
    val engineers: List<EngineerEntity> = emptyList(),
    val fees: List<FeeEntity> = emptyList(),
    val reports: List<ReportEntity> = emptyList()
)

object SnapshotStore {
    private val gson = Gson()

    fun toJson(data: SnapshotData): String = gson.toJson(data)

    fun fromJson(json: String): SnapshotData? = try {
        gson.fromJson(json, SnapshotData::class.java) ?: SnapshotData()
    } catch (_: Exception) {
        null
    }

    /** Save a snapshot of the current dataset (called after a sync or an
     *  import). The snapshot holds the full DB state, including archived
     *  cases, so a merge can restore them. */
    suspend fun saveSnapshot(dao: NezamDao, label: String, createdAt: String): SnapshotEntity? {
        return try {
            val data = SnapshotData(
                cases = dao.getAllCases().first(),
                specs = dao.getAllSpecifications().first(),
                engineers = dao.getAllEngineers().first(),
                fees = dao.getAllFees().first(),
                reports = dao.getAllReports().first()
            )
            val entity = SnapshotEntity(
                createdAt = createdAt, label = label, dataJson = toJson(data),
                caseCount = data.cases.size, feeCount = data.fees.size, reportCount = data.reports.size
            )
            val id = dao.insertSnapshot(entity)
            entity.copy(id = id)
        } catch (_: Exception) {
            null
        }
    }
}
