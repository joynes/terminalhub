package se.joynes.terminalhub

import com.trilead.ssh2.crypto.PEMDecoder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import se.joynes.terminalhub.data.security.SshKeyGenerator

class SshKeyGeneratorTest {
    @Test
    fun generatedKeyCanBeUsedByTrileadPemDecoder() {
        val key = SshKeyGenerator().generate("test@terminalhub")

        assertTrue(key.publicKeyOpenSsh.startsWith("ssh-rsa "))
        assertTrue(key.privateKeyPem.startsWith("-----BEGIN RSA PRIVATE KEY-----"))
        assertNotNull(PEMDecoder.decode(key.privateKeyPem.toCharArray(), null))
    }
}
