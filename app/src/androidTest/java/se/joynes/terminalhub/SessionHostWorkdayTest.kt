package se.joynes.terminalhub

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.ui.screen.sessions.SessionHostScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

/** Real shells, Room and the complete host screen exercise ordinary multi-project work. */
@HiltAndroidTest
class SessionHostWorkdayTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var sessions: TerminalSessionManager
    @Inject lateinit var settings: AppSettingsRepository
    private val names = listOf("flow-a", "flow-b", "flow-c")
    private var ids = emptyList<Long>()
    private lateinit var oldSettings: AppSettings
    private val navigation = mutableListOf<String>()

    @Before fun setup() {
        hilt.inject(); oldSettings = settings.settings.value
        compose.runOnIdle { sessions.clearForConfigImport() }
        db.clearAllTables()
        context.getSharedPreferences("session_host", Context.MODE_PRIVATE).edit().clear().commit()
        settings.applySettingsImport(settings.settings.value, AppSettings())
        settings.setBackgroundSshRecommendationHandled()
        settings.setKeyBarRows(listOf(listOf("ESC", "TAB", "TEXT_INPUT", "PROJECT_NOTES", "PINNED_ACTIONS", "DOWNLOAD", "ENTER")))
        ids = runBlocking { names.map { name ->
            projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL,
                name = name, useTmux = false, customScript = "cd {{PROJECT_PATH}}"))
        } }
        compose.setContent { TerminalHubTheme {
            SessionHostScreen(onOpenServers = { navigation += "servers" }, onAddServer = { navigation += "add-server" },
                onAddProject = { navigation += "add-project" }, onOpenLogs = { navigation += "logs" }, onOpenSettings = { navigation += "settings" })
        } }
        compose.waitUntil(10_000) { sessions.sessions.value.map { it.projectId }.containsAll(ids) }
        select(0)
    }
    @After fun cleanup() {
        compose.runOnIdle { sessions.clearForConfigImport() }
        settings.applySettingsImport(settings.settings.value, oldSettings)
        db.clearAllTables()
        names.forEach { File(sessions.localProjectPath(it)).deleteRecursively() }
    }
    private fun select(index: Int) {
        compose.onNodeWithTag("project-tab-${ids[index]}").performClick()
        compose.waitUntil(5000) { sessions.sessions.value.firstOrNull { it.id == sessions.activeId.value }?.projectId == ids[index] }
        compose.onNodeWithTag("project-tab-${ids[index]}").assertIsSelected()
    }
    private fun menu(label: String) {
        compose.onNodeWithText("⚙").performClick()
        compose.onNodeWithText(label).performClick()
    }
    @Test fun typedCommandRunsInTheChosenProjectAndDraftsStayWithTheirProject() {
        compose.onNodeWithText("✎").performClick()
        compose.onNodeWithContentDescription("Terminal text input").performTextInput("draft for first project")
        select(1)
        compose.onNodeWithText("TEXT INPUT").assertDoesNotExist()
        compose.onNodeWithText("✎").performClick()
        compose.onNodeWithContentDescription("Terminal text input").performTextInput("second draft")
        select(0)
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("draft for first project")
        compose.onNodeWithContentDescription("Terminal text input").performTextReplacement("printf workday-ok > workday-result.txt")
        compose.onNodeWithText("↵").performClick()
        val result = File(sessions.localProjectPath(names[0]), "workday-result.txt")
        compose.waitUntil(8000) { result.exists() && result.readText() == "workday-ok" }
        assertFalse(File(sessions.localProjectPath(names[1]), "workday-result.txt").exists())
        compose.onNodeWithText("TEXT INPUT").assertDoesNotExist()
        select(1)
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("second draft")
    }
    @Test fun notesSaveForTheCurrentProjectAndRemainSeparateWhenSwitchingTabs() {
        compose.onNodeWithContentDescription("Project notes").performClick()
        compose.onNodeWithContentDescription("Project note").performTextInput("Plan for A")
        compose.onNodeWithText("CLOSE").performClick()
        select(1)
        compose.onNodeWithContentDescription("Project notes").performClick()
        compose.onNodeWithContentDescription("Project note").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Project note").performTextInput("Plan for B")
        compose.onNodeWithText("CLOSE").performClick()
        select(0)
        compose.onNodeWithContentDescription("Project notes").performClick()
        compose.onNodeWithContentDescription("Project note").assertTextContains("Plan for A")
        compose.onNodeWithText("CLOSE").performClick()
        compose.waitUntil(8000) { runBlocking { db.projectNoteDao().get(ids[0])?.text == "Plan for A" && db.projectNoteDao().get(ids[1])?.text == "Plan for B" } }
    }
    @Test fun menusNavigateAndFinderSelectsAProjectWithoutLosingTheOtherShells() {
        for ((label, expected) in listOf("Settings" to "settings", "Servers" to "servers", "See Logs" to "logs")) {
            menu(label); compose.runOnIdle { assertEquals(expected, navigation.last()) }
        }
        menu("About")
        compose.onNodeWithText("OPEN SOURCE • GPL-3.0").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").performClick()
        menu("Find…")
        compose.onNodeWithTag("quick-switch-query").performTextInput("flow-b")
        compose.onNodeWithText("OPEN").performClick()
        compose.waitUntil(5000) { sessions.sessions.value.first { it.id == sessions.activeId.value }.projectId == ids[1] }
        compose.onNodeWithTag("project-tab-${ids[1]}").assertIsSelected()
        assertEquals(3, sessions.sessions.value.size)
        menu("Recent Projects")
        compose.onNodeWithText("RECENT PROJECTS").assertIsDisplayed()
        compose.onAllNodesWithText("FLOW-A").onLast().performClick()
        compose.waitUntil(5000) { sessions.sessions.value.first { it.id == sessions.activeId.value }.projectId == ids[0] }
        compose.onNodeWithContentDescription("Pinned actions").performClick()
        compose.onNodeWithText("Pinned actions").assertIsDisplayed()
        compose.onNodeWithText("Recent").performClick()
        compose.onNodeWithText("Recent input history is off.").assertIsDisplayed()
    }
}
