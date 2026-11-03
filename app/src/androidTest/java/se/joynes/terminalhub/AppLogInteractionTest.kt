package se.joynes.terminalhub

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.entity.AppLogEntity
import se.joynes.terminalhub.ui.screen.applog.AppLogScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class AppLogInteractionTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var db: AppDatabase
    @Before fun setup() {
        hilt.inject(); db.clearAllTables()
        runBlocking {
            db.appLogDao().insert(AppLogEntity(timestamp = 1000, level = "INFO", tag = "coverage-fixture", message = "coverage-first"))
            db.appLogDao().insert(AppLogEntity(timestamp = 2000, level = "WARN", tag = "coverage-fixture", message = "coverage-second\nfull details"))
            db.appLogDao().insert(AppLogEntity(timestamp = 3000, level = "CUSTOM", tag = "coverage-fixture", message = "coverage-third"))
        }
        compose.setContent { TerminalHubTheme { AppLogScreen({}) } }
        compose.waitUntil(5000) { compose.onAllNodesWithText("coverage-third").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun clipboard(): String {
        val manager = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return manager.primaryClip!!.getItemAt(0).text.toString()
    }
    @Test fun entryDetailsAndSelectionCanBeCopiedWithoutLosingMultilineText() {
        compose.onNodeWithText("coverage-second\nfull details").performClick()
        compose.onNodeWithText("WARN [coverage-fixture]").assertIsDisplayed()
        compose.onNodeWithText("COPY ENTRY").performClick()
        compose.runOnIdle { assertTrue(clipboard().contains("WARN [coverage-fixture]\n\ncoverage-second\nfull details")) }
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithText("SEL").performClick()
        compose.onNodeWithText("SELECT LOGS").assertIsDisplayed()
        compose.onNodeWithText("COPY ALL").performClick()
        compose.runOnIdle {
            assertTrue(clipboard().contains("coverage-first")); assertTrue(clipboard().contains("coverage-third"))
            assertTrue(clipboard().indexOf("coverage-first") < clipboard().indexOf("coverage-third"))
        }
        compose.onNodeWithText("CLOSE").performClick()
        compose.onNodeWithText("COPY").performClick()
        compose.runOnIdle { assertTrue(clipboard().contains("full details")) }
    }
    @Test fun searchLevelFilterAndAutoscrollRemainUsableWithAnEmptyResult() {
        compose.onNodeWithText("AS:ON").performClick()
        compose.onNodeWithText("AS:OFF").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("coverage-second")
        compose.waitUntil(5000) { compose.onAllNodesWithText("coverage-first").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("coverage-second\nfull details").assertExists()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("ERROR").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("coverage-second\nfull details").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("SEL").performClick()
        compose.onNodeWithText("[no logs]").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").performClick()
    }
}
