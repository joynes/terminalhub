package se.joynes.terminalhub.data.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.terminalhub.data.db.AppDatabase
import se.joynes.terminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.terminalhub.data.db.entity.ServerEntity
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.ProjectTargetType
import se.joynes.terminalhub.data.model.LOCAL_PROJECT_SERVER_ID
import se.joynes.terminalhub.data.model.projectNameValidationError
import se.joynes.terminalhub.data.db.entity.ProjectNoteEntity
import se.joynes.terminalhub.data.notes.ProjectNotesRepository
import se.joynes.terminalhub.data.notes.MAX_NOTE_BYTES
import org.json.JSONArray
import org.json.JSONObject
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.runtime.AppRuntimeRepository
import se.joynes.terminalhub.data.security.SecurePrefsManager
import se.joynes.terminalhub.data.repository.ProjectRepository
import se.joynes.terminalhub.data.repository.ServerRepository
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.settings.KeyBarLayoutConfig
import se.joynes.terminalhub.data.settings.normalizeKeyBarHighlightIntensity
import se.joynes.terminalhub.data.settings.normalizeTextInputPanelOpacitySetting
import se.joynes.terminalhub.domain.TerminalSessionManager
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val servers: Int, val projects: Int)

@Singleton
class ExportImportManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: AppDatabase,
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository,
    private val textInputHistoryDao: TextInputHistoryDao,
    private val securePrefsManager: SecurePrefsManager,
    private val settingsRepository: AppSettingsRepository,
    private val runtimeRepository: AppRuntimeRepository,
    private val sessionManager: TerminalSessionManager,
    private val notesRepository: ProjectNotesRepository
) {
    // ── Serialization ───────────────────────────────────────────────────────

    suspend fun exportYaml(context: Context, uri: Uri, activeProjectIds: Set<Long>? = null, includeLocalNotes: Boolean = false) {
        notesRepository.flush()
        val servers = serverRepo.getAll().first()
        val locals = projectRepo.getAll().first().filter { it.isLocal && (activeProjectIds == null || it.id in activeProjectIds) }
        val yaml = buildYaml(servers, locals, includeLocalNotes) { server ->
            val projects = projectRepo.getByServer(server.id).first()
            activeProjectIds?.let { ids -> projects.filter { it.id in ids } } ?: projects
        }
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open export destination")
        output.use { it.write(yaml.toByteArray(StandardCharsets.UTF_8)) }
    }

    private suspend fun buildYaml(
        servers: List<Server>,
        localProjects: List<Project>,
        includeLocalNotes: Boolean,
        projectsForServer: suspend (Server) -> List<Project>
    ): String {
        val sb = StringBuilder()
        val pinnedActions = db.pinnedActionDao().all()
        sb.appendLine("version: 2")
        sb.appendLine("settings:")
        sb.appendLine("  keyBarLayout: ${ys(KeyBarLayoutConfig.encode(settingsRepository.settings.value.keyBarRows))}")
        sb.appendLine("  keyBarHighlights: ${ys(KeyBarLayoutConfig.encodeHighlights(settingsRepository.settings.value.keyBarHighlightedKeyIds))}")
        sb.appendLine("  keyBarHighlightIntensity: ${settingsRepository.settings.value.keyBarHighlightIntensity}")
        sb.appendLine("  textInputPanelOpacity: ${settingsRepository.settings.value.textInputPanelOpacity}")
        sb.appendLine("  globalPinnedActions: ${ys(encodePinnedActions(pinnedActions.filter { it.scope == "GLOBAL" }))}")
        val locals = JSONArray()
        for (project in localProjects) {
            val item = JSONObject().put("name", project.name).put("customScript", project.customScript)
                .put("aiCommand", project.aiCommand).put("colorSeed", project.colorSeed)
                .put("gitUrl", project.gitUrl).put("useTmux", project.useTmux)
                .put("pinnedActions", encodePinnedActions(pinnedActions.filter { it.scope == "PROJECT" && it.projectId == project.id }))
            if (includeLocalNotes) db.projectNoteDao().get(project.id)?.let { item.put("note", it.text) }
            locals.put(item)
        }
        sb.appendLine("  localProjects: ${ys(locals.toString())}")
        sb.appendLine("servers:")
        for (server in servers) {
            val projects = projectsForServer(server)
            sb.appendLine("- name: ${ys(server.name)}")
            sb.appendLine("  host: ${ys(server.host)}")
            sb.appendLine("  port: ${server.port}")
            sb.appendLine("  username: ${ys(server.username)}")
            sb.appendLine("  authType: ${ys(server.authType)}")
            sb.appendLine("  keyAlias: ${ys(server.keyAlias ?: "")}")
            sb.appendLine("  projectsFolder: ${ys(server.projectsFolder)}")
            sb.appendLine("  setupScript: ${ys(server.setupScript)}")
            if (projects.isEmpty()) {
                sb.appendLine("  projects: []")
            } else {
                sb.appendLine("  projects:")
                for (project in projects) {
                    sb.appendLine("  - name: ${ys(project.name)}")
                    sb.appendLine("    useTmux: ${project.useTmux}")
                    sb.appendLine("    customScript: ${ys(project.customScript)}")
                    sb.appendLine("    aiCommand: ${ys(project.aiCommand)}")
                    sb.appendLine("    colorSeed: ${project.colorSeed}")
                    sb.appendLine("    gitUrl: ${ys(project.gitUrl)}")
                    sb.appendLine("    pinnedActions: ${ys(encodePinnedActions(pinnedActions.filter { it.scope == "PROJECT" && it.projectId == project.id }))}")
                }
            }
        }
        return sb.toString()
    }

    /** Double-quote a string value with escape sequences for newlines, quotes, backslashes. */
    private fun ys(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // ── Deserialization ─────────────────────────────────────────────────────

    suspend fun importYaml(context: Context, uri: Uri): ImportResult {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.reader().readText() }
            ?: error("Cannot read file")

        val servers = parseYaml(text)
        val localProjects = JSONArray(extractSettingsValue(text, "localProjects") ?: "[]")
        for (index in 0 until localProjects.length()) {
            val local = localProjects.getJSONObject(index)
            require(projectNameValidationError(local.getString("name")) == null) { "Invalid local project name" }
            require(local.optString("note").toByteArray(Charsets.UTF_8).size <= MAX_NOTE_BYTES) { "Note exceeds 256 KB" }
            decodePinnedActions(local.optString("pinnedActions", "[]"), 0L)
        }
        // Validate every pin before the existing replace-configuration operation starts.
        val globalPins = decodePinnedActions(extractSettingsValue(text, "globalPinnedActions"))
        servers.forEach { server ->
            parseProjectsBlock(server["__projects__"] ?: "").forEach { project ->
                decodePinnedActions(project["pinnedActions"], 0L)
            }
        }
        // Older backups may omit individual settings. Keep each current value in that case
        // instead of silently resetting it during import.
        val keyBarRows = extractKeyBarLayoutFromYaml(text) ?: settingsRepository.settings.value.keyBarRows
        val keyBarHighlightedKeyIds = extractKeyBarHighlightsFromYaml(text)
            ?: settingsRepository.settings.value.keyBarHighlightedKeyIds
        val keyBarHighlightIntensity = extractKeyBarHighlightIntensityFromYaml(text)
            ?: settingsRepository.settings.value.keyBarHighlightIntensity
        val textInputPanelOpacity = extractTextInputPanelOpacityFromYaml(text)
            ?: settingsRepository.settings.value.textInputPanelOpacity
        var serversImported = 0
        var projectsImported = 0

        // Consent belongs to this device and is never imported, even from an edited backup.
        settingsRepository.setInputHistoryEnabled(false)
        notesRepository.resetForImport()
        sessionManager.clearForConfigImport()
        runtimeRepository.clearSessionState()
        securePrefsManager.clearAll()
        appContext.getSharedPreferences("session_host", Context.MODE_PRIVATE).edit().clear().apply()

        db.withTransaction {
            db.projectNoteDao().clearAll()
            textInputHistoryDao.clearAll()
            db.pinnedActionDao().clearAll()
            globalPins.forEach { db.pinnedActionDao().save(it) }
            projectRepo.clearAll()
            serverRepo.clearAll()

            for (serverMap in servers) {
                val server = Server(
                    id = 0,
                    name = serverMap["name"] ?: "",
                    host = serverMap["host"] ?: "",
                    port = serverMap["port"]?.toIntOrNull() ?: 22,
                    username = serverMap["username"] ?: "",
                    authType = serverMap["authType"] ?: "password",
                    keyAlias = serverMap["keyAlias"]?.ifBlank { null },
                    projectsFolder = serverMap["projectsFolder"] ?: "~/terminalhub",
                    setupScript = serverMap["setupScript"] ?: ServerEntity.DEFAULT_SETUP_SCRIPT
                )
                val serverId = serverRepo.save(server)
                serversImported++

                val projectsRaw = serverMap["__projects__"] ?: ""
                val projectMaps = parseProjectsBlock(projectsRaw)
                for (projectMap in projectMaps) {
                    val project = Project(
                        id = 0,
                        serverId = serverId,
                        name = projectMap["name"] ?: "",
                        useTmux = projectMap["useTmux"] != "false",
                        customScript = projectMap["customScript"] ?: "cd {{PROJECT_PATH}}",
                        aiCommand = projectMap["aiCommand"] ?: "",
                        colorSeed = projectMap["colorSeed"]?.toIntOrNull() ?: 0,
                        gitUrl = projectMap["gitUrl"] ?: ""
                    )
                    val projectId = projectRepo.save(project)
                    decodePinnedActions(projectMap["pinnedActions"], projectId).forEach {
                        db.pinnedActionDao().save(it)
                    }
                    projectsImported++
                }
            }
            for (index in 0 until localProjects.length()) {
                val item = localProjects.getJSONObject(index)
                val id = projectRepo.save(Project(serverId = LOCAL_PROJECT_SERVER_ID, targetType = ProjectTargetType.LOCAL,
                    name = item.getString("name"), customScript = item.optString("customScript", "cd {{PROJECT_PATH}}"),
                    aiCommand = item.optString("aiCommand"), colorSeed = item.optInt("colorSeed"),
                    gitUrl = item.optString("gitUrl"), useTmux = item.optBoolean("useTmux", true)))
                if (item.has("note")) db.projectNoteDao().save(ProjectNoteEntity(id, item.getString("note"), System.currentTimeMillis()))
                decodePinnedActions(item.optString("pinnedActions", "[]"), id).forEach { db.pinnedActionDao().save(it) }
                projectsImported++
            }
        }
        settingsRepository.setKeyBarRows(keyBarRows)
        settingsRepository.setKeyBarHighlightedKeyIds(keyBarHighlightedKeyIds)
        settingsRepository.setKeyBarHighlightIntensity(keyBarHighlightIntensity)
        settingsRepository.setTextInputPanelOpacity(textInputPanelOpacity)
        return ImportResult(serversImported, projectsImported)
    }

    /**
     * Minimal line-by-line parser for our fixed YAML schema.
     * Returns a list of server maps; each has string keys + "__projects__" containing
     * a raw block of project lines (re-parsed by parseProjectsBlock).
     */
    private fun parseYaml(text: String): List<Map<String, String>> {
        val servers = mutableListOf<MutableMap<String, String>>()
        var currentServer: MutableMap<String, String>? = null
        val projectLines = StringBuilder()
        var inProjectsBlock = false

        for (rawLine in text.lines()) {
            val indent = rawLine.length - rawLine.trimStart().length
            val line = rawLine.trim()

            if (line.isEmpty() || line.startsWith("#")) continue
            if (line == "version: 1" || line.startsWith("version:")) continue
            if (line == "servers:") continue

            when {
                // New server item (0-indent list marker)
                indent == 0 && line.startsWith("- ") -> {
                    currentServer?.let { it["__projects__"] = projectLines.toString() }
                    projectLines.clear()
                    inProjectsBlock = false
                    currentServer = mutableMapOf()
                    servers.add(currentServer)
                    parseKv(line.removePrefix("- "))?.let { (k, v) -> currentServer[k] = v }
                }
                // Server-level field (2-indent)
                indent == 2 && currentServer != null -> {
                    when {
                        line == "projects: []" -> { inProjectsBlock = false }
                        line == "projects:" -> { inProjectsBlock = true }
                        line.startsWith("- ") && inProjectsBlock -> {
                            projectLines.appendLine("- ${line.removePrefix("- ")}")
                        }
                        !inProjectsBlock -> {
                            parseKv(line)?.let { (k, v) -> currentServer?.set(k, v) }
                        }
                        else -> projectLines.appendLine(line)
                    }
                }
                // Project-level field (4-indent)
                indent == 4 && inProjectsBlock -> {
                    projectLines.appendLine(line)
                }
            }
        }
        currentServer?.let { it["__projects__"] = projectLines.toString() }
        return servers
    }

    private fun parseProjectsBlock(block: String): List<Map<String, String>> {
        val projects = mutableListOf<MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        for (rawLine in block.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("- ")) {
                current = mutableMapOf()
                projects.add(current)
                parseKv(line.removePrefix("- "))?.let { (k, v) -> current[k] = v }
            } else {
                parseKv(line)?.let { (k, v) -> current?.set(k, v) }
            }
        }
        return projects
    }

    /** Parse "key: value" — value may be double-quoted with escape sequences. */
    private fun parseKv(line: String): Pair<String, String>? {
        val colonIdx = line.indexOf(':')
        if (colonIdx < 0) return null
        val key = line.substring(0, colonIdx).trim()
        val rawVal = line.substring(colonIdx + 1).trim()
        val value = unquote(rawVal)
        return key to value
    }

    private fun unquote(s: String): String {
        return decodeYamlScalar(s)
    }
}

