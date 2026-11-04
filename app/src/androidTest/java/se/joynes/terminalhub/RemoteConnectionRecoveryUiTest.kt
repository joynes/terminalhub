package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.testing.*
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.security.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.di.SshModule
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.fixtures.RemoteHostFixture
import se.joynes.terminalhub.ui.screen.sessions.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@UninstallModules(SshModule::class)
class RemoteConnectionRecoveryUiTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    private val remote = RemoteHostFixture()
    @BindValue @JvmField val connector: SshTransportConnector = remote
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository
    @Inject lateinit var sessions: TerminalSessionManager
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var knownHosts: KnownHostRepository
    @Inject lateinit var runtime: se.joynes.terminalhub.data.runtime.AppRuntimeRepository
    private lateinit var host: SessionHostViewModel
    private lateinit var original: AppSettings
    private lateinit var ids: List<Long>
    @Before fun setup() {
        hilt.inject(); original = settings.settings.value
        compose.runOnIdle { sessions.clearForConfigImport() }; db.clearAllTables()
        for (name in listOf("session_host", "tmux_window_layouts")) compose.activity.getSharedPreferences(name, 0).edit().clear().commit()
        settings.applySettingsImport(settings.settings.value, AppSettings(sshKeepaliveEnabled = false))
        settings.setBackgroundSshRecommendationHandled(); knownHosts.forget("recovery-fixture.invalid", 22)
        ids = runBlocking {
            val server = servers.save(Server(name = "Recovery", host = "recovery-fixture.invalid", username = "fixture"))
            listOf("remote-one", "remote-two").map { projects.save(Project(serverId = server, name = it, customScript = "", useTmux = true)) }
        }
    }
    @After fun cleanup() {
        compose.runOnIdle { sessions.clearForConfigImport() }
        compose.activity.stopService(android.content.Intent(compose.activity, se.joynes.terminalhub.service.BackgroundSshService::class.java))
        compose.waitUntil(5000) { !runtime.state.value.foregroundServiceRunning }
        db.clearAllTables(); knownHosts.forget("recovery-fixture.invalid", 22)
        settings.applySettingsImport(settings.settings.value, original)
    }
    private fun show(failure: Exception? = null) {
        remote.failure = failure
        compose.setContent { TerminalHubTheme {
            host = hiltViewModel()
            SessionHostScreen(onOpenServers = {}, onAddServer = {}, onAddProject = {}, onOpenLogs = {}, onOpenSettings = {}, viewModel = host)
        } }
    }
    private fun challenge(kind: HostKeyChallengeKind) = HostKeyChallenge(SshEndpoint("recovery-fixture.invalid", 22), kind,
        "ssh-ed25519", byteArrayOf(4, 5, 6), "SHA256:recovery-new", trustedFingerprint = if (kind == HostKeyChallengeKind.CHANGED) "SHA256:recovery-old" else null)
    @Test fun failedConnectionDisplaysErrorAndReconnectCreatesLiveTerminal() {
        show(java.io.IOException("Network fixture unavailable"))
        compose.waitUntil(10000) { host.projectTabs.value.size == 2 && host.projectTabs.value.none { it.isConnecting } }
        compose.onNodeWithText("CONNECTION FAILED").assertIsDisplayed()
        compose.onNode(hasText("Network fixture unavailable", substring = true)).assertExists()
        remote.failure = null
        compose.onNodeWithText("RECONNECT").performClick()
        compose.waitUntil(10000) { sessions.sessions.value.any { it.isConnected } }
        compose.onNodeWithText("CONNECTION FAILED").assertDoesNotExist()
    }
    @Test fun oneExplicitTrustApprovalReconnectsBothProjectsWaitingOnTheSameHost() {
        show(HostKeyVerificationException(challenge(HostKeyChallengeKind.UNKNOWN), "Unknown host fixture"))
        compose.waitUntil(10000) { host.hostKeyPrompts.value.firstOrNull()?.projectIds?.size == 2 }
        compose.onNodeWithText("TRUST SSH HOST?").assertIsDisplayed()
        compose.onNodeWithText("2 tabs are waiting for this server. One approval reconnects them all.").assertExists()
        remote.failure = null
        compose.onNodeWithText("TRUST AND RECONNECT").performClick()
        compose.waitUntil(15000) { sessions.sessions.value.count { it.isConnected } == 2 }
        assertTrue(knownHosts.lookup("recovery-fixture.invalid", 22) is KnownHostLookup.Trusted)
        compose.onNodeWithText("TRUST SSH HOST?").assertDoesNotExist()
    }
    @Test fun changedHostWarningBlocksTrustAndCanBeDismissedWithoutSavingReplacement() {
        show(HostKeyVerificationException(challenge(HostKeyChallengeKind.CHANGED), "Changed host fixture"))
        compose.waitUntil(10000) { host.hostKeyPrompts.value.firstOrNull()?.projectIds?.size == 2 }
        compose.onNodeWithText("SSH HOST KEY CHANGED").assertIsDisplayed()
        compose.onNodeWithText("Trusted: SHA256:recovery-old").assertExists()
        compose.onNodeWithText("TRUST AND RECONNECT").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(5000) { host.hostKeyPrompts.value.isEmpty() }
        assertTrue(knownHosts.lookup("recovery-fixture.invalid", 22) is KnownHostLookup.Missing)
        assertTrue(sessions.sessions.value.isEmpty())
    }
    @Test fun decliningBackgroundRecommendationKeepsConnectedProjectsAndRecordsDecision() {
        settings.applySettingsImport(settings.settings.value, settings.settings.value.copy(backgroundSshRecommendationHandled = false))
        show()
        compose.waitUntil(15000) { compose.onAllNodesWithText("KEEP SSH CONNECTED WHEN SWITCHING APPS?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("NOT NOW").performClick()
        compose.waitUntil(10000) { sessions.sessions.value.count { it.isConnected } == 2 }
        assertTrue(settings.settings.value.backgroundSshRecommendationHandled)
        assertFalse(settings.settings.value.keepSshActiveInBackground)
    }
    @Test fun acceptingBackgroundRecommendationStartsTheServiceAndRemembersAutomaticStartOnlyAfterApproval() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            compose.activity.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        settings.setBackgroundSshRecommendationHandled(false)
        show()
        compose.waitUntil(15000) { compose.onAllNodesWithText("KEEP SSH CONNECTED WHEN SWITCHING APPS?").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(settings.settings.value.automaticallyStartBackgroundSsh)
        compose.onNode(isToggleable() and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("KEEP ALIVE").performClick()
        compose.waitUntil(10000) { runtime.state.value.foregroundServiceRunning }
        assertTrue(settings.settings.value.keepSshActiveInBackground)
        assertTrue(settings.settings.value.automaticallyStartBackgroundSsh)
        assertTrue(settings.settings.value.backgroundSshRecommendationHandled)
        assertEquals(2, sessions.sessions.value.count { it.isConnected })
    }
    @Test fun reminderAfterStoppedServiceCanBeDismissedWithoutDisconnectingProjects() {
        settings.setKeepSshActiveInBackground(true)
        show()
        compose.waitUntil(15000) { host.showBackgroundSshRestartReminder.value }
        compose.onNodeWithText("NOT NOW").performClick()
        compose.waitUntil(5000) { !host.showBackgroundSshRestartReminder.value }
        assertEquals(2, sessions.sessions.value.count { it.isConnected })
        assertFalse(runtime.state.value.foregroundServiceRunning)
    }
}
