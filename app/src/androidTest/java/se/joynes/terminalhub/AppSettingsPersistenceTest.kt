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
