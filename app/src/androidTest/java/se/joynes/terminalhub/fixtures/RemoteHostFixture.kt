package se.joynes.terminalhub.fixtures

import android.util.Base64
import com.trilead.ssh2.FixtureSshSession
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.ssh.*

/** Only the SSH channel boundary is simulated. Host, session, window and persistence code stays real. */
class RemoteHostFixture : SshTransportConnector {
    data class Window(val id: String, var name: String, val key: String)
    private data class Project(val name: String, val number: Int,
        val windows: MutableList<Window> = mutableListOf(), var active: String = "")
    private val projects = listOf(Project("remote-one", 1), Project("remote-two", 2)).onEach { p ->
        p.windows += Window("@${p.number}1", "AI", "${p.name}-ai")
        p.windows += Window("@${p.number}2", "Tests", "${p.name}-tests")
        p.active = p.windows.first().id
    }
    val shells = CopyOnWriteArrayList<FixtureSshSession>()
    val commands = CopyOnWriteArrayList<String>()
    @Volatile var failure: Exception? = null

    override fun connect(server: Server, password: String?, privateKeyPem: String?): SshTransport {
        failure?.let { throw it }
        return object : SshTransport {
            private val channels = CopyOnWriteArrayList<FixtureSshSession>()
            override fun openProjectSession() = FixtureSshSession(true, ::execute).also { channels += it; shells += it }
            override fun openAuxiliarySession() = FixtureSshSession(false, ::execute).let { session ->
                channels += session
                SshAuxiliarySession(session) { channels -= session }
            }
            override fun openSftpSession(): SshAuxiliarySftp = throw IOException("Fixture file transfer is unavailable")
            override fun updateProjectIds(projectIds: Set<Long>) = Unit
            override fun close() { channels.forEach { it.close() }; channels.clear() }
            override fun debugIdentity() = System.identityHashCode(this)
        }
    }
    @Synchronized private fun execute(command: String): String {
        commands += command
        val project = projects.firstOrNull { command.contains(it.name) || command.contains("\$${it.number}") } ?: projects.first()
        if (!command.contains("TERMINALHUB_WINDOWS_V3")) return "TMUX_SESSION_EXISTS\n"
        val target = Regex("@\\d+").find(command)?.value
        val encoded = Regex("@terminalhub_label_base64\\s+[^A-Za-z0-9+/=]*([A-Za-z0-9+/=]+)").find(command)?.groupValues?.get(1)
        val requestedName = encoded?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
        when {
            command.contains("tmux new-window") -> {
                val next = "@${project.number}${project.windows.size + 1}"
                project.windows += Window(next, requestedName ?: "Terminal", "${project.name}-new-${project.windows.size}")
                project.active = next
            }
            command.contains("tmux kill-window") -> {
                project.windows.removeAll { it.id == target }
                if (project.active == target) project.active = project.windows.first().id
            }
            command.contains("tmux rename-window") && requestedName != null -> project.windows.firstOrNull { it.id == target }?.name = requestedName
            command.contains("tmux select-window") && target != null -> project.active = target
        }
        val fields = mutableListOf("TERMINALHUB_WINDOWS_V3", "\$${project.number}")
        project.windows.forEachIndexed { index, window ->
            fields += listOf(window.id, index.toString(), if (window.id == project.active) "1" else "0", "1", window.name,
                window.key, "fixture-ai", "/srv/${project.name}", Base64.encodeToString(window.name.toByteArray(), Base64.NO_WRAP))
        }
        fields += "TERMINALHUB_WINDOWS_END"
        return fields.joinToString("\u0000", postfix = "\u0000")
    }
}
