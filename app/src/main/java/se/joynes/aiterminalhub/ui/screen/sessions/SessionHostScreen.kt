package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalScreen
import se.joynes.aiterminalhub.ui.theme.*
import kotlin.math.abs

@Composable
fun SessionHostScreen(
    serverId: Long,
    onBack: () -> Unit,
    onAddProject: () -> Unit,
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val tabs by viewModel.tabs.collectAsState()
    val pagerState = rememberPagerState { maxOf(tabs.size, 1) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(serverId) { viewModel.initForServer(serverId) }
    LaunchedEffect(pagerState.currentPage, tabs.size) {
        if (tabs.isNotEmpty()) viewModel.activateTab(pagerState.currentPage)
    }

    Scaffold(
        topBar = { RetroTopBar(title = "TERMINAL", onBack = onBack) },
        containerColor = MegaDriveBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MegaDriveBg)) {
            if (tabs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "NO PROJECTS",
                            color = MegaDriveDim,
                            fontSize = 12.sp,
                            fontFamily = MonoFontFamily
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                        RetroButton("[ + ADD PROJECT ]", onAddProject)
                    }
                }
            } else {
                SessionTabBar(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } },
                    onTabClose = { viewModel.closeTab(it) },
                    onAddProject = onAddProject
                )
                // Intercept horizontal drags at PointerEventPass.Initial (before the Terminal
                // composable sees them) and drive the pager continuously so the swipe feels
                // like scrolling a list rather than a discrete gesture.
                val swipeModifier = Modifier.pointerInput(pagerState, tabs.size) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        val vt = VelocityTracker()
                        vt.resetTracking()
                        vt.addPosition(down.uptimeMillis, down.position)
                        var dx = 0f
                        var dy = 0f
                        var isHorizontal = false

                        // Feed pixel deltas into the pager's scroll session so content
                        // moves with the finger in real time.
                        val deltas = Channel<Float>(Channel.UNLIMITED)
                        val scrollJob = coroutineScope.launch {
                            pagerState.scroll { for (d in deltas) scrollBy(d) }
                        }

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                            vt.addPosition(c.uptimeMillis, c.position)
                            val delta = c.position - c.previousPosition
                            dx += delta.x
                            dy += delta.y
                            if (!isHorizontal && abs(dx) > viewConfiguration.touchSlop) {
                                isHorizontal = abs(dx) >= abs(dy)
                            }
                            if (isHorizontal) {
                                c.consume()
                                deltas.trySend(-delta.x)
                            }
                            if (!c.pressed) break
                        }

                        deltas.close()

                        // Snap to the target page based on velocity and distance.
                        if (isHorizontal && tabs.size > 1) {
                            val vx = vt.calculateVelocity().x
                            val target = when {
                                vx < -300f || dx < -60.dp.toPx() ->
                                    (pagerState.currentPage + 1).coerceAtMost(tabs.size - 1)
                                vx > 300f || dx > 60.dp.toPx() ->
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                else -> pagerState.currentPage
                            }
                            coroutineScope.launch { pagerState.animateScrollToPage(target) }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f).fillMaxWidth().then(swipeModifier)
                ) { page ->
                    val tab = tabs.getOrNull(page)
                    if (tab?.sessionId != null) {
                        TerminalScreen(sessionId = tab.sessionId)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(MegaDriveBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "CONNECTING...",
                                color = MegaDrivePrimary,
                                fontSize = 12.sp,
                                fontFamily = MonoFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}
