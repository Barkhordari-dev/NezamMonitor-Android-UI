package com.nezammonitor.app.ui.screens.directory

import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
// v3.11.6: ui-test 1.5.4 has no assertDoesNotExist (added in 1.7);
// non-existence is asserted via onAllNodesWithText(...).assertCountEquals(0).
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.nezammonitor.app.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v3.11.6 — regression tests for the phone crashes in 3.11.5: opening an
 * office detail or an engineer profile crashed the app with a bare
 * java.lang.NullPointerException inside LazyColumn composition
 * (DirectoryOfficeDetailScreen.kt:148, DirectoryEngineerProfileScreen
 * .kt:199).
 *
 * Root cause: both screens read their data through a DELEGATED property
 * (`val detail by viewModel.directoryOffice.collectAsState()`), and every
 * read re-reads the StateFlow — smart-cast does not apply. The `when`
 * branch checked `detail != null` once, but the LazyColumn items re-read
 * `detail!!` fresh each time. A state write to null landing while the
 * LazyColumn's item provider was being rebuilt (a reload, or the back
 * button's clearDirectoryDetail()) threw the NPE. Fix: capture the value
 * ONCE into a plain local and use only that inside the LazyColumn; the
 * load functions no longer null the shown detail at start.
 *
 * These tests render the REAL screens with a REAL MainViewModel (real
 * file Room DB + real snapshot asset, same setup as
 * MainViewModelInitOrderTest) under Robolectric + Compose.
 */
