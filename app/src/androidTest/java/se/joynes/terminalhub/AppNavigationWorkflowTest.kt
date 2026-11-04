package se.joynes.terminalhub

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.domain.TerminalSessionManager

@HiltAndroidTest
class AppNavigationWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository
    @Inject lateinit var credentials: SecurePrefsManager
    @Inject lateinit var sessions: TerminalSessionManager
    @Inject lateinit var settings: AppSettingsRepository
    private lateinit var original: AppSettings
    private var scenario: ActivityScenario<MainActivity>? = null
    private var serverId = 0L
    @Before fun setup() {
        hilt.inject(); original = settings.settings.value
        InstrumentationRegistry.getInstrumentation().runOnMainSync { sessions.clearForConfigImport() }
        db.clearAllTables()
        settings.applySettingsImport(settings.settings.value, AppSettings())
        settings.setBackgroundSshRecommendationHandled()
        context.getSharedPreferences("session_host", 0).edit().clear().commit()
        runBlocking {
            projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL, name = "nav-fixture", useTmux = false))
            serverId = servers.save(Server(name = "Navigation server", host = "fixture.invalid", username = "fixture"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("⚙").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { sessions.clearForConfigImport() }
        settings.applySettingsImport(settings.settings.value, original)
        credentials.clearAll(); db.clearAllTables()
        for (name in listOf("nav-fixture", "nav-new")) File(sessions.localProjectPath(name)).deleteRecursively()
    }
    private fun menu(label: String) { compose.onNodeWithText("⚙").performClick(); compose.onNodeWithText(label).performClick() }
    private fun back() { compose.onNodeWithContentDescription("Back").performClick() }
    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())
    @Test fun settingsLogsServersAndSessionLogsNavigateAndSettingsSurviveRecreation() {
        menu("Settings")
        compose.onNodeWithText("> SETTINGS").assertIsDisplayed()
        scenario!!.recreate()
        compose.onNodeWithText("> SETTINGS").assertIsDisplayed()
        compose.onNodeWithTag("settings-sections").performScrollToNode(hasText("CONNECTION & RECOVERY"))
        compose.onNodeWithText("CONNECTION & RECOVERY").performClick()
        compose.onNodeWithText("RECONNECT ALL").performScrollTo().performClick()
        compose.onNodeWithText("⚙").assertIsDisplayed()
        menu("See Logs"); compose.onNodeWithText("> APP LOG").assertIsDisplayed(); back()
        menu("Servers")
        compose.onNodeWithText("Navigation server").assertIsDisplayed()
        compose.onNodeWithText("SLOG").performClick()
        compose.onNodeWithText("> SESSION LOG").assertIsDisplayed(); back()
        compose.onNodeWithText("LOG").performClick()
        compose.onNodeWithText("> APP LOG").assertIsDisplayed(); back()
        compose.onNodeWithText("SESS").performClick()
        compose.onNodeWithText("⚙").assertIsDisplayed()
        assertTrue(sessions.sessions.value.any { it.projectName == "nav-fixture" })
    }
    @Test fun editingAndDeletingAServerUsesConfirmationAndLeavesLocalProjectsAlone() {
        credentials.savePassword(serverId, "fixture password"); credentials.savePrivateKey(serverId, "fixture key")
        menu("Servers")
        compose.onNodeWithText("EDIT").performClick()
        compose.onNodeWithText("> EDIT SERVER").assertIsDisplayed()
        field("Display name *").performScrollTo().performTextReplacement("Edited navigation server")
        compose.onNodeWithText("[ SAVE ]").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Edited navigation server").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("[ PROJECTS ]").performClick()
        compose.onNodeWithText("NO PROJECTS").assertIsDisplayed()
        compose.onNodeWithText("[ ADD PROJECT ]").performClick()
        field("Folder/session name *").performTextInput("nav-new")
        compose.onNodeWithText("[ SAVE ]").performScrollTo().performClick()
        compose.onNodeWithText("NO PROJECTS").assertIsDisplayed()
        back()
        compose.onNodeWithText("DELETE").performClick()
        compose.onNodeWithText("DELETE SERVER?").assertIsDisplayed()
        compose.onNodeWithText("CANCEL").performClick()
        compose.onNodeWithText("Edited navigation server").assertIsDisplayed()
        compose.onNodeWithText("DELETE").performClick()
        compose.onNode(hasText("DELETE") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("NO SERVERS CONFIGURED").fetchSemanticsNodes().isNotEmpty() }
        assertNull(credentials.getPassword(serverId)); assertNull(credentials.getPrivateKey(serverId))
        assertEquals(setOf("nav-fixture", "nav-new"), runBlocking { projects.getAll().first().map { it.name }.toSet() })
        compose.onNodeWithText("[ SESSIONS ]").performClick()
        compose.onNodeWithText("⚙").assertIsDisplayed()
    }
    @Test fun addingAServerCanGenerateAndSaveAnEncryptedPrivateKeyWithoutConnecting() {
        menu("Servers")
        compose.onNodeWithContentDescription("Add server").performClick()
        field("Display name *").performTextInput("Generated-key server")
        field("Host or IP address *").performTextInput("unused.invalid")
        field("SSH username *").performScrollTo().performTextInput("fixture")
        compose.onNodeWithText("[ GENERATE KEY ]").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("KEY READY").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("PUBLIC KEY - COPY THIS TO SERVER").assertExists()
        compose.onNodeWithText("[ SAVE ]").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Generated-key server").fetchSemanticsNodes().isNotEmpty() }
        val server = runBlocking { servers.getAll().first().single { it.name == "Generated-key server" } }
        assertTrue(credentials.getPrivateKey(server.id)!!.startsWith("-----BEGIN RSA PRIVATE KEY-----"))
        assertTrue(sessions.sessions.value.all { it.projectName == "nav-fixture" })
    }
}
