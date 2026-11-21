package com.nezammonitor.app.ui.screens.reportgen

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

// ==================== REPORT WIZARD — PURE DOCX LOGIC (v3.11.0) ====================
// No Android/Compose dependency: this file is compiled both into the app and
// into the JVM harness (~/workspace/wordtest-311/), so the exact code that
// runs on the phone is what the harness proves. The template facts it relies
// on were dumped from the real templates (1–4.docx, wordtest-381/2.docx,
// wordtest-382/template3.docx):
//  - Table 1 is the checkbox table: header «ردیف | شرح کنترل | انجام شده |
//    انجام نشده | شامل نمی‌شود», data rows «N | <description> | ☐ | ☐ | ☐»,
//    each ☐ a single run in "Segoe UI Symbol" with no explicit color.
//  - Table 2 is توضیحات: one cell, paragraphs «توضیحات:», intro, «۱- »,
//    «۲- », closing. The numbered items are EMPTY in 1–4.docx and FILLED
//    (with the standard items) in template3.docx.
//  - v3.11.2: fully content-driven — NO header words and NO fixed
//    columns are assumed anymore. The checkbox table is the table with
//    ☐ rows; the header row is the row above the first ☐ row and its
//    cells at the ☐ columns are the status labels (shown in the UI as
//    the file wrote them); the checkbox columns are the cells with ☐,
//    the row-number column is the cell that is only digits, and the
//    description column is the first cell that is neither. The توضیحات
//    cell is the cell with numbered («N- ») paragraphs — the «توضیحات»
//    title is only a hint. A future template may rename its columns,
//    reorder them, add a fourth status column, or rename the section,
//    and the wizard still reads it as-is; the only fallback is the
//    default three status labels when a file has no readable header.
//    The template facts below describe the CURRENT files (1–4.docx),
//    not assumptions the code makes.
//  - The only checkbox mark in the templates is ☐ (no ☑, no w14:checkbox,
//    no FORMCHECKBOX). Marking = replacing ☐ with ☑ in the same run, which
//    is exactly what the app's PDF renderer (DocxToPdf.kt) turns into a
//    stroked black square with a black tick, and what Word shows as ☑.

const val WIZARD_CHECKBOX_EMPTY = '☐'
const val WIZARD_CHECKBOX_MARKED = '☑'

/** The status-column labels, used ONLY as a fallback when the template
 *  file has no readable header row (v3.11.2). The real labels are read
 *  from the file's header row — see WizardCheckboxTable.headers — so a
 *  future template may rename its columns or add a fourth one and the
 *  wizard still shows the file's own words. */
val WIZARD_CHECKBOX_COLUMNS = listOf("انجام شده", "انجام نشده", "شامل نمی‌شود")

/** One row of the checkbox table (index = 0-based position among the
 *  parsed data rows; tableRow = the raw row index in the table, used
 *  for marking, so filtered-out rows cannot shift the mapping). */
data class WizardCheckboxRow(val index: Int, val number: String, val description: String, val tableRow: Int)

/** The parsed checkbox table. [headers] are the status-column labels
 *  read from the file's header row (or the fallback labels when the
 *  file has no readable header) — one label per status column, in
 *  order. [checkboxCols] are the raw column indices of the status
 *  columns and [descriptionCol] the raw index of the description
 *  column. [dataStartRow] is the raw table row the data rows start at
 *  (header row + 1, or the first ☐ row when there is no header) — kept
 *  explicit so a table with a title row above the header still maps
 *  its data rows correctly. */
data class WizardCheckboxTable(
    val headers: List<String>,
    val rows: List<WizardCheckboxRow>,
    val dataStartRow: Int = 1,
    val checkboxCols: List<Int> = emptyList(),
    val descriptionCol: Int = 1
)

/** What the wizard read from the template file. Either part may be absent
 *  when the template has no such table — the wizard then skips that stage
 *  instead of inventing rows. */
data class WizardTemplateData(
    val checkboxTable: WizardCheckboxTable?,
    val descriptionItems: List<String>?
)

