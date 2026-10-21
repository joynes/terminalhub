package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.ui.screen.download.DownloadState

class WorkspaceToolsTest {
    @Test fun commandSearchMatchesAllWordsInTitleOrAliasesWithoutRunningAnything() {
        var calls = 0
        val commands = listOf(WorkspaceCommand("one", "Reconnect all", "anslut SSH") { calls++ },
            WorkspaceCommand("two", "Settings", "inställningar") { calls++ })
        assertEquals(listOf("one"), filterWorkspaceCommands(commands, " SSH anslut ").map { it.id })
        assertEquals(2, filterWorkspaceCommands(commands, "").size)
        assertTrue(filterWorkspaceCommands(commands, "missing").isEmpty())
        assertEquals(0, calls)
    }
    @Test fun connectionProgressHasPriorityOverStaleError() {
        val tab = ProjectTabState(1, "dev", null, false, connectionError = "old error")
        assertEquals("Connection failed", connectionActivityLabel(tab))
        assertEquals("Connecting…", connectionActivityLabel(tab.copy(isConnecting = true)))
        assertEquals("Connected", connectionActivityLabel(tab.copy(isConnected = true)))
    }
    @Test fun activityDoesNotListEveryIdleBrowserAsAFileTransfer() {
        assertNull(transferActivityLabel(DownloadState.Idle))
        assertTrue(transferActivityLabel(DownloadState.Paused("a.zip", "network lost", 0))!!.contains("network lost"))
    }
}
