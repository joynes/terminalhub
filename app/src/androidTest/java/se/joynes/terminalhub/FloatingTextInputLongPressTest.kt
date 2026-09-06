package se.joynes.terminalhub

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import se.joynes.terminalhub.ui.screen.sessions.FloatingTextInputDialog
import se.joynes.terminalhub.ui.theme.TerminalHubTheme

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FloatingTextInputLongPressTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

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
}
