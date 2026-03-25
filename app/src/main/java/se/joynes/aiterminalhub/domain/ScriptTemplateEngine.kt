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

    /** Silent exec: mkdir + create tmux session if useTmux, otherwise just mkdir. */
    fun renderSetup(server: Server, project: Project): String {
        val path = projectPath(server, project)
        val session = sessionName(project)
        return if (project.useTmux) {
            "mkdir -p $path 2>/dev/null; " +
            "(tmux has-session -t $session 2>/dev/null && " +
            "tmux list-panes -t $session -F '#{pane_dead}' 2>/dev/null | grep -q 1 && " +
            "tmux kill-session -t $session 2>/dev/null); " +
            "tmux has-session -t $session 2>/dev/null || " +
            "tmux new-session -d -s $session -c $path; " +
            renderTmuxTouchScrollSetup(session)
        } else {
            "mkdir -p $path 2>/dev/null"
        }
    }

    /** Interactive attach sent to shell after connect. Empty = plain shell. */
    fun renderAttach(server: Server, project: Project): String =
        if (project.useTmux) {
            val session = sessionName(project)
            "${renderTmuxTouchScrollSetup(session)}; ${render(ServerEntity.DEFAULT_ATTACH_COMMAND, server, project)}"
        } else {
            ""
        }

    /** Custom script run inside the session after attach. */
    fun renderCustomScript(server: Server, project: Project): String =
        render(project.customScript, server, project)

    /** AI tool command run last. Empty = none. */
    fun renderAiCommand(project: Project): String = project.aiCommand.trim()

    fun render(template: String, server: Server, project: Project): String {
        val path = projectPath(server, project)
        val session = sessionName(project)
        return template
            .replace("{{PROJECT_NAME}}", project.name)
            .replace("{{PROJECT_PATH}}", path)
            .replace("{{SESSION_NAME}}", session)
    }

    private fun renderTmuxTouchScrollSetup(session: String): String =
        "tmux set-option -t $session mouse on 2>/dev/null || true"
}
