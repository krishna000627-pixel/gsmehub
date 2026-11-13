package com.krishna.gamehub.web

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.krishna.gamehub.data.AppRepo
import java.io.ByteArrayInputStream

/**
 * Serves https://<id>.appassets.androidplatform.net/apps/<id>/ from filesDir/apps/<id>/.
 * Each app gets its own origin, so localStorage and IndexedDB are isolated.
 */
class AppWebClient(
    appId: String,
    private val allowInternet: () -> Boolean,
    private val onCommit: () -> Unit,
    private val onDone: () -> Unit,
    private val onExternal: (Uri) -> Unit
) : WebViewClient() {
    val host = AppRepo.hostOf(appId)

    private val loader = WebViewAssetLoader.Builder()
        .setDomain(host)
        .addPathHandler(
            "/apps/$appId/",
            WebViewAssetLoader.InternalStoragePathHandler(AppRepo.ctx, AppRepo.dirOf(appId))
        )
        .build()

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        if (uri.host.equals(host, ignoreCase = true)) {
            val r = loader.shouldInterceptRequest(uri) ?: return empty(404, "Not Found")
            return withCsp(r)
        }
        return when (uri.scheme) {
            "data", "blob", "about" -> null
            else -> if (allowInternet()) null else empty(403, "Blocked")
        }
    }

    private fun withCsp(r: WebResourceResponse): WebResourceResponse {
        if (allowInternet() || r.mimeType?.contains("html", true) != true) return r
        val h = HashMap<String, String>(r.responseHeaders ?: emptyMap())
        h["Content-Security-Policy"] = CSP
        r.responseHeaders = h
        return r
    }

    private fun empty(code: Int, reason: String) = WebResourceResponse(
        "text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.host.equals(host, ignoreCase = true)) return false
        if (uri.scheme == "blob" || uri.scheme == "data" || uri.scheme == "about") return false
        onExternal(uri)
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url != null && Uri.parse(url).host.equals(host, ignoreCase = true)) {
            view.evaluateJavascript(GamehubBridge.SHIM, null)
        }
    }

    override fun onPageCommitVisible(view: WebView, url: String?) = onCommit()

    override fun onPageFinished(view: WebView, url: String?) = onDone()

    companion object {
        const val CSP = "default-src 'self' data: blob: 'unsafe-inline' 'unsafe-eval'; " +
            "connect-src 'self' data: blob:; frame-src 'self' data: blob:"
    }
}
