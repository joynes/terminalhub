package se.joynes.terminalhub.data.model

import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.domain.ScriptTemplateEngine

class ProjectDisplayNameTest {
    @Test fun displayNameNeverChangesProjectPathsOrTmuxCommands() {
        val original = Project(id = 42, serverId = 1, name = "existing-project", gitUrl = "git@example.com:repo.git")
        val renamed = original.copy(displayName = "Mitt projekt åäö 😀 / ' ; $(anything)")
        val engine = ScriptTemplateEngine()
        val server = Server(name = "Test", host = "example.org", username = "user")
        assertEquals(renamed.displayName, renamed.displayTitle)
        assertEquals(engine.projectPath(server, original), engine.projectPath(server, renamed))
        assertEquals(engine.localProjectPath("/local", original), engine.localProjectPath("/local", renamed))
        assertEquals(engine.sessionName(original), engine.sessionName(renamed))
        assertEquals(engine.renderSetup(server, original), engine.renderSetup(server, renamed))
        assertEquals(engine.renderAttach(server, original), engine.renderAttach(server, renamed))
        assertEquals(original.name, original.displayTitle)
        assertEquals(original.name, original.copy(displayName = " ").displayTitle)
    }

    @Test fun displayTitlesAllowSwedishAndSpacesButRejectControlsAndOversizedText() {
        assertNull(projectDisplayNameValidationError("Mitt projekt åäö 😀"))
        assertNull(projectDisplayNameValidationError(""))
        assertNotNull(projectDisplayNameValidationError("a\nb"))
        assertNotNull(projectDisplayNameValidationError("a\u001bb"))
        assertNotNull(projectDisplayNameValidationError("x".repeat(121)))
        assertFalse(isValidProjectName("Mitt projekt åäö"))
    }
}
