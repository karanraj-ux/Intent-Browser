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
            WebViewPool.getWebView(ctx).apply {
                if (webViewState != null) {
                    restoreState(webViewState)
                }
                
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.setGeolocationEnabled(true)
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                
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
                        urlStr?.let {
                            val title = view?.title ?: it
                            onTitleAndLoadingChange(it, title, false)
                            
                            if (isFocusMode) {
                                val js = StringBuilder()
                                js.append("var style = document.createElement('style'); style.type = 'text/css';")
                                
                                if (it.contains("youtube.com")) {
                                    js.append("style.innerHTML += 'ytd-rich-grid-renderer, ytd-watch-next-secondary-results-renderer, #shorts-container { display: none !important; } ';")
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
                            if (isFocusMode) {
                                if (requestUrl.endsWith(".mp4") || requestUrl.endsWith(".webm") || requestUrl.contains("youtube.com/shorts")) {
                                    return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                                }
                            }
                            if (AdBlocker.isAdOrTracker(requestUrl) || AdBlocker.isAdultContent(requestUrl) || requestUrl.contains("pornhub.com") || requestUrl.contains("xvideos.com")) {
                                return WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
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
                        // Advanced Fullscreen Video Handling could be implemented here
                    }

                    override fun onHideCustomView() {
                        super.onHideCustomView()
                    }
                    
                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                        callback?.invoke(origin, true, false)
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
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
        }
    )
}
