package se.joynes.terminalhub

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.runtime.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.domain.TerminalSessionManager
import se.joynes.terminalhub.service.BackgroundSshService

/** Runs the real foreground service and its notification, without contacting any SSH server. */
@HiltAndroidTest
class BackgroundServiceLifecycleTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var runtime: AppRuntimeRepository
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var mode: BackgroundSshModeController
    @Inject lateinit var sessions: TerminalSessionManager
    private lateinit var original: AppSettings
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private fun service(action: String? = null) = Intent(context, BackgroundSshService::class.java).setAction(action)
    @Before fun setup() {
        hilt.inject(); original = settings.settings.value
        context.stopService(service())
        compose.runOnIdle { sessions.clearForConfigImport() }
        runtime.clearSessionState()
        settings.setKeepSshActiveInBackground(false)
        if (Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        compose.setContent { Text("Background service test") }
    }
    @After fun cleanup() {
        context.stopService(service())
        compose.waitUntil(5000) { !runtime.state.value.foregroundServiceRunning }
        runtime.clearSessionState()
        settings.applySettingsImport(settings.settings.value, original)
    }
    private fun start() {
        ContextCompat.startForegroundService(context, service(BackgroundSshService.ACTION_START))
        compose.waitUntil(5000) { runtime.state.value.foregroundServiceRunning }
        compose.waitUntil(5000) { notifications.activeNotifications.any { it.id == 4101 } }
    }
    @Test fun userStartShowsUpdatedCountAndNotificationStopClearsThePreference() {
        start()
        assertTrue(settings.settings.value.keepSshActiveInBackground)
        assertEquals(BackgroundSshMode.ACTIVE, mode.mode.value)
        compose.runOnIdle { runtime.noteSessions(setOf(4, 7), emptySet(), 4) }
        compose.waitUntil(5000) { notifications.activeNotifications.any {
            it.id == 4101 && it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == "2 active SSH connections"
        } }
        context.startService(service(BackgroundSshService.ACTION_REFRESH))
        val notification = notifications.activeNotifications.single { it.id == 4101 }.notification
        assertEquals("TerminalHub SSH active", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        notification.actions.single().actionIntent.send()
        compose.waitUntil(5000) { !runtime.state.value.foregroundServiceRunning }
        assertFalse(settings.settings.value.keepSshActiveInBackground)
        assertEquals(BackgroundSshService.STOP_REASON_NOTIFICATION, runtime.state.value.lastServiceStopReason)
        assertNotNull(runtime.state.value.lastServiceStopSnapshot)
    }
    @Test fun systemServiceDestructionRecordsLossAndPreservesTheUsersBackgroundChoice() {
        start()
        context.stopService(service())
        compose.waitUntil(5000) { !runtime.state.value.foregroundServiceRunning }
        assertEquals(BackgroundSshService.STOP_REASON_SERVICE_DESTROYED, runtime.state.value.lastServiceStopReason)
        assertTrue(settings.settings.value.keepSshActiveInBackground)
        assertEquals(BackgroundSshMode.OFF, mode.mode.value)
    }
    @Test fun unknownStartActionStopsWithoutStartingForegroundMode() {
        context.startService(service("fixture.UNKNOWN"))
        compose.waitUntil(5000) { runtime.state.value.lastServiceStopReason == BackgroundSshService.STOP_REASON_MISSING_ACTION }
        assertFalse(runtime.state.value.foregroundServiceRunning)
        assertFalse(settings.settings.value.keepSshActiveInBackground)
        assertTrue(notifications.activeNotifications.none { it.id == 4101 })
    }
}
