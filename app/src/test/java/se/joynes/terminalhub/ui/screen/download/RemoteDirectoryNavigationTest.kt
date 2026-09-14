package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.terminalhub.data.ssh.RemoteFileEntry

class RemoteDirectoryNavigationTest {

    @Test
    fun `navigation enters nested folders and returns one level at a time`() {
        val stems = childRemoteDirectory("", "stems")
        val drums = childRemoteDirectory(stems, "drums")

        assertEquals("stems", stems)
        assertEquals("stems/drums", drums)
        assertEquals("stems", parentRemoteDirectory(drums))
        assertEquals("", parentRemoteDirectory(stems))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `navigation rejects parent traversal entry`() {
        childRemoteDirectory("stems", "..")
    }

    @Test
    fun `breadcrumb distinguishes root and nested directory`() {
        assertEquals("PROJECT /", remoteDirectoryLabel(""))
        assertEquals("PROJECT / stems/drums", remoteDirectoryLabel("stems/drums"))
    }

    @Test
    fun `sort keeps directories first and sorts names in either direction`() {
        val entries = listOf(
            RemoteFileEntry("z.txt", 1),
            RemoteFileEntry("beats", 0, isDirectory = true),
            RemoteFileEntry("a.txt", 2),
            RemoteFileEntry("audio", 0, isDirectory = true)
        )

        val ascending = sortRemoteFileEntries(
            entries,
            RemoteFileSortSelection(RemoteFileSort.NAME, ascending = true)
        )
        val descending = sortRemoteFileEntries(
            entries,
            RemoteFileSortSelection(RemoteFileSort.NAME, ascending = false)
        )

        assertEquals(listOf("audio", "beats", "a.txt", "z.txt"), ascending.map { it.name })
        assertEquals(listOf("beats", "audio", "z.txt", "a.txt"), descending.map { it.name })
    }

    @Test
    fun `sorts files by size and type`() {
        val entries = listOf(
            RemoteFileEntry("notes.txt", 100),
            RemoteFileEntry("cover.jpg", 300),
            RemoteFileEntry("small.jpg", 20)
        )

        val bySize = sortRemoteFileEntries(
            entries,
            RemoteFileSortSelection(RemoteFileSort.SIZE, ascending = false)
        )
        val byType = sortRemoteFileEntries(
            entries,
            RemoteFileSortSelection(RemoteFileSort.TYPE, ascending = true)
        )

        assertEquals(listOf("cover.jpg", "notes.txt", "small.jpg"), bySize.map { it.name })
        assertEquals(listOf("cover.jpg", "small.jpg", "notes.txt"), byType.map { it.name })
    }

    @Test
    fun `new size sort starts with largest file and repeated tap reverses it`() {
        val initial = RemoteFileSortSelection(RemoteFileSort.NAME, ascending = true)
        val sizeDescending = nextRemoteFileSortSelection(initial, RemoteFileSort.SIZE)

        assertEquals(RemoteFileSortSelection(RemoteFileSort.SIZE, ascending = false), sizeDescending)
        assertEquals(
            RemoteFileSortSelection(RemoteFileSort.SIZE, ascending = true),
            nextRemoteFileSortSelection(sizeDescending, RemoteFileSort.SIZE)
        )
    }

    @Test
    fun `sorts files by modification date in either direction`() {
        val entries = listOf(
            RemoteFileEntry("middle.txt", 1, modifiedAtEpochSeconds = 200),
            RemoteFileEntry("old.txt", 1, modifiedAtEpochSeconds = 100),
            RemoteFileEntry("new.txt", 1, modifiedAtEpochSeconds = 300)
        )

        val newestFirst = sortRemoteFileEntries(
            entries,
            RemoteFileSortSelection(RemoteFileSort.DATE, ascending = false)
        )
        val oldestFirst = sortRemoteFileEntries(
            entries,
            RemoteFileSortSelection(RemoteFileSort.DATE, ascending = true)
        )

        assertEquals(listOf("new.txt", "middle.txt", "old.txt"), newestFirst.map { it.name })
        assertEquals(listOf("old.txt", "middle.txt", "new.txt"), oldestFirst.map { it.name })
    }
}
