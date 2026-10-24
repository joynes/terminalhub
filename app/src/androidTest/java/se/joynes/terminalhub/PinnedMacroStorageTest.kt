package se.joynes.terminalhub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.db.*
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.export.*
import se.joynes.terminalhub.data.model.PinnedMacroCodec
import se.joynes.terminalhub.domain.*

/** Constructs the actual v11 table, so upgrade tests do not accidentally retain v12 columns. */
internal fun restoreV11PinnedTable(db: SQLiteDatabase) {
    val columns = "id,name,text,scope,projectId,createdAt,updatedAt,usageCount,lastUsedAt,sendEnter"
    db.execSQL("ALTER TABLE pinned_actions RENAME TO pins_fixture_v12")
    db.execSQL("CREATE TABLE pinned_actions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, text TEXT NOT NULL, scope TEXT NOT NULL, projectId INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, usageCount INTEGER NOT NULL, lastUsedAt INTEGER, sendEnter INTEGER NOT NULL)")
    db.execSQL("INSERT INTO pinned_actions ($columns) SELECT $columns FROM pins_fixture_v12")
    db.execSQL("DROP TABLE pins_fixture_v12")
}

@RunWith(AndroidJUnit4::class)
class PinnedMacroStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun upgradeFrom11PreservesPlainPinsAndMacroSurvivesReopeningDatabase() = runBlocking {
        val name = "macro-migration-${System.nanoTime()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_11_12).build()
        try {
            open().let { db ->
                db.pinnedActionDao().save(PinnedActionEntity(name = "Existing", text = "git status", scope = "GLOBAL", usageCount = 7))
                db.close()
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
                restoreV11PinnedTable(it); it.version = 11
            }
            val steps = TerminalMacro.interruptAndSend("echo 'å😀'\n")
            open().let { db ->
                val original = db.pinnedActionDao().all().single()
                assertEquals("git status", original.text); assertFalse(original.isMacro); assertEquals(7L, original.usageCount)
                db.pinnedActionDao().save(PinnedActionEntity(name = "Macro", text = TerminalMacro.summary(steps), scope = "GLOBAL", sendEnter = false,
                    macroJson = PinnedMacroCodec.encode(steps)))
                db.close()
            }
            open().let { db ->
                assertEquals(steps, PinnedMacroCodec.decode(db.pinnedActionDao().all().first { it.isMacro }.macroJson))
                assertEquals(2, db.pinnedActionDao().all().size)
                db.close()
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun macroBackupRestoresStepsAndScopeWhileLegacyBackupsStillImport() {
        val steps = listOf(MacroStep.Key("/", ctrl = true), MacroStep.Text("  å😀\n\"quoted\" \\ "), MacroStep.Pause(700), MacroStep.Key("TAB", shift = true, repeat = 3))
        val macro = PinnedActionEntity(name = "Macro", text = TerminalMacro.summary(steps), scope = "GLOBAL", sendEnter = false, macroJson = PinnedMacroCodec.encode(steps))
        val encoded = encodePinnedActions(listOf(macro))
        val project = decodePinnedActions(encoded, 88).single()
        assertTrue(project.isMacro); assertFalse(project.sendEnter)
        assertEquals(88L, project.projectId); assertEquals("PROJECT", project.scope)
        assertEquals(steps, PinnedMacroCodec.decode(project.macroJson))
        val global = decodePinnedActions(encoded).single()
        assertNull(global.projectId); assertEquals("GLOBAL", global.scope)
        val legacy = decodePinnedActions("""[{"name":"Legacy","text":"git status","sendEnter":true}]""").single()
        assertFalse(legacy.isMacro); assertTrue(legacy.sendEnter)
    }

    @Test fun malformedMacroAndFutureVersionsAreRejectedBeforeBackupImport() {
        val broken = listOf("""{"version":2,"steps":[]}""", """{"version":1,"steps":[{"type":"pause","milliseconds":-1}]}""",
            """{"version":1,"steps":[{"type":"execute","text":"never"}]}""")
        broken.forEach { payload ->
            val json = """[{"name":"Bad","text":"summary","sendEnter":false,"macro":$payload}]"""
            assertTrue(runCatching { decodePinnedActions(json) }.isFailure)
        }
    }
}
