package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal fun terminalTextInputPayload(text: String, executeImmediately: Boolean): String {
    if (!executeImmediately || text.endsWith('\n') || text.endsWith('\r')) return text
    return "$text\r"
}

internal fun insertTextAtCursor(value: TextFieldValue, insertedText: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val updated = value.text.replaceRange(start, end, insertedText)
    val cursor = start + insertedText.length
    return TextFieldValue(updated, TextRange(cursor))
}
