package se.joynes.terminalhub.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TerminalMacroTest {
    private class Target : MacroTerminalTarget {
        var connected = true
        val events = mutableListOf<String>()
        var barrier: CompletableDeferred<Unit>? = null
        override fun isConnected() = connected
        override fun paste(text: String) { events += "paste:$text" }
        override fun press(key: MacroStep.Key) { events += "key:" + TerminalMacro.encodeKey(key) }
        override suspend fun flush() { events += "flush"; barrier?.await() }
    }

    @Test fun `Ctrl C sends ETX and control slash sends US rather than printable hash`() {
        assertEquals("\u0003", TerminalMacro.encodeKey(MacroStep.Key("C", ctrl = true)))
        assertEquals("\u001f", TerminalMacro.encodeKey(MacroStep.Key("/", ctrl = true)))
        assertEquals("\u001b\u0003", TerminalMacro.encodeKey(MacroStep.Key("c", ctrl = true, alt = true)))
        assertEquals("c", TerminalMacro.encodeKey(MacroStep.Key("C")))
        assertEquals("C", TerminalMacro.encodeKey(MacroStep.Key("c", shift = true)))
        assertEquals("\r", TerminalMacro.encodeKey(MacroStep.Key("ENTER")))
        assertEquals("\u001b[Z", TerminalMacro.encodeKey(MacroStep.Key("TAB", shift = true)))
        assertEquals("\u001bOP", TerminalMacro.encodeKey(MacroStep.Key("F1")))
        assertEquals("\u001b[1;5A", TerminalMacro.encodeKey(MacroStep.Key("UP", ctrl = true)))
        assertEquals("\u001bOA", TerminalMacro.encodeKey(MacroStep.Key("UP"), cursorApplication = true))
    }

    @Test fun `interrupt text Enter execute in exact order with independent writes and settling time`() = runTest {
        val target = Target()
        val progress = mutableListOf<Int>()
        val job = launch { TerminalMacroRunner().run(TerminalMacro.interruptAndSend("git status"), target) { step, _ -> progress += step } }
        runCurrent()
        assertEquals(listOf("key:\u0003", "flush"), target.events)
        advanceTimeBy(380); runCurrent()
        assertEquals(listOf("key:\u0003", "flush", "paste:git status", "flush"), target.events)
        advanceTimeBy(349); runCurrent()
        assertFalse(target.events.contains("key:\r"))
        advanceTimeBy(1); runCurrent(); job.join()
        assertEquals(listOf("key:\u0003", "flush", "paste:git status", "flush", "key:\r", "flush"), target.events)
        assertEquals(listOf(1, 2, 3, 4), progress)
    }

    @Test fun `Enter cannot overtake a pending pasted write`() = runTest {
        val target = Target().apply { barrier = CompletableDeferred() }
        val job = launch { TerminalMacroRunner().run(listOf(MacroStep.Text("hello"), MacroStep.Key("ENTER")), target) }
        runCurrent(); advanceTimeBy(3000); runCurrent()
        assertEquals(listOf("paste:hello", "flush"), target.events)
        target.barrier!!.complete(Unit)
        runCurrent(); advanceTimeBy(324); runCurrent()
        assertFalse(target.events.contains("key:\r"))
        advanceTimeBy(1); runCurrent(); job.join()
        assertEquals("key:\r", target.events[target.events.lastIndex - 1])
    }

    @Test fun `explicit pause counts toward paste settling and key repetition stays ordered`() = runTest {
        val target = Target()
        TerminalMacroRunner().run(listOf(MacroStep.Text("x"), MacroStep.Pause(1000), MacroStep.Key("UP", repeat = 3), MacroStep.Key("ENTER")), target)
        assertEquals(1240, testScheduler.currentTime)
        assertEquals(listOf("paste:x", "flush", "key:\u001b[A", "flush", "key:\u001b[A", "flush", "key:\u001b[A", "flush", "key:\r", "flush"), target.events)
    }

    @Test fun `disconnect during pause stops before any later input`() = runTest {
        val target = Target()
        val result = async { runCatching { TerminalMacroRunner().run(listOf(MacroStep.Key("C", ctrl = true), MacroStep.Pause(2000), MacroStep.Text("never sent")), target) } }
        runCurrent(); target.connected = false
        advanceUntilIdle()
        assertTrue(result.await().exceptionOrNull() is IllegalStateException)
        assertFalse(target.events.any { it.startsWith("paste:") })
    }

    @Test fun `cancel while waiting for flush does not send Enter`() = runTest {
        val target = Target().apply { barrier = CompletableDeferred() }
        val job = launch { TerminalMacroRunner().run(listOf(MacroStep.Text("partial"), MacroStep.Key("ENTER")), target) }
        runCurrent(); job.cancelAndJoin(); target.barrier!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("paste:partial", "flush"), target.events)
    }

    @Test fun `limits reject invalid macros before sending anything`() = runTest {
        val invalid = listOf(emptyList(), listOf(MacroStep.Pause(-1)), listOf(MacroStep.Pause(60_001)),
            listOf(MacroStep.Text("")), listOf(MacroStep.Text("x".repeat(65_537))), listOf(MacroStep.Key("UNKNOWN")),
            listOf(MacroStep.Key("C", repeat = 0)), List(101) { MacroStep.Key("ENTER") }, List(11) { MacroStep.Key("UP", repeat = 100) })
        invalid.forEach { steps ->
            val target = Target()
            assertTrue(runCatching { TerminalMacroRunner().run(steps, target) }.isFailure)
            assertTrue(target.events.isEmpty())
        }
    }

    @Test fun `controller prevents overlapping macros and stop followed by new run keeps new status`() = runTest {
        val controller = PinnedMacroController(this)
        val target = Target()
        assertTrue(controller.start("first", "project", listOf(MacroStep.Pause(2000)), target))
        assertFalse(controller.start("duplicate", "other", listOf(MacroStep.Key("ENTER")), target))
        runCurrent()
        controller.stop()
        assertTrue(controller.start("second", "project", listOf(MacroStep.Pause(1000), MacroStep.Key("TAB")), target))
        runCurrent()
        assertEquals("second", controller.state.value?.name)
        assertEquals(true, controller.state.value?.running)
        advanceUntilIdle()
        assertEquals("second", controller.state.value?.name)
        assertEquals("Macro completed", controller.state.value?.message)
        assertEquals(listOf("key:\t", "flush"), target.events)
        controller.dismiss(); assertNull(controller.state.value)
    }

    @Test fun `stalled flush times out with visible failure instead of staying running`() = runTest {
        val controller = PinnedMacroController(this)
        val target = Target().apply { barrier = CompletableDeferred() }
        controller.start("stalled", "project", listOf(MacroStep.Text("text"), MacroStep.Key("ENTER")), target)
        advanceUntilIdle()
        assertEquals(false, controller.state.value?.running)
        assertTrue(controller.state.value!!.message!!.contains("timed out"))
        assertFalse(target.events.contains("key:\r"))
    }
}
