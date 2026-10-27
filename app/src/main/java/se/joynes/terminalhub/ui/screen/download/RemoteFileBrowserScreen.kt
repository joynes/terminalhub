package se.joynes.terminalhub.ui.screen.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import se.joynes.terminalhub.data.ssh.RemoteFileEntry
import se.joynes.terminalhub.ui.theme.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path

internal val BrowserFolderIcon = ImageVector.Builder("Folder", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
        moveTo(3f, 6f); lineTo(9f, 6f); lineTo(11f, 8f); lineTo(21f, 8f)
        lineTo(21f, 19f); lineTo(3f, 19f); close()
    }
}.build()
internal val BrowserFileIcon = ImageVector.Builder("File", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
        moveTo(5f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(5f, 21f); close()
        moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
        moveTo(8f, 12f); lineTo(16f, 12f); moveTo(8f, 16f); lineTo(16f, 16f)
    }
}.build()

internal data class RemoteBreadcrumb(val name: String, val directory: String)
internal fun remoteBreadcrumbs(directory: String): List<RemoteBreadcrumb> =
    listOf(RemoteBreadcrumb("Project", "")) + directory.split('/').filter(String::isNotEmpty).let { parts ->
        parts.mapIndexed { index, name -> RemoteBreadcrumb(name, parts.take(index + 1).joinToString("/")) }
    }

/** One full-screen window above the terminal/keybar, with fixed controls and a single scrolling list. */
@Composable
internal fun RemoteBrowserFrame(title: String, onBack: () -> Unit, onClose: () -> Unit = onBack,
    content: @Composable ColumnScope.() -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val root = LocalView.current.rootView
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    fun usableHeight(): Int {
        val insets = ViewCompat.getRootWindowInsets(root)?.getInsetsIgnoringVisibility(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val height = root.height.takeIf { it > 0 } ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
        return (height - (insets?.top ?: 0) - (insets?.bottom ?: 0)).coerceAtLeast(1)
    }
    var height by remember(root, configuration.screenHeightDp, configuration.screenWidthDp) { mutableIntStateOf(usableHeight()) }
    DisposableEffect(root, configuration.screenHeightDp, configuration.screenWidthDp) {
        val listener = android.view.View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> height = usableHeight() }
        root.addOnLayoutChangeListener(listener)
        onDispose { root.removeOnLayoutChangeListener(listener) }
    }
    LaunchedEffect(Unit) { keyboard?.hide() }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = true, dismissOnClickOutside = false)) {
        // Older Compose dialogs use the full Android 15 display height even when system
        // bars are outside the window. Bound the content to the actual usable window.
        Column(Modifier.fillMaxWidth().height(with(density) { height.toDp() })
            .background(MegaDriveSurface).imePadding().testTag("remote-browser-fullscreen")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MegaDriveOnSurface) }
                Text(title, modifier = Modifier.weight(1f), color = MegaDriveOnSurface,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close file browser", tint = MegaDriveOnSurface) }
            }
            content()
        }
    }
}

