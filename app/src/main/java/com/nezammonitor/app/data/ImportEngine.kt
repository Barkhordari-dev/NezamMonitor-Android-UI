package com.nezammonitor.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

data class ImportResult(
    val success: Boolean,
    val message: String,
    val casesCount: Int = 0,
    val engineersCount: Int = 0,
    val feesCount: Int = 0,
    val reportsCount: Int = 0,
    val receiptsCount: Int = 0
)

class ImportEngine(private val context: Context) {
    private val gson = Gson()
    private val db = NezamDatabase.getDatabase(context)
    private val dao = db.nezamDao()
    
    private fun readJsonFile(file: File): String {
        val raw = file.readText(Charsets.UTF_8)
        return if (raw.isNotEmpty() && raw[0] == '\uFEFF') raw.substring(1) else raw
    }
    
    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
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
            
            val manifestFile = File(tempDir, "manifest.json")
            if (!manifestFile.exists()) {
                tempFile.delete()
                tempDir.deleteRecursively()
                return@withContext ImportResult(false, "Missing manifest.json")
            }
            
            val manifest = gson.fromJson(readJsonFile(manifestFile), ManifestData::class.java)
            if (manifest.format != "NezamMonitor.AndroidData") {
                tempFile.delete()
                tempDir.deleteRecursively()
                return@withContext ImportResult(false, "Invalid format: ${manifest.format}")
            }
            
            val dataFile = File(tempDir, "data.json")
            if (!dataFile.exists()) {
                tempFile.delete()
                tempDir.deleteRecursively()
                return@withContext ImportResult(false, "Missing data.json")
            }
            
            val exportData = gson.fromJson(readJsonFile(dataFile), ExportData::class.java)
            
            if (exportData.cases.isEmpty()) {
                tempFile.delete()
                tempDir.deleteRecursively()
                return@withContext ImportResult(false, "Package contains no cases")
            }
            
            db.withTransaction {
                dao.deleteAllFollowUpEdits()
                dao.deleteAllReports()
                dao.deleteAllFees()
                dao.deleteAllEngineers()
                dao.deleteAllSpecifications()
                dao.deleteAllCases()
                try { dao.deleteAllFeeReceipts() } catch (_: Exception) {}
                
                val cases = exportData.cases.map { ec ->
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
                        executionDate = ec.executionDate, dbId = ec.dbId
                    )
                }
                dao.insertCases(cases)
                
                val specs = exportData.cases.filter { it.specification != null }.map { ec ->
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
                
                val engineers = exportData.cases.flatMap { ec ->
                    ec.engineers.map { ee ->
                        EngineerEntity(caseNumber = ec.caseNumber, discipline = ee.discipline, name = ee.name, role = ee.role, phone = ee.phone, designLevel = ee.designLevel, supervisionLevel = ee.supervisionLevel, executionLevel = ee.executionLevel)
                    }
                }
                dao.insertEngineers(engineers)
                
                val fees = exportData.cases.flatMap { ec ->
                    ec.fees.map { ef ->
                        FeeEntity(
                            caseNumber = ec.caseNumber, discipline = ef.discipline,
                            serviceType = ef.serviceType, stage = ef.stage,
                            startDate = ef.startDate, endDate = ef.endDate,
                            amount = ef.amount, payStatus = ef.payStatus,
                            confirmStatus = ef.confirmStatus, amountType = ef.amountType,
                            description = ef.description
                        )
                    }
                }
                dao.insertFees(fees)
                
                val reports = exportData.cases.flatMap { ec ->
                    ec.reports.map { er ->
                        ReportEntity(
                            caseNumber = ec.caseNumber, rowNo = er.rowNo,
                            reportType = er.reportType, stage = er.stage,
                            engineer = er.engineer, discipline = er.discipline,
                            visitDate = er.visitDate, ceilingCount = er.ceilingCount,
                            indicator = er.indicator, hasFile = er.hasFile
                        )
                    }
                }
                dao.insertReports(reports)
                
                val edits = exportData.cases.filter { it.followUpEdit != null }.map { ec ->
                    val f = ec.followUpEdit!!
                    FollowUpEditEntity(
                        caseNumber = ec.caseNumber, description = f.description,
                        caseNumberEdit = f.caseNumberEdit, ownerEdit = f.ownerEdit,
                        addressEdit = f.addressEdit, ownerMobileEdit = f.ownerMobileEdit
                    )
                }
                edits.forEach { dao.insertFollowUpEdit(it) }
                // FeeReceipts (اختیاری)
                val feeReceiptsRaw = exportData.feeReceipts
                if (feeReceiptsRaw.isNotEmpty()) {
                    val receipts = feeReceiptsRaw.map { fr ->
                        FeeReceiptEntity(receiptDate = fr.receiptDate, amount = fr.amount, relatedEndDatesRaw = fr.relatedEndDatesRaw, isAdvance = fr.isAdvance, feeSum = fr.feeSum, changes = fr.changes, isRecentlyUpdated = fr.isRecentlyUpdated, createdAt = fr.createdAt, updatedAt = fr.updatedAt)
                    }
                    dao.insertFeeReceipts(receipts)
                }
            }
            
