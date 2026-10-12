package com.kiaraen.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression for the v0.2.1 in-app crash: the tab NavHost shared the root
 * NavController (IllegalStateException as soon as MAIN opened), the signup
 * link was plain text, and the profile CTA bounced guests straight back.
 * Walks the real UI: guest → every tab → auth → signup → review quiz.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = KiaraApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `guest reaches every tab then auth then signup then quiz`() {
        tapText("ادامه بدون حساب", scroll = true)

        listOf("levels", "collections", "progress", "profile").forEach { tapTag("tab_$it") }

        // profile CTA must open the form even for a remembered guest
        tapText("ورود یا ثبت")
        waitText("خوش برگشتی")

        // signup link must switch the form
        tapText("حساب نداری", scroll = true)
        waitText("کیارات رو بساز")

        // back to the tabs, then the home review quiz
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        tapTag("tab_home")
        tapText("شروع مرور امروز")
        waitText("سوال")
    }

    private fun waitText(text: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tapText(text: String, scroll: Boolean = false) {
        waitText(text)
        val node = compose.onAllNodesWithText(text, substring = true).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
        compose.waitForIdle()
    }

    private fun tapTag(tag: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
