package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val url: String,
    val mimeType: String,
    val timestamp: Long,
    val status: String = "PENDING", // PENDING, RUNNING, SUCCESSFUL, FAILED
    val progress: Int = 0
)
