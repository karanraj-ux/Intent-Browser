package com.intentbrowser.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView

/**
 * Borderless page screenshots: captures exactly what the page shows —
 * no address bar, no buttons, no browser UI. Visible area only; full-page
 * scrolling capture is deliberately not offered (it OOMs on long pages
 * and comes out broken in WebView too often to ship).
 *
 * Must be called on the UI thread (WebView.draw requires it).
 */
object ScreenshotHelper {

    /** Draw the WebView's current visible content into a bitmap. Null on failure. */
    fun capture(webView: WebView): Bitmap? {
        if (webView.width <= 0 || webView.height <= 0) return null
        return try {
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /**
     * Save a bitmap as PNG into Pictures/IntentBrowser via MediaStore.
     * Call off the UI thread. True on success.
     */
    fun saveToPictures(context: Context, bitmap: Bitmap): Boolean {
        return try {
            val resolver = context.contentResolver
            val name = "intent_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/IntentBrowser"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            var ok = false
            resolver.openOutputStream(uri)?.use { out ->
                ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (!ok) {
                try { resolver.delete(uri, null, null) } catch (_: Exception) {}
                return false
            }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }
}
