package se.joynes.terminalhub.data.model

import org.json.JSONArray
import org.json.JSONObject
import se.joynes.terminalhub.domain.MacroStep
import se.joynes.terminalhub.domain.TerminalMacro

/** Versioned optional payload: empty means a legacy plain-text pinned action. */
object PinnedMacroCodec {
    fun encode(steps: List<MacroStep>): String {
        TerminalMacro.validate(steps)
        val items = JSONArray()
        steps.forEach { step -> items.put(when (step) {
            is MacroStep.Text -> JSONObject().put("type", "text").put("text", step.text)
            is MacroStep.Pause -> JSONObject().put("type", "pause").put("milliseconds", step.milliseconds)
            is MacroStep.Key -> JSONObject().put("type", "key").put("key", step.key).put("ctrl", step.ctrl)
                .put("alt", step.alt).put("shift", step.shift).put("repeat", step.repeat)
        }) }
        return JSONObject().put("version", 1).put("steps", items).toString()
    }

    fun decode(value: String): List<MacroStep> {
        require(value.length <= 1_048_576) { "Macro is too large." }
        val root = JSONObject(value)
        require(root.getInt("version") == 1) { "Unsupported macro version. Update TerminalHub to use this macro." }
        val items = root.getJSONArray("steps")
        require(items.length() in 1..TerminalMacro.MAX_STEPS) { "Use between 1 and ${TerminalMacro.MAX_STEPS} steps." }
        return (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            when (item.getString("type")) {
                "text" -> MacroStep.Text(item.getString("text"))
                "pause" -> MacroStep.Pause(item.getLong("milliseconds"))
                "key" -> MacroStep.Key(item.getString("key"), item.getBoolean("ctrl"), item.getBoolean("alt"),
                    item.getBoolean("shift"), item.getInt("repeat"))
                else -> error("Unknown macro step. Update TerminalHub to use this macro.")
            }
        }.also(TerminalMacro::validate)
    }
}
