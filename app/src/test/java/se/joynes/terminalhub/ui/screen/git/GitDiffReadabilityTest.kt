package se.joynes.terminalhub.ui.screen.git

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import se.joynes.terminalhub.data.git.*

class GitDiffReadabilityTest {
    @Test fun `hunks number added removed and context lines independently`() {
        val lines = parseGitDiff("diff --git a/a.kt b/a.kt\n--- a/a.kt\n+++ b/a.kt\n@@ -4,2 +4,3 @@\n val a = 1\n-old\n+new\n+++source\n\\ No newline at end of file\n")
        assertEquals(DiffLineKind.HEADER, lines[2].kind)
        assertEquals(4, lines[4].oldLine); assertEquals(4, lines[4].newLine)
        assertEquals(5, lines[5].oldLine); assertNull(lines[5].newLine)
        assertEquals(5, lines[6].newLine); assertNull(lines[6].oldLine)
        assertEquals("++source", lines[7].text)
        assertEquals(6, lines[7].newLine)
        assertEquals(DiffLineKind.NOTE, lines[8].kind)
    }

    @Test fun `zero context insertion and multiple hunks reset line numbers`() {
        val lines = parseGitDiff("@@ -0,0 +1,1 @@\n+new\n@@ -20 +21 @@\n-old\n+newer\n")
        assertEquals(1, lines[1].newLine)
        assertEquals(20, lines[3].oldLine)
        assertEquals(21, lines[4].newLine)
    }

    @Test fun `syntax tokens do not treat comment markers inside strings as comments`() {
        val code = "val url = \"https://example.com/#anchor\" // comment"
        val tokens = codeTokens(code, "source.kt")
        assertEquals(listOf(CodeTokenKind.KEYWORD, CodeTokenKind.STRING, CodeTokenKind.COMMENT), tokens.map { it.kind })
        assertEquals("// comment", code.substring(tokens.last().start, tokens.last().end))
        val python = codeTokens("print('# text') # comment", "source.py")
        assertEquals(listOf(CodeTokenKind.STRING, CodeTokenKind.COMMENT), python.map { it.kind })
        assertTrue(codeTokens("plain text # keep", "readme.txt").isEmpty())
    }

    @Test fun `multiline comments and strings keep their syntax color across changed lines`() {
        val lexer = CodeLexer("sample.kt")
        lexer.tokens("/* comment begins")
        assertEquals(CodeTokenKind.COMMENT, lexer.tokens("class still a comment").single().kind)
        val ended = lexer.tokens("ends */ val a = 1")
        assertEquals(listOf(CodeTokenKind.COMMENT, CodeTokenKind.KEYWORD, CodeTokenKind.NUMBER), ended.map { it.kind })
        val python = CodeLexer("sample.py")
        python.tokens("message = \"\"\"first line")
        assertEquals(CodeTokenKind.STRING, python.tokens("# this is text").single().kind)
        assertEquals(CodeTokenKind.STRING, python.tokens("last\"\"\"").single().kind)
        assertEquals(CodeTokenKind.COMMENT, python.tokens("# now a comment").single().kind)
    }

    @Test fun `all syntax colors meet WCAG normal text contrast on both diff backgrounds`() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance(); val y = b.luminance()
            return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
        }
        val foregrounds = CodeTokenKind.entries.map(CodePalette::token) + listOf(CodePalette.foreground, CodePalette.secondary)
        for (bg in listOf(CodePalette.background, CodePalette.added, CodePalette.removed)) {
            foregrounds.forEach { assertTrue("Contrast ${contrast(it, bg)}", contrast(it, bg) >= 4.5f) }
        }
        assertTrue(contrast(CodePalette.addedMarker, CodePalette.added) >= 4.5f)
        assertTrue(contrast(CodePalette.removedMarker, CodePalette.removed) >= 4.5f)
    }

    @Test fun `truncated file list is never presented as a complete list`() = kotlinx.coroutines.runBlocking {
        val reader = GitReviewReader { GitCommandResult("M\u0000a.txt\u0000", 0, true) }
        try { reader.snapshot("repo", GitReviewMode.ALL); fail("Expected an explicit limit error") }
        catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("Too many files")) }
    }
}