/** The user's standard توضیحات items (verbatim, Ardakan municipality):
 *  used when the template's numbered items are all empty. The intro and
 *  closing paragraphs are NOT stored here — they already exist in the
 *  template and are left untouched. */
val STANDARD_DESCRIPTION_ITEMS = listOf(
    "پروژه مذکور تاکنون به مرحله اجرای تأسیسات مکانیکی نرسیده است.",
    "مجری ذی‌صلاحی که احراز صلاحیت وی به‌طور قطعی محرز شده باشد، معرفی نگردیده است."
)

/** Initial توضیحات items for the wizard: the template's items when any of
 *  them is non-blank, otherwise the standard items. */
fun initialDescriptionItems(templateItems: List<String>?): List<String> {
    val fromTemplate = templateItems?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    return if (fromTemplate.isNotEmpty()) fromTemplate else STANDARD_DESCRIPTION_ITEMS
}

// ---------- Checkbox table ----------

/** A cell's text is a row number when it is only digits (Persian,
 *  Arabic or Latin) — «۱», «2», «٠٣». */
private fun isNumberCell(text: String): Boolean {
    val t = text.trim()
    return t.isNotEmpty() && t.all { it in '0'..'9' || it in '۰'..'۹' || it in '٠'..'٩' }
}

/** A checkbox cell holds an empty box or a marked one — after marking,
 *  a cell holds ☑ instead of ☐, and the column detection must still
 *  see it (otherwise countMarkedCheckboxes would miss the very marks
 *  it is counting). */
private fun isCheckboxCell(text: String): Boolean =
    text.contains(WIZARD_CHECKBOX_EMPTY) || text.contains(WIZARD_CHECKBOX_MARKED)

/** Find the checkbox table: the table with data rows carrying checkbox
 *  cells (☐, or ☑ once marked). Nothing about the header words is
 *  row right above the first ☐ row (when there is one), and the status
 *  labels are read from it; when there is no header row the fallback
 *  labels are used. Columns are found by CONTENT, not by index: the
 *  checkbox columns are the cells with ☐, the row-number column is the
 *  cell that is only digits, and the description column is the first
 *  cell that is neither a checkbox cell nor the number cell. So a
 *  future template may rename its columns, reorder them, or add a
 *  fourth status column, and the wizard still reads it as-is. Returns
 *  the table and its parsed rows, or null when no table has ☐ rows. */
fun findCheckboxTable(doc: XWPFDocument): Pair<XWPFTable, WizardCheckboxTable>? {
    for (table in doc.tables) {
        val rows = table.rows
        if (rows.isEmpty()) continue
        // First data row: the first row with checkbox cells (☐ or ☑)
        // in at least two cells (one box could be a stray mark; a
        // checkbox row has several).
        val firstBoxRow = rows.indexOfFirst { row ->
            row.tableCells.count { isCheckboxCell(it.text) } >= 2
        }
        if (firstBoxRow < 0) continue
        // The checkbox columns: the checkbox cells of the first data row.
        val checkboxCols = rows[firstBoxRow].tableCells.mapIndexedNotNull { i, cell ->
            if (isCheckboxCell(cell.text)) i else null
        }
        if (checkboxCols.isEmpty()) continue
        // The header row is the row right above the first data row; its
        // cells at the checkbox columns are the status labels. A missing
        // or blank label falls back to the default label for that
        // position (and then to a generic «ستون N»).
        val headerRow = if (firstBoxRow > 0) rows[firstBoxRow - 1] else null
        val headers = checkboxCols.mapIndexed { i, col ->
            headerRow?.tableCells?.getOrNull(col)?.text?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: WIZARD_CHECKBOX_COLUMNS.getOrNull(i)
                ?: "ستون ${i + 1}"
        }
        // The description column: the first cell of the first data row
        // that is neither a checkbox cell nor the row-number cell.
        val firstCells = rows[firstBoxRow].tableCells
        val numberCol = firstCells.indexOfFirst { isNumberCell(it.text) }
        val descriptionCol = firstCells.indices.firstOrNull { i ->
            i !in checkboxCols && i != numberCol && firstCells[i].text.trim().isNotBlank()
        } ?: 1
        // Data rows: rows from the first ☐ row on that still carry a
        // checkbox cell (☐ or ☑) in a checkbox column (a trailing
        // non-checkbox row in the same table is not a checkbox row).
        val parsedRows = rows.drop(firstBoxRow).mapIndexedNotNull { dropIdx, row ->
            if (checkboxCols.none { c ->
                    row.tableCells.getOrNull(c)?.let { isCheckboxCell(it.text) } == true
                }
            ) {
                null
            } else {
                val cells = row.tableCells
                Triple(
                    cells.getOrNull(numberCol)?.text?.trim() ?: "",
                    cells.getOrNull(descriptionCol)?.text?.trim() ?: "",
                    firstBoxRow + dropIdx
                )
            }
        }.mapIndexed { idx, (number, description, tableRow) ->
            WizardCheckboxRow(idx, number, description, tableRow)
        }
        if (parsedRows.isEmpty()) continue
        return table to WizardCheckboxTable(headers, parsedRows, firstBoxRow, checkboxCols, descriptionCol)
    }
    return null
}

