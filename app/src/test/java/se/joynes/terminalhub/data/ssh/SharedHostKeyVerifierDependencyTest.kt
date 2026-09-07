package se.joynes.terminalhub.data.ssh

import org.junit.Assert.assertTrue
import org.junit.Test

class SharedHostKeyVerifierDependencyTest {
    @Test
    fun `every raw SSH client requires shared verifier`() {
        listOf(
            TrileadSshTransportConnector::class.java,
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

    @Test
    fun `file transfers use shared transport pool instead of raw connections`() {
        listOf(ScpUploader::class.java, ScpDownloader::class.java).forEach { client ->
            assertTrue(
                "${client.simpleName} must receive SharedSshTransportPool",
                client.declaredConstructors.any { constructor ->
                    SharedSshTransportPool::class.java in constructor.parameterTypes
                }
            )
        }
    }
}
