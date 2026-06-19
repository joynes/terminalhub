package se.joynes.aiterminal.domain.usecase

import se.joynes.aiterminal.data.model.Project
import se.joynes.aiterminal.data.model.Server
import se.joynes.aiterminal.data.ssh.SshManager
import se.joynes.aiterminal.domain.ScriptTemplateEngine
import javax.inject.Inject

class ApplySessionScript @Inject constructor(
    private val engine: ScriptTemplateEngine,
    private val sshManager: SshManager
) {
    operator fun invoke(sessionId: String, server: Server, project: Project) {
        val rendered = engine.renderCustomScript(server, project)
        if (rendered.isNotBlank()) {
            sshManager.getSession(sessionId)?.send("$rendered\n")
        }
    }
}
