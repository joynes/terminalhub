package se.joynes.terminalhub

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalInputListener
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.settings.TerminalScrollSettings
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.settings.TerminalScrollSettingsEditor
import se.joynes.terminalhub.ui.screen.terminal.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class TerminalScrollUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun presetsSlidersAndInertiaToggleUpdateWithoutLosingOtherControls() {
        var settings by mutableStateOf(TerminalScrollSettings())
        rule.setContent { TerminalHubTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) { TerminalScrollSettingsEditor(settings) { settings = it } }
        } }
        rule.onNodeWithText("Fast").performClick()
        rule.runOnIdle { assertEquals(TerminalScrollSettings.fast, settings) }
        rule.onNodeWithContentDescription("Terminal speed").performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        rule.runOnIdle { assertEquals(3f, settings.terminalSpeed); assertEquals(TerminalScrollSettings.fast.braking, settings.braking) }
        rule.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        rule.runOnIdle { assertFalse(settings.inertia) }
        rule.onNodeWithText("Default").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(TerminalScrollSettings(), settings) }
    }

    @Test fun tmuxFingerFlickContinuesAfterReleaseAndTouchStopsQueuedWheelEvents() {
        lateinit var view: TerminalView
        lateinit var session: TerminalSession
        var writes = 0
        rule.setContent {
            AndroidView(modifier = Modifier.fillMaxWidth().height(300.dp).testTag("terminal"), factory = { context ->
                session = TerminalSession.createRemoteSession(2000, TerminalSessionClientImpl(context), object : TerminalInputListener {
                    override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean { writes++; return true }
                })
                TerminalView(context, null).also {
                    view = it
                    it.setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, {}))
                    it.setTextSize(24); it.attachSession(session)
                    it.setScrollPreferences(1f, 1f, true, 1f, 1f, 50f, 60, 3, false)
                }
            })
        }
        var atRelease = 0
        rule.runOnIdle {
            val bytes = "\u001b[?1049h\u001b[?1000h\u001b[?1006h".toByteArray()
            session.emulator.append(bytes, bytes.size)
            val time = SystemClock.uptimeMillis()
            fun event(action: Int, elapsed: Long, y: Float) {
                val event = MotionEvent.obtain(time, time + elapsed, action, 100f, y, 0)
                view.onTouchEvent(event); event.recycle()
            }
            val bottom = view.height * .85f
            event(MotionEvent.ACTION_DOWN, 0, bottom)
            event(MotionEvent.ACTION_MOVE, 30, bottom - 80)
            event(MotionEvent.ACTION_MOVE, 60, bottom - 200)
            event(MotionEvent.ACTION_UP, 90, bottom - 280)
            atRelease = writes
            assertTrue(atRelease > 0)
        }
        rule.waitUntil(3000) { writes > atRelease }
        var stopped = 0
        rule.runOnIdle {
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
            view.onTouchEvent(down); down.recycle()
            stopped = writes
        }
        Thread.sleep(250)
        rule.runOnIdle { assertEquals(stopped, writes) }
        var withoutInertia = 0
        rule.runOnIdle {
            val time = SystemClock.uptimeMillis()
            fun event(action: Int, elapsed: Long, y: Float) {
                val event = MotionEvent.obtain(time, time + elapsed, action, 100f, y, 0)
                view.onTouchEvent(event); event.recycle()
            }
            event(MotionEvent.ACTION_CANCEL, 0, 100f)
            view.setScrollPreferences(1f, 1f, false, 1f, 1f, 50f, 60, 3, false)
            val bottom = view.height * .85f
            event(MotionEvent.ACTION_DOWN, 10, bottom)
            event(MotionEvent.ACTION_MOVE, 40, bottom - 80)
            event(MotionEvent.ACTION_MOVE, 70, bottom - 200)
            event(MotionEvent.ACTION_UP, 100, bottom - 280)
            withoutInertia = writes
            assertTrue(withoutInertia > stopped)
        }
        Thread.sleep(250)
        rule.runOnIdle { assertEquals(withoutInertia, writes) }
    }
}
