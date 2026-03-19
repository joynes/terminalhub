package se.joynes.aiterminalhub.ui.screen.applog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import se.joynes.aiterminalhub.data.model.AppLogEntry
import se.joynes.aiterminalhub.ui.components.*
import se.joynes.aiterminalhub.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOG_LEVEL_COLORS = mapOf(
    "TRACE" to MegaDriveDim,
    "DEBUG" to MegaDriveOnSurface,
    "INFO" to MegaDriveGreen,
    "WARN" to MegaDriveWarning,
    "ERROR" to MegaDriveError
)

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

@Composable
fun AppLogScreen(
    onBack: () -> Unit,
    viewModel: AppLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()
    val search by viewModel.searchQuery.collectAsState()
    val selectedLevel by viewModel.selectedLevel.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size, autoScroll) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            RetroTopBar(
                title = "APP LOG",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { viewModel.export() }) {
                        Text("EXP", color = MegaDriveWarning, fontSize = 10.sp, fontFamily = MonoFontFamily)
                    }
                    IconButton(onClick = { viewModel.toggleAutoScroll() }) {
                        Text(
                            if (autoScroll) "AS:ON" else "AS:OFF",
                            color = if (autoScroll) MegaDriveGreen else MegaDriveDim,
                            fontSize = 8.sp,
                            fontFamily = MonoFontFamily
                        )
                    }
                }
            )
        },
        containerColor = MegaDriveBg
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MegaDriveBg)) {
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.setSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search logs...", color = MegaDriveDim, fontSize = 12.sp, fontFamily = MonoFontFamily) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MegaDrivePrimary,
                    unfocusedBorderColor = MegaDriveDim,
                    focusedTextColor = MegaDriveOnSurface,
                    unfocusedTextColor = MegaDriveOnSurface,
                    cursorColor = MegaDrivePrimary
                ),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR").forEach { level ->
                    val isSelected = selectedLevel == level
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setLevel(level) },
                        label = { Text(level, fontSize = 9.sp, fontFamily = MonoFontFamily) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LOG_LEVEL_COLORS[level]?.copy(alpha = 0.2f) ?: MegaDrivePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = LOG_LEVEL_COLORS[level] ?: MegaDrivePrimary,
                            containerColor = MegaDriveSurface,
                            labelColor = MegaDriveDim
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(logs, key = { it.id }) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: AppLogEntry) {
    val color = LOG_LEVEL_COLORS[entry.level] ?: MegaDriveOnSurface
    val time = remember(entry.timestamp) { TIME_FMT.format(Date(entry.timestamp)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MegaDriveSurface.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(time, color = MegaDriveDim, fontSize = 9.sp, fontFamily = MonoFontFamily)
        Text(entry.level.take(5).padEnd(5), color = color, fontSize = 9.sp, fontFamily = MonoFontFamily)
        Text("[${entry.tag}]", color = MegaDriveDim, fontSize = 9.sp, fontFamily = MonoFontFamily)
        Text(entry.message, color = MegaDriveOnSurface, fontSize = 9.sp, fontFamily = MonoFontFamily, modifier = Modifier.weight(1f))
    }
}
