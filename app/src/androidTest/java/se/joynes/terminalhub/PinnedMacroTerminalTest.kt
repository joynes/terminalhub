package se.joynes.terminalhub

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.domain.*
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.TerminalViewClientImpl

/** Real PTYs and shell processes verify the production target adapter, not just callback mocks. */
@HiltAndroidTest
class PinnedMacroTerminalTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()
    @Inject lateinit var sessions: TerminalSessionManager
    @Inject @ApplicationContext lateinit var context: Context
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val firstName = "macro-test-first"
    private val secondName = "macro-test-second"

    @Before fun setup() {
        hilt.inject()
        compose.runOnIdle {
            sessions.sessions.value.forEach { sessions.close(it.id, killTmuxSession = false) }
            File(sessions.localProjectPath(firstName)).deleteRecursively()
            File(sessions.localProjectPath(secondName)).deleteRecursively()
            sessions.registerLocal(firstName, projectId = 900001)
        }
        compose.setContent {
            val session by sessions.activeSession().collectAsState()
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                TerminalView(context, null).apply {
                    setTextSize(24)
                    setTerminalViewClient(TerminalViewClientImpl(MutableModifierManager(), {}, {}))
                }
            }, update = { view -> session?.let { view.attachSession(it) } })
        }
        compose.waitUntil(5000) { sessions.activeSession().value?.isRunning == true }
    }

    @After fun cleanup() {
        jobs.cancel()
        compose.runOnIdle { sessions.sessions.value.forEach { sessions.close(it.id, killTmuxSession = false) } }
        File(sessions.localProjectPath(firstName)).deleteRecursively()
        File(sessions.localProjectPath(secondName)).deleteRecursively()
    }

    @Test fun ctrlCInterruptsCommandAndMacroStaysInOriginalTerminalAfterTabSwitch() {
        lateinit var target: MacroTerminalTarget
        val controller = PinnedMacroController(jobs)
        val result = File(sessions.localProjectPath(firstName), "result.txt")
        val other = File(sessions.localProjectPath(secondName), "result.txt")
        compose.runOnIdle {
            target = requireNotNull(sessions.macroTargetForActive())
            target.paste("sleep 30")
            target.press(MacroStep.Key("ENTER"))
        }
        // A short real wait lets the shell enter sleep before sending its interrupt.
        compose.waitForIdle()
        Thread.sleep(250)
        compose.runOnIdle {
            assertTrue(controller.start("Interrupt and write", firstName,
                listOf(MacroStep.Key("C", ctrl = true), MacroStep.Pause(500),
                    MacroStep.Text("printf macro-ok > result.txt"), MacroStep.Key("ENTER")), target))
            sessions.registerLocal(secondName, projectId = 900002)
            sessions.switchTo(sessions.sessions.value.first { it.projectId == 900002L }.id)
        }
        compose.waitUntil(8000) { controller.state.value?.running == false }
        compose.waitUntil(5000) { result.exists() && result.readText() == "macro-ok" }
        assertEquals("macro-ok", result.readText())
        assertFalse(other.exists())
        assertEquals("Macro completed", controller.state.value?.message)
    }

    @Test fun closingOriginalSessionStopsRemainingStepsAndNeverTargetsNewSession() {
        val controller = PinnedMacroController(jobs)
        compose.runOnIdle {
            val target = requireNotNull(sessions.macroTargetForActive())
            assertTrue(controller.start("Paused", firstName,
                listOf(MacroStep.Pause(1000), MacroStep.Text("touch must-not-exist"), MacroStep.Key("ENTER")), target))
            val original = sessions.activeId.value!!
            sessions.close(original, killTmuxSession = false)
            sessions.registerLocal(firstName, projectId = 900001)
            assertFalse(target.isConnected())
        }
        compose.waitUntil(5000) { controller.state.value?.running == false }
        assertTrue(controller.state.value!!.message!!.startsWith("Macro stopped"))
        assertFalse(File(sessions.localProjectPath(firstName), "must-not-exist").exists())
    }

    @Test fun broadcastWritesToBothRealShellsWithoutSwitchingTheSelectedTab() {
        val controller = PinnedMacroController(jobs)
        compose.runOnIdle {
            sessions.registerLocal(secondName, projectId = 900002)
        }
        compose.runOnIdle {
            val first = sessions.sessions.value.first { it.projectId == 900001L }.id
            val second = sessions.sessions.value.first { it.projectId == 900002L }.id
            val before = sessions.activeId.value
            val destinations = listOf(MacroDestination(900001, firstName, requireNotNull(sessions.macroTargetForSession(first))),
                MacroDestination(900002, secondName, requireNotNull(sessions.macroTargetForSession(second))))
            assertTrue(controller.startMany("Write", listOf(MacroStep.Text("printf broadcast-ok > broadcast.txt"), MacroStep.Key("ENTER")), destinations))
            assertEquals(before, sessions.activeId.value)
        }
        compose.waitUntil(8000) { controller.state.value?.running == false }
        compose.waitUntil(5000) {
            listOf(firstName, secondName).all {
                val result = File(sessions.localProjectPath(it), "broadcast.txt")
                result.exists() && result.readText() == "broadcast-ok"
            }
        }
        listOf(firstName, secondName).forEach { assertEquals("broadcast-ok", File(sessions.localProjectPath(it), "broadcast.txt").readText()) }
        assertEquals("Completed in 2 projects", controller.state.value?.message)
    }
}
