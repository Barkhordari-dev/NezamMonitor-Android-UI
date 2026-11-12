package com.nezammonitor.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.BuildConfig
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.*
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.components.NezamSegmentedControl
import com.nezammonitor.app.ui.components.SectionTitle
import com.nezammonitor.app.ui.screens.reportgen.todayJalali
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Session-only unlock flag for the hidden settings menu. Process memory
 *  ONLY — never persisted (no SharedPreferences, no Room). When the app
 *  process dies or restarts it is false again and the menu locks. */
object HiddenMenuSession {
    @Volatile
    var unlocked: Boolean = false
}

/** The Drive folder whose FIRST SUBFOLDER's name is the hidden-menu
 *  password (the outer folder's own name is NOT the password). The folder
 *  id comes from BuildConfig (local.properties on the developer's machine,
 *  never in the repo); a build without it gets an empty id and the menu
 *  simply cannot fetch a password (cached name / NO_NETWORK fallback). The
 *  password itself is NEVER written anywhere in the app — it is fetched
 *  from Drive at unlock time. */
private const val HIDDEN_MENU_PREFS = "hidden_menu"
private const val HIDDEN_MENU_CACHED_NAME = "cached_folder_name"

enum class HiddenPasswordResult { OK, WRONG, NO_NETWORK }

/** Fetches the Drive folder page (same pattern as fetchDriveListing in
 *  ReportHelpScreen) and returns the name of the FIRST SUBFOLDER inside
 *  it — that name is the hidden-menu password. The page <title> is the
 *  OUTER folder's own name and is NOT the password. Null when the page
 *  cannot be fetched or the folder has no subfolder. */
private fun fetchHiddenMenuFolderName(): String? {
    val url = URL("https://drive.google.com/embeddedfolderview?id=${BuildConfig.HIDDEN_MENU_FOLDER_ID}#list")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        connectTimeout = 15000
        readTimeout = 15000
    }
    val html = try {
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    } finally {
        conn.disconnect()
    }
    // One entry looks like: id="entry-<id>" ... flip-entry-title">NAME<
    // and a folder's own <a href> points at /drive/folders/<id>.
    val entryRe = Regex("""id="entry-([A-Za-z0-9_-]+)".{0,2000}?flip-entry-title">([^<]+)<""", RegexOption.DOT_MATCHES_ALL)
    for (m in entryRe.findAll(html)) {
        val id = m.groupValues[1]
        if (!m.value.contains("/drive/folders/$id")) continue
        val name = m.groupValues[2].trim()
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .trim()
        if (name.isNotBlank()) return name
    }
    return null
}

/** Checks the entered password against the name of the first subfolder
 *  inside the Drive folder: fetch it live (and cache it on success); on a
 *  network failure fall back to the last cached name; with no cached name
 *  either, report NO_NETWORK. The comparison is exact and case-sensitive. */
internal suspend fun checkHiddenMenuPassword(context: Context, entered: String): HiddenPasswordResult =
    withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(HIDDEN_MENU_PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString(HIDDEN_MENU_CACHED_NAME, null)
        val fetched = runCatching { fetchHiddenMenuFolderName() }.getOrNull()
        if (fetched != null) {
            prefs.edit().putString(HIDDEN_MENU_CACHED_NAME, fetched).apply()
            return@withContext if (entered.trim() == fetched) HiddenPasswordResult.OK else HiddenPasswordResult.WRONG
        }
        val fallback = cached?.takeIf { it.isNotBlank() } ?: return@withContext HiddenPasswordResult.NO_NETWORK
        return@withContext if (entered.trim() == fallback) HiddenPasswordResult.OK else HiddenPasswordResult.WRONG
    }

// ---- Search normalisation (same rules as CasesScreen.normalizeSearchText) ----

private fun String.normalizeDigitsLocal(): String = this
    .replace("۰", "0").replace("۱", "1").replace("۲", "2")
    .replace("۳", "3").replace("۴", "4").replace("۵", "5")
    .replace("۶", "6").replace("۷", "7").replace("۸", "8")
    .replace("۹", "9")
    .replace("٠", "0").replace("١", "1").replace("٢", "2")
    .replace("٣", "3").replace("٤", "4").replace("٥", "5")
    .replace("٦", "6").replace("٧", "7").replace("٨", "8")
    .replace("٩", "9")

private fun normalizeSearchTextLocal(s: String): String = toPersianChars(s.normalizeDigitsLocal())
    .replace('ة', 'ه').replace('ؤ', 'و').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    .replace("ـ", "").replace("\u200C", "").replace("\u200D", "")
    .trim()

// ---- Edit model ----

private enum class FieldKind { TEXT, CHECKBOX, LONG }

/** One editable field on an edit form: [name] is the entity field name
 *  (also the override field name), [initial] its current value as text
 *  ("true"/"false" for CHECKBOX, decimal digits for LONG). */
private data class EditFieldDef(
    val name: String,
    val label: String,
    val kind: FieldKind,
    val initial: String
)

/** A loaded record ready to edit. [updateRow] writes the new values back
 *  to Room (copy + insert REPLACE with the same id). */
private data class EditSession(
    val table: String,
    val recordKey: String,
    val title: String,
    val readOnlyLines: List<String>,
    val fields: List<EditFieldDef>,
    val updateRow: suspend (Map<String, String>) -> Unit
)

private data class EditTarget(val table: String, val caseNumber: String?, val id: Long?)

private data class HiddenListItem(val id: Long, val title: String, val subtitle: String)

private sealed interface HiddenPage {
    object Home : HiddenPage
    data class CaseMenu(val caseNumber: String) : HiddenPage
    data class RecordList(val table: String, val caseNumber: String?) : HiddenPage
    data class Edit(val target: EditTarget) : HiddenPage
}

private fun hiddenTableTitle(table: String): String = when (table) {
    "cases" -> "پرونده"
    "specifications" -> "مشخصات"
    "engineers" -> "مهندسین"
    "fees" -> "حق‌الزحمه‌ها"
    "reports" -> "گزارش‌ها"
    "fee_receipts" -> "رسیدها"
    "followup_edits" -> "پیگیری"
    else -> table
}

