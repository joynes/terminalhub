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
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.model.PinnedMacroCodec
import se.joynes.terminalhub.domain.TerminalMacro

@HiltAndroidTest
class InputHistoryBackupTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var manager: ExportImportManager

    @Test fun completeBackupPreservesGlobalAndProjectMacrosAndRemapsTheirProject() = runBlocking {
        hilt.inject()
        database.clearAllTables()
        val file = File.createTempFile("macro-full-backup", ".yaml", context.cacheDir)
        val steps = TerminalMacro.interruptAndSend("echo å😀")
        val payload = PinnedMacroCodec.encode(steps)
        try {
            val serverId = database.serverDao().insert(ServerEntity(name = "Fixture", host = "example.invalid", username = "fixture"))
            val projectId = database.projectDao().insert(ProjectEntity(serverId = serverId, name = "macro-project"))
            database.pinnedActionDao().save(PinnedActionEntity(name = "Global macro", text = "summary", scope = "GLOBAL", sendEnter = false, macroJson = payload))
            database.pinnedActionDao().save(PinnedActionEntity(name = "Project macro", text = "summary", scope = "PROJECT", projectId = projectId, sendEnter = false, macroJson = payload))
            manager.exportYaml(context, Uri.fromFile(file))
            manager.importYaml(context, Uri.fromFile(file))
            val actions = database.pinnedActionDao().all()
            assertEquals(2, actions.size)
            actions.forEach { assertEquals(steps, PinnedMacroCodec.decode(it.macroJson)); assertFalse(it.sendEnter) }
            val restoredId = database.projectDao().getAll().first().single().id
            assertEquals(restoredId, actions.first { it.name == "Project macro" }.projectId)
            assertNull(actions.first { it.name == "Global macro" }.projectId)
        } finally { database.clearAllTables(); file.delete() }
    }

    @Test fun exportOmitsHistoryAndConsentAndImportAlwaysRequiresFreshOptIn() = runBlocking {
        hilt.inject()
        database.clearAllTables()
        val file = File.createTempFile("history-backup-test", ".yaml", context.cacheDir)
        try {
            settings.setInputHistoryEnabled(true)
            val scrollConfig = se.joynes.terminalhub.data.settings.TerminalScrollSettings.fast.copy(reverseDirection = true)
            settings.setTerminalScroll(scrollConfig)
            database.textInputHistoryDao().insert(TextInputHistoryEntity(projectId = 1, text = "private-history-marker"))
            database.pinnedActionDao().save(PinnedActionEntity(name = "Global action", text = "git status", scope = "GLOBAL"))
            manager.exportYaml(context, Uri.fromFile(file))
            val text = file.readText()
            assertFalse(text.contains("private-history-marker"))
            assertFalse(text.contains("inputHistoryEnabled"))
            assertFalse(text.contains("input_history_enabled"))
            assertTrue(text.contains("Global action"))
            settings.setTerminalScroll(se.joynes.terminalhub.data.settings.TerminalScrollSettings())
            manager.importYaml(context, Uri.fromFile(file))
            assertEquals(scrollConfig, settings.settings.value.terminalScroll)
            assertFalse(settings.settings.value.inputHistoryEnabled)
            assertFalse(AppSettingsRepository(context).settings.value.inputHistoryEnabled)
            assertEquals("Global action", database.pinnedActionDao().all().single().name)
        } finally {
            settings.setInputHistoryEnabled(false)
            database.clearAllTables()
            file.delete()
        }
    }
}
