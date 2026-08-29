package se.joynes.terminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.joynes.terminalhub.ui.theme.*
import kotlin.math.roundToInt

/**
 * Draggable text-input panel that overlays the terminal content inside the same window.
 * Using the same window (not a Dialog) is required for the IME microphone / voice dictation
 * to work — Dialog creates a separate window and loses IME voice support.
 */
@Composable
fun FloatingTextInputDialog(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    history: List<String> = emptyList(),
    onSaveHistory: (String) -> Unit = {},
    bottomAvoidanceDp: Dp = 0.dp,
    panelOpacity: Float? = null,
    onPanelOpacityChange: ((Float) -> Unit)? = null
) {
    val density = LocalDensity.current
    var showHistory by remember { mutableStateOf(false) }
    var localPanelOpacity by rememberSaveable { mutableStateOf(0.50f) }
    val effectivePanelOpacity = normalizeTextInputPanelOpacity(panelOpacity ?: localPanelOpacity)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun send() {
        if (text.text.isNotEmpty()) {
            onSaveHistory(text.text)
            onSend(text.text)
            onDismiss()
        }
    }

    // Full-size overlay so the panel can float over the terminal without creating a new window.
    // Intentionally do not intercept taps outside the panel: the user should be able to keep
    // scrolling or interacting with the terminal while the text input stays open.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // maxHeight is the actual host area below the app's status/tab bars. Using the
        // full device screen height here double-counted that top area and pushed the
        // panel's last lines underneath the configurable key bar.
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
        val bottomAvoidancePx = with(density) { bottomAvoidanceDp.toPx() }
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val panelWidthDp = maxWidth * 0.92f
        val panelWidthPx = with(density) { panelWidthDp.toPx() }
        val panelHeightPx = with(density) { 184.dp.toPx() }
        val minPanelTopPx = with(density) { 12.dp.toPx() }
        val panelBottomGapPx = with(density) { 8.dp.toPx() }
        val verticalLayout = calculateFloatingPanelVerticalLayout(
            containerHeightPx = containerHeightPx,
            imeBottomPx = imeBottomPx,
            bottomAvoidancePx = bottomAvoidancePx,
            panelHeightPx = panelHeightPx,
            minPanelTopPx = minPanelTopPx,
            panelBottomGapPx = panelBottomGapPx
        )
        val maxPanelTopPx = verticalLayout.maxPanelTopPx
        val anchoredPanelTopPx = verticalLayout.anchoredPanelTopPx

        var offsetX by remember(availableWidthPx, panelWidthPx) {
            mutableFloatStateOf(((availableWidthPx - panelWidthPx) / 2f).coerceAtLeast(0f))
        }
        var offsetY by remember(anchoredPanelTopPx) { mutableFloatStateOf(anchoredPanelTopPx) }

        LaunchedEffect(availableWidthPx, panelWidthPx) {
            offsetX = offsetX.coerceIn(0f, (availableWidthPx - panelWidthPx).coerceAtLeast(0f))
        }
        LaunchedEffect(anchoredPanelTopPx, maxPanelTopPx) {
            val draggedAwayFromAnchor = kotlin.math.abs(offsetY - anchoredPanelTopPx) > 1f
            offsetY = if (draggedAwayFromAnchor) {
                offsetY.coerceIn(minPanelTopPx, maxPanelTopPx)
            } else {
                anchoredPanelTopPx
            }
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(panelWidthDp)
                .background(MegaDriveSurface.copy(alpha = effectivePanelOpacity), RoundedCornerShape(4.dp))
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
                    .background(MegaDrivePrimary.copy(alpha = effectivePanelOpacity), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "TEXT INPUT",
                    color = MegaDriveBg,
                    fontSize = 11.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x)
                                .coerceIn(0f, (availableWidthPx - panelWidthPx).coerceAtLeast(0f))
                            offsetY = (offsetY + drag.y).coerceIn(minPanelTopPx, maxPanelTopPx)
                        }
                    }
                )
                Slider(
                    value = effectivePanelOpacity,
                    onValueChange = { value ->
                        val normalized = normalizeTextInputPanelOpacity(value)
                        if (onPanelOpacityChange != null) {
                            onPanelOpacityChange(normalized)
                        } else {
                            localPanelOpacity = normalized
                        }
                    },
                    valueRange = MIN_TEXT_INPUT_PANEL_OPACITY..MAX_TEXT_INPUT_PANEL_OPACITY,
                    colors = SliderDefaults.colors(
                        thumbColor = MegaDriveBg,
                        activeTrackColor = MegaDriveBg,
                        inactiveTrackColor = MegaDriveBg.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .width(72.dp)
                        .height(30.dp)
                        .semantics { contentDescription = "Text input opacity" }
                )
                Text(
                    "${(effectivePanelOpacity * 100).roundToInt()}%",
                    color = MegaDriveBg,
                    fontSize = 9.sp,
                    fontFamily = MonoFontFamily,
                    modifier = Modifier.width(30.dp)
                )
                Spacer(Modifier.weight(1f))
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
                        history.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(color = MegaDriveDim.copy(alpha = 0.45f))
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = textInputHistoryPreview(entry),
                                        color = MegaDrivePrimary,
                                        fontSize = 12.sp,
                                        fontFamily = MonoFontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                leadingIcon = {
                                    Text(
                                        text = "${index + 1}.",
                                        color = MegaDriveDim,
                                        fontSize = 10.sp,
                                        fontFamily = MonoFontFamily
                                    )
                                },
                                onClick = {
                                    onTextChange(TextFieldValue(entry, TextRange(entry.length)))
                                    showHistory = false
                                },
                                modifier = Modifier.background(MegaDriveSurface)
                            )
                        }
                    }
                }
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        "Type or dictate...",
                        color = MegaDriveDim, fontSize = 12.sp, fontFamily = MonoFontFamily
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor    = MegaDriveBg.copy(alpha = effectivePanelOpacity),
                    unfocusedContainerColor  = MegaDriveBg.copy(alpha = effectivePanelOpacity),
                    disabledContainerColor   = MegaDriveBg.copy(alpha = effectivePanelOpacity),
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
                    .fillMaxWidth()
                    .padding(8.dp)
                    .height(108.dp)
                    .focusRequester(focusRequester)
            )
        }
    }
}

