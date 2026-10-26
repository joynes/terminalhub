package se.joynes.terminalhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import se.joynes.terminalhub.data.settings.AppSettings
import se.joynes.terminalhub.data.ssh.*
import se.joynes.terminalhub.domain.TerminalSessionId
import se.joynes.terminalhub.ui.navigation.SessionTabBar
import se.joynes.terminalhub.ui.screen.settings.TabAppearanceEditor
import se.joynes.terminalhub.ui.screen.sessions.*
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

class TabAppearanceSettingsTest {
    @get:Rule val compose = createComposeRule()

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun hidingSingleTerminalReclaimsContentSpaceAndCompactPreviewRemainsReadable() {
        val tabs = (1L..15L).map { ProjectTabState(it, "Project-$it", TerminalSessionId("$it"), true) }
        var windows by mutableStateOf(TmuxWindowsState(projectId = 1, connected = true, snapshot = TmuxWindowsSnapshot("\$1",
            listOf(TmuxWindow("@1", 0, "Code", true, 1), TmuxWindow("@2", 1, "Logs", false, 1)))))
        compose.setContent { TerminalHubTheme { Column(Modifier.fillMaxSize().background(Color.Black)) {
            SessionTabBar(tabs, tabs.first().sessionId, {}, { _, _ -> }, onReorder = {}, onAddProject = {})
            TmuxWindowBar(windows, false, {}, {}, { _, _ -> }, {}, {})
            Box(Modifier.fillMaxWidth().weight(1f).testTag("terminal-area")) { Text("Terminal output\nMore room for your work", color = Color.White, fontSize = 16.sp) }
        } } }
        val before = compose.onNodeWithTag("terminal-area").fetchSemanticsNode().boundsInRoot.height
        screenshot("tabs-compact.png")
        compose.runOnIdle { windows = windows.copy(snapshot = windows.snapshot!!.copy(windows = windows.snapshot!!.windows.take(1))) }
        compose.onNodeWithTag("project-terminal-bar").assertDoesNotExist()
        val after = compose.onNodeWithTag("terminal-area").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Hiding the row must return its height to terminal content", after > before)
        screenshot("tabs-single.png")
    }

    @Test fun slidersAndResetsKeepProjectAndTerminalPreferencesIndependent() {
        var settings by mutableStateOf(AppSettings())
        compose.setContent { TerminalHubTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            TabAppearanceEditor(settings) { project, w, h, t -> settings = if (project)
                settings.copy(projectTabWidth = w, projectTabHeight = h, projectTabTextSize = t)
                else settings.copy(terminalTabWidth = w, terminalTabHeight = h, terminalTabTextSize = t) }
        } } }
        compose.onNodeWithContentDescription("Project tab width").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.runOnIdle { assertEquals(120, settings.projectTabWidth); assertEquals(100, settings.terminalTabWidth) }
        compose.onNodeWithContentDescription("Terminal tab text size").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(18f) }
        compose.runOnIdle { assertEquals(18, settings.terminalTabTextSize); assertEquals(9, settings.projectTabTextSize) }
        compose.onNodeWithText("Reset project tabs").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(70, settings.projectTabWidth); assertEquals(18, settings.terminalTabTextSize) }
        compose.onNodeWithText("Reset terminal tabs").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AppSettings(), settings) }
    }

    @Test fun largeAndroidFontScaleExpandsBothRowsInsteadOfClippingLabels() {
        val tab = ProjectTabState(1, "Project", TerminalSessionId("one"), true)
        val windows = TmuxWindowsState(projectId = 1, connected = true, snapshot = TmuxWindowsSnapshot("\$1",
            listOf(TmuxWindow("@1", 0, "Code", true, 1), TmuxWindow("@2", 1, "Logs", false, 1))))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f)) { TerminalHubTheme { Column {
                SessionTabBar(listOf(tab), tab.sessionId, {}, { _, _ -> }, onReorder = {}, onAddProject = {}, tabHeight = 24, tabTextSize = 24)
                TmuxWindowBar(windows, false, {}, {}, { _, _ -> }, {}, {}, tabHeight = 24, tabTextSize = 24)
            } } }
        }
        // Android uses nonlinear font scaling; verify expansion rather than a linear sp-to-dp formula.
        compose.onNodeWithTag("project-tab-1").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Terminal 1: Code").assertHeightIsAtLeast(70.dp)
        screenshot("tabs-large-font.png")
    }
}
