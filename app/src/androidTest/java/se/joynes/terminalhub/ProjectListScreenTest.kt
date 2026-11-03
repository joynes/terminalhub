package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import se.joynes.terminalhub.data.db.AppDatabase
import javax.inject.Inject
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.projects.ProjectListScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProjectListScreenTest {
    
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var db: AppDatabase
    @Before fun setup() { hiltRule.inject(); db.clearAllTables() }


    @Test
    fun emptyStateIsShown() {
        composeRule.setContent {
            TerminalHubTheme {
                ProjectListScreen(serverId = 1L, onAddProject = {}, onEditProject = {}, onConnect = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("NO PROJECTS").assertIsDisplayed()
    }

    @Test
    fun addProjectButtonVisible() {
        composeRule.setContent {
            TerminalHubTheme {
                ProjectListScreen(serverId = 1L, onAddProject = {}, onEditProject = {}, onConnect = {}, onBack = {})
            }
        }
        composeRule.onNodeWithContentDescription("Add project").assertIsDisplayed()
    }
}
