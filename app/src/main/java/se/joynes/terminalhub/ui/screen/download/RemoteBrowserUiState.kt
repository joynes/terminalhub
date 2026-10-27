package se.joynes.terminalhub.ui.screen.download

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import se.joynes.terminalhub.data.settings.RemoteBrowseViewState
import se.joynes.terminalhub.data.ssh.RemoteFileEntry

internal fun remoteEntryKey(entry: RemoteFileEntry) = "${entry.isDirectory}:${entry.name}"

/** Prefer the same file over the old index when the remote listing has changed. */
internal fun restoredRemoteScroll(saved: RemoteBrowseViewState, entries: List<RemoteFileEntry>): Pair<Int, Int> {
    if (entries.isEmpty()) return 0 to 0
    val anchorIndex = saved.anchorKey?.let { key -> entries.indexOfFirst { remoteEntryKey(it) == key } } ?: -1
    return if (anchorIndex >= 0) anchorIndex to saved.firstVisibleOffset.coerceAtLeast(0)
    else saved.firstVisibleIndex.coerceIn(0, entries.lastIndex) to 0
}

internal class RemoteBrowserUiState(saved: RemoteBrowseViewState, entries: List<RemoteFileEntry>) {
    private val restored = restoredRemoteScroll(saved, entries)
    val list = LazyListState(restored.first, restored.second)
    val filteredList = LazyListState()
    var query by mutableStateOf("")
    var searching by mutableStateOf(false)
    val panel = ScrollState(saved.panelScroll.coerceAtLeast(0))
    var selectedFiles by mutableStateOf(saved.selectedFiles.intersect(entries.filterNot { it.isDirectory }.map { it.name }.toSet()))

    fun snapshot(entries: List<RemoteFileEntry>) = RemoteBrowseViewState(
        firstVisibleIndex = list.firstVisibleItemIndex,
        firstVisibleOffset = list.firstVisibleItemScrollOffset,
        anchorKey = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }?.key as? String
            ?: entries.getOrNull(list.firstVisibleItemIndex)?.let(::remoteEntryKey),
        panelScroll = panel.value,
        selectedFiles = selectedFiles
    )
}

@OptIn(FlowPreview::class)
@Composable
internal fun rememberRemoteBrowserUiState(
    identity: String,
    directory: String,
    entries: List<RemoteFileEntry>,
    load: () -> RemoteBrowseViewState,
    save: (RemoteBrowseViewState) -> Unit
): RemoteBrowserUiState = key(identity, directory) {
    val state = remember { RemoteBrowserUiState(load(), entries) }
    val currentEntries by rememberUpdatedState(entries)
    val currentSave by rememberUpdatedState(save)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state) {
        // Avoid a disk update for every animation frame; disposal/backgrounding flushes immediately.
        snapshotFlow { state.snapshot(currentEntries) }.debounce(150).collect { currentSave(it) }
    }
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) currentSave(state.snapshot(currentEntries))
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            currentSave(state.snapshot(currentEntries))
        }
    }
    state
}
