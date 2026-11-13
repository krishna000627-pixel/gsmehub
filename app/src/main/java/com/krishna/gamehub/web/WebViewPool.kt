package com.krishna.gamehub.web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import com.krishna.gamehub.data.AppRepo
import kotlin.math.min

/** One WebView created in Application.onCreate and reused for every run. */
object WebViewPool {
    private lateinit var app: Application
    private lateinit var wrapper: MutableContextWrapper
    private var web: WebView? = null
    private var owner: Activity? = null

    fun prewarm(a: Application) {
        app = a
        wrapper = MutableContextWrapper(a)
        web = create()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val w = WebView(wrapper)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
        }
        w.setBackgroundColor(Color.TRANSPARENT)
        w.overScrollMode = View.OVER_SCROLL_NEVER
        w.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        return w
    }

    fun acquire(activity: Activity): WebView {
        val w = web ?: create().also { web = it }
        owner = activity
        wrapper.baseContext = activity
        (w.parent as? ViewGroup)?.removeView(w)
        return w
    }

    fun release(activity: Activity, w: WebView) {
        if (owner !== activity) return
        owner = null
        (w.parent as? ViewGroup)?.removeView(w)
        w.stopLoading()
        w.removeJavascriptInterface(GamehubBridge.NAME)
        w.loadUrl("about:blank")
        w.clearHistory()
        wrapper.baseContext = app
    }

    fun clearOrigin(id: String) {
        runCatching { WebStorage.getInstance().deleteOrigin("https://${AppRepo.hostOf(id)}") }
        runCatching { CookieManager.getInstance().flush() }
    }

    fun clearCache() {
        runCatching { web?.clearCache(true) }
    }

    fun capture(w: WebView, maxWidth: Int = 720): Bitmap? {
        if (w.width <= 0 || w.height <= 0) return null
        val scale = min(1f, maxWidth / w.width.toFloat())
        val bmp = Bitmap.createBitmap(
            (w.width * scale).toInt().coerceAtLeast(1),
            (w.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bmp)
        c.scale(scale, scale)
        return runCatching { w.draw(c); bmp }.getOrNull()
    }
}
