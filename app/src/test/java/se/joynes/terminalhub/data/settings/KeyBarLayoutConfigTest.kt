package se.joynes.terminalhub.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyBarLayoutConfigTest {

    @Test
    fun `default layout survives persistence round trip`() {
        val encoded = KeyBarLayoutConfig.encode(KeyBarLayoutConfig.defaultRows)

        assertEquals(KeyBarLayoutConfig.defaultRows, KeyBarLayoutConfig.decode(encoded))
    }

    @Test
    fun `alt can be replaced with c`() {
        val rows = KeyBarLayoutConfig.defaultRows
        val altIndex = rows[1].indexOf("ALT")

        val updated = KeyBarLayoutConfig.replaceKey(rows, 1, altIndex, "CHAR_C")

        assertEquals("CHAR_C", updated[1][altIndex])
        assertEquals("c", KeyBarLayoutConfig.definition(updated[1][altIndex])?.text)
    }

    @Test
    fun `keys and rows can be added removed and reordered`() {
        val withRow = KeyBarLayoutConfig.addRow(KeyBarLayoutConfig.defaultRows)
        val withKey = KeyBarLayoutConfig.addKey(withRow, 2, "ENTER")
        val withoutFirstKey = KeyBarLayoutConfig.removeKey(withKey, 2, 0)
        val moved = KeyBarLayoutConfig.moveRow(withoutFirstKey, 2, 0)

        assertEquals(3, moved.size)
        assertEquals(listOf("ENTER"), moved[0])
        assertEquals(2, KeyBarLayoutConfig.removeRow(moved, 0).size)
    }

    @Test
    fun `unknown imported keys are discarded safely`() {
        val decoded = KeyBarLayoutConfig.decode("CTRL,DOES_NOT_EXIST,CHAR_C|UNKNOWN")

        assertEquals(listOf(listOf("CTRL", "CHAR_C")), decoded)
        assertNull(KeyBarLayoutConfig.definition("DOES_NOT_EXIST"))
    }

    @Test
    fun `limits imported rows and keys`() {
        val oversizedRow = List(KeyBarLayoutConfig.MAX_KEYS_PER_ROW + 3) { "CHAR_C" }.joinToString(",")
        val encoded = List(KeyBarLayoutConfig.MAX_ROWS + 2) { oversizedRow }.joinToString("|")

        val decoded = KeyBarLayoutConfig.decode(encoded)

        assertEquals(KeyBarLayoutConfig.MAX_ROWS, decoded.size)
        decoded.forEach { assertEquals(KeyBarLayoutConfig.MAX_KEYS_PER_ROW, it.size) }
    }

    @Test
    fun `blank or wholly invalid storage restores defaults`() {
        assertEquals(KeyBarLayoutConfig.defaultRows, KeyBarLayoutConfig.decode(""))
        assertEquals(KeyBarLayoutConfig.defaultRows, KeyBarLayoutConfig.decode("UNKNOWN"))
    }
}