private fun nowStamp(): String {
    val timePart = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    return "${todayJalali().format()} $timePart"
}

// ---- Per-table field definitions (labels) and value application ----
// "id" and "caseNumber" are NEVER editable — they identify the record.
// FollowUpEditEntity's caseNumber is its primary key: read-only too.

private fun caseFields(row: CaseEntity): List<EditFieldDef> = listOf(
    EditFieldDef("serial", "سریال", FieldKind.TEXT, row.serial),
    EditFieldDef("owner", "مالک", FieldKind.TEXT, row.owner),
    EditFieldDef("ownerMobile", "موبایل مالک", FieldKind.TEXT, row.ownerMobile),
    EditFieldDef("responsibility", "مسئولیت", FieldKind.TEXT, row.responsibility),
    EditFieldDef("capacityDate", "تاریخ ظرفیت", FieldKind.TEXT, row.capacityDate),
    EditFieldDef("office", "دفتر", FieldKind.TEXT, row.office),
    EditFieldDef("reportDate1", "تاریخ گزارش ۱", FieldKind.TEXT, row.reportDate1),
    EditFieldDef("reportDate2", "تاریخ گزارش ۲", FieldKind.TEXT, row.reportDate2),
    EditFieldDef("reportDate3", "تاریخ گزارش ۳", FieldKind.TEXT, row.reportDate3),
    EditFieldDef("ownerFather", "نام پدر مالک", FieldKind.TEXT, row.ownerFather),
    EditFieldDef("ownerNationalCode", "کد ملی مالک", FieldKind.TEXT, row.ownerNationalCode),
    EditFieldDef("ownerAddress", "آدرس مالک", FieldKind.TEXT, row.ownerAddress),
    EditFieldDef("ownerZip", "کد پستی مالک", FieldKind.TEXT, row.ownerZip),
    EditFieldDef("ownerTel", "تلفن مالک", FieldKind.TEXT, row.ownerTel),
    EditFieldDef("ownerBirthLoc", "محل تولد مالک", FieldKind.TEXT, row.ownerBirthLoc),
    EditFieldDef("permitNumber", "شماره پروانه", FieldKind.TEXT, row.permitNumber),
    EditFieldDef("permitDate", "تاریخ صدور پروانه", FieldKind.TEXT, row.permitDate),
    EditFieldDef("renovationCode", "کد نوسازی", FieldKind.TEXT, row.renovationCode),
    EditFieldDef("address", "نشانی", FieldKind.TEXT, row.address),
    EditFieldDef("blockNumber", "شماره بلوک", FieldKind.TEXT, row.blockNumber),
    EditFieldDef("landArea", "مساحت زمین", FieldKind.TEXT, row.landArea),
    EditFieldDef("municipalArea", "متراژ شهرداری", FieldKind.TEXT, row.municipalArea),
    EditFieldDef("effectiveArea", "متراژ مؤثر", FieldKind.TEXT, row.effectiveArea),
    EditFieldDef("floors", "تعداد طبقات", FieldKind.TEXT, row.floors),
    EditFieldDef("units", "تعداد واحد", FieldKind.TEXT, row.units),
    EditFieldDef("buildingGroup", "گروه ساختمانی", FieldKind.TEXT, row.buildingGroup),
    EditFieldDef("structureType", "نوع سازه", FieldKind.TEXT, row.structureType),
    EditFieldDef("usageType", "نوع کاربری", FieldKind.TEXT, row.usageType),
    EditFieldDef("planZone", "محدوده طرح", FieldKind.TEXT, row.planZone),
    EditFieldDef("issuer", "صادرکننده", FieldKind.TEXT, row.issuer),
    EditFieldDef("stateTitle", "وضعیت", FieldKind.TEXT, row.stateTitle),
    EditFieldDef("workType", "نوع کار", FieldKind.TEXT, row.workType),
    EditFieldDef("discipline", "رشته", FieldKind.TEXT, row.discipline),
    EditFieldDef("unitTitle", "عنوان واحد", FieldKind.TEXT, row.unitTitle),
    EditFieldDef("orderPart", "بخش دستور", FieldKind.TEXT, row.orderPart),
    EditFieldDef("executionDate", "تاریخ ترخیص", FieldKind.TEXT, row.executionDate),
    EditFieldDef("dbId", "شناسه سامانه (dbId)", FieldKind.TEXT, row.dbId),
    EditFieldDef("archived", "بایگانی شده", FieldKind.CHECKBOX, row.archived.toString())
)

private fun applyCaseValues(row: CaseEntity, v: Map<String, String>): CaseEntity = row.copy(
    serial = v["serial"] ?: row.serial,
    owner = v["owner"] ?: row.owner,
    ownerMobile = v["ownerMobile"] ?: row.ownerMobile,
    responsibility = v["responsibility"] ?: row.responsibility,
    capacityDate = v["capacityDate"] ?: row.capacityDate,
    office = v["office"] ?: row.office,
    reportDate1 = v["reportDate1"] ?: row.reportDate1,
    reportDate2 = v["reportDate2"] ?: row.reportDate2,
    reportDate3 = v["reportDate3"] ?: row.reportDate3,
    ownerFather = v["ownerFather"] ?: row.ownerFather,
    ownerNationalCode = v["ownerNationalCode"] ?: row.ownerNationalCode,
    ownerAddress = v["ownerAddress"] ?: row.ownerAddress,
    ownerZip = v["ownerZip"] ?: row.ownerZip,
    ownerTel = v["ownerTel"] ?: row.ownerTel,
    ownerBirthLoc = v["ownerBirthLoc"] ?: row.ownerBirthLoc,
    permitNumber = v["permitNumber"] ?: row.permitNumber,
    permitDate = v["permitDate"] ?: row.permitDate,
    renovationCode = v["renovationCode"] ?: row.renovationCode,
    address = v["address"] ?: row.address,
    blockNumber = v["blockNumber"] ?: row.blockNumber,
    landArea = v["landArea"] ?: row.landArea,
    municipalArea = v["municipalArea"] ?: row.municipalArea,
    effectiveArea = v["effectiveArea"] ?: row.effectiveArea,
    floors = v["floors"] ?: row.floors,
    units = v["units"] ?: row.units,
    buildingGroup = v["buildingGroup"] ?: row.buildingGroup,
    structureType = v["structureType"] ?: row.structureType,
    usageType = v["usageType"] ?: row.usageType,
    planZone = v["planZone"] ?: row.planZone,
    issuer = v["issuer"] ?: row.issuer,
    stateTitle = v["stateTitle"] ?: row.stateTitle,
    workType = v["workType"] ?: row.workType,
    discipline = v["discipline"] ?: row.discipline,
    unitTitle = v["unitTitle"] ?: row.unitTitle,
    orderPart = v["orderPart"] ?: row.orderPart,
    executionDate = v["executionDate"] ?: row.executionDate,
    dbId = v["dbId"] ?: row.dbId,
    archived = v["archived"]?.let { it == "true" } ?: row.archived
)

