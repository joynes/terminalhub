package se.joynes.terminalhub

import androidx.compose.ui.test.junit4.createComposeRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import se.joynes.terminalhub.data.logging.AppLogger
import se.joynes.terminalhub.data.ssh.SharedSshTransportPool
import se.joynes.terminalhub.data.ssh.SshConnection
import se.joynes.terminalhub.domain.*

/** Real remote terminal emulators and ordered writers, with in-memory SSH streams.
 * No TerminalView is created: connected background tabs must work before first viewing. */
@HiltAndroidTest
class BackgroundMacroTargetTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createComposeRule()
    @Inject lateinit var sessions: TerminalSessionManager
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var pool: SharedSshTransportPool
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private data class Fixture(val connection: SshConnection, val bytes: ByteArrayOutputStream, val connected: MutableStateFlow<Boolean>)
    private val fixtures = mutableListOf<Fixture>()

    @Before fun setup() {
        hilt.inject()
        compose.runOnIdle { sessions.sessions.value.forEach { sessions.close(it.id) } }
    }

    @After fun cleanup() {
        jobs.cancel()
        compose.runOnIdle { sessions.sessions.value.forEach { sessions.close(it.id) } }
        fixtures.forEach { it.connection.disconnect() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun register(projectId: Long): Fixture {
        val connection = SshConnection(logger, pool)
        val bytes = ByteArrayOutputStream()
        // Supply a connected transport without opening sockets or requiring test credentials.
        SshConnection::class.java.getDeclaredField("outputStream").apply { isAccessible = true }.set(connection, bytes)
        val connected = SshConnection::class.java.getDeclaredField("_connected").apply { isAccessible = true }
            .get(connection) as MutableStateFlow<Boolean>
        connected.value = true
        sessions.register(connection.sessionId, connection, "background-$projectId", projectId)
        return Fixture(connection, bytes, connected).also { fixtures += it }
    }

    @Test fun fifteenNeverViewedSshTabsAcceptBroadcastImmediatelyWithoutSwitchingTabs() {
        val controller = PinnedMacroController(jobs)
        lateinit var original: TerminalSessionId
        compose.runOnIdle {
            (1L..15L).forEach { register(it) }
            original = requireNotNull(sessions.activeId.value)
            assertNotNull(sessions.activeSession().value?.emulator)
            val destinations = sessions.sessions.value.map { meta ->
                MacroDestination(meta.projectId, meta.projectName, requireNotNull(sessions.macroTargetForSession(meta.id)))
            }
            assertTrue(controller.startMany("Send", listOf(MacroStep.Key("C", ctrl = true), MacroStep.Text("hello"), MacroStep.Key("ENTER")), destinations))
            assertEquals(original, sessions.activeId.value)
        }
        compose.waitUntil(8000) { controller.state.value?.running == false }
        fixtures.forEach { assertEquals("\u0003hello\r", it.bytes.toString("UTF-8")) }
        compose.runOnIdle { assertEquals(original, sessions.activeId.value) }
        assertEquals("Completed in 15 projects", controller.state.value?.message)
    }

    @Test fun offlineNeverViewedTabIsRejectedAndExcludedTabReceivesNothing() {
        val controller = PinnedMacroController(jobs)
        compose.runOnIdle {
            register(1); register(2); val offline = register(3)
            offline.connected.value = false
            val all = sessions.sessions.value
            assertNull(sessions.macroTargetForSession(all.last().id))
            val selected = all.take(1).map { MacroDestination(it.projectId, it.projectName, requireNotNull(sessions.macroTargetForSession(it.id))) }
            assertTrue(controller.startMany("Send", listOf(MacroStep.Key("ENTER")), selected))
        }
        compose.waitUntil(5000) { controller.state.value?.running == false }
        assertEquals("\r", fixtures[0].bytes.toString("UTF-8"))
        assertEquals(0, fixtures[1].bytes.size())
        assertEquals(0, fixtures[2].bytes.size())
    }
}
