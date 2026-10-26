package se.joynes.terminalhub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.ui.screen.sessions.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class PinnedActionProjectsTest {
    @get:Rule val compose = createComposeRule()
    private val action = PinnedActionEntity(name = "Restart", text = "npm start", sendEnter = true)
    private fun project(id: Long, connected: Boolean = true) = ProjectTabState(id, "project-$id", TerminalSessionId("session-$id"), connected)

    @Test fun allProjectsStartSelectedAndExcludedProjectIsNotSent() {
        var sent: Set<Long>? = null
        compose.setContent { TerminalHubTheme {
            PinnedActionProjectsDialog(action, listOf(project(1), project(2), project(3)), {}, { sent = it })
        } }
        compose.onNodeWithText("SEND TO 3 PROJECTS").assertIsEnabled()
        compose.onNodeWithText("project-2").performClick()
        compose.onNodeWithText("SEND TO 2 PROJECTS").performClick()
        compose.runOnIdle { assertEquals(setOf(1L, 3L), sent) }
    }

    @Test fun disconnectedProjectMustBeReconnectedOrDeselectedAndNoneCannotSend() {
        var sent: Set<Long>? = null
        compose.setContent { TerminalHubTheme {
            PinnedActionProjectsDialog(action, listOf(project(1), project(2, false)), {}, { sent = it })
        } }
        compose.onNodeWithText("Reconnect first").assertIsDisplayed()
        compose.onNodeWithText("SEND TO 2 PROJECTS").assertIsNotEnabled()
        compose.onNodeWithText("project-2").performClick()
        compose.onNodeWithText("SEND TO 1 PROJECTS").assertIsEnabled()
        compose.onNodeWithText("None").performClick()
        compose.onNodeWithText("SEND TO 0 PROJECTS").assertIsNotEnabled()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("SEND TO 2 PROJECTS").assertIsNotEnabled()
        compose.runOnIdle { assertNull(sent) }
    }

    @Test fun longPressActionOffersBroadcastAndCancelSendsNothing() {
        var sent = false
        compose.setContent { TerminalHubTheme {
            PinnedActionsSheet(1, listOf(action), emptyList(), onDismiss = {}, onPrepare = {}, onSend = {}, onSave = {},
                onDeletePin = {}, onDeleteHistory = {}, openProjects = listOf(project(1)),
                onSendToProjects = { _, _ -> sent = true; true })
        } }
        compose.onNodeWithText("Restart").performTouchInput { longClick() }
        compose.onNodeWithText("Send to multiple projects").performClick()
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle { assertFalse(sent) }
    }
}
