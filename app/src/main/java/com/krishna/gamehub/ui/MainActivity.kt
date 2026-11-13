package com.krishna.gamehub.ui

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.krishna.gamehub.core.ShakeHub
import com.krishna.gamehub.core.ShakeService
import com.krishna.gamehub.data.Prefs

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        setContent { GamehubTheme { MainScreen(vm) } }
        if (Prefs.backgroundShake.value) ShakeService.start(this)
    }

    override fun onResume() {
        super.onResume()
        ShakeHub.acquire("main")
    }

    override fun onPause() {
        ShakeHub.release("main")
        super.onPause()
    }
}
