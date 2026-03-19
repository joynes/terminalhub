package se.joynes.aiterminalhub.domain.usecase

import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.ssh.SshManager
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine
import javax.inject.Inject

class ApplySessionScript @Inject constructor(
    private val engine: ScriptTemplateEngine,
    private val sshManager: SshManager
) {
    operator fun invoke(sessionId: String, project: Project) {
        val rendered = engine.render(project.setupScript, project)
        if (rendered.isNotBlank()) {
            sshManager.getSession(sessionId)?.send("$rendered\n")
        }
    }
}
