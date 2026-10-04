package se.joynes.terminalhub.data.notes

import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.db.entity.ProjectNoteEntity

class ProjectNotesTest {
    private val base = RemoteNote("key", "original", 10)
    private val local = ProjectNoteEntity(1, "original", lastSyncedContentHash = base.hash, lastSyncedRemoteMtime = 10)

    @Test fun `sync decisions never use wall clock to resolve edits`() {
        assertEquals(NoteSyncDecision.EMPTY, noteSyncDecision(null, RemoteNote("key", null, null)))
        assertEquals(NoteSyncDecision.PULL, noteSyncDecision(null, base))
        assertEquals(NoteSyncDecision.UNCHANGED, noteSyncDecision(local, base))
        assertEquals(NoteSyncDecision.PULL, noteSyncDecision(local, base.copy(text = "server")))
        assertEquals(NoteSyncDecision.PUSH, noteSyncDecision(local.copy(text = "device", dirty = true, localUpdatedAt = Long.MAX_VALUE), base))
        assertEquals(NoteSyncDecision.CONFLICT, noteSyncDecision(local.copy(text = "device", dirty = true, localUpdatedAt = Long.MAX_VALUE), base.copy(text = "server")))
        assertEquals(NoteSyncDecision.PUSH, noteSyncDecision(ProjectNoteEntity(99, "offline", dirty = true), RemoteNote("key", null, null)))
        assertEquals(NoteSyncDecision.PUSH, noteSyncDecision(ProjectNoteEntity(99, "never synced"), RemoteNote("key", null, null)))
        assertEquals(NoteSyncDecision.PUSH, noteSyncDecision(local.copy(text = "", pendingDelete = true, dirty = true), base))
        assertEquals(NoteSyncDecision.CONFLICT, noteSyncDecision(local.copy(text = "", pendingDelete = true, dirty = true), base.copy(text = "server")))
        assertEquals(NoteSyncDecision.PULL, noteSyncDecision(local.copy(dirty = true), base))
    }

    @Test fun `key ignores trailing slash and local database identity`() {
        assertEquals(stableNoteKey("/home/alice/projects/demo/"), stableNoteKey("/home/alice/projects/demo"))
        assertEquals(stableNoteKey("/"), stableNoteKey("////"))
        assertTrue(stableNoteKey("/safe/../x").matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(stableNoteKey("/a"), stableNoteKey("/b"))
        assertNotEquals(noteHash("å😀\nmarkdown"), noteHash("å😀markdown"))
        assertEquals("/home/projects/demo", normalizeRemoteProjectPath("/home//projects/./tmp/../demo/"))
        assertEquals("/", normalizeRemoteProjectPath("/../../"))
    }
}