@Composable
internal fun ColumnScope.RemoteBrowserListing(
    directory: String, entries: List<RemoteFileEntry>, browser: RemoteBrowserUiState,
    sort: RemoteFileSortSelection, onSort: (RemoteFileSort) -> Unit,
    onNavigate: (String) -> Unit, onRefresh: () -> Unit, onPreview: (RemoteFileEntry) -> Unit,
    destination: String, destinationMessage: String?, checkingDestination: Boolean,
    onChangeDestination: () -> Unit, onDownload: () -> Unit
) {
    val query = browser.query
    val searching = browser.searching
    var sortMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val crumbs = remember(directory) { remoteBreadcrumbs(directory) }
    val crumbScroll = rememberLazyListState()
    LaunchedEffect(directory) { crumbScroll.scrollToItem(crumbs.lastIndex) }
    Row(Modifier.fillMaxWidth().testTag("browser-navigation"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onNavigate("") }, enabled = directory.isNotEmpty()) {
            Icon(Icons.Default.Home, "Project folder", tint = if (directory.isEmpty()) MegaDriveDim else MegaDrivePrimary)
        }
        LazyRow(Modifier.weight(1f), state = crumbScroll, verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(crumbs, key = { _, crumb -> crumb.directory }) { index, crumb ->
                if (index > 0) Text("/", color = MegaDriveDim)
                TextButton(onClick = { onNavigate(crumb.directory) }, enabled = crumb.directory != directory,
                    modifier = Modifier.widthIn(max = 200.dp).testTag("breadcrumb:${crumb.directory}")) {
                    Text(crumb.name, color = if (crumb.directory == directory) MegaDriveOnSurface else MegaDrivePrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        IconButton(onClick = { onNavigate(parentRemoteDirectory(directory)) }, enabled = directory.isNotEmpty()) {
            Icon(Icons.Default.KeyboardArrowUp, "Up one level", tint = if (directory.isEmpty()) MegaDriveDim else MegaDrivePrimary)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${entries.count { it.isDirectory }} folders · ${entries.count { !it.isDirectory }} files",
            modifier = Modifier.weight(1f), color = MegaDriveDim, style = MaterialTheme.typography.bodySmall)
        IconButton(onClick = {
            browser.searching = !searching
            if (!browser.searching) { browser.query = ""; focus.clearFocus(); keyboard?.hide() }
        }) {
            Icon(Icons.Default.Search, "Search this folder", tint = MegaDriveOnSurface)
        }
        Box {
            TextButton(onClick = { sortMenu = true }, modifier = Modifier.semantics { contentDescription = "Sort files" }) {
                Text(sort.property.label.lowercase().replaceFirstChar(Char::uppercase) + if (sort.ascending) " ↑" else " ↓")
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                RemoteFileSort.entries.forEach { property ->
                    DropdownMenuItem(text = { Text(property.label.lowercase().replaceFirstChar(Char::uppercase) +
                        if (sort.property == property) if (sort.ascending) " ↑" else " ↓" else "") },
                        onClick = { onSort(property); sortMenu = false })
                }
            }
        }
        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh files", tint = MegaDriveOnSurface) }
    }
    if (searching) OutlinedTextField(query, { browser.query = it }, singleLine = true,
        label = { Text("Filter names in this folder") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
    val visible = remember(entries, query) { entries.filter { it.name.contains(query, ignoreCase = true) } }
    // Searching uses a separate viewport so a temporary filter cannot overwrite the saved position.
    RemoteFileList(visible, browser.selectedFiles, modifier = Modifier.weight(1f).testTag("remote-file-list"),
        listState = if (query.isEmpty()) browser.list else browser.filteredList,
        onOpenDirectory = { onNavigate(childRemoteDirectory(directory, it.name)) },
        onToggleFile = { browser.selectedFiles = toggleRemoteFileSelection(browser.selectedFiles, it.name) },
        onPreviewFile = onPreview, previewOnTap = true)
    HorizontalDivider(color = MegaDriveDim.copy(alpha = .25f))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).navigationBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${browser.selectedFiles.size} selected", modifier = Modifier.weight(1f), color = MegaDriveOnSurface)
            TextButton(onClick = { browser.selectedFiles = visible.filterNot { it.isDirectory }.map { it.name }.toSet() }) { Text("Select all") }
            TextButton(onClick = { browser.selectedFiles = emptySet() }, enabled = browser.selectedFiles.isNotEmpty()) { Text("Clear") }
        }
        if (browser.selectedFiles.isNotEmpty()) Button(onClick = onDownload, enabled = !checkingDestination,
            modifier = Modifier.fillMaxWidth().testTag("download-selected")) {
            Text(if (checkingDestination) "Checking folder…" else "Download ${browser.selectedFiles.size} file(s)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(destination, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MegaDriveDim, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onChangeDestination, enabled = !checkingDestination) { Text("Change folder") }
        }
        destinationMessage?.let { Text(it, color = MegaDriveAccent, style = MaterialTheme.typography.bodySmall) }
    }
}
