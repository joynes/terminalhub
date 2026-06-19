package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.upload.FileUploadScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FileUploadScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun selectFilesButtonIsVisible() {
        composeRule.setContent {
            TerminalHubTheme { FileUploadScreen(serverId = 1L, onBack = {}) }
        }
        composeRule.onNodeWithText("[ SELECT FILES ]").assertIsDisplayed()
    }
}
