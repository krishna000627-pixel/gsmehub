@file:OptIn(ExperimentalMaterial3Api::class)

package com.krishna.webhub.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.krishna.webhub.core.Actions
import com.krishna.webhub.core.CrashLog
import com.krishna.webhub.core.ShakeHub
import com.krishna.webhub.data.AppEntity
import com.krishna.webhub.data.AppRepo
import com.krishna.webhub.data.Prefs
import com.krishna.webhub.web.AppWebClient
import com.krishna.webhub.web.WebHubBridge
import com.krishna.webhub.web.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class RunnerActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ID = "app_id"

        @Volatile
        var active: RunnerActivity? = null

        fun start(ctx: Context, id: String) {
            ctx.startActivity(Intent(ctx, RunnerActivity::class.java).putExtra(EXTRA_ID, id))
        }
    }

    lateinit var web: WebView
        private set

    var currentApp by mutableStateOf<AppEntity?>(null)
        private set
    var shakeRegistered = false

    private var fullscreen by mutableStateOf(false)
    private var overlayVisible by mutableStateOf(true)
    private var overlayShot by mutableStateOf<ImageBitmap?>(null)
    private var pickerOpen by mutableStateOf(false)
    private var pageReady = false
    private var scriptHandler: ScriptHandler? = null
    private var observeJob: Job? = null

    // ---- activity result plumbing for the bridge ----
    private var saveCb: ((Boolean, String) -> Unit)? = null
    private var savePayload: ByteArray? = null
    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val cb = saveCb
        val bytes = savePayload
        saveCb = null
        savePayload = null
        if (cb == null || bytes == null) return@registerForActivityResult
        if (uri == null) {
            cb(false, "Cancelled")
            return@registerForActivityResult
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }.isSuccess
            withContext(Dispatchers.Main) { cb(ok, if (ok) "" else "Could not write file") }
        }
    }

    private var readCb: ((String?, String?) -> Unit)? = null
    private val readLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = readCb
        readCb = null
        if (cb == null) return@registerForActivityResult
        if (uri == null) {
            cb(null, "Cancelled")
            return@registerForActivityResult
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val res = runCatching {
                var name = "file"
                var size = -1L
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = c.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = c.getString(n) ?: name
                        if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    }
                }
                if (size > 8L * 1024 * 1024) error("File too large (max 8 MB)")
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (bytes.size > 8 * 1024 * 1024) error("File too large (max 8 MB)")
                JSONObject().put("name", name).put("type", contentResolver.getType(uri) ?: "")
                    .put("size", bytes.size).put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP)).toString()
            }
            withContext(Dispatchers.Main) { cb(res.getOrNull(), res.exceptionOrNull()?.message) }
        }
    }

    private val notifyLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private var fileCb: ValueCallback<Array<Uri>>? = null
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val cb = fileCb
        fileCb = null
        cb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    private val chrome = object : WebChromeClient() {
        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            AlertDialog.Builder(this@RunnerActivity).setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            AlertDialog.Builder(this@RunnerActivity).setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onJsPrompt(
            view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult
        ): Boolean {
            val input = EditText(this@RunnerActivity).apply {
                setText(defaultValue ?: "")
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            }
            AlertDialog.Builder(this@RunnerActivity).setMessage(message).setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onShowFileChooser(
            webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
        ): Boolean {
            fileCb?.onReceiveValue(null)
            fileCb = callback
            val type = params.acceptTypes.firstOrNull { it.contains('/') } ?: "*/*"
            return runCatching { fileLauncher.launch(type); true }.getOrElse {
                fileCb = null
                callback.onReceiveValue(null)
                false
            }
        }
    }

    // ---- lifecycle ----

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        web = WebViewPool.acquire(this)
        web.webChromeClient = chrome
        setContent { WebHubTheme { RunnerScreen() } }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        active = this
        web.onResume()
        ShakeHub.acquire("runner")
    }

    override fun onPause() {
        if (active === this) active = null
        saveShot()
        web.onPause()
        ShakeHub.release("runner")
        super.onPause()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        scriptHandler?.remove()
        fileCb?.onReceiveValue(null)
        WebViewPool.release(this, web)
        super.onDestroy()
    }

    private fun handleIntent(i: Intent) {
        val id = i.getStringExtra(EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        if (id == currentApp?.id && pageReady) return
        launchApp(id)
    }

    private fun launchApp(id: String) {
        lifecycleScope.launch {
            val app = AppRepo.dao.get(id)
            if (app == null) {
                Toast.makeText(this@RunnerActivity, "App not found", Toast.LENGTH_SHORT).show()
                if (currentApp == null) finish()
                return@launch
            }
            if (currentApp != null && currentApp?.id != id) saveShot()
            pageReady = false
            shakeRegistered = false
            overlayVisible = true
            overlayShot = null
            currentApp = app
            fullscreen = app.fullscreen
            applyFullscreen()
            updateKeepAwake(app.keepAwake)
            applyOrientation(app.orientation)

            val client = AppWebClient(
                app.id,
                allowInternet = { currentApp?.allowInternet == true },
                onCommit = { hideOverlay() },
                onDone = { pageReady = true; hideOverlay() },
                onExternal = { openExternal(it) }
            )
            web.stopLoading()
            web.webViewClient = client
            web.removeJavascriptInterface(WebHubBridge.NAME)
            web.addJavascriptInterface(WebHubBridge(this@RunnerActivity, app.id), WebHubBridge.NAME)
            scriptHandler?.remove()
            scriptHandler = null
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                scriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    web, WebHubBridge.SHIM, setOf("https://${client.host}")
                )
            }
            web.clearHistory()
            val path = app.entry.split('/').joinToString("/") { Uri.encode(it) }
            web.loadUrl("https://${client.host}/apps/${app.id}/$path")

            launch(Dispatchers.IO) { AppRepo.dao.touch(app.id, System.currentTimeMillis()) }
            observeJob?.cancel()
            observeJob = launch {
                AppRepo.dao.observe(app.id).collect { e -> if (e != null) currentApp = e }
            }
            launch {
                overlayShot = withContext(Dispatchers.IO) {
                    File(AppRepo.shotsDir, "${app.id}.jpg").takeIf { it.exists() }
                        ?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
                }
            }
        }
    }

    private fun hideOverlay() {
        overlayVisible = false
    }

    private fun openExternal(uri: Uri) {
        val scheme = uri.scheme ?: return
        if (scheme !in listOf("http", "https", "mailto", "tel")) return
        val net = scheme == "http" || scheme == "https"
        if (net && currentApp?.allowInternet != true) {
            Toast.makeText(this, "Internet is off for this app", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    // ---- window ----

    private fun applyFullscreen() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun updateKeepAwake(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun applyOrientation(mode: String) {
        requestedOrientation = when (mode) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "sensor" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---- API used by the bridge and shake actions ----

    fun currentUrlBelongsTo(id: String): Boolean =
        web.url?.startsWith("https://${AppRepo.hostOf(id)}/") == true

    fun evalJs(js: String) {
        web.evaluateJavascript(js, null)
    }

    fun effectiveShakeAction(): String {
        if (shakeRegistered) return Actions.ONSHAKE
        val a = currentApp?.shakeAction ?: Actions.DEFAULT
        return if (a == Actions.DEFAULT) Prefs.defaultShake.value else a
    }

    fun callOnShake() {
        evalJs(
            "(function(){try{if(typeof window.onShake==='function')window.onShake();" +
                "window.dispatchEvent(new CustomEvent('webhub:shake'));}catch(e){}})()"
        )
    }

    fun goBack() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    fun reload() {
        web.reload()
    }

    fun screenshot() {
        val bmp = WebViewPool.capture(web, web.width) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val ok = runCatching { saveToGallery(bmp) }.onFailure { CrashLog.append("screenshot failed", it) }.isSuccess
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RunnerActivity, if (ok) "Screenshot saved" else "Could not save screenshot", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToGallery(bmp: Bitmap) {
        val name = "WebHub_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WebHub")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: error("insert failed")
            contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: error("no storage")
            File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun saveShot() {
        val app = currentApp ?: return
        if (overlayVisible || !pageReady || !::web.isInitialized) return
        val bmp = WebViewPool.capture(web) ?: return
        thread {
            runCatching {
                File(AppRepo.shotsDir, "${app.id}.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            }
            bmp.recycle()
        }
    }

    fun openOtherApp(id: String, cb: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val exists = AppRepo.dao.get(id) != null
            if (exists) {
                if (id == currentApp?.id) reload() else launchApp(id)
            }
            cb(exists)
        }
    }

    fun pickToSave(name: String, bytes: ByteArray, cb: (Boolean, String) -> Unit) {
        saveCb = cb
        savePayload = bytes
        saveLauncher.launch(name)
    }

    fun pickToRead(cb: (String?, String?) -> Unit) {
        readCb = cb
        readLauncher.launch(arrayOf("*/*"))
    }

    fun askNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ---- UI ----

    @Composable
    private fun RunnerScreen() {
        val bg = MaterialTheme.colorScheme.background
        val insets = if (fullscreen) WindowInsets.ime else WindowInsets.safeDrawing
        BackHandler { goBack() }
        Box(Modifier.fillMaxSize().background(bg)) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
                AndroidView(
                    factory = {
                        (web.parent as? ViewGroup)?.removeView(web)
                        web
                    },
                    modifier = Modifier.fillMaxSize()
                )
                AnimatedVisibility(
                    visible = overlayVisible,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(180))
                ) {
                    Box(Modifier.fillMaxSize().background(bg)) {
                        overlayShot?.let {
                            Image(
                                it, null, Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop, alignment = Alignment.TopCenter
                            )
                        }
                    }
                }
            }
            MiniControls(
                fullscreen = fullscreen,
                onReload = { reload() },
                onShake = { pickerOpen = true },
                onFullscreen = {
                    fullscreen = !fullscreen
                    applyFullscreen()
                },
                onClose = { finish() }
            )
            if (pickerOpen) {
                val apps by AppRepo.apps.collectAsState(initial = emptyList())
                val app = currentApp
                ShakeActionPicker(
                    current = app?.shakeAction ?: Actions.DEFAULT,
                    apps = apps,
                    includeDefault = true,
                    excludeId = app?.id,
                    onPick = { pick ->
                        pickerOpen = false
                        if (app != null) lifecycleScope.launch { AppRepo.dao.update(app.copy(shakeAction = pick)) }
                    },
                    onDismiss = { pickerOpen = false }
                )
            }
        }
    }
}

@Composable
private fun MiniControls(
    fullscreen: Boolean,
    onReload: () -> Unit,
    onShake: () -> Unit,
    onFullscreen: () -> Unit,
    onClose: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val sizePx = with(density) { 48.dp.toPx() }
    BackHandler(enabled = expanded) { expanded = false }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val p = pos ?: Offset(maxW - sizePx - with(density) { 8.dp.toPx() }, maxH * 0.42f)
        val expandLeft = p.x + sizePx / 2 > maxW / 2

        Box(
            Modifier
                .offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                .size(48.dp)
                .pointerInput(maxW, maxH) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val cur = pos ?: p
                        pos = Offset(
                            (cur.x + drag.x).coerceIn(0f, maxW - sizePx),
                            (cur.y + drag.y).coerceIn(0f, maxH - sizePx)
                        )
                    }
                }
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                shadowElevation = 6.dp,
                modifier = Modifier.size(48.dp).clickable { expanded = !expanded }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (expanded) Icons.Rounded.Close else Icons.Rounded.Bolt,
                        contentDescription = "Controls",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Box(
                Modifier
                    .wrapContentSize(
                        align = if (expandLeft) Alignment.CenterEnd else Alignment.CenterStart,
                        unbounded = true
                    )
                    .offset(x = if (expandLeft) (-56).dp else 56.dp)
            ) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(tween(90)) + shrinkHorizontally(tween(120))
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp
                    ) {
                        Row {
                            MiniAction(Icons.Rounded.Refresh, "Reload") { expanded = false; onReload() }
                            MiniAction(Icons.Rounded.Vibration, "Shake action") { expanded = false; onShake() }
                            MiniAction(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                "Fullscreen"
                            ) { expanded = false; onFullscreen() }
                            MiniAction(Icons.Rounded.Close, "Close") { expanded = false; onClose() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}
