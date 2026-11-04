package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import javax.inject.Inject
import java.io.File
import se.joynes.terminalhub.domain.TerminalSessionManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.terminal.TerminalScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TerminalScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var sessions: TerminalSessionManager
    private val fixtureName = "standalone-terminal-fixture"
    @Before fun setup() { hilt.inject(); composeRule.runOnIdle { sessions.clearForConfigImport() } }
    @After fun cleanup() {
        composeRule.runOnIdle { sessions.clearForConfigImport() }
        File(sessions.localProjectPath(fixtureName)).deleteRecursively()
    }


    @Test
    fun specialKeyBarIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { TerminalScreen() }
        }
        composeRule.onNodeWithText("ESC").assertIsDisplayed()
        composeRule.onNodeWithText("⇥").assertIsDisplayed()
        composeRule.onNodeWithText("CTRL").assertIsDisplayed()
    }

    @Test
    fun fontSizeControlsVisible() {
        composeRule.setContent {
            TerminalHubTheme { TerminalScreen() }
        }
        composeRule.onNodeWithText("A+").assertIsDisplayed()
        composeRule.onNodeWithText("A-").assertIsDisplayed()
    }

    @Test fun attachedLocalShellAcceptsInputAndKeyboardCanBeOpenedAndClosed() {
        composeRule.runOnIdle { sessions.registerLocal(fixtureName, projectId = 987650) }
        composeRule.setContent { TerminalHubTheme { TerminalScreen() } }
        composeRule.waitUntil(5000) { (sessions.activeSession().value?.pid ?: 0) > 0 }
        composeRule.runOnIdle {
            assertNotNull(sessions.pasteTextToActive("printf standalone-ok > result.txt"))
            sessions.sendBytesToActive(byteArrayOf(13))
        }
        val result = File(sessions.localProjectPath(fixtureName), "result.txt")
        composeRule.waitUntil(5000) { result.exists() && result.readText() == "standalone-ok" }
        composeRule.onNodeWithText("⌨").performClick()
        composeRule.onNodeWithText("⌨").performClick()
        composeRule.onNodeWithText("A+").performClick()
        composeRule.onNodeWithText("A-").performClick()
        composeRule.onNodeWithText("ESC").performClick()
        composeRule.runOnIdle { assertTrue(sessions.activeSession().value!!.isRunning) }
    }
}
