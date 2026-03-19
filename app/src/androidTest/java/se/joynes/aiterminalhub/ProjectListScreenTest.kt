package se.joynes.aiterminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit4.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.ui.screen.projects.ProjectListScreen
import se.joynes.aiterminalhub.ui.theme.AITerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProjectListScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun setup() { hiltRule.inject() }

    @Test
    fun emptyStateIsShown() {
        composeRule.setContent {
            AITerminalHubTheme {
                ProjectListScreen(serverId = 1L, onAddProject = {}, onEditProject = {}, onConnect = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("NO PROJECTS").assertIsDisplayed()
    }

    @Test
    fun addProjectButtonVisible() {
        composeRule.setContent {
            AITerminalHubTheme {
                ProjectListScreen(serverId = 1L, onAddProject = {}, onEditProject = {}, onConnect = {}, onBack = {})
            }
        }
        composeRule.onNodeWithContentDescription("Add project").assertIsDisplayed()
    }
}
