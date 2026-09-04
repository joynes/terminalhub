package se.joynes.terminalhub.data.settings

data class KeyBarKeyDefinition(
    val id: String,
    val label: String,
    val group: String,
    val text: String? = null
)

object KeyBarLayoutConfig {
    const val MAX_ROWS = 4
    const val MAX_KEYS_PER_ROW = 12

    val availableKeys: List<KeyBarKeyDefinition> = buildList {
        addAll(
            listOf(
                KeyBarKeyDefinition("ESC", "ESC", "Terminal"),
                KeyBarKeyDefinition("TAB", "TAB", "Terminal"),
                KeyBarKeyDefinition("ENTER", "ENTER", "Terminal"),
                KeyBarKeyDefinition("BACKSPACE", "BKSP", "Terminal"),
                KeyBarKeyDefinition("CTRL", "CTRL", "Modifiers"),
                KeyBarKeyDefinition("ALT", "ALT", "Modifiers"),
                KeyBarKeyDefinition("SHIFT", "SHIFT", "Modifiers")
            )
        )
        // Keep symbols close to the top of the picker: they are the keys most often
        // missing from Android's compact terminal keyboard.
        addAll(
            listOf(
                KeyBarKeyDefinition("EXCLAMATION", "!", "Symbols", "!"),
                KeyBarKeyDefinition("DOUBLE_QUOTE", "\"", "Symbols", "\""),
                KeyBarKeyDefinition("HASH", "#", "Symbols", "#"),
                KeyBarKeyDefinition("DOLLAR", "\$", "Symbols", "\$"),
                KeyBarKeyDefinition("PERCENT", "%", "Symbols", "%"),
                KeyBarKeyDefinition("AMPERSAND", "&", "Symbols", "&"),
                KeyBarKeyDefinition("SINGLE_QUOTE", "'", "Symbols", "'"),
                KeyBarKeyDefinition("LEFT_PAREN", "(", "Symbols", "("),
                KeyBarKeyDefinition("RIGHT_PAREN", ")", "Symbols", ")"),
                KeyBarKeyDefinition("ASTERISK", "*", "Symbols", "*"),
                KeyBarKeyDefinition("PLUS", "+", "Symbols", "+"),
                KeyBarKeyDefinition("COMMA", ",", "Symbols", ","),
                KeyBarKeyDefinition("DASH", "-", "Symbols", "-"),
                KeyBarKeyDefinition("DOT", ".", "Symbols", "."),
                KeyBarKeyDefinition("SLASH", "/", "Symbols", "/"),
                KeyBarKeyDefinition("COLON", ":", "Symbols", ":"),
                KeyBarKeyDefinition("SEMICOLON", ";", "Symbols", ";"),
                KeyBarKeyDefinition("LESS_THAN", "<", "Symbols", "<"),
                KeyBarKeyDefinition("EQUALS", "=", "Symbols", "="),
                KeyBarKeyDefinition("GREATER_THAN", ">", "Symbols", ">"),
                KeyBarKeyDefinition("QUESTION", "?", "Symbols", "?"),
                KeyBarKeyDefinition("AT", "@", "Symbols", "@"),
                KeyBarKeyDefinition("LEFT_BRACKET", "[", "Symbols", "["),
                KeyBarKeyDefinition("BACKSLASH", "\\", "Symbols", "\\"),
                KeyBarKeyDefinition("RIGHT_BRACKET", "]", "Symbols", "]"),
                KeyBarKeyDefinition("CARET", "^", "Symbols", "^"),
                KeyBarKeyDefinition("UNDERSCORE", "_", "Symbols", "_"),
                KeyBarKeyDefinition("BACKTICK", "`", "Symbols", "`"),
                KeyBarKeyDefinition("LEFT_BRACE", "{", "Symbols", "{"),
                KeyBarKeyDefinition("PIPE", "|", "Symbols", "|"),
                KeyBarKeyDefinition("RIGHT_BRACE", "}", "Symbols", "}"),
                KeyBarKeyDefinition("TILDE", "~", "Symbols", "~"),
                KeyBarKeyDefinition("SPACE", "SPACE", "Symbols", " ")
            )
        )
        addAll(
            listOf(
                KeyBarKeyDefinition("UP", "UP", "Navigation"),
                KeyBarKeyDefinition("DOWN", "DOWN", "Navigation"),
                KeyBarKeyDefinition("LEFT", "LEFT", "Navigation"),
                KeyBarKeyDefinition("RIGHT", "RIGHT", "Navigation"),
                KeyBarKeyDefinition("HOME", "HOME", "Navigation"),
                KeyBarKeyDefinition("END", "END", "Navigation"),
                KeyBarKeyDefinition("PAGE_UP", "PGUP", "Navigation"),
                KeyBarKeyDefinition("PAGE_DOWN", "PGDN", "Navigation"),
                KeyBarKeyDefinition("KEYBOARD", "KEYBOARD", "Actions"),
                KeyBarKeyDefinition("TEXT_INPUT", "TEXT", "Actions"),
                KeyBarKeyDefinition("UPLOAD", "UPLOAD", "Actions"),
                KeyBarKeyDefinition("DOWNLOAD", "DOWNLOAD", "Actions"),
                KeyBarKeyDefinition("PASTE", "PASTE", "Actions")
            )
        )
        ('A'..'Z').forEach { letter ->
            add(KeyBarKeyDefinition("CHAR_$letter", letter.toString(), "Letters", letter.lowercase()))
        }
        ('0'..'9').forEach { digit ->
            add(KeyBarKeyDefinition("DIGIT_$digit", digit.toString(), "Numbers", digit.toString()))
        }
    }

