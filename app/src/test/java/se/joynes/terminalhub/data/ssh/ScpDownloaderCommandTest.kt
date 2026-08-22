package se.joynes.terminalhub.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScpDownloaderCommandTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `file listing works in zsh when optional hidden globs do not match`() {
        val remoteDir = temporaryFolder.newFolder("remote project")
        File(remoteDir, "audio.wav").writeBytes(byteArrayOf(1, 2, 3))
        val shell = File("/bin/zsh").takeIf { it.isFile }?.absolutePath ?: "/bin/sh"

        val process = ProcessBuilder(shell, "-c", remoteFileListCommand(remoteDir.absolutePath))
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()

        assertEquals("stderr=$stderr", 0, process.waitFor())
        assertEquals("audio.wav\t3\n", stdout)
    }

    @Test
    fun `missing remote folder emits explicit marker`() {
        val missing = File(temporaryFolder.root, "missing folder")

        val process = ProcessBuilder("/bin/sh", "-c", remoteFileListCommand(missing.absolutePath))
            .redirectErrorStream(false)
            .start()
        val stderr = process.errorStream.bufferedReader().readText()

        assertEquals(3, process.waitFor())
        assertTrue(stderr.contains(ScpDownloader.REMOTE_DIR_MISSING_MARKER))
    }

    @Test
    fun `remote home path expands HOME but keeps project name literal`() {
        assertEquals("\"\$HOME/projects/price\\\$list\"", shellRemotePath("~/projects/price\$list"))
    }
}
