package se.joynes.aiterminalhub.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import se.joynes.aiterminalhub.data.db.entity.ServerEntity
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.data.repository.ProjectRepository
import se.joynes.aiterminalhub.data.repository.ServerRepository
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val servers: Int, val projects: Int)

@Singleton
class ExportImportManager @Inject constructor(
    private val serverRepo: ServerRepository,
    private val projectRepo: ProjectRepository
) {
    suspend fun exportYaml(context: Context, uri: Uri) {
        val servers = serverRepo.getAll().first()
        val opts = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
        }
        val data = linkedMapOf(
            "version" to 1,
            "servers" to servers.map { server ->
                val projects = projectRepo.getByServer(server.id).first()
                linkedMapOf(
                    "name" to server.name,
                    "host" to server.host,
                    "port" to server.port,
                    "username" to server.username,
                    "authType" to server.authType,
                    "keyAlias" to (server.keyAlias ?: ""),
                    "projectsFolder" to server.projectsFolder,
                    "setupScript" to server.setupScript,
                    "projects" to projects.map { project ->
                        linkedMapOf(
                            "name" to project.name,
                            "useTmux" to project.useTmux,
                            "customScript" to project.customScript,
                            "aiCommand" to project.aiCommand,
                            "colorSeed" to project.colorSeed,
                            "gitUrl" to project.gitUrl
                        )
                    }
                )
            }
        )
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.writer().use { Yaml(opts).dump(data, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun importYaml(context: Context, uri: Uri): ImportResult {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.reader().readText() }
            ?: error("Cannot read file")

        val root = Yaml(SafeConstructor()).load<Map<String, Any>>(text)
        val serversList = root["servers"] as? List<Map<String, Any>> ?: emptyList()

        var serversImported = 0
        var projectsImported = 0

        for (serverMap in serversList) {
            val server = Server(
                id = 0,
                name = serverMap["name"] as? String ?: "",
                host = serverMap["host"] as? String ?: "",
                port = (serverMap["port"] as? Int) ?: 22,
                username = serverMap["username"] as? String ?: "",
                authType = serverMap["authType"] as? String ?: "password",
                keyAlias = (serverMap["keyAlias"] as? String)?.ifBlank { null },
                projectsFolder = serverMap["projectsFolder"] as? String ?: "~/aiterminalhub",
                setupScript = serverMap["setupScript"] as? String ?: ServerEntity.DEFAULT_SETUP_SCRIPT
            )
            val serverId = serverRepo.save(server)
            serversImported++

            val projectsList = serverMap["projects"] as? List<Map<String, Any>> ?: emptyList()
            for (projectMap in projectsList) {
                val project = Project(
                    id = 0,
                    serverId = serverId,
                    name = projectMap["name"] as? String ?: "",
                    useTmux = projectMap["useTmux"] as? Boolean ?: true,
                    customScript = projectMap["customScript"] as? String ?: "cd {{PROJECT_PATH}}",
                    aiCommand = projectMap["aiCommand"] as? String ?: "",
                    colorSeed = (projectMap["colorSeed"] as? Int) ?: 0,
                    gitUrl = projectMap["gitUrl"] as? String ?: ""
                )
                projectRepo.save(project)
                projectsImported++
            }
        }
        return ImportResult(serversImported, projectsImported)
    }
}
