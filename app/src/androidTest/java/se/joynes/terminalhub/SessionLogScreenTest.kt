package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import se.joynes.terminalhub.data.db.AppDatabase
import javax.inject.Inject
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import se.joynes.terminalhub.data.db.entity.SessionLogEntity
import se.joynes.terminalhub.ui.screen.sessionlog.SessionLogScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SessionLogScreenTest {
    
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var repository: se.joynes.terminalhub.data.repository.SessionLogRepository
    @Before fun setup() { hiltRule.inject(); db.clearAllTables() }


    @Test
    fun emptyStateShown() {
        composeRule.setContent {
            TerminalHubTheme { SessionLogScreen(onBack = {}) }
        }
        composeRule.onNodeWithText("NO SESSION LOGS").assertIsDisplayed()
    }

    @Test
    fun exportButtonVisible() {
        composeRule.setContent {
            TerminalHubTheme { SessionLogScreen(onBack = {}) }
        }
        composeRule.onNodeWithText("EXP").assertIsDisplayed()
    }
    @Test fun persistedSessionLifecycleShowsStartAndEndAndFiltersByServer() {
        val first = SessionLogEntity(sessionId = "first-session-id", serverId = 42, projectId = 7, startedAt = 100, outputPath = "/fixture/output")
        val id = runBlocking { db.sessionLogDao().insert(first) }
        runBlocking { db.sessionLogDao().insert(SessionLogEntity(sessionId = "second-session-id", serverId = 43, startedAt = 200)) }
        try {
            composeRule.setContent { TerminalHubTheme { SessionLogScreen({}) } }
            composeRule.waitUntil(5000) { composeRule.onAllNodesWithText("Started: 100").fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText("Session: first-se...").assertExists()
            composeRule.onNodeWithText("Ended: 300").assertDoesNotExist()
            runBlocking { db.sessionLogDao().update(first.copy(id = id, endedAt = 300)) }
            composeRule.waitUntil(5000) { composeRule.onAllNodesWithText("Ended: 300").fetchSemanticsNodes().isNotEmpty() }
            runBlocking {
                val saved = db.sessionLogDao().getById(id)!!
                assertEquals(7L, saved.projectId); assertEquals("/fixture/output", saved.outputPath)
                assertEquals(listOf(saved), db.sessionLogDao().getByServer(42).first())
                assertEquals(2, db.sessionLogDao().getAll().first().size)
            }
        } finally { db.clearAllTables() }
    }
    @Test fun exportButtonWritesTheChosenDocumentWithCompleteSessionLifecycle() {
        val context = composeRule.activity
        val file = java.io.File.createTempFile("session-export", ".txt", context.cacheDir)
        var launches = 0
        val registry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {
                launches++; assertEquals("terminalhub-session-log.txt", input)
                dispatchResult(code, android.app.Activity.RESULT_OK, android.content.Intent().setData(android.net.Uri.fromFile(file)))
            }
        }
        runBlocking { db.sessionLogDao().insert(SessionLogEntity(sessionId = "complete-session-id", serverId = 42, projectId = 7, startedAt = 100, endedAt = 300, outputPath = "/fixture/output")) }
        try {
            composeRule.setContent {
                androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = registry
                }) { TerminalHubTheme { SessionLogScreen({}) } }
            }
            composeRule.onNodeWithText("EXP").performClick()
            composeRule.waitUntil(10000) { file.readText().contains("Output: /fixture/output") }
            assertEquals(1, launches)
            val text = file.readText()
            assertTrue(text.contains("Session: complete-session-id"))
            assertTrue(text.contains("Server: 42; project: 7"))
            assertTrue(text.contains("Started: 100; ended: 300"))
        } finally { file.delete(); db.clearAllTables() }
    }
    @Test fun failedExportReportsErrorAndAllowsRetryToAnEmptyDocument() {
        composeRule.setContent { androidx.compose.material3.Text("Export test") }
        val vm = se.joynes.terminalhub.ui.screen.sessionlog.SessionLogViewModel(repository)
        val file = java.io.File.createTempFile("empty-session-export", ".txt", composeRule.activity.cacheDir)
        try {
            composeRule.runOnIdle { vm.export(composeRule.activity, android.net.Uri.fromFile(composeRule.activity.cacheDir)) }
            composeRule.waitUntil(5000) { !vm.exporting.value && vm.exportMessage.value != null }
            assertNotEquals("Session log exported", vm.exportMessage.value)
            composeRule.runOnIdle { vm.export(composeRule.activity, android.net.Uri.fromFile(file)) }
            composeRule.waitUntil(5000) { !vm.exporting.value && vm.exportMessage.value == "Session log exported" }
            assertEquals("TerminalHub session log\n", file.readText())
        } finally { file.delete() }
    }
}
