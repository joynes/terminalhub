package se.joynes.terminalhub.data.settings

import android.view.KeyEvent

enum class ShortcutAction(val label: String) {
    NEXT_PROJECT("Next project"), PREVIOUS_PROJECT("Previous project"),
    NEXT_WINDOW("Next terminal window"), PREVIOUS_WINDOW("Previous terminal window"),
    FIND_PROJECT("Find project or terminal window"),
    NEW_PROJECT("Create project"), CLOSE_PROJECT("Close project"),
    NEW_WINDOW("Create terminal window"), CLOSE_WINDOW("Close terminal window"),
    RENAME_WINDOW("Rename terminal window")
}

data class ShortcutChord(val keyCode: Int, val modifiers: Int) {
    fun encode(): String = buildList {
        if (modifiers and 1 != 0) add("CTRL")
        if (modifiers and 2 != 0) add("ALT")
        if (modifiers and 4 != 0) add("SHIFT")
        if (modifiers and 8 != 0) add("META")
        add(requireNotNull(KeyboardShortcuts.keys.entries.firstOrNull { it.value == keyCode }?.key))
    }.joinToString("+")
}

/** Portable action=chord strings. No bindings are enabled until the user chooses them. */
object KeyboardShortcuts {
    val keys: Map<String, Int> = buildMap {
        ('A'..'Z').forEach { put(it.toString(), KeyEvent.KEYCODE_A + (it - 'A')) }
        ('0'..'9').forEach { put(it.toString(), KeyEvent.KEYCODE_0 + (it - '0')) }
        (1..12).forEach { put("F$it", KeyEvent.KEYCODE_F1 + it - 1) }
        putAll(mapOf("LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
            "TAB" to KeyEvent.KEYCODE_TAB, "ENTER" to KeyEvent.KEYCODE_ENTER, "ESCAPE" to KeyEvent.KEYCODE_ESCAPE,
            "SPACE" to KeyEvent.KEYCODE_SPACE, "PAGE_UP" to KeyEvent.KEYCODE_PAGE_UP, "PAGE_DOWN" to KeyEvent.KEYCODE_PAGE_DOWN,
            "HOME" to KeyEvent.KEYCODE_MOVE_HOME, "END" to KeyEvent.KEYCODE_MOVE_END,
            "BACKSPACE" to KeyEvent.KEYCODE_DEL, "DELETE" to KeyEvent.KEYCODE_FORWARD_DEL, "INSERT" to KeyEvent.KEYCODE_INSERT,
            "COMMA" to KeyEvent.KEYCODE_COMMA, "PERIOD" to KeyEvent.KEYCODE_PERIOD, "SLASH" to KeyEvent.KEYCODE_SLASH,
            "BACKSLASH" to KeyEvent.KEYCODE_BACKSLASH, "MINUS" to KeyEvent.KEYCODE_MINUS, "EQUALS" to KeyEvent.KEYCODE_EQUALS,
            "SEMICOLON" to KeyEvent.KEYCODE_SEMICOLON, "APOSTROPHE" to KeyEvent.KEYCODE_APOSTROPHE,
            "LEFT_BRACKET" to KeyEvent.KEYCODE_LEFT_BRACKET, "RIGHT_BRACKET" to KeyEvent.KEYCODE_RIGHT_BRACKET,
            "GRAVE" to KeyEvent.KEYCODE_GRAVE, "PLUS" to KeyEvent.KEYCODE_PLUS))
    }
    fun validate(chord: ShortcutChord) {
        require(chord.keyCode in keys.values && chord.modifiers in 0..15) { "This key cannot be used. Try another combination." }
        require(chord.modifiers and 11 != 0 || chord.keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
            "Use Ctrl, Alt or Meta with the key, or choose an F1–F12 key."
        }
        require(!(chord.keyCode == KeyEvent.KEYCODE_TAB && chord.modifiers and 2 != 0) &&
            !(chord.keyCode == KeyEvent.KEYCODE_SPACE && chord.modifiers and 3 != 0)) {
            "Android uses this combination for switching apps or keyboard languages. Choose another."
        }
    }
    fun parseChord(text: String): ShortcutChord {
        val tokens = text.split('+')
        require(tokens.isNotEmpty() && tokens.distinct().size == tokens.size) { "Invalid shortcut: $text" }
        val key = requireNotNull(keys[tokens.last()]) { "Unknown shortcut key: ${tokens.last()}" }
        var modifiers = 0
        tokens.dropLast(1).forEach { modifiers = modifiers or when (it) {
            "CTRL" -> 1; "ALT" -> 2; "SHIFT" -> 4; "META" -> 8; else -> error("Unknown modifier: $it")
        } }
        return ShortcutChord(key, modifiers).also(::validate)
    }
    fun parse(rows: List<String>): Map<ShortcutAction, ShortcutChord> {
        require(rows.size <= ShortcutAction.entries.size + 1) { "Too many keyboard shortcuts." }
        val result = linkedMapOf<ShortcutAction, ShortcutChord>()
        val hasProjectFind = rows.any { it.startsWith("FIND_PROJECT=") }
        rows.forEach { row ->
            val parts = row.split('='); require(parts.size == 2) { "Use ACTION=CTRL+ALT+KEY." }
            // Older exports had two find actions. Prefer the project binding when both exist.
            if (parts[0] == "FIND_WINDOW" && hasProjectFind) { parseChord(parts[1]); return@forEach }
            val name = if (parts[0] == "FIND_WINDOW") "FIND_PROJECT" else parts[0]
            val action = requireNotNull(ShortcutAction.entries.firstOrNull { it.name == name }) { "Unknown shortcut action: ${parts[0]}" }
            require(action !in result) { "Duplicate shortcut action: ${action.label}" }
            val chord = parseChord(parts[1])
            require(chord !in result.values) { "The same shortcut cannot control two actions." }
            result[action] = chord
        }
        return result
    }
    fun assign(rows: List<String>, action: ShortcutAction, chord: ShortcutChord?): List<String> {
        chord?.let(::validate)
        val bindings = parse(rows).toMutableMap()
        bindings.remove(action)
        require(chord == null || chord !in bindings.values) { "This shortcut is already assigned. Clear it first." }
        if (chord != null) bindings[action] = chord
        return ShortcutAction.entries.mapNotNull { key -> bindings[key]?.let { "${key.name}=${it.encode()}" } }
    }
    fun normalize(rows: List<String>): List<String> = parse(rows).map { (action, chord) -> "${action.name}=${chord.encode()}" }
    val suggestions = listOf("NEXT_PROJECT=CTRL+ALT+RIGHT", "PREVIOUS_PROJECT=CTRL+ALT+LEFT",
        "NEXT_WINDOW=CTRL+ALT+DOWN", "PREVIOUS_WINDOW=CTRL+ALT+UP", "FIND_PROJECT=CTRL+ALT+P",
        "NEW_PROJECT=CTRL+ALT+N", "CLOSE_PROJECT=CTRL+ALT+Q",
        "NEW_WINDOW=CTRL+ALT+T", "CLOSE_WINDOW=CTRL+ALT+W", "RENAME_WINDOW=CTRL+ALT+R")
}

/** Stable subsequence ranking, so 'trm' matches 'terminal'; contiguous and word-prefix matches rank first. */
fun fuzzyShortcutScore(query: String, name: String): Int? {
    val needles = query.lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
    val haystack = name.lowercase()
    var score = 0
    for (needle in needles) {
        val contiguous = haystack.indexOf(needle)
        if (contiguous >= 0) { score += contiguous + if (contiguous == 0) 0 else 10; continue }
        var previous = -1
        for (character in needle) {
            val index = haystack.indexOf(character, previous + 1)
            if (index < 0) return null
            score += 30 + index - previous - 1
            previous = index
        }
    }
    return score
}

fun cyclicShortcutIndex(size: Int, current: Int, forward: Boolean): Int? =
    if (size == 0) null else if (current !in 0 until size) if (forward) 0 else size - 1
    else (current + if (forward) 1 else size - 1) % size
