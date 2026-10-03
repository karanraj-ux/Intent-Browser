package com.intentbrowser.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "browser_history",
    indices = [Index(value = ["url"], unique = true)]
)
data class BrowserHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val accessedAt: Long = System.currentTimeMillis()
)
