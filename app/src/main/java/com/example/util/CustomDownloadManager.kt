package com.example.util

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import com.example.data.AppRepository
import com.example.data.DownloadEntity

enum class DownloadState {
    DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED
}

data class ActiveDownload(
    val id: Long,
    val title: String,
    val url: String,
    val mimeType: String,
    val state: DownloadState = DownloadState.DOWNLOADING,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val userAgent: String? = null,
    val cookies: String? = null
)

class CustomDownloadManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val _downloads = MutableStateFlow<Map<Long, ActiveDownload>>(emptyMap())
    val downloads: StateFlow<List<ActiveDownload>> = _downloads.map { it.values.toList().sortedByDescending { d -> d.id } }
        .stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, emptyList())

    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = mutableMapOf<Long, Job>()

    fun enqueue(id: Long, title: String, url: String, mimeType: String, userAgent: String?, cookies: String?) {
        val download = ActiveDownload(id, title, url, mimeType, userAgent = userAgent, cookies = cookies)
        _downloads.update { it + (id to download) }
        
        scope.launch {
            repository.insertDownload(DownloadEntity(id, title, url, mimeType, System.currentTimeMillis(), "RUNNING", 0))
        }
        
        startJob(id)
    }

    fun pause(id: Long) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _downloads.update { 
            val d = it[id]
            if (d != null) {
                it + (id to d.copy(state = DownloadState.PAUSED))
            } else it
        }
    }

    fun resume(id: Long) {
        _downloads.update {
            val d = it[id]
            if (d != null) {
                it + (id to d.copy(state = DownloadState.DOWNLOADING))
            } else it
        }
        startJob(id)
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
        jobs.remove(id)
        _downloads.update {
            val d = it[id]
            if (d != null) {
                it + (id to d.copy(state = DownloadState.CANCELED))
            } else it
        }
        val dl = _downloads.value[id]
        if (dl != null) {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${dl.title}.part")
            if (file.exists()) file.delete()
        }
        // Remove from list
        _downloads.update { it - id }
    }
    
    fun removeCompleted(id: Long) {
        _downloads.update { it - id }
    }

    private fun startJob(id: Long) {
        val job = scope.launch {
            val dl = _downloads.value[id] ?: return@launch
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "${dl.title}.part")
            val downloaded = if (file.exists()) file.length() else 0L

            val requestBuilder = Request.Builder().url(dl.url)
            if (dl.userAgent != null) requestBuilder.addHeader("User-Agent", dl.userAgent)
            if (dl.cookies != null) requestBuilder.addHeader("Cookie", dl.cookies)
            if (downloaded > 0) requestBuilder.addHeader("Range", "bytes=$downloaded-")

            try {
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    throw Exception("Failed to download: ${response.code}")
                }
                
                val body = response.body ?: throw Exception("Empty body")
                var total = body.contentLength()
                if (downloaded > 0 && response.code == 206) {
                    total += downloaded
                } else if (response.code == 200) {
                    // Server ignored range
                    if (file.exists()) file.delete()
                } else {
                    total = dl.totalBytes // Use previous if available
                }

                _downloads.update { it + (id to dl.copy(totalBytes = total)) }

                RandomAccessFile(file, "rw").use { raf ->
                    if (response.code == 206) {
                        raf.seek(downloaded)
                    } else {
                        raf.seek(0)
                    }
                    
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int = 0
                    var currentDownloaded = raf.length()
                    var lastUpdate = System.currentTimeMillis()

                    while (isActive && inputStream.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        currentDownloaded += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 300) { // Update UI every 300ms
                            lastUpdate = now
                            val progress = if (total > 0) ((currentDownloaded.toDouble() / total) * 100).toInt() else 0
                            _downloads.update { 
                                val curr = it[id]
                                if (curr != null) {
                                    it + (id to curr.copy(progress = progress, downloadedBytes = currentDownloaded))
                                } else it
                            }
                        }
                    }
                }

                if (isActive) {
                    // Finished
                    val baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val finalFile = getUniqueFile(baseDir, dl.title)
                    file.renameTo(finalFile)
                    _downloads.update { it + (id to dl.copy(state = DownloadState.COMPLETED, progress = 100, title = finalFile.name)) }
                    repository.updateDownloadProgress(id, 100, "SUCCESSFUL")
                }

            } catch (e: CancellationException) {
                // Paused or Canceled
            } catch (e: Exception) {
                Log.e("DownloadManager", "Error downloading", e)
                _downloads.update { it + (id to dl.copy(state = DownloadState.FAILED)) }
                repository.updateDownloadProgress(id, 0, "FAILED")
            }
        }
        jobs[id] = job
    }
    
    private fun getUniqueFile(baseDir: File, fileName: String): File {
        var file = File(baseDir, fileName)
        if (!file.exists()) return file
        
        val name = file.nameWithoutExtension
        val ext = file.extension
        val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
        
        var counter = 1
        while (file.exists()) {
            file = File(baseDir, "$name ($counter)$dotExt")
            counter++
        }
        return file
    }
}
