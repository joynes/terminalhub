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
import se.joynes.terminalhub.data.settings.DEFAULT_TEXT_INPUT_PANEL_OPACITY

@RunWith(AndroidJUnit4::class)
class AppSettingsPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearSettings() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun opacityDefaultsToFiftyPercentAndSurvivesRepositoryRecreation() {
        val initialRepository = AppSettingsRepository(context)
        assertEquals(DEFAULT_TEXT_INPUT_PANEL_OPACITY, initialRepository.settings.value.textInputPanelOpacity)

        initialRepository.setTextInputPanelOpacity(0.72f)

        val recreatedRepository = AppSettingsRepository(context)
        assertEquals(0.72f, recreatedRepository.settings.value.textInputPanelOpacity)
    }
}
