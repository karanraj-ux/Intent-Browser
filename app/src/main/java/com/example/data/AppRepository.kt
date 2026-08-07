package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val context: Context,
    private val offlinePageDao: OfflinePageDao,
    private val browserHistoryDao: BrowserHistoryDao,
    private val bookmarkDao: BookmarkDao,
    private val tabDao: TabDao,
    private val downloadDao: DownloadDao,
    private val noteDao: NoteDao,
    private val clipboardDao: ClipboardDao
) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getSearchEngine(): String {
        return prefs.getString("search_engine", "GOOGLE") ?: "GOOGLE"
    }

    fun saveSearchEngine(engineName: String) {
        prefs.edit().putString("search_engine", engineName).apply()
    }
    
    // Download Manager Phase 2
    fun getDownloadsDirectory(): String {
        return prefs.getString("download_dir", android.os.Environment.DIRECTORY_DOWNLOADS) ?: android.os.Environment.DIRECTORY_DOWNLOADS
    }
    
    fun saveDownloadsDirectory(dir: String) {
        prefs.edit().putString("download_dir", dir).apply()
    }

    val allPages: Flow<List<OfflinePage>> = offlinePageDao.getAllPages()
    val history: Flow<List<BrowserHistory>> = browserHistoryDao.getHistory()
    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getBookmarks()
    val tabs: Flow<List<TabEntity>> = tabDao.getAllTabs()
    val downloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val notes: Flow<List<NoteEntity>> = noteDao.getAllNotes()
    val clipboardHistory: Flow<List<ClipboardEntity>> = clipboardDao.getClipboardHistory()
    
    suspend fun insertClipboardItem(text: String) {
        clipboardDao.insertClipboard(ClipboardEntity(text = text, timestamp = System.currentTimeMillis()))
        clipboardDao.enforceLimit()
    }
    
    suspend fun clearClipboard() {
        clipboardDao.clearClipboard()
    }

    suspend fun insertNote(content: String) {
        noteDao.insertNote(NoteEntity(content = content, timestamp = System.currentTimeMillis()))
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    
    suspend fun insertDownload(download: DownloadEntity) {
        downloadDao.insertDownload(download)
    }
    
    suspend fun updateDownloadProgress(id: Long, progress: Int, status: String) {
        downloadDao.updateProgress(id, progress, status)
    }
    
    suspend fun clearDownloads() {
        downloadDao.clearDownloads()
    }
    suspend fun failStuckDownloads() {
        downloadDao.failStuckDownloads()
    }

    suspend fun saveTabs(tabs: List<TabEntity>) {
        tabDao.clearTabs()
        tabDao.insertTabs(tabs)
    }

    suspend fun insertPage(page: OfflinePage) {
        offlinePageDao.insertPage(page)
    }

    suspend fun deletePage(id: Int) {
        offlinePageDao.deletePage(id)
    }

    suspend fun insertHistory(url: String, title: String) {
        browserHistoryDao.insert(BrowserHistory(url = url, title = title))
    }

    suspend fun clearHistory() {
        browserHistoryDao.clearHistory()
    }

    suspend fun toggleBookmark(url: String, title: String) {
        if (bookmarkDao.isBookmarked(url)) {
            bookmarkDao.deleteByUrl(url)
        } else {
            bookmarkDao.insert(Bookmark(url = url, title = title))
        }
    }
}
