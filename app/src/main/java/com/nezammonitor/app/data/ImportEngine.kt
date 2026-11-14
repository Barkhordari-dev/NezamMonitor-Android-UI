package com.nezammonitor.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.google.gson.Gson
import com.nezammonitor.app.ui.screens.reportgen.nowJalaliTimestamp
import com.nezammonitor.app.ui.screens.reportgen.parseEditTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File
import javax.crypto.AEADBadTagException

data class ImportResult(
    val success: Boolean,
    val message: String,
    val casesCount: Int = 0,
    val engineersCount: Int = 0,
    val feesCount: Int = 0,
    val reportsCount: Int = 0,
    val receiptsCount: Int = 0,
    /** True only when the import failed because the package password was
     *  wrong (the GCM tag check failed) — the UI keeps the password
     *  dialog open so the user can retry. */
    val wrongPassword: Boolean = false
)

/** What the manifest says about a package, read without importing it. */
sealed interface PackageInspection {
    data class Ready(val encrypted: Boolean) : PackageInspection
    data class Invalid(val message: String) : PackageInspection
}

class ImportEngine(private val context: Context) {
    private val gson = Gson()
    private val db = NezamDatabase.getDatabase(context)
    private val dao = db.nezamDao()

    private fun readJsonFile(file: File): String {
        val raw = file.readText(Charsets.UTF_8)
        return if (raw.isNotEmpty() && raw[0] == '\uFEFF') raw.substring(1) else raw
    }

