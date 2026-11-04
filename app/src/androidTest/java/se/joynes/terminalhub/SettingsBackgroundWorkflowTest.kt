package se.joynes.terminalhub

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.*
import javax.inject.Inject
import org.junit.*
import org.junit.Assert.*
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.service.BackgroundSshService
import se.joynes.terminalhub.ui.screen.settings.SettingsScreen
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class SettingsBackgroundWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var runtime: AppRuntimeRepository
    private lateinit var original: AppSettings
    @Before fun setup() {
        hilt.inject(); original = settings.settings.value
        settings.applySettingsImport(original, AppSettings()); runtime.clearSessionState()
    }
    @After fun cleanup() {
        compose.activity.stopService(Intent(compose.activity, BackgroundSshService::class.java))
        compose.waitUntil(5000) { !runtime.state.value.foregroundServiceRunning }
        runtime.clearSessionState(); settings.applySettingsImport(settings.settings.value, original)
    }
    private fun scroll(text: String) = compose.onNodeWithTag("settings-sections").performScrollToNode(hasText(text))
    private fun expand() { scroll("CONNECTION & RECOVERY"); compose.onNodeWithText("CONNECTION & RECOVERY").performClick() }
    @Test fun explicitStartCreatesNotificationAndAutomaticStartAndStopPersist() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        runtime.noteSessions(setOf(42), emptySet(), 42)
        compose.setContent { TerminalHubTheme { SettingsScreen({}, {}) } }
        expand()
        compose.onNodeWithContentDescription("Keep SSH active in background").performScrollTo().performClick()
        compose.onNodeWithText("START").performClick()
        compose.waitUntil(10000) { runtime.state.value.foregroundServiceRunning }
        compose.onNodeWithContentDescription("Start background SSH automatically").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(settings.settings.value.automaticallyStartBackgroundSsh) }
        compose.onNodeWithContentDescription("Keep SSH active in background").performScrollTo().performClick()
        compose.waitUntil(10000) { !runtime.state.value.foregroundServiceRunning }
        assertFalse(settings.settings.value.keepSshActiveInBackground)
        assertFalse(settings.settings.value.automaticallyStartBackgroundSsh)
    }
    @Test fun deniedNotificationPermissionKeepsBackgroundModeOffAndBatterySettingsReturnRefreshesStatus() {
        val launched = mutableListOf<Any?>()
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                launched += input
                dispatchResult(code, if (input is Intent) Activity.RESULT_OK else Activity.RESULT_CANCELED, Intent())
            }
        }
        val context = object : ContextWrapper(compose.activity) {
            override fun checkPermission(permission: String, pid: Int, uid: Int) = if (permission == Manifest.permission.POST_NOTIFICATIONS)
                PackageManager.PERMISSION_DENIED else super.checkPermission(permission, pid, uid)
        }
        compose.setContent { CompositionLocalProvider(LocalContext provides context,
            LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }) {
            TerminalHubTheme { SettingsScreen({}, {}) }
        } }
        expand()
        compose.onNodeWithContentDescription("Keep SSH active in background").performScrollTo().performClick()
        compose.onNodeWithText("START").performClick()
        compose.waitUntil(5000) { launched.contains(Manifest.permission.POST_NOTIFICATIONS) }
        assertFalse(settings.settings.value.keepSshActiveInBackground)
        val battery = hasText("SET BATTERY TO UNRESTRICTED") or hasText("OPEN BATTERY SETTINGS")
        compose.onNodeWithTag("settings-sections").performScrollToNode(battery)
        compose.onNode(battery).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5000) { launched.filterIsInstance<Intent>().isNotEmpty() }
        assertEquals(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, launched.filterIsInstance<Intent>().single().action)
    }
}
