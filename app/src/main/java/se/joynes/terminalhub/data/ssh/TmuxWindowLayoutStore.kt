package se.joynes.terminalhub.data.ssh

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server

data class TmuxWindowMemory(val key: String, val program: TmuxProgram? = null)

/** Observations are recovery hints, never executable commands or process checkpoints. */
data class TmuxWindowLayout(val names: List<String>, val active: Int, val memories: List<TmuxWindowMemory> = emptyList()) {
    private fun validate() {
        require(names.size in 1..128 && active in names.indices)
        require(names.all { it.length <= 1024 && '\u0000' !in it })
        require(memories.isEmpty() || memories.size == names.size)
        require(memories.all { Regex("[a-zA-Z0-9@_-]{1,128}").matches(it.key) })
        require(memories.map { it.key }.distinct().size == memories.size)
        require(memories.all { it.program == null || listOf(it.program.command, it.program.directory).all { text -> text.length <= 8192 && '\u0000' !in text } })
    }

    fun encode(): String {
        validate()
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(2); out.writeInt(active); out.writeInt(names.size)
            names.forEach(out::writeUTF)
            out.writeInt(memories.size)
            memories.forEach {
                out.writeUTF(it.key); out.writeBoolean(it.program != null)
                it.program?.let { program -> out.writeUTF(program.command); out.writeUTF(program.directory) }
            }
        }
        return bytes.toByteArray().joinToString("") { "%02x".format(it) }
    }

    /** Build privately, then publish the complete session. Failure removes only our temporary session. */
    fun renderCreate(sessionName: String, folder: String): String {
        validate()
        fun q(value: String) = TmuxWindowCommands.quote(value)
        fun label(value: String) = q(value.replace("#", "##"))
        return buildString {
            append("( set -e; restore_name=${q("terminalhub-restore-" + UUID.randomUUID())}; ")
            append("trap 'tmux kill-session -t \"=\$restore_name\" 2>/dev/null || true' EXIT; ")
            append("restore_session=\$(tmux new-session -d -P -F '#{session_id}' -s \"\$restore_name\" -c ${TmuxWindowCommands.path(folder)} -n ${label(names.first())}); ")
            append("restore_active=\$(tmux display-message -p -t \"\$restore_session:\" '#{window_id}'); ")
            memories.firstOrNull()?.let { append("tmux set-option -w -t \"\$restore_session:\$restore_active\" @terminalhub_window_key ${q(it.key)}; ") }
            names.drop(1).forEachIndexed { index, name ->
                append("restore_window=\$(tmux new-window -d -P -F '#{window_id}' -t \"\$restore_session:\" -c ${TmuxWindowCommands.path(folder)} -n ${label(name)}); ")
                memories.getOrNull(index + 1)?.let { append("tmux set-option -w -t \"\$restore_session:\$restore_window\" @terminalhub_window_key ${q(it.key)}; ") }
                if (index + 1 == active) append("restore_active=\$restore_window; ")
            }
            append("tmux select-window -t \"\$restore_session:\$restore_active\"; ")
            append("tmux rename-session -t \"\$restore_session\" ${q(sessionName)}; ")
            append("trap - EXIT; echo TMUX_WINDOWS_RESTORED; )")
        }
    }

    companion object {
        private val shells = setOf("sh", "bash", "zsh", "fish", "dash", "ksh", "tcsh", "csh", "ash", "nu", "xonsh", "pwsh")
        fun from(snapshot: TmuxWindowsSnapshot, previous: TmuxWindowLayout? = null): TmuxWindowLayout {
            val old = previous?.memories.orEmpty().associateBy { it.key }
            val memories = if (snapshot.windows.all { it.key.isNotBlank() }) snapshot.windows.map { window ->
                val command = window.currentProgram.substringAfterLast('/').removePrefix("-")
                val observation = if (command.isNotBlank() && command !in shells && window.panes == 1)
                    TmuxProgram(window.currentProgram, window.directory) else null
                TmuxWindowMemory(window.key, observation ?: old[window.key]?.program)
            } else emptyList()
            return TmuxWindowLayout(snapshot.windows.map { it.name }, snapshot.windows.indexOfFirst { it.active }, memories)
        }
        fun decode(text: String?): TmuxWindowLayout? = runCatching {
            if (text == null || text.length > 16_000_000 || text.length % 2 != 0) return null
            val bytes = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readInt(); require(version in 1..2)
                val active = input.readInt(); val count = input.readInt()
                require(count in 1..128)
                val names = List(count) { input.readUTF() }
                val memories = if (version == 1) emptyList() else {
                    val size = input.readInt(); require(size == 0 || size == count)
                    List(size) { TmuxWindowMemory(input.readUTF(), if (input.readBoolean()) TmuxProgram(input.readUTF(), input.readUTF()) else null) }
                }
                TmuxWindowLayout(names, active, memories).also { it.validate(); require(input.available() == 0) }
            }
        }.getOrNull()
    }
}

@Singleton
class TmuxWindowLayoutStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("tmux_window_layouts", Context.MODE_PRIVATE)
    private fun key(server: Server, project: Project): String {
        val identity = listOf(project.id.toString(), project.name, server.host, server.port.toString(), server.username, server.projectsFolder)
            .joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun read(server: Server, project: Project): TmuxWindowLayout? = TmuxWindowLayout.decode(prefs.getString(key(server, project), null))
    suspend fun save(server: Server, project: Project, snapshot: TmuxWindowsSnapshot, onlyIfMissing: Boolean = false) = withContext(Dispatchers.IO) {
        val key = key(server, project)
        synchronized(prefs) {
            val previous = prefs.getString(key, null)
            val encoded = TmuxWindowLayout.from(snapshot, TmuxWindowLayout.decode(previous)).encode()
            if ((!onlyIfMissing || TmuxWindowLayout.decode(previous) == null) && previous != encoded) {
                check(prefs.edit().putString(key, encoded).commit()) { "Could not save terminal window layout on this device." }
            }
        }
    }
}
