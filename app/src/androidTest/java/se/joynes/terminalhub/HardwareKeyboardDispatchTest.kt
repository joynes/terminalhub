package se.joynes.terminalhub

import android.os.SystemClock
import android.os.Process
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.termux.terminal.TerminalInputListener
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.ssh.TerminalSessionClientImpl
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl
import java.io.ByteArrayOutputStream
import java.io.File

/** Exercise the real window + Compose AndroidView dispatch, not just onKeyDown directly. */
class HardwareKeyboardDispatchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun windowDispatchSendsEachHardwareCharacterOnceIncludingIntendedRepeatedLetters() =
        verifyWindowDispatch(KeyCharacterMap.VIRTUAL_KEYBOARD)

    @Test fun physicalKeyboardDeviceDispatchAlsoPreservesIntentionalRepeatedLetters() {
        val hardware = InputDevice.getDeviceIds().asSequence().mapNotNull { InputDevice.getDevice(it) }
            .firstOrNull { !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC }
        assumeTrue("Requires an attached alphabetic keyboard (or hw.keyboard=yes on the AVD)", hardware != null)
        verifyWindowDispatch(hardware!!.id)
    }

    private fun verifyWindowDispatch(deviceId: Int) {
        val output = ByteArrayOutputStream()
        lateinit var terminal: TerminalView
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                lateinit var session: TerminalSession
                session = TerminalSession.createRemoteSession(100, TerminalSessionClientImpl(context), object : TerminalInputListener {
                    override fun onTerminalInput(data: ByteArray, offset: Int, count: Int): Boolean {
                        output.write(data, offset, count)
                        // Local echo for the test screenshot only; no SSH server is involved.
                        session.appendRemoteOutput(data, offset, count)
                        return true
                    }
                })
                TerminalView(context, null).apply {
                    setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(),
                        onSendToSsh = { fail("Expected attached terminal transport") }, onTerminalTap = {}))
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTextSize(24)
                    attachSession(session)
                    post { requestFocus() }
                    terminal = this
                }
            })
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(terminal.hasFocus()) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (code in listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_A)) {
            val time = SystemClock.uptimeMillis()
            instrumentation.sendKeySync(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0,
                deviceId, 0, 0, InputDevice.SOURCE_KEYBOARD))
            instrumentation.sendKeySync(KeyEvent(time, time + 1, KeyEvent.ACTION_UP, code, 0, 0,
                deviceId, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("abba", output.toString("UTF-8"))
            terminal.onScreenUpdated()
            Log.i("THKEYDIAG", "pid=${Process.myPid()} device=$deviceId focused=${terminal.hasFocus()} downUpPairs=4 bytes=${output.size()}")
        }
        compose.waitForIdle()
        val device = UiDevice.getInstance(instrumentation)
        val top = device.executeShellCommand("dumpsys activity activities").lineSequence()
            .firstOrNull { it.contains("topResumedActivity") }.orEmpty().trim()
        Log.i("THKEYDIAG", "pid=${Process.myPid()} $top")
        assertTrue(device.takeScreenshot(File(compose.activity.cacheDir, "keyboard-dispatch-$deviceId.png")))
    }
}
