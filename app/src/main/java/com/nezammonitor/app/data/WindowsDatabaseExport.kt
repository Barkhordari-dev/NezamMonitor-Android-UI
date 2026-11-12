package com.nezammonitor.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.*

/**
 * Windows database export (v3.8.6): builds a plain (unencrypted) SQLite
 * file in the exact shape the Windows app's LegacyDatabaseImporter expects
 * (NezamMonitorPro.App/Services/LegacyDatabaseImporter.cs), so the user
 * can copy it to Windows and import it from the History/Snapshots screen
 * ("ورود دیتابیس قدیمی").
 *
 * What the importer needs (all verified against its code):
 * - The file must be plain SQLite (Microsoft.Data.Sqlite, read-only, no
 *   password) — Room's own database is NOT used; a fresh file is built
 *   with android.database.sqlite.SQLiteDatabase.
 * - A `Snapshots` table with at least one row (Id + CreatedAt): without a
 *   snapshot, NO case is imported at all.
 * - A `Cases` table with `Id` + `SnapshotId` (INTEGER): every case must
 *   point at a snapshot row in the same file.
 * - Child tables `Specifications`, `Engineers`, `Fees`, `Reports` with
 *   `Id` + `CaseId` (INTEGER): the case ids are re-assigned here
 *   (1..N) and the children are re-keyed to them.
 * - Optional global tables `FollowUpEdits` and `FeeReceipts` (copied
 *   as-is, INSERT OR REPLACE). `Settings` and `GeneratedReports` are
 *   deliberately NOT written: Settings would overwrite the Windows
 *   app's own settings, and GeneratedReports holds Windows-side file
 *   paths that mean nothing on the phone.
 * - Columns are copied BY NAME; a missing column becomes NULL, so the
 *   tables carry exactly the Windows columns.
 */
