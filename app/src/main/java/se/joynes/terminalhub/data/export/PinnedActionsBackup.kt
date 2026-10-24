package se.joynes.terminalhub.data.export

import org.json.JSONArray
import org.json.JSONObject
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity
import se.joynes.terminalhub.data.model.PinnedMacroCodec

/** Explicit configuration backups include intentional pins, never raw input history. */
internal fun encodePinnedActions(actions: List<PinnedActionEntity>): String = JSONArray().apply {
    actions.forEach { action ->
        val item = JSONObject().put("name", action.name).put("text", action.text).put("sendEnter", !action.isMacro && action.sendEnter)
        if (action.isMacro) item.put("macro", JSONObject(action.macroJson))
        put(item)
    }
}.toString()

internal fun decodePinnedActions(value: String?, projectId: Long? = null): List<PinnedActionEntity> {
    if (value == null) return emptyList()
    val array = JSONArray(value)
    return (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val name = item.getString("name")
        val text = item.getString("text")
        val macro = if (item.has("macro")) item.getJSONObject("macro").toString().also(PinnedMacroCodec::decode) else ""
        require(name.isNotBlank() && (text.isNotBlank() || macro.isNotEmpty())) { "Pinned actions require a name and input" }
        PinnedActionEntity(
            name = name, text = text, sendEnter = if (macro.isNotEmpty()) false else item.getBoolean("sendEnter"), macroJson = macro,
            scope = if (projectId == null) "GLOBAL" else "PROJECT", projectId = projectId
        )
    }
}
