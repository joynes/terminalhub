package se.joynes.aiterminalhub

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine

class ScriptTemplateEngineTest {
    private val engine = ScriptTemplateEngine()
    private val project = Project(
        id = 1L,
        serverId = 1L,
        name = "MyApp",
        projectPath = "/home/user/myapp",
        sessionName = "myapp-session",
        setupScript = ""
    )

    @Test
    fun `replaces PROJECT_NAME placeholder`() {
        val result = engine.render("cd {{PROJECT_NAME}}", project)
        assertEquals("cd MyApp", result)
    }

    @Test
    fun `replaces PROJECT_PATH placeholder`() {
        val result = engine.render("cd {{PROJECT_PATH}}", project)
        assertEquals("cd /home/user/myapp", result)
    }

    @Test
    fun `replaces SESSION_NAME placeholder`() {
        val result = engine.render("tmux new -s {{SESSION_NAME}}", project)
        assertEquals("tmux new -s myapp-session", result)
    }

    @Test
    fun `replaces all placeholders`() {
        val template = "cd {{PROJECT_PATH}} && tmux new -s {{SESSION_NAME}} -n {{PROJECT_NAME}}"
        val result = engine.render(template, project)
        assertEquals("cd /home/user/myapp && tmux new -s myapp-session -n MyApp", result)
    }

    @Test
    fun `returns unchanged string when no placeholders`() {
        val script = "echo hello world"
        assertEquals(script, engine.render(script, project))
    }
}