/** The one-checkbox-per-row rule (radio behavior): selecting a column in a
 *  row replaces any previous selection in that row; tapping the already
 *  selected column clears it (no checkbox selected in that row). */
fun selectCheckbox(current: Map<Int, Int>, row: Int, col: Int): Map<Int, Int> {
    val next = current.toMutableMap()
    if (next[row] == col) next.remove(row) else next[row] = col
    return next
}

/** Mark the selected cells in the document: replace ☐ with ☑ in the chosen
 *  column's cell of each selected row, and set the run color to explicit
 *  black (000000) so the tick is black in Word too (the template runs have
 *  no explicit color). Returns how many cells were actually marked.
 *  Cells without a ☐ are left alone; unknown rows/columns are ignored.
 *  The column is the status column's position among the file's own
 *  status columns (parsed.checkboxCols), so a fourth status column
 *  marks exactly like the first three. */
fun applyCheckboxSelections(doc: XWPFDocument, selections: Map<Int, Int>): Int {
    val found = findCheckboxTable(doc) ?: return 0
    val (table, parsed) = found
    var marked = 0
    for ((rowIdx, colIdx) in selections) {
        val parsedRow = parsed.rows.firstOrNull { it.index == rowIdx } ?: continue
        val checkboxCol = parsed.checkboxCols.getOrNull(colIdx) ?: continue
        val row = table.rows.getOrNull(parsedRow.tableRow) ?: continue
        val cell = row.tableCells.getOrNull(checkboxCol) ?: continue
        var cellMarked = false
        for (para in cell.paragraphs) {
            for (run in para.runs) {
                val text = runTextOf(run)
                if (text.contains(WIZARD_CHECKBOX_EMPTY)) {
                    setRunTextOf(run, text.replace(WIZARD_CHECKBOX_EMPTY, WIZARD_CHECKBOX_MARKED))
                    try { run.setColor("000000") } catch (_: Exception) { }
                    cellMarked = true
                }
            }
        }
        if (cellMarked) marked++
    }
    return marked
}

/** How many ☑ marks the checkbox table currently holds (0 when there is no
 *  checkbox table). Used by the wizard to verify the marks landed in the
 *  generated file. */
fun countMarkedCheckboxes(doc: XWPFDocument): Int {
    val found = findCheckboxTable(doc) ?: return 0
    val (table, parsed) = found
    var count = 0
    for (parsedRow in parsed.rows) {
        val row = table.rows.getOrNull(parsedRow.tableRow) ?: continue
        for (checkboxCol in parsed.checkboxCols) {
            val cell = row.tableCells.getOrNull(checkboxCol) ?: continue
            for (para in cell.paragraphs) {
                for (run in para.runs) {
                    if (runTextOf(run).contains(WIZARD_CHECKBOX_MARKED)) count++
                }
            }
        }
    }
    return count
}

