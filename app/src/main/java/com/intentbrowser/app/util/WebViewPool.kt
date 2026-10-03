package com.intentbrowser.app.util

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

    /**
     * Pre-warm the Chromium engine at app start. Uses the application context so
     * the pooled view never leaks an Activity. (The old MainActivity created a
     * WebView and never destroyed it — a permanent leak for a one-time warmup.)
     */
    fun warmup(context: Context) {
        if (pool.isEmpty()) {
            pool.add(createWebView(context.applicationContext))
        }
    }

    

class CustomWebView(context: Context) : WebView(context) {
}

    private fun createWebView(context: Context): WebView {
        val webView = CustomWebView(context)
        WebViewSettingsManager.applySettings(webView)
        return webView
    }
}
