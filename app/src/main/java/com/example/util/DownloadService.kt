package com.example.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == action) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            val query = DownloadManager.Query()
            query.setFilterById(downloadId)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0) {
                    val status = cursor.getInt(statusIndex)
                    
                    val dbStatus = when(status) {
                        DownloadManager.STATUS_SUCCESSFUL -> "SUCCESSFUL"
                        DownloadManager.STATUS_FAILED -> "FAILED"
                        else -> "PENDING"
                    }
                    
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(context)
                        db.downloadDao().updateProgress(downloadId, 100, dbStatus)
                    }
                }
            }
            cursor.close()
        }
    }
}
