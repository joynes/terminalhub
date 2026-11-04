package se.joynes.terminalhub

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.ui.components.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class AboutAndDisplayTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun aboutShowsInstalledVersionAndOpensSourceRepository() {
        var opened: Intent? = null; var closed = false
        val context = object : ContextWrapper(compose.activity) { override fun startActivity(intent: Intent) { opened = intent } }
        compose.setContent { CompositionLocalProvider(LocalContext provides context) { TerminalHubTheme { TerminalHubAboutDialog({ closed = true }) } } }
        compose.onNodeWithContentDescription("App icon").assertIsDisplayed()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").assertIsDisplayed()
        compose.onNodeWithText("OPEN SOURCE • GPL-3.0").assertIsDisplayed()
        compose.onNodeWithText("github.com/joynes/terminalhub").performClick()
        compose.runOnIdle { assertEquals("https://github.com/joynes/terminalhub", opened?.data.toString()); assertEquals(Intent.ACTION_VIEW, opened?.action) }
        compose.onNodeWithText("CLOSE").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun crtOverlayDrawsVisibleScanlinesWithoutObscuringBackground() {
        compose.setContent { TerminalHubTheme { Box(Modifier.size(100.dp).background(Color.White).testTag("crt-fixture")) { CrtScanlineOverlay(Modifier.fillMaxSize()) } } }
        val pixels = compose.onNodeWithTag("crt-fixture").captureToImage().toPixelMap()
        val shades = (0 until pixels.height).map { pixels[pixels.width / 2, it].red }
        assertTrue(shades.min() < shades.max()); assertTrue(shades.min() > .5f)
    }
}