            val casesCount = exportData.cases.size
            val engineersCount = exportData.cases.sumOf { it.engineers.size }
            val feesCount = exportData.cases.sumOf { it.fees.size }
            val reportsCount = exportData.cases.sumOf { it.reports.size }
            val receiptsCount = exportData.feeReceipts.size
            val msg = if (receiptsCount > 0) "$casesCount پرونده، $engineersCount مهندس، $feesCount حق\u200Cالزحمه، $reportsCount گزارش، $receiptsCount دریافتی وارد شد" else "$casesCount پرونده، $engineersCount مهندس، $feesCount حق\u200Cالزحمه، $reportsCount گزارش وارد شد"
            val counts = ImportResult(true, msg, casesCount, engineersCount, feesCount, reportsCount, receiptsCount)
            
            tempFile.delete()
            tempDir.deleteRecursively()
            
            counts
        } catch (e: Exception) {
            ImportResult(false, "خطا: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}

data class ManifestData(
    val format: String = "", val version: String = "", val formatVersion: String = "",
    val createdAt: String = "", val sourceApplication: String = "",
    val sourceApplicationVersion: String = "", val recordCount: Int = 0,
    val schemaVersion: String = "", val checksum: String = "",
    val exportedEntities: List<String> = emptyList()
)

data class ExportData(val cases: List<ExportCase> = emptyList(), val feeReceipts: List<ExportFeeReceipt> = emptyList())

data class ExportCase(
    val caseNumber: String = "", val serial: String = "", val owner: String = "",
    val ownerMobile: String = "", val responsibility: String = "",
    val capacityDate: String = "", val office: String = "",
    val reportDate1: String = "", val reportDate2: String = "", val reportDate3: String = "",
    val ownerFather: String = "", val ownerNationalCode: String = "", val ownerAddress: String = "", val ownerZip: String = "", val ownerTel: String = "", val ownerBirthLoc: String = "",
    val permitNumber: String = "", val permitDate: String = "", val renovationCode: String = "", val address: String = "", val blockNumber: String = "", val landArea: String = "", val municipalArea: String = "", val effectiveArea: String = "", val floors: String = "", val units: String = "", val buildingGroup: String = "", val structureType: String = "", val usageType: String = "", val planZone: String = "", val issuer: String = "", val stateTitle: String = "", val workType: String = "", val discipline: String = "", val unitTitle: String = "", val orderPart: String = "", val executionDate: String = "", val dbId: String = "",
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
    val ceilingCount: String = "", val indicator: String = "", val hasFile: Boolean = false
)

data class ExportFollowUpEdit(
    val description: String = "", val caseNumberEdit: String = "",
    val ownerEdit: String = "", val addressEdit: String = "",
    val ownerMobileEdit: String = ""
)

data class ExportFeeReceipt(
    val receiptDate: String = "", val amount: Long = 0L,
    val relatedEndDatesRaw: String = "", val isAdvance: Boolean = false,
    val feeSum: Long = 0L, val changes: String = "",
    val isRecentlyUpdated: Boolean = false,
    val createdAt: String = "", val updatedAt: String = ""
)
