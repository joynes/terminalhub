package se.joynes.terminalhub

import android.content.ContextWrapper
import android.content.Intent
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl

@RunWith(AndroidJUnit4::class)
class WrappedLinkBrowserTest {
    @get:Rule val rule = createComposeRule()

    @Test fun eitherWrappedFragmentOpensCompleteUrlInAndroidBrowserIntent() {
        val opened = mutableListOf<Intent>()
        lateinit var view: TerminalView
        lateinit var session: TerminalSession
        rule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(300.dp).testTag("terminal"),
                factory = { context ->
                    val browserContext = object : ContextWrapper(context) {
                        override fun startActivity(intent: Intent) { opened += intent }
                    }
                    session = TerminalSession.createRemoteSession(100, TerminalSessionClientImpl(browserContext),
                        object : TerminalInputListener {
                            override fun onTerminalInput(data: ByteArray, offset: Int, count: Int) = true
                        })
                    TerminalView(browserContext, null).also {
                        view = it
                        it.setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, {}))
                        it.setTextSize(24)
                        it.attachSession(session)
                    }
                }
            )
        }
        rule.runOnIdle {
            val first = "  inne i Music (https://joynes.github.io/"
            val text = "\u001b[?1000h" + first.padEnd(session.emulator.mColumns) +
                "  joynes.se/#music). Den separata AI-sektionen är borttagen."
            val bytes = text.toByteArray()
            session.emulator.append(bytes, bytes.size)
            view.invalidate()
        }
        for ((index, cell) in listOf(20 to 0, 5 to 1).withIndex()) {
            val (column, row) = cell
            var point = Offset.Zero
            rule.runOnIdle {
                val x = view.getPointX(column) + 2f
                val ys = (0 until view.height).filter { y ->
                    val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y.toFloat(), 0)
                    val matches = view.getColumnAndRow(event, true)[1] == row
                    event.recycle()
                    matches
                }
                point = Offset(x, (ys.first() + ys.last()) / 2f)
            }
            rule.onNodeWithTag("terminal").performTouchInput { advanceEventTime(500); click(point) }
            // TerminalView waits for Android's single-tap confirmation (double-tap timeout).
            rule.waitUntil(5_000) { opened.size == index + 1 }
        }
        rule.runOnIdle {
            assertEquals(2, opened.size)
            opened.forEach {
                assertEquals(Intent.ACTION_VIEW, it.action)
                assertEquals("https://joynes.github.io/joynes.se/#music", it.dataString)
            }
        }
    }
}
