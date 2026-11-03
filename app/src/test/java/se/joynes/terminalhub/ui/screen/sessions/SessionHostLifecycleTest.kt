package se.joynes.terminalhub.ui.screen.sessions

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.runtime.*
import se.joynes.terminalhub.data.security.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.domain.*
import se.joynes.terminalhub.domain.usecase.ConnectToServer

@OptIn(ExperimentalCoroutinesApi::class)
class SessionHostLifecycleTest {
    @get:Rule val temp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val context: Context = mock()
    private val prefs: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock(defaultAnswer = Mockito.RETURNS_SELF)
    private val projects: ProjectRepository = mock()
    private val servers: ServerRepository = mock()
    private val connect: ConnectToServer = mock()
    private val ssh: SshManager = mock()
    private val sessions: TerminalSessionManager = mock()
    private val inputs: InputActionsRepository = mock()
    private val settings: AppSettingsRepository = mock()
    private val runtime: AppRuntimeRepository = mock()
    private val known: KnownHostRepository = mock()
    private val mode = BackgroundSshModeController()
    private val layouts: TmuxWindowLayoutStore = mock()
    private val windows: RemoteTmuxWindows = mock()
    private val connection: SshConnection = mock()
    private val server = Server(id = 1, name = "Server", host = "fixture.invalid", username = "user")
    private val a = Project(id = 1, serverId = 1, name = "one", useTmux = true, customScript = "echo setup", aiCommand = "ai-client")
    private val b = Project(id = 2, serverId = 1, name = "two", useTmux = false)
    private val c = Project(id = 3, serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL, name = "local", useTmux = false)
    private val all = MutableStateFlow(listOf(a, b, c))
    private val live = MutableStateFlow(listOf(meta(a), meta(b), meta(c)))
    private val active = MutableStateFlow<TerminalSessionId?>(TerminalSessionId("old-1"))
    private val preferences = MutableStateFlow(AppSettings())
    private val runtimeState = MutableStateFlow(AppRuntimeState())
    private val connected = MutableStateFlow(true)
    private val errors = MutableStateFlow<String?>(null)
    private val challenge = MutableStateFlow<HostKeyChallenge?>(null)
    private val closed = mutableSetOf<Long>()
    private val store = ViewModelStore()
    private lateinit var vm: SessionHostViewModel
    private fun meta(p: Project, id: String = "old-${p.id}", connected: Boolean = true) = TerminalSessionMeta(
        TerminalSessionId(id), p.name, p.id, p.useTmux, connected, false, emptyList())

