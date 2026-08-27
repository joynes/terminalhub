package se.joynes.terminalhub.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SshEndpoint(val normalizedHost: String, val port: Int)

data class KnownHost(
    val endpoint: SshEndpoint,
    val algorithm: String,
    val keyBytes: ByteArray,
    val fingerprint: String,
    val firstTrustedAt: Long,
    val lastSeenAt: Long?
) {
    override fun equals(other: Any?): Boolean = other is KnownHost &&
        endpoint == other.endpoint && algorithm == other.algorithm &&
        keyBytes.contentEquals(other.keyBytes) && fingerprint == other.fingerprint &&
        firstTrustedAt == other.firstTrustedAt && lastSeenAt == other.lastSeenAt

    override fun hashCode(): Int = 31 * endpoint.hashCode() + keyBytes.contentHashCode()
}

enum class HostKeyChallengeKind { UNKNOWN, CHANGED }

data class HostKeyChallenge(
    val endpoint: SshEndpoint,
    val kind: HostKeyChallengeKind,
    val presentedAlgorithm: String,
    val presentedKeyBytes: ByteArray,
    val presentedFingerprint: String,
    val trustedAlgorithm: String? = null,
    val trustedFingerprint: String? = null
) {
    override fun equals(other: Any?): Boolean = other is HostKeyChallenge &&
        endpoint == other.endpoint && kind == other.kind &&
        presentedAlgorithm == other.presentedAlgorithm &&
        presentedKeyBytes.contentEquals(other.presentedKeyBytes) &&
        presentedFingerprint == other.presentedFingerprint &&
        trustedAlgorithm == other.trustedAlgorithm && trustedFingerprint == other.trustedFingerprint

    override fun hashCode(): Int = 31 * endpoint.hashCode() + presentedKeyBytes.contentHashCode()
}

sealed interface KnownHostLookup {
    data object Missing : KnownHostLookup
    data class Trusted(val knownHost: KnownHost) : KnownHostLookup
    data object Corrupt : KnownHostLookup
}

sealed interface HostKeyCheckResult {
    data object Accepted : HostKeyCheckResult
    data class Rejected(val challenge: HostKeyChallenge) : HostKeyCheckResult
    data object CorruptStore : HostKeyCheckResult
    data object InvalidCandidate : HostKeyCheckResult
}

