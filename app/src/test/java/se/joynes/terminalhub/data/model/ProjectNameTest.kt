package se.joynes.terminalhub.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNameTest {

    @Test
    fun `portable ascii project names are accepted`() {
        listOf("new2", "midi-music", "api_server", "terminal.hub", "Project123").forEach { name ->
            assertTrue(name, isValidProjectName(name))
            assertNull(name, projectNameValidationError(name))
        }
    }

    @Test
    fun `Swedish and other non ascii letters are rejected`() {
        listOf("räksmörgås", "midi-musik-ö", "åland", "projekt-ä", "café").forEach { name ->
            assertFalse(name, isValidProjectName(name))
            assertTrue(projectNameValidationError(name)?.contains("å, ä and ö") == true)
        }
    }

    @Test
    fun `unsafe paths whitespace and shell characters are rejected`() {
        listOf("two words", "../escape", ".hidden", "folder/name", "name$", "name'quote").forEach { name ->
            assertFalse(name, isValidProjectName(name))
        }
    }

    @Test
    fun `name length is limited`() {
        assertTrue(isValidProjectName("a".repeat(MAX_PROJECT_NAME_LENGTH)))
        assertFalse(isValidProjectName("a".repeat(MAX_PROJECT_NAME_LENGTH + 1)))
    }
}
