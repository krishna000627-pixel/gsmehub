package com.krishna.gamehub.core

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    private lateinit var file: File

    fun install(ctx: Context) {
        file = File(ctx.filesDir, "crash.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { append("Uncaught exception on ${t.name}", e) }
            previous?.uncaughtException(t, e)
        }
    }

    fun append(title: String, e: Throwable? = null) {
        synchronized(this) {
            runCatching {
                if (file.exists() && file.length() > 64 * 1024) {
                    file.writeText(file.readText().takeLast(32 * 1024))
                }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val trace = if (e != null) Log.getStackTraceString(e) else ""
                file.appendText("[$stamp] $title\n$trace\n")
            }
        }
    }

    fun read(): String = synchronized(this) { if (file.exists()) file.readText() else "" }

    fun clear() {
        synchronized(this) { file.delete() }
    }
}
