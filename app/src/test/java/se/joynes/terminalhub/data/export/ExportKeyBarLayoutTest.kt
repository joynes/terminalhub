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

    @Test
    fun `backup restores text input panel opacity`() {
        val yaml = """
            version: 2
            settings:
              textInputPanelOpacity: 0.65
            servers: []
        """.trimIndent()

        assertEquals(0.65f, extractTextInputPanelOpacityFromYaml(yaml))
    }

    @Test
    fun `old backup leaves current text input opacity untouched`() {
        assertNull(extractTextInputPanelOpacityFromYaml("version: 1\nservers: []"))
    }

    @Test
    fun `backup restores key bar highlights`() {
        val yaml = """
            version: 2
            settings:
              keyBarHighlights: "TEXT_INPUT,UPLOAD,CHAR_C"
            servers: []
        """.trimIndent()

        assertEquals(
            setOf("TEXT_INPUT", "UPLOAD", "CHAR_C"),
            extractKeyBarHighlightsFromYaml(yaml)
        )
    }

    @Test
    fun `old backup leaves current key bar highlights untouched`() {
        assertNull(extractKeyBarHighlightsFromYaml("version: 1\nservers: []"))
    }

    @Test
    fun `backup restores key bar highlight intensity`() {
        val yaml = """
            version: 2
            settings:
              keyBarHighlightIntensity: 0.22
            servers: []
        """.trimIndent()

        assertEquals(0.22f, extractKeyBarHighlightIntensityFromYaml(yaml))
    }

    @Test
    fun `highlight intensity from backup is constrained and optional`() {
        val oversized = """
            version: 2
            settings:
              keyBarHighlightIntensity: 2.0
            servers: []
        """.trimIndent()

        assertEquals(0.40f, extractKeyBarHighlightIntensityFromYaml(oversized))
        assertNull(extractKeyBarHighlightIntensityFromYaml("version: 1\nservers: []"))
    }
}
