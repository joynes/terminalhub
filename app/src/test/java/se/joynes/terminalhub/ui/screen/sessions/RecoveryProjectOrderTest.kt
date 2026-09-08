package se.joynes.terminalhub.ui.screen.sessions

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.ProjectTargetType

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

    @Test
    fun allPersistedOpenTabsActivateOnInitialLoadWithoutRecoveryFlag() {
        val projects = listOf(
            Project(id = 1L, serverId = 10L, name = "one"),
            Project(id = 2L, serverId = 10L, name = "two"),
            Project(id = 3L, serverId = 10L, name = "three")
        )

        val toActivate = projectsToAutoActivateOnLoad(
            visibleProjects = projects,
            liveProjectIds = emptySet(),
            preferredProjectId = 2L,
            initialLoad = true,
            recoveryPending = false
        )

        assertEquals(listOf(2L, 1L, 3L), toActivate.map { it.id })
    }

    @Test
    fun initialActivationSkipsTabsThatAlreadyHaveLiveSessions() {
        val projects = listOf(
            Project(id = 1L, serverId = 10L, name = "one"),
            Project(id = 2L, serverId = 10L, name = "two"),
            Project(
                id = 3L,
                serverId = -1L,
                name = "local",
                targetType = ProjectTargetType.LOCAL
            )
        )

        val toActivate = projectsToAutoActivateOnLoad(
            visibleProjects = projects,
            liveProjectIds = setOf(1L, 3L),
            preferredProjectId = 1L,
            initialLoad = true,
            recoveryPending = false
        )

        assertEquals(listOf(2L), toActivate.map { it.id })
    }

    @Test
    fun ordinaryDatabaseRefreshDoesNotReconnectMissingTabsInALoop() {
        val projects = listOf(Project(id = 1L, serverId = 10L, name = "one"))

        val toActivate = projectsToAutoActivateOnLoad(
            visibleProjects = projects,
            liveProjectIds = emptySet(),
            preferredProjectId = null,
            initialLoad = false,
            recoveryPending = false
        )

        assertEquals(emptyList<Project>(), toActivate)
    }
}
