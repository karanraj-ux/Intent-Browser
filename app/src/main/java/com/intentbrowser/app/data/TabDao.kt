package com.intentbrowser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY orderIndex ASC")
    fun getAllTabs(): Flow<List<TabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Query("DELETE FROM tabs")
    suspend fun clearTabs()

    /** Atomic replace: a crash between clear and insert used to lose every tab. */
    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>) {
        clearTabs()
        insertTabs(tabs)
    }
}
