package com.nezammonitor.app.ui.screens.reportgen

import com.nezammonitor.app.data.ReportEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests for the local report-file lookup ([findLocalServerReportFile]):
 * v3.11.1 — the exact name the Update screen writes, then the cache
 * copy, then any file in the case's downloads folder. v3.11.3 —
 * nezam_output is the single home now (same per-case folder shape as
 * the generated reports); the old downloads/ folders and the cache are
 * only legacy fallbacks. The folders are real temp folders (no Android
 * Context).
 */
class ServerReportFilesTest {

    private lateinit var root: File
    private lateinit var nezamOutputDir: File
    private lateinit var downloadsDir: File
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("srv-reports-test").toFile()
        nezamOutputDir = File(root, "nezam_output").apply { mkdirs() }
        downloadsDir = File(root, "downloads").apply { mkdirs() }
        cacheDir = File(root, "server_reports").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun report(
        id: Long = 5,
        caseNumber: String = "1404/123",
        reportType: String = "گزارش مرحله اول",
        imageName: String = "abc.jpg",
        hasFile: Boolean = true
    ) = ReportEntity(
        id = id, caseNumber = caseNumber, reportType = reportType,
        imageName = imageName, hasFile = hasFile
    )

    private fun lookup(r: ReportEntity = report()) =
        findLocalServerReportFile(nezamOutputDir, downloadsDir, cacheDir, r)

    /** The legacy folder the Update screen wrote before v3.11.3:
     *  downloads/<n>_<owner>_<safeCase> (n is the update order). */
    private fun caseFolder(owner: String = "مالک", caseNumber: String = "1404/123", counter: Int = 1): File {
        val safeCase = caseNumber.replace("/", "_")
        return File(downloadsDir, "${counter}_${owner}_$safeCase").apply { mkdirs() }
    }

    /** The v3.11.3 folder: nezam_output/<n> - <owner> - <reversedCase>. */
    private fun nezamFolder(owner: String = "مالک", caseNumber: String = "1404/123", counter: Int = 1): File {
        return File(nezamOutputDir, reportCaseFolderName(counter, owner, caseNumber)).apply { mkdirs() }
    }

    @Test
    fun exactMatchInNezamOutput() {
        val folder = nezamFolder()
        val file = File(folder, "1_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertEquals(file, lookup())
    }

    @Test
    fun nezamOutputWinsOverLegacyDownloads() {
        val legacy = caseFolder()
        File(legacy, "1_گزارش مرحله اول.jpg").apply { writeText("x") }
        val folder = nezamFolder()
        val file = File(folder, "5_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertEquals(file, lookup())
    }

    @Test
    fun exactMatchInLegacyCaseFolder() {
        val folder = caseFolder()
        val file = File(folder, "1_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertEquals(file, lookup())
    }

    @Test
    fun exactMatchIgnoresUpdateCounter() {
        // The folder counter is the update order, not the case number.
        val folder = caseFolder(counter = 7)
        val file = File(folder, "3_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertEquals(file, lookup())
    }

    @Test
    fun cacheCopyWhenNoFolderMatch() {
        val cached = File(cacheDir, "5_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertEquals(cached, lookup())
    }

    @Test
    fun legacyCacheNameFromHistoryTab() {
        // Before v3.11.3 the history tab wrote the sanitized imageName.
        val legacy = File(cacheDir, "abc.jpg").apply { writeText("x") }
        assertEquals(legacy, lookup())
    }

    @Test
    fun fallbackPrefersFileWithReportType() {
        val folder = caseFolder()
        File(folder, "9_چیز دیگر.png").apply { writeText("x") }
        // Same type, different extension — the exact pattern does not
        // match it, but the fallback prefers it over the other file.
        val typed = File(folder, "2_گزارش مرحله اول.png").apply { writeText("x") }
        assertEquals(typed, lookup())
    }

    @Test
    fun fallbackFirstFileWhenNoTypeMatch() {
        val folder = caseFolder()
        val first = File(folder, "a_random.bin").apply { writeText("x") }
        File(folder, "z_other.bin").apply { writeText("x") }
        assertEquals(first, lookup())
    }

    @Test
    fun noFolderReturnsNull() {
        assertNull(lookup())
    }

    @Test
    fun wrongCaseFolderIsIgnored() {
        val folder = caseFolder(caseNumber = "1404/999")
        File(folder, "1_گزارش مرحله اول.jpg").apply { writeText("x") }
        val wrongNezam = nezamFolder(caseNumber = "1404/999")
        File(wrongNezam, "1_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertNull(lookup())
    }

    @Test
    fun hasFileFalseReturnsNull() {
        val folder = nezamFolder()
        File(folder, "1_گزارش مرحله اول.jpg").apply { writeText("x") }
        assertNull(lookup(report(hasFile = false)))
    }

    @Test
    fun blankImageNameReturnsNull() {
        assertNull(lookup(report(imageName = "")))
    }

    @Test
    fun emptyCacheFileIsIgnored() {
        File(cacheDir, "5_گزارش مرحله اول.jpg").apply { writeText("") } // 0 bytes
        assertNull(lookup())
    }
}
