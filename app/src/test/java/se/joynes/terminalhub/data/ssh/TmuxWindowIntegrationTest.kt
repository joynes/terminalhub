package se.joynes.terminalhub.data.ssh

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.domain.ScriptTemplateEngine

/** Executes production commands against a real, private tmux server/socket. */
class TmuxWindowIntegrationTest {
    private val folder = Files.createTempDirectory(File("/tmp").toPath(), "thtw-").toFile()
    private val working = File(folder, "project ' å").apply { mkdirs() }
    private val binary = listOf("/opt/homebrew/bin/tmux", "/usr/bin/tmux").firstOrNull { File(it).canExecute() }
    private fun q(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun run(command: String, success: Boolean = true): String {
        val builder = ProcessBuilder("/bin/bash", "-c", command).redirectErrorStream(true)
        builder.environment()["TMUX_TMPDIR"] = folder.path
        builder.environment()["TMUX"] = ""
        val process = builder.start()
        val text = process.inputStream.bufferedReader().readText()
        assertTrue("Command timed out", process.waitFor(10, TimeUnit.SECONDS))
        if (success) assertEquals(text, 0, process.exitValue()) else assertNotEquals("Expected a rejected command", 0, process.exitValue())
        return text
    }

    private fun tmux(args: String) = run("${q(binary!!)} $args")
    private fun snapshot(name: String = "project") = TmuxWindowCommands.parse(run(TmuxWindowCommands.list(name)))
    private fun execute(command: String) = TmuxWindowCommands.parse(run(command))

    @Before fun setup() {
        assumeTrue("A local tmux is needed for integration tests", binary != null)
        tmux("-f /dev/null new-session -d -s project -c ${q(working.path)} /bin/sh")
    }
    @After fun cleanup() {
        if (binary != null) runCatching { tmux("kill-server") }
        folder.deleteRecursively()
    }

    @Test fun `create switch rename and close keep one session and other terminal processes`() {
        val first = snapshot()
        assertEquals(1, first.windows.size)
        val firstPane = tmux("list-panes -t project -F '#{pane_id}'").trim()
        val pid = tmux("display-message -p -t ${q(firstPane)} '#{pane_pid}'").trim()
        val second = execute(TmuxWindowCommands.create(first, working.path, "Tests å😀"))
        assertEquals(first.sessionId, second.sessionId)
        assertEquals(2, second.windows.size)
        val added = second.windows.single { it.id != first.windows.single().id }
        assertEquals("Tests å😀", added.name)
        assertTrue(added.active)
        assertEquals(working.canonicalPath, tmux("display-message -p -t ${q(second.sessionId + ":" + added.id)} '#{pane_current_path}'").trim())
        val selected = execute(TmuxWindowCommands.select(second, first.windows.single()))
        assertEquals(first.windows.single().id, selected.windows.single { it.active }.id)
        val renamed = execute(TmuxWindowCommands.rename(selected, added, "Logs ' \$(touch HACKED)"))
        assertEquals("Logs ' \$(touch HACKED)", renamed.windows.first { it.id == added.id }.name)
        assertFalse(File(working, "HACKED").exists())
        val closed = execute(TmuxWindowCommands.close(renamed, added))
        assertEquals(first.windows.single().id, closed.windows.single().id)
        assertEquals(pid, tmux("display-message -p -t ${q(firstPane)} '#{pane_pid}'").trim())
        assertEquals(1, tmux("list-sessions -F '#{session_id}'").lines().count(String::isNotBlank))
    }

    @Test fun `exact project targets and membership prevent changes to another session`() {
        tmux("new-session -d -s project-other /bin/sh")
        val a = snapshot()
        val b = snapshot("project-other")
        assertNotEquals(a.sessionId, b.sessionId)
        assertTrue(runCatching { TmuxWindowCommands.select(a, b.windows.single()) }.isFailure)
        val forged = a.copy(windows = a.windows + b.windows.single())
        run(TmuxWindowCommands.select(forged, b.windows.single()), success = false)
        assertEquals(a, snapshot())
        assertEquals(b, snapshot("project-other"))
    }

    @Test fun `last terminal is protected locally and against a stale server snapshot`() {
        val first = snapshot()
        assertTrue(runCatching { TmuxWindowCommands.close(first, first.windows.single()) }.isFailure)
        val second = execute(TmuxWindowCommands.create(first, working.path, "extra"))
        val added = second.windows.first { it.id != first.windows.single().id }
        tmux("kill-window -t ${q(second.sessionId + ":" + added.id)}")
        run(TmuxWindowCommands.close(second, first.windows.single()), success = false)
        assertEquals(first.windows.single().id, snapshot().windows.single().id)
    }

    @Test fun `existing tabs newlines and Unicode names cannot corrupt the framed list`() {
        val first = snapshot()
        val name = "existing\tname\nwith 'quotes' å😀"
        tmux("rename-window -t ${q(first.sessionId + ":" + first.windows.single().id)} ${q(name)}")
        val displayedName = tmux("display-message -p -t ${q(first.sessionId + ":" + first.windows.single().id)} '#{window_name}'").trimEnd('\n')
        assertEquals(displayedName, snapshot().windows.single().name)
        assertTrue(displayedName.contains("å😀"))
        assertTrue(runCatching { TmuxWindowCommands.create(snapshot(), working.path, "new\nname") }.isFailure)
    }

    @Test fun `new controls rediscover IDs and selected terminal without local persisted state`() {
        val second = execute(TmuxWindowCommands.create(snapshot(), working.path, "Second"))
        assertEquals(second, snapshot())
        tmux("new-window -d -t project -n External /bin/sh")
        val changed = snapshot()
        assertEquals(3, changed.windows.size)
        assertEquals(second.windows.single { it.active }.id, changed.windows.single { it.active }.id)
    }

    @Test fun `names are literal rather than tmux formats or shell commands`() {
        val marker = File(folder, "HACKED")
        val name = "#(touch ${marker.path})"
        val created = execute(TmuxWindowCommands.create(snapshot(), working.path, name))
        val added = created.windows.single { it.active }
        assertEquals(name, added.name)
        assertFalse(marker.exists())
        val rename = "#{session_name} #(printf injected) \$HOME"
        val renamed = execute(TmuxWindowCommands.rename(created, added, rename))
        assertEquals(rename, renamed.windows.single { it.id == added.id }.name)
        assertFalse(marker.exists())
    }

    @Test fun `reconnecting project does not kill healthy windows if another pane is dead`() {
        tmux("set-option -g remain-on-exit on")
        val initial = snapshot()
        val extra = execute(TmuxWindowCommands.create(initial, working.path, "healthy"))
        val deadWindow = initial.windows.single()
        tmux("respawn-window -k -t ${q(extra.sessionId + ":" + deadWindow.id)} 'exit 0'")
        tmux("select-window -t ${q(extra.sessionId + ":" + deadWindow.id)}")
        val engine = ScriptTemplateEngine()
        val server = Server(name = "fixture", host = "fixture", username = "fixture", projectsFolder = folder.path)
        val project = Project(serverId = 1, name = "project")
        val output = run(engine.renderSetup(server, project))
        assertTrue(output.contains("TMUX_SESSION_EXISTS"))
        assertEquals(extra.windows.map { it.id }.toSet(), snapshot().windows.map { it.id }.toSet())
    }
}
