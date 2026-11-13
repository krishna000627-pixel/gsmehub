package com.krishna.gamehub.web

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.krishna.gamehub.R
import com.krishna.gamehub.core.CrashLog
import com.krishna.gamehub.core.Haptics
import com.krishna.gamehub.core.Torch
import com.krishna.gamehub.ui.RunnerActivity
import org.json.JSONArray
import org.json.JSONObject

/** Exposed as window.__WH. The page-facing API is window.Gamehub (see SHIM). */
class GamehubBridge(private val act: RunnerActivity, private val appId: String) {
    private val main = Handler(Looper.getMainLooper())

    private fun supported(): List<String> = buildList {
        if (Haptics.available(act)) add("vibrate")
        if (Torch.available(act)) add("flashlight")
        addAll(
            listOf(
                "toast", "share", "clipboardGet", "clipboardSet", "saveFile", "readFile",
                "notify", "keepAwake", "setOrientation", "appInfo", "openApp", "registerShake"
            )
        )
    }

    @JavascriptInterface
    fun features(): String = JSONArray(supported()).toString()

    @JavascriptInterface
    fun call(method: String, argsJson: String, cbId: String) {
        val cb = cbId.filter { it.isDigit() }
        main.post {
            if (act.isFinishing || !act.currentUrlBelongsTo(appId)) {
                reject(cb, "Not allowed")
                return@post
            }
            try {
                dispatch(method, JSONArray(argsJson), cb)
            } catch (e: Throwable) {
                CrashLog.append("bridge $method failed", e)
                reject(cb, e.message ?: "Error")
            }
        }
    }

    private fun resolve(cb: String, jsonValue: String) =
        act.evalJs("window.__whResolve('$cb',true,$jsonValue)")

    private fun reject(cb: String, msg: String) =
        act.evalJs("window.__whResolve('$cb',false,${JSONObject.quote(msg)})")

    private fun dispatch(method: String, a: JSONArray, cb: String) {
        if (method !in supported()) {
            reject(cb, "Unsupported: $method")
            return
        }
        when (method) {
            "vibrate" -> {
                Haptics.buzz(act, a.optLong(0, 50))
                resolve(cb, "true")
            }
            "flashlight" -> {
                val ok = Torch.turn(act, a.optBoolean(0, true))
                if (ok) resolve(cb, "true") else reject(cb, "Flashlight unavailable")
            }
            "toast" -> {
                Toast.makeText(act, a.optString(0), Toast.LENGTH_SHORT).show()
                resolve(cb, "true")
            }
            "share" -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, a.optString(0))
                act.startActivity(Intent.createChooser(send, null))
                resolve(cb, "true")
            }
            "clipboardGet" -> {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = cm.primaryClip
                val text = if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(act)?.toString() ?: ""
                } else ""
                resolve(cb, JSONObject.quote(text))
            }
            "clipboardSet" -> {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("GameHub", a.optString(0)))
                resolve(cb, "true")
            }
            "saveFile" -> {
                val name = a.optString(0).ifBlank { "file" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val bytes = Base64.decode(a.optString(1), Base64.DEFAULT)
                act.pickToSave(name, bytes) { ok, msg -> if (ok) resolve(cb, "true") else reject(cb, msg) }
            }
            "readFile" -> act.pickToRead { json, err ->
                if (json != null) resolve(cb, json) else reject(cb, err ?: "Cancelled")
            }
            "notify" -> {
                if (!NotificationManagerCompat.from(act).areNotificationsEnabled()) {
                    act.askNotifyPermission()
                    reject(cb, "Notification permission needed")
                } else {
                    notify(a.optString(0), a.optString(1))
                    resolve(cb, "true")
                }
            }
            "keepAwake" -> {
                act.updateKeepAwake(a.optBoolean(0, true))
                resolve(cb, "true")
            }
            "setOrientation" -> {
                act.applyOrientation(a.optString(0, "auto"))
                resolve(cb, "true")
            }
            "appInfo" -> {
                val app = act.currentApp
                resolve(
                    cb,
                    JSONObject().put("id", appId).put("name", app?.name ?: "").put("version", app?.version ?: "")
                        .put("gamehub", "1.0.0").toString()
                )
            }
            "openApp" -> act.openOtherApp(a.optString(0)) { ok ->
                if (ok) resolve(cb, "true") else reject(cb, "App not found")
            }
            "registerShake" -> {
                act.shakeRegistered = a.optBoolean(0, true)
                resolve(cb, "true")
            }
        }
    }

    private fun notify(title: String, body: String) {
        val nm = act.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App notifications", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            act, appId.hashCode(),
            Intent(act, RunnerActivity::class.java).putExtra(RunnerActivity.EXTRA_ID, appId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(act, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title.ifBlank { act.currentApp?.name ?: "GameHub" })
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(act).notify((System.currentTimeMillis() % 100000).toInt(), n)
        }
    }

    companion object {
        const val NAME = "__WH"
        private const val CHANNEL = "app_notifications"

        val SHIM = """
(function () {
  if (window.Gamehub || !window.__WH) return;
  var N = window.__WH, cbs = {}, seq = 0, sup = [];
  try { sup = JSON.parse(N.features()); } catch (e) {}
  window.__whResolve = function (id, ok, v) {
    var c = cbs[id]; if (!c) return; delete cbs[id];
    if (ok) c.res(v); else c.rej(new Error(v));
  };
  function call(m, a) {
    return new Promise(function (res, rej) {
      var id = String(++seq); cbs[id] = { res: res, rej: rej };
      try { N.call(m, JSON.stringify(a || []), id); } catch (e) { delete cbs[id]; rej(e); }
    });
  }
  var api = {
    version: '1.0.0',
    features: sup.slice(),
    has: function (n) { return sup.indexOf(n) >= 0; }
  };
  sup.forEach(function (m) {
    api[m] = function () { return call(m, Array.prototype.slice.call(arguments)); };
  });
  Object.defineProperty(window, 'Gamehub', { value: Object.freeze(api), configurable: false });
})();
""".trimIndent()
    }
}
