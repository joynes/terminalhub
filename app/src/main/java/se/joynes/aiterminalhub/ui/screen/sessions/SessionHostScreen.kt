package se.joynes.aiterminalhub.ui.screen.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.ui.components.RetroTopBar
import se.joynes.aiterminalhub.ui.navigation.SessionTabBar
import se.joynes.aiterminalhub.ui.screen.terminal.TerminalScreen
import se.joynes.aiterminalhub.ui.theme.MegaDriveBg

@Composable
fun SessionHostScreen(
    serverId: Long,
    projectId: Long?,
    onBack: () -> Unit,
    viewModel: SessionHostViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val pagerState = rememberPagerState { maxOf(sessions.size, 1) }

    LaunchedEffect(serverId, projectId) { viewModel.initSession(serverId, projectId) }
    LaunchedEffect(pagerState.currentPage) { viewModel.onPageChanged(pagerState.currentPage) }

    Scaffold(
        topBar = { RetroTopBar(title = "SESSIONS", onBack = onBack) },
        containerColor = MegaDriveBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MegaDriveBg)) {
            SessionTabBar(
                sessionIds = sessions.map { it.id },
                selectedIndex = pagerState.currentPage,
                onTabSelected = { /* pager controlled */ }
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                val session = sessions.getOrNull(page)
                if (session != null) {
                    TerminalScreen(sessionId = session.id)
                }
            }
        }
    }
}