    @Before fun setup() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { it.arguments[1] }
        whenever(projects.getAll()).thenReturn(all)
        whenever(servers.getAll()).thenReturn(flowOf(listOf(server)))
        whenever(servers.getById(1)).thenReturn(server)
        whenever(sessions.sessions).thenReturn(live)
        whenever(sessions.activeId).thenReturn(active)
        whenever(sessions.activeSession()).thenReturn(MutableStateFlow<TerminalSession?>(null))
        whenever(sessions.screenUpdates).thenReturn(MutableSharedFlow())
        whenever(sessions.isProjectClosed(any())).thenAnswer { it.getArgument<Long>(0) in closed }
        doAnswer { closed += it.getArgument<Long>(0); null }.whenever(sessions).markProjectClosed(any())
        doAnswer { closed -= it.getArgument<Long>(0); null }.whenever(sessions).markProjectOpen(any())
        doAnswer { active.value = it.getArgument(0); null }.whenever(sessions).switchTo(any())
        doAnswer {
            val id = it.getArgument<TerminalSessionId>(0)
            live.value = live.value.filterNot { item -> item.id == id }
            if (active.value == id && it.getArgument<Boolean>(2)) active.value = live.value.firstOrNull()?.id
            null
        }.whenever(sessions).close(any(), any(), any())
        doAnswer {
            val project = all.value.first { p -> p.id == it.getArgument<Long>(3) }
            live.value += meta(project, it.getArgument(0)); null
        }.whenever(sessions).register(any(), any(), any(), any(), any(), anyOrNull(), any())
        whenever(sessions.localProjectPath(any())).thenAnswer { File(temp.root, it.getArgument<String>(0)).path }
        whenever(settings.settings).thenReturn(preferences)
        whenever(runtime.state).thenReturn(runtimeState)
        whenever(connect.invoke(server)).thenReturn(connection)
        whenever(connection.sessionId).thenReturn("new-session")
        whenever(sessions.getConnectionForProject(any())).thenReturn(connection)
        whenever(connection.connected).thenReturn(connected)
        whenever(connection.lastErrorMessage).thenReturn(errors)
        whenever(connection.hostKeyChallenge).thenReturn(challenge)
        whenever(connection.runSilent(any())).thenReturn("TMUX_SESSION_CREATED")
        whenever(connection.awaitOutputQuiescence(any(), any(), any())).thenReturn(true)
        whenever(connection.awaitTransportQuiescence(any(), any())).thenReturn(true)
        whenever(windows.list(server, a)).thenReturn(TmuxWindowsSnapshot("\$1", listOf(TmuxWindow("@1", 0, "Shell", true, 1, "stable"))))
        vm = SessionHostViewModel(context, mock<AppLogger>(), servers, projects, connect, ssh, ScriptTemplateEngine(),
            sessions, inputs, settings, runtime, known, mode, layouts, windows)
        store.put("host", vm)
        vm.init(); runCurrent()
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun initializationIsIdempotentAndOpenExistingProjectOnlySwitchesFocus() = runTest(dispatcher) {
        vm.init(); vm.openProject(2); runCurrent()
        assertEquals(TerminalSessionId("old-2"), active.value)
        assertEquals(listOf(1L, 2L, 3L), vm.projectTabs.value.map { it.projectId })
        verifyNoInteractions(connect)
        assertEquals(1L, vm.serverId.value)
        vm.switchToSession(TerminalSessionId("old-3")); runCurrent()
        assertNull(vm.serverId.value)
    }
    @Test fun validTabReorderPersistsAndInvalidOrOutOfBoundsMovesDoNothing() = runTest(dispatcher) {
        vm.reorderSessions(listOf(3, 1, 2)); runCurrent()
        assertEquals(listOf(3L, 1L, 2L), vm.projectTabs.value.map { it.projectId })
        verify(editor).putString("project_tab_order", "3,1,2")
        vm.reorderSessions(listOf(1, 1, 2)); vm.moveSession(-1, 2); vm.moveSession(0, 9); runCurrent()
        assertEquals(listOf(3L, 1L, 2L), vm.projectTabs.value.map { it.projectId })
        vm.moveSession(0, 2); runCurrent()
        assertEquals(listOf(1L, 2L, 3L), vm.projectTabs.value.map { it.projectId })
    }
    @Test fun closingProjectRemovesItsTabAndReopeningActivatesOnlyThatProject() = runTest(dispatcher) {
        vm.closeSession(1, TerminalSessionId("old-1")); runCurrent()
        assertTrue(1L in closed); assertEquals(listOf(2L, 3L), vm.projectTabs.value.map { it.projectId })
        vm.reopenSession(1); runCurrent()
        assertFalse(1L in closed)
        verify(connect).invoke(server)
        assertTrue(vm.projectTabs.value.any { it.projectId == 1L })
        verify(connection).bindProject(1, "one")
    }
    @Test fun successfulReconnectReplacesTheOldSessionAndRunsStartupOnlyForANewTmuxSession() = runTest(dispatcher) {
        vm.reconnectProject(1); runCurrent()
        assertEquals(TerminalSessionId("new-session"), active.value)
        assertFalse(live.value.any { it.id.value == "old-1" })
        verify(sessions).close(TerminalSessionId("old-1"), killTmuxSession = false, selectReplacementIfActive = false)
        verify(connection).send("echo setup\n"); verify(connection).send("ai-client\n")
        assertFalse(vm.projectTabs.value.single { it.projectId == 1L }.isConnecting)
        verify(layouts).save(eq(server), eq(a), any(), eq(true))
    }
    @Test fun existingTmuxDoesNotRerunStartupCommandsAndMissingServerShowsRecoverableError() = runTest(dispatcher) {
        whenever(connection.runSilent(any())).thenReturn("TMUX_SESSION_EXISTS")
        vm.reconnectProject(1); runCurrent()
        verify(connection, never()).send("echo setup\n"); verify(connection, never()).send("ai-client\n")
        whenever(servers.getById(1)).thenReturn(null)
        vm.reconnectProject(2); runCurrent()
        assertEquals("Server configuration was not found.", vm.projectTabs.value.single { it.projectId == 2L }.connectionError)
    }
    @Test fun failedTmuxSetupOrCloneNeverRunsProjectStartupCommands() = runTest(dispatcher) {
        whenever(connection.runSilent(any())).thenReturn("unexpected setup output")
        vm.reconnectProject(1); runCurrent()
        assertTrue(vm.projectTabs.value.single { it.projectId == 1L }.connectionError!!.contains("Could not create or restore"))
        verify(ssh).destroySession("new-session")
        whenever(connection.runSilent(any())).thenReturn("TMUX_SESSION_CREATED\n${ScriptTemplateEngine.GIT_CLONE_FAILED_MARKER}")
        vm.reconnectProject(1); runCurrent()
        verify(connection, never()).send("echo setup\n"); verify(connection, never()).send("ai-client\n")
        verify(connection).send(argThat { contains("Git clone failed") })
    }
    @Test fun failedConnectionExposesTheHostKeyPromptAndDismissDoesNotTrustIt() = runTest(dispatcher) {
        val key = HostKeyChallenge(SshEndpoint("fixture.invalid", 22), HostKeyChallengeKind.UNKNOWN, "ssh-ed25519", byteArrayOf(1), "SHA256:fixture")
        connected.value = false; errors.value = "Verify host key"; challenge.value = key
        vm.reconnectProject(1); runCurrent()
        val prompt = vm.hostKeyPrompts.value.single()
        assertEquals(key, prompt.challenge); assertEquals(setOf(1L), prompt.projectIds)
        assertEquals("Verify host key", vm.projectTabs.value.single { it.projectId == 1L }.connectionError)
        vm.dismissHostKeyChallenge(prompt); runCurrent()
        assertTrue(vm.hostKeyPrompts.value.isEmpty()); verifyNoInteractions(known)
    }
    @Test fun restartUsesAnExistingConnectionAndCreatesAReplacementWithoutMovingOtherTabs() = runTest(dispatcher) {
        whenever(sessions.getConnectionForProject(1)).thenReturn(connection)
        vm.restartTmuxProject(1); runCurrent()
        assertEquals(TerminalSessionId("new-session"), active.value)
        assertEquals(3, live.value.size)
        verify(connection).runSilent(ScriptTemplateEngine().renderKillTmux(a))
        assertNull(vm.projectTabs.value.single { it.projectId == 1L }.connectionError)
    }
    @Test fun restartWithoutServerAndRestartingALocalProjectCannotKillOtherSessions() = runTest(dispatcher) {
        vm.restartTmuxProject(3); runCurrent(); verifyNoInteractions(connect)
        whenever(servers.getById(1)).thenReturn(null)
        vm.restartTmuxProject(1); runCurrent()
        assertEquals("Server configuration was not found.", vm.projectTabs.value.single { it.projectId == 1L }.connectionError)
        assertEquals(3, live.value.size)
    }
    @Test fun reconnectAllDisconnectedSkipsLocalAndConnectedProjects() = runTest(dispatcher) {
        live.value = live.value.map { if (it.projectId == 2L) it.copy(isConnected = false) else it }; runCurrent()
        vm.reconnectAllDisconnected(); runCurrent()
        verify(connection).bindProject(2, "two")
        verify(connection, never()).bindProject(1, "one")
        verify(connection, never()).bindProject(3, "local")
    }
    @Test fun pendingEnterStaysBoundToOriginalSessionAndBlocksMacrosUntilWritesFinish() = runTest(dispatcher) {
        whenever(sessions.pasteTextToActive(any(), anyOrNull())).thenReturn(TerminalSessionId("old-1"))
        assertTrue(vm.sendTextInputToActive("echo one", true))
        assertEquals(setOf(TerminalSessionId("old-1")), vm.pendingInputSessions.value)
        active.value = TerminalSessionId("old-2")
        advanceTimeBy(2000); runCurrent()
        verify(sessions).sendBytesToSession(eq(TerminalSessionId("old-1")), argThat { contentEquals(byteArrayOf(13)) })
        assertTrue(vm.pendingInputSessions.value.isEmpty())
        verify(sessions, never()).sendBytesToSession(eq(TerminalSessionId("old-2")), any())
    }
    @Test fun pasteOnlyDoesNotScheduleEnterAndMissingTargetReturnsFailure() = runTest(dispatcher) {
        whenever(sessions.pasteTextToActive(any(), anyOrNull())).thenReturn(TerminalSessionId("old-1"))
        assertTrue(vm.sendTextInputToActive("draft", false)); runCurrent()
        verify(sessions, never()).sendBytesToSession(any(), any())
        whenever(sessions.pasteTextToActive(any(), anyOrNull())).thenReturn(null)
        assertFalse(vm.sendTextInputToActive("lost", true)); assertTrue(vm.pendingInputSessions.value.isEmpty())
    }
    @Test fun emptyClosedAndUnavailableBroadcastTargetsAreRejectedBeforeSending() = runTest(dispatcher) {
        val action = PinnedActionEntity(name = "Status", text = "pwd")
        assertEquals("Select at least one project.", vm.sendPinnedToProjects(action, emptySet()))
        assertTrue(vm.sendPinnedToProjects(action, setOf(999))!!.contains("closed"))
        assertTrue(vm.sendPinnedToProjects(action, setOf(1))!!.contains("unavailable"))
        live.value = live.value.map { if (it.projectId == 1L) it.copy(isConnected = false) else it }; runCurrent()
        assertTrue(vm.sendPinnedToProjects(action, setOf(1))!!.contains("Reconnect"))
        verify(inputs, never()).markUsed(any())
    }
    @Test fun plainPinnedInputBroadcastUsesCapturedTerminalsAndMarksTheActionUsed() = runTest(dispatcher) {
        val target: MacroTerminalTarget = mock()
        whenever(target.isConnected()).thenReturn(true)
        whenever(sessions.macroTargetForSession(any())).thenReturn(target)
        val action = PinnedActionEntity(id = 9, name = "Status", text = "pwd", sendEnter = true)
        assertNull(vm.sendPinnedToProjects(action, setOf(1, 2)))
        advanceUntilIdle()
        verify(target, times(2)).paste("pwd")
        verify(target, times(2)).press(MacroStep.Key("ENTER"))
        verify(inputs).markUsed(9)
    }
    @Test fun malformedOrEmptyMacroCannotBeRunAndPlainActionsAreNotKeyboardMacros() {
        assertFalse(vm.runPinnedMacro(PinnedActionEntity(name = "Plain", text = "pwd")))
        assertFalse(vm.runPinnedMacro(PinnedActionEntity(name = "Broken", text = "", macroJson = "bad")))
    }
    @Test fun backgroundPermissionDenialClearsThePreferenceWithoutStartingAnyService() {
        vm.startRecommendedBackgroundSsh(false)
        verify(settings).setKeepSshActiveInBackground(false)
        assertEquals(BackgroundSshMode.OFF, mode.mode.value)
        assertFalse(vm.tryAutomaticallyStartBackgroundSsh(false))
    }

