package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads")
    suspend fun clearDownloads()
    
    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Int, status: String)
    @Query("UPDATE downloads SET status = 'FAILED' WHERE status IN ('RUNNING', 'PENDING')")
    suspend fun failStuckDownloads()
}
