package com.example.ui

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TabState(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "app://newtab",
    val title: String = "New Tab",
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val webViewState: Bundle? = null,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false
)

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
    BRAVE("Brave", "https://search.brave.com/search?q=%s"),
    YAHOO("Yahoo", "https://search.yahoo.com/search?p=%s"),
    ECOSIA("Ecosia", "https://www.ecosia.org/search?q=%s"),
    STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query=%s")
}

class MainViewModel(private val repository: AppRepository, val downloadManager: com.example.util.CustomDownloadManager) : ViewModel() {
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
                        TabState(id = it.id, url = it.url, title = it.title)
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
                com.example.data.TabEntity(
                    id = tabState.id,
                    url = tabState.url,
                    title = tabState.title,
                    orderIndex = index
                )
            }
            repository.saveTabs(entities)
        }
        updateSyncServerTabs()
    }

    val bookmarks = repository.bookmarks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val history = repository.history.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPages = repository.allPages.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isFocusMode = MutableStateFlow(true)
    val isFocusMode: StateFlow<Boolean> = _isFocusMode.asStateFlow()

    private val _isAdBlockerEnabled = MutableStateFlow(true)
    val isAdBlockerEnabled: StateFlow<Boolean> = _isAdBlockerEnabled.asStateFlow()

    fun toggleFocusMode(enabled: Boolean) {
        _isFocusMode.value = enabled
        // Save to repository ideally, but for now just state
    }
    
    fun toggleAdBlocker(enabled: Boolean) {
        _isAdBlockerEnabled.value = enabled
    }
    

    private val _sharedClipboard = MutableStateFlow("")
    val sharedClipboard: StateFlow<String> = _sharedClipboard.asStateFlow()

    
    private val _receivedMessage = MutableStateFlow<String?>(null)
    val receivedMessage: StateFlow<String?> = _receivedMessage.asStateFlow()
    fun clearReceivedMessage() { _receivedMessage.value = null }

    private var meshChatServer: com.example.util.MeshChatServer? = null
    
    private val _meshServerIp = MutableStateFlow<String?>(null)
    val meshServerIp: StateFlow<String?> = _meshServerIp.asStateFlow()
    
    private val _meshPin = MutableStateFlow<String?>(null)
    val meshPin: StateFlow<String?> = _meshPin.asStateFlow()
    
    private val _meshMessages = MutableStateFlow<List<com.example.util.MeshMessage>>(emptyList())
    val meshMessages: StateFlow<List<com.example.util.MeshMessage>> = _meshMessages.asStateFlow()

    fun startMeshServer() {
        val ip = com.example.util.NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            val fullIp = "http://$ip:8081"
            _meshServerIp.value = fullIp
            meshChatServer = com.example.util.MeshChatServer(port = 8081, serverIp = fullIp).apply {
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

    

    fun startSyncServer() {
        val ip = com.example.util.NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            _syncServerIp.value = "http://$ip:8080"
            com.example.util.ClipboardServer.currentClipboard = _sharedClipboard.value
            com.example.util.ClipboardServer.onClipboardChanged = { text ->
                _sharedClipboard.value = text
            }
            com.example.util.ClipboardServer.onTabAction = { action, payload ->
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    when (action) {
                        "close" -> closeTab(payload)
                        "open" -> createNewTab(payload)
                    }
                }
            }
            com.example.util.ClipboardServer.onMessageReceived = { msg ->
                _receivedMessage.value = msg
            }
            updateSyncServerTabs()
            com.example.util.ClipboardServer.start()
        } else {
            _syncServerIp.value = "Connect to Wi-Fi first"
        }
    }
    
    private fun updateSyncServerTabs() {
        val json = _tabs.value.joinToString(prefix = "[", postfix = "]") {
            "{\"id\":\"${it.id}\",\"url\":\"${it.url}\",\"title\":\"${it.title.replace("\"", "\\\"")}\"}"
        }
        com.example.util.ClipboardServer.currentTabsJson = json
    }


    fun stopSyncServer() {
        com.example.util.ClipboardServer.stop()
        _syncServerIp.value = null
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
        com.example.util.ClipboardServer.currentClipboard = text
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
                } else {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    cookieManager.flush()
                }
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

    fun updateTabTitleAndLoading(tabId: String, url: String, title: String, isLoading: Boolean) {
        var isIncognito = false
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) {
                isIncognito = it.isIncognito
                it.copy(url = url, title = title, isLoading = isLoading)
            } else it
        }
        persistTabs()
        if (!isLoading && url.startsWith("http") && !isIncognito) {
            viewModelScope.launch { repository.insertHistory(url, title) }
        }
    }

    fun updateTabProgress(tabId: String, progress: Float) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(progress = progress) else it
        }
    }

    fun saveWebViewState(tabId: String, state: Bundle) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(webViewState = state) else it
        }
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
            repository.insertPage(com.example.data.OfflinePage(url = url, title = title, filePath = filePath))
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

    fun deleteNote(note: com.example.data.NoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    
    fun deleteOfflinePage(id: Int) {
        viewModelScope.launch {
            repository.deletePage(id)
        }
    }
    
    fun registerDownload(id: Long, title: String, url: String, mimeType: String) {
        viewModelScope.launch {
            repository.insertDownload(com.example.data.DownloadEntity(
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
}
