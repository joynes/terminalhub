package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import se.joynes.terminalhub.data.security.HostKeyChallenge
import se.joynes.terminalhub.data.security.HostKeyChallengeKind
import se.joynes.terminalhub.data.security.SshEndpoint
import se.joynes.terminalhub.domain.TerminalSessionId

class SessionReconnectUxTest {
    @Test
    fun `identical host challenge from several tabs becomes one prompt`() {
        val challenge = challenge("server.example", 22, byteArrayOf(1, 2, 3))

        val prompts = groupHostKeyChallenges(
            mapOf(10L to challenge, 11L to challenge, 12L to challenge)
        )

        assertEquals(1, prompts.size)
        assertEquals(setOf(10L, 11L, 12L), prompts.single().projectIds)
    }

    @Test
    fun `different endpoints remain separate trust decisions`() {
        val prompts = groupHostKeyChallenges(
            mapOf(
                10L to challenge("one.example", 22, byteArrayOf(1)),
                11L to challenge("two.example", 22, byteArrayOf(2))
            )
        )

        assertEquals(2, prompts.size)
    }

    @Test
    fun `background SSH is recommended once after first connected remote session`() {
        assertTrue(
            shouldShowBackgroundSshRecommendation(
                recommendationHandled = false,
                keepSshActiveInBackground = false,
                connectedRemoteSessionCount = 1
            )
        )
        assertFalse(
            shouldShowBackgroundSshRecommendation(
                recommendationHandled = true,
                keepSshActiveInBackground = false,
                connectedRemoteSessionCount = 1
            )
        )
        assertFalse(
            shouldShowBackgroundSshRecommendation(
                recommendationHandled = false,
                keepSshActiveInBackground = true,
                connectedRemoteSessionCount = 1
            )
        )
        assertFalse(
            shouldShowBackgroundSshRecommendation(
                recommendationHandled = false,
                keepSshActiveInBackground = false,
                connectedRemoteSessionCount = 0
            )
        )
    }

    @Test
    fun `replacement preserves only the tab that was active`() {
        val active = TerminalSessionId("active")
        val inactive = TerminalSessionId("inactive")

        assertTrue(shouldSwitchToReplacementSession(false, active, active))
        assertFalse(shouldSwitchToReplacementSession(false, inactive, active))
        assertTrue(shouldSwitchToReplacementSession(true, inactive, active))
    }

    @Test
    fun `connection progress clearly identifies one or several tabs`() {
        assertEquals("CONNECTING…", connectionProgressLabel(emptyList()))
        assertEquals("CONNECTING API…", connectionProgressLabel(listOf("api")))
        assertEquals(
            "CONNECTING 3 TABS…",
            connectionProgressLabel(listOf("api", "web", "worker"))
        )
    }

    @Test
    fun `reconnect progress replaces disconnected popup instead of stacking`() {
        assertEquals(
            TerminalConnectionOverlay.PROGRESS,
            terminalConnectionOverlay(
                hasRenderedSession = true,
                hasConnectingRemoteTabs = true,
                activeRemoteTabDisconnected = true
            )
        )
        assertEquals(
            TerminalConnectionOverlay.DISCONNECTED,
            terminalConnectionOverlay(
                hasRenderedSession = true,
                hasConnectingRemoteTabs = false,
                activeRemoteTabDisconnected = true
            )
        )
    }

    private fun challenge(host: String, port: Int, key: ByteArray) = HostKeyChallenge(
        endpoint = SshEndpoint(host, port),
        kind = HostKeyChallengeKind.UNKNOWN,
        presentedAlgorithm = "ssh-ed25519",
        presentedKeyBytes = key,
        presentedFingerprint = "SHA256:test"
    )
}
