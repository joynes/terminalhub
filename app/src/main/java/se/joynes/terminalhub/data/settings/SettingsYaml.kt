package se.joynes.terminalhub.data.settings

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor

data class SettingsChange(val key: String, val before: String, val after: String)
data class SettingsImportPreview(val base: AppSettings, val next: AppSettings, val changes: List<SettingsChange>) {
    val enablesHistory get() = !base.inputHistoryEnabled && next.inputHistoryEnabled
    val enablesBackground get() = (!base.keepSshActiveInBackground && next.keepSshActiveInBackground) ||
        (!base.automaticallyStartBackgroundSsh && next.automaticallyStartBackgroundSsh)
}

/** Strict portable schema; comments are descriptive only and never interpreted as instructions. */
object SettingsYaml {
    const val MAX_BYTES = 64 * 1024
    private data class Field(val key: String, val explanation: String, val read: (AppSettings) -> Any)
    private val fields = listOf(
        Field("keyboardShortcuts", "list of ACTION=MODIFIER+KEY strings; default [] (disabled). External Bluetooth/USB shortcuts take priority over terminal input on the terminal screen. Use CTRL, ALT, SHIFT, META plus a key, or bare F1..F12. No duplicate actions/combinations. Actions: ${ShortcutAction.entries.joinToString { it.name }}. Keys: ${KeyboardShortcuts.keys.keys.joinToString()}. Example: [\"NEXT_PROJECT=CTRL+ALT+RIGHT\", \"FIND_PROJECT=CTRL+ALT+P\"]. CLOSE actions retain confirmation; windows refer to the current project's tmux windows. Some combinations are reserved by Android.") { it.keyboardShortcuts },
        Field("projectTabWidth", "integer 48..200 dp; default 70. Width of each project tab. Narrower fits more projects per row; long names are truncated.") { it.projectTabWidth },
        Field("projectTabHeight", "integer 24..64 dp; default 28. Minimum project tab height. Larger text or Android font scaling may increase the actual height to keep labels readable.") { it.projectTabHeight },
        Field("projectTabTextSize", "integer 8..24 sp; default 9. Project tab label size, also affected by Android font scaling. Does not change terminal content font size.") { it.projectTabTextSize },
        Field("terminalTabWidth", "integer 48..240 dp; default 100. Width of each tmux terminal tab within a project. Long names are truncated; scroll sideways to reach more terminals.") { it.terminalTabWidth },
        Field("terminalTabHeight", "integer 24..64 dp; default 28. Minimum tmux terminal tab height. Text may increase the actual height. The row hides when only one terminal exists; create another from the project long-press menu.") { it.terminalTabHeight },
        Field("terminalTabTextSize", "integer 8..24 sp; default 12. tmux terminal tab label size. Independent of project labels and terminal content text.") { it.terminalTabTextSize },
        Field("remoteDownloadSort", "NAME, DATE, TYPE or SIZE; default NAME. Last remote file browser sort, shared across projects. Directories always appear first. Folder access permissions stay on this device and are not exported.") { it.remoteDownloadSort },
        Field("remoteDownloadSortAscending", "boolean; default true. Last file browser sort direction: true is ascending (oldest first for DATE), false descending (newest first). Tapping the selected sort reverses direction.") { it.remoteDownloadSortAscending },
        Field("preferFastResume", "boolean; default true. Re-focuses the terminal/keyboard when returning to the app. Faster typing readiness; false avoids automatically opening the keyboard. Does not reconnect SSH or guarantee background survival.") { it.preferFastResume },
        Field("executeTextInputOnSend", "boolean; default true. Text Input submissions send text then Enter, running commands or confirming interactive prompts. false sends text without execution. Direct pinned actions use their own per-action Send + Enter choice, independent of this setting. Review destructive commands first.") { it.executeTextInputOnSend },
        Field("textInputPanelOpacity", "number 0.15..1.0; default 0.50. Text Input background opacity: 0.15 is mostly transparent, 1.0 opaque. Lower values reveal the terminal but can reduce readability. Does not affect all app screens.") { it.textInputPanelOpacity },
        Field("inputHistoryEnabled", "boolean; default false. Records text sent to terminals, up to 100 entries per project on this device. Can include passwords, tokens and private commands. Requires explicit approval in import preview when enabling. false stops recording but keeps old history. No history content is exported here.") { it.inputHistoryEnabled },
        Field("sshKeepaliveEnabled", "boolean; default true. Sends SSH connection checks to help detect a lost connection and keep transports responsive. Does not replace the Android foreground service, battery configuration, network connectivity or tmux. Disabling reduces checks but can delay disconnect detection.") { it.sshKeepaliveEnabled },
        Field("keepSshActiveInBackground", "boolean; default false. User approval for background SSH with an ongoing Android notification; may use battery/mobile data. Importing true does not grant notification permission or immediately start the service. Use Start or opt into automatic start. Turning false stops the active service and may disconnect SSH tabs; remote tmux work continues.") { it.keepSshActiveInBackground },
        Field("automaticallyStartBackgroundSsh", "boolean; default false. With background SSH approved, start when the terminal screen is visible and a remote tab is connected, without repeated prompts. Needs keepSshActiveInBackground=true and Android notification permission. No hidden/boot start. Stop revokes automatic start. Import enabling requires explicit approval.") { it.automaticallyStartBackgroundSsh },
        Field("backgroundKeepaliveProfile", "enum: AGGRESSIVE (30 seconds), BALANCED (120 seconds, default), BATTERY_SAVER (300 seconds), ULTRA_BATTERY_SAVER (600 seconds). Background SSH check interval: shorter detects drops sooner with more traffic/battery; longer saves power but can detect problems later. Foreground checks use 60 seconds. Only effective with keepalive enabled and Android allowing execution.") { it.backgroundKeepaliveProfile.name },
        Field("backgroundKeepaliveScope", "enum: ACTIVE_TAB_ONLY (default) or ALL_SESSIONS. Selects remote projects eligible for background connection checks. Shared transports may still protect other tabs on the same server. All sessions checks idle server connections too; more traffic. Does not disable inactive tabs or their notifications.") { it.backgroundKeepaliveScope.name },
        Field("keyBarRows", "list of 1..4 rows; each a list of 1..12 valid key IDs. Order is screen order. Modifier keys CTRL/ALT/SHIFT latch for combinations; CHAR_C is literal c (CTRL + CHAR_C sends Ctrl+C). Removing launchers hides shortcuts but not their features. Unknown IDs and oversized rows are rejected, never silently dropped. All available IDs and labels follow below.") { it.keyBarRows },
        Field("keyBarHighlightedKeyIds", "list of valid key IDs; default TEXT_INPUT, UPLOAD, DOWNLOAD. Gives those buttons a subtle accent; [] means no accents. IDs need not be present in the current rows. This changes appearance only, not key actions.") { it.keyBarHighlightedKeyIds.sorted() },
        Field("keyBarHighlightIntensity", "number 0.05..0.40; default 0.12. Strength of highlighted key backgrounds. Higher is more prominent; lower is subtler. Has no visible effect with no highlighted keys.") { it.keyBarHighlightIntensity }
    )
    private val scrollFields = listOf(
        Field("terminalSpeed", "number 0.25..5; default 1.5. Finger drag multiplier for normal terminal scrollback. Higher moves more rows per finger distance. Terminal text moves in whole rows, not sub-row pixels.") { it.terminalScroll.terminalSpeed },
        Field("remoteSpeed", "number 0.25..5; default 1.0. Drag multiplier in tmux/full-screen apps. They interpret wheel events themselves (or arrow keys without mouse tracking), so one event may move several lines. Network latency and server configuration also affect feel.") { it.terminalScroll.remoteSpeed },
        Field("inertia", "boolean; default true. Continue scrolling after a quick finger flick. false stops at release and disables the remaining flick-related controls, while both drag speed settings still work. A new touch/tab switch stops any glide.") { it.terminalScroll.inertia },
        Field("flingSpeed", "number 0.25..4; default 1.0. Velocity multiplier for after-scroll, in addition to the relevant drag multiplier. Higher starts faster and travels farther; lower is gentler. Only used when inertia=true.") { it.terminalScroll.flingSpeed },
        Field("braking", "number 0.25..4; default 1.0. Android friction multiplier. Higher brakes harder and stops sooner; lower glides longer. Only used with inertia=true. Try increasing this if a flick overshoots.") { it.terminalScroll.braking },
        Field("minimumFlickSpeed", "number 50..2000 pixels/second; default 150. Releases slower than this do not start after-scroll. Higher avoids accidental glides. Adds a threshold above Android gesture detection; cannot lower the device's own threshold. Only used with inertia=true.") { it.terminalScroll.minimumFlickSpeed },
        Field("remoteFlingLimit", "integer 10..1000; default 120. Maximum wheel/arrow events per remote flick, not necessarily screen lines. Caps a tmux glide and SSH input traffic. Does not cap direct finger dragging or mouse wheel events. Only used with inertia=true.") { it.terminalScroll.remoteFlingLimit },
        Field("remoteEventsPerFrame", "integer 1..20; default 6. Remote wheel/arrow burst limit per display frame during a glide. Smaller sends gentler bursts and drains a queued glide more slowly; larger catches up faster with more SSH traffic per frame. Only used with inertia=true.") { it.terminalScroll.remoteEventsPerFrame },
        Field("reverseDirection", "boolean; default false. Reverses finger drag and flick direction in both normal and remote terminals. Does not reverse physical mouse wheel events or keybar arrow buttons.") { it.terminalScroll.reverseDirection }
    )

