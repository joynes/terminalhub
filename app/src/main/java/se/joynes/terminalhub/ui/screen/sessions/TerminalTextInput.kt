package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal const val MIN_TEXT_INPUT_SUBMIT_DELAY_MS = 300L
internal const val MAX_TEXT_INPUT_SUBMIT_DELAY_MS = 5_000L
private const val TEXT_INPUT_SUBMIT_DELAY_PER_CHARACTER_MS = 5L

internal data class TerminalTextInputSubmission(
    val pasteText: String,
    val sendEnter: Boolean,
    val enterDelayMs: Long
)

/**
 * Interactive TUIs distinguish pasted text from a real Enter key. Keep the paste and Enter as
 * separate writes so burst-paste detection (notably in Codex) cannot absorb Enter into the paste.
 */
internal fun terminalTextInputSubmission(
    text: String,
    executeImmediately: Boolean
): TerminalTextInputSubmission {
    if (!executeImmediately) {
        return TerminalTextInputSubmission(text, sendEnter = false, enterDelayMs = 0L)
    }

    val pasteText = text.removeSingleTrailingLineEnding()
    val delayMs = (MIN_TEXT_INPUT_SUBMIT_DELAY_MS +
        pasteText.length * TEXT_INPUT_SUBMIT_DELAY_PER_CHARACTER_MS)
        .coerceAtMost(MAX_TEXT_INPUT_SUBMIT_DELAY_MS)
    return TerminalTextInputSubmission(pasteText, sendEnter = true, enterDelayMs = delayMs)
}

private fun String.removeSingleTrailingLineEnding(): String = when {
    endsWith("\r\n") -> dropLast(2)
    endsWith('\n') || endsWith('\r') -> dropLast(1)
    else -> this
}

internal fun insertTextAtCursor(value: TextFieldValue, insertedText: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val updated = value.text.replaceRange(start, end, insertedText)
    val cursor = start + insertedText.length
    return TextFieldValue(updated, TextRange(cursor))
}
