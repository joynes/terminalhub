package se.joynes.terminalhub

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.terminal.MutableModifierManager
import se.joynes.terminalhub.ui.screen.terminal.SpecialKeyBar
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class SpecialKeyBarFeedbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keyReportsPressedStateWhileFingerIsDown() {
        composeRule.setContent {
            TerminalHubTheme {
                SpecialKeyBar(
                    modifierManager = MutableModifierManager(),
                    rows = listOf(listOf("CHAR_C")),
                    onKey = {}
                )
            }
        }

        val key = composeRule.onNodeWithText("C")
        key.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready"))

        key.performTouchInput { down(center) }
        key.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pressed"))

        key.performTouchInput { up() }
        key.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready"))
    }
}
