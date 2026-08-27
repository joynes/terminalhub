package se.joynes.terminalhub.data.security

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KnownHostRepositoryTest {
    @Test
    fun `fingerprint uses OpenSSH SHA256 format without padding`() {
        assertEquals(
            "SHA256:LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ",
            formatSshSha256Fingerprint("hello".toByteArray())
        )
    }

    @Test
    fun `hostname normalization trims and ignores case`() {
        assertEquals("example.internal", KnownHostRepository.normalizeHostname("  EXAMPLE.Internal "))
    }

    @Test
    fun `first contact rejects then explicit trust stores exact key and reconnect matches`() {
        val repository = repository()
        val key = byteArrayOf(1, 2, 3, 4)
        val first = repository.check("Host", 22, "ssh-ed25519", key) as HostKeyCheckResult.Rejected

        assertEquals(HostKeyChallengeKind.UNKNOWN, first.challenge.kind)
        val trusted = repository.trust(first.challenge)
        assertEquals("ssh-ed25519", trusted.algorithm)
        assertArrayEquals(key, trusted.keyBytes)
        assertEquals(HostKeyCheckResult.Accepted, repository.check("host", 22, "ssh-ed25519", key))
    }

    @Test
    fun `changed algorithm or bytes rejects without mutating trusted record`() {
        val repository = repository()
        val original = byteArrayOf(7, 8, 9)
        val challenge = (repository.check("host", 22, "ssh-ed25519", original) as HostKeyCheckResult.Rejected).challenge
        repository.trust(challenge)

        val changedBytes = repository.check("host", 22, "ssh-ed25519", byteArrayOf(7, 8, 0)) as HostKeyCheckResult.Rejected
        val changedAlgorithm = repository.check("host", 22, "rsa-sha2-512", original) as HostKeyCheckResult.Rejected

        assertEquals(HostKeyChallengeKind.CHANGED, changedBytes.challenge.kind)
        assertEquals(HostKeyChallengeKind.CHANGED, changedAlgorithm.challenge.kind)
        val stillTrusted = repository.lookup("HOST", 22) as KnownHostLookup.Trusted
        assertArrayEquals(original, stillTrusted.knownHost.keyBytes)
        assertEquals("ssh-ed25519", stillTrusted.knownHost.algorithm)
    }

    @Test
    fun `ports are isolated and usernames cannot affect endpoint trust`() {
        val repository = repository()
        val key = byteArrayOf(5, 5, 5)
        val challenge = (repository.check("host", 22, "ssh-ed25519", key) as HostKeyCheckResult.Rejected).challenge
        repository.trust(challenge)

        assertEquals(HostKeyCheckResult.Accepted, repository.check("HOST", 22, "ssh-ed25519", key))
        assertTrue(repository.lookup("host", 2222) is KnownHostLookup.Missing)
    }

    @Test
    fun `forget removes only endpoint and next attempt is first trust again`() {
        val repository = repository()
        val key = byteArrayOf(4, 2)
        val challenge = (repository.check("host", 22, "ssh-rsa", key) as HostKeyCheckResult.Rejected).challenge
        repository.trust(challenge)
        repository.forget("host", 22)

        val next = repository.check("host", 22, "ssh-rsa", key) as HostKeyCheckResult.Rejected
        assertEquals(HostKeyChallengeKind.UNKNOWN, next.challenge.kind)
    }

    @Test
    fun `corrupt record fails closed`() {
        val storage = mutableMapOf<String, String>()
        val repository = repository(storage)
        val challenge = (repository.check("host", 22, "ssh-rsa", byteArrayOf(1)) as HostKeyCheckResult.Rejected).challenge
        repository.trust(challenge)
        val key = storage.keys.single()
        storage[key] = "not-a-valid-record"

        assertEquals(HostKeyCheckResult.CorruptStore, repository.check("host", 22, "ssh-rsa", byteArrayOf(1)))
    }

    private fun repository(storage: MutableMap<String, String> = mutableMapOf()): KnownHostRepository {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        whenever(prefs.contains(any())).thenAnswer { storage.containsKey(it.getArgument(0)) }
        whenever(prefs.getString(any(), anyOrNull())).thenAnswer { storage[it.getArgument(0)] }
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenAnswer {
            storage[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        whenever(editor.remove(any())).thenAnswer {
            storage.remove(it.getArgument(0))
            editor
        }
        whenever(editor.apply()).thenAnswer { null }
        return KnownHostRepository(context)
    }
}
