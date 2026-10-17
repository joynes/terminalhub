package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.terminalhub.data.settings.RemoteBrowseViewState
import se.joynes.terminalhub.data.ssh.RemoteFileEntry

class RemoteBrowserScrollRestoreTest {
    private val entries = (0..49).map { RemoteFileEntry("file-$it.txt", 10) }

    @Test fun exactItemAndPixelOffsetAreRestored() {
        val saved = RemoteBrowseViewState(25, 17, remoteEntryKey(entries[25]))
        assertEquals(25 to 17, restoredRemoteScroll(saved, entries))
    }

    @Test fun insertedFilesAndChangedSortKeepTheSameFileAtTheTop() {
        val saved = RemoteBrowseViewState(25, 17, remoteEntryKey(entries[25]))
        assertEquals(26 to 17, restoredRemoteScroll(saved, listOf(RemoteFileEntry("new.txt", 1)) + entries))
        assertEquals(24 to 17, restoredRemoteScroll(saved, entries.reversed()))
    }

    @Test fun removedAnchorShortenedAndEmptyListsFallBackSafely() {
        val saved = RemoteBrowseViewState(25, 17, "false:removed.txt")
        assertEquals(2 to 0, restoredRemoteScroll(saved, entries.take(3)))
        assertEquals(0 to 0, restoredRemoteScroll(saved, emptyList()))
    }

    @Test fun fileAndDirectoryAnchorsAreDistinctAndOffsetsCannotBeNegative() {
        val sameNames = listOf(RemoteFileEntry("name", 0, true), RemoteFileEntry("name", 1))
        assertEquals(1 to 0, restoredRemoteScroll(RemoteBrowseViewState(-1, -2, "false:name"), sameNames))
    }
}