    fun export(settings: AppSettings, includeDeviceApprovals: Boolean = true): String = buildString {
        appendLine("# TerminalHub SETTINGS ONLY — safe to discuss and edit with an AI.")
        appendLine("# No servers, projects, credentials, pins, history text or notes are included.")
        appendLine("# AI: discuss the user's goal, change only relevant values, keep format/version and")
        appendLine("# key names, return YAML (not a Markdown code fence). Explain trade-offs separately.")
        appendLine("# Comments (#) can be edited/added anywhere. They are ignored on import.")
        appendLine("# Missing settings stay unchanged; unknown keys, wrong types, duplicates and")
        appendLine("# out-of-range values reject the entire import. Import previews changes first.")
        appendLine("# Background service and history approval cannot be silently enabled by this file.")
        appendLine("# For a new phone use Full app configuration instead; this is preferences only.")
        appendLine("format: terminalhub-settings")
        appendLine("version: 1")
        appendLine("settings:")
        fields.filter { includeDeviceApprovals || it.key !in setOf("inputHistoryEnabled", "keepSshActiveInBackground", "automaticallyStartBackgroundSsh") }.forEach { field ->
            field.explanation.chunkedWords().forEach { appendLine("  # $it") }
            if (field.key == "keyBarRows") {
                KeyBarLayoutConfig.availableKeys.groupBy { it.group }.forEach { (group, keys) ->
                    ("$group: " + keys.joinToString(", ") { "${it.id} (${it.label})" }).chunkedWords().forEach { appendLine("  # $it") }
                }
            }
            appendLine("  ${field.key}: ${scalar(field.read(settings))}")
            appendLine()
        }
        appendLine("  # Independent terminal scrolling controls. Gentle: 1/0.5 speeds, 0.65 flick,")
        appendLine("  # 1.8 braking, 250 threshold, 40 events/flick, 3/frame. Fast: 2.5/1.5 speeds,")
        appendLine("  # 1.5 flick, 0.7 braking, 100 threshold, 240 events/flick, 10/frame.")
        appendLine("  terminalScroll:")
        scrollFields.forEach { field ->
            field.explanation.chunkedWords().forEach { appendLine("    # $it") }
            appendLine("    ${field.key}: ${scalar(field.read(settings))}")
            appendLine()
        }
        appendLine("# NOT IMPORTABLE: backgroundSshRecommendationHandled is internal prompt state.")
        appendLine("# Android notification permission and battery Unrestricted/Don't optimize are")
        appendLine("# device controls, changed in Android Settings, not portable YAML preferences.")
        appendLine("# Theme/terminal font size are not exposed as portable settings in this schema.")
    }

