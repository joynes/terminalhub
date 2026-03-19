package se.joynes.aiterminalhub.domain

import se.joynes.aiterminalhub.data.db.entity.ServerEntity
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.Server
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptTemplateEngine @Inject constructor() {

    fun projectPath(server: Server, project: Project): String =
        "${server.projectsFolder}/${project.name}"

    fun sessionName(project: Project): String =
        project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun render(server: Server, project: Project): String {
        val template = project.setupScript ?: server.setupScript
        return render(template, server, project)
    }

    /** Returns the shell command to send after setup (e.g. `tmux attach -t name`).
     *  Empty string means no attach command (plain shell). */
    fun renderAttach(server: Server, project: Project): String {
        if (project.setupScript != null) return "" // custom or empty script — no auto-attach
        return render(ServerEntity.DEFAULT_ATTACH_COMMAND, server, project)
    }

    fun render(template: String, server: Server, project: Project): String {
        val path = projectPath(server, project)
        val session = sessionName(project)
        return template
            .replace("{{PROJECT_NAME}}", project.name)
            .replace("{{PROJECT_PATH}}", path)
            .replace("{{SESSION_NAME}}", session)
    }
}
