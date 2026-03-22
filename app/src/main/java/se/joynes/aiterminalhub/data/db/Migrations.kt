package se.joynes.aiterminalhub.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import se.joynes.aiterminalhub.data.db.entity.ServerEntity

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add per-project setup script override (NULL = use server default)
        db.execSQL("ALTER TABLE projects ADD COLUMN setupScript TEXT DEFAULT NULL")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add new columns to servers (SQLite supports ADD COLUMN)
        val defaultScript = ServerEntity.DEFAULT_SETUP_SCRIPT
            .replace("'", "''") // escape single quotes for SQL
        db.execSQL("ALTER TABLE servers ADD COLUMN projectsFolder TEXT NOT NULL DEFAULT '~/aiterminalhub'")
        db.execSQL("ALTER TABLE servers ADD COLUMN setupScript TEXT NOT NULL DEFAULT '$defaultScript'")

        // Recreate projects table without projectPath, sessionName, setupScript
        db.execSQL("""
            CREATE TABLE projects_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                serverId INTEGER NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("INSERT INTO projects_new (id, serverId, name, createdAt) SELECT id, serverId, name, createdAt FROM projects")
        db.execSQL("DROP TABLE projects")
        db.execSQL("ALTER TABLE projects_new RENAME TO projects")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite ALTER TABLE only allows constant defaults — add with 0 then assign
        // a unique seed per project using Knuth's multiplicative hash on the row id.
        db.execSQL("ALTER TABLE projects ADD COLUMN colorSeed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE projects SET colorSeed = ABS(id * 2246822519 + 1103515245)")
    }
}
