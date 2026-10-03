package com.intentbrowser.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcel
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.intentbrowser.app.data.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class TabState(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "app://newtab",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val webViewState: Bundle? = null,
    /** Marshalled [webViewState], persisted to Room so tabs survive process death. */
    val webViewStateBlob: ByteArray? = null,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false
)

enum class SearchEngine(val displayName: String, val searchUrl: String, val homeUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s", "https://www.google.com/"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/"),
    BING("Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com/"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s", "https://search.brave.com/"),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p=%s", "https://search.yahoo.com/"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s", "https://www.ecosia.org/"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s", "https://www.startpage.com/")
}

class MainViewModel(private val repository: AppRepository, val downloadManager: com.intentbrowser.app.util.CustomDownloadManager) : ViewModel() {
    private val _tabs = MutableStateFlow<List<TabState>>(listOf(TabState()))
    val tabs: StateFlow<List<TabState>> = _tabs.asStateFlow()

    private val _selectedSearchEngine = MutableStateFlow(
        try {
            SearchEngine.valueOf(repository.getSearchEngine())
        } catch (e: Exception) {
            SearchEngine.GOOGLE
        }
    )
    val selectedSearchEngine: StateFlow<SearchEngine> = _selectedSearchEngine.asStateFlow()
    
    fun setSearchEngine(engine: SearchEngine) {
        _selectedSearchEngine.value = engine
        repository.saveSearchEngine(engine.name)
    }

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _isSplitScreen = MutableStateFlow(false)
    val isSplitScreen: StateFlow<Boolean> = _isSplitScreen.asStateFlow()

    private val _secondaryTabId = MutableStateFlow<String?>(null)
    val secondaryTabId: StateFlow<String?> = _secondaryTabId.asStateFlow()

    private var isFirstLoad = true
    init {
        viewModelScope.launch {
            repository.failStuckDownloads()
            repository.tabs.collect { savedTabs ->
                if (isFirstLoad && savedTabs.isNotEmpty()) {
                    isFirstLoad = false
                    val restoredTabs = savedTabs.sortedBy { it.orderIndex }.map {
                        TabState(
                            id = it.id,
                            url = it.url,
                            title = it.title,
                            // Full state restore (back/forward history, scroll, forms).
                            // Falls back to cold-loading the URL if unmarshalling fails.
                            webViewState = it.stateBlob?.unmarshallBundle()
                        )
                    }
                    _tabs.value = restoredTabs
                    if (restoredTabs.none { it.id == _activeTabId.value }) {
                        _activeTabId.value = restoredTabs.first().id
                    }
                }
            }
        }
    }

    private fun persistTabs() {
        viewModelScope.launch {
            val entities = _tabs.value.filter { !it.isIncognito }.mapIndexed { index, tabState ->
                com.intentbrowser.app.data.TabEntity(
                    id = tabState.id,
                    url = tabState.url,
                    title = tabState.title,
                    orderIndex = index,
                    stateBlob = tabState.webViewStateBlob
                )
            }
            repository.saveTabs(entities)
        }
        updateSyncServerTabs()
    }

    val bookmarks = repository.bookmarks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPages = repository.allPages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isAdBlockerEnabled = MutableStateFlow(repository.isAdBlockerEnabled())
    val isAdBlockerEnabled: StateFlow<Boolean> = _isAdBlockerEnabled.asStateFlow()

    private val _blockThirdPartyCookies = MutableStateFlow(repository.isThirdPartyCookiesBlocked())
    val blockThirdPartyCookies: StateFlow<Boolean> = _blockThirdPartyCookies.asStateFlow()

    fun toggleAdBlocker(enabled: Boolean) {
        _isAdBlockerEnabled.value = enabled
        repository.saveAdBlockerEnabled(enabled)
    }

    fun toggleThirdPartyCookies(blocked: Boolean) {
        _blockThirdPartyCookies.value = blocked
        repository.saveThirdPartyCookiesBlocked(blocked)
    }

    private val _sharedClipboard = MutableStateFlow("")
    val sharedClipboard: StateFlow<String> = _sharedClipboard.asStateFlow()

    
    private val _receivedMessage = MutableStateFlow<String?>(null)
    val receivedMessage: StateFlow<String?> = _receivedMessage.asStateFlow()
    fun clearReceivedMessage() { _receivedMessage.value = null }

    // ---- Drop: E2EE tab/text dropping over the PIN-gated Local Hub ----
    private val _dropReceived = MutableStateFlow<String?>(null)
    val dropReceived: StateFlow<String?> = _dropReceived.asStateFlow()
    fun clearDropReceived() { _dropReceived.value = null }

    // --- Drop files (chunked E2EE, same session key as text drops) ---
    data class ReceivedFile(
        val name: String,
        val mime: String,
        val size: Long,
        val contentUri: String
    )

    private val _dropFileReceived = MutableStateFlow<ReceivedFile?>(null)
    val dropFileReceived: StateFlow<ReceivedFile?> = _dropFileReceived.asStateFlow()
    fun clearDropFileReceived() { _dropFileReceived.value = null }

    private data class DropFileAssembly(
        val name: String,
        val mime: String,
        val size: Long,
        val total: Int,
        val chunks: MutableMap<Int, ByteArray>
    )

    private val dropFileParts = mutableMapOf<String, DropFileAssembly>()
    private var hubAppContext: android.content.Context? = null

    /**
     * Drop the current tab to the paired PC. Returns false when there is no
     * paired device with a completed ECDH exchange (server off / not paired).
     */
    fun dropCurrentTab(): Boolean {
        val tab = _tabs.value.find { it.id == _activeTabId.value } ?: return false
        val url = tab.url
        if (!url.startsWith("http")) return false
        val envelope = JSONObject().apply {
            put("kind", "text")
            put("text", url)
        }.toString()
        val payload = com.intentbrowser.app.util.ClipboardServer.dropEncrypt(envelope) ?: return false
        com.intentbrowser.app.util.ClipboardServer.dropToClient(payload)
        return true
    }

    /**
     * Drop a file to the paired PC: read -> 256KB chunks -> each chunk as an
     * encrypted JSON envelope -> queued for the PC's long-poll. Returns false
     * when there is no paired device with a completed ECDH exchange.
     */
    fun dropFile(context: android.content.Context, uri: Uri): Boolean {
        if (com.intentbrowser.app.util.ClipboardServer.dropEncrypt("ping") == null) return false
        val cr = context.contentResolver
        val name = queryDisplayName(cr, uri) ?: "dropped-file"
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val bytes = try {
            cr.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } ?: return false
        if (bytes.isEmpty()) return false
        val tid = UUID.randomUUID().toString()
        val chunkSize = 256 * 1024
        val n = (bytes.size + chunkSize - 1) / chunkSize
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 0 until n) {
                val chunk = bytes.copyOfRange(i * chunkSize, minOf(bytes.size, (i + 1) * chunkSize))
                val envelope = JSONObject().apply {
                    put("kind", "file")
                    put("tid", tid)
                    put("name", name)
                    put("mime", mime)
                    put("size", bytes.size)
                    put("i", i)
                    put("n", n)
                    put("data", Base64.encodeToString(chunk, Base64.NO_WRAP))
                }.toString()
                val payload = com.intentbrowser.app.util.ClipboardServer.dropEncrypt(envelope)
                    ?: return@launch
                com.intentbrowser.app.util.ClipboardServer.dropToClient(payload)
            }
        }
        return true
    }

    private fun queryDisplayName(
        cr: android.content.ContentResolver,
        uri: Uri
    ): String? = try {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Route a decrypted drop plaintext: text envelope, file chunk, or legacy raw text. */
    private fun handleDropPlaintext(plain: String) {
        val json = try {
            JSONObject(plain)
        } catch (_: Exception) {
            null
        }
        if (json == null || json.optString("kind").isBlank()) {
            _dropReceived.value = plain // legacy raw text
            return
        }
        when (json.optString("kind")) {
            "text" -> _dropReceived.value = json.optString("text")
            "file" -> handleDropFileChunk(json)
        }
    }

    private fun handleDropFileChunk(json: JSONObject) {
        val tid = json.optString("tid")
        if (tid.isBlank()) return
        val asm = dropFileParts.getOrPut(tid) {
            DropFileAssembly(
                name = json.optString("name").ifBlank { "dropped-file" },
                mime = json.optString("mime").ifBlank { "application/octet-stream" },
                size = json.optLong("size", 0),
                total = json.optInt("n", 0),
                chunks = mutableMapOf()
            )
        }
        if (asm.total <= 0) return
        val i = json.optInt("i", -1)
        val data = json.optString("data")
        if (i in 0 until asm.total && data.isNotBlank() && !asm.chunks.containsKey(i)) {
            try {
                asm.chunks[i] = Base64.decode(data, Base64.DEFAULT)
            } catch (_: Exception) {
                return
            }
        }
        if (asm.chunks.size >= asm.total) {
            dropFileParts.remove(tid)
            if (asm.size <= 0 || asm.size > 500L * 1024 * 1024) return // sanity cap: 500MB
            val out = ByteArray(asm.size.toInt())
            var pos = 0
            for (k in 0 until asm.total) {
                val c = asm.chunks[k] ?: return
                val take = minOf(c.size, out.size - pos)
                if (take <= 0) return
                c.copyInto(out, pos, 0, take)
                pos += take
            }
            val uri = saveDropFileToDownloads(asm.name, asm.mime, out)
            if (uri != null) {
                _dropFileReceived.value = ReceivedFile(asm.name, asm.mime, out.size.toLong(), uri)
            }
        }
    }

    /** Save a fully-reassembled dropped file into Downloads (MediaStore). */
    private fun saveDropFileToDownloads(name: String, mime: String, bytes: ByteArray): String? {
        val ctx = hubAppContext ?: return null
        return try {
            val resolver = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            uri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private var meshChatServer: com.intentbrowser.app.util.MeshChatServer? = null
    
    private val _meshServerIp = MutableStateFlow<String?>(null)
    val meshServerIp: StateFlow<String?> = _meshServerIp.asStateFlow()
    
    private val _meshPin = MutableStateFlow<String?>(null)
    val meshPin: StateFlow<String?> = _meshPin.asStateFlow()
    
    private val _meshMessages = MutableStateFlow<List<com.intentbrowser.app.util.MeshMessage>>(emptyList())
    val meshMessages: StateFlow<List<com.intentbrowser.app.util.MeshMessage>> = _meshMessages.asStateFlow()

    fun startMeshServer() {
        val ip = com.intentbrowser.app.util.NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            val fullIp = "http://$ip:8081"
            _meshServerIp.value = fullIp
            meshChatServer = com.intentbrowser.app.util.MeshChatServer(port = 8081, serverIp = fullIp).apply {
                start()
                _meshPin.value = getPin()
                
                viewModelScope.launch {
                    messages.collect { msgs ->
                        _meshMessages.value = msgs
                    }
                }
            }
        } else {
            _meshServerIp.value = "Connect to Wi-Fi first"
            _meshPin.value = null
        }
    }
    
    fun stopMeshServer() {
        meshChatServer?.stop()
        meshChatServer = null
        _meshServerIp.value = null
        _meshPin.value = null
    }
    
    fun sendMeshMessage(payload: String, sender: String = "Host") {
        meshChatServer?.sendLocalMessage(payload, sender)
    }

    private val _syncServerIp = MutableStateFlow<String?>(null)
    val syncServerIp: StateFlow<String?> = _syncServerIp.asStateFlow()

    private val _syncPin = MutableStateFlow<String?>(null)
    val syncPin: StateFlow<String?> = _syncPin.asStateFlow()

    private val _nearbyDevices =
        MutableStateFlow<List<com.intentbrowser.app.util.NearbyDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<com.intentbrowser.app.util.NearbyDevice>> =
        _nearbyDevices.asStateFlow()
    private var nearbyCollectJob: Job? = null

    // --- Follow mode (co-browsing) ---
    /** Which device we are currently following (null = not following). */
    data class FollowInfo(val deviceName: String, val host: String, val port: Int)

    private val _isLeading = MutableStateFlow(false)
    val isLeading: StateFlow<Boolean> = _isLeading.asStateFlow()

    private val _following = MutableStateFlow<FollowInfo?>(null)
    val following: StateFlow<FollowInfo?> = _following.asStateFlow()

    private val _followError = MutableStateFlow<String?>(null)
    val followError: StateFlow<String?> = _followError.asStateFlow()

    private var followJob: Job? = null

    fun startSyncServer(context: android.content.Context) {
        hubAppContext = context.applicationContext
        val ip = com.intentbrowser.app.util.NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            _syncServerIp.value = "http://$ip:8080"
            com.intentbrowser.app.util.ClipboardServer.currentClipboard = _sharedClipboard.value
            com.intentbrowser.app.util.ClipboardServer.onClipboardChanged = { text ->
                _sharedClipboard.value = text
            }
            com.intentbrowser.app.util.ClipboardServer.onTabAction = { action, payload ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    when (action) {
                        "close" -> closeTab(payload)
                        "open" -> createNewTab(payload)
                    }
                }
            }
            com.intentbrowser.app.util.ClipboardServer.onMessageReceived = { msg ->
                _receivedMessage.value = msg
            }
            updateSyncServerTabs()
            com.intentbrowser.app.util.ClipboardServer.start()
            _syncPin.value = com.intentbrowser.app.util.ClipboardServer.getPin()
            com.intentbrowser.app.util.ClipboardServer.onDropReceived = { payload ->
                val plain = com.intentbrowser.app.util.ClipboardServer.decryptDrop(payload)
                if (plain != null) {
                    handleDropPlaintext(plain)
                }
            }
            // Nearby discovery: advertise this hub + find others on the LAN.
            com.intentbrowser.app.util.NearbyDiscovery.start(context, 8080)
            nearbyCollectJob?.cancel()
            nearbyCollectJob = viewModelScope.launch {
                com.intentbrowser.app.util.NearbyDiscovery.devices.collect {
                    _nearbyDevices.value = it
                }
            }
        } else {
            _syncServerIp.value = "Connect to Wi-Fi first"
        }
    }
    
    private fun updateSyncServerTabs() {
        val json = _tabs.value.joinToString(prefix = "[", postfix = "]") {
            "{\"id\":\"${it.id}\",\"url\":\"${it.url}\",\"title\":\"${it.title.replace("\"", "\\\"")}\"}"
        }
        com.intentbrowser.app.util.ClipboardServer.currentTabsJson = json
    }


    fun stopSyncServer() {
        com.intentbrowser.app.util.ClipboardServer.onDropReceived = null
        com.intentbrowser.app.util.ClipboardServer.stop()
        nearbyCollectJob?.cancel()
        nearbyCollectJob = null
        com.intentbrowser.app.util.NearbyDiscovery.stop()
        _syncServerIp.value = null
        _syncPin.value = null
        _nearbyDevices.value = emptyList()
        _isLeading.value = false
        stopFollowing()
    }

    fun toggleSplitScreen() {
        if (_isSplitScreen.value) {
            _isSplitScreen.value = false
            _secondaryTabId.value = null
        } else {
            _isSplitScreen.value = true
            if (_tabs.value.size == 1) {
                val newTab = TabState(url = "app://newtab")
                _tabs.value = _tabs.value + newTab
                _secondaryTabId.value = newTab.id
                persistTabs()
            } else {
                _secondaryTabId.value = _tabs.value.firstOrNull { it.id != _activeTabId.value }?.id
            }
        }
    }

    fun updateClipboard(text: String) {
        _sharedClipboard.value = text
        com.intentbrowser.app.util.ClipboardServer.currentClipboard = text
    }

    /**
     * Open a URL in the other split-screen pane. If split-screen is off, it is
     * turned on with a fresh tab; if it is on, the secondary tab navigates.
     */
    fun openInSecondaryPane(url: String) {
        val secondaryId = _secondaryTabId.value
        if (_isSplitScreen.value && secondaryId != null &&
            _tabs.value.any { it.id == secondaryId }
        ) {
            loadUrl(secondaryId, url)
        } else {
            val newTab = TabState(url = url)
            _tabs.value = _tabs.value + newTab
            _secondaryTabId.value = newTab.id
            _isSplitScreen.value = true
            persistTabs()
        }
    }

    fun createNewTab(url: String = "app://newtab", isIncognito: Boolean = false) {
        val newTab = TabState(url = url, isIncognito = isIncognito)
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        persistTabs()
    }

    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val tabToClose = currentTabs.find { it.id == tabId }
        val isClosingIncognito = tabToClose?.isIncognito == true

        if (currentTabs.size <= 1) {
            _tabs.value = listOf(TabState())
            _activeTabId.value = _tabs.value.first().id
            _isSplitScreen.value = false
            _secondaryTabId.value = null
            persistTabs()
        } else {
            val index = currentTabs.indexOfFirst { it.id == tabId }
            val newTabs = currentTabs.filter { it.id != tabId }
            _tabs.value = newTabs
            
            if (_activeTabId.value == tabId) {
                val newIndex = if (index >= newTabs.size) newTabs.size - 1 else index
                _activeTabId.value = newTabs[newIndex].id
            }
            if (_secondaryTabId.value == tabId) {
                _secondaryTabId.value = newTabs.firstOrNull { it.id != _activeTabId.value }?.id
                if (_secondaryTabId.value == null) {
                    _isSplitScreen.value = false
                }
            }
            persistTabs()
        }

        if (isClosingIncognito && _tabs.value.none { it.isIncognito }) {
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                    val profileStore = androidx.webkit.ProfileStore.getInstance()
                    profileStore.deleteProfile("incognito")
                }
                // NOTE: no destructive fallback. Without multi-profile support the
                // incognito WebView shares storage with the main profile, and the old
                // fallback (WebStorage.deleteAllData + removeAllCookies) wiped the
                // user's REAL logins and site data. Never do that.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun switchTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun toggleDesktopMode(tabId: String) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(isDesktopMode = !it.isDesktopMode) else it
        }
    }

    fun loadUrl(tabId: String, url: String) {
        _tabs.value = _tabs.value.map { 
            if (it.id == tabId) it.copy(url = url, isLoading = true) else it 
        }
        persistTabs()
    }

    /**
     * Open a URL sent by another app (ACTION_VIEW). Reuses the current tab if
     * it's still a fresh new-tab page; otherwise opens a new tab.
     */
    fun openExternalUrl(url: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        val current = _tabs.value.find { it.id == _activeTabId.value }
        if (current != null && current.url == "app://newtab") {
            loadUrl(current.id, clean)
        } else {
            createNewTab(clean)
        }
    }

    /** Leader side: start/stop broadcasting our navigations to paired followers. */
    fun setLeading(leading: Boolean) {
        _isLeading.value = leading
    }

    /**
     * Follower side: pair with the leader's hub via its PIN, then long-poll
     * /api/follow/poll and load every navigation the leader commits.
     */
    fun followDevice(device: com.intentbrowser.app.util.NearbyDevice, pin: String) {
        followJob?.cancel()
        _followError.value = null
        followJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = pairWithLeader(device, pin)
                if (token == null) {
                    _followError.value = "Wrong PIN — ask for the 4-digit code on their screen"
                    return@launch
                }
                _following.value = FollowInfo(device.name, device.host, device.port)
                var since = 0
                while (isActive) {
                    try {
                        val (url, seq) = pollFollow(device, token, since)
                        since = seq
                        if (url != null) {
                            val current = _tabs.value.find { it.id == _activeTabId.value }?.url
                            if (url != current) {
                                withContext(Dispatchers.Main) {
                                    loadUrl(_activeTabId.value, url)
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Leader unreachable or hub stopped — retry quietly.
                        delay(3000)
                    }
                }
            } catch (_: CancellationException) {
                // stopFollowing() — normal.
            }
        }
    }

    fun stopFollowing() {
        followJob?.cancel()
        followJob = null
        _following.value = null
    }

    private fun pairWithLeader(
        device: com.intentbrowser.app.util.NearbyDevice,
        pin: String
    ): String? {
        val conn = URL("http://${device.host}:${device.port}/api/pair")
            .openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = "pin=" + URLEncoder.encode(pin.trim(), "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().readText()
            JSONObject(text).optString("token").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun pollFollow(
        device: com.intentbrowser.app.util.NearbyDevice,
        token: String,
        since: Int
    ): Pair<String?, Int> {
        val conn = URL(
            "http://${device.host}:${device.port}/api/follow/poll?token=$token&since=$since"
        ).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 35000 // server long-polls up to ~25s
            if (conn.responseCode != 200) throw IOException("http ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(text)
            val url = json.optString("url").takeIf { it.isNotBlank() }
            Pair(url, json.optInt("seq", since))
        } finally {
            conn.disconnect()
        }
    }

    fun updateTabTitleAndLoading(tabId: String, url: String, title: String, isLoading: Boolean) {
        var changed = false
        var recordHistory = false
        var navigatedUrl: String? = null
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) {
                if (it.url != url || it.title != title || it.isLoading != isLoading) {
                    changed = true
                    if (it.url != url) navigatedUrl = url
                    // History only for real page commits (not iframes — those never
                    // reach here anymore — and not for incognito tabs).
                    if (!isLoading && url.startsWith("http") && !it.isIncognito) {
                        recordHistory = true
                    }
                    it.copy(url = url, title = title, isLoading = isLoading)
                } else it
            } else it
        }
        // Don't rewrite the tabs DB on every no-op callback (title/progress churn).
        if (changed) persistTabs()
        if (recordHistory) {
            viewModelScope.launch { repository.insertHistory(url, title) }
        }
        // Follow mode: when leading, broadcast committed page loads to followers.
        // (Skip our own hub pages so followers never loop back into a hub.)
        val nav = navigatedUrl
        if (nav != null && _isLeading.value && !isLoading &&
            nav.startsWith("http") && !nav.contains(":8080")
        ) {
            com.intentbrowser.app.util.ClipboardServer.broadcastNavigation(nav)
        }
    }

    fun updateTabProgress(tabId: String, progress: Float) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(progress = progress) else it
        }
    }

    fun saveWebViewState(tabId: String, state: Bundle) {
        val blob = try { state.marshall() } catch (_: Exception) { null }
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(webViewState = state, webViewStateBlob = blob) else it
        }
        // Tab switches/closes are the moments state must reach disk.
        persistTabs()
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch {
            repository.toggleBookmark(url, title)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun saveOfflinePage(url: String, title: String, filePath: String) {
        viewModelScope.launch {
            repository.insertPage(com.intentbrowser.app.data.OfflinePage(url = url, title = title, filePath = filePath))
        }
    }

    val downloads = repository.downloads.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val notes = repository.notes.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val clipboardHistory = repository.clipboardHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    private val _showScratchpadBottomSheet = MutableStateFlow(false)
    val showScratchpadBottomSheet: StateFlow<Boolean> = _showScratchpadBottomSheet.asStateFlow()
    
    private val _showClipboardBottomSheet = MutableStateFlow(false)
    val showClipboardBottomSheet: StateFlow<Boolean> = _showClipboardBottomSheet.asStateFlow()
    
    private val _pendingAiPrompt = MutableStateFlow<String?>(null)
    val pendingAiPrompt: StateFlow<String?> = _pendingAiPrompt.asStateFlow()

    fun setScratchpadVisibility(visible: Boolean) {
        _showScratchpadBottomSheet.value = visible
    }
    
    fun setClipboardVisibility(visible: Boolean) {
        _showClipboardBottomSheet.value = visible
    }
    
    fun addClipboardItem(text: String) {
        viewModelScope.launch {
            repository.insertClipboardItem(text)
        }
    }
    
    fun clearClipboardHistory() {
        viewModelScope.launch {
            repository.clearClipboard()
        }
    }
    
    fun sendNoteToAi(noteText: String, tabId: String) {
        _pendingAiPrompt.value = noteText
        createNewTab("https://gemini.google.com/")
        setScratchpadVisibility(false)
    }
    
    fun clearPendingAiPrompt() {
        _pendingAiPrompt.value = null
    }

    fun addNote(content: String) {
        viewModelScope.launch {
            repository.insertNote(content)
        }
    }

    fun deleteNote(note: com.intentbrowser.app.data.NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    
    fun deleteOfflinePage(id: Int) {
        viewModelScope.launch {
            repository.deletePage(id)
        }
    }
    
    fun registerDownload(id: Long, title: String, url: String, mimeType: String) {        viewModelScope.launch {
            repository.insertDownload(com.intentbrowser.app.data.DownloadEntity(
                id = id,
                title = title,
                url = url,
                mimeType = mimeType,
                timestamp = System.currentTimeMillis()
            ))
        }
    }
    
    fun updateDownloadProgress(id: Long, progress: Int, status: String) {
        viewModelScope.launch {
            repository.updateDownloadProgress(id, progress, status)
        }
    }
    
    fun clearDownloads() {
        viewModelScope.launch {
            repository.clearDownloads()
        }
    }

    // --- Downloads: ids generated here (System.currentTimeMillis() collided). ---

    private val downloadIdCounter = AtomicLong(System.currentTimeMillis())

    fun startDownload(
        fileName: String,
        url: String,
        mimeType: String?,
        userAgent: String?,
        cookies: String?
    ) {
        val id = downloadIdCounter.incrementAndGet()
        downloadManager.enqueue(id, fileName, url, mimeType ?: "", userAgent, cookies)
    }

    fun startBlobDownload(dataUrl: String) {
        val id = downloadIdCounter.incrementAndGet()
        downloadManager.enqueueBlob(id, dataUrl)
    }

    // --- Favicons: captured locally, never fetched from Google. ---

    fun saveFavicon(pageUrl: String, icon: Bitmap) {
        val host = try {
            Uri.parse(pageUrl).host
        } catch (_: Exception) {
            null
        } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveFavicon(host, icon)
        }
    }

    fun getFaviconFile(pageUrl: String): java.io.File? {
        val host = try {
            Uri.parse(pageUrl).host
        } catch (_: Exception) {
            null
        } ?: return null
        val f = repository.getFaviconFile(host)
        return if (f.exists()) f else null
    }

    // --- Geolocation per-origin consent ---

    fun getGeoDecision(origin: String): Boolean? = repository.getGeoDecision(origin)

    fun saveGeoDecision(origin: String, allow: Boolean) {
        repository.saveGeoDecision(origin, allow)
    }
}

/** Marshall a WebView.saveState() Bundle so it can live in a Room BLOB column. */
private fun Bundle.marshall(): ByteArray {
    val p = Parcel.obtain()
    try {
        writeToParcel(p, 0)
        return p.marshall()
    } finally {
        p.recycle()
    }
}

private fun ByteArray.unmarshallBundle(): Bundle? = try {
    val p = Parcel.obtain()
    try {
        p.unmarshall(this, 0, size)
        p.setDataPosition(0)
        p.readBundle(Bundle::class.java.classLoader)
    } finally {
        p.recycle()
    }
} catch (_: Exception) {
    null
}
