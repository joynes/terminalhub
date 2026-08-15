package se.joynes.terminalhub

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.ssh.SshManager
import se.joynes.terminalhub.domain.ScriptTemplateEngine
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.domain.usecase.ConnectToServer
import se.joynes.terminalhub.ui.screen.sessions.SessionHostScreen
import se.joynes.terminalhub.ui.screen.sessions.SessionHostViewModel
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SessionHostOnboardingTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

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
    @Inject lateinit var settingsRepository: AppSettingsRepository
    @Inject lateinit var runtimeRepository: AppRuntimeRepository

    @Before
    fun setup() {
        hiltRule.inject()
        sessionManager.sessions.value.forEach { sessionManager.close(it.id) }
        context.getSharedPreferences("session_host", Context.MODE_PRIVATE).edit().clear().commit()
        db.clearAllTables()
    }

    @Test
    fun addProjectButtonIsEnabledWhenServerExistsButNoProjectExists() {
        runBlocking {
            serverRepo.save(
                Server(
                    name = "macmini",
                    host = "100.116.112.95",
                    username = "joka",
                    authType = "key"
                )
            )

            val viewModel = SessionHostViewModel(
                context = context,
                logger = logger,
                serverRepo = serverRepo,
                projectRepo = projectRepo,
                connectToServer = connectToServer,
                sshManager = sshManager,
                engine = engine,
                sessionManager = sessionManager,
                textInputHistoryDao = textInputHistoryDao,
                settingsRepository = settingsRepository,
                runtimeRepository = runtimeRepository
            )

            composeRule.setContent {
                TerminalHubTheme {
                    SessionHostScreen(
                        onOpenServers = {},
                        onAddServer = {},
                        onAddProject = { _ -> },
                        onOpenLogs = {},
                        onOpenSettings = {},
                        viewModel = viewModel
                    )
                }
            }

            composeRule.onNodeWithText("SERVER READY").assertIsDisplayed()
            composeRule.onNodeWithText("[ + ADD PROJECT ]").assertIsEnabled()
        }
    }
}
