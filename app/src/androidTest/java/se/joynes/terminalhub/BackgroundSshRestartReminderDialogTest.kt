package se.joynes.terminalhub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.*
import org.junit.Assert.assertFalse
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.sessions.BackgroundSshRestartReminderDialog
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class BackgroundSshRestartReminderDialogTest {
    @Test fun checkboxAloneDoesNotStartAndStartUsesSelectedApproval() {
        var automatic by mutableStateOf(false)
        var started = false
        var remembered = false
        composeRule.setContent {
            TerminalHubTheme {
                BackgroundSshRestartReminderDialog(
                    onStart = { started = true; remembered = automatic }, onNotNow = {},
                    automaticStart = automatic, onAutomaticStartChange = { automatic = it })
            }
        }
        composeRule.onNodeWithText("Start automatically when I open the app — don't ask again").assertIsDisplayed()
        composeRule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        composeRule.runOnIdle { assertTrue(automatic); assertFalse(started) }
        composeRule.onNodeWithText("START BACKGROUND SSH").performClick()
        composeRule.runOnIdle { assertTrue(started); assertTrue(remembered) }
    }
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stoppedApprovedServiceShowsActionableStartButton() {
        val started = AtomicBoolean(false)
        composeRule.setContent {
            TerminalHubTheme {
                BackgroundSshRestartReminderDialog(
                    onStart = { started.set(true) },
                    onNotNow = {}
                )
            }
        }

        composeRule.onNodeWithText("BACKGROUND SSH IS OFF").assertIsDisplayed()
        composeRule.onNodeWithText("START BACKGROUND SSH").assertIsDisplayed().performClick()

        assertTrue(started.get())
    }
}
