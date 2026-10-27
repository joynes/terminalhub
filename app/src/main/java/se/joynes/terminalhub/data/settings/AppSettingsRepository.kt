package se.joynes.terminalhub.data.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

internal const val DEFAULT_EXECUTE_TEXT_INPUT_ON_SEND = true
internal const val DEFAULT_TEXT_INPUT_PANEL_OPACITY = 0.50f
internal const val MIN_TEXT_INPUT_PANEL_OPACITY_SETTING = 0.15f
internal const val MAX_TEXT_INPUT_PANEL_OPACITY_SETTING = 1f
internal const val DEFAULT_KEY_BAR_HIGHLIGHT_INTENSITY = 0.12f
internal const val MIN_KEY_BAR_HIGHLIGHT_INTENSITY = 0.05f
internal const val MAX_KEY_BAR_HIGHLIGHT_INTENSITY = 0.40f

internal fun normalizeTextInputPanelOpacitySetting(value: Float): Float =
    value.coerceIn(MIN_TEXT_INPUT_PANEL_OPACITY_SETTING, MAX_TEXT_INPUT_PANEL_OPACITY_SETTING)

internal fun normalizeKeyBarHighlightIntensity(value: Float): Float =
    value.coerceIn(MIN_KEY_BAR_HIGHLIGHT_INTENSITY, MAX_KEY_BAR_HIGHLIGHT_INTENSITY)

data class AppSettings(
    val keyboardShortcuts: List<String> = emptyList(),
    val projectTabWidth: Int = 70,
    val projectTabHeight: Int = 28,
    val projectTabTextSize: Int = 9,
    val terminalTabWidth: Int = 100,
    val terminalTabHeight: Int = 28,
    val terminalTabTextSize: Int = 12,
    val remoteDownloadSort: String = "NAME",
    val remoteDownloadSortAscending: Boolean = true,
    val terminalScroll: TerminalScrollSettings = TerminalScrollSettings(),
    val inputHistoryEnabled: Boolean = false,
    val preferFastResume: Boolean = true,
    val executeTextInputOnSend: Boolean = DEFAULT_EXECUTE_TEXT_INPUT_ON_SEND,
    val textInputPanelOpacity: Float = DEFAULT_TEXT_INPUT_PANEL_OPACITY,
    val sshKeepaliveEnabled: Boolean = true,
    val keepSshActiveInBackground: Boolean = false,
    val automaticallyStartBackgroundSsh: Boolean = false,
    val backgroundSshRecommendationHandled: Boolean = false,
    val backgroundKeepaliveProfile: BackgroundKeepaliveProfile = BackgroundKeepaliveProfile.BALANCED,
    val backgroundKeepaliveScope: BackgroundKeepaliveScope = BackgroundKeepaliveScope.ACTIVE_TAB_ONLY,
    val keyBarRows: List<List<String>> = KeyBarLayoutConfig.defaultRows,
    val keyBarHighlightedKeyIds: Set<String> = KeyBarLayoutConfig.defaultHighlightedKeyIds,
    val keyBarHighlightIntensity: Float = DEFAULT_KEY_BAR_HIGHLIGHT_INTENSITY
)

enum class BackgroundKeepaliveProfile {
    AGGRESSIVE,
    BALANCED,
    BATTERY_SAVER,
    ULTRA_BATTERY_SAVER
}

