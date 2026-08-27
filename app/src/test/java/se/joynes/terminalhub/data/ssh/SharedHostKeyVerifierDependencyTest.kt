package se.joynes.terminalhub.data.ssh

import org.junit.Assert.assertTrue
import org.junit.Test

class SharedHostKeyVerifierDependencyTest {
    @Test
    fun `every raw SSH and SCP client requires shared verifier`() {
        listOf(
            SshConnection::class.java,
            SshConnectionFactory::class.java,
            ScpUploader::class.java,
            ScpDownloader::class.java,
            SshPublicKeyInstaller::class.java
        ).forEach { client ->
            assertTrue(
                "${client.simpleName} must receive TerminalHubHostKeyVerifier",
                client.declaredConstructors.any { constructor ->
                    TerminalHubHostKeyVerifier::class.java in constructor.parameterTypes
                }
            )
        }
    }
}
