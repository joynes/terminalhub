package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.testing.*
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*
import se.joynes.terminalhub.data.security.*
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.di.SshModule
import se.joynes.terminalhub.fixtures.RemoteHostFixture
import se.joynes.terminalhub.ui.screen.servers.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@UninstallModules(SshModule::class)
class ServerIdentityWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    private val remote = RemoteHostFixture()
    @BindValue @JvmField val connector: SshTransportConnector = remote
    @Inject lateinit var knownHosts: KnownHostRepository
    private lateinit var vm: AddEditServerViewModel
    private val host = "identity-fixture.invalid"
    @Before fun setup() {
        hilt.inject(); knownHosts.forget(host, 22)
        compose.setContent { TerminalHubTheme {
            vm = hiltViewModel(); AddEditServerScreen(null, {}, vm)
        } }
        compose.runOnIdle { vm.update { copy(host = this@ServerIdentityWorkflowTest.host, username = "fixture") } }
    }
    @After fun cleanup() { knownHosts.forget(host, 22) }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun challenge(kind: HostKeyChallengeKind) = HostKeyChallenge(SshEndpoint(host, 22), kind,
        "ssh-ed25519", byteArrayOf(1, 2, 3), "SHA256:fixture-new", trustedFingerprint = if (kind == HostKeyChallengeKind.CHANGED) "SHA256:fixture-old" else null)
    @Test fun unknownHostCanBeCancelledThenExplicitlyTrustedRetriedAndForgotten() {
        remote.failure = HostKeyVerificationException(challenge(HostKeyChallengeKind.UNKNOWN), "Unknown host fixture")
        click("[ TEST SSH ]")
        compose.waitUntil(10000) { vm.state.value.hostKeyChallenge != null }
        compose.onNodeWithText("TRUST SSH HOST?").assertIsDisplayed()
        compose.onNodeWithText("CANCEL").performClick()
        assertTrue(knownHosts.lookup(host, 22) is KnownHostLookup.Missing)
        click("[ TEST SSH ]")
        compose.waitUntil(10000) { vm.state.value.hostKeyChallenge != null }
        remote.failure = null
        compose.onNodeWithText("TRUST AND RETRY").performClick()
        compose.waitUntil(10000) { vm.state.value.sshTestStatus == SshTestStatus.Success }
        click("HOST KEY TRUSTED")
        assertTrue(knownHosts.lookup(host, 22) is KnownHostLookup.Trusted)
        click("[ FORGET TRUSTED KEY ]")
        compose.onNodeWithText("CANCEL").performClick()
        assertTrue(knownHosts.lookup(host, 22) is KnownHostLookup.Trusted)
        click("[ FORGET TRUSTED KEY ]")
        compose.onNodeWithText("FORGET").performClick()
        compose.waitUntil(5000) { knownHosts.lookup(host, 22) is KnownHostLookup.Missing }
        compose.onNodeWithText("HOST KEY NOT TRUSTED").assertExists()
    }
    @Test fun changedHostShowsBothFingerprintsAndCannotBeTrustedFromWarning() {
        remote.failure = HostKeyVerificationException(challenge(HostKeyChallengeKind.CHANGED), "Changed host fixture")
        click("[ TEST SSH ]")
        compose.waitUntil(10000) { vm.state.value.hostKeyChallenge != null }
        compose.onNodeWithText("SSH HOST KEY CHANGED").assertIsDisplayed()
        compose.onNodeWithText("Trusted: SHA256:fixture-old").assertExists()
        compose.onNodeWithText("Presented: SHA256:fixture-new").assertExists()
        compose.onNodeWithText("TRUST AND RETRY").assertDoesNotExist()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("SSH FAIL").performScrollTo().assertExists()
        assertTrue(knownHosts.lookup(host, 22) is KnownHostLookup.Missing)
    }
    @Test fun generatedPublicKeyCanBeCopiedAndOptionalPasswordIsHiddenAgain() {
        click("[ GENERATE KEY ]")
        compose.waitUntil(15000) { vm.state.value.publicKey.isNotEmpty() }
        click("[ COPY ]")
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals(vm.state.value.publicKey, clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        click("[ USE ONE-TIME PASSWORD TO INSTALL KEY ]")
        compose.onNode(hasText("Password for public-key install") and hasSetTextAction()).performScrollTo().performTextInput("fixture-only")
        assertEquals("fixture-only", vm.state.value.password)
        click("[ HIDE PASSWORD OPTION ]")
        compose.onNodeWithText("ONE-TIME PASSWORD — OPTIONAL").assertDoesNotExist()
        assertTrue(vm.state.value.privateKey.contains("PRIVATE KEY"))
    }
}
