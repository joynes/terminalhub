package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.theme.*
import kotlin.math.roundToInt

/**
 * Draggable text-input panel that overlays the terminal content inside the same window.
 * Using the same window (not a Dialog) is required for the IME microphone / voice dictation
 * to work — Dialog creates a separate window and loses IME voice support.
 */
@Composable
fun FloatingTextInputDialog(
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val panelWidthDp   = (configuration.screenWidthDp * 0.92f).dp
    val panelWidthPx   = with(density) { panelWidthDp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx * 0.04f) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx * 0.15f) }
    var text    by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun send() {
        if (text.isNotEmpty()) {
            onSend(text)
            onDismiss()
        }
    }

    // Full-size overlay so tapping outside dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    awaitPointerEvent() // first touch outside the panel = dismiss
                    onDismiss()
                }
            }
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(panelWidthDp)
                .background(MegaDriveSurface, RoundedCornerShape(4.dp))
                // Stop dismiss-on-outside-tap from propagating through the panel
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
        ) {
            // Draggable title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(MegaDrivePrimary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x).coerceIn(0f, screenWidthPx - panelWidthPx)
                            offsetY = (offsetY + drag.y).coerceIn(0f, screenHeightPx - with(density) { 160.dp.toPx() })
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TEXT INPUT", color = MegaDriveBg, fontSize = 11.sp, fontFamily = MonoFontFamily)
                Text(
                    "✕", color = MegaDriveBg, fontSize = 13.sp, fontFamily = MonoFontFamily,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "Type or dictate...",
                            color = MegaDriveDim, fontSize = 12.sp, fontFamily = MonoFontFamily
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = MegaDriveBg,
                        unfocusedContainerColor = MegaDriveBg,
                        focusedTextColor        = MegaDrivePrimary,
                        unfocusedTextColor      = MegaDrivePrimary,
                        focusedIndicatorColor   = MegaDrivePrimary,
                        unfocusedIndicatorColor = MegaDriveDim,
                        cursorColor             = MegaDrivePrimary,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = MonoFontFamily, fontSize = 13.sp, color = MegaDrivePrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .focusRequester(focusRequester)
                )
                RetroButton(text = "SEND", onClick = { send() })
            }
        }
    }
}
