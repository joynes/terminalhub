package se.joynes.terminalhub.data.ssh

import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.domain.ScriptTemplateEngine
import javax.inject.Inject
import java.util.UUID

data class TmuxProgram(val command: String, val directory: String)
data class TmuxWindow(val id: String, val index: Int, val name: String, val active: Boolean, val panes: Int,
    val key: String = "", val currentProgram: String = "", val directory: String = "", val lastProgram: TmuxProgram? = null)
data class TmuxWindowsSnapshot(val sessionId: String, val windows: List<TmuxWindow>)

/** All targets are tmux object IDs, scoped to the project's exact session. */
object TmuxWindowCommands {
    internal const val START = "TERMINALHUB_WINDOWS_V2"
    internal const val END = "TERMINALHUB_WINDOWS_END"
    private val sessionId = Regex("\\$\\d+")
    private val windowId = Regex("@\\d+")
    internal fun quote(value: String): String {
        require('\u0000' !in value) { "Invalid tmux argument." }
        return "'" + value.replace("'", "'\\''") + "'"
    }
    internal fun path(value: String) = when {
        value == "~" -> "\"\$HOME\""
        value.startsWith("~/") -> "\"\$HOME\"/" + quote(value.removePrefix("~/"))
        else -> quote(value)
    }

    fun validateName(name: String) {
        require(name.codePointCount(0, name.length) <= 64 && name.none(Character::isISOControl)) {
            "Use up to 64 characters, without line breaks or control characters."
        }
    }

    private fun login(script: String) = "bash -lc " + quote("set -euo pipefail; $script")

    /** NUL framing keeps Unicode, quotes, tabs and newlines in existing names unambiguous. */
    fun list(sessionName: String): String = login(
        "session_id=\$(tmux display-message -p -t ${quote("=$sessionName:")} '#{session_id}'); " +
        renderList("\"\$session_id\"")
    )

    private fun renderList(target: String): String =
        "printf '%s\\000%s\\000' '$START' $target; " +
        "tmux list-windows -t $target -F '#{window_id} #{window_index} #{window_active} #{window_panes}' | " +
        "while read -r window_id window_index window_active window_panes; do " +
        // -o only sets an absent option. The logical key survives rename and server restoration.
        "tmux set-option -woq -t \"\$session_id:\$window_id\" @terminalhub_window_key '${UUID.randomUUID()}-'\"\$window_id\"; " +
        "window_name=\$(tmux display-message -p -t \"\$session_id:\$window_id\" '#{window_name}'); " +
        "window_key=\$(tmux display-message -p -t \"\$session_id:\$window_id\" '#{@terminalhub_window_key}'); " +
        "window_program=\$(tmux display-message -p -t \"\$session_id:\$window_id\" '#{pane_current_command}'); " +
        "window_directory=\$(tmux display-message -p -t \"\$session_id:\$window_id\" '#{pane_current_path}'); " +
        "printf '%s\\000%s\\000%s\\000%s\\000%s\\000%s\\000%s\\000%s\\000' \"\$window_id\" \"\$window_index\" \"\$window_active\" \"\$window_panes\" \"\$window_name\" \"\$window_key\" \"\$window_program\" \"\$window_directory\"; " +
        "done; printf '%s\\000' '$END'"

    private fun verify(snapshot: TmuxWindowsSnapshot, window: TmuxWindow? = null): String {
        require(sessionId.matches(snapshot.sessionId)) { "Invalid tmux session ID." }
        if (window != null) {
            require(windowId.matches(window.id) && snapshot.windows.any { it.id == window.id }) { "Window does not belong to this project." }
        }
        val target = quote(snapshot.sessionId)
        return "session_id=$target; tmux has-session -t $target; " + if (window == null) "" else
            "tmux list-windows -t $target -F '#{window_id}' | grep -Fx -- ${quote(window.id)} >/dev/null; "
    }

    fun create(snapshot: TmuxWindowsSnapshot, folder: String, name: String): String {
        validateName(name)
        val chosenName = name.trim().ifBlank {
            generateSequence(2) { it + 1 }.map { "Terminal $it" }.first { candidate -> snapshot.windows.none { it.name == candidate } }
        }
        // tmux treats names as formats, even after shell quoting. Escape its format introducer.
        val label = " -n ${quote(chosenName.replace("#", "##"))}"
        return login(verify(snapshot) +
            "new_id=\$(tmux new-window -d -P -F '#{window_id}' -t ${quote(snapshot.sessionId + ":")} -c ${path(folder)}$label); " +
            "tmux select-window -t \"\$session_id:\$new_id\"; " + renderList("\"\$session_id\""))
    }

