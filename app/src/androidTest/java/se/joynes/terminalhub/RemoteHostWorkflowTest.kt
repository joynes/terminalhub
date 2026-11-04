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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.*
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.data.ssh.SshTransportConnector
import se.joynes.terminalhub.di.SshModule
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.domain.MacroStep
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
        compose.waitUntil(5000) { host.projectTabs.value.firstOrNull { it.projectId == ids[0] }?.projectName == "Work one" }
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
    private fun saveAction(name: String, text: String, macro: List<MacroStep>? = null, sendEnter: Boolean = true): Long = runBlocking {
        db.pinnedActionDao().save(PinnedActionEntity(name = name, text = text, scope = "GLOBAL", sendEnter = sendEnter,
            macroJson = macro?.let(PinnedMacroCodec::encode) ?: ""))
    }
    private fun openAction(name: String, hold: Boolean = false) {
        compose.onNodeWithContentDescription("Pinned actions").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("pinned-actions-grid").performScrollToNode(hasText(name))
        if (hold) compose.onNodeWithText(name).performTouchInput { longClick() }
        else compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.OnClick) { it() }
    }
    @Test fun savedCommandAndMacroExecuteInCurrentTerminalAndRecordUsage() {
        val command = saveAction("Command fixture", "command-fixture")
        openAction("Command fixture")
        compose.waitUntil(5000) { remote.shells.any { it.typed.toString().contains("command-fixture\r") } }
        compose.waitUntil(5000) { runBlocking { db.pinnedActionDao().all().first { it.id == command }.usageCount > 0 } }
        val macro = saveAction("Macro fixture", "summary-not-sent", listOf(MacroStep.Key("C", ctrl = true),
            MacroStep.Pause(20), MacroStep.Text("macro-fixture"), MacroStep.Key("ENTER")))
        openAction("Macro fixture")
        compose.waitUntil(10000) { remote.shells.any { it.typed.toString().contains("\u0003macro-fixture\r") } }
        compose.waitUntil(5000) { runBlocking { db.pinnedActionDao().all().first { it.id == macro }.usageCount > 0 } }
        assertFalse(remote.shells.any { it.typed.toString().contains("summary-not-sent") })
    }
    @Test fun broadcastCommandAndMacroReachBothRemoteProjectsWithoutSwitchingActiveTab() {
        val active = sessions.activeId.value
        saveAction("Broadcast fixture", "broadcast-fixture")
        openAction("Broadcast fixture", hold = true)
        compose.onNodeWithText("Send to multiple projects").performClick()
        compose.onNode(hasText("local-fixture") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("SEND TO 2 PROJECTS").performClick()
        compose.waitUntil(10000) { remote.shells.count { it.typed.toString().contains("broadcast-fixture\r") } == 2 }
        assertEquals(active, sessions.activeId.value)
        saveAction("Broadcast macro", "never-send-summary", listOf(MacroStep.Text("broadcast-macro"), MacroStep.Key("ENTER")))
        openAction("Broadcast macro", hold = true)
        compose.onNodeWithText("Send to multiple projects").performClick()
        compose.onNode(hasText("local-fixture") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("SEND TO 2 PROJECTS").performClick()
        compose.waitUntil(10000) { remote.shells.count { it.typed.toString().contains("broadcast-macro\r") } == 2 }
        assertEquals(active, sessions.activeId.value)
    }
    @Test fun preparedActionOpensEditorWithoutSendingAndCanBeDeletedFromProject() {
        val action = saveAction("Prepare fixture", "review-before-send", sendEnter = false)
        openAction("Prepare fixture")
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("review-before-send")
        assertFalse(remote.shells.any { it.typed.toString().contains("review-before-send") })
        compose.onNodeWithText("✕").performClick()
        openAction("Prepare fixture", hold = true)
        compose.onNodeWithText("Delete action").performClick()
        compose.waitUntil(5000) { runBlocking { db.pinnedActionDao().all().none { it.id == action } } }
        compose.onNodeWithText("Prepare fixture").assertDoesNotExist()
    }
    private fun shortcut(code: Int) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.runOnMainSync {
            val decor = android.view.inspector.WindowInspector.getGlobalWindowViews().last { it.isShown && it.hasWindowFocus() }
            val now = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
                decor.dispatchKeyEvent(android.view.KeyEvent(now, now, action, code, 0,
                    android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_ALT_ON, -1, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD))
            }
        }
        compose.waitForIdle()
    }
    @Test fun externalKeyboardCanSwitchProjectsAndManageTerminalWindows() {
        settings.setKeyboardShortcuts(KeyboardShortcuts.suggestions)
        val original = sessions.activeId.value
        shortcut(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        compose.waitUntil(5000) { sessions.activeId.value != original }
        shortcut(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        compose.waitUntil(5000) { sessions.activeId.value == original }
        waitWindow("Terminal 1: AI")
        shortcut(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        compose.waitUntil(10000) { !terminals.state.value.busy && terminals.state.value.snapshot?.windows?.firstOrNull { it.active }?.name == "Tests" }
        shortcut(android.view.KeyEvent.KEYCODE_DPAD_UP)
        compose.waitUntil(10000) { !terminals.state.value.busy && terminals.state.value.snapshot?.windows?.firstOrNull { it.active }?.name == "AI" }
        shortcut(android.view.KeyEvent.KEYCODE_R)
        compose.onNode(hasText("Terminal name") and hasSetTextAction()).performTextReplacement("Keyboard renamed")
        compose.onNodeWithText("SAVE").performClick(); waitWindow("Terminal 1: Keyboard renamed")
        shortcut(android.view.KeyEvent.KEYCODE_T)
        compose.waitUntil(10000) { !terminals.state.value.busy && terminals.state.value.snapshot?.windows?.size == 3 }
        shortcut(android.view.KeyEvent.KEYCODE_W)
        compose.onNodeWithText("CLOSE TERMINAL").performClick()
        compose.waitUntil(10000) { !terminals.state.value.busy && terminals.state.value.snapshot?.windows?.size == 2 }
        shortcut(android.view.KeyEvent.KEYCODE_P)
        compose.onNodeWithTag("quick-switch-query").performTextInput("remote-two")
        compose.onNodeWithText("OPEN").performClick()
        compose.waitUntil(10000) { sessions.sessions.value.firstOrNull { it.id == sessions.activeId.value }?.projectId == ids[1] }
    }
    @Test fun terminalProtocolRepliesAreSuppressedWhileExplicitInputAndReorderingKeepSessionOwnership() {
        val original = sessions.activeId.value!!
        val initial = sessions.sessions.value.map { it.id }
        compose.runOnIdle {
            val session = sessions.activeSession().value!!
            for (reply in listOf("\u001B[8;24;80t", "\u001B[4;240;800t", "\u001B[9;24;80t", "\u001B[>0;1c", "\u001B[?64;1c", "\u001B]10;rgb:ffff/ffff/ffff\u0007", "\u001B]11;rgb:0000/0000/0000\u0007")) {
                session.write(reply)
            }
            sessions.sendBytesToActive("owned-input".toByteArray())
            sessions.recordUserInput(session, "typed-input")
            sessions.recordUserPaste(session, "pasted-input")
            sessions.moveSession(0, 2)
            assertEquals(listOf(initial[1], initial[2], initial[0]), sessions.sessions.value.map { it.id })
            assertEquals(original, sessions.activeId.value)
        }
        compose.waitUntil(5000) { remote.shells.any { it.typed.toString().contains("owned-input") } }
        assertFalse(remote.shells.any { it.typed.toString().contains("rgb:") || it.typed.toString().contains("24;80t") })
        compose.runOnIdle { sessions.moveSession(2, 0) }
        projectMenu("Close")
        compose.onNodeWithText("KILL TMUX").performClick()
        compose.waitUntil(10000) { sessions.sessions.value.none { it.id == original } && remote.commands.any { it.contains("tmux kill-session") } }
        assertTrue(sessions.sessions.value.any { it.projectId == ids[1] && it.isConnected })
    }
    @Test fun droppedChannelsShowDisconnectedOverlayAndReconnectAllKeepsTheSelectedProject() {
        val originalProject = ids[0]
        remote.shells.forEach { it.close() }
        compose.waitUntil(10000) { host.projectTabs.value.filter { it.projectId in ids.take(2) }.all { !it.isConnected && !it.isConnecting } }
        compose.onNodeWithText("SESSION DISCONNECTED").assertIsDisplayed()
        compose.onNodeWithText("RECONNECT ALL (2)").performClick()
        compose.waitUntil(15000) { host.projectTabs.value.filter { it.projectId in ids.take(2) }.all { it.isConnected } }
        compose.onNodeWithText("SESSION DISCONNECTED").assertDoesNotExist()
        assertEquals(originalProject, sessions.sessions.value.first { it.id == sessions.activeId.value }.projectId)
    }
    @Test fun editorImeSendHistoryAndKeybarPasteRetainTheCurrentProject() {
        settings.setInputHistoryEnabled(true)
        settings.setKeyBarRows(listOf(listOf("TEXT_INPUT", "PASTE", "TAB", "ENTER", "KEYBOARD", "CHAR_C")))
        compose.onNodeWithText("✎").performClick()
        compose.onNodeWithContentDescription("Terminal text input").performTextInput("editor-fixture")
        compose.onNodeWithContentDescription("Terminal text input").performImeAction()
        compose.waitUntil(5000) { remote.shells.any { it.typed.toString().contains("editor-fixture\r") } }
        compose.waitUntil(5000) { runBlocking { db.textInputHistoryDao().getRecentForProject(ids[0]).first().any { it.text == "editor-fixture" } } }
        compose.onNodeWithText("✎").performClick()
        compose.onNodeWithText("▾ HISTORY").performClick()
        compose.onNodeWithText("editor-fixture").performClick()
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("editor-fixture")
        compose.onNodeWithText("C").performClick(); compose.onNodeWithText("⇥").performClick()
        compose.onNodeWithContentDescription("Terminal text input").assertTextContains("editor-fixturec\t")
        compose.onNodeWithText("✕").performClick()
        compose.runOnIdle { (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("fixture", "clipboard-fixture")) }
        compose.onNodeWithText("PASTE").performClick()
        compose.waitUntil(5000) { remote.shells.any { it.typed.toString().contains("clipboard-fixture") } }
        compose.onNodeWithContentDescription("Toggle keyboard").performClick()
        compose.onNodeWithContentDescription("Toggle keyboard").performClick()
        compose.onNodeWithText("C").performClick()
        compose.waitUntil(5000) { remote.shells.any { it.typed.toString().contains("clipboard-fixturec") } }
    }
    @Test fun scrollingBackThroughLongOutputShowsReturnToBottomControl() {
        fun find(view: android.view.View): com.termux.view.TerminalView? {
            if (view is com.termux.view.TerminalView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        var terminal: com.termux.view.TerminalView? = null
        compose.runOnIdle {
            terminal = find(compose.activity.window.decorView)
            val text = (1..200).joinToString("\r\n") { "Output line $it" }.toByteArray()
            terminal!!.mEmulator.append(text, text.size)
            terminal!!.onScreenUpdated(true); terminal!!.scrollToRow(-30)
        }
        compose.onNodeWithText("↓").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(terminal!!.isAtBottom) }
        compose.onNodeWithText("↓").assertDoesNotExist()
    }
}
