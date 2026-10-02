package se.joynes.terminalhub.domain

/** Approximate user-input drafts, never terminal output. Cursor editing is deliberately not emulated. */
class TerminalInputHistoryRecorder(private val save: (Long, String) -> Unit) {
    private val drafts = mutableMapOf<Long, StringBuilder>()

    @Synchronized
    fun input(projectId: Long, text: String) {
        // Key events deliver complete escape sequences. Ignore navigation/function/Alt keys.
        if (text.startsWith('\u001b')) return
        val draft = drafts.getOrPut(projectId) { StringBuilder() }
        text.codePoints().forEach { code ->
            when (code) {
                3 -> draft.clear() // Ctrl+C
                8, 127 -> if (draft.isNotEmpty()) {
                    draft.delete(draft.offsetByCodePoints(draft.length, -1), draft.length)
                }
                10, 13 -> {
                    val submitted = draft.toString()
                    draft.clear()
                    if (submitted.isNotBlank()) save(projectId, submitted)
                }
                9 -> draft.append('\t')
                else -> if (!Character.isISOControl(code)) draft.appendCodePoint(code)
            }
        }
    }

    /** Paste is one semantic operation: embedded newlines are not simulated Enter keystrokes. */
    @Synchronized
    fun paste(projectId: Long, text: String) {
        drafts.getOrPut(projectId) { StringBuilder() }.append(text)
    }

    @Synchronized
    fun complete(projectId: Long, text: String) {
        drafts.remove(projectId)
        if (text.isNotBlank()) save(projectId, text)
    }

    @Synchronized
    fun forget(projectId: Long) { drafts.remove(projectId) }
}
