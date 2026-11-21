package com.nezammonitor.app.ui.screens.reportgen

import com.nezammonitor.app.data.CaseEntity
import com.nezammonitor.app.data.EngineerEntity
import com.nezammonitor.app.data.SpecificationEntity
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Tests for the v3.11.1 Persian-script fix (bug A): the API returns
 * names in Arabic script (ي/ك), and the wizard's stage 1 showed them
 * raw. Every filled value — auto values from reportFieldValues and
 * wizard-edited values through replaceInParagraph — must come out in
 * Persian script. The template's own fixed text is untouched.
 */
class ReportFieldValuesTest {

    private fun fixtureCopy(resource: String = "wizard/stage2-template.docx"): File {
        val tmp = File.createTempFile("field-values-test-", ".docx")
        javaClass.classLoader!!.getResourceAsStream(resource)!!.use { input ->
            FileOutputStream(tmp).use { output -> input.copyTo(output) }
        }
        return tmp
    }

    private fun arabicCase() = CaseEntity(
        caseNumber = "1404/514",
        owner = "محمد كمالي اردكاني"
    )

    private fun arabicSpec() = SpecificationEntity(
        renovationCode = "123",
        blockTitle = "بلوك1",
        permitNumber = "1404/0805",
        permitDate = "1404/11/19",
        blockCount = "1",
        usageType = "مسكوني",
        structureType = "آجري",
        buildingGroup = "الف",
        floors = "1"
    )

    private fun arabicEngineers() = listOf(
        EngineerEntity(caseNumber = "1404/514", discipline = "هماهنگ كننده", name = "مهديه قپاني"),
        EngineerEntity(caseNumber = "1404/514", discipline = "مكانيك", name = "ايمان ناظمي اردكاني")
    )

    @Test
    fun reportFieldValues_convertsArabicScriptToPersian() {
        val values = reportFieldValues(
            arabicCase(), arabicSpec(), arabicEngineers(),
            "1405/08/29", false, "محمدمهدي برخورداري"
        )
        for ((key, value) in values) {
            assertFalse(
                "value of $key still has Arabic letters: $value",
                value.contains('ي') || value.contains('ك')
            )
        }
        assertEquals("محمد کمالی اردکانی", values["«مالک»"])
        assertEquals("بلوک1", values["«عنوان_بلوک»"])
        assertEquals("مسکونی", values["«نوع_کاربری»"])
        assertEquals("آجری", values["«نوع_سازه»"])
        assertEquals("مهدیه قپانی", values["«هماهنگکننده»"])
        assertEquals("ایمان ناظمی اردکانی", values["«ناظر_مکانیک»"])
        assertEquals("محمدمهدی برخورداری", values["«نام ناظر»"])
    }

    @Test
    fun replaceInParagraph_wizardOverridesComeOutPersian() {
        val file = fixtureCopy()
        XWPFDocument(FileInputStream(file)).use { doc ->
            val para = doc.paragraphs.first { it.text.contains("«مالک»") }
            replaceInParagraph(
                para, arabicCase(), arabicSpec(), arabicEngineers(),
                "1405/08/29", false, "محمدمهدي برخورداري",
                mapOf("«مالک»" to "علي يوسفي")
            )
            val text = para.text
            // The edited value, converted.
            assertTrue(text.contains("علی یوسفی"))
            assertFalse(text.contains("علي"))
            assertFalse(text.contains("يوسفي"))
            // The auto values, converted.
            assertTrue(text.contains("محمدمهدی برخورداری"))
            assertTrue(text.contains("مهدیه قپانی"))
            assertTrue(text.contains("بلوک1"))
            assertFalse(text.contains("مهديه"))
            assertFalse(text.contains("بلوك"))
            // No Arabic-script letter anywhere in the filled paragraph.
            assertFalse("paragraph still has Arabic ي: $text", text.contains('ي'))
            assertFalse("paragraph still has Arabic ك: $text", text.contains('ك'))
        }
    }

    @Test
    fun replaceInParagraph_asciiQuotedEngineerPlaceholderIsFilled() {
        // v3.11.1: template 4 writes the engineer placeholder with
        // ASCII double quotes ("نام ناظر") instead of guillemets — it
        // was never substituted at all until this fix.
        val file = fixtureCopy("wizard/template4.docx")
        XWPFDocument(FileInputStream(file)).use { doc ->
            val para = doc.paragraphs.first { it.text.contains("«مالک»") }
            assertTrue(para.text.contains("\"نام ناظر\""))
            replaceInParagraph(
                para, arabicCase(), arabicSpec(), arabicEngineers(),
                "1405/08/29", false, "محمدمهدي برخورداري"
            )
            val text = para.text
            assertTrue(text.contains("محمدمهدی برخورداری"))
            assertFalse(text.contains("\"نام ناظر\""))
            assertFalse("paragraph still has Arabic ي: $text", text.contains('ي'))
            assertFalse("paragraph still has Arabic ك: $text", text.contains('ك'))
        }
    }
}
