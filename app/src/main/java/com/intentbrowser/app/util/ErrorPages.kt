package com.intentbrowser.app.util

import android.webkit.WebViewClient

/**
 * Local, honest error pages. A browser must distinguish "you're offline"
 * from "this site 404'd" — the old code showed the offline page for every
 * 4xx/5xx, which is wrong.
 */
object ErrorPages {

    fun page(title: String, heading: String, message: String, url: String?): String {
        val retry = if (url != null) {
            "<a class=\"btn\" href=\"$url\">Try again</a>"
        } else ""
        return """
        <!DOCTYPE html>
        <html lang="en"><head><meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>${escape(title)}</title>
        <style>
          body{font-family:sans-serif;background:#f8f9fa;color:#343a40;display:flex;
            flex-direction:column;align-items:center;justify-content:center;
            min-height:100vh;margin:0;text-align:center;padding:24px;box-sizing:border-box}
          .icon{font-size:64px;margin-bottom:16px}
          h1{font-size:22px;margin:0 0 8px}
          p{font-size:15px;color:#6c757d;max-width:420px;line-height:1.5}
          .url{font-size:13px;color:#868e96;word-break:break-all;max-width:420px;margin-top:8px}
          .btn{display:inline-block;margin-top:20px;padding:12px 28px;background:#1a73e8;
            color:#fff;text-decoration:none;border-radius:24px;font-weight:600}
        </style></head>
        <body>
          <div class="icon">🌐</div>
          <h1>${escape(heading)}</h1>
          <p>${escape(message)}</p>
          ${if (url != null) "<div class=\"url\">${escape(url)}</div>" else ""}
          $retry
        </body></html>
        """.trimIndent()
    }

    fun forNetError(errorCode: Int, url: String?): String {
        val (heading, message) = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP ->
                "Couldn't find that site" to
                    "The address doesn't seem to exist. Check the spelling, or try searching for it instead."
            WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT ->
                "Couldn't connect" to
                    "The site took too long to respond. It may be down, or your connection may have dropped."
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                "Secure connection failed" to
                    "This site's security certificate couldn't be verified, so the connection was stopped to protect you."
            else ->
                "This page couldn't be loaded" to
                    "Something went wrong while loading the page. Check your connection and try again."
        }
        return page("Couldn't load page", heading, message, url)
    }

    fun forHttpError(statusCode: Int, url: String?): String {
        val (heading, message) = when (statusCode) {
            401, 403 -> "Access denied ($statusCode)" to
                "The site refused to show this page. You may need to sign in."
            404 -> "Page not found (404)" to
                "This page doesn't exist on the site. Check the address or go back."
            in 500..599 -> "Site error ($statusCode)" to
                "The site's server ran into a problem. This usually fixes itself — try again in a bit."
            else -> "Couldn't load page ($statusCode)" to
                "The server returned an error. Try again, or go back."
        }
        return page("Error $statusCode", heading, message, url)
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
}