@RunWith(RobolectricTestRunner::class)
// Tall viewport: the detail screens are LazyColumns, and a section
// below the fold is not composed at all in the default 320x470 window
// — so the history/education cards would never appear in the tree.
// With a tall window the whole page composes at once.
@Config(sdk = [34], qualifiers = "w420dp-h1800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DirectoryDetailScreensTest {

    @get:Rule
    // A bare ComponentActivity as the host (declared in the DEBUG
    // manifest app/src/debug/AndroidManifest.xml — Robolectric
    // resolves the launched activity from the debug variant's merged
    // manifest and refuses an undeclared one). MainActivity
    // cannot host these tests: its onCreate already calls setContent,
    // so the rule's setContent throws "has already set content". The
    // system-Back tests live in ExitConfirmBackTest with the real
    // MainActivity instead.
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewModel: MainViewModel? = null

    @After
    fun tearDown() {
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

    private fun createSeededViewModel(): MainViewModel {
        val vm = MainViewModel(RuntimeEnvironment.getApplication())
        viewModel = vm
        val settled = waitUntil {
            vm.directorySeedStatus.value.state in setOf("seeded", "already", "failed")
        }
        assertTrue("seed never settled: ${vm.directorySeedStatus.value}", settled)
        assertFalse(
            "seed failed: ${vm.directorySeedStatus.value.reason}",
            vm.directorySeedStatus.value.state == "failed"
        )
        assertEquals(203, vm.directorySeedStatus.value.offices)
        assertEquals(5079, vm.directorySeedStatus.value.engineers)
        // Wait until the counts are really the full snapshot, not a
        // mid-seed transient, before any test reads the tables.
        val dao = com.nezammonitor.app.data.NezamDatabase
            .getDatabase(androidx.test.core.app.ApplicationProvider.getApplicationContext())
            .directoryDao()
        assertTrue(
            "directory counts never settled at 203/5079",
            waitUntil {
                kotlinx.coroutines.runBlocking {
                    dao.getOfficeCount() == 203 && dao.getEngineerCount() == 5079
                }
            }
        )
        return vm
    }

    private fun showOffice(vm: MainViewModel, name: String) {
        composeRule.setContent {
            MaterialTheme { DirectoryOfficeDetailScreen(vm, name, {}, {}) }
        }
        // The load coroutine is posted to the paused main looper:
        // composeRule.waitUntil does not pump it, so use the
        // shadow-looper waitUntil below (same reason as the seed wait).
        assertTrue("office never loaded", waitUntil { vm.directoryOffice.value != null })
    }

    private fun showEngineer(vm: MainViewModel, name: String) {
        composeRule.setContent {
            MaterialTheme { DirectoryEngineerProfileScreen(vm, name, {}, {}) }
        }
        assertTrue("engineer never loaded", waitUntil { vm.directoryEngineer.value != null })
    }



    // ---- Crash repro: the state flips to null while the LazyColumn is
    // composed. On the pre-fix code the derived-state recompute re-reads
    // `detail!!` / `profile!!` and throws the phone's NPE; after the
    // fix the captured local keeps the page alive and it falls back to
    // the loading/message state.

    @Test
    fun officeDetail_clearWhileComposed_doesNotCrash() {
        val vm = createSeededViewModel()
        showOffice(vm, "۱۹۴")
        composeRule.onAllNodesWithText("دفتر 194").assertCountEquals(2)
        composeRule.onNodeWithText("اعضای دفتر (10)").assertIsDisplayed()

        // The back-navigation trigger: the detail is cleared while the
        // LazyColumn is still composed.
        vm.clearDirectoryDetail()
        composeRule.waitForIdle()

        // No crash: the page survives (empty state — the screen is
        // leaving anyway) instead of the NPE at :148.
        composeRule.onAllNodesWithText("دفتر 194").assertCountEquals(0)
    }

    @Test
    fun officeDetail_reloadMissingWhileShown_doesNotCrash() {
        val vm = createSeededViewModel()
        showOffice(vm, "۱۹۴")
        composeRule.onAllNodesWithText("دفتر 194").assertCountEquals(2)

        // Same screen, new name that does not exist (office -> member ->
        // profile -> office link): the old load nulled the state first.
        vm.loadDirectoryOfficeByName("۸۹")
        assertTrue("not-found message never arrived", waitUntil { vm.directoryMessage.value != null })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("یافت نشد", substring = true).assertIsDisplayed()
    }

    @Test
    fun engineerProfile_clearWhileComposed_doesNotCrash() {
        val vm = createSeededViewModel()
        showEngineer(vm, "مهدیه قپاني")
        composeRule.onAllNodesWithText("مهدیه قپانی").assertCountEquals(2)

        vm.clearDirectoryDetail()
        composeRule.waitForIdle()

        // No crash: the page survives instead of the NPE at :199.
        composeRule.onAllNodesWithText("مهدیه قپانی").assertCountEquals(0)
    }

    @Test
    fun engineerProfile_reloadMissingWhileShown_doesNotCrash() {
        val vm = createSeededViewModel()
        showEngineer(vm, "مهدیه قپاني")
        composeRule.onAllNodesWithText("مهدیه قپانی").assertCountEquals(2)

        vm.loadDirectoryEngineerByName("مهندسی که نیست")
        assertTrue("not-found message never arrived", waitUntil { vm.directoryMessage.value != null })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("یافت نشد", substring = true).assertIsDisplayed()
    }

    // ---- Uniform display: every engineer renders the same four cards,
    // with «ثبت نشده» / «موردی ثبت نشده است» instead of blanks or
    // vanishing cards.

    @Test
    fun engineerProfile_fullEngineer_historyIsSelfExplanatory() {
        val vm = createSeededViewModel()
        // Engineer 3320 (امیرحسین کریمی): 3 history rows, 1 education
        // row whose start is the 1300/01/01 placeholder.
        showEngineer(vm, "اميرحسين كريمي")

        composeRule.onNodeWithText("مشخصات پروانه").assertIsDisplayed()
        composeRule.onNodeWithText("تاریخ اخذ پروانه").assertIsDisplayed()
        composeRule.onNodeWithText("تاریخ اخذ هر پایهٔ پروانه").assertIsDisplayed()
        composeRule.onNodeWithText("مدارک تحصیلی").assertIsDisplayed()

        // Only the obtained grades, fully labeled — two rows obtained
        // grade 3 on the same date.
        composeRule.onAllNodesWithText("پایه ۳: ۱۳۹۱/۱۲/۲۳").assertCountEquals(2)
        // The garbage date (year 1278) is never shown as a real date,
        // and no "_" literal survives anywhere on the page.
        composeRule.onAllNodesWithText("1278", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("_", substring = true).assertCountEquals(0)
        // The 1300/01/01 placeholder start is never shown as a date.
        composeRule.onAllNodesWithText("1300/01/01", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("تا 1381/10/20").assertIsDisplayed()
        // The «پایه اجرا» row obtained no grade: it says so.
        composeRule.onNodeWithText("ثبت نشده").assertIsDisplayed()
    }

    @Test
    fun engineerProfile_listOnlyEngineer_sameShapeAsFull() {
        val vm = createSeededViewModel()
        // List-only engineer (id 6949): no history, no education, "_"
        // grades, no phone, no licence dates, no office.
        showEngineer(vm, "اناهیتا آب حیات")

        // The same four sections as a full engineer.
        composeRule.onNodeWithText("مشخصات پروانه").assertIsDisplayed()
        composeRule.onNodeWithText("تاریخ اخذ پروانه").assertIsDisplayed()
        composeRule.onNodeWithText("مدارک تحصیلی").assertIsDisplayed()
        // Both empty sections keep their card with the empty line.
        composeRule.onAllNodesWithText("موردی ثبت نشده است").assertCountEquals(2)
        // Grades ("_"), licence dates and phone show «ثبت نشده»;
        // the discipline comes from the record, not a literal.
        composeRule.onNodeWithText("رشته: معماری").assertIsDisplayed()
        // 5 exact DetailRow placeholders (3 grades + 2 licence
        // dates) plus the combined phone/office lines below.
        composeRule.onAllNodesWithText("ثبت نشده", substring = false).assertCountEquals(5)
        composeRule.onNodeWithText("همراه: ثبت نشده", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("دفتر: ثبت نشده", substring = false).assertIsDisplayed()
        composeRule.onAllNodesWithText("_", substring = true).assertCountEquals(0)
    }

    @Test
    fun officeDetail_rendersUniformly() {
        val vm = createSeededViewModel()
        // Office 194: the contact field is blank in the snapshot.
        showOffice(vm, "۱۹۴")

        composeRule.onAllNodesWithText("دفتر 194").assertCountEquals(2)
        composeRule.onNodeWithText("اعضای دفتر (10)").assertIsDisplayed()
        // The blank contact row shows the placeholder, not a blank.
        composeRule.onNodeWithText("ثبت نشده").assertIsDisplayed()
        composeRule.onNodeWithText("رابط دفتر").assertIsDisplayed()
    }
}
