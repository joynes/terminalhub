package se.joynes.aiterminal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminal.ui.screen.projects.ProjectListScreen
import se.joynes.aiterminal.ui.theme.AITerminalTheme

@RunWith(AndroidJUnit4::class)
class ProjectListScreenTest {
    
    @get:Rule val composeRule = createComposeRule()


    @Test
    fun emptyStateIsShown() {
        composeRule.setContent {
            AITerminalTheme {
                ProjectListScreen(serverId = 1L, onAddProject = {}, onEditProject = {}, onConnect = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("NO PROJECTS").assertIsDisplayed()
    }

    @Test
    fun addProjectButtonVisible() {
        composeRule.setContent {
            AITerminalTheme {
                ProjectListScreen(serverId = 1L, onAddProject = {}, onEditProject = {}, onConnect = {}, onBack = {})
            }
        }
        composeRule.onNodeWithContentDescription("Add project").assertIsDisplayed()
    }
}
