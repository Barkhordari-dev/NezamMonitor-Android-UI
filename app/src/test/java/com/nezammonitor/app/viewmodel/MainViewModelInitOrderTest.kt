package com.nezammonitor.app.viewmodel

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * v3.11.5 — regression test for the phone bug in 3.11.4: BOTH detail
 * pages showed «اطلاعات ... بارگذاری نشده است: Attempt to invoke
 * interface method 'java.lang.Object kotlin.Lazy.getValue()' on a null
 * object reference (جستجو: «مهدیه قپاني»)» — and the same for offices.
 *
 * Root cause: MainViewModel declared `directoryRepository` (by lazy)
 * and `directoryDao` AFTER the init block, while init's viewModelScope
 * launch touched both before their initializers ran (viewModelScope
 * uses Dispatchers.Main.immediate, so the coroutine body starts
 * synchronously during init on the main thread). The NPE was swallowed
 * by the runCatching in init into SeedOutcome.Failed, the seed never
 * ran, the tables stayed empty, and both detail pages showed the
 * failure reason. Same initializer-order family as the 3.7.6 launch
 * crash (archivedNumbersFlow declared after init).
 *
 * This test constructs a REAL MainViewModel (Robolectric Application,
 * real file Room database, real snapshot asset) and drives the
 * ViewModel's own load functions. The 3.11.4 proof test drove the
 * seeder directly and never constructed the ViewModel, so it could not
 * catch this. On the 3.11.4 code this test FAILS: the seed outcome is
 * Failed with the Lazy NPE as its reason, and the tables stay empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelInitOrderTest {

    private var viewModel: MainViewModel? = null

    @After
    fun tearDown() {
        // Note: ViewModel.onCleared() is protected, so it cannot be called
        // from the test; dropping the reference is enough here (the
        // singleton test database and the prefs listener are harmless).
        viewModel = null
    }

    /** Idle the main looper until [condition] holds or [timeoutMs] passes. */
    private fun waitUntil(timeoutMs: Long = 60_000, condition: () -> Boolean): Boolean {
        val looper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            looper.idle()
            if (condition()) return true
            Thread.sleep(100)
            looper.idle()
            if (condition()) return true
        }
        return condition()
    }

    private fun waitForSeed(vm: MainViewModel): MainViewModel.DirectorySeedStatus {
        val settled = waitUntil {
            vm.directorySeedStatus.value.state in setOf("seeded", "already", "failed")
        }
        assertTrue("seed never settled: ${vm.directorySeedStatus.value}", settled)
        return vm.directorySeedStatus.value
    }

    @Test
    fun constructingViewModel_seedsDirectory_andDetailPagesFill() {
        val vm = MainViewModel(RuntimeEnvironment.getApplication())
        viewModel = vm

        val status = waitForSeed(vm)
        assertFalse("seed failed: ${status.reason}", status.state == "failed")
        assertFalse("seed failure is the Lazy NPE", status.reason.contains("Lazy"))
        assertTrue(
            "seed state is seeded/already, was ${status.state}",
            status.state == "seeded" || status.state == "already"
        )
        assertEquals(203, status.offices)
        assertEquals(5079, status.engineers)

        // Office: the exact bare number the UI sends for case.office.
        vm.loadDirectoryOfficeByName("۱۹۴")
        assertTrue(
            "office detail never loaded",
            waitUntil { vm.directoryOffice.value != null || vm.directoryMessage.value != null }
        )
        assertNull("office load failed: ${vm.directoryMessage.value}", vm.directoryMessage.value)
        val office = requireNotNull(vm.directoryOffice.value) { "office detail is null" }
        assertEquals("دفتر 194", office.office.name)
        assertEquals(433, office.office.siteId)
        assertEquals(10, office.members.size)

        // Engineer: the exact name from the user's phone error
        // («مهدیه قپاني», mixed Persian/Arabic script).
        vm.loadDirectoryEngineerByName("مهدیه قپاني")
        assertTrue(
            "engineer profile never loaded",
            waitUntil { vm.directoryEngineer.value != null || vm.directoryMessage.value != null }
        )
        assertNull("engineer load failed: ${vm.directoryMessage.value}", vm.directoryMessage.value)
        val profile = requireNotNull(vm.directoryEngineer.value) { "engineer profile is null" }
        assertEquals(7207, profile.engineer.siteId)
        assertEquals("مهدیه", profile.engineer.name)
        assertEquals("قپانی", profile.engineer.family)

        // Engineer in Arabic script (the 3.11.4 proof value).
        vm.loadDirectoryEngineerByName("اميرحسين كريمي")
        assertTrue(
            "second engineer profile never loaded",
            waitUntil {
                val p = vm.directoryEngineer.value
                (p != null && p.engineer.siteId == 3320) || vm.directoryMessage.value != null
            }
        )
        assertEquals(3320, vm.directoryEngineer.value?.engineer?.siteId)
    }

    @Test
    fun secondConstruction_seesAlreadySeeded() {
        val vm = MainViewModel(RuntimeEnvironment.getApplication())
        viewModel = vm
        val status = waitForSeed(vm)
        assertTrue(
            "second construction state is seeded/already, was ${status.state}",
            status.state == "seeded" || status.state == "already"
        )
        assertEquals(203, status.offices)
        assertEquals(5079, status.engineers)
    }
}
