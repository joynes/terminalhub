package se.joynes.aiterminalhub.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import se.joynes.aiterminalhub.data.model.Project
import se.joynes.aiterminalhub.data.model.Server

class ScriptTemplateEngineTest {
    private val engine = ScriptTemplateEngine()

    @Test
    fun renderSetupQuotesHomePathAndEmitsCloneFailureMarker() {
        val server = Server(
            name = "prod",
            host = "example.com",
            username = "demo",
            projectsFolder = "~/projects with spaces"
        )
        val project = Project(
            serverId = 1L,
            name = "sample-project",
            gitUrl = "https://github.com/example/sample-project"
        )

        val setup = engine.renderSetup(server, project)

        assertTrue(setup.contains("git clone --depth 1 'https://github.com/example/sample-project' '\$HOME/projects with spaces/sample-project'"))
        assertTrue(setup.contains(ScriptTemplateEngine.GIT_CLONE_FAILED_MARKER))
        assertTrue(setup.contains("mkdir -p '\$HOME/projects with spaces/sample-project'"))
    }
}
