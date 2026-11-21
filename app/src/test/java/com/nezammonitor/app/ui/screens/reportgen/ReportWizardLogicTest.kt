package com.nezammonitor.app.ui.screens.reportgen

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Tests for the v3.11.0 report wizard's pure docx logic
 * (ReportWizardLogic.kt), against the REAL Ardakan templates
 * (fixtures: src/test/resources/wizard/ — stage2-template.docx is a
 * copy of template 2.docx; template1/3/4.docx are copies of the
 * user's 1/3/4.docx; template3-filled.docx is the filled
 * wordtest-382/template3.docx with the standard items in it).
 */
class ReportWizardLogicTest {

    private fun fixtureCopy(resource: String = "wizard/stage2-template.docx"): File {
        val tmp = File.createTempFile("wizard-test-", ".docx")
        javaClass.classLoader!!.getResourceAsStream(resource)!!.use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        return tmp
    }

    private fun open(file: File): XWPFDocument = XWPFDocument(FileInputStream(file))

    // ---------- Radio rule: one checkbox per row ----------

    @Test
    fun selectCheckbox_selectsAndReplacesInSameRow() {
        var sel: Map<Int, Int> = emptyMap()
        sel = selectCheckbox(sel, 0, 0)
        assertEquals(mapOf(0 to 0), sel)
        // Selecting a second column in the same row replaces the first.
        sel = selectCheckbox(sel, 0, 2)
        assertEquals(mapOf(0 to 2), sel)
        // Other rows are untouched.
        sel = selectCheckbox(sel, 3, 1)
        assertEquals(mapOf(0 to 2, 3 to 1), sel)
    }

    @Test
    fun selectCheckbox_tappingSelectedClearsIt() {
        val sel = selectCheckbox(mapOf(0 to 1, 2 to 0), 0, 1)
        assertEquals(mapOf(2 to 0), sel)
    }

    @Test
    fun selectCheckbox_neverHoldsTwoColumnsOfOneRow() {
        var sel: Map<Int, Int> = emptyMap()
        for (col in 0..2) sel = selectCheckbox(sel, 4, col)
        assertEquals(mapOf(4 to 2), sel)
    }

    // ---------- Checkbox table parsing ----------

    @Test
    fun parseCheckboxTable_stage2HasElevenRows() {
        val file = fixtureCopy()
        open(file).use { doc ->
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            val (_, table) = found!!
            assertEquals(11, table.rows.size)
            // v3.11.2: headers are the file's own status labels (the
            // header cells at the ☐ columns), not all header cells.
            assertEquals(
                listOf("انجام شده", "انجام نشده", "شامل نمی‌شود"),
                table.headers
            )
            assertTrue(table.rows.all { it.description.isNotBlank() })
            // The template numbers its rows with Latin digits.
            assertEquals("1", table.rows[0].number)
        }
    }

    @Test
    fun parseCheckboxTable_noTableReturnsNull() {
        XWPFDocument().use { doc ->
            assertNull(findCheckboxTable(doc))
            assertEquals(0, countMarkedCheckboxes(doc))
            assertEquals(0, applyCheckboxSelections(doc, mapOf(0 to 0)))
        }
    }

    // ---------- Checkbox table parsing: every real template ----------

    @Test
    fun parseCheckboxTable_template1HasSevenRows() {
        val file = fixtureCopy("wizard/template1.docx")
        open(file).use { doc ->
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            assertEquals(7, found!!.second.rows.size)
            assertEquals(1, found.second.dataStartRow)
        }
    }

    @Test
    fun parseCheckboxTable_template3HasSixRows() {
        val file = fixtureCopy("wizard/template3.docx")
        open(file).use { doc ->
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            assertEquals(6, found!!.second.rows.size)
        }
    }

    @Test
    fun parseCheckboxTable_template4HasSixRows() {
        val file = fixtureCopy("wizard/template4.docx")
        open(file).use { doc ->
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            assertEquals(6, found!!.second.rows.size)
        }
    }

    @Test
    fun parseDescriptionItems_template3FilledHasStandardItems() {
        val file = fixtureCopy("wizard/template3-filled.docx")
        open(file).use { doc ->
            assertEquals(STANDARD_DESCRIPTION_ITEMS, parseDescriptionItems(doc))
        }
    }

    // ---------- Tables found by content, not by index (v3.11.1) ----------

