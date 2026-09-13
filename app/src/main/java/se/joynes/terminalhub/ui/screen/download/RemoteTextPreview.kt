package se.joynes.terminalhub.ui.screen.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import se.joynes.terminalhub.ui.components.RetroButton
import se.joynes.terminalhub.ui.theme.MegaDriveAccent
import se.joynes.terminalhub.ui.theme.MegaDriveBg
import se.joynes.terminalhub.ui.theme.MegaDriveDim
import se.joynes.terminalhub.ui.theme.MegaDriveOnSurface
import se.joynes.terminalhub.ui.theme.MegaDrivePrimary
import se.joynes.terminalhub.ui.theme.MegaDriveSurface
import se.joynes.terminalhub.ui.theme.MonoFontFamily

internal const val MAX_REMOTE_TEXT_PREVIEW_BYTES = 512L * 1024L

private val previewableTextExtensions = setOf(
    "bash", "c", "cc", "conf", "cpp", "css", "csv", "gitignore", "go", "gradle",
    "h", "hpp", "htm", "html", "ini", "java", "js", "json", "jsx", "kt", "kts",
    "log", "markdown", "md", "mdown", "mkdn", "properties", "py", "rb", "rs", "sh",
    "toml", "ts", "tsx", "tsv", "txt", "xml", "yaml", "yml", "zsh"
)

private val previewableTextNames = setOf(
    "changelog", "dockerfile", "license", "makefile", "readme"
)

internal fun isPreviewableTextFile(fileName: String): Boolean {
    val normalized = fileName.trim().lowercase()
    val extension = normalized.substringAfterLast('.', "")
    val baseName = normalized.substringBeforeLast('.', normalized)
    return extension in previewableTextExtensions || baseName in previewableTextNames
}

internal fun isMarkdownFile(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in setOf("markdown", "md", "mdown", "mkdn")

internal enum class MarkdownBlockKind {
    HEADING,
    PARAGRAPH,
    BULLET,
    QUOTE,
    CODE,
    DIVIDER
}

internal data class MarkdownPreviewBlock(
    val kind: MarkdownBlockKind,
    val text: String = "",
    val level: Int = 0
)

internal fun parseMarkdownPreview(content: String): List<MarkdownPreviewBlock> {
    val blocks = mutableListOf<MarkdownPreviewBlock>()
    val codeLines = mutableListOf<String>()
    var inCodeBlock = false

    fun flushCode() {
        if (codeLines.isNotEmpty() || inCodeBlock) {
            blocks += MarkdownPreviewBlock(MarkdownBlockKind.CODE, codeLines.joinToString("\n"))
            codeLines.clear()
        }
    }

    content.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) flushCode()
            inCodeBlock = !inCodeBlock
            return@forEach
        }
        if (inCodeBlock) {
            codeLines += line
            return@forEach
        }
        when {
            trimmed.isEmpty() -> Unit
            trimmed.matches(Regex("^([-*_])(?:\\s*\\1){2,}$")) ->
                blocks += MarkdownPreviewBlock(MarkdownBlockKind.DIVIDER)
            Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed) != null -> {
                val match = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)!!
                blocks += MarkdownPreviewBlock(
                    MarkdownBlockKind.HEADING,
                    match.groupValues[2],
                    match.groupValues[1].length
                )
            }
            trimmed.startsWith(">") -> blocks += MarkdownPreviewBlock(
                MarkdownBlockKind.QUOTE,
                trimmed.removePrefix(">").trimStart()
            )
            Regex("^[-+*]\\s+.+$").matches(trimmed) -> blocks += MarkdownPreviewBlock(
                MarkdownBlockKind.BULLET,
                "• " + trimmed.drop(1).trimStart()
            )
            Regex("^\\d+[.)]\\s+.+$").matches(trimmed) ->
                blocks += MarkdownPreviewBlock(MarkdownBlockKind.BULLET, trimmed)
            else -> blocks += MarkdownPreviewBlock(MarkdownBlockKind.PARAGRAPH, line)
        }
    }
    if (inCodeBlock || codeLines.isNotEmpty()) flushCode()
    return blocks
}

private val inlineMarkdownPattern = Regex(
    """\*\*([^*]+)\*\*|__([^_]+)__|`([^`]+)`|\[([^]]+)]\((https?://[^)]+)\)"""
)

internal fun formattedMarkdownInline(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    inlineMarkdownPattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() || match.groupValues[2].isNotEmpty() -> {
                val boldText = match.groupValues[1].ifEmpty { match.groupValues[2] }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(boldText)
                pop()
            }
            match.groupValues[3].isNotEmpty() -> {
                pushStyle(SpanStyle(color = MegaDrivePrimary, background = MegaDriveBg))
                append(match.groupValues[3])
                pop()
            }
            else -> {
                pushStyle(SpanStyle(color = MegaDrivePrimary, textDecoration = TextDecoration.Underline))
                append(match.groupValues[4])
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}

@Composable
internal fun RemoteTextPreviewDialog(
    fileName: String,
    content: String,
    markdown: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(8.dp))
                .background(MegaDriveSurface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "PREVIEW",
                        color = MegaDriveAccent,
                        fontSize = 11.sp,
                        fontFamily = MonoFontFamily
                    )
                    Text(
                        fileName,
                        color = MegaDrivePrimary,
                        fontSize = 14.sp,
                        fontFamily = MonoFontFamily,
                        maxLines = 2
                    )
                }
                RetroButton(text = "CLOSE", onClick = onDismiss)
            }

            SelectionContainer(modifier = Modifier.weight(1f)) {
                if (markdown) {
                    MarkdownPreview(content)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("remote-text-preview")
                            .background(MegaDriveBg, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        item {
                            Text(
                                content.ifEmpty { "(empty file)" },
                                color = MegaDriveOnSurface,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                fontFamily = MonoFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownPreview(content: String) {
    val blocks = parseMarkdownPreview(content)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("remote-text-preview")
            .background(Color(0xFF11111E), RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (blocks.isEmpty()) {
            item { Text("(empty file)", color = MegaDriveDim, fontSize = 13.sp) }
        }
        itemsIndexed(blocks, key = { index, _ -> index }) { _, block ->
            when (block.kind) {
                MarkdownBlockKind.HEADING -> Text(
                    formattedMarkdownInline(block.text),
                    color = MegaDrivePrimary,
                    fontSize = (23 - block.level.coerceIn(1, 6) * 1.5f).sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold
                )
                MarkdownBlockKind.PARAGRAPH,
                MarkdownBlockKind.BULLET -> Text(
                    formattedMarkdownInline(block.text),
                    color = MegaDriveOnSurface,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
                MarkdownBlockKind.QUOTE -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MegaDriveAccent.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        formattedMarkdownInline(block.text),
                        color = MegaDriveOnSurface,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
                MarkdownBlockKind.CODE -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MegaDriveBg, RoundedCornerShape(4.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        block.text,
                        color = MegaDrivePrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = MonoFontFamily
                    )
                }
                MarkdownBlockKind.DIVIDER -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MegaDriveDim.copy(alpha = 0.55f))
                        .widthIn(min = 1.dp)
                        .padding(top = 1.dp)
                )
            }
        }
    }
}
