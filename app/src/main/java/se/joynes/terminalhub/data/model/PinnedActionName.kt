package se.joynes.terminalhub.data.model

/** Keep unnamed actions recognizable without displaying an entire prompt as their title. */
fun pinnedActionName(name: String, text: String): String {
    if (name.isNotBlank()) return name.trim()
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val points = firstLine.codePoints().toArray()
    return if (points.size <= 40) firstLine else String(points, 0, 40) + "…"
}
