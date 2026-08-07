package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY accessedAt DESC LIMIT 100")
    fun getHistory(): Flow<List<BrowserHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: BrowserHistory)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()
}
