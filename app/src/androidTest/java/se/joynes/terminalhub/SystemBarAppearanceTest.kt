package se.joynes.terminalhub

import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Before
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SystemBarAppearanceTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Before fun setup() { hiltRule.inject() }

    @Test
    fun darkAppUsesLightStatusAndNavigationBarIcons() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

                assertFalse(controller.isAppearanceLightStatusBars)
                assertFalse(controller.isAppearanceLightNavigationBars)
            }
        }
    }
}
