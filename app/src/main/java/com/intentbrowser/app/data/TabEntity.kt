package com.intentbrowser.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val orderIndex: Int,
    // Marshalled WebView.saveState() Bundle (back/forward history, scroll, form state).
    // Null = cold-load the URL. Written on tab switch/close, read on process restart.
    val stateBlob: ByteArray? = null
)
