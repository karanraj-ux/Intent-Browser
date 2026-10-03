package com.intentbrowser.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.intentbrowser.app.data.AppDatabase
import com.intentbrowser.app.data.AppRepository
import com.intentbrowser.app.ui.MainApp
import com.intentbrowser.app.ui.MainViewModel
import com.intentbrowser.app.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  private var pendingViewModel: MainViewModel? = null

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
    val downloadManager = com.intentbrowser.app.util.CustomDownloadManager(this, repository)
    com.intentbrowser.app.util.AdBlocker.init(applicationContext)

    // WebView warmup: create into the pool, don't leak a throwaway instance.
    // (The old code created one WebView and never destroyed it.)
    try {
        com.intentbrowser.app.util.WebViewPool.warmup(applicationContext)
    } catch (e: Exception) {
        // Ignore on devices without WebView
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
            pendingViewModel = viewModel

            // Download notifications need runtime permission on API 33+.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* best effort; downloads still work, just silently */ }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Open links sent by other apps (we're a VIEW handler now).
            LaunchedEffect(Unit) {
                handleViewIntent(intent, viewModel)
            }

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

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingViewModel?.let { handleViewIntent(intent, it) }
  }

  private fun handleViewIntent(intent: Intent?, viewModel: MainViewModel) {
    if (intent?.action == Intent.ACTION_VIEW) {
        val url = intent.dataString
        if (!url.isNullOrBlank()) {
            viewModel.openExternalUrl(url)
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
