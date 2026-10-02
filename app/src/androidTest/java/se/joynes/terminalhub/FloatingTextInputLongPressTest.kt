package se.joynes.terminalhub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.sessions.FloatingTextInputDialog
import se.joynes.terminalhub.ui.screen.sessions.voiceInputDraftAfterResult
import se.joynes.terminalhub.ui.screen.sessions.textInputDraftAfterChange
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@RunWith(AndroidJUnit4::class)
class FloatingTextInputLongPressTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun longPressSelectsTextInsideFloatingInputPanel() {
        var value by mutableStateOf(
            TextFieldValue("alpha beta gamma", TextRange("alpha beta gamma".length))
        )
        composeRule.setContent {
            TerminalHubTheme {
                FloatingTextInputDialog(
                    text = value,
                    onTextChange = { value = it },
                    onSend = {},
                    onDismiss = {}
                )
            }
        }

        val input = composeRule.onNodeWithContentDescription("Terminal text input")
        input.performTouchInput { longClick(Offset(center.x * 0.55f, center.y)) }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            value.selection.start != value.selection.end
        }
    }

    @Test
    fun repeatedDictationReopensEditorWithNewTextAtSavedCursor() {
        var visible by mutableStateOf(true)
        var value by mutableStateOf(TextFieldValue("begin end", TextRange(5)))
        composeRule.setContent {
            TerminalHubTheme {
                if (visible) {
                    FloatingTextInputDialog(
                        text = value,
                        onTextChange = {
                            value = textInputDraftAfterChange(visible, value, it)
                        },
                        onSend = {},
                        onDismiss = {}
                    )
                }
            }
        }

        for (spoken in listOf(" first", " second")) {
            var snapshot = TextFieldValue()
            composeRule.runOnIdle { snapshot = value; visible = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                value = voiceInputDraftAfterResult(snapshot, spoken)
                visible = true
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithContentDescription("Terminal text input")
            .assertTextEquals("begin first second end")
    }
}
