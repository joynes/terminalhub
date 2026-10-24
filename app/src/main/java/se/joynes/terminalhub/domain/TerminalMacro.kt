package se.joynes.terminalhub.domain

import android.view.KeyEvent
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalKeyEncoding
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

sealed interface MacroStep {
    data class Text(val text: String) : MacroStep
    data class Key(val key: String, val ctrl: Boolean = false, val alt: Boolean = false,
        val shift: Boolean = false, val repeat: Int = 1) : MacroStep
    data class Pause(val milliseconds: Long) : MacroStep
}

object TerminalMacro {
    const val MAX_STEPS = 100
    val namedKeys = linkedMapOf(
        "ENTER" to KeyEvent.KEYCODE_ENTER, "TAB" to KeyEvent.KEYCODE_TAB,
        "ESC" to KeyEvent.KEYCODE_ESCAPE, "BACKSPACE" to KeyEvent.KEYCODE_DEL,
        "DELETE" to KeyEvent.KEYCODE_FORWARD_DEL, "INSERT" to KeyEvent.KEYCODE_INSERT,
        "UP" to KeyEvent.KEYCODE_DPAD_UP, "DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
        "LEFT" to KeyEvent.KEYCODE_DPAD_LEFT, "RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "HOME" to KeyEvent.KEYCODE_MOVE_HOME, "END" to KeyEvent.KEYCODE_MOVE_END,
        "PAGE_UP" to KeyEvent.KEYCODE_PAGE_UP, "PAGE_DOWN" to KeyEvent.KEYCODE_PAGE_DOWN
    ) + (1..12).associate { "F$it" to KeyEvent.KEYCODE_F1 + it - 1 }

    fun validate(steps: List<MacroStep>) {
        require(steps.isNotEmpty() && steps.size <= MAX_STEPS) { "Use between 1 and $MAX_STEPS steps." }
        var textSize = 0L
        var keyCount = 0L
        var pauseTotal = 0L
        steps.forEach { step ->
            when (step) {
                is MacroStep.Text -> {
                    require(step.text.isNotEmpty() && step.text.length <= 65_536) { "Text steps need 1–65,536 characters." }
                    textSize += step.text.length
                }
                is MacroStep.Key -> {
                    require(step.repeat in 1..100) { "Repeat a key between 1 and 100 times." }
                    encodeKey(step)
                    keyCount += step.repeat
                }
                is MacroStep.Pause -> {
                    require(step.milliseconds in 0..60_000) { "Each pause must be between 0 and 60,000 ms." }
                    pauseTotal += step.milliseconds
                }
            }
        }
        require(textSize <= 262_144) { "A macro can contain up to 262,144 text characters." }
        require(keyCount <= 1000) { "A macro can contain up to 1,000 key presses." }
        require(pauseTotal <= 300_000) { "Total pauses can be at most 5 minutes." }
    }

    fun encodeKey(step: MacroStep.Key, cursorApplication: Boolean = false, keypadApplication: Boolean = false): String {
        val name = step.key.trim().uppercase(Locale.ROOT)
        namedKeys[name]?.let { keyCode ->
            val modifiers = (if (step.ctrl) KeyHandler.KEYMOD_CTRL else 0) or
                (if (step.alt) KeyHandler.KEYMOD_ALT else 0) or (if (step.shift) KeyHandler.KEYMOD_SHIFT else 0)
            return requireNotNull(KeyHandler.getCode(keyCode, modifiers, cursorApplication, keypadApplication)) { "Unsupported key combination." }
        }
        val key = if (name == "SPACE") " " else step.key
        require(key.codePointCount(0, key.length) == 1 && !Character.isISOControl(key.codePointAt(0))) {
            "Choose a named key or enter one character, such as C or /."
        }
        val character = if (step.shift) key.uppercase(Locale.ROOT) else key.lowercase(Locale.ROOT)
        val value = if (step.ctrl) String(Character.toChars(TerminalKeyEncoding.controlCodePoint(key.codePointAt(0)))) else character
        return if (step.alt) "\u001b$value" else value
    }

    fun stepLabel(step: MacroStep): String = when (step) {
        is MacroStep.Text -> "Text: " + step.text.replace('\n', ' ').replace('\r', ' ').take(50)
        is MacroStep.Pause -> "Wait ${step.milliseconds} ms"
        is MacroStep.Key -> listOfNotNull(if (step.ctrl) "Ctrl" else null, if (step.alt) "Alt" else null,
            if (step.shift) "Shift" else null, step.key.uppercase(Locale.ROOT)).joinToString("+") +
            if (step.repeat > 1) " ×${step.repeat}" else ""
    }

    fun summary(steps: List<MacroStep>): String = steps.joinToString(" → ", transform = ::stepLabel)
    fun interruptAndSend(text: String) = listOf(MacroStep.Key("C", ctrl = true), MacroStep.Pause(300), MacroStep.Text(text), MacroStep.Key("ENTER"))
}

/** Captured terminal identity. Implementations must reject disconnected/replaced sessions. */
interface MacroTerminalTarget {
    fun isConnected(): Boolean
    fun paste(text: String)
    fun press(key: MacroStep.Key)
    suspend fun flush()
}

/** Sequential input, not shell exec. No inferred prompt detection or automatic retry. */
class TerminalMacroRunner(private val sleep: suspend (Long) -> Unit = { delay(it) }) {
    suspend fun run(steps: List<MacroStep>, target: MacroTerminalTarget, onStep: (Int, Int) -> Unit = { _, _ -> }) {
        TerminalMacro.validate(steps)
        var pendingPasteDelay = 0L
        fun checkTarget() {
            check(target.isConnected()) { "Macro stopped: its terminal disconnected or was replaced. Reconnect before running it again." }
        }
        suspend fun settle(milliseconds: Long) {
            // Detect a disconnect during a long user pause, even if the server returns later.
            var remaining = milliseconds
            while (remaining > 0) {
                currentCoroutineContext().ensureActive()
                checkTarget()
                val duration = minOf(remaining, 100)
                sleep(duration)
                remaining -= duration
            }
            checkTarget()
        }
        withTimeout(360_000) {
            steps.forEachIndexed { index, step ->
                currentCoroutineContext().ensureActive()
                checkTarget()
                onStep(index + 1, steps.size)
                when (step) {
                    is MacroStep.Text -> {
                        target.paste(step.text)
                        withTimeout(15_000) { target.flush() }
                        pendingPasteDelay = (300L + step.text.length * 5L).coerceAtMost(5000)
                    }
                    is MacroStep.Key -> {
                        if (pendingPasteDelay > 0) { settle(pendingPasteDelay); pendingPasteDelay = 0 }
                        repeat(step.repeat) {
                            currentCoroutineContext().ensureActive()
                            checkTarget()
                            target.press(step)
                            withTimeout(15_000) { target.flush() }
                            if (it < step.repeat - 1 || index < steps.lastIndex) settle(80)
                        }
                    }
                    is MacroStep.Pause -> {
                        settle(step.milliseconds)
                        pendingPasteDelay = (pendingPasteDelay - step.milliseconds).coerceAtLeast(0)
                    }
                }
            }
        }
    }
}