    /** A table with a title row above the header row: the header is in
     *  row 1, not row 0 — the old code (header = rows[0]) missed it. */
    @Test
    fun findCheckboxTable_headerNotInFirstRow() {
        XWPFDocument().use { doc ->
            val table = doc.createTable(4, 5)
            table.rows[0].tableCells[0].setText("جدول کنترل عملیات اجرایی")
            listOf("ردیف", "شرح کنترل", "انجام شده", "انجام نشده", "شامل نمی‌شود")
                .forEachIndexed { i, h -> table.rows[1].tableCells[i].setText(h) }
            for (r in 2..3) {
                table.rows[r].tableCells[0].setText((r - 1).toString())
                table.rows[r].tableCells[1].setText("مورد ${r - 1}")
                for (c in 2..4) table.rows[r].tableCells[c].setText("☐")
            }
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            assertEquals(2, found!!.second.rows.size)
            assertEquals(2, found.second.dataStartRow)
            // Marking still lands in the right raw row.
            assertEquals(1, applyCheckboxSelections(doc, mapOf(0 to 1)))
            assertEquals(1, countMarkedCheckboxes(doc))
            assertTrue(table.rows[2].tableCells[3].text.contains('☑'))
            assertTrue(table.rows[3].tableCells[3].text.contains('☐'))
        }
    }

    /** A table with no header words at all, only ☐ rows: the fallback
     *  still finds it. */
    @Test
    fun findCheckboxTable_boxRowsWithoutHeaderWords() {
        XWPFDocument().use { doc ->
            val table = doc.createTable(2, 5)
            listOf("شماره", "شرح", "الف", "ب", "ج")
                .forEachIndexed { i, h -> table.rows[0].tableCells[i].setText(h) }
            table.rows[1].tableCells[0].setText("1")
            table.rows[1].tableCells[1].setText("مورد اول")
            for (c in 2..4) table.rows[1].tableCells[c].setText("☐")
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            assertEquals(1, found!!.second.rows.size)
            assertEquals(1, found.second.dataStartRow)
            assertEquals(1, applyCheckboxSelections(doc, mapOf(0 to 2)))
            assertTrue(table.rows[1].tableCells[4].text.contains('☑'))
        }
    }

    /** The توضیحات cell is the SECOND cell of the table and «توضیحات:»
     *  is its second paragraph — the old code only looked at the first
     *  paragraph of the first cell of each table. */
    @Test
    fun findDescriptionCell_notFirstCellOrParagraph() {
        XWPFDocument().use { doc ->
            val table = doc.createTable(1, 2)
            table.rows[0].tableCells[0].setText("چیزی دیگر")
            val cell = table.rows[0].tableCells[1]
            cell.paragraphs[0].createRun().setText("مقدمه‌ای قبل از توضیحات")
            cell.addParagraph().createRun().setText("توضیحات:")
            cell.addParagraph().createRun().setText("۱- مورد اول")
            cell.addParagraph().createRun().setText("۲- مورد دوم")
            assertNotNull(findDescriptionCell(doc))
            assertEquals(listOf("مورد اول", "مورد دوم"), parseDescriptionItems(doc))
        }
    }

    // ---------- Checkbox marking ----------

    @Test
    fun applyCheckboxSelections_marksExactlyTheSelectedCells() {
        val file = fixtureCopy()
        val selections = mapOf(0 to 0, 1 to 2, 5 to 1)
        open(file).use { doc ->
            assertEquals(3, applyCheckboxSelections(doc, selections))
            FileOutputStream(file).use { doc.write(it) }
        }
        open(file).use { doc ->
            assertEquals(3, countMarkedCheckboxes(doc))
            val (table, _) = findCheckboxTable(doc)!!
            // Row 0: only column 0 is marked.
            assertTrue(table.rows[1].tableCells[2].text.contains('☑'))
            assertTrue(table.rows[1].tableCells[3].text.contains('☐'))
            assertTrue(table.rows[1].tableCells[4].text.contains('☐'))
            // Row 1: only column 2 is marked. Row 5: only column 1.
            assertTrue(table.rows[2].tableCells[4].text.contains('☑'))
            assertTrue(table.rows[6].tableCells[3].text.contains('☑'))
            // An untouched row has no mark anywhere.
            assertTrue((2..4).none { table.rows[4].tableCells[it].text.contains('☑') })
            // The marked runs kept the template's "Segoe UI Symbol" font
            // and got explicit black (000000).
            for ((row, col) in selections) {
                val cell = table.rows[row + 1].tableCells[2 + col]
                for (para in cell.paragraphs) for (run in para.runs) {
                    val text = run.ctr.tList.joinToString("") { it.stringValue ?: "" }
                    if (text.contains('☑')) {
                        assertEquals("Segoe UI Symbol", run.fontFamily)
                        assertEquals("000000", run.color)
                    }
                }
            }
        }
    }

    @Test
    fun applyCheckboxSelections_unknownRowOrColumnIsIgnored() {
        val file = fixtureCopy()
        open(file).use { doc ->
            assertEquals(0, applyCheckboxSelections(doc, mapOf(99 to 0, 0 to 5)))
            assertEquals(0, countMarkedCheckboxes(doc))
        }
    }

    // ---------- توضیحات parsing / applying ----------

