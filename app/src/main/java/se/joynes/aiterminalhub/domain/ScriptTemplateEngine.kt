package se.joynes.aiterminalhub.domain

import se.joynes.aiterminalhub.data.model.Project
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptTemplateEngine @Inject constructor() {
    fun render(template: String, project: Project): String =
        template
            .replace("{{PROJECT_NAME}}", project.name)
            .replace("{{PROJECT_PATH}}", project.projectPath)
            .replace("{{SESSION_NAME}}", project.sessionName)
}