internal const val MIN_TEXT_INPUT_PANEL_OPACITY = 0.15f
internal const val MAX_TEXT_INPUT_PANEL_OPACITY = 1f

internal fun normalizeTextInputPanelOpacity(value: Float): Float =
    value.coerceIn(MIN_TEXT_INPUT_PANEL_OPACITY, MAX_TEXT_INPUT_PANEL_OPACITY)

internal fun textInputHistoryPreview(entry: String): String =
    entry.replace(Regex("\\s+"), " ").trim()

internal data class FloatingPanelVerticalLayout(
    val availableBottomPx: Float,
    val maxPanelTopPx: Float,
    val anchoredPanelTopPx: Float
)

internal fun calculateFloatingPanelVerticalLayout(
    containerHeightPx: Float,
    imeBottomPx: Float,
    bottomAvoidancePx: Float,
    panelHeightPx: Float,
    minPanelTopPx: Float,
    panelBottomGapPx: Float
): FloatingPanelVerticalLayout {
    val availableBottomPx = (containerHeightPx - imeBottomPx - bottomAvoidancePx)
        .coerceAtLeast(minPanelTopPx + panelHeightPx)
    val maxPanelTopPx = (availableBottomPx - panelHeightPx).coerceAtLeast(minPanelTopPx)
    val anchoredPanelTopPx = (availableBottomPx - panelHeightPx - panelBottomGapPx)
        .coerceIn(minPanelTopPx, maxPanelTopPx)
    return FloatingPanelVerticalLayout(
        availableBottomPx = availableBottomPx,
        maxPanelTopPx = maxPanelTopPx,
        anchoredPanelTopPx = anchoredPanelTopPx
    )
}

/** Compatibility overload for static previews that do not need cursor-aware insertion. */
@Composable
fun FloatingTextInputDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    history: List<String> = emptyList(),
    onSaveHistory: (String) -> Unit = {},
    bottomAvoidanceDp: Dp = 0.dp,
    panelOpacity: Float? = null,
    onPanelOpacityChange: ((Float) -> Unit)? = null
) {
    FloatingTextInputDialog(
        text = TextFieldValue(text, TextRange(text.length)),
        onTextChange = { onTextChange(it.text) },
        onSend = onSend,
        onDismiss = onDismiss,
        history = history,
        onSaveHistory = onSaveHistory,
        bottomAvoidanceDp = bottomAvoidanceDp,
        panelOpacity = panelOpacity,
        onPanelOpacityChange = onPanelOpacityChange
    )
}
