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
                // Intercept horizontal swipes at the Initial pass so they reach the
                // pager even when the Terminal composable consumes touch events.
                val swipeModifier = Modifier.pointerInput(pagerState, tabs.size) {
                    val velocityTracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.position - change.previousPosition
                            dx += delta.x
                            dy += delta.y
                            // Consume the event once we're confident it's a horizontal drag,
                            // preventing the terminal from treating it as a scrollback gesture.
                            if (abs(dx) > abs(dy) * 1.5f && abs(dx) > viewConfiguration.touchSlop) {
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        val vx = velocityTracker.calculateVelocity().x
                        val isHorizontal = abs(dx) > abs(dy)
                        val isFling = abs(vx) > 400f || abs(dx) > 80.dp.toPx()
                        if (isHorizontal && isFling && tabs.size > 1) {
                            val target = if (dx < 0) {
                                (pagerState.currentPage + 1).coerceAtMost(tabs.size - 1)
                            } else {
                                (pagerState.currentPage - 1).coerceAtLeast(0)
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
