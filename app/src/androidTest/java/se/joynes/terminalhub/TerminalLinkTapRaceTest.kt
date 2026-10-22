package se.joynes.terminalhub

import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.rules.TestName
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl

/** Exercise Android's delayed single-tap confirmation against a changing terminal buffer. */
class TerminalLinkTapRaceTest {
    @get:Rule val rule = createComposeRule()
    @get:Rule val testName = TestName()
    private lateinit var view: TerminalView
    private lateinit var session: TerminalSession
    private val opened = mutableListOf<Intent>()
    private var keyboardRequests = 0
    private var terminalWrites = 0
    private var failBrowser = false

    private fun setup() {
        rule.setContent {
            AndroidView(modifier = Modifier.fillMaxWidth().height(300.dp), factory = { context ->
                val browser = object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) {
                        opened += intent
                        if (failBrowser) throw ActivityNotFoundException("Test: no browser")
                    }
                }
                session = TerminalSession.createRemoteSession(500, TerminalSessionClientImpl(browser),
                    object : TerminalInputListener {
                        override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean {
                            terminalWrites++; return true
                        }
                    })
                TerminalView(browser, null).also {
                    view = it; it.setTextSize(24)
                    it.setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, { keyboardRequests++ }))
                    it.attachSession(session)
                }
            })
        }
        rule.waitForIdle()
    }

    private fun text(value: String) {
        val bytes = value.toByteArray()
        session.emulator.append(bytes, bytes.size)
        view.onScreenUpdated()
    }

    private fun tap(beforeUp: () -> Unit = {}, afterUp: () -> Unit = {}, column: Int = 10, releaseOffset: Float = 0f) {
        val x = view.getPointX(column) + 2f
        val candidates = (0 until view.height).filter { y ->
            val e = MotionEvent.obtain(0, 0, 0, x, y.toFloat(), 0)
            val match = view.getColumnAndRow(e, false)[1] == 0
            e.recycle(); match
        }
        val y = (candidates.first() + candidates.last()) / 2f
        val time = SystemClock.uptimeMillis()
        fun event(action: Int) {
            val eventX = if (action == MotionEvent.ACTION_UP) x + releaseOffset else x
            val e = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, eventX, y, 0)
            view.onTouchEvent(e); e.recycle()
        }
        event(MotionEvent.ACTION_DOWN)
        beforeUp()
        event(MotionEvent.ACTION_UP)
        afterUp()
    }

    @Test fun outputBetweenReleaseAndConfirmationDoesNotReplaceLinkWithKeyboard() {
        setup()
        rule.runOnIdle {
            text("https://example.com/original")
            tap(afterUp = { text("\r\u001b[2Kordinary output") })
        }
        rule.waitUntil(3000) { opened.isNotEmpty() || keyboardRequests > 0 }
        rule.runOnIdle {
            assertEquals("https://example.com/original", opened.singleOrNull()?.dataString)
            assertEquals(0, keyboardRequests)
        }
    }

    @Test fun changedLinkDuringFingerContactStillOpensOriginalAndSendsNoTmuxMouseClick() {
        setup()
        rule.runOnIdle {
            text("\u001b[?1000hhttps://example.com/original")
            tap(beforeUp = { text("\r\u001b[2Kordinary output") })
        }
        rule.waitUntil(3000) { opened.isNotEmpty() || keyboardRequests > 0 }
        rule.runOnIdle {
            assertEquals("https://example.com/original", opened.singleOrNull()?.dataString)
            assertEquals(0, keyboardRequests)
            assertEquals(0, terminalWrites)
        }
    }

    @Test fun missingBrowserDoesNotFallThroughToKeyboard() {
        setup(); failBrowser = true
        rule.runOnIdle { text("https://example.com/original"); tap() }
        rule.waitUntil(3000) { opened.isNotEmpty() }
        rule.runOnIdle { assertEquals(0, keyboardRequests) }
    }

    @Test fun slightFingerDriftOutsideLinkDoesNotSendMouseClickToTmux() {
        setup()
        rule.runOnIdle {
            text("\u001b[?1000hhttps://a.co/x")
            tap(column = 13, releaseOffset = view.getPointX(1).toFloat())
        }
        rule.waitUntil(3000) { opened.isNotEmpty() || keyboardRequests > 0 }
        rule.runOnIdle {
            assertEquals("https://a.co/x", opened.singleOrNull()?.dataString)
            assertEquals(0, keyboardRequests)
            assertEquals(0, terminalWrites)
        }
    }

    @Test fun ordinaryTextTapStillOpensKeyboardAndNeverOpensNewlyArrivingLink() {
        setup()
        rule.runOnIdle {
            text("ordinary terminal text")
            tap(afterUp = { text("\r\u001b[2Khttps://example.com/unrelated") })
        }
        rule.waitUntil(3000) { opened.isNotEmpty() || keyboardRequests > 0 }
        rule.runOnIdle { assertEquals(1, keyboardRequests); assertTrue(opened.isEmpty()) }
    }

    @Test fun changingSessionBeforeConfirmationDoesNotOpenAnythingInNewSession() {
        setup()
        rule.runOnIdle {
            text("https://example.com/original")
            tap(afterUp = {
                val next = TerminalSession.createRemoteSession(100, TerminalSessionClientImpl(view.context),
                    object : TerminalInputListener {
                        override fun onTerminalInput(data: ByteArray, offset: Int, count: Int) = true
                    })
                view.attachSession(next)
            })
        }
        awaitConfirmationWindow()
        rule.runOnIdle { assertEquals(0, keyboardRequests); assertTrue(opened.isEmpty()) }
    }

    @Test fun scrollingFromLinkDoesNotOpenBrowserOrKeyboard() {
        setup()
        rule.runOnIdle {
            text("https://example.com/original")
            val time = SystemClock.uptimeMillis()
            for ((action, y) in listOf(MotionEvent.ACTION_DOWN to 10f, MotionEvent.ACTION_MOVE to 150f,
                MotionEvent.ACTION_UP to 150f)) {
                val event = MotionEvent.obtain(time, time + action * 30L, action, 80f, y, 0)
                view.onTouchEvent(event); event.recycle()
            }
        }
        awaitConfirmationWindow()
        rule.runOnIdle { assertEquals(0, keyboardRequests); assertTrue(opened.isEmpty()) }
    }

    private fun awaitConfirmationWindow() {
        val elapsed = java.util.concurrent.atomic.AtomicBoolean()
        rule.runOnIdle { view.postDelayed({ elapsed.set(true) }, android.view.ViewConfiguration.getDoubleTapTimeout() + 100L) }
        rule.waitUntil(3000) { elapsed.get() }
    }

    @After fun recordDiagnosticEvidenceWithoutLoggingLinkContents() {
        if (!::view.isInitialized) return
        val device = androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
        val top = device.executeShellCommand("dumpsys activity activities").lineSequence()
            .firstOrNull { it.contains("topResumedActivity") }.orEmpty().trim()
        android.util.Log.i("THLINKDIAG", "pid=${android.os.Process.myPid()} test=${testName.methodName} browserIntents=${opened.size} keyboardRequests=$keyboardRequests $top")
        device.takeScreenshot(java.io.File(view.context.cacheDir, "link-tap-${testName.methodName}.png"))
    }
}
