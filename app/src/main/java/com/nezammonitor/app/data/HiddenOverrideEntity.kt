package com.nezammonitor.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nezammonitor.app.ui.components.stageNumberFromTitle
import kotlinx.coroutines.flow.first

/** One permanent field override made in the hidden settings menu (v3.8.4).
 *  A row in this table means "after every sync/import, set this field of
 *  this record to this value". Temporary hidden-menu edits do NOT create
 *  rows here — they only update the record itself, so the next sync/import
 *  overwrites them with the server/file value.
 *
 *  [recordKey] identifies the record without its Room id (ids are
 *  re-assigned on every sync/import, so they are useless as keys):
 *   - cases / specifications / followup_edits: caseNumber
 *   - engineers: caseNumber + "|" + name
 *   - fees: caseNumber + "|" + stage + "|" + amountType
 *   - reports: caseNumber + "|" + stage number (stageNumberFromTitle), or
 *     the raw stage title when the number is 0 (rich server titles)
 *   - fee_receipts: id + "|" + receiptDate + "|" + amount — the id is the
 *     primary match; receiptDate+amount is the fallback after an import
 *     re-assigns the ids (an id-only key could never match again).
 *  [value] is the field value as text ("true"/"false" for Booleans,
 *  decimal digits for Longs, raw text otherwise). */
@Entity(
    tableName = "hidden_overrides",
    indices = [Index(value = ["tableName", "recordKey", "fieldName"], unique = true)]
)
data class HiddenOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableName: String = "",
    val recordKey: String = "",
    val fieldName: String = "",
    val value: String = "",
    val updatedAt: String = ""
)

/** Record key for a case row. */
fun hiddenKeyForCase(caseNumber: String): String = caseNumber

/** Record key for an engineer row (identity = case + name). */
fun hiddenKeyForEngineer(caseNumber: String, name: String): String = "$caseNumber|$name"

/** Record key for a fee row (identity = case + stage + amount type). */
fun hiddenKeyForFee(caseNumber: String, stage: String, amountType: String): String =
    "$caseNumber|$stage|$amountType"

/** Record key for a report row (identity = case + stage number). The raw
 *  stage title is used when stageNumberFromTitle returns 0 (rich server
 *  titles such as "مرحله اول: مرحله فونداسیون"), so two reports of one
 *  case never share a key. */
fun hiddenKeyForReport(caseNumber: String, stage: String): String {
    val n = stageNumberFromTitle(stage)
    return "$caseNumber|${if (n > 0) n.toString() else stage}"
}

/** Record key for a receipt row: id first, then receiptDate+amount as the
 *  fallback identity for after an import re-assigns the ids. */
fun hiddenKeyForReceipt(id: Long, receiptDate: String, amount: Long): String =
    "$id|$receiptDate|$amount"

/** Re-applies every permanent hidden-menu override onto the current rows.
 *  Called once after an API sync finishes and once after a .nzmdata import
 *  finishes — the two points where sync/import rewrite the rows. An override
 *  whose row no longer exists is KEPT (skipped silently) so it can apply
 *  again if the row comes back. A failure on one override never stops the
 *  others, and never fails the surrounding sync/import. */