    fun select(snapshot: TmuxWindowsSnapshot, window: TmuxWindow): String = login(verify(snapshot, window) +
        "tmux select-window -t ${quote(snapshot.sessionId + ":" + window.id)}; " + renderList("\"\$session_id\""))

    fun rename(snapshot: TmuxWindowsSnapshot, window: TmuxWindow, name: String): String {
        validateName(name)
        require(name.isNotBlank()) { "Enter a terminal name." }
        return login(verify(snapshot, window) +
            "tmux rename-window -t ${quote(snapshot.sessionId + ":" + window.id)} -- ${quote(name.trim().replace("#", "##"))}; " + renderList("\"\$session_id\""))
    }

    fun close(snapshot: TmuxWindowsSnapshot, window: TmuxWindow): String {
        require(snapshot.windows.size > 1) { "Keep one terminal in the project. Use Restart session to restart the project." }
        return login(verify(snapshot, window) +
            // Recheck on the server: another client may have closed a window since the list was read.
            "window_count=\$(tmux list-windows -t \"\$session_id\" -F '#{window_id}' | wc -l); " +
            "[ \"\$window_count\" -gt 1 ]; " +
            "tmux kill-window -t ${quote(snapshot.sessionId + ":" + window.id)}; " + renderList("\"\$session_id\""))
    }

    fun parse(output: String): TmuxWindowsSnapshot {
        val start = output.indexOf("$START\u0000")
        val end = output.lastIndexOf("\u0000$END\u0000")
        require(start >= 0 && end > start) { "Could not read project terminals. Reconnect the project and refresh." }
        val fields = output.substring(start + START.length + 1, end).split('\u0000')
        require(sessionId.matches(fields.first()) && fields.size >= 9 && (fields.size - 1) % 8 == 0) { "Invalid tmux terminal list." }
        val windows = fields.drop(1).chunked(8).map { row ->
            require(windowId.matches(row[0]) && row[2] in listOf("0", "1")) { "Invalid tmux window." }
            require(Regex("[a-zA-Z0-9@_-]{1,128}").matches(row[5])) { "Invalid terminal identity." }
            TmuxWindow(row[0], row[1].toInt(), row[4], row[2] == "1", row[3].toInt(), row[5], row[6], row[7])
        }
        require(windows.size <= 128 && windows.map { it.id }.distinct().size == windows.size &&
            windows.map { it.key }.distinct().size == windows.size && windows.count { it.active } == 1) {
            "Could not read a complete terminal list. Refresh to try again."
        }
        return TmuxWindowsSnapshot(fields.first(), windows)
    }
}

class RemoteTmuxWindows @Inject constructor(private val executor: RemoteCommandExecutor, private val templates: ScriptTemplateEngine) {
    suspend fun list(server: Server, project: Project) = execute(server, TmuxWindowCommands.list(templates.sessionName(project)))
    suspend fun create(server: Server, project: Project, snapshot: TmuxWindowsSnapshot, name: String) =
        execute(server, TmuxWindowCommands.create(snapshot, templates.projectPath(server, project), name))
    suspend fun select(server: Server, snapshot: TmuxWindowsSnapshot, window: TmuxWindow) = execute(server, TmuxWindowCommands.select(snapshot, window))
    suspend fun rename(server: Server, snapshot: TmuxWindowsSnapshot, window: TmuxWindow, name: String) = execute(server, TmuxWindowCommands.rename(snapshot, window, name))
    suspend fun close(server: Server, snapshot: TmuxWindowsSnapshot, window: TmuxWindow) = execute(server, TmuxWindowCommands.close(snapshot, window))

    private suspend fun execute(server: Server, command: String): TmuxWindowsSnapshot {
        val result = executor.run(server, command)
        check(result.exitCode == 0 && !result.truncated) { "Could not update project terminals. Refresh before trying again; the server may already have applied the change." }
        return TmuxWindowCommands.parse(result.output)
    }
}