private fun specificationFields(row: SpecificationEntity): List<EditFieldDef> = listOf(
    EditFieldDef("address", "نشانی", FieldKind.TEXT, row.address),
    EditFieldDef("buildingGroup", "گروه ساختمانی", FieldKind.TEXT, row.buildingGroup),
    EditFieldDef("renovationCode", "کد نوسازی", FieldKind.TEXT, row.renovationCode),
    EditFieldDef("planInstructionNo", "شماره دستور نقشه", FieldKind.TEXT, row.planInstructionNo),
    EditFieldDef("planInstructionType", "نوع دستور نقشه", FieldKind.TEXT, row.planInstructionType),
    EditFieldDef("planInstructionDate", "تاریخ دستور نقشه", FieldKind.TEXT, row.planInstructionDate),
    EditFieldDef("landArea", "مساحت زمین", FieldKind.TEXT, row.landArea),
    EditFieldDef("parafArea", "متراژ پاراف", FieldKind.TEXT, row.parafArea),
    EditFieldDef("capacityArea", "مساحت ظرفیت", FieldKind.TEXT, row.capacityArea),
    EditFieldDef("structureType", "نوع سازه", FieldKind.TEXT, row.structureType),
    EditFieldDef("blockTitle", "عنوان بلوک", FieldKind.TEXT, row.blockTitle),
    EditFieldDef("blockCount", "تعداد بلوک", FieldKind.TEXT, row.blockCount),
    EditFieldDef("floors", "تعداد طبقات", FieldKind.TEXT, row.floors),
    EditFieldDef("units", "تعداد واحد", FieldKind.TEXT, row.units),
    EditFieldDef("issuer", "صادرکننده", FieldKind.TEXT, row.issuer),
    EditFieldDef("permitNumber", "شماره پروانه", FieldKind.TEXT, row.permitNumber),
    EditFieldDef("permitDate", "تاریخ صدور پروانه", FieldKind.TEXT, row.permitDate),
    EditFieldDef("releaseDate", "تاریخ ترخیص", FieldKind.TEXT, row.releaseDate),
    EditFieldDef("planZone", "محدوده طرح", FieldKind.TEXT, row.planZone),
    EditFieldDef("usageType", "نوع کاربری", FieldKind.TEXT, row.usageType)
)

private fun applySpecificationValues(row: SpecificationEntity, v: Map<String, String>): SpecificationEntity = row.copy(
    address = v["address"] ?: row.address,
    buildingGroup = v["buildingGroup"] ?: row.buildingGroup,
    renovationCode = v["renovationCode"] ?: row.renovationCode,
    planInstructionNo = v["planInstructionNo"] ?: row.planInstructionNo,
    planInstructionType = v["planInstructionType"] ?: row.planInstructionType,
    planInstructionDate = v["planInstructionDate"] ?: row.planInstructionDate,
    landArea = v["landArea"] ?: row.landArea,
    parafArea = v["parafArea"] ?: row.parafArea,
    capacityArea = v["capacityArea"] ?: row.capacityArea,
    structureType = v["structureType"] ?: row.structureType,
    blockTitle = v["blockTitle"] ?: row.blockTitle,
    blockCount = v["blockCount"] ?: row.blockCount,
    floors = v["floors"] ?: row.floors,
    units = v["units"] ?: row.units,
    issuer = v["issuer"] ?: row.issuer,
    permitNumber = v["permitNumber"] ?: row.permitNumber,
    permitDate = v["permitDate"] ?: row.permitDate,
    releaseDate = v["releaseDate"] ?: row.releaseDate,
    planZone = v["planZone"] ?: row.planZone,
    usageType = v["usageType"] ?: row.usageType
)

private fun engineerFields(row: EngineerEntity): List<EditFieldDef> = listOf(
    EditFieldDef("discipline", "رشته", FieldKind.TEXT, row.discipline),
    EditFieldDef("name", "نام", FieldKind.TEXT, row.name),
    EditFieldDef("role", "نقش", FieldKind.TEXT, row.role),
    EditFieldDef("phone", "تلفن", FieldKind.TEXT, row.phone),
    EditFieldDef("designLevel", "سطح طراحی", FieldKind.TEXT, row.designLevel),
    EditFieldDef("supervisionLevel", "سطح نظارت", FieldKind.TEXT, row.supervisionLevel),
    EditFieldDef("executionLevel", "سطح اجرا", FieldKind.TEXT, row.executionLevel)
)

private fun applyEngineerValues(row: EngineerEntity, v: Map<String, String>): EngineerEntity = row.copy(
    discipline = v["discipline"] ?: row.discipline,
    name = v["name"] ?: row.name,
    role = v["role"] ?: row.role,
    phone = v["phone"] ?: row.phone,
    designLevel = v["designLevel"] ?: row.designLevel,
    supervisionLevel = v["supervisionLevel"] ?: row.supervisionLevel,
    executionLevel = v["executionLevel"] ?: row.executionLevel
)

