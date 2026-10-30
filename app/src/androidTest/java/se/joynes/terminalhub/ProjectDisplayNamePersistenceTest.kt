package se.joynes.terminalhub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.*
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.model.*
import se.joynes.terminalhub.domain.ScriptTemplateEngine

/** Recreate the actual old schema, so upgrade fixtures exercise every migration. */
internal fun restoreV12ProjectsTable(db: SQLiteDatabase) {
    val columns = "id,serverId,targetType,name,useTmux,customScript,aiCommand,colorSeed,createdAt,gitUrl,lastOpenedAt"
    db.execSQL("ALTER TABLE projects RENAME TO projects_fixture_v13")
    db.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serverId INTEGER NOT NULL, targetType TEXT NOT NULL, name TEXT NOT NULL, useTmux INTEGER NOT NULL, customScript TEXT NOT NULL, aiCommand TEXT NOT NULL, colorSeed INTEGER NOT NULL, createdAt INTEGER NOT NULL, gitUrl TEXT NOT NULL, lastOpenedAt INTEGER NOT NULL)")
    db.execSQL("INSERT INTO projects ($columns) SELECT $columns FROM projects_fixture_v13")
    db.execSQL("DROP TABLE projects_fixture_v13")
}

@HiltAndroidTest
class ProjectDisplayNamePersistenceTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var manager: ExportImportManager
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var servers: ServerRepository

    @Test fun migrationAndRenamingPreserveIdentityAndReopenWithNewDisplayName() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val name = "display-name-migration.db"
        app.deleteDatabase(name)
        fun open() = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(MIGRATION_12_13).build()
        var db = open()
        try {
            val id = db.projectDao().insert(ProjectEntity(serverId = 7, name = "keep-folder", colorSeed = 42, lastOpenedAt = 123, gitUrl = "git@example.org:repo.git"))
            db.projectNoteDao().save(ProjectNoteEntity(id, "keep notes"))
            db.close()
            SQLiteDatabase.openDatabase(app.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                restoreV12ProjectsTable(it)
                it.version = 12
            }
            db = open()
            val repo = ProjectRepository(db.projectDao())
            val before = repo.getById(id)!!
            assertEquals("keep-folder", before.displayTitle)
            repo.renameDisplayName(id, "  Mitt projekt åäö 😀  ")
            val after = repo.getById(id)!!
            assertEquals(before.copy(displayName = "Mitt projekt åäö 😀"), after)
            val engine = ScriptTemplateEngine()
            val server = Server(name = "server", host = "example.org", username = "user")
            assertEquals(engine.renderSetup(server, before), engine.renderSetup(server, after))
            assertEquals("keep notes", db.projectNoteDao().get(id)?.text)
            db.close()
            db = open()
            assertEquals(after, ProjectRepository(db.projectDao()).getById(id))
            try { ProjectRepository(db.projectDao()).renameDisplayName(id, "\n"); fail("Blank name must fail") }
            catch (_: IllegalArgumentException) { }
            assertEquals(after, ProjectRepository(db.projectDao()).getById(id))
        } finally { db.close(); app.deleteDatabase(name) }
    }

    @Test fun fullBackupRoundTripsLocalAndRemoteTitlesAndOldBackupsStillImport() = runBlocking {
        hilt.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database.clearAllTables()
        val file = File.createTempFile("project-titles", ".yaml", context.cacheDir)
        try {
            val serverId = servers.save(Server(name = "Test server", host = "example.org", username = "user"))
            projects.save(Project(serverId = serverId, name = "remote-folder", displayName = "Remote åäö 'quoted'"))
            projects.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL,
                name = "local-folder", displayName = "Local 😀 title"))
            manager.exportYaml(context, Uri.fromFile(file))
            val backup = file.readText()
            manager.importYaml(context, Uri.fromFile(file))
            val imported = projects.getAll().first().associateBy { it.name }
            assertEquals("Remote åäö 'quoted'", imported["remote-folder"]?.displayTitle)
            assertEquals("Local 😀 title", imported["local-folder"]?.displayTitle)
            // Omitted displayName in older remote backup leaves the original folder name visible.
            file.writeText(backup.lineSequence().filterNot { it.trimStart().startsWith("displayName:") }.joinToString("\n"))
            manager.importYaml(context, Uri.fromFile(file))
            assertEquals("remote-folder", projects.getAll().first().first { !it.isLocal }.displayTitle)
        } finally { database.clearAllTables(); file.delete() }
    }
}
