package se.joynes.aiterminalhub

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI test that adds a real server pointing at the host Mac
 * (10.0.2.2 = localhost from the Android emulator), connects, and
 * verifies the terminal renders SSH output.
 *
 * Prerequisites:
 *  - SSH server running on the host Mac (System Settings → Sharing → Remote Login)
 *  - Set SSH_TEST_USER / SSH_TEST_PASS via adb shell am instrument -e args, or
 *    hardcode credentials below for local dev use only.
 */
@RunWith(AndroidJUnit4::class)
class LocalConnectionTest {

    // ── Adjust these for your local machine ─────────────────────────────────
    private val host     = "10.0.2.2"   // emulator → host Mac
    private val port     = "22"
    private val username = "demo"
    private val password = ""           // leave blank to skip password auth
    // ────────────────────────────────────────────────────────────────────────

    private lateinit var device: UiDevice

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun connectToLocalMacAndSeeTerminal() {
        // ── 1. Wait for server list screen ───────────────────────────────────
        device.wait(Until.hasObject(By.text("SERVERS")), 10_000)

        // ── 2. Add server ────────────────────────────────────────────────────
        device.findObject(By.desc("Add server")).click()

        device.wait(Until.hasObject(By.text("Hostname / IP *")), 5_000)
        device.findObject(By.text("Hostname / IP *")).text = host
        device.findObject(By.text("Port (default 22)")).text = port
        device.findObject(By.text("Username *")).text = username
        if (password.isNotBlank()) {
            device.findObject(By.text("Password")).text = password
        }
        device.findObject(By.text("[ SAVE ]")).click()

        // ── 3. Back on server list — navigate to projects ────────────────────
        device.wait(Until.hasObject(By.text("PROJECTS")), 5_000)
        device.findObject(By.text("PROJECTS")).click()

        device.wait(Until.hasObject(By.text("[ ADD PROJECT ]")), 5_000)
        device.findObject(By.text("[ ADD PROJECT ]")).click()

        device.wait(Until.hasObject(By.text("Project Name *")), 3_000)
        device.findObject(By.text("Project Name *")).text = "test"
        device.findObject(By.text("[ SAVE ]")).click()

        // ── 4. Connect ───────────────────────────────────────────────────────
        device.wait(Until.hasObject(By.text("CONNECT")), 5_000)
        device.findObject(By.text("CONNECT")).click()

        // ── 5. Terminal screen loads ─────────────────────────────────────────
        device.wait(Until.hasObject(By.text("ESC")), 10_000)
        assert(device.findObject(By.text("ESC")) != null) { "ESC key not found" }
        assert(device.findObject(By.text("TAB")) != null) { "TAB key not found" }
        assert(device.findObject(By.text("CTRL-C")) != null) { "CTRL-C key not found" }

        // ── 6. Navigate back to log and verify connection success ─────────────
        device.pressBack() // back to projects
        device.pressBack() // back to servers

        device.wait(Until.hasObject(By.text("LOG")), 5_000)
        device.findObject(By.text("LOG")).click()

        device.wait(Until.hasObject(By.text("Shell channel opened")), 10_000)
        assert(device.findObject(By.text("Shell channel opened")) != null) {
            "Shell channel opened not found in logs"
        }
    }
}
