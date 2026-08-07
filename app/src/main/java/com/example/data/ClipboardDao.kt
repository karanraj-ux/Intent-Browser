package com.example.data
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC LIMIT 200")
    fun getClipboardHistory(): Flow<List<ClipboardEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClipboard(item: ClipboardEntity)

    @Query("DELETE FROM clipboard_history WHERE id NOT IN (SELECT id FROM clipboard_history ORDER BY timestamp DESC LIMIT 200)")
    suspend fun enforceLimit()
    
    @Query("DELETE FROM clipboard_history")
    suspend fun clearClipboard()
}
