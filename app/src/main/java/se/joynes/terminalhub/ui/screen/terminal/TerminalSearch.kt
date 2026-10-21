package se.joynes.terminalhub.ui.screen.terminal

internal data class TerminalSearchRow(val row: Int, val text: String, val wraps: Boolean)

/** Search logical lines: a screen wrap joins text, a real newline never does. */
internal fun findTerminalMatches(rows: List<TerminalSearchRow>, query: String, matchCase: Boolean): List<SearchMatch> {
    if (query.isEmpty()) return emptyList()
    val found = mutableListOf<SearchMatch>()
    val text = StringBuilder()
    val segments = mutableListOf<Pair<Int, Int>>()
    fun finish() {
        var start = 0
        while (true) {
            val index = text.indexOf(query, start, ignoreCase = !matchCase)
            if (index < 0) break
            val row = segments.lastOrNull { it.first <= index }?.second
            if (row != null && found.lastOrNull()?.row != row) found += SearchMatch(row)
            start = index + query.length
        }
        text.clear(); segments.clear()
    }
    for (line in rows) {
        segments += text.length to line.row
        text.append(line.text)
        if (!line.wraps) finish()
    }
    finish()
    return found
}
