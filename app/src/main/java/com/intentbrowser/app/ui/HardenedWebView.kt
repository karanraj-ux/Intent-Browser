package com.intentbrowser.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewFeature
import com.intentbrowser.app.util.AdBlocker
import com.intentbrowser.app.util.ErrorPages
import com.intentbrowser.app.util.WebViewPool
import com.intentbrowser.app.util.WebViewSettingsManager
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Reports SPA (pushState/replaceState) navigations that never fire page callbacks. */
private class SpaUrlWatcher(private val onChange: (String) -> Unit) {
    @JavascriptInterface
    fun onUrlChange(url: String) = onChange(url)
}

/** One-shot receiver for blob: download content (data URL with base64). */
private class BlobWatcher(private val onResult: (String) -> Unit) {
    @JavascriptInterface
    fun onBlob(result: String) = onResult(result)
}

private fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

private fun String.isBrowsable(): Boolean =
    this != "app://newtab" && this != "about:blank" && isNotBlank()

private fun sslReason(primaryError: Int): String = when (primaryError) {
    SslError.SSL_NOTYETVALID -> "The certificate is not yet valid (device clock may be wrong)."
    SslError.SSL_EXPIRED -> "The certificate has expired."
    SslError.SSL_IDMISMATCH -> "The certificate is for a different site (hostname mismatch)."
    SslError.SSL_UNTRUSTED -> "The certificate is from an untrusted authority."
    SslError.SSL_DATE_INVALID -> "The certificate date is invalid."
    SslError.SSL_INVALID -> "The certificate is invalid."
    else -> "Unknown certificate problem."
}

