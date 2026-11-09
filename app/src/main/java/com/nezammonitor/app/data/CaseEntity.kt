package com.nezammonitor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",
    val serial: String = "",
    val owner: String = "",
    val ownerMobile: String = "",
    val responsibility: String = "",
    val capacityDate: String = "",
    val office: String = "",
    val reportDate1: String = "",
    val reportDate2: String = "",
    val reportDate3: String = "",
    val ownerFather: String = "",
    val ownerNationalCode: String = "",
    val ownerAddress: String = "",
    val ownerZip: String = "",
    val ownerTel: String = "",
    val ownerBirthLoc: String = "",
    val permitNumber: String = "",
    val permitDate: String = "",
    val renovationCode: String = "",
    val address: String = "",
    val blockNumber: String = "",
    val landArea: String = "",
    val municipalArea: String = "",
    val effectiveArea: String = "",
    val floors: String = "",
    val units: String = "",
    val buildingGroup: String = "",
    val structureType: String = "",
    val usageType: String = "",
    val planZone: String = "",
    val issuer: String = "",
    val stateTitle: String = "",
    val workType: String = "",
    val discipline: String = "",
    val unitTitle: String = "",
    val orderPart: String = "",
    val executionDate: String = "",
    val dbId: String = ""
)

@Entity(tableName = "specifications")
data class SpecificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",
    val address: String = "",
    val buildingGroup: String = "",
    val renovationCode: String = "",
    val planInstructionNo: String = "",
    val planInstructionType: String = "",
    val planInstructionDate: String = "",
    val landArea: String = "",
    val parafArea: String = "",
    val capacityArea: String = "",
    val structureType: String = "",
    val blockTitle: String = "",
    val blockCount: String = "",
    val floors: String = "",
    val units: String = "",
    val issuer: String = "",
    val permitNumber: String = "",
    val permitDate: String = "",
    val releaseDate: String = "",
    val planZone: String = "",
    val usageType: String = ""
)

@Entity(tableName = "engineers")
data class EngineerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",
    val discipline: String = "",
    val name: String = "",
    val role: String = "",
    val phone: String = "",
    val designLevel: String = "",
    val supervisionLevel: String = "",
    val executionLevel: String = ""
)

@Entity(tableName = "fees")
data class FeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",
    val discipline: String = "",
    val serviceType: String = "",
    val stage: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val amount: String = "",
    val payStatus: String = "",
    val confirmStatus: String = "",
    val amountType: String = "",
    val description: String = ""
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseNumber: String = "",
    val rowNo: String = "",
    val reportType: String = "",
    val stage: String = "",
    val engineer: String = "",
    val discipline: String = "",
    val visitDate: String = "",
    val ceilingCount: String = "",
    val indicator: String = "",
    val hasFile: Boolean = false
)

@Entity(tableName = "fee_receipts")
data class FeeReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptDate: String = "",
    val amount: Long = 0L,
    val relatedEndDatesRaw: String = "",
    val isAdvance: Boolean = false,
    val feeSum: Long = 0L,
    val changes: String = "",
    val isRecentlyUpdated: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "followup_edits")
data class FollowUpEditEntity(
    @PrimaryKey val caseNumber: String = "",
    val description: String = "",
    val caseNumberEdit: String = "",
    val ownerEdit: String = "",
    val addressEdit: String = "",
    val ownerMobileEdit: String = ""
)
