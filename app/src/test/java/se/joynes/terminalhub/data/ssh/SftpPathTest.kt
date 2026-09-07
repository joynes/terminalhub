package se.joynes.terminalhub.data.ssh

import com.trilead.ssh2.SFTPv3Client
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SftpPathTest {
    @Test
    fun `remote home path is resolved through SFTP`() {
        val client: SFTPv3Client = mock()
        whenever(client.canonicalPath(".")).thenReturn("/home/demo")

        assertEquals("/home/demo/projects/music", resolveSftpPath(client, "~/projects/music"))
        assertEquals("/home/demo", resolveSftpPath(client, "~"))
    }

    @Test
    fun `absolute remote path remains unchanged`() {
        val client: SFTPv3Client = mock()

        assertEquals("/srv/projects/music", resolveSftpPath(client, "/srv/projects/music"))
    }

    @Test
    fun `remote subdirectory stays below project root`() {
        assertEquals("~/projects/demo/assets/audio", remoteSubdirectory("~/projects/demo", "assets/audio"))
        assertEquals("~/projects/demo", remoteSubdirectory("~/projects/demo/", ""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `remote subdirectory rejects traversal`() {
        remoteSubdirectory("~/projects/demo", "assets/../../secrets")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transfer file name rejects a path`() {
        requireValidTransferFileName("nested/secrets.txt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `transfer file name rejects parent traversal`() {
        requireValidTransferFileName("..")
    }
}
