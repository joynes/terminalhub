package se.joynes.aiterminalhub

import org.junit.Assert.assertEquals
import org.junit.Test
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.Server
import se.joynes.aiterminalhub.domain.ScriptTemplateEngine

class ScriptTemplateEngineTest {
    private val engine = ScriptTemplateEngine()
    private val server = Server(
        id = 1L,
        name = "Dev",
        host = "192.168.1.100",
        username = "ubuntu",
        projectsFolder = "/home/user"
    )
    private val project = Project(id = 1L, serverId = 1L, name = "MyApp")

    @Test
    fun `replaces PROJECT_NAME placeholder`() {
        val result = engine.render("cd {{PROJECT_NAME}}", server, project)
        assertEquals("cd MyApp", result)
    }

    @Test
    fun `replaces PROJECT_PATH placeholder`() {
        val result = engine.render("cd {{PROJECT_PATH}}", server, project)
        assertEquals("cd /home/user/MyApp", result)
    }

    @Test
    fun `replaces SESSION_NAME placeholder`() {
        val result = engine.render("tmux new -s {{SESSION_NAME}}", server, project)
        assertEquals("tmux new -s myapp", result)
    }

    @Test
    fun `replaces all placeholders`() {
        val template = "cd {{PROJECT_PATH}} && tmux new -s {{SESSION_NAME}} -n {{PROJECT_NAME}}"
        val result = engine.render(template, server, project)
        assertEquals("cd /home/user/MyApp && tmux new -s myapp -n MyApp", result)
    }

    @Test
    fun `returns unchanged string when no placeholders`() {
        val script = "echo hello world"
        assertEquals(script, engine.render(script, server, project))
    }

    @Test
    fun `projectPath combines server folder and project name`() {
        assertEquals("/home/user/MyApp", engine.projectPath(server, project))
    }

    @Test
    fun `sessionName lowercases and slugifies project name`() {
        val p = Project(id = 2L, serverId = 1L, name = "My Cool App")
        assertEquals("my-cool-app", engine.sessionName(p))
    }
}
