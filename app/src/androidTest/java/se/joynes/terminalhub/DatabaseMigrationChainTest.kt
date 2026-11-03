package se.joynes.terminalhub

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.db.*
import se.joynes.terminalhub.data.db.entity.ServerEntity

class DatabaseMigrationChainTest {
    @Test fun originalInstallationMigratesThroughEveryVersionWithoutLosingProjects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE servers (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE projects (id INTEGER PRIMARY KEY, serverId INTEGER NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, projectPath TEXT, sessionName TEXT, setupScript TEXT)")
                    db.execSQL("INSERT INTO servers VALUES (7, 'Original server')")
                    db.execSQL("INSERT INTO projects VALUES (10, 7, 'first', 123, '/old', 'old', 'old script')")
                    db.execSQL("INSERT INTO projects VALUES (11, 7, 'second', 456, '/other', 'other', NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            val chain = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            chain.forEach { migration ->
                migration.migrate(db)
                if (migration.endVersion == 10) db.execSQL("INSERT INTO pinned_actions VALUES (1, 'Existing pin', 'pwd', 'PROJECT', 10, 100, 100, 3, NULL, 1)")
            }
            db.query("SELECT * FROM projects ORDER BY id").use { rows ->
                assertEquals(2, rows.count)
                assertTrue(rows.moveToFirst())
                fun string(name: String) = rows.getString(rows.getColumnIndexOrThrow(name))
                fun long(name: String) = rows.getLong(rows.getColumnIndexOrThrow(name))
                assertEquals(10L, long("id")); assertEquals(7L, long("serverId")); assertEquals("first", string("name"))
                assertEquals(123L, long("createdAt")); assertEquals(1L, long("useTmux"))
                assertEquals("cd {{PROJECT_PATH}}", string("customScript")); assertEquals("", string("aiCommand"))
                assertEquals("", string("gitUrl")); assertEquals("ssh", string("targetType"))
                assertEquals(0L, long("lastOpenedAt")); assertEquals("", string("displayName"))
                val firstColor = long("colorSeed"); assertTrue(firstColor != 0L)
                assertTrue(rows.moveToNext()); assertEquals("second", string("name"))
                assertTrue(firstColor != long("colorSeed"))
                assertEquals(-1, rows.getColumnIndex("projectPath")); assertEquals(-1, rows.getColumnIndex("setupScript"))
            }
            db.query("SELECT projectsFolder, setupScript FROM servers WHERE id=7").use {
                assertTrue(it.moveToFirst()); assertEquals("~/terminalhub", it.getString(0))
                assertEquals(ServerEntity.DEFAULT_SETUP_SCRIPT, it.getString(1))
            }
            db.query("SELECT name, text, usageCount, macroJson FROM pinned_actions").use {
                assertTrue(it.moveToFirst()); assertEquals("Existing pin", it.getString(0)); assertEquals("pwd", it.getString(1))
                assertEquals(3, it.getInt(2)); assertEquals("", it.getString(3))
            }
            db.execSQL("INSERT INTO text_input_history (projectId,text,createdAt) VALUES (10,'history',123)")
            db.execSQL("INSERT INTO project_notes (projectId,text,localUpdatedAt,dirty,pendingDelete) VALUES (10,'note',123,0,0)")
            db.query("SELECT text FROM project_notes WHERE projectId=10").use { assertTrue(it.moveToFirst()); assertEquals("note", it.getString(0)) }
        } finally { helper.close() }
    }
}
