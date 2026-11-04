package se.joynes.terminalhub.ui.screen.terminal

import android.view.KeyEvent
import com.termux.terminal.TerminalSession
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class TerminalViewInputClientTest {
    @Test fun `unicode and control input go to attached session and are recorded before transient modifiers clear`() {
        val modifier = MutableModifierManager()
        val session = mock(TerminalSession::class.java)
        val recorded = mutableListOf<String>()
        val client = TerminalViewClientImpl(modifier, { error("Attached input must use session transport") }, {},
            recordInput = { owner, text -> assertSame(session, owner); recorded += text })
        modifier.shift = true
        assertTrue(client.readShiftKey()); assertFalse(client.readControlKey()); assertFalse(client.readAltKey()); assertFalse(client.readFnKey())
        assertTrue(client.onCodePoint(0x1f600, false, session))
        verify(session).write("😀".toByteArray(), 0, 4)
        assertFalse(modifier.shift)
        modifier.ctrl = true
        client.onCodePoint('c'.code, true, session)
        verify(session).write(byteArrayOf(3), 0, 1)
        modifier.alt = true
        assertTrue(client.readAltKey())
        client.onCodePoint('å'.code, false, session)
        verify(session).write("\u001Bå".toByteArray(), 0, 3)
        assertEquals(listOf("😀", "\u0003", "\u001Bå"), recorded)
        assertFalse(modifier.alt); assertFalse(modifier.ctrl)
    }
    @Test fun `special keys produce exact ANSI bytes and printable keys stay with IME`() {
        val sent = mutableListOf<String>()
        val modifiers = MutableModifierManager()
        val client = TerminalViewClientImpl(modifiers, { sent += it.toString(Charsets.UTF_8) }, {})
        val keys = linkedMapOf(
            KeyEvent.KEYCODE_ENTER to "\r", KeyEvent.KEYCODE_DPAD_UP to "\u001B[A",
            KeyEvent.KEYCODE_DPAD_DOWN to "\u001B[B", KeyEvent.KEYCODE_DPAD_RIGHT to "\u001B[C",
            KeyEvent.KEYCODE_DPAD_LEFT to "\u001B[D", KeyEvent.KEYCODE_PAGE_UP to "\u001B[5~",
            KeyEvent.KEYCODE_PAGE_DOWN to "\u001B[6~", KeyEvent.KEYCODE_MOVE_HOME to "\u001B[H",
            KeyEvent.KEYCODE_MOVE_END to "\u001B[F", KeyEvent.KEYCODE_DEL to "\u007F",
            KeyEvent.KEYCODE_FORWARD_DEL to "\u001B[3~", KeyEvent.KEYCODE_ESCAPE to "\u001B",
            KeyEvent.KEYCODE_TAB to "\t", KeyEvent.KEYCODE_F1 to "\u001BOP",
            KeyEvent.KEYCODE_F2 to "\u001BOQ", KeyEvent.KEYCODE_F3 to "\u001BOR", KeyEvent.KEYCODE_F4 to "\u001BOS",
            KeyEvent.KEYCODE_F5 to "\u001B[15~", KeyEvent.KEYCODE_F6 to "\u001B[17~", KeyEvent.KEYCODE_F7 to "\u001B[18~",
            KeyEvent.KEYCODE_F8 to "\u001B[19~", KeyEvent.KEYCODE_F9 to "\u001B[20~", KeyEvent.KEYCODE_F10 to "\u001B[21~",
            KeyEvent.KEYCODE_F11 to "\u001B[23~", KeyEvent.KEYCODE_F12 to "\u001B[24~")
        keys.forEach { (key, _) -> modifiers.ctrl = true; assertTrue(client.onKeyDown(key, null, null)); assertFalse(modifiers.ctrl) }
        assertEquals(keys.values.toList(), sent)
        assertFalse(client.onKeyDown(KeyEvent.KEYCODE_A, null, null))
        client.onCodePoint('a'.code, false, null)
        assertEquals("a", sent.last())
        assertFalse(client.onKeyUp(KeyEvent.KEYCODE_A, null))
    }
    @Test fun `attached special key and paste callbacks retain session identity and search selection`() {
        val session = mock(TerminalSession::class.java)
        var input = ""; var paste = ""; var search = ""; var taps = 0
        val client = TerminalViewClientImpl(MutableModifierManager(), { error("No fallback") }, { taps++ }, { search = it },
            { owner, text -> assertSame(session, owner); input = text },
            { owner, text -> assertSame(session, owner); paste = text })
        client.onKeyDown(KeyEvent.KEYCODE_ENTER, null, session)
        verify(session).write(byteArrayOf(13), 0, 1); assertEquals("\r", input)
        client.onUserPaste(session, "multiline\ntext"); assertEquals("multiline\ntext", paste)
        client.onSearchRequested("selected words"); assertEquals("selected words", search)
        client.onSingleTapUp(null); assertEquals(1, taps)
        assertEquals(1.5f, client.onScale(1.5f)); assertFalse(client.onLongPress(null))
        assertTrue(client.shouldEnforceCharBasedInput()); assertTrue(client.isTerminalViewSelected())
        assertFalse(client.shouldBackButtonBeMappedToEscape()); assertFalse(client.shouldUseCtrlSpaceWorkaround())
    }
}
