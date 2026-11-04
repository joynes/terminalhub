package se.joynes.terminalhub.domain.usecase

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.repository.AppLogRepository
import se.joynes.terminalhub.data.model.AppLogEntry
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.domain.ScriptTemplateEngine

class LogExportAndTmuxStartTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun logExportWritesEveryEntryInRepositoryOrderAndClosesTheFile() = runBlocking {
        val context: Context = mock()
        whenever(context.getExternalFilesDir(null)).thenReturn(temporary.root)
        val logs: AppLogRepository = mock()
        whenever(logs.recentLogs(1000)).thenReturn(flowOf(listOf(
            AppLogEntry(1, 100, "INFO", "SSH", "Connected å"), AppLogEntry(2, 200, "ERROR", "Upload", "Failed\nsecond line"))))
        val result = ExportSessionLog(context, logs)()
        assertEquals(temporary.root, result.parentFile)
        assertEquals("[INFO] 100 [SSH] Connected å\n[ERROR] 200 [Upload] Failed\nsecond line\n", result.readText())
        assertTrue(result.delete())
    }
    @Test fun emptyLogExportProducesAnEmptyReadableFile() = runBlocking {
        val context: Context = mock(); whenever(context.getExternalFilesDir(null)).thenReturn(temporary.root)
        val logs: AppLogRepository = mock(); whenever(logs.recentLogs(1000)).thenReturn(flowOf(emptyList()))
        assertEquals("", ExportSessionLog(context, logs)().readText())
    }
    @Test fun missingSshSessionCannotPretendToStartTmux() = runBlocking {
        val manager: SshManager = mock()
        assertFalse(StartTmuxSession(manager, mock())("missing", "project"))
    }
    @Test fun tmuxNameWithQuotesStaysOneLiteralShellArgument() = runBlocking {
        val manager: SshManager = mock(); val connection: SshConnection = mock()
        whenever(manager.getSession("session")).thenReturn(connection)
        assertTrue(StartTmuxSession(manager, mock())("session", "project ' ${'$'}HOME"))
        verify(connection).send("tmux new-session -d -s 'project '\\'' ${'$'}HOME' 2>/dev/null || true\n")
        verify(connection).send("tmux attach-session -t 'project '\\'' ${'$'}HOME'\n")
    }
    @Test fun customStartupScriptSubstitutesProjectPathAndNameAndLeavesEmptyOrMissingSessionsAlone() {
        val manager: SshManager = mock(); val connection: SshConnection = mock()
        whenever(manager.getSession("current")).thenReturn(connection)
        val apply = ApplySessionScript(ScriptTemplateEngine(), manager)
        val server = Server(id = 1, name = "Fixture", host = "fixture.invalid", username = "user", projectsFolder = "/srv/projects")
        val project = Project(serverId = 1, name = "sample", customScript = "cd {{PROJECT_PATH}}\nprintf {{PROJECT_NAME}}")
        apply("current", server, project)
        verify(connection).send("cd /srv/projects/sample\nprintf sample\n")
        clearInvocations(manager, connection)
        apply("current", server, project.copy(customScript = ""))
        verifyNoInteractions(manager, connection)
        apply("missing", server, project)
        verifyNoInteractions(connection)
    }
}