enum class BackgroundKeepaliveScope {
    ALL_SESSIONS,
    ACTIVE_TAB_ONLY
}

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val historyConsentPrefs = context.getSharedPreferences("input_history_consent", Context.MODE_PRIVATE)
    private val historyDisabledListeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    private val _settings = MutableStateFlow(
        AppSettings(
            keyboardShortcuts = runCatching {
                prefs.getString("keyboard_shortcuts", "").orEmpty().split('\n').filter(String::isNotBlank)
                    .let(KeyboardShortcuts::normalize)
            }.getOrDefault(emptyList()),
            projectTabWidth = prefs.getInt("projectTabWidth", 70).coerceIn(48, 200),
            projectTabHeight = prefs.getInt("projectTabHeight", 28).coerceIn(24, 64),
            projectTabTextSize = prefs.getInt("projectTabTextSize", 9).coerceIn(8, 24),
            terminalTabWidth = prefs.getInt("terminalTabWidth", 100).coerceIn(48, 240),
            terminalTabHeight = prefs.getInt("terminalTabHeight", 28).coerceIn(24, 64),
            terminalTabTextSize = prefs.getInt("terminalTabTextSize", 12).coerceIn(8, 24),
            remoteDownloadSort = prefs.getString("remote_download_sort", "NAME")
                ?.takeIf { it in setOf("NAME", "DATE", "TYPE", "SIZE") } ?: "NAME",
            remoteDownloadSortAscending = prefs.getBoolean("remote_download_sort_ascending", true),
            terminalScroll = TerminalScrollSettings.decode(prefs.getString("terminal_scroll", null)) ?: TerminalScrollSettings(),
            inputHistoryEnabled = historyConsentPrefs.getBoolean(KEY_INPUT_HISTORY_ENABLED, false),
            preferFastResume = prefs.getBoolean(KEY_FAST_RESUME, true),
            executeTextInputOnSend = prefs.getBoolean(
                KEY_EXECUTE_TEXT_INPUT_ON_SEND,
                DEFAULT_EXECUTE_TEXT_INPUT_ON_SEND
            ),
            textInputPanelOpacity = normalizeTextInputPanelOpacitySetting(
                prefs.getFloat(KEY_TEXT_INPUT_PANEL_OPACITY, DEFAULT_TEXT_INPUT_PANEL_OPACITY)
            ),
            sshKeepaliveEnabled = prefs.getBoolean(KEY_SSH_KEEPALIVE, true),
            keepSshActiveInBackground = prefs.getBoolean(KEY_KEEP_SSH_ACTIVE_IN_BACKGROUND, false),
            automaticallyStartBackgroundSsh = prefs.getBoolean(KEY_AUTO_BACKGROUND_SSH, false),
            backgroundSshRecommendationHandled = prefs.getBoolean(KEY_BACKGROUND_SSH_RECOMMENDATION_HANDLED, false),
            backgroundKeepaliveProfile = prefs.getString(KEY_BACKGROUND_KEEPALIVE_PROFILE, BackgroundKeepaliveProfile.BALANCED.name)
                ?.let { runCatching { BackgroundKeepaliveProfile.valueOf(it) }.getOrNull() }
                ?: BackgroundKeepaliveProfile.BALANCED,
            backgroundKeepaliveScope = prefs.getString(KEY_BACKGROUND_KEEPALIVE_SCOPE, BackgroundKeepaliveScope.ACTIVE_TAB_ONLY.name)
                ?.let { runCatching { BackgroundKeepaliveScope.valueOf(it) }.getOrNull() }
                ?: BackgroundKeepaliveScope.ACTIVE_TAB_ONLY,
            keyBarRows = KeyBarLayoutConfig.decode(prefs.getString(KEY_KEY_BAR_LAYOUT, null),
                migrateFactory = prefs.getInt("key_bar_layout_schema", 0) < 2),
            keyBarHighlightedKeyIds = KeyBarLayoutConfig.decodeHighlights(
                prefs.getString(KEY_KEY_BAR_HIGHLIGHTS, null)
            ),
            keyBarHighlightIntensity = normalizeKeyBarHighlightIntensity(
                prefs.getFloat(KEY_KEY_BAR_HIGHLIGHT_INTENSITY, DEFAULT_KEY_BAR_HIGHLIGHT_INTENSITY)
            )
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setKeyboardShortcuts(rows: List<String>) {
        KeyboardShortcuts.parse(rows)
        update(_settings.value.copy(keyboardShortcuts = rows.toList()))
    }

    fun setInputHistoryEnabled(enabled: Boolean) {
        update(_settings.value.copy(inputHistoryEnabled = enabled))
        // Synchronous notification: a rapid off/on toggle must still discard existing drafts.
        if (!enabled) historyDisabledListeners.forEach { it() }
    }

    internal fun onInputHistoryDisabled(listener: () -> Unit) {
        historyDisabledListeners.add(listener)
    }

    fun setPreferFastResume(enabled: Boolean) {
        update(_settings.value.copy(preferFastResume = enabled))
    }

    fun setTerminalScroll(settings: TerminalScrollSettings) = update(_settings.value.copy(terminalScroll = settings.normalized()))

    fun setTabAppearance(project: Boolean, width: Int, height: Int, textSize: Int) {
        update(if (project) _settings.value.copy(projectTabWidth = width.coerceIn(48, 200),
            projectTabHeight = height.coerceIn(24, 64), projectTabTextSize = textSize.coerceIn(8, 24))
        else _settings.value.copy(terminalTabWidth = width.coerceIn(48, 240),
            terminalTabHeight = height.coerceIn(24, 64), terminalTabTextSize = textSize.coerceIn(8, 24)))
    }

    fun setRemoteDownloadSort(property: String, ascending: Boolean) {
        require(property in setOf("NAME", "DATE", "TYPE", "SIZE"))
        update(_settings.value.copy(remoteDownloadSort = property, remoteDownloadSortAscending = ascending))
    }

    /** Validated settings-only import: one observable update, no project/credential mutation. */
    fun applySettingsImport(expected: AppSettings, next: AppSettings) {
        require(_settings.value == expected) { "Settings changed during preview. Import the file again to review the latest changes." }
        update(next)
        if (!next.inputHistoryEnabled) historyDisabledListeners.forEach { it() }
    }

    fun setExecuteTextInputOnSend(enabled: Boolean) {
        update(_settings.value.copy(executeTextInputOnSend = enabled))
    }

    fun setTextInputPanelOpacity(opacity: Float) {
        update(
            _settings.value.copy(
                textInputPanelOpacity = normalizeTextInputPanelOpacitySetting(opacity)
            )
        )
    }

    fun setSshKeepaliveEnabled(enabled: Boolean) {
        update(_settings.value.copy(sshKeepaliveEnabled = enabled))
    }

    fun setKeepSshActiveInBackground(enabled: Boolean) {
        update(_settings.value.copy(keepSshActiveInBackground = enabled,
            automaticallyStartBackgroundSsh = enabled && _settings.value.automaticallyStartBackgroundSsh))
    }

    fun setAutomaticallyStartBackgroundSsh(enabled: Boolean) {
        update(_settings.value.copy(automaticallyStartBackgroundSsh = enabled))
    }

    fun setBackgroundSshRecommendationHandled(handled: Boolean = true) {
        update(_settings.value.copy(backgroundSshRecommendationHandled = handled))
    }

    fun setBackgroundKeepaliveProfile(profile: BackgroundKeepaliveProfile) {
        update(_settings.value.copy(backgroundKeepaliveProfile = profile))
    }

    fun setBackgroundKeepaliveScope(scope: BackgroundKeepaliveScope) {
        update(_settings.value.copy(backgroundKeepaliveScope = scope))
    }

    fun setKeyBarRows(rows: List<List<String>>) {
        update(_settings.value.copy(keyBarRows = KeyBarLayoutConfig.normalize(rows)))
    }

    fun setKeyBarHighlightedKeyIds(keyIds: Set<String>) {
        update(
            _settings.value.copy(
                keyBarHighlightedKeyIds = KeyBarLayoutConfig.normalizeHighlights(keyIds)
            )
        )
    }

    fun setKeyBarHighlightIntensity(intensity: Float) {
        update(
            _settings.value.copy(
                keyBarHighlightIntensity = normalizeKeyBarHighlightIntensity(intensity)
            )
        )
    }

    private fun update(next: AppSettings) {
        KeyboardShortcuts.parse(next.keyboardShortcuts)
        _settings.value = next
        historyConsentPrefs.edit().putBoolean(KEY_INPUT_HISTORY_ENABLED, next.inputHistoryEnabled).apply()
        prefs.edit()
            .putString("keyboard_shortcuts", next.keyboardShortcuts.joinToString("\n"))
            .putInt("projectTabWidth", next.projectTabWidth)
            .putInt("projectTabHeight", next.projectTabHeight)
            .putInt("projectTabTextSize", next.projectTabTextSize)
            .putInt("terminalTabWidth", next.terminalTabWidth)
            .putInt("terminalTabHeight", next.terminalTabHeight)
            .putInt("terminalTabTextSize", next.terminalTabTextSize)
            .putString("terminal_scroll", next.terminalScroll.encode())
            .putString("remote_download_sort", next.remoteDownloadSort)
            .putBoolean("remote_download_sort_ascending", next.remoteDownloadSortAscending)
            .putBoolean(KEY_FAST_RESUME, next.preferFastResume)
            .putBoolean(KEY_EXECUTE_TEXT_INPUT_ON_SEND, next.executeTextInputOnSend)
            .putFloat(KEY_TEXT_INPUT_PANEL_OPACITY, next.textInputPanelOpacity)
            .putBoolean(KEY_SSH_KEEPALIVE, next.sshKeepaliveEnabled)
            .putBoolean(KEY_KEEP_SSH_ACTIVE_IN_BACKGROUND, next.keepSshActiveInBackground)
            .putBoolean(KEY_AUTO_BACKGROUND_SSH, next.automaticallyStartBackgroundSsh)
            .putBoolean(KEY_BACKGROUND_SSH_RECOMMENDATION_HANDLED, next.backgroundSshRecommendationHandled)
            .putString(KEY_BACKGROUND_KEEPALIVE_PROFILE, next.backgroundKeepaliveProfile.name)
            .putString(KEY_BACKGROUND_KEEPALIVE_SCOPE, next.backgroundKeepaliveScope.name)
            .putString(KEY_KEY_BAR_LAYOUT, KeyBarLayoutConfig.encode(next.keyBarRows))
            .putInt("key_bar_layout_schema", 2)
            .putString(
                KEY_KEY_BAR_HIGHLIGHTS,
                KeyBarLayoutConfig.encodeHighlights(next.keyBarHighlightedKeyIds)
            )
            .putFloat(KEY_KEY_BAR_HIGHLIGHT_INTENSITY, next.keyBarHighlightIntensity)
            .apply()
    }

    companion object {
        private const val KEY_INPUT_HISTORY_ENABLED = "input_history_enabled"
        private const val KEY_FAST_RESUME = "prefer_fast_resume"
        private const val KEY_EXECUTE_TEXT_INPUT_ON_SEND = "execute_text_input_on_send"
        private const val KEY_TEXT_INPUT_PANEL_OPACITY = "text_input_panel_opacity"
        private const val KEY_SSH_KEEPALIVE = "ssh_keepalive_enabled"
        private const val KEY_KEEP_SSH_ACTIVE_IN_BACKGROUND = "keep_ssh_active_in_background"
        private const val KEY_AUTO_BACKGROUND_SSH = "automatically_start_background_ssh"
        private const val KEY_BACKGROUND_SSH_RECOMMENDATION_HANDLED = "background_ssh_recommendation_handled"
        private const val KEY_BACKGROUND_KEEPALIVE_PROFILE = "background_keepalive_profile"
        private const val KEY_BACKGROUND_KEEPALIVE_SCOPE = "background_keepalive_scope"
        private const val KEY_KEY_BAR_LAYOUT = "key_bar_layout"
        private const val KEY_KEY_BAR_HIGHLIGHTS = "key_bar_highlights"
        private const val KEY_KEY_BAR_HIGHLIGHT_INTENSITY = "key_bar_highlight_intensity"
    }
}