@Singleton
class KnownHostRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun lookup(host: String, port: Int): KnownHostLookup {
        val endpoint = endpoint(host, port)
        val key = storageKey(endpoint)
        if (!prefs.contains(key)) return KnownHostLookup.Missing
        val raw = runCatching { prefs.getString(key, null) }.getOrNull()
            ?: return KnownHostLookup.Corrupt
        return decode(endpoint, raw)?.let(KnownHostLookup::Trusted) ?: KnownHostLookup.Corrupt
    }

    @Synchronized
    fun check(host: String, port: Int, algorithm: String?, keyBytes: ByteArray?): HostKeyCheckResult {
        if (algorithm.isNullOrBlank() || keyBytes == null || keyBytes.isEmpty()) {
            return HostKeyCheckResult.InvalidCandidate
        }
        val endpoint = endpoint(host, port)
        val fingerprint = formatSshSha256Fingerprint(keyBytes)
        return when (val stored = lookup(host, port)) {
            KnownHostLookup.Missing -> HostKeyCheckResult.Rejected(
                HostKeyChallenge(
                    endpoint = endpoint,
                    kind = HostKeyChallengeKind.UNKNOWN,
                    presentedAlgorithm = algorithm,
                    presentedKeyBytes = keyBytes.copyOf(),
                    presentedFingerprint = fingerprint
                )
            )
            KnownHostLookup.Corrupt -> HostKeyCheckResult.CorruptStore
            is KnownHostLookup.Trusted -> {
                val known = stored.knownHost
                if (known.algorithm == algorithm && known.keyBytes.contentEquals(keyBytes)) {
                    save(known.copy(lastSeenAt = System.currentTimeMillis()))
                    HostKeyCheckResult.Accepted
                } else {
                    HostKeyCheckResult.Rejected(
                        HostKeyChallenge(
                            endpoint = endpoint,
                            kind = HostKeyChallengeKind.CHANGED,
                            presentedAlgorithm = algorithm,
                            presentedKeyBytes = keyBytes.copyOf(),
                            presentedFingerprint = fingerprint,
                            trustedAlgorithm = known.algorithm,
                            trustedFingerprint = known.fingerprint
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun trust(challenge: HostKeyChallenge): KnownHost {
        require(challenge.kind == HostKeyChallengeKind.UNKNOWN) {
            "A changed host key must be forgotten before a replacement can be trusted."
        }
        check(lookup(challenge.endpoint.normalizedHost, challenge.endpoint.port) is KnownHostLookup.Missing) {
            "This endpoint already has a trusted key."
        }
        val now = System.currentTimeMillis()
        return KnownHost(
            endpoint = challenge.endpoint,
            algorithm = challenge.presentedAlgorithm,
            keyBytes = challenge.presentedKeyBytes.copyOf(),
            fingerprint = formatSshSha256Fingerprint(challenge.presentedKeyBytes),
            firstTrustedAt = now,
            lastSeenAt = now
        ).also(::save)
    }

    @Synchronized
    fun forget(host: String, port: Int) {
        prefs.edit().remove(storageKey(endpoint(host, port))).apply()
    }

    private fun save(knownHost: KnownHost) {
        val encodedKey = encodeBase64(knownHost.keyBytes)
        val encoded = listOf(
            RECORD_VERSION,
            knownHost.algorithm,
            encodedKey,
            knownHost.firstTrustedAt.toString(),
            (knownHost.lastSeenAt ?: -1L).toString()
        ).joinToString(SEPARATOR)
        prefs.edit().putString(storageKey(knownHost.endpoint), encoded).apply()
    }

    private fun decode(endpoint: SshEndpoint, raw: String): KnownHost? = runCatching {
        val parts = raw.split(SEPARATOR)
        require(parts.size == 5 && parts[0] == RECORD_VERSION)
        val algorithm = parts[1].takeIf { it.isNotBlank() } ?: error("missing algorithm")
        val bytes = decodeBase64(parts[2]).takeIf { it.isNotEmpty() } ?: error("missing key")
        val trustedAt = parts[3].toLong().also { require(it > 0) }
        val lastSeen = parts[4].toLong().takeIf { it >= 0 }
        KnownHost(endpoint, algorithm, bytes, formatSshSha256Fingerprint(bytes), trustedAt, lastSeen)
    }.getOrNull()

    companion object {
        private const val PREFS_NAME = "known_ssh_hosts"
        private const val RECORD_VERSION = "1"
        private const val SEPARATOR = "|"

        fun normalizeHostname(host: String): String = host.trim().lowercase(Locale.ROOT)

        fun endpoint(host: String, port: Int): SshEndpoint {
            require(port in 1..65535) { "Invalid SSH port" }
            val normalized = normalizeHostname(host)
            require(normalized.isNotBlank()) { "SSH host is required" }
            return SshEndpoint(normalized, port)
        }

        private fun storageKey(endpoint: SshEndpoint): String {
            val raw = "${endpoint.normalizedHost}:${endpoint.port}".toByteArray(Charsets.UTF_8)
            return "host_" + encodeBase64(raw).trimEnd('=').replace('+', '-').replace('/', '_')
        }
    }
}

fun formatSshSha256Fingerprint(keyBytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
    return "SHA256:" + encodeBase64(digest).trimEnd('=')
}

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun encodeBase64(bytes: ByteArray): String = buildString((bytes.size + 2) / 3 * 4) {
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xff
        val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else -1
        val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else -1
        append(BASE64_ALPHABET[first ushr 2])
        append(BASE64_ALPHABET[((first and 0x03) shl 4) or if (second >= 0) second ushr 4 else 0])
        append(if (second >= 0) BASE64_ALPHABET[((second and 0x0f) shl 2) or if (third >= 0) third ushr 6 else 0] else '=')
        append(if (third >= 0) BASE64_ALPHABET[third and 0x3f] else '=')
        index += 3
    }
}

private fun decodeBase64(value: String): ByteArray {
    require(value.length % 4 == 0)
    val output = ArrayList<Byte>(value.length / 4 * 3)
    value.chunked(4).forEach { chunk ->
        require(chunk.length == 4)
        val numbers = chunk.map { character ->
            if (character == '=') -1 else BASE64_ALPHABET.indexOf(character).also { require(it >= 0) }
        }
        require(numbers[0] >= 0 && numbers[1] >= 0)
        require(numbers[2] >= 0 || numbers[3] == -1)
        output += ((numbers[0] shl 2) or (numbers[1] ushr 4)).toByte()
        if (numbers[2] >= 0) output += (((numbers[1] and 0x0f) shl 4) or (numbers[2] ushr 2)).toByte()
        if (numbers[3] >= 0) output += (((numbers[2] and 0x03) shl 6) or numbers[3]).toByte()
    }
    return output.toByteArray()
}
