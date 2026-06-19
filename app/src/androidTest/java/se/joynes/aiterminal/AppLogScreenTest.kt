package se.joynes.aiterminal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminal.ui.screen.applog.AppLogScreen
import se.joynes.aiterminal.ui.theme.AITerminalTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppLogScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun logScreenShowsFilterChips() {
        composeRule.setContent {
            AITerminalTheme {
                AppLogScreen(onBack = {})
            }
        }
        composeRule.onNodeWithText("ALL").assertIsDisplayed()
        composeRule.onNodeWithText("INFO").assertIsDisplayed()
        composeRule.onNodeWithText("ERROR").assertIsDisplayed()
    }

    @Test
    fun searchFieldIsVisible() {
        composeRule.setContent {
            AITerminalTheme { AppLogScreen(onBack = {}) }
        }
        composeRule.onNodeWithText("Search logs...", useUnmergedTree = true).assertExists()
    }

    @Test
    fun clickingLevelFilterChangesSelection() {
        composeRule.setContent {
            AITerminalTheme { AppLogScreen(onBack = {}) }
        }
        composeRule.onNodeWithText("ERROR").performClick()
        composeRule.onNodeWithText("ERROR").assertIsSelected()
    }
}
