package se.joynes.terminalhub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.*
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.model.*

@HiltAndroidTest
class ProjectNotesPersistenceTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var manager: ExportImportManager
    @Inject lateinit var projects: ProjectRepository

    @Test fun localNotesRequireExplicitExportAndRestoreToNewProjectIds() = runBlocking {
        hilt.inject()
        database.clearAllTables()
        val file = File.createTempFile("notes-test", ".yaml", context.cacheDir)
        val noteText = "private-note-marker å😀\r\n- Markdown \\n 'quotes'"
        try {
            val id = projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL, name = "local-demo"))
            database.projectNoteDao().save(ProjectNoteEntity(id, noteText, dirty = true))
            database.projectNoteDao().save(ProjectNoteEntity(99999, "remote-secret-never-exported", dirty = true))
            manager.exportYaml(context, Uri.fromFile(file))
            assertFalse(file.readText().contains("private-note-marker"))
            assertFalse(file.readText().contains("remote-secret-never-exported"))
            manager.exportYaml(context, Uri.fromFile(file), includeLocalNotes = true)
            assertTrue(file.readText().contains("private-note-marker"))
            assertFalse(file.readText().contains("remote-secret-never-exported"))
            manager.importYaml(context, Uri.fromFile(file))
            val restored = projects.getAll().first().single()
            assertTrue(restored.isLocal)
            assertNotEquals(id, restored.id)
            assertEquals(noteText, database.projectNoteDao().get(restored.id)?.text)
            assertNull(database.projectNoteDao().get(99999))
        } finally { database.clearAllTables(); file.delete() }
    }

    @Test fun migrationPreservesProjectsPinsAndNewNotesSurviveReopen() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val name = "notes-migration-test.db"
        app.deleteDatabase(name)
        var db = Room.databaseBuilder(app, AppDatabase::class.java, name).build()
        try {
            db.pinnedActionDao().save(PinnedActionEntity(name = "Keep", text = "git status", scope = "GLOBAL"))
            db.projectDao().insert(ProjectEntity(serverId = 7, name = "demo"))
            db.close()
            SQLiteDatabase.openDatabase(app.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE project_notes")
                it.version = 10
            }
            db = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(MIGRATION_10_11).build()
            assertEquals("Keep", db.pinnedActionDao().all().single().name)
            assertEquals("demo", db.projectDao().getAll().first().single().name)
            val note = ProjectNoteEntity(1, "saved å😀", dirty = true, pendingDelete = true)
            db.projectNoteDao().save(note)
            assertEquals(note, db.projectNoteDao().observe(1).first())
            db.close()
            db = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(MIGRATION_10_11).build()
            assertEquals(note, db.projectNoteDao().get(1))
            db.projectNoteDao().save(note.copy(text = "updated"))
            assertEquals("updated", db.projectNoteDao().get(1)?.text)
            db.projectNoteDao().delete(1)
            assertNull(db.projectNoteDao().get(1))
        } finally { db.close(); app.deleteDatabase(name) }
    }
}
