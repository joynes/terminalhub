package se.joynes.terminalhub.data.ssh

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderedByteWriterTest {

    @Test
    fun `bracketed paste and enter are written in enqueue order`() = runTest {
        val written = mutableListOf<String>()
        val writer = OrderedByteWriter(backgroundScope) { written += it.decodeToString() }

        assertTrue(writer.enqueue("\u001B[200~".encodeToByteArray()))
        assertTrue(writer.enqueue("git status".encodeToByteArray()))
        assertTrue(writer.enqueue("\u001B[201~".encodeToByteArray()))
        val pasteFlushed = async { writer.awaitDrained() }
        runCurrent()

        assertTrue(pasteFlushed.isCompleted)
        assertEquals(listOf("\u001B[200~", "git status", "\u001B[201~"), written)

        assertTrue(writer.enqueue("\r".encodeToByteArray()))
        runCurrent()
        assertEquals(listOf("\u001B[200~", "git status", "\u001B[201~", "\r"), written)
    }

    @Test
    fun `queued bytes cannot be changed by the caller`() = runTest {
        val written = mutableListOf<String>()
        val writer = OrderedByteWriter(backgroundScope) { written += it.decodeToString() }
        val bytes = "safe".encodeToByteArray()

        assertTrue(writer.enqueue(bytes))
        bytes[0] = 'X'.code.toByte()
        val drained = async { writer.awaitDrained() }
        assertFalse(drained.isCompleted)
        runCurrent()

        assertTrue(drained.isCompleted)
        assertEquals(listOf("safe"), written)
    }
}
