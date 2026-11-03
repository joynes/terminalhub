package se.joynes.terminalhub

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.terminal.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class TerminalSearchOverlayTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: TerminalView
    private lateinit var session: TerminalSession
    private var show by mutableStateOf(false)
    private fun setup(initial: String = "", output: (Int) -> String) {
        compose.setContent { TerminalHubTheme {
            Column(Modifier.fillMaxSize().imePadding()) {
                AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                    session = TerminalSession.createRemoteSession(2000, TerminalSessionClientImpl(context)) { _, _, _ -> true }
                    TerminalView(context, null).also {
                        view = it
                        it.setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, {}))
                        it.setTextSize(24); it.attachSession(session)
                    }
                })
                if (show) TerminalSearchOverlay(initial, view, { show = false })
            }
        } }
        compose.waitUntil(5000) { ::view.isInitialized && view.mEmulator != null }
        compose.runOnIdle {
            val bytes = output(view.mEmulator.mColumns).toByteArray()
            session.emulator.append(bytes, bytes.size)
            show = true
        }
    }
    @Test fun searchIgnoresCaseNavigatesBothDirectionsAndWrapsAtEnds() {
        setup("needle") { "Needle first\r\nnoise\r\nneedle second\r\n" }
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("▼").performClick()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithText("▼").performClick()
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithText("▲").performClick()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithText("✕").performClick()
        compose.runOnIdle { assertFalse(show) }
        compose.onNodeWithText("Search...").assertDoesNotExist()
    }
    @Test fun editedQueryHandlesNoMatchWhitespaceAndImeNext() {
        setup { "alpha\r\nAlpha\r\n" }
        compose.onNodeWithText("Search...").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput(" alpha ")
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("absent")
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("▼").performClick()
        compose.onNodeWithText("▲").performClick()
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement(" ")
        compose.onNodeWithText("0").assertDoesNotExist()
    }
    @Test fun matchesAcrossWrappedTerminalRowsAreFound() {
        setup("needle") { cols -> "x".repeat(cols - 2) + "needle\r\n" }
        compose.onNodeWithText("1/1").assertIsDisplayed()
    }
    @Test fun searchWithoutAnAttachedTerminalStillAllowsTypingAndDismissal() {
        var dismissed = false
        compose.setContent { TerminalHubTheme { TerminalSearchOverlay("", null, { dismissed = true }) } }
        compose.onNode(hasSetTextAction()).performTextInput("query")
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithText("✕").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
