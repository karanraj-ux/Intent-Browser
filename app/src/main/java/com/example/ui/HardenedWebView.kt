package com.example.ui

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AdBlocker
import com.example.util.WebViewPool
import java.io.ByteArrayInputStream

@Composable
fun HardenedWebView(
    url: String,
    tabId: String,
    webViewState: Bundle?,
    isFocusMode: Boolean,
    isIncognito: Boolean = false,
    isDesktopMode: Boolean = false,
    onTitleAndLoadingChange: (String, String, Boolean) -> Unit,
    onProgressChange: (Float) -> Unit,
    onSaveState: (Bundle) -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onCreateNewTab: (String) -> Unit = {},
    onDownloadStarted: ((Long, String, String, String, String?, String?) -> Unit)? = null,
    onPageFinishedListener: ((String, WebView) -> Unit)? = null
) {
    val context = LocalContext.current
    var uploadMessage by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var fullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.all { it.value }) {
            pendingGeoCallback?.invoke(pendingGeoOrigin, true, false)
        } else {
            pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
        }
        pendingGeoCallback = null
        pendingGeoOrigin = null
    }

    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val hardwarePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.all { it.value }) {
            pendingPermissionRequest?.grant(pendingPermissionRequest?.resources)
        } else {
            pendingPermissionRequest?.deny()
        }
        pendingPermissionRequest = null
    }
    
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            uploadMessage?.onReceiveValue(uris.toTypedArray())
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val webView = if (isIncognito) {
                WebView(ctx).apply {
                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
                        try {
                            val profileStore = androidx.webkit.ProfileStore.getInstance()
                            val profile = profileStore.getOrCreateProfile("incognito")
                            androidx.webkit.WebViewCompat.setProfile(this, profile.name)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    com.example.util.WebViewSettingsManager.applySettings(this)
                }
            } else {
                WebViewPool.getWebView(ctx)
            }
            
            webView.apply {
                if (webViewState != null) {
                    restoreState(webViewState)
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, urlStr: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, urlStr, favicon)
                        urlStr?.let {
                            if (it != url) {
                                onTitleAndLoadingChange(it, "Loading...", true)
                            }
                        }
                    }

                                                            override fun onPageFinished(view: WebView?, urlStr: String?) {
                        super.onPageFinished(view, urlStr)
                        
                        // Force Enable Zoom (Accessibility)
                        val forceZoomJs = "(function() { " +
                            "  var meta = document.querySelector('meta[name=\"viewport\"]'); " +
                            "  if (meta) { " +
                            "    meta.content = meta.content.replace(/user-scalable=no/ig, 'user-scalable=yes').replace(/maximum-scale=[0-9\\.]+/ig, 'maximum-scale=5.0'); " +
                            "  } else { " +
                            "    meta = document.createElement('meta'); " +
                            "    meta.name = 'viewport'; " +
                            "    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes'; " +
                            "    document.head.appendChild(meta); " +
                            "  } " +
                            "})();"
                        view?.evaluateJavascript(forceZoomJs, null)

                        urlStr?.let {
                            val title = view?.title ?: it
                            onTitleAndLoadingChange(it, title, false)
                            
                            if (isFocusMode) {
                                val js = StringBuilder()
                                js.append("var style = document.createElement('style'); style.type = 'text/css';")
                                
                                if (it.contains("youtube.com")) {
                                    js.append("style.innerHTML += 'ytd-rich-grid-renderer, ytd-watch-next-secondary-results-renderer, #shorts-container { display: none !important; } ';")
                                    js.append("var banner = document.createElement('div'); banner.innerText = 'Focus Mode Active: Use YouTube for study only. Do not waste time.'; banner.style.cssText = 'position:fixed; top:0; left:0; width:100%; background:#d32f2f; color:#fff; text-align:center; padding:10px; z-index:999999; font-weight:bold; font-family:sans-serif;'; document.body.appendChild(banner);")
                                }
                                if (it.contains("reddit.com")) {
                                    js.append("var banner = document.createElement('div'); banner.innerText = 'Focus Mode Active: Use Reddit as a data source only. Avoid doomscrolling.'; banner.style.cssText = 'position:fixed; top:0; left:0; width:100%; background:#d32f2f; color:#fff; text-align:center; padding:10px; z-index:999999; font-weight:bold; font-family:sans-serif;'; document.body.appendChild(banner);")
                                }
                                if (it.contains("instagram.com")) {
                                    js.append("style.innerHTML += 'main[role=\"main\"] > div > div > div > div:first-child { display: none !important; } ';")
                                }
                                if (it.contains("twitter.com") || it.contains("x.com")) {
                                    js.append("style.innerHTML += '[data-testid=\"primaryColumn\"] > div > div:nth-child(4) { display: none !important; } ';")
                                }
                                if (it.contains("facebook.com")) {
                                    js.append("style.innerHTML += '[role=\"feed\"] { display: none !important; } ';")
                                }
                                if (it.contains("reddit.com")) {
                                    js.append("style.innerHTML += 'shreddit-sidebar { display: none !important; } .m-sidebar { display: none !important; } shreddit-feed { display: none !important; } ';")
                                }
                                
                                js.append("document.head.appendChild(style);")
                                view?.evaluateJavascript(js.toString(), null)
                            }
                            
                            onPageFinishedListener?.invoke(it, view!!)
                        }
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            if (error?.errorCode == WebViewClient.ERROR_HOST_LOOKUP || error?.errorCode == WebViewClient.ERROR_CONNECT || error?.errorCode == WebViewClient.ERROR_TIMEOUT) {
                                view?.loadUrl("file:///android_asset/offline.html")
                            }
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val urlStr = request?.url?.toString() ?: return false
                        
                        if (isFocusMode && AdBlocker.isDistracting(urlStr)) {
                            view?.loadUrl("file:///android_asset/focus_blocked.html")
                            return true
                        }
                        
                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://") || urlStr.startsWith("file://") || urlStr.startsWith("app://")) {
                            return false
                        }
                        return try {
                            val intent = if (urlStr.startsWith("intent:")) {
                                android.content.Intent.parseUri(urlStr, android.content.Intent.URI_INTENT_SCHEME).apply {
                                    addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                                    component = null
                                    selector = null
                                }
                            } else {
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(urlStr))
                            }
                            if (AdBlocker.isAdOrTracker(intent.dataString ?: "")) {
                                return true // Block tracking intent
                            }
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            view?.context?.startActivity(intent)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }

                                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val requestUrl = request?.url?.toString()
                        if (requestUrl != null) {
                            if (requestUrl.contains("ai.studio") || requestUrl.contains("gemini.google.com") || requestUrl.contains("wayback")) {
                                return super.shouldInterceptRequest(view, request)
                            }
                            if (isFocusMode) {
                                if (requestUrl.endsWith(".mp4") || requestUrl.endsWith(".webm") || requestUrl.contains("youtube.com/shorts")) {
                                    return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                                }
                            }
                            if (AdBlocker.isAdOrTracker(requestUrl) || AdBlocker.isAdultContent(requestUrl) || requestUrl.contains("pornhub.com") || requestUrl.contains("xvideos.com")) {
                                if (request.isForMainFrame) {
                                    try {
                                        return WebResourceResponse("text/html", "UTF-8", ctx.assets.open("offline.html"))
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                                return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame == true && errorResponse?.statusCode ?: 200 >= 400) {
                            view?.loadUrl("file:///android_asset/offline.html")
                        }
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        val builder = AlertDialog.Builder(ctx)
                        builder.setMessage("SSL Certificate error. Do you want to continue anyway?")
                        builder.setPositiveButton("Continue") { _, _ -> handler?.proceed() }
                        builder.setNegativeButton("Cancel") { _, _ -> handler?.cancel() }
                        val dialog = builder.create()
                        dialog.show()
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChange(newProgress / 100f)
                    }
                    
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        uploadMessage = filePathCallback
                        fileChooserLauncher.launch("*/*")
                        return true
                    }

                    override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                        super.onShowCustomView(view, callback)
                        fullscreenView = view
                        customViewCallback = callback
                    }

                    override fun onHideCustomView() {
                        super.onHideCustomView()
                        fullscreenView = null
                        customViewCallback = null
                    }
                    
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        permissionLauncher.launch(arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
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
                            request?.grant(request.resources)
                            pendingPermissionRequest = null
                        }
                    }

                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                        val result = view?.hitTestResult
                        val data = result?.extra
                        if (data != null) {
                            onCreateNewTab(data)
                        } else {
                            // Fallback if data is null, open a new tab with blank for intercepting
                            onCreateNewTab("about:blank")
                        }
                        return false
                    }

                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        AlertDialog.Builder(ctx)
                            .setTitle("Alert")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                            .setCancelable(false)
                            .create()
                            .show()
                        return true
                    }

                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        AlertDialog.Builder(ctx)
                            .setTitle("Confirm")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                            .setCancelable(false)
                            .create()
                            .show()
                        return true
                    }

                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                        val input = android.widget.EditText(ctx)
                        input.setText(defaultValue)
                        AlertDialog.Builder(ctx)
                            .setTitle("Prompt")
                            .setMessage(message)
                            .setView(input)
                            .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm(input.text.toString()) }
                            .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                            .setCancelable(false)
                            .create()
                            .show()
                        return true
                    }
                }

                setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                    val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                    val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                    onDownloadStarted?.invoke(System.currentTimeMillis(), fileName, downloadUrl, mimetype, userAgent, cookies)
                    Toast.makeText(ctx, "Download started: $fileName", Toast.LENGTH_LONG).show()
                }
            }
        },
        update = { view ->
            onWebViewCreated(view)
            
            // Apply Desktop Mode
            if (isDesktopMode) {
                view.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
                view.settings.useWideViewPort = true
                view.settings.loadWithOverviewMode = true
            } else {
                view.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36 IntentBrowser/1.0"
            }
            
            // Apply Focus Mode logic
            view.settings.loadsImagesAutomatically = !isFocusMode
            view.settings.blockNetworkImage = isFocusMode
            
            if (view.url != url && url != "app://newtab" && url != "about:blank") {
                view.loadUrl(url)
            }
        },
        onRelease = { view ->
            val bundle = Bundle()
            view.saveState(bundle)
            onSaveState(bundle)
            if (isIncognito) {
                view.destroy()
            } else {
                WebViewPool.recycle(view)
            }
        }
    )

    if (fullscreenView != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                customViewCallback?.onCustomViewHidden()
                fullscreenView = null
                customViewCallback = null
            },
            properties = androidx.compose.ui.window.DialogProperties(
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
}
