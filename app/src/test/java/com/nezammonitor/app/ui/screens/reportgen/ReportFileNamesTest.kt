package com.nezammonitor.app.ui.screens.reportgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.11.3: the file names for generated reports must never contain a
 * slash — Kotlin's File.copyTo creates missing parent directories, so
 * a slash in the name nested the file two folders deep (the A1 bug).
 * The report CONTENT keeps slashes; only the FILE NAME uses dashes.
 */
class ReportFileNamesTest {

    @Test
    fun outputFileNameSlashDateBecomesDashes() {
        val name = reportOutputFileName("اول", "مالک", "1405/08/23", "")
        assertEquals("گزارش مرحله اول مالک - 1405-08-23.docx", name)
        assertFalse(name.contains("/"))
    }

    @Test
    fun outputFileNameKeepsLtrSuffix() {
        val name = reportOutputFileName("دوم", "مالک", "1405/08/23", " - LTR")
        assertEquals("گزارش مرحله دوم مالک - 1405-08-23 - LTR.docx", name)
        assertFalse(name.contains("/"))
    }

    @Test
    fun outputFileNameDashDateStaysUnchanged() {
        val name = reportOutputFileName("اول", "مالک", "1405-08-23", "")
        assertEquals("گزارش مرحله اول مالک - 1405-08-23.docx", name)
    }

    @Test
    fun caseFolderNameMatchesGeneratedLayout() {
        // <counter> - <owner> - <reversedCase>, counter in Persian digits.
        assertEquals("۰۱ - مالک - 123-1404", reportCaseFolderName(1, "مالک", "1404/123"))
        assertEquals("۱۲ - علی رضایی - 45-1403", reportCaseFolderName(12, "علی رضایی", "1403/45"))
    }

    @Test
    fun reverseCaseNumberForFile() {
        assertEquals("123-1404", reverseCaseNumberForFile("1404/123"))
        assertEquals("45-1403", reverseCaseNumberForFile("1403/45"))
    }

    @Test
    fun sanitizeFileNamePartReplacesSeparators() {
        // The generic sanitizer replaces with "_"; the report DATE
        // gets dashes specifically in reportOutputFileName.
        assertEquals("1405_08_23", sanitizeFileNamePart("1405/08/23"))
        assertEquals("a_b_c", sanitizeFileNamePart("a/b:c"))
        assertEquals("a_b", sanitizeFileNamePart("a\\b"))
    }

    @Test
    fun findReportCaseFolderFindsBySuffix() {
        val root = File(System.getProperty("java.io.tmpdir"), "rfn-test-${System.nanoTime()}")
        try {
            val folder = File(root, reportCaseFolderName(3, "مالک", "1404/123")).apply { mkdirs() }
            File(root, "۰۲ - کسی دیگر - 999-1404").apply { mkdirs() }
            assertEquals(folder, findReportCaseFolder(root, "1404/123"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findReportCaseFolderIgnoresWrongCase() {
        val root = File(System.getProperty("java.io.tmpdir"), "rfn-test2-${System.nanoTime()}")
        try {
            File(root, reportCaseFolderName(1, "مالک", "1404/999")).apply { mkdirs() }
            assertNull(findReportCaseFolder(root, "1404/123"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun findReportCaseFolderOnMissingRoot() {
        assertNull(findReportCaseFolder(File("/definitely/not/here"), "1404/123"))
    }

    @Test
    fun generatedNameHasNoSlashAnywhere() {
        // The full relative path the generator writes must be exactly
        // two levels: outputs/<folder>/<file>.docx.
        val folder = reportCaseFolderName(1, "مالک", "1404/123")
        val file = reportOutputFileName("اول", "مالک", "1405/08/23", "")
        val relative = File(File("outputs", folder), file)
        assertEquals(3, relative.path.split(File.separatorChar).size)
        assertTrue(file.endsWith(".docx"))
    }
}