private fun feeFields(row: FeeEntity): List<EditFieldDef> = listOf(
    EditFieldDef("discipline", "رشته", FieldKind.TEXT, row.discipline),
    EditFieldDef("serviceType", "نوع خدمت", FieldKind.TEXT, row.serviceType),
    EditFieldDef("stage", "مرحله", FieldKind.TEXT, row.stage),
    EditFieldDef("startDate", "تاریخ شروع", FieldKind.TEXT, row.startDate),
    EditFieldDef("endDate", "تاریخ پرداخت", FieldKind.TEXT, row.endDate),
    EditFieldDef("amount", "مبلغ", FieldKind.TEXT, row.amount),
    EditFieldDef("payStatus", "وضعیت پرداخت", FieldKind.TEXT, row.payStatus),
    EditFieldDef("confirmStatus", "وضعیت تأیید", FieldKind.TEXT, row.confirmStatus),
    EditFieldDef("amountType", "نوع مبلغ", FieldKind.TEXT, row.amountType),
    EditFieldDef("description", "توضیحات", FieldKind.TEXT, row.description)
)

private fun applyFeeValues(row: FeeEntity, v: Map<String, String>): FeeEntity = row.copy(
    discipline = v["discipline"] ?: row.discipline,
    serviceType = v["serviceType"] ?: row.serviceType,
    stage = v["stage"] ?: row.stage,
    startDate = v["startDate"] ?: row.startDate,
    endDate = v["endDate"] ?: row.endDate,
    amount = v["amount"] ?: row.amount,
    payStatus = v["payStatus"] ?: row.payStatus,
    confirmStatus = v["confirmStatus"] ?: row.confirmStatus,
    amountType = v["amountType"] ?: row.amountType,
    description = v["description"] ?: row.description
)

private fun reportFields(row: ReportEntity): List<EditFieldDef> = listOf(
    EditFieldDef("rowNo", "ردیف", FieldKind.TEXT, row.rowNo),
    EditFieldDef("reportType", "نوع گزارش", FieldKind.TEXT, row.reportType),
    EditFieldDef("stage", "مرحله", FieldKind.TEXT, row.stage),
    EditFieldDef("engineer", "مهندس", FieldKind.TEXT, row.engineer),
    EditFieldDef("discipline", "رشته", FieldKind.TEXT, row.discipline),
    EditFieldDef("visitDate", "تاریخ بازدید", FieldKind.TEXT, row.visitDate),
    EditFieldDef("ceilingCount", "تعداد سقف", FieldKind.TEXT, row.ceilingCount),
    EditFieldDef("indicator", "شاخص", FieldKind.TEXT, row.indicator),
    EditFieldDef("hasFile", "فایل دارد", FieldKind.CHECKBOX, row.hasFile.toString()),
    EditFieldDef("imageName", "نام فایل تصویر", FieldKind.TEXT, row.imageName)
)

private fun applyReportValues(row: ReportEntity, v: Map<String, String>): ReportEntity = row.copy(
    rowNo = v["rowNo"] ?: row.rowNo,
    reportType = v["reportType"] ?: row.reportType,
    stage = v["stage"] ?: row.stage,
    engineer = v["engineer"] ?: row.engineer,
    discipline = v["discipline"] ?: row.discipline,
    visitDate = v["visitDate"] ?: row.visitDate,
    ceilingCount = v["ceilingCount"] ?: row.ceilingCount,
    indicator = v["indicator"] ?: row.indicator,
    hasFile = v["hasFile"]?.let { it == "true" } ?: row.hasFile,
    imageName = v["imageName"] ?: row.imageName
)

private fun receiptFields(row: FeeReceiptEntity): List<EditFieldDef> = listOf(
    EditFieldDef("receiptDate", "تاریخ رسید", FieldKind.TEXT, row.receiptDate),
    EditFieldDef("amount", "مبلغ", FieldKind.LONG, row.amount.toString()),
    EditFieldDef("relatedEndDatesRaw", "تاریخ‌های مرتبط", FieldKind.TEXT, row.relatedEndDatesRaw),
    EditFieldDef("isAdvance", "پیش‌پرداخت", FieldKind.CHECKBOX, row.isAdvance.toString()),
    EditFieldDef("feeSum", "جمع حق‌الزحمه", FieldKind.LONG, row.feeSum.toString()),
    EditFieldDef("changes", "تغییرات", FieldKind.TEXT, row.changes),
    EditFieldDef("isRecentlyUpdated", "به‌تازگی به‌روز شده", FieldKind.CHECKBOX, row.isRecentlyUpdated.toString()),
    EditFieldDef("createdAt", "ایجاد", FieldKind.TEXT, row.createdAt),
    EditFieldDef("updatedAt", "به‌روزرسانی", FieldKind.TEXT, row.updatedAt)
)

private fun applyReceiptValues(row: FeeReceiptEntity, v: Map<String, String>): FeeReceiptEntity = row.copy(
    receiptDate = v["receiptDate"] ?: row.receiptDate,
    amount = v["amount"]?.toLongOrNull() ?: row.amount,
    relatedEndDatesRaw = v["relatedEndDatesRaw"] ?: row.relatedEndDatesRaw,
    isAdvance = v["isAdvance"]?.let { it == "true" } ?: row.isAdvance,
    feeSum = v["feeSum"]?.toLongOrNull() ?: row.feeSum,
    changes = v["changes"] ?: row.changes,
    isRecentlyUpdated = v["isRecentlyUpdated"]?.let { it == "true" } ?: row.isRecentlyUpdated,
    createdAt = v["createdAt"] ?: row.createdAt,
    updatedAt = v["updatedAt"] ?: row.updatedAt
)

private fun followUpFields(row: FollowUpEditEntity): List<EditFieldDef> = listOf(
    EditFieldDef("description", "توضیحات", FieldKind.TEXT, row.description),
    EditFieldDef("caseNumberEdit", "ویرایش شماره پرونده", FieldKind.TEXT, row.caseNumberEdit),
    EditFieldDef("ownerEdit", "ویرایش مالک", FieldKind.TEXT, row.ownerEdit),
    EditFieldDef("addressEdit", "ویرایش آدرس", FieldKind.TEXT, row.addressEdit),
    EditFieldDef("ownerMobileEdit", "ویرایش موبایل مالک", FieldKind.TEXT, row.ownerMobileEdit)
)

