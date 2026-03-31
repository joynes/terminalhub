package se.joynes.aiterminalhub

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.data.db.AppDatabase
import se.joynes.aiterminalhub.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.aiterminalhub.data.model.ProjectTargetType
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.ui.screen.projects.AddEditProjectViewModel

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddEditProjectScreenTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projectRepo: ProjectRepository
    @Inject lateinit var serverRepo: ServerRepository

    @Before
    fun setup() {
        hiltRule.inject()
        db.clearAllTables()
    }

    @Test
    fun localTargetCanBeSavedAsRealProjectTargetType() = runBlocking {
        val viewModel = AddEditProjectViewModel(projectRepo, serverRepo)
        viewModel.update {
            copy(
                targetType = ProjectTargetType.LOCAL,
                selectedServerId = null,
                name = "local-demo",
                useTmux = false
            )
        }

        viewModel.save()

        assertTrue(waitUntil(5_000) { viewModel.state.value.saved })

        val project = projectRepo.getAll().first().single()
        assertEquals(ProjectTargetType.LOCAL, project.targetType)
        assertEquals(LOCAL_PROJECT_SERVER_ID, project.serverId)
        assertEquals(false, project.useTmux)
        assertEquals("local-demo", project.name)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }
}
