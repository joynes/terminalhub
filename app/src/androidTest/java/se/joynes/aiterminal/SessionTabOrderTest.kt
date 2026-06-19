package se.joynes.aiterminal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.aiterminal.data.db.AppDatabase
import se.joynes.aiterminal.data.db.dao.TextInputHistoryDao
import se.joynes.aiterminal.data.logging.AppLogger
import se.joynes.aiterminal.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.aiterminal.data.model.Project
import se.joynes.aiterminal.data.model.ProjectTargetType
import se.joynes.aiterminal.data.repository.ProjectRepository
import se.joynes.aiterminal.data.repository.ServerRepository
import se.joynes.aiterminal.data.ssh.SshManager
import se.joynes.aiterminal.domain.ScriptTemplateEngine
import se.joynes.aiterminal.domain.TerminalSessionManager
import se.joynes.aiterminal.domain.usecase.ConnectToServer
import se.joynes.aiterminal.ui.screen.sessions.SessionHostViewModel

@HiltAndroidTest
class SessionTabOrderTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var projectRepo: ProjectRepository
    @Inject lateinit var serverRepo: ServerRepository
    @Inject lateinit var connectToServer: ConnectToServer
    @Inject lateinit var sshManager: SshManager
    @Inject lateinit var engine: ScriptTemplateEngine
    @Inject lateinit var sessionManager: TerminalSessionManager
    @Inject lateinit var textInputHistoryDao: TextInputHistoryDao

    private lateinit var viewModel: SessionHostViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        sessionManager.sessions.value.forEach { sessionManager.close(it.id) }
        context.getSharedPreferences("session_host", Context.MODE_PRIVATE).edit().clear().commit()
        db.clearAllTables()
        viewModel = SessionHostViewModel(
            context = context,
            logger = logger,
            serverRepo = serverRepo,
            projectRepo = projectRepo,
            connectToServer = connectToServer,
            sshManager = sshManager,
            engine = engine,
            sessionManager = sessionManager,
            textInputHistoryDao = textInputHistoryDao
        )
    }

    @Test
    fun moveSessionReordersVisibleProjectTabs() = runBlocking {
        projectRepo.save(localProject("alpha"))
        projectRepo.save(localProject("beta"))
        projectRepo.save(localProject("gamma"))

        viewModel.init()
        waitUntil(10_000) { viewModel.projectTabs.value.size == 3 }
        assertEquals(listOf("alpha", "beta", "gamma"), viewModel.projectTabs.value.map { it.projectName })

        viewModel.moveSession(fromIndex = 2, toIndex = 0)

        waitUntil(5_000) {
            viewModel.projectTabs.value.map { it.projectName } == listOf("gamma", "alpha", "beta")
        }
        assertEquals(listOf("gamma", "alpha", "beta"), viewModel.projectTabs.value.map { it.projectName })
    }

    private fun localProject(name: String) = Project(
        serverId = LOCAL_PROJECT_SERVER_ID,
        targetType = ProjectTargetType.LOCAL,
        name = name,
        useTmux = false
    )

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
