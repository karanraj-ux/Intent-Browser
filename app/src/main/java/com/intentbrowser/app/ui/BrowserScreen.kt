package com.intentbrowser.app.ui
import com.intentbrowser.app.util.DownloadState
import com.intentbrowser.app.util.QrCodeGenerator
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Download

import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import com.intentbrowser.app.data.NoteEntity
import com.intentbrowser.app.data.ClipboardEntity


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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.intentbrowser.app.util.AdBlocker
import com.intentbrowser.app.util.WebViewPool
import com.intentbrowser.app.util.WebViewSettingsManager
import org.json.JSONObject
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
            onNewIncognitoTab = {
                viewModel.createNewTab(isIncognito = true)
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
    val syncPin by viewModel.syncPin.collectAsStateWithLifecycle()
    val nearbyDevices by viewModel.nearbyDevices.collectAsStateWithLifecycle()
    val isLeading by viewModel.isLeading.collectAsStateWithLifecycle()
    val following by viewModel.following.collectAsStateWithLifecycle()
    val followError by viewModel.followError.collectAsStateWithLifecycle()

    var followTarget by remember { mutableStateOf<com.intentbrowser.app.util.NearbyDevice?>(null) }
    var followPinInput by remember { mutableStateOf("") }
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
            // Single active WebView: background tabs are parked — their state is
            // saved on dispose and restored on switch. Composing a WebView per
            // tab kept every renderer alive and caused OOM crashes.
            key(activeTab.id) {
                BrowserTabPanel(
                    viewModel = viewModel,
                    tab = activeTab,
                    modifier = Modifier.fillMaxSize(),
                    onOpenTabSwitcher = onOpenTabSwitcher,
                    onNavigate = onNavigate,
                    onShowSyncDialog = { showSyncDialog = true },
                    onShowMeshDialog = { showMeshDialog = true }
                )
            }
        }
        
        ActiveDownloadsOverlay(viewModel = viewModel)

        if (following != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Following " + (following?.deviceName ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { viewModel.stopFollowing() }) {
                    Text("Stop")
                }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Connect your devices on the same Wi-Fi to:", style = MaterialTheme.typography.bodyMedium)
                    Text(syncServerIp ?: "Starting...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (syncPin != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pairing PIN:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            syncPin ?: "",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    // Lead a follow session
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lead a session", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Paired phones auto-open every page you visit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isLeading,
                            onCheckedChange = { viewModel.setLeading(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nearby devices",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Phones on this Wi-Fi running Intent Browser",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (followError != null) {
                        Text(
                            followError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (nearbyDevices.isEmpty()) {
                        Text(
                            "Searching your Wi-Fi for other Intent Browsers…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        nearbyDevices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    device.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = {
                                    followPinInput = ""
                                    followTarget = device
                                }) {
                                    Text("Follow")
                                }
                                TextButton(onClick = {
                                    viewModel.createNewTab(device.url)
                                    showSyncDialog = false
                                }) {
                                    Text("Open hub")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Manual backup: QR + IP, for when Nearby can't see this phone.
                    Text(
                        "Or connect manually",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Scan with another phone's camera, or type the address — works even when Nearby can't see this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (syncServerIp?.startsWith("http") == true) {
                        var hubQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(syncServerIp) {
                            val ip = syncServerIp
                            if (ip != null) {
                                withContext(Dispatchers.Default) {
                                    hubQrBitmap = QrCodeGenerator.generateQrCode(ip, 400)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            hubQrBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "QR code of this phone's hub address",
                                    modifier = Modifier.size(160.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
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

    // PIN entry for following a nearby device.
    followTarget?.let { target ->
        AlertDialog(
            onDismissRequest = {
                followTarget = null
                followPinInput = ""
            },
            title = { Text("Follow " + target.name) },
            text = {
                Column {
                    Text(
                        "Ask them for the 4-digit PIN shown on their screen.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = followPinInput,
                        onValueChange = {
                            followPinInput = it.filter { c -> c.isDigit() }.take(4)
                        },
                        label = { Text("PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.followDevice(target, followPinInput)
                        followTarget = null
                        followPinInput = ""
                    },
                    enabled = followPinInput.length == 4
                ) {
                    Text("Follow")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    followTarget = null
                    followPinInput = ""
                }) {
                    Text("Cancel")
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
                        var joinIp by remember { mutableStateOf("") }
                        
                        Button(onClick = { viewModel.startMeshServer() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Mesh Host")
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Or Join Existing Mesh:", style = MaterialTheme.typography.bodyMedium)
                        androidx.compose.material3.OutlinedTextField(
                            value = joinIp,
                            onValueChange = { joinIp = it },
                            placeholder = { Text("e.g. 192.168.1.5:8081") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                showMeshDialog = false
                                val url = if (!joinIp.startsWith("http")) "http://$joinIp" else joinIp
                                viewModel.createNewTab(url)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = joinIp.isNotBlank()
                        ) {
                            Text("Join Mesh")
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
    var urlInput by remember(tab.id) { mutableStateOf(tab.url) }
    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val panelScope = rememberCoroutineScope()
    val dropFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (viewModel.dropFile(context, uri)) {
                Toast.makeText(context, "Dropping file… watch the hub page on your PC.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Start Local Hub and pair a device first", Toast.LENGTH_LONG).show()
            }
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isAdBlockerEnabled by viewModel.isAdBlockerEnabled.collectAsStateWithLifecycle()
    val blockThirdPartyCookies by viewModel.blockThirdPartyCookies.collectAsStateWithLifecycle()

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

    // Drop: decrypted payload from the paired device (E2EE — server saw ciphertext only).
    val dropReceived by viewModel.dropReceived.collectAsStateWithLifecycle()
    val dropFileReceived by viewModel.dropFileReceived.collectAsStateWithLifecycle()
    if (dropReceived != null) {
        val isUrl = (dropReceived ?: "").startsWith("http")
        AlertDialog(
            onDismissRequest = { viewModel.clearDropReceived() },
            title = { Text("Drop received") },
            text = {
                Text(
                    dropReceived ?: "",
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            },
            confirmButton = {
                if (isUrl) {
                    TextButton(onClick = {
                        viewModel.createNewTab(dropReceived ?: "")
                        viewModel.clearDropReceived()
                    }) { Text("Open") }
                } else {
                    TextButton(onClick = {
                        viewModel.updateClipboard(dropReceived ?: "")
                        viewModel.clearDropReceived()
                    }) { Text("Copy") }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDropReceived() }) { Text("Dismiss") }
            }
        )
    }

    // Drop file received: fully reassembled + saved to Downloads (E2EE — server saw ciphertext only).
    dropFileReceived?.let { file ->
        val sizeLabel = if (file.size >= 1024 * 1024) {
            "%.1f MB".format(file.size / (1024.0 * 1024.0))
        } else {
            "%d KB".format((file.size + 1023) / 1024)
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearDropFileReceived() },
            title = { Text("Drop received") },
            text = {
                Text(file.name + "\n" + sizeLabel + " — saved to Downloads")
            },
            confirmButton = {
                TextButton(onClick = {
                    val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(android.net.Uri.parse(file.contentUri), file.mime)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(
                            android.content.Intent.createChooser(openIntent, "Open file")
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearDropFileReceived()
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearDropFileReceived() }) { Text("Dismiss") }
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

    val isUrlFocused = remember { mutableStateOf(false) }
    val history by viewModel.history.collectAsStateWithLifecycle()
    // Sync the address bar when the tab navigates — but never wipe what the user is typing.
    LaunchedEffect(tab.url) {
        if (!isUrlFocused.value) urlInput = tab.url
    }
    // Debounced suggestions query: no per-keystroke filtering of the history list.
    var suggestionQuery by remember { mutableStateOf("") }
    LaunchedEffect(urlInput) {
        delay(250)
        suggestionQuery = urlInput
    }
    // Renderer-crash recovery: bumping the nonce recreates the WebView.
    var crashNonce by remember { mutableStateOf(0) }
    var longPressResult by remember { mutableStateOf<WebView.HitTestResult?>(null) }
    var showFindBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

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
                        IconButton(onClick = { viewModel.loadUrl(tab.id, selectedSearchEngine.homeUrl) }) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }
                        
                        // Compact Address Bar
                        val containerColor = if (tab.isIncognito) Color(0xFF333333) else MaterialTheme.colorScheme.surfaceVariant
                        val contentColor = if (tab.isIncognito) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = containerColor,
                            contentColor = contentColor
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                if (tab.isIncognito) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Incognito",
                                        modifier = Modifier.size(24.dp).padding(end = 4.dp),
                                        tint = contentColor
                                    )
                                } else {
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
                            }
                            Box(modifier = Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                                val displayValue = if (isUrlFocused.value) {
                                    urlInput.let { if (it == "app://newtab") "" else it }
                                } else {
                                    if (tab.url == "app://newtab") "" else tab.url.replace(Regex("^https?://"), "").removeSuffix("/")
                                }
                                
                                BasicTextField(
                                    value = displayValue,
                                    onValueChange = { urlInput = it },
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { isUrlFocused.value = it.isFocused },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
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
                                            // Actually dismiss the keyboard and release focus.
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            isUrlFocused.value = false
                                        }
                                    ),
                                    decorationBox = { innerTextField ->
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (displayValue.isEmpty()) {
                                                    Text("Search or type URL", style = MaterialTheme.typography.bodyLarge, color = contentColor.copy(alpha = 0.5f))
                                                }
                                                innerTextField()
                                            }
                                            if (isUrlFocused.value && urlInput.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { urlInput = "" },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Clear", tint = contentColor.copy(alpha = 0.7f))
                                                }
                                            }
                                        }
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
                            ) {DropdownMenuItem(
                                    text = { Text("New Tab") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Add,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.createNewTab()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        activeWebView?.reload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Find in page") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Search,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        showFindBar = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reader Mode") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Article,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        val readerJs = """
                                            (function(){
                                              const style = document.createElement('style');
                                              style.innerHTML = `
                                                body { background: #fdfdfd !important; color: #111 !important; font-family: serif; font-size: 20px; line-height: 1.6; max-width: 700px; margin: 0 auto; padding: 20px; }
                                                img { max-width: 100%; height: auto; }
                                                nav, header, footer, aside, .ad, .advertisement, iframe { display: none !important; }
                                                * { background-color: transparent !important; color: inherit !important; font-family: inherit !important; }
                                              `;
                                              document.head.appendChild(style);
                                            })();
                                        """.trimIndent()
                                        activeWebView?.evaluateJavascript(readerJs, null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (tab.isDesktopMode) "Mobile Site" else "Desktop Site") },
                                    leadingIcon = { Icon(
                                        Icons.Default.DesktopWindows,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.toggleDesktopMode(tab.id)
                                        activeWebView?.reload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Page") },
                                    leadingIcon = { Icon(
                                        if (isBookmarked) Icons.Default.Bookmark
                                        else Icons.Default.BookmarkBorder,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.toggleBookmark(tab.url, activeWebView?.title ?: tab.title)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save to Scratchpad") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        activeWebView?.evaluateJavascript("(function(){ return window.getSelection().toString(); })();") { selected ->
                                            val text = if (selected != null && selected != "\"\"" && selected != "null") selected.trim('"') else tab.url
                                            viewModel.addNote(text)
                                            android.widget.Toast.makeText(context, "Saved to Scratchpad", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Save Offline") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Download,
                                        contentDescription = null
                                    ) },
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
                                    text = { Text("Screenshot") },
                                    leadingIcon = { Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        val wv = activeWebView
                                        if (wv == null) {
                                            Toast.makeText(context, "Nothing to capture", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val bmp = com.intentbrowser.app.util.ScreenshotHelper.capture(wv)
                                            if (bmp == null) {
                                                Toast.makeText(context, "Screenshot failed", Toast.LENGTH_SHORT).show()
                                            } else {
                                                panelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    val ok = com.intentbrowser.app.util.ScreenshotHelper.saveToPictures(context, bmp)
                                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                        Toast.makeText(
                                                            context,
                                                            if (ok) "Screenshot saved to Pictures" else "Screenshot failed",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Drop this tab") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Send,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        if (viewModel.dropCurrentTab()) {
                                            Toast.makeText(context, "Dropped! Open the Local Hub page on your PC.", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Start Local Hub and pair a device first", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Drop file") },
                                    leadingIcon = { Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        dropFilePicker.launch("*/*")
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (isSplitScreen) "Exit Split Screen" else "Split Screen") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Splitscreen,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.toggleSplitScreen()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Local Hub") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Wifi,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        onShowSyncDialog()
                                        viewModel.startSyncServer(context.applicationContext)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Mesh Chat / Secure Connect") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Chat,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        onShowMeshDialog()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    leadingIcon = { Icon(
                                        Icons.Default.History,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("history")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bookmarks") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Star,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("bookmarks")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Offline Pages") },
                                    leadingIcon = { Icon(
                                        Icons.Default.OfflinePin,
                                        contentDescription = null
                                    ) },
                                    onClick = {
                                        showMoreMenu = false
                                        onNavigate("offline")
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(
                                        Icons.Default.Settings,
                                        contentDescription = null
                                    ) },
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
        // Pull-to-refresh: drag down from the top of a settled page to reload.
        var pullOffset by remember { mutableStateOf(0f) }
        var ptrRefreshing by remember { mutableStateOf(false) }
        LaunchedEffect(tab.isLoading) { if (!tab.isLoading) ptrRefreshing = false }
        val ptrConnection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val wv = activeWebView
                val canPull = tab.url.startsWith("http") && (wv?.scrollY ?: 1) == 0 &&
                    !ptrRefreshing && !tab.isLoading
                if (available.y > 8 && canPull) {
                    pullOffset = (pullOffset + available.y * 0.4f).coerceAtMost(160f)
                    if (pullOffset >= 120f) {
                        pullOffset = 0f
                        ptrRefreshing = true
                        wv?.reload()
                    }
                    return available
                }
                return Offset.Zero
            }
        }

        val webContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(ptrConnection)
            ) {
                if (tab.url == "app://newtab") {
                    NewTabDashboard(viewModel, bookmarks, tab.id)
                } else if (tab.url.startsWith("app://offline/")) {
                    val pageId = tab.url.removePrefix("app://offline/").toIntOrNull()
                    val allPages by viewModel.allPages.collectAsStateWithLifecycle()
                    val page = allPages.find { it.id == pageId }

                    if (page != null) {
                        // Dedicated viewer: its own WebView with file access scoped to
                        // our files, destroyed on dispose. (Was: pooled view with stale
                        // clients that leaked and couldn't load file:// URLs.)
                        OfflinePageViewer(filePath = page.filePath)
                    } else {
                        Text("Offline page not found", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    // crashNonce recreates the WebView after a renderer crash.
                    key(crashNonce) {
                        HardenedWebView(
                            url = tab.url,
                            tabId = tab.id,
                            webViewState = tab.webViewState,
                            isAdBlockerEnabled = isAdBlockerEnabled,
                            blockThirdPartyCookies = blockThirdPartyCookies,
                            isIncognito = tab.isIncognito,
                            isDesktopMode = tab.isDesktopMode,
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
                            onDownloadStarted = { fileName, url, mime, userAgent, cookies ->
                                viewModel.startDownload(fileName, url, mime, userAgent, cookies)
                            },
                            onBlobDownload = { dataUrl ->
                                viewModel.startBlobDownload(dataUrl)
                            },
                            onRendererCrashed = {
                                crashNonce++
                                Toast.makeText(context, "Tab crashed — reloading", Toast.LENGTH_SHORT).show()
                            },
                            onLongPress = { result ->
                                longPressResult = result
                            },
                            onFaviconReceived = { pageUrl, icon ->
                                viewModel.saveFavicon(pageUrl, icon)
                            },
                            geoDecision = { origin -> viewModel.getGeoDecision(origin) },
                            onSaveGeoDecision = { origin, allow ->
                                viewModel.saveGeoDecision(origin, allow)
                            },
                            onPageFinishedListener = { finishedUrl, view ->
                                val prompt = viewModel.pendingAiPrompt.value
                                if (prompt != null && finishedUrl.contains("gemini.google.com")) {
                                    // Escaped properly: notes with backticks/${} used to break this.
                                    val safePrompt = JSONObject.quote(prompt)
                                    view.evaluateJavascript("""
                                        setTimeout(() => {
                                            let editor = document.querySelector('rich-textarea') || document.querySelector('textarea');
                                            if (editor) {
                                                const t = $safePrompt;
                                                if ('value' in editor) editor.value = t;
                                                else editor.textContent = t;
                                            }
                                        }, 2000);
                                    """.trimIndent(), null)
                                    viewModel.clearPendingAiPrompt()
                                }
                            }
                        )
                    }
                }

                // Pull-to-refresh indicator
                if (pullOffset > 4f || ptrRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(28.dp),
                        strokeWidth = 3.dp
                    )
                }

                // Find-in-page bar
                if (showFindBar) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().zIndex(9f),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = findQuery,
                                onValueChange = {
                                    findQuery = it
                                    if (it.isNotEmpty()) activeWebView?.findAllAsync(it)
                                    else activeWebView?.clearMatches()
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { activeWebView?.findNext(true) }
                                ),
                                decorationBox = { inner ->
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        if (findQuery.isEmpty()) {
                                            Text(
                                                "Find in page",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            IconButton(onClick = { activeWebView?.findNext(true) }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Next match")
                            }
                            IconButton(onClick = { activeWebView?.findNext(false) }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Previous match")
                            }
                            IconButton(onClick = {
                                showFindBar = false
                                findQuery = ""
                                activeWebView?.clearMatches()
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close find")
                            }
                        }
                    }
                }

                // AwesomeBar Suggestions Overlay
                AnimatedVisibility(
                    visible = isUrlFocused.value && suggestionQuery.isNotBlank() && suggestionQuery != tab.url && suggestionQuery != "app://newtab",
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)).zIndex(10f)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        val suggestions = history.filter { it.url.contains(suggestionQuery, ignoreCase = true) || it.title.contains(suggestionQuery, ignoreCase = true) }.take(5)
                        if (suggestions.isNotEmpty()) {
                            items(suggestions.size) { index ->
                                val suggestion = suggestions[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadUrl(tab.id, suggestion.url)
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
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
                                            val q = suggestionQuery
                                            val formattedUrl = if (q.contains(".") && !q.contains(" ")) "https://$q"
                                            else String.format(selectedSearchEngine.searchUrl, q.replace(" ", "+"))
                                            viewModel.loadUrl(tab.id, formattedUrl)
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            isUrlFocused.value = false
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Search for \"$suggestionQuery\"", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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

    // Long-press context menu (links & images) — was missing entirely.
    longPressResult?.let { result ->
        val linkUrl = result.extra
        val isImage = result.type == WebView.HitTestResult.IMAGE_TYPE ||
            result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val isAnchor = result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
            result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        AlertDialog(
            onDismissRequest = { longPressResult = null },
            title = { Text(if (isImage) "Image" else "Link") },
            text = {
                if (!linkUrl.isNullOrBlank()) {
                    Text(
                        linkUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                Column {
                    if (isAnchor && !linkUrl.isNullOrBlank()) {
                        TextButton(onClick = {
                            viewModel.createNewTab(linkUrl)
                            longPressResult = null
                        }) { Text("Open in new tab") }
                        TextButton(onClick = {
                            viewModel.openInSecondaryPane(linkUrl)
                            longPressResult = null
                        }) { Text("Open in other pane") }
                        TextButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", linkUrl))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                            longPressResult = null
                        }) { Text("Copy link") }
                    }
                    if (isImage && !linkUrl.isNullOrBlank()) {
                        TextButton(onClick = {
                            viewModel.startDownload(
                                URLUtil.guessFileName(linkUrl, null, null),
                                linkUrl, null, null, null
                            )
                            longPressResult = null
                        }) { Text("Save image") }
                    }
                }
            }
        )
    }
}

/**
 * Dedicated offline-page viewer. Own WebView with file access enabled (the main
 * engine keeps it off), destroyed on dispose.
 */
@Composable
fun OfflinePageViewer(filePath: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                WebViewSettingsManager.applySettings(this)
                settings.allowFileAccess = true
                webViewClient = WebViewClient()
                loadUrl("file://$filePath")
            }
        },
        onRelease = { it.destroy() }
    )
}

@Composable
fun TabSwitcherScreen(
    tabs: List<TabState>,
    activeTabId: String,
    onCloseTab: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
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
                        onClick = onNewIncognitoTab,
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "New Incognito Tab", modifier = Modifier.size(24.dp))
                    }
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
fun NewTabDashboard(viewModel: MainViewModel, bookmarks: List<com.intentbrowser.app.data.Bookmark>, tabId: String) {
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

@Composable
fun ActiveDownloadsOverlay(viewModel: MainViewModel) {
    val downloads by viewModel.downloadManager.downloads.collectAsStateWithLifecycle()
    val activeDownloads = downloads.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = activeDownloads.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Downloading ${activeDownloads.size} file(s)", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { /* Could expand or hide */ }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    activeDownloads.take(2).forEach { dl ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(dl.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("${dl.progress}%", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (dl.state == DownloadState.DOWNLOADING) {
                                IconButton(onClick = { viewModel.downloadManager.pause(dl.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(16.dp))
                                }
                            } else {
                                IconButton(onClick = { viewModel.downloadManager.resume(dl.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { viewModel.downloadManager.cancel(dl.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                        LinearProgressIndicator(progress = { dl.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