internal fun extractKeyBarLayoutFromYaml(text: String): List<List<String>>? {
    var inSettings = false
    for (rawLine in text.lines()) {
        val indent = rawLine.length - rawLine.trimStart().length
        val line = rawLine.trim()
        if (indent == 0) {
            inSettings = line == "settings:"
            continue
        }
        if (inSettings && indent == 2 && line.startsWith("keyBarLayout:")) {
            val rawValue = line.substringAfter(':').trim()
            return KeyBarLayoutConfig.decode(decodeYamlScalar(rawValue))
        }
    }
    return null
}

internal fun extractTextInputPanelOpacityFromYaml(text: String): Float? {
    val rawValue = extractSettingsValue(text, "textInputPanelOpacity") ?: return null
    return rawValue.toFloatOrNull()
        ?.let(::normalizeTextInputPanelOpacitySetting)
}

internal fun extractKeyBarHighlightsFromYaml(text: String): Set<String>? {
    val rawValue = extractSettingsValue(text, "keyBarHighlights") ?: return null
    return KeyBarLayoutConfig.decodeHighlights(rawValue)
}

internal fun extractKeyBarHighlightIntensityFromYaml(text: String): Float? {
    val rawValue = extractSettingsValue(text, "keyBarHighlightIntensity") ?: return null
    return rawValue.toFloatOrNull()?.let(::normalizeKeyBarHighlightIntensity)
}

private fun extractSettingsValue(text: String, key: String): String? {
    var inSettings = false
    for (rawLine in text.lines()) {
        val indent = rawLine.length - rawLine.trimStart().length
        val line = rawLine.trim()
        if (indent == 0) {
            inSettings = line == "settings:"
            continue
        }
        if (inSettings && indent == 2 && line.startsWith("$key:")) {
            return decodeYamlScalar(line.substringAfter(':').trim())
        }
    }
    return null
}

internal fun decodeYamlScalar(value: String): String {
    if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
        // Decode once, left to right: chained replacements corrupt literal \\n and nested JSON.
        val body = value.substring(1, value.length - 1)
        return buildString {
            var index = 0
            while (index < body.length) {
                val char = body[index++]
                if (char != '\\' || index == body.length) {
                    append(char)
                } else {
                    when (val escaped = body[index++]) {
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        '"', '\\' -> append(escaped)
                        else -> { append('\\'); append(escaped) }
                    }
                }
            }
        }
    }
    return value
}
