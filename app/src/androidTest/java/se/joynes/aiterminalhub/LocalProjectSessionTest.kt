package se.joynes.aiterminalhub

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.aiterminalhub.data.db.AppDatabase
import se.joynes.aiterminalhub.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.ProjectTargetType
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.domain.TerminalSessionManager

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocalProjectSessionTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject lateinit var projectRepo: ProjectRepository
    @Inject lateinit var sessionManager: TerminalSessionManager

    private lateinit var appContext: Context

    @Before
    fun setup() {
        hiltRule.inject()
        appContext = ApplicationProvider.getApplicationContext()
        sessionManager.sessions.value.forEach { sessionManager.close(it.id) }
        appContext.getSharedPreferences("session_manager", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db.clearAllTables()
        File(appContext.filesDir, "projects").deleteRecursively()
    }

    @Test
    fun localProjectStartsLocalTerminalSessionInMainActivity() {
        val projectId = kotlinx.coroutines.runBlocking {
            projectRepo.save(
                Project(
                    serverId = LOCAL_PROJECT_SERVER_ID,
                    targetType = ProjectTargetType.LOCAL,
                    name = "local-e2e",
                    useTmux = false,
                    customScript = "cd {{PROJECT_PATH}}",
                    aiCommand = ""
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val sessionStarted = waitUntil(10_000) {
                sessionManager.sessions.value.any { it.projectId == projectId && it.isConnected }
            }
            assertTrue("Expected a local session to be registered", sessionStarted)
        }

        assertTrue(
            "Expected local project directory to exist",
            File(appContext.filesDir, "projects/local-e2e").isDirectory
        )
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
