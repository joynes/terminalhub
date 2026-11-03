package se.joynes.terminalhub

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.entity.*
import se.joynes.terminalhub.data.export.ExportImportManager
import se.joynes.terminalhub.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.settings.AppSettings
import se.joynes.terminalhub.data.settings.AppSettingsRepository

/** Round trips exercise the public document API, real Room transactions and preferences. */
@HiltAndroidTest
class ConfigurationBackupTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var backup: ExportImportManager
    @Inject lateinit var credentials: SecurePrefsManager
    @Inject lateinit var settings: AppSettingsRepository
    private lateinit var file: File
    private lateinit var original: AppSettings

    @Before fun setup() {
        hilt.inject()
        original = settings.settings.value
        db.clearAllTables()
        file = File.createTempFile("configuration-coverage", ".yaml", context.cacheDir)
    }
    @After fun cleanup() {
        settings.applySettingsImport(settings.settings.value, original)
        db.clearAllTables(); credentials.clearAll(); file.delete()
    }
    private fun scalar(value: String) = JSONObject.quote(value)
    private fun writeLocal(item: JSONObject) {
        file.writeText("version: 2\nsettings:\n  localProjects: ${scalar(JSONArray().put(item).toString())}\nservers:\n")
    }
    private suspend fun assertRejectedWithoutReplacingExistingData(text: String) {
        val server = db.serverDao().insert(ServerEntity(name = "Keep server", host = "example.invalid", username = "user"))
        val project = db.projectDao().insert(ProjectEntity(serverId = server, name = "keep-project"))
        credentials.savePassword(server, "fixture-secret")
        file.writeText(text)
        assertTrue("Malformed configuration must be rejected", runCatching { backup.importYaml(context, Uri.fromFile(file)) }.isFailure)
        assertEquals("keep-project", db.projectDao().getAll().first().single().name)
        assertEquals(project, db.projectDao().getAll().first().single().id)
        assertEquals("fixture-secret", credentials.getPassword(server))
    }

    @Test fun fullRoundTripPreservesUnicodeEscapesServerFieldsAndLocalNotesButOmitsSecrets() = runBlocking {
        val server = db.serverDao().insert(ServerEntity(name = "Å server", host = "example.invalid", port = 2222,
            username = "user", authType = "key", keyAlias = "ssh-fixture", projectsFolder = "~/my projects",
            setupScript = "printf 'ready\\n'\n# next\t\"quoted\""))
        db.serverDao().insert(ServerEntity(name = "Empty", host = "empty.invalid", username = "other"))
        val remote = db.projectDao().insert(ProjectEntity(serverId = server, name = "remote", displayName = "Webb 😀",
            useTmux = false, customScript = "echo \"å\"\ncd {{PROJECT_PATH}}", aiCommand = "ai-client", colorSeed = 42, gitUrl = "ssh://example.invalid/repo"))
        val local = db.projectDao().insert(ProjectEntity(serverId = LOCAL_PROJECT_SERVER_ID, targetType = "local", name = "local",
            displayName = "Lokalt", useTmux = false, customScript = "pwd", aiCommand = "local-ai", colorSeed = 19, gitUrl = "https://example.invalid/local"))
        db.projectNoteDao().save(ProjectNoteEntity(local, "Anteckning\n😀"))
        db.pinnedActionDao().save(PinnedActionEntity(name = "Local pin", text = "ls", scope = "PROJECT", projectId = local, sendEnter = false))
        db.pinnedActionDao().save(PinnedActionEntity(name = "Remote pin", text = "pwd", scope = "PROJECT", projectId = remote))
        credentials.savePassword(server, "never-export-this-secret")
        backup.exportYaml(context, Uri.fromFile(file), includeLocalNotes = true)
        assertFalse(file.readText().contains("never-export-this-secret"))
        val result = backup.importYaml(context, Uri.fromFile(file))
        assertEquals(2, result.servers); assertEquals(2, result.projects)
        val restoredServer = db.serverDao().getAll().first().single { it.name == "Å server" }
        assertEquals(2222, restoredServer.port); assertEquals("key", restoredServer.authType)
        assertEquals("ssh-fixture", restoredServer.keyAlias); assertEquals("~/my projects", restoredServer.projectsFolder)
        assertEquals("printf 'ready\\n'\n# next\t\"quoted\"", restoredServer.setupScript)
        val projects = db.projectDao().getAll().first()
        val r = projects.single { it.name == "remote" }
        assertEquals(restoredServer.id, r.serverId); assertEquals("Webb 😀", r.displayName)
        assertFalse(r.useTmux); assertEquals(42, r.colorSeed); assertEquals("ai-client", r.aiCommand)
        assertEquals("echo \"å\"\ncd {{PROJECT_PATH}}", r.customScript); assertEquals("ssh://example.invalid/repo", r.gitUrl)
        val l = projects.single { it.name == "local" }
        assertEquals("local", l.targetType); assertEquals("Lokalt", l.displayName)
        assertEquals("local-ai", l.aiCommand); assertEquals(19, l.colorSeed); assertFalse(l.useTmux)
        assertEquals("https://example.invalid/local", l.gitUrl)
        assertEquals("Anteckning\n😀", db.projectNoteDao().get(l.id)?.text)
        val pins = db.pinnedActionDao().all()
        assertEquals(l.id, pins.single { it.name == "Local pin" }.projectId)
        assertFalse(pins.single { it.name == "Local pin" }.sendEnter)
        assertEquals(r.id, pins.single { it.name == "Remote pin" }.projectId)
        assertNull(credentials.getPassword(restoredServer.id))
    }

    @Test fun exportingSelectedProjectsFiltersBothRemoteAndLocalProjectsAndTheirPins() = runBlocking {
        val server = db.serverDao().insert(ServerEntity(name = "Server", host = "example.invalid", username = "user"))
        val selected = db.projectDao().insert(ProjectEntity(serverId = server, name = "selected"))
        db.projectDao().insert(ProjectEntity(serverId = server, name = "unselected"))
        val local = db.projectDao().insert(ProjectEntity(serverId = LOCAL_PROJECT_SERVER_ID, targetType = "local", name = "local-selected"))
        val omitted = db.projectDao().insert(ProjectEntity(serverId = LOCAL_PROJECT_SERVER_ID, targetType = "local", name = "local-omitted"))
        db.projectNoteDao().save(ProjectNoteEntity(local, "note-must-stay-private"))
        db.pinnedActionDao().save(PinnedActionEntity(name = "Omitted pin", text = "pwd", scope = "PROJECT", projectId = omitted))
        backup.exportYaml(context, Uri.fromFile(file), setOf(selected, local))
        val yaml = file.readText()
        assertFalse(yaml.contains("unselected")); assertFalse(yaml.contains("local-omitted"))
        assertFalse(yaml.contains("Omitted pin")); assertFalse(yaml.contains("note-must-stay-private"))
        backup.importYaml(context, Uri.fromFile(file))
        assertEquals(setOf("selected", "local-selected"), db.projectDao().getAll().first().map { it.name }.toSet())
    }

    @Test fun legacyBackupSuppliesDefaultsAndKeepsPreferencesThatAreNotPresent() = runBlocking {
        settings.setKeyBarHighlightIntensity(.63f); settings.setTextInputPanelOpacity(.72f)
        file.writeText("""
            # legacy configuration
            version: 1
            servers:
            - name: Old
              host: old.invalid
              username: user
              port: invalid
              ignored line without colon
              projects:
              - name: legacy
        """.trimIndent())
        val result = backup.importYaml(context, Uri.fromFile(file))
        assertEquals(1, result.servers); assertEquals(1, result.projects)
        val server = db.serverDao().getAll().first().single()
        assertEquals(22, server.port); assertEquals("password", server.authType)
        assertNull(server.keyAlias); assertEquals("~/terminalhub", server.projectsFolder)
        val project = db.projectDao().getAll().first().single()
        assertTrue(project.useTmux); assertEquals("cd {{PROJECT_PATH}}", project.customScript)
        assertEquals("", project.aiCommand); assertEquals(0, project.colorSeed)
        assertEquals(.63f, settings.settings.value.keyBarHighlightIntensity)
        assertEquals(.72f, settings.settings.value.textInputPanelOpacity)
    }

    @Test fun invalidLocalNameIsRejectedBeforeDeletingExistingConfiguration() = runBlocking {
        writeLocal(JSONObject().put("name", "../escape"))
        assertRejectedWithoutReplacingExistingData(file.readText())
    }
    @Test fun invalidLocalTitleIsRejectedBeforeDeletingExistingConfiguration() = runBlocking {
        writeLocal(JSONObject().put("name", "valid").put("displayName", "bad\u0000title"))
        assertRejectedWithoutReplacingExistingData(file.readText())
    }
    @Test fun oversizedUtf8NoteIsRejectedBeforeDeletingExistingConfiguration() = runBlocking {
        writeLocal(JSONObject().put("name", "valid").put("note", "😀".repeat(65537)))
        assertRejectedWithoutReplacingExistingData(file.readText())
    }
    @Test fun malformedGlobalPinIsRejectedBeforeDeletingExistingConfiguration() = runBlocking {
        val pins = JSONArray().put(JSONObject().put("name", "").put("text", "pwd").put("sendEnter", true))
        assertRejectedWithoutReplacingExistingData("settings:\n  globalPinnedActions: ${scalar(pins.toString())}\nservers:\n")
    }
    @Test fun malformedRemoteMacroIsRejectedBeforeDeletingExistingConfiguration() = runBlocking {
        val pins = JSONArray().put(JSONObject().put("name", "Macro").put("text", "summary").put("macro", JSONObject().put("version", 999)))
        assertRejectedWithoutReplacingExistingData("servers:\n- name: New\n  host: new.invalid\n  projects:\n  - name: demo\n    pinnedActions: ${scalar(pins.toString())}\n")
    }
    @Test fun settingsOnlyDocumentIsRejectedBeforeDeletingExistingConfiguration() = runBlocking {
        assertRejectedWithoutReplacingExistingData("format: terminalhub-settings\nversion: 1\nsettings:\n  preferFastResume: false\n")
    }
}
