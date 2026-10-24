package se.joynes.terminalhub.ui.screen.git

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import se.joynes.terminalhub.data.git.GitDiffLine
import se.joynes.terminalhub.data.git.DiffLineKind

/** A small, bounded lexer: no HTML/WebView, network requests or executable file content. */
internal enum class CodeTokenKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE }
internal data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

internal object CodePalette {
    val background = Color(0xFF10151D)
    val foreground = Color(0xFFE6EDF3)
    val secondary = Color(0xFFB5C0CD)
    val added = Color(0xFF152F25)
    val removed = Color(0xFF392028)
    val addedMarker = Color(0xFF8EE6A5)
    val removedMarker = Color(0xFFFFA5AE)
    fun token(kind: CodeTokenKind): Color = when (kind) {
        CodeTokenKind.KEYWORD -> Color(0xFFFFB4D9)
        CodeTokenKind.STRING -> Color(0xFFA5D8FF)
        CodeTokenKind.COMMENT -> Color(0xFFBDCBD6)
        CodeTokenKind.NUMBER -> Color(0xFFF3D28F)
        CodeTokenKind.TYPE -> Color(0xFFD4BBFF)
    }
}

private val keywords = ("abstract as async await break case catch class const continue data def default " +
    "delete do elif else enum except export extends false False final finally for from fun function if " +
    "implements import in instanceof interface internal is lambda let match new nil None null object " +
    "override package pass private protected public raise readonly return sealed static struct super " +
    "switch this throw throws trait true True try type typeof val var void when while with yield " +
    "fn impl mod mut pub self use where echo then fi done esac local end").split(' ').toSet()

internal class CodeLexer(private val fileName: String) {
    private var continuation: String? = null
    private var continuationKind = CodeTokenKind.STRING

    fun tokens(code: String): List<CodeToken> {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val hashComments = extension in setOf("py", "sh", "bash", "zsh", "rb", "yaml", "yml", "toml", "conf") ||
            fileName.substringAfterLast('/').lowercase() in setOf("dockerfile", "makefile")
        val slashComments = extension in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "c", "h", "cpp", "hpp", "cc", "go", "rs", "cs", "swift", "css", "jsonc")
        val supported = hashComments || slashComments || extension in setOf("json", "html", "xml", "sql")
        if (!supported) return emptyList()
        val tokens = mutableListOf<CodeToken>()
        var i = 0
        while (i < code.length) {
            val start = i
            val ch = code[i]
            when {
                continuation != null -> {
                    val end = code.indexOf(continuation!!, i)
                    i = if (end < 0) code.length else end + continuation!!.length
                    tokens += CodeToken(start, i, continuationKind)
                    if (end >= 0) continuation = null
                }
                (hashComments && ch == '#') || (slashComments && code.startsWith("//", i)) ||
                    (extension == "sql" && code.startsWith("--", i)) -> {
                    tokens += CodeToken(i, code.length, CodeTokenKind.COMMENT); break
                }
                slashComments && code.startsWith("/*", i) -> {
                    val end = code.indexOf("*/", i + 2)
                    i = if (end < 0) code.length else end + 2
                    tokens += CodeToken(start, i, CodeTokenKind.COMMENT)
                    if (end < 0) { continuation = "*/"; continuationKind = CodeTokenKind.COMMENT }
                }
                ch in charArrayOf('\'', '"', '`') -> {
                    val delimiter = if (code.startsWith("$ch$ch$ch", i)) "$ch$ch$ch" else "$ch"
                    i += delimiter.length
                    var ended = false
                    while (i < code.length) {
                        if (code[i] == '\\') i = minOf(code.length, i + 2)
                        else if (code.startsWith(delimiter, i)) { i += delimiter.length; ended = true; break }
                        else i++
                    }
                    tokens += CodeToken(start, i, CodeTokenKind.STRING)
                    if (!ended && (delimiter.length == 3 || ch == '`')) {
                        continuation = delimiter; continuationKind = CodeTokenKind.STRING
                    }
                }
                ch.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit()) -> {
                    i++
                    while (i < code.length && (code[i].isLetterOrDigit() || code[i] in "._")) i++
                    tokens += CodeToken(start, i, CodeTokenKind.NUMBER)
                }
                ch.isLetter() || ch == '_' -> {
                    i++
                    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                    val word = code.substring(start, i)
                    val kind = when {
                        word in keywords || extension == "sql" && word.lowercase() in setOf("select", "from", "where", "join", "insert", "update", "delete", "into", "values", "create", "table", "order", "by", "limit", "and", "or", "not", "null") -> CodeTokenKind.KEYWORD
                        word.first().isUpperCase() -> CodeTokenKind.TYPE
                        else -> null
                    }
                    if (kind != null) tokens += CodeToken(start, i, kind)
                }
                else -> i++
            }
        }
        return tokens
    }
}

internal fun codeTokens(code: String, fileName: String): List<CodeToken> = CodeLexer(fileName).tokens(code)

internal fun highlightDiffLines(lines: List<GitDiffLine>, fileName: String): List<AnnotatedString> {
    var old = CodeLexer(fileName)
    var new = CodeLexer(fileName)
    return lines.map { line ->
        val text = line.text.take(16_000)
        val tokens = when (line.kind) {
            DiffLineKind.ADDED -> new.tokens(text)
            DiffLineKind.REMOVED -> old.tokens(text)
            DiffLineKind.CONTEXT -> { old.tokens(text); new.tokens(text) }
            DiffLineKind.HUNK -> { old = CodeLexer(fileName); new = CodeLexer(fileName); emptyList() }
            else -> emptyList()
        }
        buildAnnotatedString {
            append(text)
            tokens.forEach { addStyle(SpanStyle(color = CodePalette.token(it.kind)), it.start, it.end) }
        }
    }
}
