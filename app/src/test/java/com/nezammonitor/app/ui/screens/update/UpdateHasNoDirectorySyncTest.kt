package com.nezammonitor.app.ui.screens.update

import com.nezammonitor.app.data.DirectoryRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v3.11.3 scope change (user decision 2026-11-20): there is NO in-app
 * directory update anymore — the bundled snapshot is replaced by the
 * user in a future version. The Update flow must perform ZERO directory
 * network calls, and its result card must contain no directory lines.
 * Two guards:
 *  1. Reflection: DirectoryRepository exposes no refresh/fetch/search/
 *     sync method at all — the whole site-fetching layer is gone, so
 *     no caller (Update screen or otherwise) can trigger one.
 *  2. Source: UpdateScreen.kt and MainViewModel.kt contain none of the
 *     removed entry points or UI strings (checkbox, standalone button,
 *     result-card lines, per-item refresh).
 * The detail/profile screens are still covered by the seeder tests —
 * they read the local tables only.
 */
class UpdateHasNoDirectorySyncTest {

    private fun sourceFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File(System.getProperty("user.dir"), relative)
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("source file not found from ${System.getProperty("user.dir")}: $relative")
    }

    @Test
    fun directoryRepositoryHasNoNetworkMethods() {
        val names = DirectoryRepository::class.java.declaredMethods.map { it.name }
        assertFalse("methods: $names", names.any { it.contains("refresh", ignoreCase = true) })
        assertFalse("methods: $names", names.any { it.contains("fetch", ignoreCase = true) })
        assertFalse("methods: $names", names.any { it.contains("search", ignoreCase = true) })
        assertFalse("methods: $names", names.any { it.contains("sync", ignoreCase = true) })
        assertTrue("methods: $names", names.contains("ensureSnapshotSeeded"))
    }

    @Test
    fun updateScreenHasNoDirectoryUpdate() {
        val src = sourceFile("src/main/java/com/nezammonitor/app/ui/screens/update/UpdateScreen.kt")
            .readText(Charsets.UTF_8)
        val gone = listOf(
            "refreshDirectoryForCases", "update_directory", "directorySync",
            "directoryLastSync", "directoryRefForCase", "DirectoryRepository",
            "بروزرسانی دفاتر و مهندسین", "بروزرسانی فقط دفاتر",
            "دفاتر و مهندسین همین پرونده‌ها", "آخرین بروزرسانی دفاتر"
        )
        for (token in gone) {
            assertFalse("UpdateScreen still contains: $token", src.contains(token))
        }
        // The result card reports only cases/fees/reports.
        assertTrue(src.contains("بروزرسانی کامل شد"))
    }

    @Test
    fun viewModelHasNoDirectoryUpdateEntryPoints() {
        val src = sourceFile("src/main/java/com/nezammonitor/app/viewmodel/MainViewModel.kt")
            .readText(Charsets.UTF_8)
        val gone = listOf(
            "refreshDirectoryForCases", "refreshCurrentDirectoryOffice",
            "refreshCurrentDirectoryEngineer", "refreshOfficeByName",
            "refreshEngineerByName", "directorySync", "DirectorySyncUiState",
            "loadDirectoryLastSync", "cancelDirectorySync", "directory_last_sync"
        )
        for (token in gone) {
            assertFalse("MainViewModel still contains: $token", src.contains(token))
        }
        // The local-only loaders are still there.
        assertTrue(src.contains("loadDirectoryOfficeByName"))
        assertTrue(src.contains("loadDirectoryEngineerByName"))
    }

    @Test
    fun detailScreensHaveNoRefreshButtons() {
        val office = sourceFile("src/main/java/com/nezammonitor/app/ui/screens/directory/DirectoryOfficeDetailScreen.kt")
            .readText(Charsets.UTF_8)
        val engineer = sourceFile("src/main/java/com/nezammonitor/app/ui/screens/directory/DirectoryEngineerProfileScreen.kt")
            .readText(Charsets.UTF_8)
        assertFalse(office.contains("refreshCurrentDirectoryOffice"))
        assertFalse(engineer.contains("refreshCurrentDirectoryEngineer"))
    }
}
