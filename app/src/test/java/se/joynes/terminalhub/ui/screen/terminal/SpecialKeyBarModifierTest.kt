package se.joynes.terminalhub.ui.screen.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialKeyBarModifierTest {

    @Test
    fun `ctrl c produces terminal interrupt byte`() {
        val result = applyKeyBarModifiers("c", ctrl = true, alt = false, shift = false)

        assertEquals(listOf(0x03), result.toByteArray(Charsets.UTF_8).map(Byte::toInt))
    }

    @Test
    fun `all lowercase ctrl letters map to ASCII control range`() {
        ('a'..'z').forEachIndexed { index, letter ->
            val result = applyKeyBarModifiers(letter.toString(), ctrl = true, alt = false, shift = false)

            assertEquals(index + 1, result.single().code)
        }
    }

    @Test
    fun `plain c remains lowercase`() {
        assertEquals("c", applyKeyBarModifiers("c", ctrl = false, alt = false, shift = false))
    }

    @Test
    fun `shift letter is uppercase and alt letter is escape prefixed`() {
        assertEquals("C", applyKeyBarModifiers("c", ctrl = false, alt = false, shift = true))
        assertEquals("\u001Bc", applyKeyBarModifiers("c", ctrl = false, alt = true, shift = false))
    }
}
