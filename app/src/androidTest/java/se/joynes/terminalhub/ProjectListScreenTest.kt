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
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.ui.screen.projects.ProjectListScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProjectListScreenTest {
    
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository
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
    @Test fun savedProjectCanBeConnectedOrEditedAndOtherServersStayHidden() {
        val ids = runBlocking {
            val server = servers.save(Server(name = "Primary", host = "first.invalid", username = "fixture"))
            val other = servers.save(Server(name = "Other", host = "other.invalid", username = "fixture"))
            projects.save(Project(serverId = other, name = "other-server-project"))
            server to projects.save(Project(serverId = server, name = "selected-project"))
        }
        var connected: Long? = null; var edited: Long? = null
        try {
            composeRule.setContent { TerminalHubTheme { ProjectListScreen(ids.first, {}, { edited = it }, { connected = it }, {}) } }
            composeRule.waitUntil(5000) { composeRule.onAllNodesWithText("selected-project").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText("other-server-project").assertDoesNotExist()
            composeRule.onNodeWithText("CONNECT").performClick()
            composeRule.onNodeWithText("EDIT").performClick()
            composeRule.runOnIdle { assertEquals(ids.second, connected); assertEquals(ids.second, edited) }
        } finally { db.clearAllTables() }
    }
}
