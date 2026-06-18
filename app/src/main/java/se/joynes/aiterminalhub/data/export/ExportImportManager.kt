package se.joynes.aiterminalhub.data.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import se.joynes.aiterminalhub.data.db.AppDatabase
import se.joynes.aiterminalhub.data.db.dao.TextInputHistoryDao
import se.joynes.aiterminalhub.data.db.entity.ServerEntity
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.runtime.AppRuntimeRepository
import se.joynes.aiterminalhub.data.security.SecurePrefsManager
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import se.joynes.aiterminalhub.domain.TerminalSessionManager
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
    private val runtimeRepository: AppRuntimeRepository,
    private val sessionManager: TerminalSessionManager
) {
    // ── Serialization ───────────────────────────────────────────────────────

    suspend fun exportYaml(context: Context, uri: Uri) {
        val servers = serverRepo.getAll().first()
        val yaml = buildYaml(servers) { server ->
            projectRepo.getByServer(server.id).first()
        }
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("Cannot open export destination")
        output.use { it.write(yaml.toByteArray(StandardCharsets.UTF_8)) }
    }

    private suspend fun buildYaml(
        servers: List<Server>,
        projectsForServer: suspend (Server) -> List<Project>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("version: 1")
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
        var serversImported = 0
        var projectsImported = 0

        sessionManager.clearForConfigImport()
        runtimeRepository.clearSessionState()
        securePrefsManager.clearAll()
        appContext.getSharedPreferences("session_host", Context.MODE_PRIVATE).edit().clear().apply()

        db.withTransaction {
            textInputHistoryDao.clearAll()
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
                    projectsFolder = serverMap["projectsFolder"] ?: "~/aiterminalhub",
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
                    projectRepo.save(project)
                    projectsImported++
                }
            }
        }
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
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            return s.substring(1, s.length - 1)
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return s
    }
}
