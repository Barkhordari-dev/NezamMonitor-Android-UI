package com.nezammonitor.app.ui.screens.reportgen

import java.io.File

// ==================== REPORT FILE NAMES (v3.11.3) ====================
// Pure helpers (no Android dependency) shared by generated reports and
// downloaded server reports, so the JVM unit tests and the JVM harness
// can compile and test them directly.
//
// The two rules that matter:
//
// 1. A FILE NAME must never contain '/'. `File.copyTo` creates missing
//    parent directories, so a slash in the name silently becomes nested
//    folders: the Persian report date "1405/08/23" in a file name made
//    `گزارش مرحله اول … - 1405/08/23.docx` land as
//    `…/گزارش مرحله اول … - 1405/08/23.docx` — a folder "… - 1405",
//    inside it "08", inside it "23.docx". The report CONTENT keeps its
//    slashes; only the NAME is sanitized (slashes → dashes).
//
// 2. One case folder name, everywhere: "<counter> - <owner> -
//    <reversedCase>" (counter = the position in the current list,
//    1-based, Persian digits, zero-padded to two; the case number
//    reversed, "1404/123" → "123-1404"). Generated reports live in
//    <outputs>/<folder>/ and downloaded server reports in
//    <reportPath>/nezam_output/<folder>/ — the same shape, only the
//    root differs.

/** Characters that must never appear in a file or folder name: path
 *  separators and characters illegal on some filesystems. */
internal fun sanitizeFileNamePart(text: String): String =
    text.replace(Regex("[/\\\\:*?\"<>|]"), "_")

private fun persianDigits(input: String): String = input
    .replace("0", "۰").replace("1", "۱").replace("2", "۲").replace("3", "۳")
    .replace("4", "۴").replace("5", "۵").replace("6", "۶").replace("7", "۷")
    .replace("8", "۸").replace("9", "۹")

/** "1404/123" → "123-1404" (the case number, reversed for folder names). */
internal fun reverseCaseNumberForFile(caseNumber: String): String {
    if (!caseNumber.contains("/")) return caseNumber
    return caseNumber.split("/").reversed().joinToString("-")
}

/** One case folder name, shared by generated reports and downloaded
 *  server reports: "<counter> - <owner> - <reversedCase>". */
internal fun reportCaseFolderName(counter: Int, owner: String, caseNumber: String): String {
    val folderNum = persianDigits(counter.toString().padStart(2, '0'))
    return "$folderNum - ${sanitizeFileNamePart(owner)} - ${reverseCaseNumberForFile(caseNumber)}"
}

/** The file name of a generated Word report. The date uses dashes,
 *  never slashes — a slash here used to create nested folders (see the
 *  header comment). The LTR suffix is appended after the date. */
internal fun reportOutputFileName(
    stageName: String,
    owner: String,
    reportDate: String,
    ltrSuffix: String
): String {
    val safeOwner = sanitizeFileNamePart(owner)
    val safeDate = reportDate.replace("/", "-")
    return "گزارش مرحله $stageName $safeOwner - $safeDate$ltrSuffix.docx"
}

/** Finds the case folder inside [dir] by the reversed case number
 *  suffix (" - <reversedCase>"), or null when there is none. */
internal fun findReportCaseFolder(dir: File, caseNumber: String): File? {
    if (!dir.isDirectory) return null
    val suffix = " - ${reverseCaseNumberForFile(caseNumber)}"
    return dir.listFiles()?.firstOrNull { it.isDirectory && it.name.endsWith(suffix) }
}
