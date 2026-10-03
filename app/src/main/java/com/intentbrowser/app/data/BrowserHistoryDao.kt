package com.intentbrowser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY accessedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<BrowserHistory>>

    /**
     * Returns the new row id, or -1 if the URL already exists (dedup via unique url index).
     * Callers must then call [refreshVisit] instead of inserting a duplicate.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: BrowserHistory): Long

    @Query("UPDATE browser_history SET title = :title, accessedAt = :accessedAt WHERE url = :url")
    suspend fun refreshVisit(url: String, title: String, accessedAt: Long)

    @Query("DELETE FROM browser_history WHERE accessedAt < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM browser_history WHERE id NOT IN (SELECT id FROM browser_history ORDER BY accessedAt DESC LIMIT :keep)")
    suspend fun pruneToSize(keep: Int)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()
}
