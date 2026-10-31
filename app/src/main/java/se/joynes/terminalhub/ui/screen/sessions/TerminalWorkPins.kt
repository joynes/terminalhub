package se.joynes.terminalhub.ui.screen.sessions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Stable tmux keys distinguish a new window from one reusing an old tmux ID. */
data class TerminalWorkPin(val projectId: Long, val windowKey: String, val name: String) {
    fun matches(project: Long?, key: String) = projectId == project && key.isNotBlank() && windowKey == key
    fun encode(): String = "$projectId:$windowKey:" + name.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
    companion object {
        fun decode(value: String): TerminalWorkPin? = runCatching {
            require(value.length <= 9000)
            val parts = value.split(':', limit = 3)
            require(parts.size == 3 && parts[1].matches(Regex("[a-zA-Z0-9@_-]{1,128}")))
            TerminalWorkPin(parts[0].toLong().also { require(it > 0) }, parts[1],
                String(parts[2].also { require(it.length % 2 == 0) }.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8).also { require(it.length <= 1024) })
        }.getOrNull()
    }
}

class TerminalWorkPins(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("terminal_work_pins", Context.MODE_PRIVATE)
    private fun read() = prefs.getStringSet("windows", emptySet()).orEmpty().mapNotNull(TerminalWorkPin::decode).toSet()
    private val mutable = MutableStateFlow(read())
    val pins = mutable.asStateFlow()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> mutable.value = read() }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun close() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    fun toggle(pin: TerminalWorkPin) {
        if (pin.windowKey.isBlank()) return
        val current = mutable.value
        val remaining = current.filterNot { it.matches(pin.projectId, pin.windowKey) }.toSet()
        val next = if (remaining.size == current.size) remaining + pin else remaining
        prefs.edit().putStringSet("windows", next.map { it.encode() }.toSet()).apply()
        mutable.value = next
    }
}

@Composable
fun PinnedWorkDialog(pins: Set<TerminalWorkPin>, projectName: (Long) -> String,
    windowName: (TerminalWorkPin) -> String, onChoose: (TerminalWorkPin) -> Unit,
    onRemove: (TerminalWorkPin) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("📌 Active work") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (pins.isEmpty()) Text("Hold a terminal tab and choose Mark as active work. For a single terminal, use Terminal information in the project menu.")
            pins.sortedWith(compareBy({ projectName(it.projectId) }, { windowName(it) })).forEach { pin ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onChoose(pin) }, modifier = Modifier.weight(1f)) {
                        Text("${projectName(pin.projectId)} → ${windowName(pin)}")
                    }
                    TextButton(onClick = { onRemove(pin) }) { Text("Unpin") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } })
}