@Composable
fun HardenedWebView(
    url: String,
    tabId: String,
    webViewState: Bundle?,
    isAdBlockerEnabled: Boolean,
    blockThirdPartyCookies: Boolean = true,
    isIncognito: Boolean = false,
    isDesktopMode: Boolean = false,
    onTitleAndLoadingChange: (String, String, Boolean) -> Unit,
    onProgressChange: (Float) -> Unit,
    onSaveState: (Bundle) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onCreateNewTab: (String) -> Unit = {},
    onDownloadStarted: ((fileName: String, url: String, mimeType: String, userAgent: String?, cookies: String?) -> Unit)? = null,
    onBlobDownload: ((dataUrl: String) -> Unit)? = null,
    onPageFinishedListener: ((String, WebView) -> Unit)? = null,
    onRendererCrashed: () -> Unit = {},
    onLongPress: (WebView.HitTestResult) -> Unit = {},
    onFaviconReceived: ((pageUrl: String, icon: Bitmap) -> Unit)? = null,
    geoDecision: (String) -> Boolean? = { null },
    onSaveGeoDecision: (String, Boolean) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    var uploadMessage by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var fullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var popupTitle by remember { mutableStateOf("") }

    // Refs protect WebView callbacks from stale composition captures.
    val urlRef = remember { mutableStateOf(url) }
    val adblockRef = remember { mutableStateOf(isAdBlockerEnabled) }
    val cookieRef = remember { mutableStateOf(blockThirdPartyCookies) }
    // The URL we last explicitly asked the WebView to load. The reload-loop fix:
    // never loadUrl just because view.url drifted (redirects, SPA pushState).
    val loadedUrlRef = remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }

    val geoPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.all { it.value }
            pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
            pendingGeoCallback = null
            pendingGeoOrigin = null
        }

    fun grantGeo(allow: Boolean) {
        val cb = pendingGeoCallback
        val origin = pendingGeoOrigin
        pendingGeoCallback = null
        pendingGeoOrigin = null
        if (allow) {
            geoPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            cb?.invoke(origin, false, false)
        }
    }

    fun askGeoPermission(origin: String, callback: GeolocationPermissions.Callback?) {
        val activity = context.activityOrNull()
        if (activity == null || activity.isFinishing) {
            callback?.invoke(origin, false, false)
            return
        }
        when (geoDecision(origin)) {
            true -> {
                pendingGeoCallback = callback
                pendingGeoOrigin = origin
                grantGeo(true)
            }
            false -> callback?.invoke(origin, false, false)
            null -> {
                val checkBox = CheckBox(context).apply { text = "Remember my choice" }
                val layout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 16, 48, 0)
                    addView(checkBox)
                }
                AlertDialog.Builder(activity)
                    .setTitle("Share your location?")
                    .setMessage("$origin wants to use your location.")
                    .setView(layout)
                    .setPositiveButton("Allow") { _, _ ->
                        if (checkBox.isChecked) onSaveGeoDecision(origin, true)
                        pendingGeoCallback = callback
                        pendingGeoOrigin = origin
                        grantGeo(true)
                    }
                    .setNegativeButton("Block") { _, _ ->
                        if (checkBox.isChecked) onSaveGeoDecision(origin, false)
                        callback?.invoke(origin, false, false)
                    }
                    .setOnCancelListener { callback?.invoke(origin, false, false) }
                    .show()
            }
        }
    }

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val hardwarePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.all { it.value }) {
                pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
            } else {
                pendingPermissionRequest?.deny()
            }
            pendingPermissionRequest = null
        }

    val singleFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uploadMessage?.onReceiveValue(uri?.let { arrayOf(it) })
            uploadMessage = null
        }
    val multiFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            uploadMessage?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
            uploadMessage = null
        }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val webView = if (isIncognito) {
                WebView(ctx).apply {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                        try {
                            val profile = androidx.webkit.ProfileStore.getInstance()
                                .getOrCreateProfile("incognito")
                            androidx.webkit.WebViewCompat.setProfile(this, profile)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    WebViewSettingsManager.applySettings(this, blockThirdPartyCookies)
                }
            } else {
                WebViewPool.getWebView(ctx).also {
                    // Re-apply: pooled views may carry older settings.
                    WebViewSettingsManager.applySettings(it, cookieRef.value)
                }
            }

            webView.apply {
                addJavascriptInterface(SpaUrlWatcher { newUrl ->
                    // SPA navigation (YouTube, Gmail…): update the URL bar without reloading.
                    val current = urlRef.value
                    if (newUrl.isNotBlank() && newUrl != current && newUrl.startsWith("http")) {
                        onTitleAndLoadingChange(newUrl, title ?: newUrl, false)
                    }
                }, "IntentBrowser")

                addJavascriptInterface(BlobWatcher { result ->
                    if (result == "ERROR" || !result.startsWith("data:")) {
                        Toast.makeText(ctx, "Download failed", Toast.LENGTH_SHORT).show()
                    } else {
                        onBlobDownload?.invoke(result)
                    }
                }, "IntentBrowserBlob")

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, urlStr: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, urlStr, favicon)
                        // Only the requested navigation drives the loading state (not iframes).
                        if (urlStr != null && urlStr == urlRef.value) {
                            onTitleAndLoadingChange(urlStr, "Loading…", true)
                        }
                    }

                    override fun onPageCommitVisible(view: WebView?, urlStr: String?) {
                        super.onPageCommitVisible(view, urlStr)
                        // Main-frame only: the reliable navigation signal (no iframe spam).
                        urlStr?.let {
                            loadedUrlRef.value = it
                            onTitleAndLoadingChange(it, view?.title ?: it, false)
                        }
                    }

                    override fun onPageFinished(view: WebView?, urlStr: String?) {
                        super.onPageFinished(view, urlStr)

                        // Force-enable zoom (accessibility): break user-scalable=no.
                        val forceZoomJs = "(function(){var m=document.querySelector('meta[name=\"viewport\"]');" +
                            "if(m){m.content=m.content.replace(/user-scalable=no/ig,'user-scalable=yes')" +
                            ".replace(/maximum-scale=[0-9.]+/ig,'maximum-scale=5.0');}" +
                            "else{m=document.createElement('meta');m.name='viewport';" +
                            "m.content='width=device-width,initial-scale=1.0,maximum-scale=5.0,user-scalable=yes';" +
                            "document.head.appendChild(m);}})();"
                        view?.evaluateJavascript(forceZoomJs, null)

                        // SPA URL watcher (pushState/replaceState/popstate/hashchange).
                        val spaJs = "(function(){if(window.__ibWatched)return;window.__ibWatched=true;" +
                            "function n(){try{IntentBrowser.onUrlChange(location.href)}catch(e){}}" +
                            "var p=history.pushState;history.pushState=function(){var r=p.apply(this,arguments);n();return r;};" +
                            "var rp=history.replaceState;history.replaceState=function(){var r=rp.apply(this,arguments);n();return r;};" +
                            "window.addEventListener('popstate',n);window.addEventListener('hashchange',n);})();"
                        view?.evaluateJavascript(spaJs, null)

                        urlStr?.let {
                            onPageFinishedListener?.invoke(it, view!!)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true && error != null) {
                            val failedUrl = request.url.toString()
                            view?.loadDataWithBaseURL(
                                null,
                                ErrorPages.forNetError(error.errorCode, failedUrl),
                                "text/html", "UTF-8", null
                            )
                            onTitleAndLoadingChange(failedUrl, "Couldn't load page", false)
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        val status = errorResponse?.statusCode ?: 200
                        if (request?.isForMainFrame == true && status >= 400) {
                            val failedUrl = request.url.toString()
                            view?.loadDataWithBaseURL(
                                null,
                                ErrorPages.forHttpError(status, failedUrl),
                                "text/html", "UTF-8", null
                            )
                            onTitleAndLoadingChange(failedUrl, "Error $status", false)
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?, handler: SslErrorHandler?, error: SslError?
                    ) {
                        val activity = ctx.activityOrNull()
                        if (activity == null || activity.isFinishing || error == null) {
                            handler?.cancel()
                            return
                        }
                        val cert = error.certificate
                        val details = buildString {
                            append("Couldn't verify this site's security.\n\n")
                            append("Site: ${error.url}\n")
                            append("Problem: ${sslReason(error.primaryError)}\n")
                            cert?.let {
                                append("Issued to: ${it.issuedTo?.dName}\n")
                                append("Issued by: ${it.issuedBy?.dName}\n")
                            }
                            append("\nOnly continue if you understand the risk.")
                        }
                        AlertDialog.Builder(activity)
                            .setTitle("Security warning")
                            .setMessage(details)
                            .setPositiveButton("Continue anyway") { _, _ -> handler?.proceed() }
                            .setNegativeButton("Go back") { _, _ -> handler?.cancel() }
                            .setOnCancelListener { handler?.cancel() }
                            .show()
                    }

                    override fun onSafeBrowsingHit(
                        view: WebView?,
                        request: WebResourceRequest?,
                        threatType: Int,
                        callback: SafeBrowsingResponse?
                    ) {
                        if (WebViewFeature.isFeatureSupported(
                                WebViewFeature.SAFE_BROWSING_RESPONSE_SHOW_INTERSTITIAL
                            )
                        ) {
                            // Chrome-style interstitial: "Back to safety" / details → proceed.
                            callback?.showInterstitial(true)
                        } else {
                            super.onSafeBrowsingHit(view, request, threatType, callback)
                        }
                    }

                    override fun onReceivedHttpAuthRequest(
                        view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?
                    ) {
                        val activity = ctx.activityOrNull()
                        if (activity == null || activity.isFinishing || handler == null) {
                            handler?.cancel()
                            return
                        }
                        val userInput = EditText(activity).apply { hint = "Username" }
                        val passInput = EditText(activity).apply {
                            hint = "Password"
                            inputType =
                                android.text.InputType.TYPE_CLASS_TEXT or
                                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        }
                        val layout = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(48, 16, 48, 0)
                            addView(userInput)
                            addView(passInput)
                        }
                        AlertDialog.Builder(activity)
                            .setTitle("Sign in")
                            .setMessage("$host asks for a username and password.")
                            .setView(layout)
                            .setPositiveButton("Sign in") { _, _ ->
                                handler.proceed(
                                    userInput.text.toString(),
                                    passInput.text.toString()
                                )
                            }
                            .setNegativeButton("Cancel") { _, _ -> handler.cancel() }
                            .setOnCancelListener { handler.cancel() }
                            .show()
                    }

                    override fun onFormResubmission(
                        view: WebView?,
                        dontResend: android.os.Message?,
                        resend: android.os.Message?
                    ) {
                        val activity = ctx.activityOrNull()
                        if (activity == null || activity.isFinishing) {
                            dontResend?.sendToTarget()
                            return
                        }
                        AlertDialog.Builder(activity)
                            .setTitle("Resubmit form?")
                            .setMessage("Going back will resend the information you entered on the page.")
                            .setPositiveButton("Resend") { _, _ -> resend?.sendToTarget() }
                            .setNegativeButton("Cancel") { _, _ -> dontResend?.sendToTarget() }
                            .setOnCancelListener { dontResend?.sendToTarget() }
                            .show()
                    }

                    override fun onRenderProcessGone(
                        view: WebView?, detail: RenderProcessGoneDetail?
                    ): Boolean {
                        // Renderer crashed (usually low RAM). The UI recreates the tab.
                        onRendererCrashed()
                        return true
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val urlStr = request?.url?.toString() ?: return false

                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://") ||
                            urlStr.startsWith("file://")
                        ) {
                            return false
                        }
                        return try {
                            val intent = if (urlStr.startsWith("intent:")) {
                                Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                    component = null
                                    selector = null
                                }
                            } else {
                                Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                            }
                            if (AdBlocker.isAdOrTracker(intent.dataString ?: "")) {
                                return true // Block tracking intent
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                view?.context?.startActivity(intent)
                                true
                            } catch (e: ActivityNotFoundException) {
                                // App not installed: fall back to the Play Store / web URL.
                                val fallback = if (urlStr.startsWith("intent:")) {
                                    try {
                                        Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                                            .getStringExtra("browser_fallback_url")
                                    } catch (_: Exception) {
                                        null
                                    }
                                } else null
                                if (fallback != null) {
                                    view?.loadUrl(fallback)
                                    true
                                } else {
                                    Toast.makeText(
                                        ctx, "No app can open this link", Toast.LENGTH_SHORT
                                    ).show()
                                    true
                                }
                            }
                        } catch (e: Exception) {
                            false
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?, request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val requestUrl = request?.url?.toString()
                        if (requestUrl != null) {
                            if (requestUrl.contains("ai.studio") ||
                                requestUrl.contains("gemini.google.com") ||
                                requestUrl.contains("wayback")
                            ) {
                                return super.shouldInterceptRequest(view, request)
                            }
                            if (adblockRef.value) {
                                // Main frames: domain-only matching (no path-heuristic false
                                // positives). Subresources: full heuristics.
                                val blocked = if (request.isForMainFrame) {
                                    AdBlocker.isBlockedDomain(requestUrl) ||
                                        AdBlocker.isAdultDomain(requestUrl)
                                } else {
                                    AdBlocker.isAdOrTracker(requestUrl) ||
                                        AdBlocker.isAdultContent(requestUrl)
                                }
                                if (blocked) {
                                if (request.isForMainFrame) {
                                    return WebResourceResponse(
                                        "text/html", "UTF-8",
                                        ByteArrayInputStream(
                                            ErrorPages.page(
                                                "Blocked",
                                                "Blocked",
                                                "This page was blocked by the ad & tracker blocker. " +
                                                    "Turn it off in Settings to visit it.",
                                                requestUrl
                                            ).toByteArray()
                                        )
                                    )
                                }
                                return WebResourceResponse(
                                    "text/plain", "UTF-8",
                                    ByteArrayInputStream("".toByteArray())
                                )
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChange(newProgress / 100f)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        // Main-frame only: keeps SPA titles fresh without iframe spam.
                        view?.url?.let { pageUrl ->
                            if (!title.isNullOrBlank()) {
                                onTitleAndLoadingChange(pageUrl, title, false)
                            }
                        }
                    }

                    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                        super.onReceivedIcon(view, icon)
                        val pageUrl = view?.url
                        if (icon != null && pageUrl != null) {
                            onFaviconReceived?.invoke(pageUrl, icon)
                        }
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        uploadMessage = filePathCallback
                        // Honor what the site asked for (was: always */* multi).
                        val accept = fileChooserParams?.acceptTypes
                            ?.firstOrNull { it.isNotBlank() && !it.contains(",") }
                            ?: "*/*"
                        if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                            multiFileLauncher.launch(accept)
                        } else {
                            singleFileLauncher.launch(accept)
                        }
                        return true
                    }

                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                        super.onShowCustomView(view, callback)
                        // True fullscreen video: hide system bars while showing.
                        ctx.activityOrNull()?.window?.let { window ->
                            WindowCompat.getInsetsController(window, window.decorView)
                                .hide(WindowInsetsCompat.Type.systemBars())
                        }
                        fullscreenView = view
                        customViewCallback = callback
                    }

                    override fun onHideCustomView() {
                        super.onHideCustomView()
                        ctx.activityOrNull()?.window?.let { window ->
                            WindowCompat.getInsetsController(window, window.decorView)
                                .show(WindowInsetsCompat.Type.systemBars())
                        }
                        customViewCallback?.onCustomViewHidden()
                        fullscreenView = null
                        customViewCallback = null
                    }

                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?, callback: GeolocationPermissions.Callback?
                    ) {
                        if (origin == null) {
                            callback?.invoke(null, false, false)
                            return
                        }
                        askGeoPermission(origin, callback)
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        pendingPermissionRequest = request
                        val androidPermissions = mutableListOf<String>()
                        if (request?.resources?.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) == true) {
                            androidPermissions.add(android.Manifest.permission.CAMERA)
                        }
                        if (request?.resources?.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) == true) {
                            androidPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                        }
                        if (androidPermissions.isNotEmpty()) {
                            hardwarePermissionLauncher.launch(androidPermissions.toTypedArray())
                        } else {
                            // Non-hardware resources (e.g. MIDI): grant; camera/mic always
                            // go through the Android permission prompt above.
                            request?.grant(request.resources)
                            pendingPermissionRequest = null
                        }
                    }

                    override fun onCreateWindow(
                        view: WebView?, isDialog: Boolean, isUserGesture: Boolean,
                        resultMsg: android.os.Message?
                    ): Boolean {
                        // Real popup support (OAuth, payments, window.open) — was: silently dropped.
                        val activity = ctx.activityOrNull()
                        if (view == null || activity == null || activity.isFinishing) {
                            return false
                        }
                        val newWebView = WebView(view.context)
                        WebViewSettingsManager.applySettings(newWebView)
                        newWebView.webViewClient = object : WebViewClient() {
                            override fun onCloseWindow(w: WebView?) {
                                super.onCloseWindow(w)
                                popupWebView = null
                                w?.destroy()
                            }
                        }
                        newWebView.webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(v: WebView?, title: String?) {
                                super.onReceivedTitle(v, title)
                                popupTitle = title ?: ""
                            }
                        }
                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                        transport?.webView = newWebView
                        resultMsg?.sendToTarget()
                        popupWebView = newWebView
                        return true
                    }

                    override fun onJsAlert(
                        view: WebView?, url: String?, message: String?, result: JsResult?
                    ): Boolean {
                        val activity = ctx.activityOrNull()
                        if (activity == null || activity.isFinishing) {
                            result?.cancel()
                            return true
                        }
                        AlertDialog.Builder(activity)
                            .setTitle("Alert")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }

                    override fun onJsConfirm(
                        view: WebView?, url: String?, message: String?, result: JsResult?
                    ): Boolean {
                        val activity = ctx.activityOrNull()
                        if (activity == null || activity.isFinishing) {
                            result?.cancel()
                            return true
                        }
                        AlertDialog.Builder(activity)
                            .setTitle("Confirm")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }

                    override fun onJsPrompt(
                        view: WebView?, url: String?, message: String?, defaultValue: String?,
                        result: JsPromptResult?
                    ): Boolean {
                        val activity = ctx.activityOrNull()
                        if (activity == null || activity.isFinishing) {
                            result?.cancel()
                            return true
                        }
                        val input = EditText(activity)
                        input.setText(defaultValue)
                        AlertDialog.Builder(activity)
                            .setTitle("Prompt")
                            .setMessage(message)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                result?.confirm(input.text.toString())
                            }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                            .setOnCancelListener { result?.cancel() }
                            .show()
                        return true
                    }
                }

                setOnLongClickListener {
                    val r = hitTestResult
                    if (r != null && r.type != WebView.HitTestResult.UNKNOWN_TYPE) {
                        onLongPress(r)
                        true
                    } else {
                        false
                    }
                }

                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                    if (downloadUrl.startsWith("blob:")) {
                        // blob: URLs can't go through a download manager; fetch via the page.
                        val js = "(async function(){try{" +
                            "const r=await fetch(${JSONObject.quote(downloadUrl)});" +
                            "const b=await r.blob();" +
                            "const fr=new FileReader();" +
                            "fr.onload=function(){IntentBrowserBlob.onBlob(fr.result);};" +
                            "fr.onerror=function(){IntentBrowserBlob.onBlob('ERROR');};" +
                            "fr.readAsDataURL(b);" +
                            "}catch(e){IntentBrowserBlob.onBlob('ERROR');}})();"
                        evaluateJavascript(js, null)
                        return@setDownloadListener
                    }
                    val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                    val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                    onDownloadStarted?.invoke(fileName, downloadUrl, mimetype, userAgent, cookies)
                    Toast.makeText(ctx, "Download started: $fileName", Toast.LENGTH_LONG).show()
                }

                // Initial navigation (exactly once — updates never re-fire this).
                if (webViewState != null) {
                    restoreState(webViewState)
                    loadedUrlRef.value = url
                } else if (url.isBrowsable()) {
                    loadUrl(url)
                    loadedUrlRef.value = url
                }
            }
            webViewRef = webView
            onWebViewCreated(webView)
            webView
        },
        update = { view ->
            // Sync refs first (callbacks read these, not stale captures).
            urlRef.value = url
            adblockRef.value = isAdBlockerEnabled
            cookieRef.value = blockThirdPartyCookies
            onWebViewCreated(view)

            // Third-party cookie policy applies live.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance()
                    .setAcceptThirdPartyCookies(view, !blockThirdPartyCookies)
            }

            view.settings.userAgentString = if (isDesktopMode) {
                WebViewSettingsManager.desktopUserAgent()
            } else {
                WebViewSettingsManager.mobileUserAgent()
            }
            view.settings.useWideViewPort = true
            view.settings.loadWithOverviewMode = isDesktopMode

            // THE reload-loop fix: only load when the tab asks for a *different*
            // URL than we last loaded AND the view isn't already there.
            // Redirects and SPA pushState navigations never trigger a reload.
            if (url.isBrowsable() && url != loadedUrlRef.value && view.url != url) {
                view.loadUrl(url)
                loadedUrlRef.value = url
            }
        },
        onRelease = { view ->
            try {
                val bundle = Bundle()
                view.saveState(bundle)
                onSaveState(bundle)
            } catch (e: Exception) {
                // Crashed renderers may refuse saveState; tab keeps its URL.
            }
            if (isIncognito) {
                view.destroy()
            } else {
                WebViewPool.recycle(view)
            }
            webViewRef = null
        }
    )

    // Fullscreen video dialog
    if (fullscreenView != null) {
        Dialog(
            onDismissRequest = {
                customViewCallback?.onCustomViewHidden()
                fullscreenView = null
                customViewCallback = null
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val parent = fullscreenView?.parent as? android.view.ViewGroup
                    parent?.removeView(fullscreenView)
                    fullscreenView!!
                }
            )
        }
    }

    // Popup window dialog (OAuth, payments, window.open)
    popupWebView?.let { pw ->
        Dialog(
            onDismissRequest = {
                popupWebView = null
                try {
                    pw.destroy()
                } catch (_: Exception) {
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        popupTitle.ifBlank { "Popup" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        popupWebView = null
                        try {
                            pw.destroy()
                        } catch (_: Exception) {
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close popup")
                    }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { pw }
                )
            }
        }
    }
}
