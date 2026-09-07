package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.upload.FileUploadScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class FileUploadScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun selectFilesButtonIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { FileUploadScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText(
            "Use the + button in the terminal key bar to upload files."
        ).assertIsDisplayed()
    }
}
