package com.example.util

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewSettingsManager {
    fun applySettings(webView: WebView) {
        val settings = webView.settings

        // 1. Enable DOM Storage and Database (Crucial for modern apps like AI Studio, Gemini)
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // 2. Enable JavaScript
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true

        // 3. Set Mixed Content Mode to NEVER ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // 4. Enable Third-Party Cookies (Required for logins, Google Auth, etc.)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        // 5. Custom User-Agent to ensure compatibility (Modern Chrome Mobile UA)
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36 IntentBrowser/1.0"

        // Viewport and Zoom
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Media and Windows
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportMultipleWindows(true)

        // Security - restrict file access
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(true)
        
        // Cache
        settings.cacheMode = WebSettings.LOAD_DEFAULT
    }
}
