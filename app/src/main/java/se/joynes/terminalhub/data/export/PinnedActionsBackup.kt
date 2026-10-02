package se.joynes.terminalhub.data.export

import org.json.JSONArray
import org.json.JSONObject
import se.joynes.terminalhub.data.db.entity.PinnedActionEntity

/** Explicit configuration backups include intentional pins, never raw input history. */
internal fun encodePinnedActions(actions: List<PinnedActionEntity>): String = JSONArray().apply {
    actions.forEach { action ->
        put(JSONObject().put("name", action.name).put("text", action.text).put("sendEnter", action.sendEnter))
    }
}.toString()

internal fun decodePinnedActions(value: String?, projectId: Long? = null): List<PinnedActionEntity> {
    if (value == null) return emptyList()
    val array = JSONArray(value)
    return (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val name = item.getString("name")
        val text = item.getString("text")
        require(name.isNotBlank() && text.isNotBlank()) { "Pinned actions require a name and input" }
        PinnedActionEntity(
            name = name, text = text, sendEnter = item.getBoolean("sendEnter"),
            scope = if (projectId == null) "GLOBAL" else "PROJECT", projectId = projectId
        )
    }
}
