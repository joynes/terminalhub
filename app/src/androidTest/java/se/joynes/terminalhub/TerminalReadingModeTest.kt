package se.joynes.terminalhub

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.terminal.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class TerminalReadingModeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun readingModeAnchorsOutputUntilFollowAndDoesNotLeakIntoAnotherSession() {
        lateinit var view: TerminalView
        lateinit var session: TerminalSession
        rule.setContent {
            AndroidView(modifier = Modifier.fillMaxWidth().height(300.dp), factory = { context ->
                session = TerminalSession.createRemoteSession(1000, TerminalSessionClientImpl(context),
                    object : TerminalInputListener { override fun onTerminalInput(data: ByteArray, offset: Int, count: Int) = true })
                TerminalView(context, null).also {
                    view = it
                    it.setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, {}))
                    it.setTextSize(24); it.attachSession(session)
                }
            })
        }
        rule.runOnIdle {
            fun append(text: String) { val bytes = text.toByteArray(); session.emulator.append(bytes, bytes.size); view.onScreenUpdated() }
            append((1..80).joinToString("\r\n") { "Line $it" })
            assertEquals(0, view.topRow)
            view.setReadingMode(true)
            append("\r\nNew output\r\nMore output")
            assertTrue(view.isReadingMode)
            assertTrue(view.topRow < 0)
            view.scrollToBottom()
            assertFalse(view.isReadingMode)
            assertEquals(0, view.topRow)
            append("\r\nFollow me")
            assertEquals(0, view.topRow)
            view.setReadingMode(true)
            val next = TerminalSession.createRemoteSession(1000, TerminalSessionClientImpl(view.context),
                object : TerminalInputListener { override fun onTerminalInput(data: ByteArray, offset: Int, count: Int) = true })
            view.attachSession(next)
            assertFalse(view.isReadingMode)
        }
    }

    @Test fun searchCanBeOpenedEmptyAndNavigateMatchingLines() {
        lateinit var view: TerminalView
        var ready by mutableStateOf(false)
        rule.setContent { TerminalHubTheme {
            Column {
                AndroidView(modifier = Modifier.fillMaxWidth().height(250.dp), factory = { context ->
                    val session = TerminalSession.createRemoteSession(1000, TerminalSessionClientImpl(context),
                        object : TerminalInputListener { override fun onTerminalInput(data: ByteArray, offset: Int, count: Int) = true })
                    TerminalView(context, null).also {
                        view = it; it.setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, {}))
                        it.setTextSize(24); it.attachSession(session)
                    }
                })
                if (ready) TerminalSearchOverlay("", view, {})
            }
        } }
        rule.runOnIdle {
            val bytes = "needle one\r\nother text\r\nNeedle two".toByteArray()
            view.mEmulator.append(bytes, bytes.size); view.onScreenUpdated()
            ready = true
        }
        rule.onNode(hasSetTextAction()).performTextInput("needle")
        rule.waitUntil(3000) { rule.onAllNodesWithText("1/2").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithText("▼").performClick()
        rule.onNodeWithText("2/2").assertIsDisplayed()
        rule.onNodeWithText("Aa").performClick()
        rule.waitUntil(3000) { rule.onAllNodesWithText("1/1").fetchSemanticsNodes().size == 1 }
    }
}
