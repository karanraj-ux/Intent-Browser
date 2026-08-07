package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_pages")
data class OfflinePage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val title: String,
    val filePath: String,
    val savedAt: Long = System.currentTimeMillis()
)