    @Test
    fun parseDescriptionItems_stage2HasTwoEmptyItems() {
        val file = fixtureCopy()
        open(file).use { doc ->
            val items = parseDescriptionItems(doc)
            assertNotNull(items)
            assertEquals(listOf("", ""), items)
            // Empty template items fall back to the standard items.
            assertEquals(STANDARD_DESCRIPTION_ITEMS, initialDescriptionItems(items))
        }
    }

    @Test
    fun parseDescriptionItems_noTableReturnsNull() {
        XWPFDocument().use { doc ->
            assertNull(parseDescriptionItems(doc))
            assertFalse(applyDescriptionItems(doc, listOf("x")))
            // …but a missing table still falls back to the standard items
            // for the wizard's display; generation simply skips the edit.
            assertEquals(STANDARD_DESCRIPTION_ITEMS, initialDescriptionItems(null))
        }
    }

    @Test
    fun applyDescriptionItems_writesItemsAndKeepsIntroAndClosing() {
        val file = fixtureCopy()
        open(file).use { doc ->
            assertTrue(applyDescriptionItems(doc, STANDARD_DESCRIPTION_ITEMS))
            FileOutputStream(file).use { doc.write(it) }
        }
        open(file).use { doc ->
            assertEquals(STANDARD_DESCRIPTION_ITEMS, parseDescriptionItems(doc))
            val texts = findDescriptionCell(doc)!!.paragraphs.map { it.text }
            assertTrue(texts[1].startsWith("به استحضار شهرداری محترم اردکان"))
            assertTrue(texts.last().startsWith("بدین‌وسیله"))
            assertTrue(texts[2].startsWith("۱- "))
            assertTrue(texts[3].startsWith("۲- "))
        }
    }

    @Test
    fun applyDescriptionItems_insertsExtraItemsBeforeClosing() {
        val file = fixtureCopy()
        val three = STANDARD_DESCRIPTION_ITEMS + "مورد سوم آزمایشی."
        open(file).use { doc ->
            assertTrue(applyDescriptionItems(doc, three))
            FileOutputStream(file).use { doc.write(it) }
        }
        open(file).use { doc ->
            assertEquals(three, parseDescriptionItems(doc))
            val texts = findDescriptionCell(doc)!!.paragraphs.map { it.text }
            assertEquals(6, texts.size)
            assertTrue(texts[4].startsWith("۳- "))
            assertTrue(texts.last().startsWith("بدین‌وسیله"))
        }
    }

    @Test
    fun applyDescriptionItems_removesSurplusItems() {
        val file = fixtureCopy()
        open(file).use { doc ->
            applyDescriptionItems(doc, STANDARD_DESCRIPTION_ITEMS + "مورد سوم.")
            FileOutputStream(file).use { doc.write(it) }
        }
        open(file).use { doc ->
            assertTrue(applyDescriptionItems(doc, listOf("فقط یک مورد.")))
            FileOutputStream(file).use { doc.write(it) }
        }
        open(file).use { doc ->
            assertEquals(listOf("فقط یک مورد."), parseDescriptionItems(doc))
            val texts = findDescriptionCell(doc)!!.paragraphs.map { it.text }
            assertEquals(4, texts.size)
            assertTrue(texts.last().startsWith("بدین‌وسیله"))
        }
    }

    // ---------- File-level helper ----------

    @Test
    fun applyWizardEditsToDocx_appliesBothEdits() {
        val file = fixtureCopy()
        assertTrue(
            applyWizardEditsToDocx(
                file,
                selections = mapOf(2 to 1),
                descriptionItems = STANDARD_DESCRIPTION_ITEMS
            )
        )
        open(file).use { doc ->
            assertEquals(1, countMarkedCheckboxes(doc))
            assertEquals(STANDARD_DESCRIPTION_ITEMS, parseDescriptionItems(doc))
        }
    }

    @Test
    fun applyWizardEditsToDocx_noEditsDoesNothing() {
        val file = fixtureCopy()
        assertFalse(applyWizardEditsToDocx(file, emptyMap(), null))
    }

    @Test
    fun loadWizardTemplateData_readsBothTables() {
        val file = fixtureCopy()
        val data = loadWizardTemplateData(file)
        assertNotNull(data)
        assertEquals(11, data!!.checkboxTable!!.rows.size)
        assertEquals(listOf("", ""), data.descriptionItems)
    }

    // ---------- Mutated template (v3.11.2): nothing is hardcoded ----------

