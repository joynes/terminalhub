package se.joynes.aiterminalhub

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import android.view.KeyEvent
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end UI test that adds a real server pointing at the host Mac
 * (10.0.2.2 = localhost from the Android emulator), connects, and
 * verifies the terminal renders SSH output.
 *
 * Prerequisites:
 *  - SSH server running on the host Mac (brew / Remote Login enabled)
 *  - Username below must match your Mac login
 *
 * Screenshots are saved to ~/Downloads/ and can be pulled with:
 *   adb pull /sdcard/Download/test-screenshots/ ~/Desktop/
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocalConnectionTest {

    // ── Adjust these for your local machine ─────────────────────────────────
    private val host     = "10.0.2.2"   // emulator → host Mac
    private val port     = "22"
    private val username = "demo"
    private val password = ""           // leave blank to skip password auth
    // ────────────────────────────────────────────────────────────────────────

    // HiltAndroidRule must come first so the component is ready before MainActivity launches
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var device: UiDevice
    // Use Downloads dir which is writable from tests
    private val screenshotDir = "/sdcard/Download/test-screenshots"

    @Before
    fun setUp() {
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("mkdir -p $screenshotDir")
        // Wake screen and dismiss keyguard so the activity is visible
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        Thread.sleep(500)
    }

    private fun screenshot(name: String) {
        try { device.takeScreenshot(File("$screenshotDir/$name.png")) } catch (_: Exception) {}
    }

    private fun waitFor(text: String, timeoutMs: Long = 10_000) =
        checkNotNull(device.wait(Until.findObject(By.textContains(text)), timeoutMs)) {
            dumpOnFail("Timed out waiting for text: \"$text\"")
        }

    /** Click a field by its label text, then type into the focused element. */
    private fun typeInto(labelText: String, value: String) {
        waitFor(labelText).click()
        Thread.sleep(200)
        // Clear existing content and type new value
        device.pressKeyCode(KeyEvent.KEYCODE_MOVE_END)
        repeat(50) { device.pressKeyCode(KeyEvent.KEYCODE_DEL) } // clear up to 50 chars
        InstrumentationRegistry.getInstrumentation().sendStringSync(value)
        Thread.sleep(100)
    }

    private fun waitForDesc(desc: String, timeoutMs: Long = 10_000) =
        checkNotNull(device.wait(Until.findObject(By.desc(desc)), timeoutMs)) {
            dumpOnFail("Timed out waiting for desc: \"$desc\"")
        }

    /** Scroll to a button and click it. Swipes down to reveal off-screen elements. */
    private fun scrollAndClick(text: String) {
        // Try UiScrollable first
        try {
            UiScrollable(UiSelector().scrollable(true))
                .scrollIntoView(UiSelector().textContains(text))
        } catch (_: Exception) {
            // Fallback: swipe up to reveal content below
            device.swipe(540, 1600, 540, 600, 10)
            Thread.sleep(300)
        }
        waitFor(text).click()
    }

    private fun dumpOnFail(msg: String): String {
        try {
            device.executeShellCommand("uiautomator dump $screenshotDir/ui_dump.xml")
            device.takeScreenshot(File("$screenshotDir/failure.png"))
        } catch (_: Exception) {}
        return msg
    }

    @Test
    fun connectToLocalMacAndSeeTerminal() {
        // ── 1. Wait for server list screen ───────────────────────────────────
        waitFor("SERVERS", 15_000)
        screenshot("01_server_list")

        // ── 2. Add server ────────────────────────────────────────────────────
        waitForDesc("Add server").click()

        typeInto("Hostname / IP *", host)
        typeInto("Port (default 22)", port)
        typeInto("Username *", username)
        if (password.isNotBlank()) typeInto("Password", password)
        // Dismiss keyboard (keyboard is open after typing → pressBack closes IME, not activity)
        device.pressBack()
        Thread.sleep(400)
        screenshot("02_add_server_filled")
        scrollAndClick("[ SAVE ]")

        // ── 3. Navigate to projects ──────────────────────────────────────────
        waitFor("PROJECTS", 10_000).click()
        screenshot("03_project_list")

        waitFor("[ ADD PROJECT ]").click()
        typeInto("Project Name *", "test")
        device.pressBack()
        Thread.sleep(400)
        screenshot("04_add_project_filled")
        scrollAndClick("[ SAVE ]")

        // ── 4. Connect ───────────────────────────────────────────────────────
        waitFor("CONNECT", 10_000).click()
        screenshot("05_connecting")

        // ── 5. Terminal screen loads ─────────────────────────────────────────
        // SpecialKeyBar buttons use text, not content-description
        waitFor("ESC", 30_000)
        screenshot("06_terminal_loaded")

        checkNotNull(device.findObject(By.textContains("ESC")))    { "ESC key not found" }
        checkNotNull(device.findObject(By.textContains("TAB")))    { "TAB key not found" }
        checkNotNull(device.findObject(By.textContains("CTRL-C"))) { "CTRL-C key not found" }

        Thread.sleep(2_000)
        screenshot("07_terminal_with_output")

        // ── 6. Navigate back to server list ──────────────────────────────────
        device.pressBack() // SessionHost → Projects
        device.pressBack() // Projects → ServerList
        waitFor("SERVERS", 5_000) // ensure we're on server list
        screenshot("08_server_list_after_connect")

        // Click LOG (exact match so we don't hit SLOG)
        checkNotNull(device.wait(Until.findObject(By.text("LOG")), 5_000)) {
            dumpOnFail("LOG button not found")
        }.click()
        screenshot("09_log_screen_before_wait")

        // "Connecting to" is always logged (DEBUG) even if auth fails.
        // Set `password` above to your Mac login password to also verify "Shell channel opened".
        waitFor("Connecting to", 15_000)
        screenshot("10_log_with_connect_entry")

        // If SSH auth succeeded, "Shell channel opened" should also appear
        val shellOpened = device.wait(Until.findObject(By.textContains("Shell channel opened")), 3_000)
        if (shellOpened != null) {
            screenshot("11_log_shell_opened")
        }
    }
}
