package se.joynes.terminalhub.data.ssh

import org.junit.Assert.*
import org.junit.Test

class TmuxWindowLayoutTest {
    @Test fun startCommandFollowsStableKeyThroughRenameReorderAndRestoreWithoutExecuting() {
        val command = "python report.py --title 'År 2026' | tee report.log"
        val original = TmuxWindowLayout(listOf("Code", "Logs"), 0, listOf(
            TmuxWindowMemory("code-key", startCommand = command), TmuxWindowMemory("logs-key", startCommand = "tail -f app.log")))
        val restored = TmuxWindowsSnapshot("\$77", listOf(
            TmuxWindow("@99", 0, "Logs renamed", true, 1, "logs-key", "zsh"),
            TmuxWindow("@98", 1, "Code renamed", false, 1, "code-key", "zsh")))
        val next = TmuxWindowLayout.from(restored, TmuxWindowLayout.decode(original.encode()))
        assertEquals(listOf("tail -f app.log", command), next.memories.map { it.startCommand })
        assertFalse(next.renderCreate("demo", "/work").contains(command))
        val replacement = restored.copy(windows = listOf(restored.windows.first().copy(key = "new-key")))
        assertEquals("", TmuxWindowLayout.from(replacement, next).memories.single().startCommand)
    }

    @Test fun versionTwoMigratesAndControlCharactersAreRejected() {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use {
            it.writeInt(2); it.writeInt(0); it.writeInt(1); it.writeUTF("Code")
            it.writeInt(1); it.writeUTF("stable-key"); it.writeBoolean(true); it.writeUTF("codex"); it.writeUTF("/work")
        }
        val old = TmuxWindowLayout.decode(bytes.toByteArray().joinToString("") { "%02x".format(it) })!!
        assertEquals("", old.memories.single().startCommand)
        assertEquals(TmuxProgram("codex", "/work"), old.memories.single().program)
        listOf("echo ok\nexit", "bad\u0000", "x".repeat(8193)).forEach { command ->
            assertTrue(runCatching { TmuxWindowLayout.validateStartCommand(command) }.isFailure)
        }
    }
    private fun snapshot(program: String = "codex", key: String = "stable-key", name: String = "Code") =
        TmuxWindowsSnapshot("\$1", listOf(TmuxWindow("@1", 0, name, true, 1, key, program, "/work/project")))

    @Test fun interactiveProgramIsRememberedAfterReturningToShellAndRename() {
        val observed = TmuxWindowLayout.from(snapshot())
        val shell = TmuxWindowLayout.from(snapshot("zsh", name = "Renamed"), observed)
        assertEquals(TmuxProgram("codex", "/work/project"), shell.memories.single().program)
        assertEquals(shell, TmuxWindowLayout.decode(shell.encode()))
        val next = TmuxWindowLayout.from(snapshot("vim"), shell)
        assertEquals("vim", next.memories.single().program!!.command)
    }

    @Test fun recycledTmuxIdDoesNotInheritAnotherWindowsProgram() {
        val old = TmuxWindowLayout.from(snapshot())
        val replacement = TmuxWindowLayout.from(snapshot("bash", key = "new-key"), old)
        assertNull(replacement.memories.single().program)
        assertTrue(TmuxWindowLayout.from(snapshot().copy(windows = emptyList()), old).memories.isEmpty())
    }

    @Test fun splitPanesDoNotOverwriteSinglePaneProgramAndNoInputHistoryIsNeeded() {
        val old = TmuxWindowLayout.from(snapshot())
        val split = snapshot("vim").let { it.copy(windows = listOf(it.windows.single().copy(panes = 2))) }
        assertEquals(old.memories, TmuxWindowLayout.from(split, old).memories)
        assertNull(TmuxWindowLayout.from(split).memories.single().program)
    }

    @Test fun versionOneLayoutsMigrateWithoutLosingWindows() {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { it.writeInt(1); it.writeInt(1); it.writeInt(2); it.writeUTF("Code"); it.writeUTF("Logs") }
        assertEquals(TmuxWindowLayout(listOf("Code", "Logs"), 1), TmuxWindowLayout.decode(bytes.toByteArray().joinToString("") { "%02x".format(it) }))
    }

    @Test fun namesOrderAndSelectionSurviveEncodingWithoutCommandsOrIds() {
        val layout = TmuxWindowLayout(listOf("Code å😀", "Logs ' \" #()", "Tests"), 2)
        assertEquals(layout, TmuxWindowLayout.decode(layout.encode()))
        assertNull(TmuxWindowLayout.decode("corrupt"))
        assertNull(TmuxWindowLayout.decode(null))
        assertTrue(runCatching { TmuxWindowLayout(emptyList(), 0).encode() }.isFailure)
        assertTrue(runCatching { TmuxWindowLayout(listOf("a"), 2).encode() }.isFailure)
    }
}
