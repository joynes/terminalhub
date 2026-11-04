package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import se.joynes.terminalhub.data.security.*
import java.io.IOException

class HostKeyVerificationFailureTest {
    private val store = mock(KnownHostRepository::class.java)
    private val verifier = TerminalHubHostKeyVerifier(store)
    private val key = byteArrayOf(1, 2, 3)
    private fun challenge(kind: HostKeyChallengeKind) = HostKeyChallenge(SshEndpoint("server.test", 22), kind,
        "ssh-ed25519", key, "SHA256:new", trustedFingerprint = if (kind == HostKeyChallengeKind.CHANGED) "SHA256:old" else null)
    @Test fun `unknown and changed host errors survive transport wrapping and retain fingerprints`() {
        for (kind in HostKeyChallengeKind.entries) {
            val challenge = challenge(kind)
            `when`(store.check("server.test", 22, "ssh-ed25519", key)).thenReturn(HostKeyCheckResult.Rejected(challenge))
            val error = runCatching { withVerifiedHostKey(verifier, "server.test", 22) {
                assertFalse(verifier.verifyServerHostKey("server.test", 22, "ssh-ed25519", key))
                throw IOException("Transport wrapped rejection")
            } }.exceptionOrNull() as HostKeyVerificationException
            assertSame(challenge, error.challenge)
            assertTrue(error.message!!.contains(if (kind == HostKeyChallengeKind.UNKNOWN) "Unknown" else "changed"))
            assertSame(error, verifier.failureFor("SERVER.TEST", 22))
            verifier.prepareAttempt("server.test", 22)
            assertNull(verifier.failureFor("server.test", 22))
        }
    }
    @Test fun `invalid candidate and corrupt trust store fail closed without a trustable challenge`() {
        listOf(HostKeyCheckResult.CorruptStore to "corrupt", HostKeyCheckResult.InvalidCandidate to "invalid").forEach { (result, message) ->
            `when`(store.check("server.test", 22, "ssh-ed25519", key)).thenReturn(result)
            assertFalse(verifier.verifyServerHostKey("server.test", 22, "ssh-ed25519", key))
            val error = verifier.failureFor("server.test", 22)!!
            assertNull(error.challenge); assertTrue(error.message!!.contains(message))
        }
        assertFalse(verifier.verifyServerHostKey(null, -1, null, null))
    }
    @Test fun `new attempt clears prior rejection and preserves unrelated network error`() {
        `when`(store.check("server.test", 22, "ssh-ed25519", key)).thenReturn(HostKeyCheckResult.Rejected(challenge(HostKeyChallengeKind.UNKNOWN)))
        verifier.verifyServerHostKey("server.test", 22, "ssh-ed25519", key)
        val network = IOException("Network lost")
        assertSame(network, runCatching { withVerifiedHostKey(verifier, "server.test", 22) { throw network } }.exceptionOrNull())
        `when`(store.check("server.test", 22, "ssh-ed25519", key)).thenReturn(HostKeyCheckResult.Accepted)
        assertEquals("connected", withVerifiedHostKey(verifier, "server.test", 22) {
            assertTrue(verifier.verifyServerHostKey("server.test", 22, "ssh-ed25519", key)); "connected"
        })
        assertNull(verifier.failureFor("server.test", 22))
    }
}