// ---------- توضیحات table ----------

/** A numbered توضیحات paragraph: a line starting with a (Persian, Arabic
 *  or Latin) number, a hyphen and optional space — e.g. «۱- », «2- ». */
private val DESCRIPTION_NUMBER_REGEX = Regex("^[۰-۹٠-٩0-9]+[-–—]\\s*")

/** The توضیحات cell: a cell of any table whose paragraphs include
 *  numbered items («۱- », «2- » …). The «توضیحات» title is only a hint,
 *  not a requirement — a future template may rename the section and the
 *  numbered items still identify it. Preference order: a cell with both
 *  the title and numbered items, then a cell with numbered items only,
 *  then (for the old behaviour) a cell with the title only. Null when
 *  no cell has any of these. */
fun findDescriptionCell(doc: XWPFDocument): XWPFTableCell? {
    val cells = doc.tables.flatMap { it.rows }.flatMap { it.tableCells }
    fun hasTitle(cell: XWPFTableCell) =
        cell.paragraphs.any { it.text.trim().startsWith("توضیحات") }
    fun hasNumbered(cell: XWPFTableCell) =
        cell.paragraphs.any { DESCRIPTION_NUMBER_REGEX.containsMatchIn(it.text.trim()) }
    return cells.firstOrNull { hasTitle(it) && hasNumbered(it) }
        ?: cells.firstOrNull { hasNumbered(it) }
        ?: cells.firstOrNull { hasTitle(it) }
}

/** The numbered توضیحات items of the template, in order (empty string for
 *  an empty item). Null when the template has no توضیحات table. */
fun parseDescriptionItems(doc: XWPFDocument): List<String>? {
    val cell = findDescriptionCell(doc) ?: return null
    return cell.paragraphs.mapNotNull { para ->
        val text = para.text.trim()
        val match = DESCRIPTION_NUMBER_REGEX.find(text) ?: return@mapNotNull null
        text.substring(match.range.last + 1).trim()
    }
}

/** Replace the numbered توضیحات items with [items], numbered ۱- … with
 *  Persian digits. Surplus numbered paragraphs are removed; missing ones
 *  are inserted right before the closing paragraph, cloning the last
 *  numbered paragraph's run properties. The intro («توضیحات:» + the
 *  municipality paragraph) and the closing paragraph are untouched.
 *  Returns false when the template has no توضیحات table or no numbered
 *  paragraphs to anchor on. */
fun applyDescriptionItems(doc: XWPFDocument, items: List<String>): Boolean {
    val cell = findDescriptionCell(doc) ?: return false
    fun numberedIndices(): List<Int> = cell.paragraphs.mapIndexedNotNull { i, p ->
        if (DESCRIPTION_NUMBER_REGEX.containsMatchIn(p.text.trim())) i else null
    }
    var numbered = numberedIndices()
    if (numbered.isEmpty()) return false
    // Remove surplus numbered paragraphs from the end, so indices stay valid.
    for (i in numbered.indices.reversed()) {
        if (i >= items.size) cell.removeParagraph(numbered[i])
    }
    numbered = numberedIndices()
    if (numbered.isEmpty()) return false
    // Insert missing paragraphs right before the closing paragraph (the
    // paragraph after the last numbered one), cloning the last numbered
    // paragraph's runs so the new items look identical.
    while (numbered.size < items.size) {
        val targetIndex = numbered.size
        val closing = cell.paragraphs.getOrNull(numbered.last() + 1)
        val newPara = if (closing != null) cell.insertNewParagraph(closing.ctp.newCursor())
            else cell.addParagraph()
        val source = cell.paragraphs[numbered.last()]
        for (run in source.runs) {
            val newRun = newPara.createRun()
            try { newRun.ctr.set(run.ctr.copy()) } catch (_: Exception) { }
        }
        setParagraphText(newPara, "${persianDigits(targetIndex + 1)}- ${items[targetIndex]}")
        numbered = numberedIndices()
    }
    // Write every item (also rewrites the ones just inserted, same text).
    numbered.forEachIndexed { i, pIdx ->
        if (i < items.size) {
            setParagraphText(cell.paragraphs[pIdx], "${persianDigits(i + 1)}- ${items[i]}")
        }
    }
    return true
}