private fun applyFollowUpValues(row: FollowUpEditEntity, v: Map<String, String>): FollowUpEditEntity = row.copy(
    description = v["description"] ?: row.description,
    caseNumberEdit = v["caseNumberEdit"] ?: row.caseNumberEdit,
    ownerEdit = v["ownerEdit"] ?: row.ownerEdit,
    addressEdit = v["addressEdit"] ?: row.addressEdit,
    ownerMobileEdit = v["ownerMobileEdit"] ?: row.ownerMobileEdit
)

/** Persian label of an override field, for the "تغییرات دائمی فعال" list. */
private fun overrideFieldLabel(table: String, field: String): String {
    val labels = when (table) {
        "cases" -> mapOf(
            "serial" to "سریال", "owner" to "مالک", "ownerMobile" to "موبایل مالک",
            "responsibility" to "مسئولیت", "capacityDate" to "تاریخ ظرفیت", "office" to "دفتر",
            "reportDate1" to "تاریخ گزارش ۱", "reportDate2" to "تاریخ گزارش ۲",
            "reportDate3" to "تاریخ گزارش ۳", "ownerFather" to "نام پدر مالک",
            "ownerNationalCode" to "کد ملی مالک", "ownerAddress" to "آدرس مالک",
            "ownerZip" to "کد پستی مالک", "ownerTel" to "تلفن مالک",
            "ownerBirthLoc" to "محل تولد مالک", "permitNumber" to "شماره پروانه",
            "permitDate" to "تاریخ صدور پروانه", "renovationCode" to "کد نوسازی",
            "address" to "نشانی", "blockNumber" to "شماره بلوک", "landArea" to "مساحت زمین",
            "municipalArea" to "متراژ شهرداری", "effectiveArea" to "متراژ مؤثر",
            "floors" to "تعداد طبقات", "units" to "تعداد واحد",
            "buildingGroup" to "گروه ساختمانی", "structureType" to "نوع سازه",
            "usageType" to "نوع کاربری", "planZone" to "محدوده طرح",
            "issuer" to "صادرکننده", "stateTitle" to "وضعیت", "workType" to "نوع کار",
            "discipline" to "رشته", "unitTitle" to "عنوان واحد",
            "orderPart" to "بخش دستور", "executionDate" to "تاریخ ترخیص",
            "dbId" to "شناسه سامانه (dbId)", "archived" to "بایگانی شده"
        )
        "specifications" -> mapOf(
            "address" to "نشانی", "buildingGroup" to "گروه ساختمانی",
            "renovationCode" to "کد نوسازی", "planInstructionNo" to "شماره دستور نقشه",
            "planInstructionType" to "نوع دستور نقشه",
            "planInstructionDate" to "تاریخ دستور نقشه", "landArea" to "مساحت زمین",
            "parafArea" to "متراژ پاراف", "capacityArea" to "مساحت ظرفیت",
            "structureType" to "نوع سازه", "blockTitle" to "عنوان بلوک",
            "blockCount" to "تعداد بلوک", "floors" to "تعداد طبقات",
            "units" to "تعداد واحد", "issuer" to "صادرکننده",
            "permitNumber" to "شماره پروانه", "permitDate" to "تاریخ صدور پروانه",
            "releaseDate" to "تاریخ ترخیص", "planZone" to "محدوده طرح",
            "usageType" to "نوع کاربری"
        )
        "engineers" -> mapOf(
            "discipline" to "رشته", "name" to "نام", "role" to "نقش",
            "phone" to "تلفن", "designLevel" to "سطح طراحی",
            "supervisionLevel" to "سطح نظارت", "executionLevel" to "سطح اجرا"
        )
        "fees" -> mapOf(
            "discipline" to "رشته", "serviceType" to "نوع خدمت", "stage" to "مرحله",
            "startDate" to "تاریخ شروع", "endDate" to "تاریخ پرداخت", "amount" to "مبلغ",
            "payStatus" to "وضعیت پرداخت", "confirmStatus" to "وضعیت تأیید",
            "amountType" to "نوع مبلغ", "description" to "توضیحات"
        )
        "reports" -> mapOf(
            "rowNo" to "ردیف", "reportType" to "نوع گزارش", "stage" to "مرحله",
            "engineer" to "مهندس", "discipline" to "رشته",
            "visitDate" to "تاریخ بازدید", "ceilingCount" to "تعداد سقف",
            "indicator" to "شاخص", "hasFile" to "فایل دارد", "imageName" to "نام فایل تصویر"
        )
        "fee_receipts" -> mapOf(
            "receiptDate" to "تاریخ رسید", "amount" to "مبلغ",
            "relatedEndDatesRaw" to "تاریخ‌های مرتبط", "isAdvance" to "پیش‌پرداخت",
            "feeSum" to "جمع حق‌الزحمه", "changes" to "تغییرات",
            "isRecentlyUpdated" to "به‌تازگی به‌روز شده", "createdAt" to "ایجاد",
            "updatedAt" to "به‌روزرسانی"
        )
        "followup_edits" -> mapOf(
            "description" to "توضیحات", "caseNumberEdit" to "ویرایش شماره پرونده",
            "ownerEdit" to "ویرایش مالک", "addressEdit" to "ویرایش آدرس",
            "ownerMobileEdit" to "ویرایش موبایل مالک"
        )
        else -> emptyMap()
    }
    return labels[field] ?: field
}

/** The record key a row will have AFTER applying [values]: the same key
 *  builders as at load time, fed with the edited values. Only tables whose
 *  key contains an editable field can change key (engineers: name; fees:
 *  stage/amountType; reports: stage; receipts: receiptDate/amount);
 *  cases/specifications/followup_edits are keyed by caseNumber, which is
 *  never editable, so their key never changes. */
