package com.example.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.data.NoteEntity
import com.example.data.ClipboardEntity


import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.AdBlocker
import com.example.util.WebViewPool
import java.io.ByteArrayInputStream

@Composable
fun BrowserScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit = {}) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()
    
    var showTabSwitcher by remember { mutableStateOf(false) }

    if (showTabSwitcher) {
        BackHandler { showTabSwitcher = false }
        TabSwitcherScreen(
            tabs = tabs,
            activeTabId = activeTabId,
            onCloseTab = { viewModel.closeTab(it) },
            onSelectTab = { 
                viewModel.switchTab(it)
                showTabSwitcher = false 
            },
            onNewTab = { 
                viewModel.createNewTab()
                showTabSwitcher = false
            },
            onCloseSwitcher = { showTabSwitcher = false }
        )
    } else {
        BrowserContent(viewModel, tabs, activeTab, onOpenTabSwitcher = { showTabSwitcher = true }, onNavigate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserContent(
    viewModel: MainViewModel,
    tabs: List<TabState>,
    activeTab: TabState,
    onOpenTabSwitcher: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val isSplitScreen by viewModel.isSplitScreen.collectAsStateWithLifecycle()
    val secondaryTabId by viewModel.secondaryTabId.collectAsStateWithLifecycle()
    val secondaryTab = tabs.find { it.id == secondaryTabId }

    var showSyncDialog by remember { mutableStateOf(false) }
    var showMeshDialog by remember { mutableStateOf(false) }
    val syncServerIp by viewModel.syncServerIp.collectAsStateWithLifecycle()
    val sharedClipboard by viewModel.sharedClipboard.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSplitScreen && secondaryTab != null) {
            SplitScreenLayout(
                primaryContent = {
                    BrowserTabPanel(
                        viewModel = viewModel,
                        tab = activeTab,
                        modifier = Modifier.fillMaxSize(),
                        onOpenTabSwitcher = onOpenTabSwitcher,
                        onNavigate = onNavigate,
                        onShowSyncDialog = { showSyncDialog = true },
                        onShowMeshDialog = { showMeshDialog = true }
                    )
                },
                secondaryContent = {
                    BrowserTabPanel(
                        viewModel = viewModel,
                        tab = secondaryTab,
                        modifier = Modifier.fillMaxSize(),
                        onOpenTabSwitcher = onOpenTabSwitcher,
                        onNavigate = onNavigate,
                        onShowSyncDialog = { showSyncDialog = true },
                        onShowMeshDialog = { showMeshDialog = true }
                    )
                }
            )
        } else {
            tabs.forEach { tab ->
                val isActive = tab.id == activeTab.id
                BrowserTabPanel(
                    viewModel = viewModel,
                    tab = tab,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (isActive) 1f else 0f)
                        .alpha(if (isActive) 1f else 0f)
                        .offset(x = if (isActive) 0.dp else 20000.dp),
                    onOpenTabSwitcher = onOpenTabSwitcher,
                    onNavigate = onNavigate,
                    onShowSyncDialog = { showSyncDialog = true },
                    onShowMeshDialog = { showMeshDialog = true }
                )
            }
        }
    }

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSyncDialog = false
                viewModel.stopSyncServer()
            },
            title = { Text("Local Hub (Remote & Sync)") },
            text = {
                Column {
                    Text("Connect your devices on the same Wi-Fi to:", style = MaterialTheme.typography.bodyMedium)
                    Text(syncServerIp ?: "Starting...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Open this IP in any browser on your network to send SMS/OTP messages, manage open tabs, and sync clipboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = sharedClipboard,
                        onValueChange = { viewModel.updateClipboard(it) },
                        label = { Text("Shared Clipboard") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showSyncDialog = false
                    viewModel.stopSyncServer()
                }) {
                    Text("Close")
                }
            }
        )
    }

    val meshServerIp by viewModel.meshServerIp.collectAsStateWithLifecycle()
    val meshPin by viewModel.meshPin.collectAsStateWithLifecycle()

    if (showMeshDialog) {
        AlertDialog(
            onDismissRequest = { 
                showMeshDialog = false 
                viewModel.stopMeshServer()
            },
            title = { Text("Mesh Chat Connect") },
            text = {
                Column {
                    Text("Secure E2EE Mesh Network", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("End-to-End encrypted chat over local Wi-Fi. Messages are stored only in RAM and vanish when closed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (meshServerIp == null) {
                        Button(onClick = { viewModel.startMeshServer() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Mesh Host")
                        }
                    } else {
                        Text("Share this address:", style = MaterialTheme.typography.bodyMedium)
                        Text(meshServerIp ?: "", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Session PIN:", style = MaterialTheme.typography.bodyMedium)
                        Text(meshPin ?: "", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { 
                            showMeshDialog = false
                            viewModel.createNewTab(meshServerIp ?: "")
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Chat in Browser Tab")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showMeshDialog = false 
                    viewModel.stopMeshServer()
                }) {
                    Text("Close Host")
                }
            }
        )
    }

}
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BrowserTabPanel(
    viewModel: MainViewModel,
    tab: TabState,
    modifier: Modifier = Modifier,
    onOpenTabSwitcher: () -> Unit,
    onNavigate: (String) -> Unit,
    onShowSyncDialog: () -> Unit,
    onShowMeshDialog: () -> Unit
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isBookmarked = bookmarks.any { it.url == tab.url }
    var urlInput by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val isFocusMode by viewModel.isFocusMode.collectAsStateWithLifecycle()

    val showScratchpad by viewModel.showScratchpadBottomSheet.collectAsStateWithLifecycle()
    val showClipboard by viewModel.showClipboardBottomSheet.collectAsStateWithLifecycle()
    val receivedMessage by viewModel.receivedMessage.collectAsStateWithLifecycle()
    if (receivedMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearReceivedMessage() },
            title = { Text("New Message Received") },
            text = { Text(receivedMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.updateClipboard(receivedMessage ?: "")
                    viewModel.clearReceivedMessage() 
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearReceivedMessage() }) { Text("Dismiss") }
            }
        )
    }

    
    if (showScratchpad) {
        ModalBottomSheet(onDismissRequest = { viewModel.setScratchpadVisibility(false) }) {
            ScratchpadContent(viewModel)
        }
    }
    
    if (showClipboard) {
        ModalBottomSheet(onDismissRequest = { viewModel.setClipboardVisibility(false) }) {
            ClipboardContent(viewModel)
        }
    }

    val isSplitScreen by viewModel.isSplitScreen.collectAsStateWithLifecycle()
    val selectedSearchEngine by viewModel.selectedSearchEngine.collectAsStateWithLifecycle()
    var showEngineDialog by remember { mutableStateOf(false) }

    var uploadMessage by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val isUrlFocused = remember { mutableStateOf(false) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            uploadMessage?.onReceiveValue(uris.toTypedArray())
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    BackHandler(enabled = activeWebView?.canGoBack() == true) {
        activeWebView?.goBack()
    }


    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 600.dp
        
        val addressBarContent = @Composable {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = if (isWide) 2.dp else 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    if (!isWide) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        IconButton(onClick = { viewModel.loadUrl(tab.id, selectedSearchEngine.searchUrl.replace("search?q=%s", "").replace("?q=%s", "")) }) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                        
                        // Compact Address Bar
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Box {
                                    IconButton(
                                        onClick = { showEngineDialog = true },
                                        modifier = Modifier.size(28.dp).padding(end = 4.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, androidx.compose.foundation.shape.CircleShape)
                                        ) {
                                            Text(
                                                text = selectedSearchEngine.displayName.take(1),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showEngineDialog,
                                        onDismissRequest = { showEngineDialog = false }
                                    ) {
                                        SearchEngine.entries.forEach { engine ->
                                            DropdownMenuItem(
                                                text = { Text(engine.displayName) },
                                                onClick = {
                                                    viewModel.setSearchEngine(engine)
                                                    showEngineDialog = false
                                                }
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = urlInput.let { if (it == "app://newtab") "" else it },
                                        onValueChange = { urlInput = it },
                                        modifier = Modifier.fillMaxWidth().onFocusChanged { isUrlFocused.value = it.isFocused },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = ImeAction.Go,
                                            keyboardType = KeyboardType.Uri
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onGo = {
                                                if (urlInput.isNotBlank()) {
                                                    val formattedUrl = if (urlInput.startsWith("http") || urlInput.startsWith("file://")) urlInput else {
                                                        if (urlInput.contains(".") && !urlInput.contains(" ")) "https://$urlInput"
                                                        else String.format(selectedSearchEngine.searchUrl, urlInput.replace(" ", "+"))
                                                    }
                                                    viewModel.loadUrl(tab.id, formattedUrl)
                                                }
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (urlInput.isEmpty() || urlInput == "app://newtab") {
                                                Text("Search or type URL", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            innerTextField()
                                        }
                                    )
                                }
                                if (tab.url != "app://newtab") {
                                    IconButton(onClick = { 
                                        viewModel.toggleBookmark(tab.url, activeWebView?.title ?: tab.title)
                                    }, modifier = Modifier.size(28.dp)) {
                                        Icon(
                                            if (isBookmarked) Icons.Default.Star else Icons.Outlined.Star,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .clickable { onOpenTabSwitcher() },
                            contentAlignment = Alignment.Center
                        ) {
                            val tabsCount = viewModel.tabs.collectAsStateWithLifecycle().value.size
                            Text(
                                tabsCount.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Tab") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.createNewTab()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isSplitScreen) "Exit Split Screen" else "Split Screen") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.toggleSplitScreen()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Page") },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.toggleBookmark(tab.url, activeWebView?.title ?: tab.title)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = {
                                        showMoreMenu = false
                                        activeWebView?.reload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Offline") },
                                    onClick = {
                                        showMoreMenu = false
                                        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                                        val file = java.io.File(dir, "offline_${System.currentTimeMillis()}.mht")
                                        activeWebView?.saveWebArchive(file.absolutePath, false) { path ->
                                            if (path != null) {
                                                viewModel.saveOfflinePage(tab.url, activeWebView?.title ?: "Saved Page", path)
                                                Toast.makeText(context, "Saved offline", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("history")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("bookmarks")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Offline Pages") },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("offline")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Mesh Chat / Secure Connect") },
                                    onClick = {
                                        showMoreMenu = false
                                        onShowMeshDialog()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sync Clipboard") },
                                    onClick = {
                                        showMoreMenu = false
                                        onShowSyncDialog()
                                        viewModel.startSyncServer()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("settings")
                                    }
                                )

                            }
                        }
                    }

                    if (tab.isLoading) {
                        LinearProgressIndicator(
                            progress = { tab.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

        }
        val webContent: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxSize()) {
                if (tab.url == "app://newtab") {
                    NewTabDashboard(viewModel, bookmarks, tab.id)
                } else if (tab.url.startsWith("app://offline/")) {
                    val pageId = tab.url.removePrefix("app://offline/").toIntOrNull()
                    val allPages by viewModel.allPages.collectAsStateWithLifecycle()
                    val page = allPages.find { it.id == pageId }
                    
                    if (page != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebViewPool.getWebView(context).apply {
                                    loadUrl("file://${page.filePath}")
                                }
                            }
                        )
                    } else {
                        Text("Offline page not found", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    HardenedWebView(
                        url = tab.url,
                        tabId = tab.id,
                        webViewState = tab.webViewState,
                        isFocusMode = isFocusMode,
                        onTitleAndLoadingChange = { url, title, isLoading ->
                            viewModel.updateTabTitleAndLoading(tab.id, url, title, isLoading)
                        },
                        onProgressChange = { progress ->
                            viewModel.updateTabProgress(tab.id, progress)
                        },
                        onSaveState = { bundle ->
                            viewModel.saveWebViewState(tab.id, bundle)
                        },
                        onWebViewCreated = { view ->
                            activeWebView = view
                        },
                        onCreateNewTab = { url ->
                            viewModel.createNewTab(url)
                        },
                        onDownloadStarted = { id, title, url, mime, userAgent, cookies ->
                            viewModel.downloadManager.enqueue(id, title, url, mime, userAgent, cookies)
                        },
                        onPageFinishedListener = { finishedUrl, view ->
                            val prompt = viewModel.pendingAiPrompt.value
                            if (prompt != null && finishedUrl.contains("gemini.google.com")) {
                                view.evaluateJavascript("""
                                    setTimeout(() => {
                                        let editor = document.querySelector('rich-textarea') || document.querySelector('textarea');
                                        if (editor) {
                                            editor.innerHTML = `${prompt}`;
                                            editor.value = `${prompt}`;
                                        }
                                    }, 2000);
                                """.trimIndent(), null)
                                viewModel.clearPendingAiPrompt()
                            }
                        }
                    )
                }
                
                // AwesomeBar Suggestions Overlay
                AnimatedVisibility(
                    visible = isUrlFocused.value && urlInput.isNotBlank() && urlInput != tab.url && urlInput != "app://newtab",
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).zIndex(10f)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        val suggestions = history.filter { it.url.contains(urlInput, ignoreCase = true) || it.title.contains(urlInput, ignoreCase = true) }.take(5)
                        if (suggestions.isNotEmpty()) {
                            items(suggestions.size) { index ->
                                val suggestion = suggestions[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadUrl(tab.id, suggestion.url)
                                            isUrlFocused.value = false
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(suggestion.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                        Text(suggestion.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                HorizontalDivider()
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val formattedUrl = if (urlInput.contains(".") && !urlInput.contains(" ")) "https://$urlInput"
                                            else String.format(selectedSearchEngine.searchUrl, urlInput.replace(" ", "+"))
                                            viewModel.loadUrl(tab.id, formattedUrl)
                                            isUrlFocused.value = false
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Search for \"$urlInput\"", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isWide) {
                addressBarContent()
                Box(modifier = Modifier.weight(1f)) { webContent() }
            } else {
                Box(modifier = Modifier.weight(1f)) { webContent() }
                addressBarContent()
            }
        }
        }
}
@Composable
fun TabSwitcherScreen(
    tabs: List<TabState>,
    activeTabId: String,
    onCloseTab: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onCloseSwitcher: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tabs (${tabs.size})", 
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = onCloseSwitcher,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(160.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tabs.size) { index ->
                    val tab = tabs[index]
                    val isActive = tab.id == activeTabId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { onSelectTab(tab.id) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Default.Home, // placeholder
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        tab.title, 
                                        style = MaterialTheme.typography.labelLarge, 
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Tab", modifier = Modifier.size(16.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    tab.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FloatingActionButton(
                        onClick = onNewTab,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab", modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }

}
@Composable
fun NewTabDashboard(viewModel: MainViewModel, bookmarks: List<com.example.data.Bookmark>, tabId: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.padding(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Premium Browser",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Fast, Secure & Private",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        
        if (bookmarks.isNotEmpty()) {
            Text(
                "Favorites",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(bookmarks.size) { index ->
                    val bookmark = bookmarks[index]
                    Card(
                        modifier = Modifier
                            .width(140.dp)
                            .height(100.dp)
                            .clickable { viewModel.loadUrl(tabId, bookmark.url) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                bookmark.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScratchpadContent(viewModel: MainViewModel) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val tabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp)) {
        Text("Scratchpad", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Quick note...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (text.isNotBlank()) {
                    viewModel.addNote(text)
                    text = ""
                }
            }) { Text("Save") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.foundation.lazy.LazyColumn {
            items(notes, key = { it.id }) { note ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(note.content)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.sendNoteToAi(note.content, tabId) }) { Text("Send to AI") }
                            IconButton(onClick = { viewModel.deleteNote(note) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardContent(viewModel: MainViewModel) {
    val clipboard by viewModel.clipboardHistory.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    
    val filtered = clipboard.filter { it.text.contains(searchQuery, ignoreCase = true) }
    
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Clipboard History", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { viewModel.clearClipboardHistory() }) { Text("Clear All") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search clipboard...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (filtered.isEmpty()) {
            Text("No clipboard history found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            androidx.compose.foundation.lazy.LazyColumn {
                items(filtered, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.text, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                viewModel.addNote(item.text)
                            }) { Icon(Icons.Default.Add, contentDescription = "Add to Notes") }
                        }
                    }
                }
            }
        }
    }
}
