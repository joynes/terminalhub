package se.joynes.terminalhub.data.notes

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import se.joynes.terminalhub.data.db.dao.ProjectNoteDao
import se.joynes.terminalhub.data.db.entity.ProjectNoteEntity
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectNotesRepositoryTest {
    @Test fun `offline edit persists survives repository recreation then reconnect uploads`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val repo = fixture.repository()
        try {
            fixture.offline = true
            repo.load(1); runCurrent()
            repo.edit(1, "offline å😀"); repo.flush()
            assertTrue(fixture.rows.getValue(1).dirty)
            advanceTimeBy(1600); runCurrent()
            assertEquals(NoteSyncStatus.OFFLINE_PENDING, repo.observe(1).value.status)
            repo.resetForImport()
            val recreated = fixture.repository()
            fixture.offline = false
            recreated.load(1); runCurrent()
            assertEquals("offline å😀", fixture.serverNote.text)
            assertFalse(fixture.rows.getValue(1).dirty)
            recreated.resetForImport()
        } finally { repo.resetForImport(); Dispatchers.resetMain() }
    }

    @Test fun `conflict choices do not silently overwrite and offline clear remains pending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        fixture.serverNote = RemoteNote("key", "initial", 1)
        val repo = fixture.repository()
        try {
            repo.load(1); runCurrent()
            fixture.offline = true
            repo.edit(1, "device"); repo.flush()
            fixture.serverNote = RemoteNote("key", "server", 2)
            fixture.offline = false
            repo.retry(1); runCurrent()
            assertEquals(NoteSyncStatus.CONFLICT, repo.observe(1).value.status)
            assertEquals("device", repo.observe(1).value.note?.text)
            assertEquals("server", fixture.serverNote.text)
            repo.resolve(1, false); runCurrent()
            assertEquals("server", fixture.rows.getValue(1).text)
            repo.edit(1, "device again"); repo.flush()
            fixture.serverNote = RemoteNote("key", "server again", 3)
            repo.retry(1); runCurrent()
            repo.resolve(1, true); runCurrent()
            assertEquals("device again", fixture.serverNote.text)
            fixture.offline = true
            repo.edit(1, "", clear = true); repo.flush()
            repo.retry(1); runCurrent()
            assertTrue(fixture.rows.getValue(1).pendingDelete)
            assertEquals("device again", fixture.serverNote.text)
            fixture.offline = false
            repo.retry(1); runCurrent()
            assertNull(fixture.serverNote.text)
            assertFalse(fixture.rows.getValue(1).pendingDelete)
        } finally { repo.resetForImport(); Dispatchers.resetMain() }
    }

    @Test fun `editing while download is suspended retains both versions as conflict`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture()
        val gate = CompletableDeferred<Unit>()
        fixture.readGate = gate
        fixture.serverNote = RemoteNote("key", "server", 1)
        val repo = fixture.repository()
        try {
            repo.load(1); runCurrent()
            repo.edit(1, "device during download"); repo.flush()
            gate.complete(Unit); runCurrent()
            assertEquals("device during download", fixture.rows.getValue(1).text)
            assertEquals(NoteSyncStatus.CONFLICT, repo.observe(1).value.status)
            assertEquals("server", fixture.serverNote.text)
        } finally { repo.resetForImport(); Dispatchers.resetMain() }
    }

    private class Fixture {
        val rows = mutableMapOf<Long, ProjectNoteEntity>()
        var serverNote = RemoteNote("key", null, null)
        var offline = false
        var readGate: CompletableDeferred<Unit>? = null
        val dao: ProjectNoteDao = mock()
        val projects: ProjectRepository = mock()
        val servers: ServerRepository = mock()
        val remote: RemoteProjectNotesStore = mock()
        suspend fun repository(): ProjectNotesRepository {
            whenever(dao.get(any())).thenAnswer { rows[it.getArgument(0)] }
            doAnswer { rows[it.getArgument<ProjectNoteEntity>(0).projectId] = it.getArgument(0); null }.whenever(dao).save(any())
            whenever(projects.getById(any())).thenReturn(Project(id = 1, serverId = 1, name = "demo"))
            whenever(servers.getById(any())).thenReturn(Server(id = 1, name = "server", host = "host", username = "user"))
            whenever(remote.read(any(), any())).thenAnswer {
                if (offline) throw IOException("offline")
                serverNote
            }
            if (readGate != null) whenever(remote.read(any(), any())).doSuspendableAnswer { readGate!!.await(); serverNote }
            whenever(remote.write(any(), any(), any(), any(), any())).thenAnswer {
                if (offline) throw IOException("offline")
                serverNote = RemoteNote("key", if (it.getArgument<Boolean>(4)) null else it.getArgument(2), 3)
                serverNote
            }
            return ProjectNotesRepository(dao, projects, servers, remote)
        }
    }
}
