package se.joynes.terminalhub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.MIGRATION_9_10
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.export.decodePinnedActions
import se.joynes.terminalhub.data.export.encodePinnedActions
import se.joynes.terminalhub.data.export.decodeYamlScalar

@RunWith(AndroidJUnit4::class)
class InputActionsStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun historyRetains100PerProjectDeduplicatesAndPreservesOriginalText() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.textInputHistoryDao()
            repeat(105) { dao.saveRecent(TextInputHistoryEntity(projectId = 1, text = "entry $it", createdAt = 42)) }
            dao.saveRecent(TextInputHistoryEntity(projectId = 1, text = "entry 104", createdAt = 42))
            dao.saveRecent(TextInputHistoryEntity(projectId = 2, text = "  multiline\n😀  ", createdAt = 42))
            val recent = dao.getRecentForProject(1).first()
            assertEquals(100, recent.size)
            assertEquals("entry 104", recent.first().text)
            assertEquals("entry 5", recent.last().text)
            assertEquals("  multiline\n😀  ", dao.getRecentForProject(2).first().single().text)
            dao.delete(recent.first().id)
            assertEquals(99, dao.getRecentForProject(1).first().size)
        } finally { db.close() }
    }

    @Test fun pinsAreScopedEditableDurableAndIndependentOfHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.pinnedActionDao()
            val projectId = dao.save(PinnedActionEntity(name = "Status", text = "git status", projectId = 1))
            dao.save(PinnedActionEntity(name = "Global", text = "/model", scope = "GLOBAL"))
            dao.save(PinnedActionEntity(name = "Other", text = "other", projectId = 2))
            assertEquals(setOf("Status", "Global"), dao.forProject(1).first().map { it.name }.toSet())
            val original = dao.all().first { it.id == projectId }
            dao.save(original.copy(name = "Changed", scope = "GLOBAL", projectId = null, sendEnter = false))
            dao.markUsed(projectId, 123)
            db.textInputHistoryDao().clearAll()
            val edited = dao.forProject(2).first().first { it.id == projectId }
            assertEquals("Changed", edited.name)
            assertEquals(1L, edited.usageCount)
            assertEquals(123L, edited.lastUsedAt)
            assertFalse(edited.sendEnter)
            dao.delete(projectId)
            assertEquals(2, dao.all().size)
        } finally { db.close() }
    }

    @Test fun migrationFrom9PreservesExistingProjectAndHistory() = runBlocking {
        val name = "pins-migration-${System.nanoTime()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_9_10).build()
        try {
            open().let { db ->
                db.openHelper.writableDatabase.execSQL("INSERT INTO projects (id,serverId,targetType,name,useTmux,customScript,aiCommand,colorSeed,createdAt,gitUrl,lastOpenedAt) VALUES (7,1,'ssh','kept',1,'cd project','',0,123,'',0)")
                db.textInputHistoryDao().saveRecent(TextInputHistoryEntity(projectId = 7, text = "keep me"))
                db.close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("DROP TABLE pinned_actions")
                it.version = 9
            }
            open().let { db ->
                try {
                    assertEquals("keep me", db.textInputHistoryDao().getRecentForProject(7).first().single().text)
                    db.openHelper.readableDatabase.query("SELECT name FROM projects WHERE id=7").use {
                        assertTrue(it.moveToFirst()); assertEquals("kept", it.getString(0))
                    }
                    db.pinnedActionDao().save(PinnedActionEntity(name = "New", text = "hello", projectId = 7))
                } finally { db.close() }
            }
            open().let { db ->
                try { assertEquals("New", db.pinnedActionDao().forProject(7).first().single().name) }
                finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun pinBackupPreservesContentAndRemapsProjectWithoutLeakingDatabaseIds() {
        val pin = PinnedActionEntity(id = 99, name = "A \"name\"", text = "line\n\"quotes\" \\ tab\t😀\r\n", projectId = 1, sendEnter = false)
        val json = encodePinnedActions(listOf(pin))
        val yamlScalar = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val restored = decodePinnedActions(decodeYamlScalar(yamlScalar), 27).single()
        assertEquals(pin.name, restored.name)
        assertEquals(pin.text, restored.text)
        assertEquals(27L, restored.projectId)
        assertEquals(0L, restored.id)
        assertFalse(restored.sendEnter)
        val global = decodePinnedActions(encodePinnedActions(listOf(pin))).single()
        assertEquals("GLOBAL", global.scope)
        assertNull(global.projectId)
    }
}
