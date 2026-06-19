package se.joynes.terminalhub.data.security

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedSshKey(
    val privateKeyPem: String,
    val publicKeyOpenSsh: String
)

@Singleton
class SshKeyGenerator @Inject constructor() {
    fun generate(comment: String): GeneratedSshKey {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(4096)
        val pair = generator.generateKeyPair()
        val privateKey = pair.private as RSAPrivateCrtKey
        val publicKey = pair.public as RSAPublicKey
        return GeneratedSshKey(
            privateKeyPem = encodeRsaPrivateKeyPem(privateKey),
            publicKeyOpenSsh = encodeOpenSshPublicKey(publicKey, comment)
        )
    }

    private fun encodeRsaPrivateKeyPem(key: RSAPrivateCrtKey): String {
        val der = derSequence(
            derInteger(BigInteger.ZERO),
            derInteger(key.modulus),
            derInteger(key.publicExponent),
            derInteger(key.privateExponent),
            derInteger(key.primeP),
            derInteger(key.primeQ),
            derInteger(key.primeExponentP),
            derInteger(key.primeExponentQ),
            derInteger(key.crtCoefficient)
        )
        val body = Base64.getEncoder().encodeToString(der)
            .chunked(64)
            .joinToString("\n")
        return "-----BEGIN RSA PRIVATE KEY-----\n$body\n-----END RSA PRIVATE KEY-----"
    }

    private fun encodeOpenSshPublicKey(key: RSAPublicKey, comment: String): String {
        val blob = ByteArrayOutputStream().apply {
            writeSshString("ssh-rsa".toByteArray(Charsets.US_ASCII))
            writeSshMpInt(key.publicExponent)
            writeSshMpInt(key.modulus)
        }.toByteArray()
        val encoded = Base64.getEncoder().encodeToString(blob)
        return "ssh-rsa $encoded $comment"
    }

    private fun derSequence(vararg values: ByteArray): ByteArray =
        derTag(0x30, values.fold(ByteArray(0)) { acc, next -> acc + next })

    private fun derInteger(value: BigInteger): ByteArray =
        derTag(0x02, value.toByteArray())

    private fun derTag(tag: Int, value: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + derLength(value.size) + value

    private fun derLength(length: Int): ByteArray {
        if (length < 128) return byteArrayOf(length.toByte())
        val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes
    }

    private fun ByteArrayOutputStream.writeSshString(value: ByteArray) {
        writeUint32(value.size)
        write(value)
    }

    private fun ByteArrayOutputStream.writeSshMpInt(value: BigInteger) {
        writeSshString(value.toByteArray())
    }

    private fun ByteArrayOutputStream.writeUint32(value: Int) {
        write(byteArrayOf(
            ((value ushr 24) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            (value and 0xff).toByte()
        ))
    }
}
