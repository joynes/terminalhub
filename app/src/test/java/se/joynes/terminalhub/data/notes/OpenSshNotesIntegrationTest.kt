package se.joynes.terminalhub.data.notes

import com.trilead.ssh2.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.domain.ScriptTemplateEngine

/** Real OpenSSH SFTP protocol over process pipes; SSH authentication/pooling have separate tests. */
class OpenSshNotesIntegrationTest {
    @Test(timeout = 20_000) fun `real openssh atomic overwrite and reinstall recovery`() = runBlocking {
        val binary = listOf("/usr/libexec/sftp-server", "/usr/lib/openssh/sftp-server").firstOrNull { File(it).canExecute() }
        assumeTrue("Local OpenSSH SFTP server required", binary != null)
        val home = Files.createTempDirectory("terminalhub-notes-sftp-").toRealPath()
        Files.createDirectories(home.resolve("projects/demo"))
        val processes = mutableListOf<Process>()
        val transport = object : SshTransport {
            override fun openProjectSession(): Session = error("Notes must not open PTY")
            override fun openAuxiliarySession(): SshAuxiliarySession = error("Notes must not exec")
            override fun openSftpSession(): SshAuxiliarySftp {
                val process = ProcessBuilder(binary!!, "-d", home.toString()).start()
                processes.add(process)
                val session: Session = mock()
                whenever(session.stdout).thenReturn(process.inputStream)
                whenever(session.stdin).thenReturn(process.outputStream)
                doAnswer { process.outputStream.close(); process.destroy(); process.waitFor(2, TimeUnit.SECONDS); null }.whenever(session).close()
                val connection: Connection = mock()
                whenever(connection.openSession()).thenReturn(session)
                return SshAuxiliarySftp(SFTPv3Client(connection)) {}
            }
            override fun updateProjectIds(projectIds: Set<Long>) {}
            override fun close() {}
            override fun debugIdentity() = 1
        }
        val pool = SharedSshTransportPool(mock<AppLogger>(), object : SshTransportConnector {
            override fun connect(server: Server, password: String?, privateKeyPem: String?) = transport
        })
        val store = RemoteProjectNotesStore(pool, mock<SecurePrefsManager>(), ScriptTemplateEngine())
        val server = Server(id = 1, name = "fixture", host = "fixture", username = "fixture", projectsFolder = "~/projects")
        val project = Project(id = 1, serverId = 1, name = "demo")
        try {
            val empty = store.read(server, project)
            assertNull(empty.text)
            assertFalse(Files.exists(home.resolve(".terminalhub")))
            val original = "# First åäö 😀\r\n\n'quoted' \\ \$HOME"
            val first = store.write(server, project, original, empty)
            assertEquals(original, first.text)
            val updated = original + "\nsecond edit"
            val second = store.write(server, project, updated, first)
            assertEquals(updated, second.text)
            val restored = store.read(server.copy(id = 55, projectsFolder = home.resolve("projects").toString()), project.copy(id = 88, serverId = 55))
            assertEquals(first.key, restored.key)
            assertEquals(updated, restored.text)
            val notePath = home.resolve(".terminalhub/project-notes/${first.key}/note.md")
            assertEquals(updated, String(Files.readAllBytes(notePath), Charsets.UTF_8))
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(notePath)))
            assertEquals("rwx------", java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(notePath.parent)))
            store.write(server, project, "", second, delete = true)
            assertNull(store.read(server, project).text)
            assertEquals(0, Files.list(notePath.parent).use { it.count() })
        } finally {
            processes.forEach { it.destroyForcibly() }
            Files.walk(home).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}
