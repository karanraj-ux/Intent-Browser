package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflinePageDao {
    @Query("SELECT * FROM offline_pages ORDER BY savedAt DESC")
    fun getAllPages(): Flow<List<OfflinePage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: OfflinePage)

    @Query("DELETE FROM offline_pages WHERE id = :id")
    suspend fun deletePage(id: Int)
}
