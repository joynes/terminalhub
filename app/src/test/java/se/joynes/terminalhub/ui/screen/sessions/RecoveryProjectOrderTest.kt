package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.terminalhub.data.model.Project

class RecoveryProjectOrderTest {
    @Test
    fun preferredProjectIsFirstWithoutDroppingOrSerializingOtherProjects() {
        val projects = listOf(
            Project(id = 1L, serverId = 10L, name = "one"),
            Project(id = 2L, serverId = 10L, name = "two"),
            Project(id = 3L, serverId = 10L, name = "three")
        )

        val ordered = recoveryProjectsInPriorityOrder(projects, preferredProjectId = 2L)

        assertEquals(listOf(2L, 1L, 3L), ordered.map { it.id })
    }

    @Test
    fun firstProjectIsUsedWhenPreviousActiveProjectIsUnavailable() {
        val projects = listOf(
            Project(id = 4L, serverId = 10L, name = "four"),
            Project(id = 5L, serverId = 10L, name = "five")
        )

        val ordered = recoveryProjectsInPriorityOrder(projects, preferredProjectId = 99L)

        assertEquals(listOf(4L, 5L), ordered.map { it.id })
    }
}
