package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.components.RetroButton
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalScreen
import se.joynes.aiterminalhub.ui.theme.*

@Composable
fun SessionHostScreen(
    serverId: Long,
    onBack: () -> Unit,
    onAddProject: () -> Unit,
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val tabs by viewModel.tabs.collectAsState()
    val pagerState = rememberPagerState { maxOf(tabs.size, 1) }

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
                    onTabSelected = { /* pager scroll not needed for now */ },
                    onTabClose = { viewModel.closeTab(it) },
                    onAddProject = onAddProject
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
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
