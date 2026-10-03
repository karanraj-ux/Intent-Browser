package com.intentbrowser.app.data

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

    // --- Browser settings (persisted; previously in-memory only) ---

    fun isAdBlockerEnabled(): Boolean = prefs.getBoolean("adblock", true)
    fun saveAdBlockerEnabled(enabled: Boolean) { prefs.edit().putBoolean("adblock", enabled).apply() }

    /** True = block third-party cookies (privacy default). */
    fun isThirdPartyCookiesBlocked(): Boolean = prefs.getBoolean("block_3p_cookies", true)
    fun saveThirdPartyCookiesBlocked(blocked: Boolean) { prefs.edit().putBoolean("block_3p_cookies", blocked).apply() }

    /** Per-origin geolocation decisions: origin -> true (allow) / false (block). Absent = ask. */
    fun getGeoDecision(origin: String): Boolean? =
        if (prefs.contains("geo_$origin")) prefs.getBoolean("geo_$origin", false) else null
    fun saveGeoDecision(origin: String, allow: Boolean) {
        prefs.edit().putBoolean("geo_$origin", allow).apply()
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
        tabDao.replaceAll(tabs)
    }

    suspend fun insertPage(page: OfflinePage) {
        offlinePageDao.insertPage(page)
    }

    suspend fun deletePage(id: Int) {
        offlinePageDao.deletePage(id)
    }

    /**
     * Deduped history: revisits refresh the existing row instead of inserting duplicates.
     * Prunes rows older than 90 days and caps the table at 2000 rows.
     */
    suspend fun insertHistory(url: String, title: String) {
        val now = System.currentTimeMillis()
        val rowId = browserHistoryDao.insert(BrowserHistory(url = url, title = title, accessedAt = now))
        if (rowId == -1L) {
            browserHistoryDao.refreshVisit(url, title, now)
        }
        browserHistoryDao.pruneOlderThan(now - 90L * 24 * 60 * 60 * 1000)
        browserHistoryDao.pruneToSize(2000)
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

    // --- Favicons: captured locally via WebView.onReceivedIcon. ---

    fun saveFavicon(host: String, icon: android.graphics.Bitmap) {
        try {
            val dir = java.io.File(context.filesDir, "favicons").apply { mkdirs() }
            val file = java.io.File(dir, host.hashCode().toString() + ".png")
            java.io.FileOutputStream(file).use { out ->
                icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {
            // Best effort; a missing favicon is not a failure.
        }
    }

    fun getFaviconFile(host: String): java.io.File =
        java.io.File(java.io.File(context.filesDir, "favicons"), host.hashCode().toString() + ".png")
}
