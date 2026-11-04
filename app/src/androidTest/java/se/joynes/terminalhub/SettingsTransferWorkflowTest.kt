package se.joynes.terminalhub

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.*
import org.junit.Assert.*
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.ui.screen.settings.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
class SettingsTransferWorkflowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var servers: ServerRepository
    private lateinit var original: AppSettings
    private lateinit var file: File
    private lateinit var vm: SettingsViewModel
    private var launches = 0
    private var cancelled = false
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches++
            dispatchResult(code, if (cancelled) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                Intent().setData(Uri.fromFile(file)))
        }
    }
    @Before fun setup() {
        hilt.inject(); original = settings.settings.value
        settings.applySettingsImport(original, AppSettings())
        runBlocking { db.clearAllTables() }
        file = File.createTempFile("transfer-workflow", ".yaml", compose.activity.cacheDir)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) { TerminalHubTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                vm = hiltViewModel(); SettingsTransferSection(vm)
            } } }
        }
    }
    @After fun cleanup() {
        settings.applySettingsImport(settings.settings.value, original)
        runBlocking { db.clearAllTables() }; file.delete()
    }
    private fun click(text: String) {
        val inDialog = hasText(text) and hasAnyAncestor(isDialog())
        if (compose.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty()) compose.onNode(inDialog).performClick()
        else compose.onNodeWithText(text).performScrollTo().performClick()
    }
    private fun yaml(body: String) { file.writeText("format: terminalhub-settings\nversion: 1\nsettings:\n$body") }
    @Test fun settingsExportAndReviewedImportRoundTripThroughDocumentPicker() {
        click("EXPORT SETTINGS")
        compose.waitUntil(10000) { file.length() > 0 && !vm.settingsFileBusy.value }
        assertTrue(file.readText().contains("terminalhub-settings"))
        yaml("  preferFastResume: false")
        click("IMPORT SETTINGS")
        compose.waitUntil(10000) { vm.settingsPreview.value != null }
        compose.onNodeWithText("preferFastResume\ntrue → false").assertExists()
        assertTrue(settings.settings.value.preferFastResume)
        click("APPLY SETTINGS")
        compose.waitUntil(5000) { !settings.settings.value.preferFastResume }
        assertNull(vm.settingsPreview.value); assertEquals(2, launches)
    }
    @Test fun sensitiveImportRequiresApprovalAndCancelLeavesPreferencesUnchanged() {
        yaml("  inputHistoryEnabled: true")
        click("IMPORT SETTINGS")
        compose.waitUntil(10000) { vm.settingsPreview.value != null }
        compose.onNodeWithText("APPLY SETTINGS").assertIsNotEnabled()
        click("CANCEL")
        assertFalse(settings.settings.value.inputHistoryEnabled)
        click("IMPORT SETTINGS")
        compose.waitUntil(10000) { vm.settingsPreview.value != null }
        compose.onNode(isToggleable()).performScrollTo().performClick()
        click("APPLY SETTINGS")
        compose.waitUntil(5000) { settings.settings.value.inputHistoryEnabled }
    }
    @Test fun backupExportIncludesSavedServersAndRestoreReplacesThemAfterWarning() {
        runBlocking { servers.save(Server(name = "Backup source", host = "source.invalid", username = "fixture")) }
        click("EXPORT BACKUP")
        compose.onNodeWithText("Export full app configuration").assertIsDisplayed()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        click("EXPORT BACKUP")
        compose.waitUntil(10000) { file.length() > 0 }
        assertTrue(file.readText().contains("Backup source"))
        runBlocking { servers.save(Server(name = "Not in backup", host = "new.invalid", username = "fixture")) }
        click("RESTORE BACKUP")
        compose.onNodeWithText("Replace app configuration?").assertIsDisplayed()
        click("CANCEL")
        assertEquals(2, runBlocking { servers.getAll().first().size })
        click("RESTORE BACKUP"); click("CHOOSE BACKUP")
        compose.waitUntil(10000) { runBlocking { servers.getAll().first().map { it.name } == listOf("Backup source") } }
        assertEquals(2, launches)
    }
    @Test fun cancelledPickerAndMalformedImportDoNotChangeSettingsOrCreatePreview() {
        cancelled = true; click("EXPORT SETTINGS"); click("IMPORT SETTINGS")
        compose.runOnIdle { assertNull(vm.settingsPreview.value); assertFalse(vm.settingsFileBusy.value) }
        cancelled = false; file.writeText("invalid: yaml")
        click("IMPORT SETTINGS")
        compose.waitUntil(10000) { launches == 3 && !vm.settingsFileBusy.value }
        compose.runOnIdle { assertNull(vm.settingsPreview.value); assertEquals(AppSettings(), settings.settings.value) }
    }
}