private fun newRecordKeyFor(session: EditSession, values: Map<String, String>): String {
    fun initial(field: String): String = session.fields.firstOrNull { it.name == field }?.initial ?: ""
    val old = session.recordKey
    return when (session.table) {
        "engineers" -> hiddenKeyForEngineer(old.substringBefore("|"), values["name"] ?: initial("name"))
        "fees" -> {
            val parts = old.split("|", limit = 3)
            hiddenKeyForFee(
                parts.getOrNull(0) ?: "",
                values["stage"] ?: initial("stage"),
                values["amountType"] ?: initial("amountType")
            )
        }
        "reports" -> hiddenKeyForReport(old.substringBefore("|"), values["stage"] ?: initial("stage"))
        "fee_receipts" -> {
            val parts = old.split("|", limit = 3)
            hiddenKeyForReceipt(
                parts.getOrNull(0)?.toLongOrNull() ?: 0L,
                values["receiptDate"] ?: initial("receiptDate"),
                values["amount"]?.toLongOrNull() ?: parts.getOrNull(2)?.toLongOrNull() ?: 0L
            )
        }
        else -> old
    }
}

/** Loads one record into an EditSession, or null when the record does not
 *  exist (only existing records are editable — nothing is ever added here). */
private suspend fun loadEditSession(dao: NezamDao, target: EditTarget): EditSession? {
    return when (target.table) {
        "cases" -> {
            val row = dao.getCase(target.caseNumber ?: return null) ?: return null
            EditSession(
                table = "cases",
                recordKey = hiddenKeyForCase(row.caseNumber),
                title = "پرونده ${row.caseNumber}",
                readOnlyLines = listOf("شناسهٔ رکورد: ${row.id}", "شماره پرونده: ${row.caseNumber}"),
                fields = caseFields(row),
                updateRow = { values -> dao.insertCases(listOf(applyCaseValues(row, values))) }
            )
        }
        "specifications" -> {
            val row = dao.getSpecification(target.caseNumber ?: return null) ?: return null
            EditSession(
                table = "specifications",
                recordKey = hiddenKeyForCase(row.caseNumber),
                title = "مشخصات پرونده ${row.caseNumber}",
                readOnlyLines = listOf("شناسهٔ رکورد: ${row.id}", "شماره پرونده: ${row.caseNumber}"),
                fields = specificationFields(row),
                updateRow = { values -> dao.insertSpecifications(listOf(applySpecificationValues(row, values))) }
            )
        }
        "engineers" -> {
            val row = dao.getEngineersForCase(target.caseNumber ?: return null)
                .firstOrNull { it.id == target.id } ?: return null
            EditSession(
                table = "engineers",
                recordKey = hiddenKeyForEngineer(row.caseNumber, row.name),
                title = "مهندس ${row.name}",
                readOnlyLines = listOf("شناسهٔ رکورد: ${row.id}", "شماره پرونده: ${row.caseNumber}"),
                fields = engineerFields(row),
                updateRow = { values -> dao.insertEngineers(listOf(applyEngineerValues(row, values))) }
            )
        }
        "fees" -> {
            val row = dao.getFeesForCase(target.caseNumber ?: return null)
                .firstOrNull { it.id == target.id } ?: return null
            EditSession(
                table = "fees",
                recordKey = hiddenKeyForFee(row.caseNumber, row.stage, row.amountType),
                title = "حق‌الزحمه — مرحله ${row.stage} (${row.amountType})",
                readOnlyLines = listOf("شناسهٔ رکورد: ${row.id}", "شماره پرونده: ${row.caseNumber}"),
                fields = feeFields(row),
                updateRow = { values -> dao.insertFees(listOf(applyFeeValues(row, values))) }
            )
        }
        "reports" -> {
            val row = dao.getReportsForCase(target.caseNumber ?: return null)
                .firstOrNull { it.id == target.id } ?: return null
            EditSession(
                table = "reports",
                recordKey = hiddenKeyForReport(row.caseNumber, row.stage),
                title = "گزارش ${row.reportType}",
                readOnlyLines = listOf("شناسهٔ رکورد: ${row.id}", "شماره پرونده: ${row.caseNumber}"),
                fields = reportFields(row),
                updateRow = { values -> dao.insertReports(listOf(applyReportValues(row, values))) }
            )
        }
        "fee_receipts" -> {
            val row = dao.getFeeReceipt(target.id ?: return null) ?: return null
            EditSession(
                table = "fee_receipts",
                recordKey = hiddenKeyForReceipt(row.id, row.receiptDate, row.amount),
                title = "رسید ${row.receiptDate}",
                readOnlyLines = listOf("شناسهٔ رکورد: ${row.id}"),
                fields = receiptFields(row),
                updateRow = { values -> dao.insertFeeReceipts(listOf(applyReceiptValues(row, values))) }
            )
        }
        "followup_edits" -> {
            val row = dao.getFollowUpEdit(target.caseNumber ?: return null) ?: return null
            EditSession(
                table = "followup_edits",
                recordKey = hiddenKeyForCase(row.caseNumber),
                title = "پیگیری پرونده ${row.caseNumber}",
                readOnlyLines = listOf("شماره پرونده: ${row.caseNumber}"),
                fields = followUpFields(row),
                updateRow = { values -> dao.insertFollowUpEdit(applyFollowUpValues(row, values)) }
            )
        }
        else -> null
    }
}

/** Rows for a record list page. */
private suspend fun loadListItems(dao: NezamDao, table: String, caseNumber: String?): List<HiddenListItem> {
    return when (table) {
        "engineers" -> dao.getEngineersForCase(caseNumber ?: return emptyList()).map {
            HiddenListItem(it.id, it.name, "${it.discipline} — ${it.role}".trim().trimStart('—').trim())
        }
        "fees" -> dao.getFeesForCase(caseNumber ?: return emptyList()).map {
            HiddenListItem(it.id, "مرحله ${it.stage} — ${toPersianChars(it.amountType)}", it.amount)
        }
        "reports" -> dao.getReportsForCase(caseNumber ?: return emptyList()).map {
            HiddenListItem(it.id, it.reportType, "${it.stage} — ${it.visitDate}")
        }
        "fee_receipts" -> dao.getAllFeeReceipts().first().map {
            HiddenListItem(it.id, it.receiptDate, "${it.amount} ریال")
        }
        else -> emptyList()
    }
}