    private suspend fun TestScope.hostPrompt(kind: HostKeyChallengeKind = HostKeyChallengeKind.UNKNOWN): HostKeyPrompt {
        val key = HostKeyChallenge(SshEndpoint("fixture.invalid", 22), kind, "ssh-ed25519", byteArrayOf(1), "SHA256:fixture")
        connected.value = false; errors.value = "Verify host key"; challenge.value = key
        vm.reconnectProject(1); runCurrent()
        val prompt = vm.hostKeyPrompts.value.single()
        connected.value = true; errors.value = null
        return prompt
    }
    @Test fun trustingAnUnknownHostStoresItAndReconnectsWhileChangedKeysRemainBlocked() = runTest(dispatcher) {
        val prompt = hostPrompt()
        whenever(known.check(any(), any(), anyOrNull(), anyOrNull())).thenReturn(HostKeyCheckResult.Rejected(prompt.challenge))
        vm.trustHostKeyAndReconnect(prompt); runCurrent()
        verify(known).trust(prompt.challenge)
        assertTrue(vm.hostKeyPrompts.value.isEmpty()); assertTrue(vm.trustingHostKeys.value.isEmpty())
        assertNull(vm.projectTabs.value.single { it.projectId == 1L }.connectionError)
        val changed = HostKeyPrompt(prompt.challenge.copy(kind = HostKeyChallengeKind.CHANGED), setOf(1))
        clearInvocations(known)
        vm.trustHostKeyAndReconnect(changed); runCurrent()
        verifyNoInteractions(known)
    }
    @Test fun alreadyTrustedHostDoesNotWriteAgainAndCorruptTrustStorageNeverReconnects() = runTest(dispatcher) {
        val prompt = hostPrompt()
        whenever(known.check(any(), any(), anyOrNull(), anyOrNull())).thenReturn(HostKeyCheckResult.Accepted)
        vm.trustHostKeyAndReconnect(prompt); runCurrent()
        verify(known, never()).trust(any())
        val blocked = hostPrompt()
        whenever(known.check(any(), any(), anyOrNull(), anyOrNull())).thenReturn(HostKeyCheckResult.CorruptStore)
        clearInvocations(connect)
        vm.trustHostKeyAndReconnect(blocked); runCurrent()
        assertEquals("Trusted-host storage is corrupt.", vm.projectTabs.value.single { it.projectId == 1L }.connectionError)
        assertTrue(vm.trustingHostKeys.value.isEmpty()); verifyNoInteractions(connect)
    }
    @Test fun invalidHostKeyCandidateStaysBlockedAndDoesNotSaveTrust() = runTest(dispatcher) {
        val prompt = hostPrompt()
        whenever(known.check(any(), any(), anyOrNull(), anyOrNull())).thenReturn(HostKeyCheckResult.InvalidCandidate)
        vm.trustHostKeyAndReconnect(prompt); runCurrent()
        assertEquals("The server presented an invalid SSH host key.", vm.projectTabs.value.single { it.projectId == 1L }.connectionError)
        verify(known, never()).trust(any())
    }
    @Test fun deletingALocalProjectMovesItsDirectoryToTrashBeforeRemovingItsConfiguration() = runTest(dispatcher) {
        val folder = File(temp.root, "local").apply { mkdirs() }
        File(folder, "keep.txt").writeText("fixture")
        vm.closeProject(3, TerminalSessionId("old-3"), deleteProject = true); runCurrent()
        assertFalse(folder.exists())
        assertEquals("fixture", File(temp.root, ".trash").listFiles()!!.single().resolve("keep.txt").readText())
        verify(projects).delete(c); assertFalse(3L in closed)
        assertFalse(vm.projectTabs.value.any { it.projectId == 3L })
    }
    @Test fun deletingRemoteProjectUsesOnlyItsOwnTransportAndDoesNotKillOtherTmuxSessions() = runTest(dispatcher) {
        vm.closeProject(1, TerminalSessionId("old-1"), killTmuxSession = true, deleteProject = true); runCurrent()
        verify(connection).runSilent(argThat { contains("tmux kill-session") && contains("one") })
        verify(connection).runSilent(argThat { contains(".trash") && contains("mv ") })
        verify(projects).delete(a)
        verify(sessions).close(TerminalSessionId("old-1"), false, true)
        assertTrue(live.value.any { it.projectId == 2L })
    }
    @Test fun deletingClosedRemoteProjectUsesAndReleasesATemporaryConnection() = runTest(dispatcher) {
        vm.closeProject(1, null, deleteProject = true); runCurrent()
        verify(connect).invoke(server); verify(ssh).destroySession("new-session")
        verify(projects).delete(a)
    }
    @Test fun renamingSuccessAndFailureAreReportedWithoutDisconnectingAnything() = runTest(dispatcher) {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiMessages.collect { messages += it } }
        vm.renameProject(1, "Title"); runCurrent()
        verify(projects).renameDisplayName(1, "Title")
        assertTrue(messages.contains("Project renamed"))
        whenever(projects.renameDisplayName(1, "Invalid")).thenThrow(IllegalArgumentException("Invalid title"))
        vm.renameProject(1, "Invalid"); runCurrent()
        assertTrue(messages.contains("Invalid title")); assertEquals(3, live.value.size)
    }
    @Test fun allProjectReconnectIncludesConnectedSshTabsButSkipsLocalTabs() = runTest(dispatcher) {
        vm.reconnectAllProjects(); runCurrent()
        verify(connection).bindProject(1, "one"); verify(connection).bindProject(2, "two")
        verify(connection, never()).bindProject(3, "local")
    }
    @Test fun repeatedReconnectIsCoalescedAndClosingCancelsOnlyItsPendingTransport() = runTest(dispatcher) {
        connected.value = false; errors.value = null
        vm.reconnectProject(1); vm.reconnectProject(1); runCurrent()
        verify(connect, times(1)).invoke(server)
        vm.closeSession(1, TerminalSessionId("old-1")); runCurrent()
        verify(ssh).destroySession("new-session")
        assertFalse(vm.projectTabs.value.any { it.projectId == 1L })
        assertTrue(live.value.any { it.projectId == 2L })
    }
    @Test fun closingWithAnOldSessionIdAlsoClosesItsAlreadyRegisteredReplacement() = runTest(dispatcher) {
        live.value += meta(a, "replacement")
        vm.closeSession(1, TerminalSessionId("old-1")); runCurrent()
        assertFalse(live.value.any { it.projectId == 1L })
        assertTrue(live.value.any { it.projectId == 2L })
    }
    @Test fun aMismatchedCloseIdCannotCloseAnotherProject() = runTest(dispatcher) {
        vm.closeSession(1, TerminalSessionId("old-2")); runCurrent()
        assertFalse(live.value.any { it.projectId == 1L })
        assertTrue(live.value.any { it.projectId == 2L })
        verify(sessions, never()).close(TerminalSessionId("old-2"), false, true)
    }
    @Test fun manualAndAutomaticBackgroundStartsRespectConsentAndHandleServiceRejection() {
        Mockito.mockConstruction(android.content.Intent::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF)).use {
            vm.startRecommendedBackgroundSsh(true, rememberAutomaticStart = true)
            verify(settings).setAutomaticallyStartBackgroundSsh(true)
            assertEquals(BackgroundSshMode.STARTING, mode.mode.value)
            vm.startRecommendedBackgroundSsh(true)
            mode.dispatch(BackgroundSshEvent.ServiceStopped)
            preferences.value = preferences.value.copy(automaticallyStartBackgroundSsh = true, keepSshActiveInBackground = true)
            runtimeState.value = runtimeState.value.copy(appInForeground = true, remoteProjectIds = setOf(1))
            assertTrue(vm.tryAutomaticallyStartBackgroundSsh(true))
            mode.dispatch(BackgroundSshEvent.ServiceStopped)
            whenever(context.startService(any())).thenThrow(IllegalStateException("OS rejected service"))
            vm.startRecommendedBackgroundSsh(true)
            verify(settings).setKeepSshActiveInBackground(false)
            assertEquals(BackgroundSshMode.OFF, mode.mode.value)
        }
    }
    @Test fun backgroundStopInProgressDoesNotStartAnotherServiceAndReminderCanBeDismissed() = runTest(dispatcher) {
        mode.dispatch(BackgroundSshEvent.UserStop)
        vm.startRecommendedBackgroundSsh(true)
        verify(settings).setKeepSshActiveInBackground(false)
        vm.dismissBackgroundSshRecommendation()
        verify(settings, atLeastOnce()).setBackgroundSshRecommendationHandled()
        vm.dismissBackgroundSshRestartReminder(); runCurrent()
        assertFalse(vm.showBackgroundSshRestartReminder.value)
    }
    @Test fun pendingEnterTimeoutClearsTheBusyFlagAndNeverSendsToAnotherTerminal() = runTest(dispatcher) {
        whenever(sessions.pasteTextToActive(any(), anyOrNull())).thenReturn(TerminalSessionId("old-1"))
        // Suspend without blocking the test dispatcher or waiting real time.
        whenever(sessions.awaitPendingWrites(any())).doSuspendableAnswer { delay(60_000) }
        assertTrue(vm.sendTextInputToActive("echo pending", true))
        advanceTimeBy(15_001); runCurrent()
        assertTrue(vm.pendingInputSessions.value.isEmpty())
        verify(sessions, never()).sendBytesToSession(any(), any())
    }
}