    /** Reads only manifest.json from the package (no data import). Used by
     *  the Home screen to ask for a password BEFORE importing, when the
     *  package is encrypted (SEC-015). */
    suspend fun inspectPackage(uri: Uri): PackageInspection = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "import_temp.nzmdata")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext PackageInspection.Invalid("Could not read file")
            val zipFile = ZipFile(tempFile)
            val header = zipFile.getFileHeader("manifest.json")
                ?: return@withContext PackageInspection.Invalid("Missing manifest.json")
            val manifestJson = zipFile.getInputStream(header).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .let { if (it.isNotEmpty() && it[0] == '\uFEFF') it.substring(1) else it }
            val manifest = gson.fromJson(manifestJson, ManifestData::class.java)
            if (manifest.format != "NezamMonitor.AndroidData") {
                return@withContext PackageInspection.Invalid("Invalid format: ${manifest.format}")
            }
            PackageInspection.Ready(manifest.encrypted)
        } catch (e: Exception) {
            PackageInspection.Invalid("خطا: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Imports a .nzmdata package. When [updateArchived] is false (the
     *  "بروزرسانی پرونده‌های بایگانی‌شده" checkbox in the Update menu),
     *  archived cases are left untouched: their rows are not deleted and
     *  their data is not re-inserted. When true, they update like the rest
     *  but keep their archived flag.
     *
     *  v2 rules (Windows v12, formatVersion "2"):
     *  - archived = local flag OR file flag; an import NEVER deletes an
     *    archived row (with updateArchived=true, archived rows missing
     *    from the file are stashed and restored after the wipe);
     *  - follow-up edits: last writer wins by updatedAt — the file wins
     *    on a tie (the file is Windows) and when a timestamp is missing
     *    or unparseable (fail open, same as Windows' FileEditWins);
     *  - dbId is backfilled only when the local row has none;
     *  - imageName is stored, so the report photo can be downloaded from
     *    the history (ReportGeneratorScreen already reads it);
     *  - receipts are replaced ONLY when the file actually has receipts
     *    (an empty list must not wipe the local receipts);
     *  - manifest.checksum (SHA-256 over the stored data.json bytes) is
     *    validated before the data is used (W4);
     *  - when the manifest says encrypted, [password] decrypts data.json
     *    (AES-256-GCM, PBKDF2-SHA256 600k — NzmdataCrypto); a wrong
     *    password is reported as "رمز اشتباه است". */
    suspend fun importFromUri(uri: Uri, updateArchived: Boolean = false, password: String? = null): ImportResult = withContext(Dispatchers.IO) {
        try {
            val tempFile = File(context.cacheDir, "import_temp.nzmdata")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext ImportResult(false, "Could not read file")

            val zipFile = ZipFile(tempFile)
            val tempDir = File(context.cacheDir, "import_extract")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()
            zipFile.extractAll(tempDir.absolutePath)

            fun cleanup() {
                tempFile.delete()
                tempDir.deleteRecursively()
            }

            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                cleanup()
                return@withContext ImportResult(false, "Missing manifest.json")
            }

            val manifest = gson.fromJson(readJsonFile(manifestFile), ManifestData::class.java)
            if (manifest.format != "NezamMonitor.AndroidData") {
                cleanup()
                return@withContext ImportResult(false, "Invalid format: ${manifest.format}")
            }

            val dataFile = File(tempDir, "data.json")
            if (!dataFile.exists()) {
                cleanup()
                return@withContext ImportResult(false, "Missing data.json")
            }

            // W4: SHA-256 over the STORED data.json bytes must match the
            // manifest checksum. For an encrypted package the stored bytes
            // are the ciphertext, so this runs before decryption.
            val dataBytes = dataFile.readBytes()
            if (manifest.checksum.isNotBlank()) {
                val computed = NzmdataCrypto.sha256Hex(dataBytes)
                if (!computed.equals(manifest.checksum, ignoreCase = true)) {
                    cleanup()
                    return@withContext ImportResult(false, "بررسی صحت فایل ناموفق بود — جمع‌بندی (checksum) مانیفست با داده‌ها نمی‌خواند؛ فایل خراب است")
                }
            }

            // SEC-015: decrypt the stored bytes when the manifest says so.
            val dataJson: String = if (manifest.encrypted) {
                if (password.isNullOrBlank()) {
                    cleanup()
                    return@withContext ImportResult(false, "این بسته رمز دارد — رمز را وارد کنید")
                }
                val salt: ByteArray
                val nonce: ByteArray
                try {
                    salt = Base64.decode(manifest.salt, Base64.DEFAULT)
                    nonce = Base64.decode(manifest.nonce, Base64.DEFAULT)
                } catch (_: IllegalArgumentException) {
                    cleanup()
                    return@withContext ImportResult(false, "بسته رمزنگاری‌شده خراب است")
                }
                if (salt.size != NzmdataCrypto.SALT_BYTES || nonce.size != NzmdataCrypto.NONCE_BYTES) {
                    cleanup()
                    return@withContext ImportResult(false, "بسته رمزنگاری‌شده خراب است")
                }
                try {
                    String(NzmdataCrypto.decrypt(dataBytes, password, salt, nonce), Charsets.UTF_8)
                } catch (_: AEADBadTagException) {
                    cleanup()
                    return@withContext ImportResult(false, "رمز اشتباه است", wrongPassword = true)
                } catch (_: Exception) {
                    cleanup()
                    return@withContext ImportResult(false, "بسته رمزنگاری‌شده خراب است")
                }
            } else {
                String(dataBytes, Charsets.UTF_8)
                    .let { if (it.isNotEmpty() && it[0] == '\uFEFF') it.substring(1) else it }
            }

            val exportData = gson.fromJson(dataJson, ExportData::class.java)

            if (exportData.cases.isEmpty()) {
                cleanup()
                return@withContext ImportResult(false, "Package contains no cases")
            }

            val archivedNumbers = dao.getArchivedCaseNumbers()
            val archivedSet = archivedNumbers.toSet()
            // Local state the merge rules need, read BEFORE the wipe:
            // the local dbId per case (backfill only when blank) and the
            // local follow-up edits (last-writer-wins).
            val localDbIds = dao.getAllCases().first().associate { it.caseNumber to it.dbId }
            val localEditsByCase = dao.getAllFollowUpEdits().first().associateBy { it.caseNumber }
            // With updateArchived=true the wipe would delete archived rows
            // that are missing from the file — stash them (cases + children)
            // and restore them after the insert. Edits are covered by the
            // follow-up merge below, so they are not stashed here.
            val archivedStash: List<ArchivedStash> = if (updateArchived && archivedNumbers.isNotEmpty()) {
                dao.getArchivedCases().first().map { c ->
                    ArchivedStash(
                        caseEntity = c,
                        spec = dao.getSpecification(c.caseNumber),
                        engineers = dao.getEngineersForCase(c.caseNumber),
                        fees = dao.getFeesForCase(c.caseNumber),
                        reports = dao.getReportsForCase(c.caseNumber)
                    )
                }
            } else emptyList()

            val importCases = if (updateArchived) exportData.cases
                else exportData.cases.filter { it.caseNumber !in archivedSet }
            val fileCaseNumbers = importCases.map { it.caseNumber }.toSet()
            val nowStamp = nowJalaliTimestamp()

            db.withTransaction {
                if (updateArchived || archivedNumbers.isEmpty()) {
                    dao.deleteAllFollowUpEdits()
                    dao.deleteAllReports()
                    dao.deleteAllFees()
                    dao.deleteAllEngineers()
                    dao.deleteAllSpecifications()
                    dao.deleteAllCases()
                } else {
                    dao.deleteFollowUpEditsExceptArchived(archivedNumbers)
                    dao.deleteReportsExceptArchived(archivedNumbers)
                    dao.deleteFeesExceptArchived(archivedNumbers)
                    dao.deleteEngineersExceptArchived(archivedNumbers)
                    dao.deleteSpecificationsExceptArchived(archivedNumbers)
                    dao.deleteNonArchivedCases()
                }

                val cases = importCases.map { ec ->
                    CaseEntity(
                        caseNumber = ec.caseNumber, serial = ec.serial,
                        owner = ec.owner, ownerMobile = ec.ownerMobile,
                        responsibility = ec.responsibility, capacityDate = ec.capacityDate,
                        office = ec.office, reportDate1 = ec.reportDate1,
                        reportDate2 = ec.reportDate2, reportDate3 = ec.reportDate3,
                        ownerFather = ec.ownerFather, ownerNationalCode = ec.ownerNationalCode,
                        ownerAddress = ec.ownerAddress, ownerZip = ec.ownerZip,
                        ownerTel = ec.ownerTel, ownerBirthLoc = ec.ownerBirthLoc,
                        permitNumber = ec.permitNumber, permitDate = ec.permitDate,
                        renovationCode = ec.renovationCode, address = ec.address,
                        blockNumber = ec.blockNumber, landArea = ec.landArea,
                        municipalArea = ec.municipalArea, effectiveArea = ec.effectiveArea,
                        floors = ec.floors, units = ec.units,
                        buildingGroup = ec.buildingGroup, structureType = ec.structureType,
                        usageType = ec.usageType, planZone = ec.planZone,
                        issuer = ec.issuer, stateTitle = ec.stateTitle,
                        workType = ec.workType, discipline = ec.discipline,
                        unitTitle = ec.unitTitle, orderPart = ec.orderPart,
                        executionDate = ec.executionDate,
                        // v2: dbId is backfilled only when the local row
                        // has none; archived is local flag OR file flag.
                        dbId = localDbIds[ec.caseNumber]?.takeIf { it.isNotBlank() } ?: ec.dbId,
                        archived = ec.caseNumber in archivedSet || ec.archived
                    )
                }
                dao.insertCases(cases)

                val specs = importCases.filter { it.specification != null }.map { ec ->
                    val s = ec.specification!!
                    SpecificationEntity(
                        caseNumber = ec.caseNumber, address = s.address,
                        buildingGroup = s.buildingGroup, renovationCode = s.renovationCode,
                        planInstructionNo = s.planInstructionNo,
                        planInstructionType = s.planInstructionType,
                        planInstructionDate = s.planInstructionDate,
                        landArea = s.landArea, parafArea = s.parafArea,
                        capacityArea = s.capacityArea, structureType = s.structureType,
                        blockTitle = s.blockTitle, blockCount = s.blockCount,
                        floors = s.floors, units = s.units, issuer = s.issuer,
                        permitNumber = s.permitNumber, permitDate = s.permitDate,
                        releaseDate = s.releaseDate, planZone = s.planZone,
                        usageType = s.usageType
                    )
                }
                dao.insertSpecifications(specs)

                val engineers = importCases.flatMap { ec ->
                    ec.engineers.map { ee ->
                        EngineerEntity(caseNumber = ec.caseNumber, discipline = ee.discipline, name = ee.name, role = ee.role, phone = ee.phone, designLevel = ee.designLevel, supervisionLevel = ee.supervisionLevel, executionLevel = ee.executionLevel)
                    }
                }
                dao.insertEngineers(engineers)

                val fees = importCases.flatMap { ec ->
                    ec.fees.map { ef ->
                        // The Windows export stores amountType as the raw code
                        // ("n"/"b"/"ho"/"t"/"pn") and confirmStatus sometimes as a bare
                        // number ("85") — normalize both on import so the stored data
                        // is display-ready (display code maps them too, for older imports).
                        val mappedAmountType = when (ef.amountType.trim().lowercase()) {
                            "n" -> "نظارت"
                            "b" -> "بیمه"
                            "ho" -> "حسن انجام کار"
                            "t" -> "مالیات نظارت سهم سازمان"
                            "pn" -> "نظارت سهم سازمان"
                            else -> ef.amountType
                        }
                        val mappedConfirm = if (ef.confirmStatus.trim() == "تایید شده" || ef.confirmStatus.trim() == "1") "تایید شده" else ""
                        FeeEntity(
                            caseNumber = ec.caseNumber, discipline = ef.discipline,
                            serviceType = ef.serviceType, stage = ef.stage,
                            startDate = ef.startDate, endDate = ef.endDate,
                            amount = ef.amount, payStatus = ef.payStatus,
                            confirmStatus = mappedConfirm, amountType = mappedAmountType,
                            description = ef.description
                        )
                    }
                }
                dao.insertFees(fees)

                val reports = importCases.flatMap { ec ->
                    ec.reports.map { er ->
                        ReportEntity(
                            caseNumber = ec.caseNumber, rowNo = er.rowNo,
                            reportType = er.reportType, stage = er.stage,
                            engineer = er.engineer, discipline = er.discipline,
                            visitDate = er.visitDate, ceilingCount = er.ceilingCount,
                            indicator = er.indicator, hasFile = er.hasFile,
                            // v2: the server file name — the history uses it
                            // to download the report photo.
                            imageName = er.imageName
                        )
                    }
                }
                dao.insertReports(reports)

                // Archived rows missing from the file must survive the wipe.
                for (stashed in archivedStash) {
                    if (stashed.caseEntity.caseNumber in fileCaseNumbers) continue
                    dao.insertCases(listOf(stashed.caseEntity))
                    stashed.spec?.let { dao.insertSpecifications(listOf(it)) }
                    if (stashed.engineers.isNotEmpty()) dao.insertEngineers(stashed.engineers)
                    if (stashed.fees.isNotEmpty()) dao.insertFees(stashed.fees)
                    if (stashed.reports.isNotEmpty()) dao.insertReports(stashed.reports)
                }

                // Follow-up edits: last writer wins by updatedAt. The file
                // is Windows, so on a tie the FILE wins; when a timestamp
                // is missing or unparseable the file wins too (fail open,
                // the same rule Windows' FileEditWins uses). A local edit
                // the wipe deleted and the file does not mention is put
                // back; an archived edit the wipe never touched is left
                // alone (insertFollowUpEdit is REPLACE, so re-inserting
                // would be harmless, but skipping it is cleaner).
                val fileEditCases = mutableSetOf<String>()
                val winners = mutableListOf<FollowUpEditEntity>()
                for (ec in importCases) {
                    val f = ec.followUpEdit ?: continue
                    fileEditCases.add(ec.caseNumber)
                    val local = localEditsByCase[ec.caseNumber]
                    if (local == null || fileEditWins(f.updatedAt, local.updatedAt)) {
                        winners.add(
                            FollowUpEditEntity(
                                caseNumber = ec.caseNumber, description = f.description,
                                caseNumberEdit = f.caseNumberEdit, ownerEdit = f.ownerEdit,
                                addressEdit = f.addressEdit, ownerMobileEdit = f.ownerMobileEdit,
                                createdAt = f.createdAt.ifBlank { local?.createdAt ?: "" },
                                updatedAt = f.updatedAt.ifBlank { nowStamp }
                            )
                        )
                    } else {
                        winners.add(local)
                    }
                }
                val deletedEditCases = if (updateArchived) localEditsByCase.keys
                    else localEditsByCase.keys - archivedSet
                for ((cn, local) in localEditsByCase) {
                    if (cn !in fileEditCases && cn in deletedEditCases) winners.add(local)
                }
                winners.forEach { dao.insertFollowUpEdit(it) }

                // FeeReceipts (اختیاری): replace ONLY when the file
                // actually has receipts — an empty list must not wipe
                // the local receipts.
                val feeReceiptsRaw = exportData.feeReceipts
                if (feeReceiptsRaw.isNotEmpty()) {
                    dao.deleteAllFeeReceipts()
                    val receipts = feeReceiptsRaw.map { fr ->
                        FeeReceiptEntity(receiptDate = fr.receiptDate, amount = fr.amount, relatedEndDatesRaw = fr.relatedEndDatesRaw, isAdvance = fr.isAdvance, feeSum = fr.feeSum, changes = fr.changes, isRecentlyUpdated = fr.isRecentlyUpdated, createdAt = fr.createdAt, updatedAt = fr.updatedAt)
                    }
                    dao.insertFeeReceipts(receipts)
                }
            }

            // Permanent hidden-menu overrides: the import just rewrote every
            // row, so re-apply them now. A failure here must not fail the
            // import itself (the data was imported; the overrides retry on
            // the next sync/import).
            try { applyHiddenOverrides(dao) } catch (_: Exception) {}

            val casesCount = importCases.size
            val engineersCount = importCases.sumOf { it.engineers.size }
            val feesCount = importCases.sumOf { it.fees.size }
            val reportsCount = importCases.sumOf { it.reports.size }
            val receiptsCount = exportData.feeReceipts.size
            val msg = if (receiptsCount > 0) "$casesCount پرونده، $engineersCount مهندس، $feesCount حق\u200Cالزحمه، $reportsCount گزارش، $receiptsCount دریافتی وارد شد" else "$casesCount پرونده، $engineersCount مهندس، $feesCount حق\u200Cالزحمه، $reportsCount گزارش وارد شد"
            val counts = ImportResult(true, msg, casesCount, engineersCount, feesCount, reportsCount, receiptsCount)

            cleanup()

            counts
        } catch (e: Exception) {
            ImportResult(false, "خطا: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Last-writer-wins for one follow-up edit. The file wins when its
     *  updatedAt is strictly newer, on a tie (the file is Windows), and
     *  when either timestamp is missing or unparseable (fail open). */
    private fun fileEditWins(fileUpdatedAt: String, localUpdatedAt: String): Boolean {
        val file = parseEditTimestamp(fileUpdatedAt)
        val local = parseEditTimestamp(localUpdatedAt)
        if (file == null || local == null) return true
        return file >= local
    }

    /** One archived case stashed before the wipe, with its children. */
    private data class ArchivedStash(
        val caseEntity: CaseEntity,
        val spec: SpecificationEntity?,
        val engineers: List<EngineerEntity>,
        val fees: List<FeeEntity>,
        val reports: List<ReportEntity>
    )
}

data class ManifestData(
    val format: String = "", val version: String = "", val formatVersion: String = "",
    val createdAt: String = "", val sourceApplication: String = "",
    val sourceApplicationVersion: String = "", val recordCount: Int = 0,
    val schemaVersion: String = "", val checksum: String = "",
    val exportedEntities: List<String> = emptyList(),
    // SEC-015: true when the stored data.json bytes are encrypted.
    val encrypted: Boolean = false, val encryption: String = "",
    val salt: String = "", val nonce: String = ""
)

data class ExportData(val cases: List<ExportCase> = emptyList(), val feeReceipts: List<ExportFeeReceipt> = emptyList())

data class ExportCase(
    val caseNumber: String = "", val serial: String = "", val owner: String = "",
    val ownerMobile: String = "", val responsibility: String = "",
    val capacityDate: String = "", val office: String = "",
    val reportDate1: String = "", val reportDate2: String = "", val reportDate3: String = "",
    val ownerFather: String = "", val ownerNationalCode: String = "", val ownerAddress: String = "", val ownerZip: String = "", val ownerTel: String = "", val ownerBirthLoc: String = "",
    val permitNumber: String = "", val permitDate: String = "", val renovationCode: String = "", val address: String = "", val blockNumber: String = "", val landArea: String = "", val municipalArea: String = "", val effectiveArea: String = "", val floors: String = "", val units: String = "", val buildingGroup: String = "", val structureType: String = "", val usageType: String = "", val planZone: String = "", val issuer: String = "", val stateTitle: String = "", val workType: String = "", val discipline: String = "", val unitTitle: String = "", val orderPart: String = "", val executionDate: String = "", val dbId: String = "",
    // v2: the archive flag rides along (archived = local OR file).
    val archived: Boolean = false,
    val specification: ExportSpecification? = null,
    val engineers: List<ExportEngineer> = emptyList(),
    val fees: List<ExportFee> = emptyList(),
    val reports: List<ExportReport> = emptyList(),
    val followUpEdit: ExportFollowUpEdit? = null
)

data class ExportSpecification(
    val address: String = "", val buildingGroup: String = "",
    val renovationCode: String = "", val planInstructionNo: String = "",
    val planInstructionType: String = "", val planInstructionDate: String = "",
    val landArea: String = "", val parafArea: String = "",
    val capacityArea: String = "", val structureType: String = "",
    val blockTitle: String = "", val blockCount: String = "",
    val floors: String = "", val units: String = "", val issuer: String = "",
    val permitNumber: String = "", val permitDate: String = "",
    val releaseDate: String = "", val planZone: String = "",
    val usageType: String = ""
)

data class ExportEngineer(val discipline: String = "", val name: String = "", val role: String = "", val phone: String = "", val designLevel: String = "", val supervisionLevel: String = "", val executionLevel: String = "")

data class ExportFee(
    val discipline: String = "", val serviceType: String = "", val stage: String = "",
    val startDate: String = "", val endDate: String = "", val amount: String = "",
    val payStatus: String = "", val confirmStatus: String = "", val amountType: String = "",
    val description: String = ""
)

data class ExportReport(
    val rowNo: String = "", val reportType: String = "", val stage: String = "",
    val engineer: String = "", val discipline: String = "", val visitDate: String = "",
    val ceilingCount: String = "", val indicator: String = "", val hasFile: Boolean = false,
    // v2: the server file name, used to download the report photo.
    val imageName: String = ""
)

data class ExportFollowUpEdit(
    val description: String = "", val caseNumberEdit: String = "",
    val ownerEdit: String = "", val addressEdit: String = "",
    val ownerMobileEdit: String = "",
    // v2: last-writer-wins timestamps.
    val createdAt: String = "", val updatedAt: String = ""
)

data class ExportFeeReceipt(
    val receiptDate: String = "", val amount: Long = 0L,
    val relatedEndDatesRaw: String = "", val isAdvance: Boolean = false,
    val feeSum: Long = 0L, val changes: String = "",
    val isRecentlyUpdated: Boolean = false,
    val createdAt: String = "", val updatedAt: String = ""
)
