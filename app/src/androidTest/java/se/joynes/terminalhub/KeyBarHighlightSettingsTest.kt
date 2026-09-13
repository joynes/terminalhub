package se.joynes.terminalhub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.settings.KeyBarSettingsEditor
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class KeyBarHighlightSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun starTogglesHighlightWithoutReplacingTheKey() {
        composeRule.setContent {
            var highlights by remember { mutableStateOf(emptySet<String>()) }
            TerminalHubTheme {
                KeyBarSettingsEditor(
                    rows = listOf(listOf("CHAR_C")),
                    highlightedKeyIds = highlights,
                    onRowsChange = {},
                    onHighlightedKeyIdsChange = { highlights = it }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Highlight C").performClick()

        composeRule.onNodeWithContentDescription("Remove highlight from C").assertIsDisplayed()
    }
}
