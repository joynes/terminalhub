package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
