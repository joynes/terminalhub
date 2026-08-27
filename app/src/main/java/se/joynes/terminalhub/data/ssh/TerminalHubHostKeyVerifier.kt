package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import se.joynes.terminalhub.data.security.HostKeyChallenge
import se.joynes.terminalhub.data.security.HostKeyCheckResult
import se.joynes.terminalhub.data.security.KnownHostRepository
import se.joynes.terminalhub.data.security.SshEndpoint
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

class HostKeyVerificationException(
    val challenge: HostKeyChallenge?,
    message: String
) : IOException(message)

@Singleton
class TerminalHubHostKeyVerifier @Inject constructor(
    private val knownHosts: KnownHostRepository
) : ExtendedServerHostKeyVerifier() {
    private val rejectedAttempts = ConcurrentHashMap<SshEndpoint, HostKeyVerificationException>()

    fun prepareAttempt(host: String, port: Int) {
        rejectedAttempts.remove(KnownHostRepository.endpoint(host, port))
    }

    fun failureFor(host: String, port: Int): HostKeyVerificationException? =
        rejectedAttempts[KnownHostRepository.endpoint(host, port)]

    override fun verifyServerHostKey(
        hostname: String?,
        port: Int,
        serverHostKeyAlgorithm: String?,
        serverHostKey: ByteArray?
    ): Boolean {
        val host = hostname.orEmpty()
        val endpoint = runCatching { KnownHostRepository.endpoint(host, port) }.getOrNull() ?: return false
        return when (val result = knownHosts.check(host, port, serverHostKeyAlgorithm, serverHostKey)) {
            HostKeyCheckResult.Accepted -> true
            is HostKeyCheckResult.Rejected -> {
                val message = when (result.challenge.kind) {
                    se.joynes.terminalhub.data.security.HostKeyChallengeKind.UNKNOWN ->
                        "Unknown SSH host key. Verify the displayed fingerprint before trusting it."
                    se.joynes.terminalhub.data.security.HostKeyChallengeKind.CHANGED ->
                        "SSH host key changed. Connection blocked; verify the server before forgetting the trusted key."
                }
                rejectedAttempts[endpoint] = HostKeyVerificationException(result.challenge, message)
                false
            }
            HostKeyCheckResult.CorruptStore -> {
                rejectedAttempts[endpoint] = HostKeyVerificationException(
                    null,
                    "The local trusted-host record is corrupt. Forget it in server settings and verify again."
                )
                false
            }
            HostKeyCheckResult.InvalidCandidate -> {
                rejectedAttempts[endpoint] = HostKeyVerificationException(null, "The server presented an invalid SSH host key.")
                false
            }
        }
    }

    // Allow negotiation to reach the verifier callback even if the server changed algorithms;
    // the callback then produces the explicit old/new fingerprint warning instead of a vague
    // "no matching algorithm" error.
    override fun getKnownKeyAlgorithmsForHost(host: String?, port: Int): List<String>? = null

    override fun removeServerHostKey(host: String?, port: Int, algorithm: String?, hostKey: ByteArray?) = Unit

    override fun addServerHostKey(hostname: String?, port: Int, algorithm: String?, hostKey: ByteArray?) = Unit
}

internal inline fun <T> withVerifiedHostKey(
    verifier: TerminalHubHostKeyVerifier,
    host: String,
    port: Int,
    connect: () -> T
): T {
    verifier.prepareAttempt(host, port)
    return try {
        connect()
    } catch (error: Exception) {
        throw verifier.failureFor(host, port) ?: error
    }
}
