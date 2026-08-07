package com.example.util

import android.content.Context
import android.webkit.WebView

object WebViewPool {
    private val pool = mutableListOf<WebView>()
    private var maxPoolSize = 2

    fun getWebView(context: Context): WebView {
        if (pool.isNotEmpty()) {
            val view = pool.removeAt(0)
            view.clearHistory()
            return view
        }
        return createWebView(context)
    }

    fun recycle(webView: WebView) {
        if (pool.size < maxPoolSize) {
            webView.loadUrl("about:blank")
            webView.clearHistory()
            pool.add(webView)
        } else {
            webView.destroy()
        }
    }

    

class CustomWebView(context: Context) : WebView(context) {
}

    private fun createWebView(context: Context): WebView {
        val webView = CustomWebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = true
        }
        return webView
    }
}
