package se.joynes.terminalhub.domain

import se.joynes.terminalhub.data.db.entity.ServerEntity
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptTemplateEngine @Inject constructor() {

    companion object {
        const val GIT_CLONE_FAILED_MARKER = "AITERM_GIT_CLONE_FAILED"
    }

    fun projectPath(server: Server, project: Project): String =
        "${server.projectsFolder}/${project.name}"

    fun localProjectPath(baseDir: String, project: Project): String =
        "$baseDir/${project.name}"

    fun trashProjectPath(server: Server, project: Project, trashKey: String): String =
        "${server.projectsFolder}/.trash/${project.name}-$trashKey"

    fun sessionName(project: Project): String =
        project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Silent exec: mkdir + optional git clone + create tmux session if useTmux. */
    fun renderSetup(server: Server, project: Project): String {
        val path = shellPath(projectPath(server, project))
        val session = sessionName(project)
        val quotedPath = shellQuote(path)
        val dirSetup = if (project.gitUrl.isNotBlank()) {
            val safeUrl = project.gitUrl.replace("'", "'\\''")
            "if [ ! -d $quotedPath/.git ]; then " +
            "if ! git clone --depth 1 '$safeUrl' $quotedPath; then " +
            "echo $GIT_CLONE_FAILED_MARKER; " +
            "mkdir -p $quotedPath 2>/dev/null; " +
            "fi; " +
            "fi; " +
            "mkdir -p $quotedPath 2>/dev/null; "
        } else {
            "mkdir -p $quotedPath 2>/dev/null; "
        }
        return if (project.useTmux) {
            dirSetup +
            "if tmux has-session -t $session 2>/dev/null; then " +
            "if tmux list-panes -t $session -F '#{pane_dead}' 2>/dev/null | grep -q 1; then " +
            "tmux kill-session -t $session 2>/dev/null; " +
            "tmux new-session -d -s $session -c $quotedPath && echo TMUX_SESSION_CREATED; " +
            "else " +
            "echo TMUX_SESSION_EXISTS; " +
            "fi; " +
            "else " +
            "tmux new-session -d -s $session -c $quotedPath && echo TMUX_SESSION_CREATED; " +
            "fi; " +
            renderTmuxTouchScrollSetup(session)
        } else {
            dirSetup.trimEnd()
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

    fun renderLocalCustomScript(baseDir: String, project: Project): String =
        render(project.customScript, localProjectPath(baseDir, project), project)

    fun renderMoveProjectToTrash(server: Server, project: Project, trashKey: String): String {
        val projectPath = shellQuote(shellPath(projectPath(server, project)))
        val trashDir = shellQuote(shellPath("${server.projectsFolder}/.trash"))
        val trashPath = shellQuote(shellPath(trashProjectPath(server, project, trashKey)))
        return "mkdir -p $trashDir 2>/dev/null; " +
            "if [ -e $projectPath ]; then mv $projectPath $trashPath; fi"
    }

    /** AI tool command run last. Empty = none. */
    fun renderAiCommand(project: Project): String = project.aiCommand.trim()

    fun render(template: String, server: Server, project: Project): String {
        val path = shellPath(projectPath(server, project))
        return render(template, path, project)
    }

    fun render(template: String, projectPath: String, project: Project): String {
        val session = sessionName(project)
        return template
            .replace("{{PROJECT_NAME}}", project.name)
            .replace("{{PROJECT_PATH}}", projectPath)
            .replace("{{SESSION_NAME}}", session)
    }

    private fun renderTmuxTouchScrollSetup(session: String): String =
        "tmux set-option -t $session mouse on 2>/dev/null || true"

    private fun shellPath(path: String): String =
        if (path.startsWith("~/")) "\$HOME/${path.removePrefix("~/")}" else path

    private fun shellQuote(value: String): String {
        val homePrefix = "\$HOME/"
        return if (value.startsWith(homePrefix)) {
            "\"\$HOME/${escapeForDoubleQuotes(value.removePrefix(homePrefix))}\""
        } else {
            "\"${escapeForDoubleQuotes(value)}\""
        }
    }

    private fun escapeForDoubleQuotes(value: String): String =
        buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '`' -> append("\\`")
                    else -> append(ch)
                }
            }
        }
}
