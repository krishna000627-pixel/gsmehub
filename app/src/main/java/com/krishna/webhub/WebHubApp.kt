package com.krishna.webhub

import android.app.Application
import com.krishna.webhub.core.CrashLog
import com.krishna.webhub.data.AppRepo
import com.krishna.webhub.data.Prefs
import com.krishna.webhub.web.WebViewPool

class WebHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        Prefs.init(this)
        AppRepo.init(this)
        WebViewPool.prewarm(this)
    }

    companion object {
        lateinit var instance: WebHubApp
            private set
    }
}