suspend fun applyHiddenOverrides(dao: NezamDao) {
    val overrides = try {
        dao.getAllOverridesList()
    } catch (_: Exception) {
        return
    }
    if (overrides.isEmpty()) return
    for (ov in overrides) {
        try {
            when (ov.tableName) {
                "cases" -> {
                    val row = dao.getCase(ov.recordKey) ?: continue
                    val updated = applyCaseField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertCases(listOf(updated))
                }
                "specifications" -> {
                    val row = dao.getSpecification(ov.recordKey) ?: continue
                    val updated = applySpecificationField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertSpecifications(listOf(updated))
                }
                "engineers" -> {
                    val parts = ov.recordKey.split("|", limit = 2)
                    if (parts.size < 2) continue
                    val row = dao.getEngineersForCase(parts[0]).firstOrNull { it.name == parts[1] } ?: continue
                    val updated = applyEngineerField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertEngineers(listOf(updated))
                }
                "fees" -> {
                    val parts = ov.recordKey.split("|", limit = 3)
                    if (parts.size < 3) continue
                    val row = dao.getFeesForCase(parts[0])
                        .firstOrNull { it.stage == parts[1] && it.amountType == parts[2] } ?: continue
                    val updated = applyFeeField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertFees(listOf(updated))
                }
                "reports" -> {
                    val parts = ov.recordKey.split("|", limit = 2)
                    if (parts.size < 2) continue
                    val row = dao.getReportsForCase(parts[0])
                        .firstOrNull { hiddenKeyForReport(parts[0], it.stage) == ov.recordKey } ?: continue
                    val updated = applyReportField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertReports(listOf(updated))
                }
                "fee_receipts" -> {
                    val parts = ov.recordKey.split("|", limit = 3)
                    val byId = parts[0].toLongOrNull()?.let { dao.getFeeReceipt(it) }
                    val row = byId ?: run {
                        if (parts.size < 3) return@run null
                        val amount = parts[2].toLongOrNull() ?: return@run null
                        dao.getAllFeeReceipts().first()
                            .firstOrNull { it.receiptDate == parts[1] && it.amount == amount }
                    } ?: continue
                    val updated = applyReceiptField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertFeeReceipts(listOf(updated))
                }
                "followup_edits" -> {
                    val row = dao.getFollowUpEdit(ov.recordKey) ?: continue
                    val updated = applyFollowUpField(row, ov.fieldName, ov.value) ?: continue
                    dao.insertFollowUpEdit(updated)
                }
            }
        } catch (_: Exception) {
            // Skip this override; the next sync/import tries again.
        }
    }
}

// One field of one row, by field name. Null = unknown field (or an
// unparseable Long/Boolean value): the override is skipped, never applied
// half-way. "id" and "caseNumber" are never overrideable.

private fun applyCaseField(row: CaseEntity, field: String, value: String): CaseEntity? = when (field) {
    "serial" -> row.copy(serial = value)
    "owner" -> row.copy(owner = value)
    "ownerMobile" -> row.copy(ownerMobile = value)
    "responsibility" -> row.copy(responsibility = value)
    "capacityDate" -> row.copy(capacityDate = value)
    "office" -> row.copy(office = value)
    "reportDate1" -> row.copy(reportDate1 = value)
    "reportDate2" -> row.copy(reportDate2 = value)
    "reportDate3" -> row.copy(reportDate3 = value)
    "ownerFather" -> row.copy(ownerFather = value)
    "ownerNationalCode" -> row.copy(ownerNationalCode = value)
    "ownerAddress" -> row.copy(ownerAddress = value)
    "ownerZip" -> row.copy(ownerZip = value)
    "ownerTel" -> row.copy(ownerTel = value)
    "ownerBirthLoc" -> row.copy(ownerBirthLoc = value)
    "permitNumber" -> row.copy(permitNumber = value)
    "permitDate" -> row.copy(permitDate = value)
    "renovationCode" -> row.copy(renovationCode = value)
    "address" -> row.copy(address = value)
    "blockNumber" -> row.copy(blockNumber = value)
    "landArea" -> row.copy(landArea = value)
    "municipalArea" -> row.copy(municipalArea = value)
    "effectiveArea" -> row.copy(effectiveArea = value)
    "floors" -> row.copy(floors = value)
    "units" -> row.copy(units = value)
    "buildingGroup" -> row.copy(buildingGroup = value)
    "structureType" -> row.copy(structureType = value)
    "usageType" -> row.copy(usageType = value)
    "planZone" -> row.copy(planZone = value)
    "issuer" -> row.copy(issuer = value)
    "stateTitle" -> row.copy(stateTitle = value)
    "workType" -> row.copy(workType = value)
    "discipline" -> row.copy(discipline = value)
    "unitTitle" -> row.copy(unitTitle = value)
    "orderPart" -> row.copy(orderPart = value)
    "executionDate" -> row.copy(executionDate = value)
    "dbId" -> row.copy(dbId = value)
    "archived" -> row.copy(archived = value == "true")
    else -> null
}

