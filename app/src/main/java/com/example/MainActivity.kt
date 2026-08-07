package com.example

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.MainApp
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val database = AppDatabase.getDatabase(this)
    val repository = AppRepository(
        this,
        database.offlinePageDao(),
        database.browserHistoryDao(),
        database.bookmarkDao(),
        database.tabDao(),
        database.downloadDao(),
        database.noteDao(),
        database.clipboardDao()
    )
    val downloadManager = com.example.util.CustomDownloadManager(this, repository)
    com.example.util.AdBlocker.init(applicationContext)

    // WebView Pool / Warmup: Waking up Chromium engine in background for instant opening
    try {
        android.webkit.WebView(this)
    } catch (e: Exception) {
        // Ignore if error occurs on devices without WebView
    }
    setContent {

      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

            val factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainViewModel(repository, downloadManager) as T
                }
            }
            val viewModel: MainViewModel = viewModel(factory = factory)
            
            // Clipboard Listener
            val clipboardManager = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboardManager.addPrimaryClipChangedListener {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    if (text != null) {
                        viewModel.addClipboardItem(text)
                    }
                }
            }

            MainApp(viewModel)
        }
      }
    }
  }

  
  override fun onPause() {
    super.onPause()
    try {
        android.webkit.CookieManager.getInstance().flush()
    } catch(e: Exception) {}
  }
  
  override fun onDestroy() {
    super.onDestroy()
  }
}

