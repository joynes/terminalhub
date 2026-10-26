package se.joynes.terminalhub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.ssh.*

class TmuxWindowLayoutPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = Server(name = "one", host = "fixture", username = "user", projectsFolder = "~/projects")
    private val project = Project(id = 3, serverId = 1, name = "project")
    @Before @After fun cleanup() { context.getSharedPreferences("tmux_window_layouts", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun windowsSurviveRepositoryRecreationAndClosedWindowsStayClosed() = runBlocking {
        val snapshot = TmuxWindowsSnapshot("\$17", listOf(TmuxWindow("@4", 0, "Code", false, 1), TmuxWindow("@7", 1, "Logs å", true, 1)))
        val store = TmuxWindowLayoutStore(context)
        store.save(server, project, snapshot)
        assertEquals(TmuxWindowLayout(listOf("Code", "Logs å"), 1), TmuxWindowLayoutStore(context).read(server, project))
        store.save(server, project, snapshot.copy(windows = listOf(snapshot.windows.first().copy(active = true))))
        assertEquals(TmuxWindowLayout(listOf("Code"), 0), TmuxWindowLayoutStore(context).read(server, project))
        assertNull(store.read(server.copy(host = "other-server"), project))
        assertNull(store.read(server, project.copy(id = 4)))
        assertNull(store.read(server, project.copy(name = "renamed")))
    }

    @Test fun lateInitialCaptureCannotOverwriteNewerWindowChanges() = runBlocking {
        val store = TmuxWindowLayoutStore(context)
        val old = TmuxWindowsSnapshot("\$1", listOf(TmuxWindow("@1", 0, "Old", true, 1)))
        store.save(server, project, old, onlyIfMissing = true)
        assertEquals(listOf("Old"), store.read(server, project)!!.names)
        val updated = old.copy(windows = listOf(old.windows.single().copy(name = "Renamed"), TmuxWindow("@2", 1, "New", false, 1)))
        store.save(server, project, updated)
        store.save(server, project, old, onlyIfMissing = true)
        assertEquals(listOf("Renamed", "New"), TmuxWindowLayoutStore(context).read(server, project)!!.names)
    }

    @Test fun lastActualProgramSurvivesAppRecreationAndTmuxIdReuseIsIsolated() = runBlocking {
        val window = TmuxWindow("@1", 0, "Code", true, 1, "original-key", "codex", "/work/src")
        val snapshot = TmuxWindowsSnapshot("\$1", listOf(window))
        TmuxWindowLayoutStore(context).save(server, project, snapshot)
        val store = TmuxWindowLayoutStore(context)
        store.save(server, project, snapshot.copy(windows = listOf(window.copy(currentProgram = "zsh", name = "Renamed"))))
        assertEquals(TmuxProgram("codex", "/work/src"), TmuxWindowLayoutStore(context).read(server, project)!!.memories.single().program)
        store.save(server, project, snapshot.copy(windows = listOf(window.copy(key = "unrelated-key", currentProgram = "bash"))))
        assertNull(TmuxWindowLayoutStore(context).read(server, project)!!.memories.single().program)
    }
}
