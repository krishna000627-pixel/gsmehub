package com.krishna.gamehub.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

object Prefs {
    private lateinit var sp: SharedPreferences

    private val themeFlow = MutableStateFlow("dark")
    private val dynamicFlow = MutableStateFlow(true)
    private val defaultShakeFlow = MutableStateFlow("library")
    private val sensitivityFlow = MutableStateFlow(13f)
    private val backgroundFlow = MutableStateFlow(false)
    private val autoStartFlow = MutableStateFlow(false)

    val theme: StateFlow<String> = themeFlow
    val dynamicColor: StateFlow<Boolean> = dynamicFlow
    val defaultShake: StateFlow<String> = defaultShakeFlow
    val sensitivity: StateFlow<Float> = sensitivityFlow
    val backgroundShake: StateFlow<Boolean> = backgroundFlow
    val autoStartDone: StateFlow<Boolean> = autoStartFlow

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("gamehub_prefs", Context.MODE_PRIVATE)
        themeFlow.value = sp.getString("theme", "dark") ?: "dark"
        dynamicFlow.value = sp.getBoolean("dynamic", true)
        defaultShakeFlow.value = sp.getString("default_shake", "library") ?: "library"
        sensitivityFlow.value = sp.getFloat("sensitivity", 13f)
        backgroundFlow.value = sp.getBoolean("background_shake", false)
        autoStartFlow.value = sp.getBoolean("auto_start_done", false)
    }

    fun updateTheme(v: String) {
        themeFlow.value = v
        sp.edit().putString("theme", v).apply()
    }

    fun updateDynamicColor(v: Boolean) {
        dynamicFlow.value = v
        sp.edit().putBoolean("dynamic", v).apply()
    }

    fun updateDefaultShake(v: String) {
        defaultShakeFlow.value = v
        sp.edit().putString("default_shake", v).apply()
    }

    fun updateSensitivity(v: Float) {
        sensitivityFlow.value = v
        sp.edit().putFloat("sensitivity", v).apply()
    }

    fun updateBackgroundShake(v: Boolean) {
        backgroundFlow.value = v
        sp.edit().putBoolean("background_shake", v).apply()
    }

    fun updateAutoStartDone(v: Boolean) {
        autoStartFlow.value = v
        sp.edit().putBoolean("auto_start_done", v).apply()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("theme", themeFlow.value)
        .put("dynamicColor", dynamicFlow.value)
        .put("defaultShake", defaultShakeFlow.value)
        .put("sensitivity", sensitivityFlow.value.toDouble())

    fun restore(o: JSONObject) {
        if (o.has("theme")) updateTheme(o.optString("theme", "dark"))
        if (o.has("dynamicColor")) updateDynamicColor(o.optBoolean("dynamicColor", true))
        if (o.has("defaultShake")) updateDefaultShake(o.optString("defaultShake", "library"))
        if (o.has("sensitivity")) updateSensitivity(o.optDouble("sensitivity", 13.0).toFloat().coerceIn(10f, 30f))
    }
}
