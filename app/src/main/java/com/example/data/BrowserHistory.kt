package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browser_history")
data class BrowserHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val accessedAt: Long = System.currentTimeMillis()
)
