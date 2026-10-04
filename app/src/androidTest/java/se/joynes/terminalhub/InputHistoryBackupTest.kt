package se.joynes.terminalhub

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.data.settings.AppSettingsRepository

@HiltAndroidTest
class InputHistoryBackupTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var settings: AppSettingsRepository
    @Inject lateinit var manager: ExportImportManager

    @Test fun exportOmitsHistoryAndConsentAndImportAlwaysRequiresFreshOptIn() = runBlocking {
        hilt.inject()
        database.clearAllTables()
        val file = File.createTempFile("history-backup-test", ".yaml", context.cacheDir)
        try {
            settings.setInputHistoryEnabled(true)
            database.textInputHistoryDao().insert(TextInputHistoryEntity(projectId = 1, text = "private-history-marker"))
            database.pinnedActionDao().save(PinnedActionEntity(name = "Global action", text = "git status", scope = "GLOBAL"))
            manager.exportYaml(context, Uri.fromFile(file))
            val text = file.readText()
            assertFalse(text.contains("private-history-marker"))
            assertFalse(text.contains("inputHistoryEnabled"))
            assertFalse(text.contains("input_history_enabled"))
            assertTrue(text.contains("Global action"))
            manager.importYaml(context, Uri.fromFile(file))
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
