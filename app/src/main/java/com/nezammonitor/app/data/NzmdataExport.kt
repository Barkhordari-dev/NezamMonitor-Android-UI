package com.nezammonitor.app.data

import com.google.gson.GsonBuilder
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Writes a .nzmdata package in the Windows format (formatVersion "2"),
 *  the mirror of AndroidExportEngine on Windows, so Windows (History →
 *  import) and another Android phone (Home → import) can read it.
 *  The JSON shape is exactly the Windows export shape (camelCase, the
 *  same ExportCase / ExportReport / ExportFollowUpEdit fields), so the
 *  file is byte-compatible with a Windows export.
 *
 *  [password] is optional (SEC-015, same as Windows): when blank the
 *  package is plain and the manifest says encrypted=false; when given,
 *  data.json is stored AES-256-GCM encrypted (PBKDF2-SHA256, 600k
 *  iterations — NzmdataCrypto) and the manifest records salt/nonce. The
 *  checksum is always over the stored data.json bytes. */
fun createNzmdataExport(
    exportsDir: File,
    cases: List<CaseEntity>,
    specs: List<SpecificationEntity>,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    reports: List<ReportEntity>,
    edits: List<FollowUpEditEntity>,
    receipts: List<FeeReceiptEntity>,
    password: String?,
    appVersion: String
): Pair<Boolean, String> {
    return try {
        exportsDir.mkdirs()
        val fileName = "NezamMonitor_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.nzmdata"
        val outFile = File(exportsDir, fileName)
        if (outFile.exists()) outFile.delete()

        val specsByCase = specs.groupBy { it.caseNumber }
        val engineersByCase = engineers.groupBy { it.caseNumber }
        val feesByCase = fees.groupBy { it.caseNumber }
        val reportsByCase = reports.groupBy { it.caseNumber }
        val editsByCase = edits.associateBy { it.caseNumber }

        val exportCases = cases.map { c ->
            ExportCase(
                caseNumber = c.caseNumber, serial = c.serial,
                owner = c.owner, ownerMobile = c.ownerMobile,
                responsibility = c.responsibility, capacityDate = c.capacityDate,
                office = c.office, reportDate1 = c.reportDate1,
                reportDate2 = c.reportDate2, reportDate3 = c.reportDate3,
                ownerFather = c.ownerFather, ownerNationalCode = c.ownerNationalCode,
                ownerAddress = c.ownerAddress, ownerZip = c.ownerZip,
                ownerTel = c.ownerTel, ownerBirthLoc = c.ownerBirthLoc,
                permitNumber = c.permitNumber, permitDate = c.permitDate,
                renovationCode = c.renovationCode, address = c.address,
                blockNumber = c.blockNumber, landArea = c.landArea,
                municipalArea = c.municipalArea, effectiveArea = c.effectiveArea,
                floors = c.floors, units = c.units,
                buildingGroup = c.buildingGroup, structureType = c.structureType,
                usageType = c.usageType, planZone = c.planZone,
                issuer = c.issuer, stateTitle = c.stateTitle,
                workType = c.workType, discipline = c.discipline,
                unitTitle = c.unitTitle, orderPart = c.orderPart,
                executionDate = c.executionDate, dbId = c.dbId,
                archived = c.archived,
                specification = specsByCase[c.caseNumber]?.firstOrNull()?.let { s ->
                    ExportSpecification(
                        address = s.address, buildingGroup = s.buildingGroup,
                        renovationCode = s.renovationCode,
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
                },
                engineers = (engineersByCase[c.caseNumber] ?: emptyList()).map { e ->
                    ExportEngineer(
                        discipline = e.discipline, name = e.name, role = e.role,
                        phone = e.phone, designLevel = e.designLevel,
                        supervisionLevel = e.supervisionLevel, executionLevel = e.executionLevel
                    )
                },
                fees = (feesByCase[c.caseNumber] ?: emptyList()).map { f ->
                    ExportFee(
                        discipline = f.discipline, serviceType = f.serviceType,
                        stage = f.stage, startDate = f.startDate, endDate = f.endDate,
                        amount = f.amount, payStatus = f.payStatus,
                        confirmStatus = f.confirmStatus,
                        // The Windows format stores the raw code, not the
                        // Persian display text (the import maps it back).
                        amountType = amountTypeToCode(f.amountType),
                        description = f.description
                    )
                },
                reports = (reportsByCase[c.caseNumber] ?: emptyList()).map { r ->
                    ExportReport(
                        rowNo = r.rowNo, reportType = r.reportType, stage = r.stage,
                        engineer = r.engineer, discipline = r.discipline,
                        visitDate = r.visitDate, ceilingCount = r.ceilingCount,
                        indicator = r.indicator, hasFile = r.hasFile,
                        imageName = r.imageName
                    )
                },
                followUpEdit = editsByCase[c.caseNumber]?.let { e ->
                    ExportFollowUpEdit(
                        description = e.description, caseNumberEdit = e.caseNumberEdit,
                        ownerEdit = e.ownerEdit, addressEdit = e.addressEdit,
                        ownerMobileEdit = e.ownerMobileEdit,
                        createdAt = e.createdAt, updatedAt = e.updatedAt
                    )
                }
            )
        }

        val exportReceipts = receipts.map { r ->
            ExportFeeReceipt(
                receiptDate = r.receiptDate, amount = r.amount,
                relatedEndDatesRaw = r.relatedEndDatesRaw, isAdvance = r.isAdvance,
                feeSum = r.feeSum, changes = r.changes,
                isRecentlyUpdated = r.isRecentlyUpdated,
                createdAt = r.createdAt, updatedAt = r.updatedAt
            )
        }

        val gson = GsonBuilder().setPrettyPrinting().create()
        val dataJson = gson.toJson(ExportData(exportCases, exportReceipts))
        var dataBytes = dataJson.toByteArray(Charsets.UTF_8)

        // SEC-015: optional encryption of the stored data.json bytes.
        val encrypted = !password.isNullOrBlank()
        var salt = ByteArray(0)
        var nonce = ByteArray(0)
        if (encrypted) {
            salt = NzmdataCrypto.randomBytes(NzmdataCrypto.SALT_BYTES)
            nonce = NzmdataCrypto.randomBytes(NzmdataCrypto.NONCE_BYTES)
            dataBytes = NzmdataCrypto.encrypt(dataBytes, password!!, salt, nonce)
        }

        val nowIso = java.time.Instant.now().toString()
        val manifest = ManifestData(
            format = "NezamMonitor.AndroidData", version = "1.0", formatVersion = "2",
            createdAt = nowIso, sourceApplication = "NezamMonitor Android",
            sourceApplicationVersion = appVersion, recordCount = cases.size,
            schemaVersion = "1",
            checksum = NzmdataCrypto.sha256Hex(dataBytes),
            encrypted = encrypted,
            encryption = if (encrypted) NzmdataCrypto.ENCRYPTION_ID else "",
            salt = if (encrypted) Base64.getEncoder().encodeToString(salt) else "",
            nonce = if (encrypted) Base64.getEncoder().encodeToString(nonce) else "",
            exportedEntities = listOf(
                "cases", "specifications", "engineers", "fees",
                "reports", "followUpEdits", "feeReceipts"
            )
        )
        val manifestJson = gson.toJson(manifest)
        val metadataJson = gson.toJson(
            mapOf(
                "versionApp" to appVersion,
                "versionPlatform" to "Android",
                "exportTime" to nowIso,
                "currency" to "IRR",
                "dateStandard" to "Persian Solar Hijri",
                "totalCases" to cases.size,
                "totalEngineers" to engineers.size,
                "totalFees" to fees.size,
                "totalReports" to reports.size
            )
        )

        val zip = ZipFile(outFile)
        fun addEntry(name: String, bytes: ByteArray) {
            val p = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                compressionLevel = CompressionLevel.NORMAL
                fileNameInZip = name
            }
            zip.addStream(ByteArrayInputStream(bytes), p)
        }
        addEntry("manifest.json", manifestJson.toByteArray(Charsets.UTF_8))
        addEntry("data.json", dataBytes)
        addEntry("metadata.json", metadataJson.toByteArray(Charsets.UTF_8))
        addEntry("attachments/.gitkeep", ByteArray(0))

        Pair(true, outFile.absolutePath)
    } catch (e: Throwable) {
        Pair(false, "خطا: ${e.message}")
    }
}

/** The reverse of the import mapping: the Persian display text back to
 *  the raw amount-type code the Windows format stores. Unknown values
 *  pass through unchanged. */
private fun amountTypeToCode(persian: String): String = when (persian.trim()) {
    "نظارت" -> "n"
    "بیمه" -> "b"
    "حسن انجام کار" -> "ho"
    "مالیات نظارت سهم سازمان" -> "t"
    "نظارت سهم سازمان" -> "pn"
    else -> persian
}
