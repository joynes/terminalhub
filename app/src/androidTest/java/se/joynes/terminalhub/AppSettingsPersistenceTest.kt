package se.joynes.terminalhub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.data.settings.AppSettingsRepository
import se.joynes.terminalhub.data.settings.DEFAULT_KEY_BAR_HIGHLIGHT_INTENSITY
import se.joynes.terminalhub.data.settings.DEFAULT_TEXT_INPUT_PANEL_OPACITY

@RunWith(AndroidJUnit4::class)
class AppSettingsPersistenceTest {
    @Test fun independentTabSizesSurviveRestartAndYamlImport() {
        val repository = AppSettingsRepository(context)
        repository.setTabAppearance(true, 100, 40, 16)
        repository.setTabAppearance(false, 140, 32, 11)
        val expected = repository.settings.value
        assertEquals(expected, AppSettingsRepository(context).settings.value)
        val preview = se.joynes.terminalhub.data.settings.SettingsYaml.preview(
            "format: terminalhub-settings\nversion: 1\nsettings:\n  terminalTabWidth: 180\n", expected)
        repository.applySettingsImport(expected, preview.next)
        assertEquals(expected.copy(terminalTabWidth = 180), AppSettingsRepository(context).settings.value)
    }
    @Test fun remoteSortingDefaultsToNameAndEveryChoiceSurvivesRecreation() {
        val initial = AppSettingsRepository(context)
        assertEquals("NAME", initial.settings.value.remoteDownloadSort)
        assertEquals(true, initial.settings.value.remoteDownloadSortAscending)
        listOf("NAME", "DATE", "TYPE", "SIZE").forEach { property ->
            listOf(false, true).forEach { ascending ->
                AppSettingsRepository(context).setRemoteDownloadSort(property, ascending)
                val recreated = AppSettingsRepository(context).settings.value
                assertEquals(property, recreated.remoteDownloadSort)
                assertEquals(ascending, recreated.remoteDownloadSortAscending)
            }
        }
    }

    @Test fun invalidStoredRemoteSortFallsBackToName() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
            .putString("remote_download_sort", "INVALID").commit()
        assertEquals("NAME", AppSettingsRepository(context).settings.value.remoteDownloadSort)
    }
    @Test fun intentionallyImportedOldFactoryKeybarIsNotMigratedAgainOnRestart() {
        val settings = AppSettingsRepository(context)
        val rows = se.joynes.terminalhub.data.settings.KeyBarLayoutConfig.defaultRows.map { row -> row.map { if (it == "PROJECT_NOTES") "DIGIT_3" else it } }
        settings.applySettingsImport(settings.settings.value, settings.settings.value.copy(keyBarRows = rows))
        assertEquals(rows, AppSettingsRepository(context).settings.value.keyBarRows)
    }
    @Test fun allScrollControlsSurviveRecreation() {
        val config = se.joynes.terminalhub.data.settings.TerminalScrollSettings(3f, .5f, false, 2f, .5f, 700f, 250, 4, true)
        AppSettingsRepository(context).setTerminalScroll(config)
        assertEquals(config, AppSettingsRepository(context).settings.value.terminalScroll)
    }
    @Test fun automaticBackgroundSshDefaultsOffPersistsAndExplicitStopRevokesIt() {
        val settings = AppSettingsRepository(context)
        assertEquals(false, settings.settings.value.automaticallyStartBackgroundSsh)
        settings.setKeepSshActiveInBackground(true)
        settings.setAutomaticallyStartBackgroundSsh(true)
        val recreated = AppSettingsRepository(context)
        assertEquals(true, recreated.settings.value.automaticallyStartBackgroundSsh)
        assertEquals(true, recreated.settings.value.keepSshActiveInBackground)
        recreated.setKeepSshActiveInBackground(false)
        assertEquals(false, AppSettingsRepository(context).settings.value.automaticallyStartBackgroundSsh)
    }
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearSettings() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("input_history_consent", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun historyDefaultsOffAndOptInSurvivesProcessRecreationOnThisDevice() {
        val repository = AppSettingsRepository(context)
        assertEquals(false, repository.settings.value.inputHistoryEnabled)
        repository.setInputHistoryEnabled(true)
        assertEquals(true, AppSettingsRepository(context).settings.value.inputHistoryEnabled)
        repository.setInputHistoryEnabled(false)
        assertEquals(false, AppSettingsRepository(context).settings.value.inputHistoryEnabled)
    }

    @Test
    fun opacityDefaultsToFiftyPercentAndSurvivesRepositoryRecreation() {
        val initialRepository = AppSettingsRepository(context)
        assertEquals(DEFAULT_TEXT_INPUT_PANEL_OPACITY, initialRepository.settings.value.textInputPanelOpacity)

        initialRepository.setTextInputPanelOpacity(0.72f)

        val recreatedRepository = AppSettingsRepository(context)
        assertEquals(0.72f, recreatedRepository.settings.value.textInputPanelOpacity)
    }

    @Test
    fun keyBarHighlightIntensityIsSubtleByDefaultAndSurvivesRepositoryRecreation() {
        val initialRepository = AppSettingsRepository(context)
        assertEquals(
            DEFAULT_KEY_BAR_HIGHLIGHT_INTENSITY,
            initialRepository.settings.value.keyBarHighlightIntensity
        )

        initialRepository.setKeyBarHighlightIntensity(0.24f)

        val recreatedRepository = AppSettingsRepository(context)
        assertEquals(0.24f, recreatedRepository.settings.value.keyBarHighlightIntensity)
    }
}