/** The hidden settings screen (v3.8.4). Reachable only after the 10-tap
 *  unlock in Settings; the route itself also checks [HiddenMenuSession] and
 *  leaves immediately when it is not unlocked. Lets the user edit any
 *  existing record and save it — temporarily (until the next sync/import)
 *  or permanently (an override row re-applies the value after every
 *  sync/import). No record is ever added or deleted here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    // Route guard: without the session unlock, leave at once.
    if (!HiddenMenuSession.unlocked) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember(context) { NezamDatabase.getDatabase(context).nezamDao() }

    val cases by viewModel.cases.collectAsState()
    val archivedCases by viewModel.archivedCases.collectAsState()
    val engineers by viewModel.engineers.collectAsState()
    val fees by viewModel.fees.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val overrides by dao.getAllOverrides().collectAsState(initial = emptyList())

    var pageStack by remember { mutableStateOf(listOf<HiddenPage>(HiddenPage.Home)) }
    val currentPage = pageStack.last()
    var statusMessage by remember { mutableStateOf("") }

    fun pushPage(page: HiddenPage) {
        statusMessage = ""
        pageStack = pageStack + page
    }

    fun popPage() {
        if (pageStack.size > 1) pageStack = pageStack.dropLast(1) else onBack()
    }

    fun saveSession(session: EditSession, values: Map<String, String>, permanent: Boolean) {
        scope.launch {
            try {
                session.updateRow(values)
                if (permanent) {
                    val changed = session.fields.filter { (values[it.name] ?: it.initial) != it.initial }
                    val now = nowStamp()
                    // If a key field changed (fee stage/amountType, engineer
                    // name, report stage, receipt date/amount), the record's
                    // key changed too: re-key every existing override of this
                    // record FIRST, or those permanent edits would never match
                    // the row again after the next sync/import. A re-key that
                    // would collide with another record's override is skipped.
                    val newKey = newRecordKeyFor(session, values)
                    val targetKey = if (newKey != session.recordKey) {
                        val all = dao.getAllOverridesList()
                        val taken = all.map { Triple(it.tableName, it.recordKey, it.fieldName) }.toSet()
                        for (ov in all.filter { it.tableName == session.table && it.recordKey == session.recordKey }) {
                            if (Triple(ov.tableName, newKey, ov.fieldName) !in taken) {
                                dao.deleteOverride(ov)
                                dao.insertOverride(ov.copy(id = 0, recordKey = newKey))
                            }
                        }
                        newKey
                    } else session.recordKey
                    for (f in changed) {
                        dao.insertOverride(
                            HiddenOverrideEntity(
                                tableName = session.table,
                                recordKey = targetKey,
                                fieldName = f.name,
                                value = values[f.name] ?: f.initial,
                                updatedAt = now
                            )
                        )
                    }
                }
                statusMessage = "✅ ذخیره شد (${if (permanent) "دائمی" else "موقت"})"
                popPage()
            } catch (e: Exception) {
                statusMessage = "❌ خطا در ذخیره: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { popPage() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
            }
            Text("تنظیمات مخفی", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (statusMessage.isNotBlank()) {
            Text(statusMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // The page gets exactly the remaining height (so the LazyColumns
        // inside Home / RecordList measure with finite constraints).
        Box(modifier = Modifier.weight(1f)) {
        when (val page = currentPage) {
            is HiddenPage.Home -> HiddenHomePage(
                cases = cases + archivedCases,
                overrides = overrides,
                onCaseClick = { pushPage(HiddenPage.CaseMenu(it)) },
                onReceiptsClick = { pushPage(HiddenPage.RecordList("fee_receipts", null)) },
                onDeleteOverride = { ov -> scope.launch { dao.deleteOverride(ov) } }
            )
            is HiddenPage.CaseMenu -> HiddenCaseMenuPage(
                caseNumber = page.caseNumber,
                cases = cases + archivedCases,
                engineerCount = engineers.count { it.caseNumber == page.caseNumber },
                feeCount = fees.count { it.caseNumber == page.caseNumber },
                reportCount = reports.count { it.caseNumber == page.caseNumber },
                onTypeClick = { table ->
                    when (table) {
                        "cases" -> scope.launch {
                            val session = loadEditSession(dao, EditTarget("cases", page.caseNumber, null))
                            if (session != null) pushPage(HiddenPage.Edit(EditTarget("cases", page.caseNumber, null)))
                            else statusMessage = "پرونده پیدا نشد"
                        }
                        "specifications" -> scope.launch {
                            if (dao.getSpecification(page.caseNumber) == null) {
                                statusMessage = "برای این پرونده مشخصات ثبت نشده است"
                            } else {
                                pushPage(HiddenPage.Edit(EditTarget("specifications", page.caseNumber, null)))
                            }
                        }
                        "followup_edits" -> scope.launch {
                            if (dao.getFollowUpEdit(page.caseNumber) == null) {
                                statusMessage = "برای این پرونده ویرایش پیگیری ثبت نشده است"
                            } else {
                                pushPage(HiddenPage.Edit(EditTarget("followup_edits", page.caseNumber, null)))
                            }
                        }
                        else -> pushPage(HiddenPage.RecordList(table, page.caseNumber))
                    }
                }
            )
            is HiddenPage.RecordList -> HiddenRecordListPage(
                dao = dao,
                table = page.table,
                caseNumber = page.caseNumber,
                onItemClick = { id -> pushPage(HiddenPage.Edit(EditTarget(page.table, page.caseNumber, id))) }
            )
            is HiddenPage.Edit -> HiddenEditPage(
                dao = dao,
                target = page.target,
                overrides = overrides,
                onSave = { session, values, permanent -> saveSession(session, values, permanent) },
                onCancel = { popPage() }
            )
        }
        }
    }
}

@Composable
private fun HiddenHomePage(
    cases: List<CaseEntity>,
    overrides: List<HiddenOverrideEntity>,
    onCaseClick: (String) -> Unit,
    onReceiptsClick: () -> Unit,
    onDeleteOverride: (HiddenOverrideEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = normalizeSearchTextLocal(query)
    val shownCases = remember(cases, q) {
        if (q.isBlank()) cases else cases.filter {
            normalizeSearchTextLocal(it.caseNumber).contains(q) ||
                normalizeSearchTextLocal(it.owner).contains(q)
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("جستجوی پرونده (شماره / مالک)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(shownCases, key = { it.caseNumber }) { c ->
            NezamCard(modifier = Modifier.fillMaxWidth(), onClick = { onCaseClick(c.caseNumber) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(toPersianChars(c.owner), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(c.caseNumber, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            NezamCard(modifier = Modifier.fillMaxWidth(), onClick = onReceiptsClick) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("🧾 رسیدها", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("همهٔ رسیدها (بدون پرونده)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionTitle("تغییرات دائمی فعال")
            Text(
                "با حذف، مقدار با بروزرسانی بعدی به مقدار سامانه برمی‌گردد.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (overrides.isEmpty()) {
                Text("هیچ تغییر دائمی فعالی نیست.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(overrides, key = { it.id }) { ov ->
            NezamCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${hiddenTableTitle(ov.tableName)} • ${overrideFieldLabel(ov.tableName, ov.fieldName)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(ov.recordKey, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("مقدار جدید: ${ov.value}", fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDeleteOverride(ov) }) {
                        Text("حذف", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenCaseMenuPage(
    caseNumber: String,
    cases: List<CaseEntity>,
    engineerCount: Int,
    feeCount: Int,
    reportCount: Int,
    onTypeClick: (String) -> Unit
) {
    val c = cases.firstOrNull { it.caseNumber == caseNumber }
    val types = listOf(
        "cases" to "پرونده",
        "specifications" to "مشخصات",
        "engineers" to "مهندسین ($engineerCount)",
        "fees" to "حق‌الزحمه‌ها ($feeCount)",
        "reports" to "گزارش‌ها ($reportCount)",
        "followup_edits" to "پیگیری"
    )
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            c?.let { "${toPersianChars(it.owner)} — ${it.caseNumber}" } ?: caseNumber,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text("یک نوع رکورد را انتخاب کنید:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        types.forEach { (table, label) ->
            NezamCard(modifier = Modifier.fillMaxWidth(), onClick = { onTypeClick(table) }) {
                Text(label, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
private fun HiddenRecordListPage(
    dao: NezamDao,
    table: String,
    caseNumber: String?,
    onItemClick: (Long) -> Unit
) {
    var items by remember(table, caseNumber) { mutableStateOf<List<HiddenListItem>?>(null) }
    LaunchedEffect(table, caseNumber) {
        items = loadListItems(dao, table, caseNumber)
    }
    val loaded = items
    if (loaded == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (loaded.isEmpty()) {
        Text("موردی نیست.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(loaded, key = { it.id }) { item ->
            NezamCard(modifier = Modifier.fillMaxWidth(), onClick = { onItemClick(item.id) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (item.subtitle.isNotBlank()) {
                        Text(item.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenEditPage(
    dao: NezamDao,
    target: EditTarget,
    overrides: List<HiddenOverrideEntity>,
    onSave: (EditSession, Map<String, String>, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var session by remember(target) { mutableStateOf<EditSession?>(null) }
    var loadFailed by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) {
        session = loadEditSession(dao, target)
        loadFailed = session == null
    }
    val s = session
    when {
        s == null && !loadFailed -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        s == null -> Text("رکورد پیدا نشد.", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
        else -> {
            val permanentFields = remember(overrides, s) {
                overrides.filter { it.tableName == s.table && it.recordKey == s.recordKey }
                    .map { it.fieldName }.toSet()
            }
            HiddenEditForm(
                session = s,
                permanentFields = permanentFields,
                onSave = { values, permanent -> onSave(s, values, permanent) },
                onCancel = onCancel
            )
        }
    }
}

/** The edit form: one field per editable value (text, checkbox, or
 *  number), the موقت/دائمی choice, and ذخیره. */