    private val definitionsById = availableKeys.associateBy { it.id }

    val defaultRows: List<List<String>> = listOf(
        listOf("ESC", "TAB", "COLON", "SLASH", "AT", "DIGIT_1", "DIGIT_2", "DIGIT_3", "DOWNLOAD", "UP", "ENTER"),
        listOf("CTRL", "ALT", "SHIFT", "KEYBOARD", "TEXT_INPUT", "UPLOAD", "LEFT", "DOWN", "RIGHT")
    )

    fun definition(id: String): KeyBarKeyDefinition? = definitionsById[id]

    fun normalize(rows: List<List<String>>): List<List<String>> {
        val normalized = rows
            .take(MAX_ROWS)
            .map { row -> row.filter(definitionsById::containsKey).take(MAX_KEYS_PER_ROW) }
            .filter { it.isNotEmpty() }
        return normalized.ifEmpty { defaultRows }
    }

    fun encode(rows: List<List<String>>): String =
        normalize(rows).joinToString("|") { row -> row.joinToString(",") }

    fun decode(encoded: String?): List<List<String>> {
        if (encoded.isNullOrBlank()) return defaultRows
        return normalize(encoded.split('|').map { row -> row.split(',').filter(String::isNotBlank) })
    }

    fun replaceKey(rows: List<List<String>>, rowIndex: Int, keyIndex: Int, keyId: String): List<List<String>> {
        if (definition(keyId) == null || rowIndex !in rows.indices || keyIndex !in rows[rowIndex].indices) return normalize(rows)
        return normalize(rows.mapIndexed { index, row ->
            if (index == rowIndex) row.toMutableList().apply { this[keyIndex] = keyId } else row
        })
    }

    fun addKey(rows: List<List<String>>, rowIndex: Int, keyId: String): List<List<String>> {
        if (definition(keyId) == null || rowIndex !in rows.indices || rows[rowIndex].size >= MAX_KEYS_PER_ROW) return normalize(rows)
        return normalize(rows.mapIndexed { index, row -> if (index == rowIndex) row + keyId else row })
    }

    fun removeKey(rows: List<List<String>>, rowIndex: Int, keyIndex: Int): List<List<String>> {
        if (rowIndex !in rows.indices || keyIndex !in rows[rowIndex].indices) return normalize(rows)
        return normalize(rows.mapIndexed { index, row ->
            if (index == rowIndex) row.filterIndexed { itemIndex, _ -> itemIndex != keyIndex } else row
        })
    }

    fun addRow(rows: List<List<String>>): List<List<String>> =
        if (rows.size >= MAX_ROWS) normalize(rows) else normalize(rows + listOf(listOf("CHAR_C")))

    fun removeRow(rows: List<List<String>>, rowIndex: Int): List<List<String>> {
        if (rows.size <= 1 || rowIndex !in rows.indices) return normalize(rows)
        return normalize(rows.filterIndexed { index, _ -> index != rowIndex })
    }

    fun moveRow(rows: List<List<String>>, fromIndex: Int, toIndex: Int): List<List<String>> {
        if (fromIndex !in rows.indices || toIndex !in rows.indices || fromIndex == toIndex) return normalize(rows)
        val updated = rows.toMutableList()
        val row = updated.removeAt(fromIndex)
        updated.add(toIndex, row)
        return normalize(updated)
    }
}
