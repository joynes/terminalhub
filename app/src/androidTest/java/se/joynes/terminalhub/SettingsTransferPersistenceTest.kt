package se.joynes.terminalhub

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.data.repository.*
import se.joynes.terminalhub.data.runtime.*
import se.joynes.terminalhub.data.settings.*
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.ui.screen.settings.SettingsViewModel

@HiltAndroidTest
class SettingsTransferPersistenceTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var files: SettingsFileManager
    @Inject lateinit var backup: ExportImportManager
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository
    @Inject lateinit var credentials: SecurePrefsManager
    @Inject lateinit var runtime: AppRuntimeRepository
    @Inject lateinit var mode: BackgroundSshModeController
    @Inject lateinit var input: InputActionsRepository

    @Test fun settingsOnlyRoundTripLeavesClosedProjectsCredentialsPinsNotesAndHistoryUntouched() = runBlocking {
        hilt.inject(); db.clearAllTables()
        val file = File.createTempFile("settings-test", ".yaml", context.cacheDir)
        val initial = settings.settings.value
        val serverId = servers.save(Server(name = "private-host-marker", host = "example.test", username = "user"))
        val projectId = projects.save(Project(serverId = serverId, name = "closed-project"))
        credentials.savePassword(serverId, "private-password-marker")
        db.projectNoteDao().save(ProjectNoteEntity(projectId, "private-note-marker"))
        db.pinnedActionDao().save(PinnedActionEntity(name = "Pin", text = "private-pin-marker"))
        db.textInputHistoryDao().insert(TextInputHistoryEntity(projectId = projectId, text = "private-history-marker"))
        try {
            files.export(Uri.fromFile(file))
            val text = file.readText()
            listOf("private-host-marker", "private-password-marker", "private-note-marker", "private-pin-marker", "private-history-marker").forEach { assertFalse(text.contains(it)) }
            file.writeText("format: terminalhub-settings\nversion: 1\nsettings:\n  terminalScroll: {remoteSpeed: 0.75}\n  preferFastResume: false")
            val preview = files.preview(Uri.fromFile(file))
            assertEquals(initial, settings.settings.value)
            settings.applySettingsImport(preview.base, preview.next)
            assertEquals(.75f, AppSettingsRepository(context).settings.value.terminalScroll.remoteSpeed)
            assertNotNull(projects.getById(projectId)); assertNotNull(servers.getById(serverId))
            assertEquals("private-password-marker", credentials.getPassword(serverId))
            assertEquals("private-note-marker", db.projectNoteDao().get(projectId)?.text)
            assertEquals("private-pin-marker", db.pinnedActionDao().all().single().text)
            assertEquals("private-history-marker", db.textInputHistoryDao().getRecentForProject(projectId).first().single().text)
            val before = settings.settings.value
            file.writeText("format: terminalhub-settings\nversion: 1\nsettings:\n  terminalScroll: {remoteSpeed: 999}")
            assertTrue(runCatching { files.preview(Uri.fromFile(file)) }.isFailure)
            assertEquals(before, settings.settings.value)
            assertTrue(runCatching { backup.importYaml(context, Uri.fromFile(file)) }.isFailure)
            assertNotNull(projects.getById(projectId)); assertEquals("private-password-marker", credentials.getPassword(serverId))
        } finally {
            settings.applySettingsImport(settings.settings.value, initial)
            credentials.clearAll(); db.clearAllTables(); file.delete()
        }
    }

    @Test fun previewCannotSilentlyEnableHistoryOrOverwriteNewerPreferences() {
        hilt.inject()
        settings.setInputHistoryEnabled(false)
        val vm = SettingsViewModel(context, settings, runtime, mode, input, files)
        val base = settings.settings.value
        try {
            vm.settingsPreview.value = SettingsYaml.preview("format: terminalhub-settings\nversion: 1\nsettings:\n  inputHistoryEnabled: true", base)
            vm.applySettingsPreview()
            assertFalse(settings.settings.value.inputHistoryEnabled)
            vm.applySettingsPreview(sensitiveChangesApproved = true)
            assertTrue(settings.settings.value.inputHistoryEnabled)
            vm.settingsPreview.value = SettingsYaml.preview("format: terminalhub-settings\nversion: 1\nsettings:\n  executeTextInputOnSend: false", settings.settings.value)
            settings.setTextInputPanelOpacity(.9f)
            vm.applySettingsPreview()
            assertEquals(.9f, settings.settings.value.textInputPanelOpacity)
            assertEquals(base.executeTextInputOnSend, settings.settings.value.executeTextInputOnSend)
        } finally { settings.applySettingsImport(settings.settings.value, base) }
    }

    @Test fun fullBackupIncludesAllClosedAndLocalProjectsAndPortablePreferences() = runBlocking {
        hilt.inject(); db.clearAllTables()
        val file = File.createTempFile("full-settings-test", ".yaml", context.cacheDir)
        val old = settings.settings.value
        try {
            val id = servers.save(Server(name = "server", host = "example.test", username = "user"))
            projects.save(Project(serverId = id, name = "closed-one"))
            projects.save(Project(serverId = id, name = "closed-two"))
            projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL, name = "local-one"))
            settings.setPreferFastResume(false); settings.setExecuteTextInputOnSend(false)
            settings.setBackgroundKeepaliveProfile(BackgroundKeepaliveProfile.BATTERY_SAVER)
            backup.exportYaml(context, Uri.fromFile(file))
            assertTrue(file.readText().contains("closed-two"))
            assertTrue(runCatching { files.preview(Uri.fromFile(file)) }.isFailure)
            settings.setPreferFastResume(true); settings.setExecuteTextInputOnSend(true)
            backup.importYaml(context, Uri.fromFile(file))
            assertEquals(setOf("closed-one", "closed-two", "local-one"), projects.getAll().first().map { it.name }.toSet())
            assertFalse(settings.settings.value.preferFastResume)
            assertFalse(settings.settings.value.executeTextInputOnSend)
            assertEquals(BackgroundKeepaliveProfile.BATTERY_SAVER, settings.settings.value.backgroundKeepaliveProfile)
            assertFalse(settings.settings.value.inputHistoryEnabled)
            assertFalse(settings.settings.value.automaticallyStartBackgroundSsh)
        } finally { settings.applySettingsImport(settings.settings.value, old); db.clearAllTables(); file.delete() }
    }
}
