package com.nezammonitor.app.ui.screens.directory

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nezammonitor.app.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v3.11.6: exit confirmation on system Back.
 *
 * MainActivity's own content is used as-is (no setContent): the real
 * NezamMonitorApp with its NavHost, drawer and the BackHandler. The
 * screen tests live in DirectoryDetailScreensTest with a bare
 * ComponentActivity host instead — MainActivity's onCreate already
 * calls setContent, so a rule's setContent throws there.
 *
 * The Environment shadow answers "not granted" to
 * isExternalStorageManager() (see ShadowEnvironmentIsExternalStorageManager):
 * under Robolectric the real method throws ArrayIndexOutOfBoundsException
 * and the activity never launches.
 *
 * "On Home" is proven behaviourally: after a back press that must pop
 * (inner screen) or close the drawer, one more back press must show the
 * exit dialog — that only happens on the Home destination.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    shadows = [ShadowEnvironmentIsExternalStorageManager::class]
)
class ExitConfirmBackTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** System Back, as the user presses it. */
    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    private fun dismissDialog() {
        composeRule.onNodeWithText("خیر").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("خروج از برنامه").assertCountEquals(0)
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun backOnHome_showsExitDialog_dismissStays() {
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("خروج از برنامه").assertCountEquals(0)

        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("خروج از برنامه").assertIsDisplayed()
        composeRule.onNodeWithText("آیا می‌خواهید از برنامه خارج شوید؟").assertIsDisplayed()

        dismissDialog()
    }

    @Test
    fun backOnInnerScreen_popsWithoutDialog() {
        composeRule.waitForIdle()

        // Home -> Cases via the bottom-bar tab. "پرونده‌ها" also names
        // the drawer item and the (below-the-fold) stat card, so pick
        // the tab by its Role (hasRole does not exist in ui-test 1.5.4,
        // so the matcher reads the Role property directly).
        // onNode(matcher) is a member of the rule in ui-test 1.5.4,
        // not a top-level finder — no import for it.
        // The drawer item ALSO has Role.Tab, so the Role alone does not
        // distinguish it: the bottom tab's merged Text is exactly
        // ["پرونده‌ها"] (its icon is an Icon, not text), while the
        // drawer item's is ["📋", "پرونده‌ها"].
        composeRule.onNode(
            SemanticsMatcher("bottom-bar Cases tab") { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
                    node.config.getOrNull(SemanticsProperties.Text)
                        ?.map { it.text } == listOf("پرونده‌ها")
            }
        ).performClick()
        composeRule.waitUntil(30_000) {
            try {
                composeRule.onNodeWithText("جستجوی شماره پرونده / مالک / موبایل...").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }

        pressBack()
        composeRule.waitForIdle()

        // Back on an inner screen pops with NO dialog, back to Home:
        // the Cases search field is gone and no dialog appeared.
        composeRule.onAllNodesWithText("خروج از برنامه").assertCountEquals(0)
        composeRule.onAllNodesWithText("جستجوی شماره پرونده / مالک / موبایل...").assertCountEquals(0)

        // One more back press proves we are on Home: the dialog appears.
        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("خروج از برنامه").assertIsDisplayed()

        dismissDialog()
    }

    @Test
    fun backWithDrawerOpen_closesDrawerWithoutDialog() {
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("منو").performClick()
        // The drawer opens with an animation launched on Main: pump
        // the paused main looper and advance the compose clock so the
        // open finishes. (The drawer's open state is proven by the
        // Back behavior below, not by the item's on-screen position:
        // under Robolectric the drawer item reports zero bounds even
        // when the drawer is open, so assertIsDisplayed on it never
        // passes here — on the phone the drawer visibly opens.)
        val shadowLooper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        repeat(30) {
            shadowLooper.idle()
            composeRule.mainClock.advanceTimeBy(100)
        }

        pressBack()
        composeRule.waitForIdle()

        // Back closed the drawer: no exit dialog appeared.
        composeRule.onAllNodesWithText("خروج از برنامه").assertCountEquals(0)

        // One more back press proves the drawer is closed and we are
        // on Home: the exit dialog appears now.
        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("خروج از برنامه").assertIsDisplayed()

        dismissDialog()
    }
}
