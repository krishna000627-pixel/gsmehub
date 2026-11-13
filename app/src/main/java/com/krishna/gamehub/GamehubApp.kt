package com.krishna.gamehub

import android.app.Application
import com.krishna.gamehub.core.CrashLog
import com.krishna.gamehub.data.AppRepo
import com.krishna.gamehub.data.Prefs
import com.krishna.gamehub.web.WebViewPool

class GamehubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        Prefs.init(this)
        AppRepo.init(this)
        WebViewPool.prewarm(this)
    }

    companion object {
        lateinit var instance: GamehubApp
            private set
    }
}