    /** The wizard must read EVERYTHING from the file: build a mutated
     *  template1.docx — table-0 title changed, header «شرح کنترل»
     *  renamed to «شرح بازدید», one checkbox row removed and one added,
     *  a third empty numbered item «۳- » in توضیحات, the «توضیحات:»
     *  title renamed, and a FOURTH status column («نامشخص») added —
     *  then parse + generation must still work on it exactly. */
    @Test
    fun mutatedTemplate_parsesAndGenerates() {
        val file = fixtureCopy("wizard/template1.docx")
        var removedDescription = ""
        // --- mutate ---
        open(file).use { doc ->
            // 1. Table-0 title changed (logic never reads it).
            replaceRunText(doc, "مرحله اول", "مرحله اول — ویرایش آزمایشی")
            // 2. Header word changed: «شرح کنترل» -> «شرح بازدید».
            replaceRunText(doc, "شرح کنترل", "شرح بازدید")
            // 3. Fourth status column «نامشخص» on every row of the
            // checkbox table (header + data).
            val (rawTable, parsedBefore) = findCheckboxTable(doc)!!
            removedDescription = parsedBefore.rows[1].description
            for ((i, row) in rawTable.rows.withIndex()) {
                val cell = row.addNewTableCell()
                cell.setText(if (i == parsedBefore.dataStartRow - 1) "نامشخص" else "☐")
            }
            // 4. Remove one data row, add one (number «۸»).
            rawTable.removeRow(parsedBefore.dataStartRow + 1)
            val newRow = rawTable.createRow()
            while (newRow.tableCells.size < 6) newRow.addNewTableCell()
            newRow.tableCells[0].setText("۸")
            newRow.tableCells[1].setText("مورد آزمایشی تازه")
            for (c in 2..5) newRow.tableCells[c].setText("☐")
            // 5. Third empty numbered item + renamed توضیحات title.
            val cell = findDescriptionCell(doc)!!
            val closing = cell.paragraphs.last()
            val third = cell.insertNewParagraph(closing.ctp.newCursor())
            third.createRun().setText("۳- ")
            replaceRunText(doc, "توضیحات:", "یادداشت‌ها:")
            FileOutputStream(file).use { doc.write(it) }
        }
        // --- parse the mutated file ---
        open(file).use { doc ->
            assertTrue(doc.tables[0].text.contains("ویرایش آزمایشی"))
            val found = findCheckboxTable(doc)
            assertNotNull(found)
            val (rawTable, table) = found!!
            assertEquals(7, table.rows.size)
            assertEquals(
                listOf("انجام شده", "انجام نشده", "شامل نمی‌شود", "نامشخص"),
                table.headers
            )
            assertEquals(listOf(2, 3, 4, 5), table.checkboxCols)
            assertTrue(table.rows.none { it.description == removedDescription })
            assertTrue(table.rows.any { it.number == "۸" && it.description == "مورد آزمایشی تازه" })
            // Marking the FOURTH column works like the first three.
            assertEquals(1, applyCheckboxSelections(doc, mapOf(0 to 3)))
            assertEquals(1, countMarkedCheckboxes(doc))
            val row0 = table.rows[0]
            assertTrue(
                rawTable.rows[row0.tableRow].tableCells[5].text.contains('☑')
            )
            // The توضیحات cell is found by its numbered items alone.
            assertEquals(listOf("", "", ""), parseDescriptionItems(doc))
            // The «توضیحات:» title was renamed — the cell is found by
            // its numbered paragraphs alone, not by the title.
            assertFalse(findDescriptionCell(doc)!!.text.contains("توضیحات"))
            assertEquals(STANDARD_DESCRIPTION_ITEMS, initialDescriptionItems(parseDescriptionItems(doc)))
            // A third item writes back into the third slot, closing last.
            val three = STANDARD_DESCRIPTION_ITEMS + "مورد سوم جهش‌یافته."
            assertTrue(applyDescriptionItems(doc, three))
            assertEquals(three, parseDescriptionItems(doc))
            val texts = findDescriptionCell(doc)!!.paragraphs.map { it.text }
            assertEquals(6, texts.size)
            assertTrue(texts[4].startsWith("۳- "))
            assertTrue(texts.last().startsWith("بدین‌وسیله"))
            FileOutputStream(file).use { doc.write(it) }
        }
    }

    /** Replace [old] with [new] in every paragraph of the document
     *  (body paragraphs + every table cell) — the mutation helper.
     *  Paragraph-level: the text may span several runs (the table-0
     *  title does), so a run-level replace would miss it. */
    private fun replaceRunText(doc: XWPFDocument, old: String, new: String) {
        val paras = doc.paragraphs +
            doc.tables.flatMap { it.rows }.flatMap { it.tableCells }.flatMap { it.paragraphs }
        for (para in paras) {
            if (para.runs.isEmpty()) continue
            val joined = para.runs.joinToString("") { run ->
                run.ctr.tList.joinToString("") { it.stringValue ?: "" }
            }
            if (!joined.contains(old)) continue
            para.runs[0].setText(joined.replace(old, new), 0)
            for (i in 1 until para.runs.size) para.runs[i].setText("", 0)
        }
    }
}
