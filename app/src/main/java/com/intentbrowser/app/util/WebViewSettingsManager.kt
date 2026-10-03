package com.intentbrowser.app.util

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

object WebViewSettingsManager {

    /**
     * The WebView's factory default UA, captured once before we override it.
     * Desktop mode is derived from this instead of a frozen 2023 Chrome string.
     */
    @Volatile
    var defaultUserAgent: String? = null
        private set

    /**
     * @param blockThirdPartyCookies true = privacy default; logins that need 3P
     * cookies can be allowed per-settings by the user.
     */
    fun applySettings(webView: WebView, blockThirdPartyCookies: Boolean = true) {
        val settings = webView.settings

        // Capture the real default UA before overriding it.
        if (defaultUserAgent == null) {
            defaultUserAgent = settings.userAgentString
        }

        // 1. DOM storage (required by modern web apps)
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true

        // 2. JavaScript
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true

        // 3. Mixed content: match Chrome (compatibility mode). NEVER_ALLOW broke
        // real https sites that still pull some http assets.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, !blockThirdPartyCookies)
        }

        // 4. Safe Browsing (malware/phishing protection, Chrome-parity)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
        }

        // 5. User-Agent: real default + our token (no more frozen Chrome/114)
        settings.userAgentString = mobileUserAgent()

        // Viewport and Zoom
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Media: require a user gesture (no autoplay-with-sound chaos)
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(true)

        // File access: off for web content. The offline-page viewer enables it
        // narrowly for its own files dir only.
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(true)

        // Cache
        settings.cacheMode = WebSettings.LOAD_DEFAULT
    }

    fun mobileUserAgent(): String {
        val base = defaultUserAgent
            ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        return "$base IntentBrowser/2.0"
    }

    fun desktopUserAgent(): String {
        val base = defaultUserAgent
            ?: "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        return base
            .replace(Regex("\\(Linux; Android [^)]+\\)"), "(Windows NT 10.0; Win64; x64)")
            .replace(" Mobile Safari/", " Safari/") + " IntentBrowser/2.0"
    }
}