@Composable
private fun HiddenEditForm(
    session: EditSession,
    permanentFields: Set<String>,
    onSave: (Map<String, String>, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var values by remember(session) { mutableStateOf(session.fields.associate { it.name to it.initial }) }
    var permanent by remember(session) { mutableStateOf(false) }
    var error by remember(session) { mutableStateOf("") }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(session.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        session.readOnlyLines.forEach { line ->
            Text(line, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "شناسهٔ رکورد و شماره پرونده قابل تغییر نیستند.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Divider()
        session.fields.forEach { field ->
            val hasPermanent = field.name in permanentFields
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(field.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (hasPermanent) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "دائمی",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (hasPermanent) {
                    Text(
                        "این فیلد تغییر دائمی فعال دارد؛ ذخیرهٔ موقت با بروزرسانی بعدی برگردانده می‌شود.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                when (field.kind) {
                    FieldKind.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = values[field.name] == "true",
                            onCheckedChange = { values = values + (field.name to it.toString()) }
                        )
                        Text(if (values[field.name] == "true") "بله" else "خیر", fontSize = 13.sp)
                    }
                    FieldKind.LONG -> OutlinedTextField(
                        value = values[field.name] ?: "",
                        onValueChange = { values = values + (field.name to it) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    FieldKind.TEXT -> OutlinedTextField(
                        value = values[field.name] ?: "",
                        onValueChange = { values = values + (field.name to it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Divider()
        Text("نوع ذخیره", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        NezamSegmentedControl(
            options = listOf("موقت", "دائمی"),
            selectedIndex = if (permanent) 1 else 0,
            onSelect = { permanent = it == 1 },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (permanent) "ذخیرهٔ دائمی: این مقدار بعد از هر بروزرسانی دوباره اعمال می‌شود."
            else "ذخیرهٔ موقت: با بروزرسانی بعدی، مقدار به مقدار سامانه برمی‌گردد.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error.isNotBlank()) {
            Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    // LONG fields must parse; anything else is saved as typed.
                    val bad = session.fields.firstOrNull {
                        it.kind == FieldKind.LONG && (values[it.name] ?: "").toLongOrNull() == null
                    }
                    if (bad != null) {
                        error = "مقدار «${bad.label}» باید یک عدد معتبر باشد."
                        return@Button
                    }
                    error = ""
                    onSave(values, permanent)
                },
                modifier = Modifier.weight(1f)
            ) { Text("ذخیره") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("انصراف") }
        }
    }
}
