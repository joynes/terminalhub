package se.joynes.terminalhub.ui.screen.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTextPreviewTest {
    @Test
    fun previewableTypesIncludeTextMarkdownAndCommonSourceFiles() {
        assertTrue(isPreviewableTextFile("README.md"))
        assertTrue(isPreviewableTextFile("LICENSE"))
        assertTrue(isPreviewableTextFile("settings.gradle.kts"))
        assertTrue(isPreviewableTextFile("output.LOG"))
        assertFalse(isPreviewableTextFile("cover.png"))
        assertFalse(isPreviewableTextFile("archive.zip"))
    }

    @Test
    fun markdownParserCreatesReadableBlocksForCommonFormatting() {
        val blocks = parseMarkdownPreview(
            """
            # Heading

            A **bold** paragraph.
            - First item
            > A quote
            ```
            echo hello
            ```
            """.trimIndent()
        )

        assertEquals(
            listOf(
                MarkdownBlockKind.HEADING,
                MarkdownBlockKind.PARAGRAPH,
                MarkdownBlockKind.BULLET,
                MarkdownBlockKind.QUOTE,
                MarkdownBlockKind.CODE
            ),
            blocks.map { it.kind }
        )
        assertEquals("Heading", blocks.first().text)
        assertEquals("echo hello", blocks.last().text)
    }

    @Test
    fun inlineMarkdownKeepsReadableTextWithoutFormattingMarkers() {
        val formatted = formattedMarkdownInline(
            "Use **bold**, `code`, and [the guide](https://example.com)."
        )

        assertEquals("Use bold, code, and the guide.", formatted.text)
    }
}