fun createWindowsDatabaseExport(
    exportsDir: File,
    cases: List<CaseEntity>,
    specs: List<SpecificationEntity>,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    reports: List<ReportEntity>,
    edits: List<FollowUpEditEntity>,
    receipts: List<FeeReceiptEntity>
): Pair<Boolean, String> {
    var db: SQLiteDatabase? = null
    return try {
        val dir = exportsDir
        dir.mkdirs()
        val fileName = "NezamMonitor_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.db"
        val file = File(dir, fileName)
        if (file.exists()) file.delete()

        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.beginTransaction()

        db.execSQL("""
            CREATE TABLE Snapshots (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                CreatedAt TEXT NOT NULL,
                CaseCount INTEGER DEFAULT 0,
                Status TEXT DEFAULT 'pending'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE Cases (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                SnapshotId INTEGER NOT NULL,
                CaseNumber TEXT NOT NULL,
                Serial TEXT DEFAULT '',
                Owner TEXT DEFAULT '',
                OwnerMobile TEXT DEFAULT '',
                Responsibility TEXT DEFAULT '',
                CapacityDate TEXT DEFAULT '',
                Office TEXT DEFAULT '',
                ReportDate1 TEXT DEFAULT '',
                ReportDate2 TEXT DEFAULT '',
                ReportDate3 TEXT DEFAULT '',
                OwnerFather TEXT DEFAULT '',
                OwnerNationalCode TEXT DEFAULT '',
                OwnerAddress TEXT DEFAULT '',
                OwnerZip TEXT DEFAULT '',
                OwnerTel TEXT DEFAULT '',
                OwnerBirthLoc TEXT DEFAULT '',
                ApiDbId TEXT DEFAULT '',
                Archived INTEGER DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE Specifications (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                CaseId INTEGER NOT NULL,
                BuildingGroup TEXT DEFAULT '',
                RenovationCode TEXT DEFAULT '',
                PlanInstructionNo TEXT DEFAULT '',
                PlanInstructionType TEXT DEFAULT '',
                PlanInstructionDate TEXT DEFAULT '',
                StructureType TEXT DEFAULT '',
                BlockTitle TEXT DEFAULT '',
                BlockCount TEXT DEFAULT '',
                Floors TEXT DEFAULT '',
                Units TEXT DEFAULT '',
                Issuer TEXT DEFAULT '',
                PermitNumber TEXT DEFAULT '',
                PermitDate TEXT DEFAULT '',
                ReleaseDate TEXT DEFAULT '',
                LandArea TEXT DEFAULT '',
                ParafArea TEXT DEFAULT '',
                Address TEXT DEFAULT '',
                PlanZone TEXT DEFAULT '',
                UsageType TEXT DEFAULT '',
                CapacityArea TEXT DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE Engineers (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                CaseId INTEGER NOT NULL,
                Discipline TEXT DEFAULT '',
                Name TEXT DEFAULT '',
                Role TEXT DEFAULT '',
                Phone TEXT DEFAULT '',
                DesignLevel TEXT DEFAULT '',
                SupervisionLevel TEXT DEFAULT '',
                ExecutionLevel TEXT DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE Fees (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                CaseId INTEGER NOT NULL,
                Discipline TEXT DEFAULT '',
                ServiceType TEXT DEFAULT '',
                Stage TEXT DEFAULT '',
                StartDate TEXT DEFAULT '',
                EndDate TEXT DEFAULT '',
                Amount TEXT DEFAULT '',
                PayStatus TEXT DEFAULT '',
                ConfirmStatus TEXT DEFAULT '',
                AmountType TEXT DEFAULT '',
                Description TEXT DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE Reports (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                CaseId INTEGER NOT NULL,
                RowNo TEXT DEFAULT '',
                ReportType TEXT DEFAULT '',
                Stage TEXT DEFAULT '',
                Engineer TEXT DEFAULT '',
                Discipline TEXT DEFAULT '',
                VisitDate TEXT DEFAULT '',
                CeilingCount TEXT DEFAULT '',
                Indicator TEXT DEFAULT '',
                HasFile INTEGER DEFAULT 0,
                ImageName TEXT DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE FollowUpEdits (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                CaseNumber TEXT NOT NULL,
                Description TEXT DEFAULT '',
                CaseNumberEdit TEXT DEFAULT '',
                OwnerEdit TEXT DEFAULT '',
                AddressEdit TEXT DEFAULT '',
                OwnerMobileEdit TEXT DEFAULT '',
                CreatedAt TEXT DEFAULT '',
                UpdatedAt TEXT DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE FeeReceipts (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                ReceiptDate TEXT DEFAULT '',
                Amount INTEGER DEFAULT 0,
                RelatedEndDatesRaw TEXT DEFAULT '',
                IsAdvance INTEGER DEFAULT 0,
                FeeSum INTEGER DEFAULT 0,
                Changes TEXT DEFAULT '',
                IsRecentlyUpdated INTEGER DEFAULT 0,
                CreatedAt TEXT DEFAULT '',
                UpdatedAt TEXT DEFAULT ''
            )
        """.trimIndent())

        // One snapshot for the whole export; every case points at it.
        val snapshotValues = ContentValues().apply {
            put("Id", 1)
            put("CreatedAt", OffsetDateTime.now().toString())
            put("CaseCount", cases.size)
            put("Status", "done")
        }
        db.insertOrThrow("Snapshots", null, snapshotValues)

        // Cases, with fresh sequential ids; remember the new id per case.
        // Plain for loop (not forEachIndexed): a lambda would capture the
        // nullable `db` variable and Kotlin refuses to smart-cast it there.
        val caseIdByNumber = mutableMapOf<String, Long>()
        for ((index, c) in cases.withIndex()) {
            val newId = (index + 1).toLong()
            val values = ContentValues().apply {
                put("Id", newId)
                put("SnapshotId", 1)
                put("CaseNumber", c.caseNumber)
                put("Serial", c.serial)
                put("Owner", c.owner)
                put("OwnerMobile", c.ownerMobile)
                put("Responsibility", c.responsibility)
                put("CapacityDate", c.capacityDate)
                put("Office", c.office)
                put("ReportDate1", c.reportDate1)
                put("ReportDate2", c.reportDate2)
                put("ReportDate3", c.reportDate3)
                put("OwnerFather", c.ownerFather)
                put("OwnerNationalCode", c.ownerNationalCode)
                put("OwnerAddress", c.ownerAddress)
                put("OwnerZip", c.ownerZip)
                put("OwnerTel", c.ownerTel)
                put("OwnerBirthLoc", c.ownerBirthLoc)
                put("ApiDbId", c.dbId)
                put("Archived", if (c.archived) 1 else 0)
            }
            db.insertOrThrow("Cases", null, values)
            caseIdByNumber[c.caseNumber] = newId
        }

        // Specifications, re-keyed to the new case ids. A case without a
        // specification row but with its own specification fields filled
        // gets a row synthesised from those fields, so nothing is lost.
        var specId = 1L
        val specCaseNumbers = specs.map { it.caseNumber }.toSet()
        for (s in specs) {
            val caseId = caseIdByNumber[s.caseNumber] ?: continue
            val values = ContentValues().apply {
                put("Id", specId++)
                put("CaseId", caseId)
                put("BuildingGroup", s.buildingGroup)
                put("RenovationCode", s.renovationCode)
                put("PlanInstructionNo", s.planInstructionNo)
                put("PlanInstructionType", s.planInstructionType)
                put("PlanInstructionDate", s.planInstructionDate)
                put("StructureType", s.structureType)
                put("BlockTitle", s.blockTitle)
                put("BlockCount", s.blockCount)
                put("Floors", s.floors)
                put("Units", s.units)
                put("Issuer", s.issuer)
                put("PermitNumber", s.permitNumber)
                put("PermitDate", s.permitDate)
                put("ReleaseDate", s.releaseDate)
                put("LandArea", s.landArea)
                put("ParafArea", s.parafArea)
                put("Address", s.address)
                put("PlanZone", s.planZone)
                put("UsageType", s.usageType)
                put("CapacityArea", s.capacityArea)
            }
            db.insertOrThrow("Specifications", null, values)
        }
        for (c in cases) {
            if (c.caseNumber in specCaseNumbers) continue
            val caseId = caseIdByNumber[c.caseNumber] ?: continue
            if (listOf(c.buildingGroup, c.renovationCode, c.structureType, c.floors, c.units,
                    c.issuer, c.permitNumber, c.permitDate, c.landArea, c.address,
                    c.planZone, c.usageType).all { it.isBlank() }) continue
            val values = ContentValues().apply {
                put("Id", specId++)
                put("CaseId", caseId)
                put("BuildingGroup", c.buildingGroup)
                put("RenovationCode", c.renovationCode)
                put("StructureType", c.structureType)
                put("BlockTitle", c.blockNumber)
                put("Floors", c.floors)
                put("Units", c.units)
                put("Issuer", c.issuer)
                put("PermitNumber", c.permitNumber)
                put("PermitDate", c.permitDate)
                put("LandArea", c.landArea)
                put("Address", c.address)
                put("PlanZone", c.planZone)
                put("UsageType", c.usageType)
            }
            db.insertOrThrow("Specifications", null, values)
        }

        var engineerId = 1L
        for (e in engineers) {
            val caseId = caseIdByNumber[e.caseNumber] ?: continue
            val values = ContentValues().apply {
                put("Id", engineerId++)
                put("CaseId", caseId)
                put("Discipline", e.discipline)
                put("Name", e.name)
                put("Role", e.role)
                put("Phone", e.phone)
                put("DesignLevel", e.designLevel)
                put("SupervisionLevel", e.supervisionLevel)
                put("ExecutionLevel", e.executionLevel)
            }
            db.insertOrThrow("Engineers", null, values)
        }

        var feeId = 1L
        for (f in fees) {
            val caseId = caseIdByNumber[f.caseNumber] ?: continue
            val values = ContentValues().apply {
                put("Id", feeId++)
                put("CaseId", caseId)
                put("Discipline", f.discipline)
                put("ServiceType", f.serviceType)
                put("Stage", f.stage)
                put("StartDate", f.startDate)
                put("EndDate", f.endDate)
                put("Amount", f.amount)
                put("PayStatus", f.payStatus)
                put("ConfirmStatus", f.confirmStatus)
                put("AmountType", f.amountType)
                put("Description", f.description)
            }
            db.insertOrThrow("Fees", null, values)
        }

        var reportId = 1L
        for (r in reports) {
            val caseId = caseIdByNumber[r.caseNumber] ?: continue
            val values = ContentValues().apply {
                put("Id", reportId++)
                put("CaseId", caseId)
                put("RowNo", r.rowNo)
                put("ReportType", r.reportType)
                put("Stage", r.stage)
                put("Engineer", r.engineer)
                put("Discipline", r.discipline)
                put("VisitDate", r.visitDate)
                put("CeilingCount", r.ceilingCount)
                put("Indicator", r.indicator)
                put("HasFile", if (r.hasFile) 1 else 0)
                put("ImageName", r.imageName)
            }
            db.insertOrThrow("Reports", null, values)
        }

        var editId = 1L
        for (e in edits) {
            val values = ContentValues().apply {
                put("Id", editId++)
                put("CaseNumber", e.caseNumber)
                put("Description", e.description)
                put("CaseNumberEdit", e.caseNumberEdit)
                put("OwnerEdit", e.ownerEdit)
                put("AddressEdit", e.addressEdit)
                put("OwnerMobileEdit", e.ownerMobileEdit)
                put("CreatedAt", "")
                put("UpdatedAt", "")
            }
            db.insertOrThrow("FollowUpEdits", null, values)
        }

        var receiptId = 1L
        for (r in receipts) {
            val values = ContentValues().apply {
                put("Id", receiptId++)
                put("ReceiptDate", r.receiptDate)
                put("Amount", r.amount)
                put("RelatedEndDatesRaw", r.relatedEndDatesRaw)
                put("IsAdvance", if (r.isAdvance) 1 else 0)
                put("FeeSum", r.feeSum)
                put("Changes", r.changes)
                put("IsRecentlyUpdated", if (r.isRecentlyUpdated) 1 else 0)
                put("CreatedAt", r.createdAt)
                put("UpdatedAt", r.updatedAt)
            }
            db.insertOrThrow("FeeReceipts", null, values)
        }

        db.setTransactionSuccessful()
        db.endTransaction()
        db.close()
        db = null

        Pair(true, file.absolutePath)
    } catch (e: Throwable) {
        try { db?.endTransaction() } catch (_: Exception) {}
        try { db?.close() } catch (_: Exception) {}
        Pair(false, "خطا: ${e.message}")
    }
}
