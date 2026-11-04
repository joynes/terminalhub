package se.joynes.terminalhub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.TerminalSession
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl

class TerminalClipboardWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun copyingAndPastingPreservesMultilineUnicodeAndNotifiesInputHistory() {
        compose.setContent { Text("Clipboard fixture") }
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val events = mutableListOf<String>()
            var updates = 0
            val client = TerminalSessionClientImpl(context, { _, text -> events += text }, { updates++ })
            val sent = java.io.ByteArrayOutputStream()
            val session = TerminalSession.createRemoteSession(2000, client) { data, offset, length -> sent.write(data, offset, length); true }
            session.updateSize(80, 24, 10, 20)
            val text = "hello å\nsecond line"
            client.onCopyTextToClipboard(session, text)
            assertEquals(text, clipboard.primaryClip!!.getItemAt(0).text.toString())
            client.onPasteTextFromClipboard(session)
            assertEquals(listOf(text), events)
            assertEquals("hello å\rsecond line", sent.toString("UTF-8"))
            client.onTextChanged(session); assertEquals(1, updates)
            clipboard.setPrimaryClip(ClipData.newPlainText("empty", ""))
            client.onPasteTextFromClipboard(session); assertEquals(1, events.size)
            clipboard.setPrimaryClip(ClipData.newPlainText("detached", "detached text"))
            client.onPasteTextFromClipboard(null)
            assertEquals("detached text", events.last())
            session.finishIfRunning()
        }
    }
}
