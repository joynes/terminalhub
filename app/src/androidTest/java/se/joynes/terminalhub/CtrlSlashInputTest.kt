package se.joynes.terminalhub

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl
import se.joynes.terminalhub.ui.theme.TerminalHubTheme
import java.io.ByteArrayOutputStream

class CtrlSlashInputTest {
    @get:Rule val compose = createComposeRule()

    private class TerminalFixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = ByteArrayOutputStream()
        val modifiers = MutableModifierManager()
        val session = TerminalSession.createRemoteSession(100, TerminalSessionClientImpl(context), object : TerminalInputListener {
            override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean {
                bytes.write(data, offset, count)
                return true
            }
        })
        val client = TerminalViewClientImpl(modifiers, onSendToSsh = { fail("Input must use the attached session") }, onTerminalTap = {})
        val view = TerminalView(context, null).apply {
            setTerminalViewClient(client)
            setTextSize(24)
            layout(0, 0, 800, 600)
            attachSession(session)
        }
    }

    @Test fun softKeyboardCtrlSlashAndUnderscoreProduceIdenticalRawBytesAndClearLatch() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val terminal = TerminalFixture()
            val input = terminal.view.onCreateInputConnection(EditorInfo())
            listOf("/", "_").forEach { key ->
                terminal.modifiers.toggleCtrl()
                input.commitText(key, 1)
                assertFalse(terminal.modifiers.ctrl)
            }
            input.commitText("/", 1)
            terminal.modifiers.toggleCtrl()
            input.commitText("c", 1)
            assertArrayEquals(byteArrayOf(31, 31, 47, 3), terminal.bytes.toByteArray())
        }
    }

    @Test fun hardwareCtrlSlashAndShiftUnderscoreUseSameEncodingWithoutBreakingPlainSlash() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val terminal = TerminalFixture()
            fun press(code: Int, modifiers: Int) {
                terminal.view.onKeyDown(code, KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers))
            }
            press(KeyEvent.KEYCODE_SLASH, KeyEvent.META_CTRL_ON)
            press(KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            press(KeyEvent.KEYCODE_SLASH, 0)
            press(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)
            assertArrayEquals(byteArrayOf(31, 31, 47, 3), terminal.bytes.toByteArray())
        }
    }

    @Test fun clientWithoutSessionUsesSameRawEncodingAndPreservesUnicode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val output = ByteArrayOutputStream()
            val client = TerminalViewClientImpl(MutableModifierManager(), onSendToSsh = { output.write(it) }, onTerminalTap = {})
            client.onCodePoint('/'.code, true, null)
            client.onCodePoint('_'.code, true, null)
            client.onCodePoint('/'.code, false, null)
            client.onCodePoint('c'.code, true, null)
            client.onCodePoint('å'.code, true, null)
            assertArrayEquals(byteArrayOf(31, 31, 47, 3) + "å".toByteArray(), output.toByteArray())
        }
    }

    @Test fun keyBarCtrlSlashWritesUnitSeparatorToRealTerminalTransport() {
        lateinit var terminal: TerminalFixture
        InstrumentationRegistry.getInstrumentation().runOnMainSync { terminal = TerminalFixture() }
        compose.setContent {
            TerminalHubTheme {
                SpecialKeyBar(modifierManager = terminal.modifiers,
                    rows = listOf(listOf("CTRL", "SLASH", "UNDERSCORE", "CHAR_C")),
                    onKey = { terminal.session.write(it) })
            }
        }
        compose.onNodeWithText("CTRL").performClick()
        compose.onNodeWithText("/").performClick()
        compose.onNodeWithText("CTRL").performClick()
        compose.onNodeWithText("_").performClick()
        compose.onNodeWithText("/").performClick()
        compose.onNodeWithText("CTRL").performClick()
        compose.onNodeWithText("C").performClick()
        compose.runOnIdle {
            assertArrayEquals(byteArrayOf(31, 31, 47, 3), terminal.bytes.toByteArray())
            assertFalse(terminal.modifiers.ctrl)
        }
    }
}
