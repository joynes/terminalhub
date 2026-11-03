package se.joynes.terminalhub

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.*
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
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.data.ssh.SshTransportConnector
import se.joynes.terminalhub.di.SshModule
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.fixtures.RemoteHostFixture
import se.joynes.terminalhub.ui.screen.sessions.SessionHostScreen
import se.joynes.terminalhub.ui.screen.sessions.SessionHostViewModel
import se.joynes.terminalhub.ui.screen.sessions.TmuxWindowsViewModel
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@UninstallModules(SshModule::class)
class RemoteHostWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    private val remote = RemoteHostFixture()
    @BindValue @JvmField val connector: SshTransportConnector = remote
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository
    @Inject lateinit var sessions: TerminalSessionManager
    @Inject lateinit var settings: AppSettingsRepository
    private lateinit var original: AppSettings
    private var ids = emptyList<Long>()
    private var voiceMode = "success"
    private var pickerLaunches = 0
    private lateinit var host: SessionHostViewModel
    private lateinit var terminals: TmuxWindowsViewModel
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            if (contract is ActivityResultContracts.GetMultipleContents) {
                pickerLaunches++; dispatchResult(requestCode, Activity.RESULT_CANCELED, Intent()); return
            }
            val intent = input as Intent
            assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
            if (voiceMode == "missing") throw ActivityNotFoundException("Fixture recognizer absent")
            val result = Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf("voice fixture"))
            dispatchResult(requestCode, if (voiceMode == "cancel") Activity.RESULT_CANCELED else Activity.RESULT_OK, result)
        }
    }
    @Before fun setup() {
        hilt.inject(); original = settings.settings.value
        compose.runOnIdle { sessions.clearForConfigImport() }
        db.clearAllTables()
        for (name in listOf("session_host", "tmux_window_layouts", "terminal_work_pins")) context.getSharedPreferences(name, 0).edit().clear().commit()
        settings.applySettingsImport(settings.settings.value, AppSettings(sshKeepaliveEnabled = false))
        settings.setBackgroundSshRecommendationHandled()
        settings.setKeyBarRows(listOf(listOf("ESC", "TAB", "TEXT_INPUT", "VOICE_INPUT", "PINNED_ACTIONS", "PROJECT_NOTES", "GIT_REVIEW"),
            listOf("UPLOAD", "DOWNLOAD", "KEYBOARD", "ENTER", "CTRL", "ALT", "SHIFT")))
        ids = runBlocking {
            val server = servers.save(Server(name = "Fixture", host = "fixture.invalid", username = "fixture"))
            listOf(projects.save(Project(serverId = server, name = "remote-one", customScript = "", useTmux = true)),
                projects.save(Project(serverId = server, name = "remote-two", customScript = "", useTmux = true)),
                projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, name = "local-fixture", targetType = ProjectTargetType.LOCAL, useTmux = false)))
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }) {
                TerminalHubTheme {
                    host = hiltViewModel()
                    terminals = hiltViewModel()
                    SessionHostScreen(onOpenServers = {}, onAddServer = {}, onAddProject = {}, onOpenLogs = {}, onOpenSettings = {}, viewModel = host)
                }
            }
        }
        compose.waitUntil(20_000) { sessions.sessions.value.map { it.projectId }.containsAll(ids) }
        select(0)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Terminal 1: AI").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        compose.runOnIdle { sessions.clearForConfigImport() }
        settings.applySettingsImport(settings.settings.value, original)
        db.clearAllTables()
        File(sessions.localProjectPath("local-fixture")).deleteRecursively()
    }
    private fun select(index: Int) {
        compose.onNodeWithTag("project-tab-${ids[index]}").performClick()
        compose.waitUntil(5000) { sessions.sessions.value.firstOrNull { it.id == sessions.activeId.value }?.projectId == ids[index] }
    }
    private fun menu(label: String) { compose.onNodeWithText("⚙").performClick(); compose.onNodeWithText(label).performClick() }
    private fun projectMenu(label: String) {
        compose.waitUntil(10_000) { host.projectTabs.value.firstOrNull { it.projectId == ids[0] }?.isConnecting == false }
        compose.onNodeWithTag("project-tab-${ids[0]}").performTouchInput { longClick() }
        compose.onNodeWithText(label).performClick()
    }
    private fun waitWindow(description: String) {
        compose.waitUntil(10_000) { !terminals.state.value.busy && !terminals.state.value.loading &&
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun switchingRenamingClosingAndCreatingWindowsKeepsTheProjectConnected() {
        val originalSession = sessions.activeId.value
        compose.onNodeWithContentDescription("Terminal 2: Tests").performClick()
        compose.waitUntil(10_000) { !terminals.state.value.busy &&
            compose.onAllNodesWithContentDescription("Terminal 2: Tests").fetchSemanticsNodes().singleOrNull()
                ?.config?.getOrNull(SemanticsProperties.Selected) == true }
        compose.onNodeWithContentDescription("Terminal 2: Tests").assertIsSelected()
        compose.onNodeWithContentDescription("Terminal 2: Tests").performTouchInput { longClick() }
        compose.onNodeWithText("Rename terminal").performClick()
        compose.onNode(hasText("Terminal name") and hasSetTextAction()).performTextReplacement("Logs")
        compose.onNodeWithText("SAVE").performClick()
        waitWindow("Terminal 2: Logs")
        compose.onNodeWithContentDescription("Terminal 2: Logs").performTouchInput { longClick() }
        compose.onNodeWithText("Close terminal").performClick()
        compose.onNodeWithText("CLOSE TERMINAL").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("project-terminal-bar").fetchSemanticsNodes().isEmpty() }
        menu("New terminal")
        compose.onNode(hasText("Name (optional)") and hasSetTextAction()).performTextInput("Build")
        compose.onNodeWithText("SAVE").performClick()
        waitWindow("Terminal 2: Build")
        compose.runOnIdle { assertEquals(originalSession, sessions.activeId.value); assertEquals(3, sessions.sessions.value.size) }
    }
    @Test fun pinnedWindowAndStartCommandCanBePreparedWithoutExecutingIt() {
        menu("Terminal information")
        compose.onNodeWithText("Edit start command").performScrollTo().performClick()
        compose.onNode(hasText("Command") and hasSetTextAction()).performTextInput("ai-client resume fixture")
        compose.onNodeWithText("SAVE").performClick()
        menu("Terminal information")
        compose.onNodeWithText("Mark as active work").performScrollTo().performClick()
        waitWindow("Terminal 1: AI, active work")
        menu("📌 Active work")
        compose.onNodeWithText("remote-one → AI").performClick()
        menu("Terminal information")
        compose.onNodeWithText("Prepare start command").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("ai-client resume fixture")
        compose.runOnIdle { assertTrue(remote.shells.none { it.typed.toString().contains("ai-client resume fixture") }) }
        compose.onNodeWithText("✕").performClick()
        menu("📌 Active work")
        compose.onNodeWithText("Unpin").performClick()
        compose.onNodeWithText("DONE").performClick()
        waitWindow("Terminal 1: AI")
    }
    @Test fun voiceInputGoesToTheOriginalTerminalOrRestoresTheEditorOnCancelAndMissingRecognizer() {
        compose.onNodeWithText("🎙").performClick()
        compose.waitUntil(5000) { remote.shells.any { it.typed.toString().contains("voice fixture") } }
        compose.onNodeWithText("TEXT INPUT").assertDoesNotExist()
        compose.onNodeWithText("✎").performClick()
        compose.onNodeWithContentDescription("Terminal text input").performTextInput("draft ")
        compose.onNodeWithText("🎙").performClick()
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("draft voice fixture")
        voiceMode = "cancel"
        compose.onNodeWithText("🎙").performClick()
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("draft voice fixture")
        voiceMode = "missing"
        compose.onNodeWithText("🎙").performClick()
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("draft voice fixture")
    }
    @Test fun projectRenameCloseFindAndRestartPreserveTheOtherProjects() {
        projectMenu("Rename project")
        compose.onNode(hasText("Display name") and hasSetTextAction()).performTextReplacement("Work one")
        compose.onNodeWithText("SAVE").performClick()
        compose.onNodeWithTag("project-tab-${ids[0]}").assertTextContains("WORK ONE")
        projectMenu("Close")
        compose.onNodeWithText("CLOSE TMUX SESSION?").assertIsDisplayed()
        compose.onNodeWithText("TAB ONLY").performClick()
        compose.onNodeWithTag("project-tab-${ids[0]}").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sessions.sessions.value.any { it.projectId == ids[1] && it.isConnected }) }
        menu("Find…")
        compose.onNodeWithText("Include closed projects").performClick()
        compose.onNodeWithTag("quick-switch-query").performTextInput("Work one")
        compose.onNodeWithText("OPEN").performClick()
        compose.waitUntil(20_000) { sessions.sessions.value.any { it.projectId == ids[0] && it.isConnected } }
        select(0)
        val beforeRestart = sessions.activeId.value
        projectMenu("Restart session…")
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle { assertEquals(beforeRestart, sessions.activeId.value) }
        projectMenu("Restart session…")
        compose.onNodeWithText("RESTART").performClick()
        compose.waitUntil(20_000) { sessions.activeId.value != beforeRestart && sessions.sessions.value.any { it.id == sessions.activeId.value && it.projectId == ids[0] } }
        assertEquals(3, sessions.sessions.value.size)
    }
    @Test fun uploadPickerCancelRemoteBrowserFailureAndGitReviewAreReachableFromTheKeybar() {
        compose.onNodeWithContentDescription("Upload files").performClick()
        compose.waitUntil(5000) { pickerLaunches == 1 }
        compose.onNodeWithText("FILE UPLOAD").assertDoesNotExist()
        compose.onNodeWithText("⇩").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("ERROR:", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("TRY AGAIN").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close file browser").performClick()
        compose.onNodeWithContentDescription("Git changes").performClick()
        compose.onNodeWithTag("git-fullscreen").assertExists()
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithTag("project-tab-${ids[0]}").assertIsSelected()
    }
}