    fun preview(text: String, current: AppSettings): SettingsImportPreview {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Settings file is too large (maximum 64 KB)." }
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 0
            nestingDepthLimit = 8
            codePointLimit = MAX_BYTES
        }
        val root = try { stringMap(Yaml(SafeConstructor(options)).load<Any>(text), "document") }
            catch (_: Exception) { throw IllegalArgumentException("Invalid YAML. Check syntax, duplicate keys and unsupported tags/aliases.") }
        require(root.keys.all { it in setOf("format", "version", "settings") }) { "Unknown document field. Use a settings-only file, not full app configuration." }
        require(root["format"] == "terminalhub-settings") { "Wrong file type. Choose Settings-only YAML, not a full configuration backup." }
        require(root["version"] is Int && root["version"] == 1) { "Unsupported settings schema version." }
        val values = stringMap(root["settings"], "settings")
        require(values.keys.all { key -> fields.any { it.key == key } || key == "terminalScroll" }) { "Unknown setting name. No changes imported." }
        fun bool(map: Map<String, Any?>, key: String, fallback: Boolean): Boolean = if (key !in map) fallback else
            map[key] as? Boolean ?: throw IllegalArgumentException("$key must be true or false.")
        fun number(map: Map<String, Any?>, key: String, fallback: Float, min: Float, max: Float): Float {
            if (key !in map) return fallback
            val value = (map[key] as? Number)?.toFloat() ?: throw IllegalArgumentException("$key must be a number.")
            require(value.isFinite() && value in min..max) { "$key must be between $min and $max." }
            return value
        }
        fun integer(map: Map<String, Any?>, key: String, fallback: Int, min: Int, max: Int): Int {
            if (key !in map) return fallback
            val value = map[key]
            require(value is Int && value in min..max) { "$key must be an integer between $min and $max." }
            return value
        }
        fun keys(value: Any?, name: String): List<String> {
            require(value is List<*>) { "$name must be a list of key IDs." }
            return value.map { require(it is String && KeyBarLayoutConfig.definition(it) != null) { "$name contains an unknown key ID." }; it }
        }
        val rows = if ("keyBarRows" !in values) current.keyBarRows else {
            val rows = values["keyBarRows"]
            require(rows is List<*> && rows.size in 1..4) { "keyBarRows needs 1..4 rows." }
            rows.map { keys(it, "keyBarRows").also { row -> require(row.size in 1..12) { "Each keybar row needs 1..12 keys." } } }
        }
        val highlights = if ("keyBarHighlightedKeyIds" in values) keys(values["keyBarHighlightedKeyIds"], "keyBarHighlightedKeyIds").toSet() else current.keyBarHighlightedKeyIds
        val scroll = if ("terminalScroll" in values) stringMap(values["terminalScroll"], "terminalScroll") else emptyMap()
        require(scroll.keys.all { key -> scrollFields.any { it.key == key } }) { "Unknown terminalScroll setting." }
        val old = current.terminalScroll
        val profile = if ("backgroundKeepaliveProfile" in values) enumValue<BackgroundKeepaliveProfile>(values["backgroundKeepaliveProfile"], "backgroundKeepaliveProfile") else current.backgroundKeepaliveProfile
        val scope = if ("backgroundKeepaliveScope" in values) enumValue<BackgroundKeepaliveScope>(values["backgroundKeepaliveScope"], "backgroundKeepaliveScope") else current.backgroundKeepaliveScope
        val remoteSort = if ("remoteDownloadSort" in values) values["remoteDownloadSort"] else current.remoteDownloadSort
        require(remoteSort is String && remoteSort in setOf("NAME", "DATE", "TYPE", "SIZE")) { "remoteDownloadSort must be NAME, DATE, TYPE or SIZE." }
        val next = current.copy(
            keyboardShortcuts = if ("keyboardShortcuts" !in values) current.keyboardShortcuts else {
                val rows = values["keyboardShortcuts"]
                require(rows is List<*> && rows.all { it is String }) { "keyboardShortcuts must be a list of ACTION=MODIFIER+KEY strings." }
                rows.map { it as String }.also { KeyboardShortcuts.parse(it) }
            },
            projectTabWidth = integer(values, "projectTabWidth", current.projectTabWidth, 48, 200),
            projectTabHeight = integer(values, "projectTabHeight", current.projectTabHeight, 24, 64),
            projectTabTextSize = integer(values, "projectTabTextSize", current.projectTabTextSize, 8, 24),
            terminalTabWidth = integer(values, "terminalTabWidth", current.terminalTabWidth, 48, 240),
            terminalTabHeight = integer(values, "terminalTabHeight", current.terminalTabHeight, 24, 64),
            terminalTabTextSize = integer(values, "terminalTabTextSize", current.terminalTabTextSize, 8, 24),
            remoteDownloadSort = remoteSort,
            remoteDownloadSortAscending = bool(values, "remoteDownloadSortAscending", current.remoteDownloadSortAscending),
            preferFastResume = bool(values, "preferFastResume", current.preferFastResume),
            executeTextInputOnSend = bool(values, "executeTextInputOnSend", current.executeTextInputOnSend),
            inputHistoryEnabled = bool(values, "inputHistoryEnabled", current.inputHistoryEnabled),
            sshKeepaliveEnabled = bool(values, "sshKeepaliveEnabled", current.sshKeepaliveEnabled),
            keepSshActiveInBackground = bool(values, "keepSshActiveInBackground", current.keepSshActiveInBackground),
            automaticallyStartBackgroundSsh = bool(values, "automaticallyStartBackgroundSsh", current.automaticallyStartBackgroundSsh),
            textInputPanelOpacity = number(values, "textInputPanelOpacity", current.textInputPanelOpacity, .15f, 1f),
            keyBarHighlightIntensity = number(values, "keyBarHighlightIntensity", current.keyBarHighlightIntensity, .05f, .4f),
            backgroundKeepaliveProfile = profile, backgroundKeepaliveScope = scope,
            keyBarRows = rows, keyBarHighlightedKeyIds = highlights,
            terminalScroll = TerminalScrollSettings(
                number(scroll, "terminalSpeed", old.terminalSpeed, .25f, 5f), number(scroll, "remoteSpeed", old.remoteSpeed, .25f, 5f),
                bool(scroll, "inertia", old.inertia), number(scroll, "flingSpeed", old.flingSpeed, .25f, 4f),
                number(scroll, "braking", old.braking, .25f, 4f), number(scroll, "minimumFlickSpeed", old.minimumFlickSpeed, 50f, 2000f),
                integer(scroll, "remoteFlingLimit", old.remoteFlingLimit, 10, 1000), integer(scroll, "remoteEventsPerFrame", old.remoteEventsPerFrame, 1, 20),
                bool(scroll, "reverseDirection", old.reverseDirection)
            )
        )
        require(!next.automaticallyStartBackgroundSsh || next.keepSshActiveInBackground) { "Automatic start requires keepSshActiveInBackground=true. Disable both to stop it." }
        val changes = (fields + scrollFields).mapNotNull { field ->
            val before = field.read(current); val after = field.read(next)
            if (before == after) null else SettingsChange(if (field in scrollFields) "terminalScroll.${field.key}" else field.key, scalar(before), scalar(after))
        }
        return SettingsImportPreview(current, next, changes)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: Any?, name: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: throw IllegalArgumentException("Invalid $name. Check the documented enum values.")
    private fun stringMap(value: Any?, name: String): Map<String, Any?> {
        require(value is Map<*, *> && value.keys.all { it is String }) { "$name must be a mapping with string keys." }
        @Suppress("UNCHECKED_CAST") return value as Map<String, Any?>
    }
    private fun scalar(value: Any): String = when (value) {
        is List<*> -> value.joinToString(", ", "[", "]") { scalar(it!!) }
        is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        else -> value.toString()
    }
    private fun String.chunkedWords(): List<String> = buildList {
        var line = ""
        split(' ').forEach { word -> if (line.length + word.length > 90) { add(line); line = word } else line = if (line.isEmpty()) word else "$line $word" }
        if (line.isNotEmpty()) add(line)
    }
}
