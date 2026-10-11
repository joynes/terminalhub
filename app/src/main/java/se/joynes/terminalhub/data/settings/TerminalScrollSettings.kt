package se.joynes.terminalhub.data.settings

fun TerminalScrollSettings.applyTo(view: com.termux.view.TerminalView) = view.setScrollPreferences(
    terminalSpeed, remoteSpeed, inertia, flingSpeed, braking, minimumFlickSpeed,
    remoteFlingLimit, remoteEventsPerFrame, reverseDirection
)

data class TerminalScrollSettings(
    val terminalSpeed: Float = 1.5f,
    val remoteSpeed: Float = 1f,
    val inertia: Boolean = true,
    val flingSpeed: Float = 1f,
    val braking: Float = 1f,
    val minimumFlickSpeed: Float = 150f,
    val remoteFlingLimit: Int = 120,
    val remoteEventsPerFrame: Int = 6,
    val reverseDirection: Boolean = false
) {
    fun normalized() = copy(
        terminalSpeed = safe(terminalSpeed, 0.25f, 5f, 1.5f),
        remoteSpeed = safe(remoteSpeed, 0.25f, 5f, 1f),
        flingSpeed = safe(flingSpeed, 0.25f, 4f, 1f),
        braking = safe(braking, 0.25f, 4f, 1f),
        minimumFlickSpeed = safe(minimumFlickSpeed, 50f, 2000f, 150f),
        remoteFlingLimit = remoteFlingLimit.coerceIn(10, 1000),
        remoteEventsPerFrame = remoteEventsPerFrame.coerceIn(1, 20)
    )
    fun encode(): String = normalized().let {
        listOf(it.terminalSpeed, it.remoteSpeed, it.inertia, it.flingSpeed, it.braking,
            it.minimumFlickSpeed, it.remoteFlingLimit, it.remoteEventsPerFrame, it.reverseDirection).joinToString(",")
    }
    companion object {
        private fun safe(value: Float, min: Float, max: Float, default: Float) = if (value.isFinite()) value.coerceIn(min, max) else default
        fun decode(value: String?): TerminalScrollSettings? = runCatching {
            val fields = value?.split(',') ?: return null
            require(fields.size == 9)
            TerminalScrollSettings(fields[0].toFloat(), fields[1].toFloat(), fields[2].toBooleanStrict(),
                fields[3].toFloat(), fields[4].toFloat(), fields[5].toFloat(), fields[6].toInt(),
                fields[7].toInt(), fields[8].toBooleanStrict()).normalized()
        }.getOrNull()
        val gentle = TerminalScrollSettings(1f, 0.5f, true, 0.65f, 1.8f, 250f, 40, 3)
        val fast = TerminalScrollSettings(2.5f, 1.5f, true, 1.5f, 0.7f, 100f, 240, 10)
    }
}
