package se.joynes.terminalhub

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.domain.TerminalInputHistoryRecorder
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl

@RunWith(AndroidJUnit4::class)
class TerminalInputIngressTest {
    @Test fun imeHardwareAndPasteRecordUserInputButProtocolRepliesDoNot() {
        lateinit var verifyPaste: () -> Unit
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val saved = mutableListOf<String>()
            val writes = StringBuilder()
            val recorder = TerminalInputHistoryRecorder { _, text -> saved += text }
            val session = TerminalSession.createRemoteSession(100, TerminalSessionClientImpl(context), object : TerminalInputListener {
                override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean {
                    writes.append(String(data, offset, count, Charsets.UTF_8))
                    return true
                }
            })
            val client = TerminalViewClientImpl(
                MutableModifierManager(), onSendToSsh = {}, onTerminalTap = {},
                recordInput = { actual, text -> assertSame(session, actual); recorder.input(1, text) },
                recordPaste = { actual, text -> assertSame(session, actual); recorder.paste(1, text) }
            )
            val view = TerminalView(context, null).apply {
                setTerminalViewClient(client)
                setTextSize(24)
                layout(0, 0, 800, 600)
                attachSession(session)
            }
            val input = view.onCreateInputConnection(EditorInfo())
            input.commitText("git status", 1)
            view.handleKeyCode(KeyEvent.KEYCODE_ENTER, 0)
            assertEquals(listOf("git status"), saved)

            client.onCodePoint('h'.code, false, session)
            client.onCodePoint('i'.code, false, session)
            client.onKeyDown(KeyEvent.KEYCODE_ENTER, null, session)
            assertEquals(listOf("git status", "hi"), saved)

            // Enable bracketed paste through terminal OUTPUT. This must never enter history.
            val output = "\u001b[?2004h\u001b[6n".toByteArray()
            session.appendRemoteOutput(output, 0, output.size)
            verifyPaste = {
                client.onUserPaste(session, "one\ntwo")
                session.emulator.paste("one\ntwo")
                assertTrue(writes.contains("\u001b[200~"))
                assertEquals(2, saved.size)
                view.handleKeyCode(KeyEvent.KEYCODE_ENTER, 0)
                assertEquals(listOf("git status", "hi", "one\ntwo"), saved)
            }
        }
        // Remote output is queued on the UI looper, just as it is during real SSH traffic.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { verifyPaste() }
    }
}
