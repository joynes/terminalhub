package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    onDismiss: () -> Unit,
    history: List<String> = emptyList(),
    onSaveHistory: (String) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx  = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val imeBottomPx    = WindowInsets.ime.getBottom(density).toFloat()
    val visibleHeightPx = (screenHeightPx - imeBottomPx).coerceAtLeast(with(density) { 220.dp.toPx() })
    val panelWidthDp   = (configuration.screenWidthDp * 0.92f).dp
    val panelWidthPx   = with(density) { panelWidthDp.toPx() }
    val minPanelTopPx  = with(density) { 80.dp.toPx() }
    val panelHeightPx  = with(density) { 160.dp.toPx() }
    val maxPanelTopPx  = (visibleHeightPx - panelHeightPx).coerceAtLeast(minPanelTopPx)

    var offsetX by remember { mutableFloatStateOf(screenWidthPx * 0.04f) }
    var offsetY by remember { mutableFloatStateOf(minPanelTopPx) }
    var text    by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(maxPanelTopPx) {
        offsetY = offsetY.coerceIn(minPanelTopPx, maxPanelTopPx)
    }

    fun send() {
        if (text.isNotEmpty()) {
            onSaveHistory(text)
            onSend(text)
            onDismiss()
        }
    }

    // Full-size overlay so tapping outside dismisses
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // detectTapGestures respects consumed events — won't fire if the panel consumed the tap
                detectTapGestures { onDismiss() }
            }
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(panelWidthDp)
                .background(MegaDriveSurface, RoundedCornerShape(4.dp))
                // Consume all pointer events so they don't reach the dismiss handler above
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
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
                            offsetY = (offsetY + drag.y).coerceIn(minPanelTopPx, maxPanelTopPx)
                        }
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TEXT INPUT", color = MegaDriveBg, fontSize = 11.sp, fontFamily = MonoFontFamily)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (history.isNotEmpty()) {
                        Text(
                            "▾ HISTORY",
                            color = MegaDriveBg,
                            fontSize = 10.sp,
                            fontFamily = MonoFontFamily,
                            modifier = Modifier.clickable { showHistory = !showHistory }
                        )
                    }
                    Text(
                        "✕", color = MegaDriveBg, fontSize = 13.sp, fontFamily = MonoFontFamily,
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }
            }

            // History dropdown
            if (history.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    DropdownMenu(
                        expanded = showHistory,
                        onDismissRequest = { showHistory = false },
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .background(MegaDriveSurface)
                    ) {
                        history.forEach { entry ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = entry,
                                        color = MegaDrivePrimary,
                                        fontSize = 12.sp,
                                        fontFamily = MonoFontFamily,
                                        maxLines = 2
                                    )
                                },
                                onClick = {
                                    text = entry
                                    showHistory = false
                                },
                                modifier = Modifier.background(MegaDriveSurface)
                            )
                        }
                    }
                }
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
                        focusedContainerColor    = MegaDriveBg,
                        unfocusedContainerColor  = MegaDriveBg,
                        disabledContainerColor   = MegaDriveBg,
                        focusedTextColor         = MegaDrivePrimary,
                        unfocusedTextColor       = MegaDrivePrimary,
                        disabledTextColor        = MegaDriveDim,
                        focusedIndicatorColor    = MegaDrivePrimary,
                        unfocusedIndicatorColor  = MegaDriveDim,
                        disabledIndicatorColor   = MegaDriveDim,
                        cursorColor              = MegaDrivePrimary,
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
