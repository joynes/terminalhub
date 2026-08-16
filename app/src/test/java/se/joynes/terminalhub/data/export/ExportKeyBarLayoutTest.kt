package se.joynes.terminalhub.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportKeyBarLayoutTest {

    @Test
    fun `version 2 backup restores key bar rows`() {
        val yaml = """
            version: 2
            settings:
              keyBarLayout: "CTRL,CHAR_C|ENTER,UP"
            servers: []
        """.trimIndent()

        assertEquals(
            listOf(listOf("CTRL", "CHAR_C"), listOf("ENTER", "UP")),
            extractKeyBarLayoutFromYaml(yaml)
        )
    }

    @Test
    fun `version 1 backup leaves existing key bar untouched`() {
        val yaml = """
            version: 1
            servers: []
        """.trimIndent()

        assertNull(extractKeyBarLayoutFromYaml(yaml))
    }

    @Test
    fun `quoted yaml scalar is decoded before layout parsing`() {
        val yaml = """
            version: 2
            settings:
              keyBarLayout: "CTRL,CHAR_C|ENTER"
            servers:
        """.trimIndent()

        assertEquals(listOf(listOf("CTRL", "CHAR_C"), listOf("ENTER")), extractKeyBarLayoutFromYaml(yaml))
    }
}