// ---------- File-level helpers ----------

/** Read a template file into what the wizard needs (checkbox table +
 *  توضیحات items). Null when the file cannot be opened. */
fun loadWizardTemplateData(templateFile: File): WizardTemplateData? {
    return try {
        val doc = XWPFDocument(FileInputStream(templateFile))
        val checkbox = findCheckboxTable(doc)?.second
        val descriptions = parseDescriptionItems(doc)
        doc.close()
        WizardTemplateData(checkbox, descriptions)
    } catch (_: Exception) {
        null
    }
}

/** Apply the wizard's edits to an already-filled report file (after
 *  replacePlaceholders): checkbox marks + توضیحات items. Returns true when
 *  the file was written. A missing table is not an error — that edit is
 *  simply skipped. */
fun applyWizardEditsToDocx(
    docxFile: File,
    selections: Map<Int, Int>,
    descriptionItems: List<String>?
): Boolean {
    if (selections.isEmpty() && descriptionItems == null) return false
    return try {
        val doc = XWPFDocument(FileInputStream(docxFile))
        if (selections.isNotEmpty()) applyCheckboxSelections(doc, selections)
        if (descriptionItems != null) {
            val clean = descriptionItems.map { stripDiacritics(it) }
            applyDescriptionItems(doc, clean)
        }
        FileOutputStream(docxFile).use { doc.write(it) }
        doc.close()
        true
    } catch (_: Exception) {
        false
    }
}

// ---------- Small text helpers (local copies — this file has no Android
//   dependency so the JVM harness can compile it on its own) ----------

/** The text of one run, from its <w:t> elements only (same approach as
 *  ReportGeneratorScreen.runText). */
private fun runTextOf(run: XWPFRun): String {
    return try {
        run.ctr.tList.joinToString("") { it.stringValue ?: "" }
    } catch (_: Exception) {
        try { run.text() } catch (_: Exception) { "" }
    }
}

/** Replace a run's text, keeping its properties: the first <w:t> gets the
 *  new text and any extra <w:t> elements are removed. */
private fun setRunTextOf(run: XWPFRun, text: String) {
    try {
        val ctr = run.ctr
        run.setText(text, 0)
        while (ctr.sizeOfTArray() > 1) ctr.removeT(1)
    } catch (_: Exception) {
    }
}

/** Set a paragraph's text in its first run (font/size preserved), clearing
 *  any other runs. Creates a run at 11pt when the paragraph has none. */
private fun setParagraphText(para: XWPFParagraph, text: String) {
    val runs = para.runs
    if (runs.isEmpty()) {
        val run = para.createRun()
        try { run.fontSize = 11 } catch (_: Exception) { }
        run.setText(text, 0)
        return
    }
    setRunTextOf(runs[0], text)
    for (i in 1 until runs.size) setRunTextOf(runs[i], "")
}

/** Small numbers (1..99) in Persian digits. */
private fun persianDigits(n: Int): String =
    n.toString().map { c -> "۰۱۲۳۴۵۶۷۸۹".getOrNull(c - '0') ?: c }.joinToString("")

/** Arabic diacritics (tanwin, short vowels, shadda, sukun, superscript
 *  alef) — same set as ReportGeneratorScreen.stripArabicDiacritics: B
 *  Nazanin has no glyphs for them, so they show as squares in the PDF.
 *  The hamza marks (U+0654/U+0655) are NOT stripped. */
private fun stripDiacritics(input: String): String =
    input.replace(Regex("[\\u064B-\\u0652\\u0670]"), "")
