package com.nezammonitor.app.ui.screens.reportgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for the v3.11.2 forgiving findTemplate: the stage number may sit
 * anywhere in the file name, the extension is case-insensitive, and the
 * other templates folder (signed vs unsigned) is tried as a fallback.
 * Nothing about the template's content is involved here — the checkbox
 * rows and توضیحات items always come from the file itself.
 */
class FindTemplateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun touch(dir: File, name: String): File {
        dir.mkdirs()
        return File(dir, name).apply { writeText("x") }
    }

    @Test
    fun exactNamesStillWin() {
        val dir = tmp.newFolder("templates")
        val one = touch(dir, "1.docx")
        touch(dir, "template1.docx")
        assertEquals(one, findTemplate(dir, "اول"))
    }

    @Test
    fun stageNumberAnywhereInName() {
        val dir = tmp.newFolder("templates")
        val one = touch(dir, "template1.docx")
        assertEquals(one, findTemplate(dir, "اول"))
        val two = touch(dir, "مرحله 2.docx")
        assertEquals(two, findTemplate(dir, "دوم"))
        // A name with the number first sorts before "template1", so the
        // loose match picks it for stage 1.
        val report = touch(dir, "1 - گزارش.docx")
        assertEquals(report, findTemplate(dir, "اول"))
    }

    @Test
    fun persianDigitInName() {
        val dir = tmp.newFolder("templates")
        val two = touch(dir, "گزارش مرحله ۲.docx")
        assertEquals(two, findTemplate(dir, "دوم"))
    }

    @Test
    fun extensionIsCaseInsensitive() {
        val dir = tmp.newFolder("templates")
        val three = touch(dir, "3.DOCX")
        assertEquals(three, findTemplate(dir, "سوم"))
    }

    @Test
    fun digitRunMustEqualStageNumber() {
        val dir = tmp.newFolder("templates")
        // "12" contains "1" but is not stage 1 — and no other stage-1
        // file exists, so stage 1 must NOT match it.
        touch(dir, "template12.docx")
        assertNull(findTemplate(dir, "اول"))
        // ...but it IS a stage-12 file, which is out of range (0) — and
        // stage 2 must not match it either ("12" != "2").
        assertNull(findTemplate(dir, "دوم"))
    }

    @Test
    fun fallbackFolderIsTried() {
        val main = tmp.newFolder("templates")
        val signed = tmp.newFolder("signed")
        val two = touch(signed, "2.docx")
        assertEquals(two, findTemplate(main, "دوم", fallbackDir = signed))
        // Main folder wins when it has the file.
        val mainTwo = touch(main, "template2.docx")
        assertEquals(mainTwo, findTemplate(main, "دوم", fallbackDir = signed))
    }

    @Test
    fun ordinalWordMatches() {
        val dir = tmp.newFolder("templates")
        val four = touch(dir, "گزارش مرحله چهارم.docx")
        assertEquals(four, findTemplate(dir, "چهارم"))
    }

    @Test
    fun noMatchReturnsNull() {
        val dir = tmp.newFolder("templates")
        touch(dir, "readme.txt")
        touch(dir, "5.docx")
        assertNull(findTemplate(dir, "اول"))
    }

    @Test
    fun missingFoldersReturnNull() {
        val missing = File(tmp.root, "no-such-dir")
        assertNull(findTemplate(missing, "اول", fallbackDir = File(tmp.root, "also-missing")))
    }
}
