package se.joynes.terminalhub.domain.usecase

import se.joynes.terminalhub.data.model.Project
import se.joynes.terminalhub.data.model.Server
import se.joynes.terminalhub.data.ssh.SshManager
import se.joynes.terminalhub.domain.ScriptTemplateEngine
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