private fun applySpecificationField(row: SpecificationEntity, field: String, value: String): SpecificationEntity? = when (field) {
    "address" -> row.copy(address = value)
    "buildingGroup" -> row.copy(buildingGroup = value)
    "renovationCode" -> row.copy(renovationCode = value)
    "planInstructionNo" -> row.copy(planInstructionNo = value)
    "planInstructionType" -> row.copy(planInstructionType = value)
    "planInstructionDate" -> row.copy(planInstructionDate = value)
    "landArea" -> row.copy(landArea = value)
    "parafArea" -> row.copy(parafArea = value)
    "capacityArea" -> row.copy(capacityArea = value)
    "structureType" -> row.copy(structureType = value)
    "blockTitle" -> row.copy(blockTitle = value)
    "blockCount" -> row.copy(blockCount = value)
    "floors" -> row.copy(floors = value)
    "units" -> row.copy(units = value)
    "issuer" -> row.copy(issuer = value)
    "permitNumber" -> row.copy(permitNumber = value)
    "permitDate" -> row.copy(permitDate = value)
    "releaseDate" -> row.copy(releaseDate = value)
    "planZone" -> row.copy(planZone = value)
    "usageType" -> row.copy(usageType = value)
    else -> null
}

private fun applyEngineerField(row: EngineerEntity, field: String, value: String): EngineerEntity? = when (field) {
    "discipline" -> row.copy(discipline = value)
    "name" -> row.copy(name = value)
    "role" -> row.copy(role = value)
    "phone" -> row.copy(phone = value)
    "designLevel" -> row.copy(designLevel = value)
    "supervisionLevel" -> row.copy(supervisionLevel = value)
    "executionLevel" -> row.copy(executionLevel = value)
    else -> null
}

private fun applyFeeField(row: FeeEntity, field: String, value: String): FeeEntity? = when (field) {
    "discipline" -> row.copy(discipline = value)
    "serviceType" -> row.copy(serviceType = value)
    "stage" -> row.copy(stage = value)
    "startDate" -> row.copy(startDate = value)
    "endDate" -> row.copy(endDate = value)
    "amount" -> row.copy(amount = value)
    "payStatus" -> row.copy(payStatus = value)
    "confirmStatus" -> row.copy(confirmStatus = value)
    "amountType" -> row.copy(amountType = value)
    "description" -> row.copy(description = value)
    else -> null
}

private fun applyReportField(row: ReportEntity, field: String, value: String): ReportEntity? = when (field) {
    "rowNo" -> row.copy(rowNo = value)
    "reportType" -> row.copy(reportType = value)
    "stage" -> row.copy(stage = value)
    "engineer" -> row.copy(engineer = value)
    "discipline" -> row.copy(discipline = value)
    "visitDate" -> row.copy(visitDate = value)
    "ceilingCount" -> row.copy(ceilingCount = value)
    "indicator" -> row.copy(indicator = value)
    "hasFile" -> row.copy(hasFile = value == "true")
    "imageName" -> row.copy(imageName = value)
    else -> null
}

private fun applyReceiptField(row: FeeReceiptEntity, field: String, value: String): FeeReceiptEntity? {
    return when (field) {
    "receiptDate" -> row.copy(receiptDate = value)
    "amount" -> row.copy(amount = value.toLongOrNull() ?: return null)
    "relatedEndDatesRaw" -> row.copy(relatedEndDatesRaw = value)
    "isAdvance" -> row.copy(isAdvance = value == "true")
    "feeSum" -> row.copy(feeSum = value.toLongOrNull() ?: return null)
    "changes" -> row.copy(changes = value)
    "isRecentlyUpdated" -> row.copy(isRecentlyUpdated = value == "true")
    "createdAt" -> row.copy(createdAt = value)
    "updatedAt" -> row.copy(updatedAt = value)
    else -> null
    }
}

private fun applyFollowUpField(row: FollowUpEditEntity, field: String, value: String): FollowUpEditEntity? = when (field) {
    "description" -> row.copy(description = value)
    "caseNumberEdit" -> row.copy(caseNumberEdit = value)
    "ownerEdit" -> row.copy(ownerEdit = value)
    "addressEdit" -> row.copy(addressEdit = value)
    "ownerMobileEdit" -> row.copy(ownerMobileEdit = value)
    else -> null
}
