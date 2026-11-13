package com.krishna.gamehub.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.krishna.gamehub.data.AppEntity
import com.krishna.gamehub.data.Prefs
import com.krishna.gamehub.ui.MainActivity
import com.krishna.gamehub.ui.RunnerActivity

data class ShakeOption(val id: String, val label: String, val hint: String)

object Actions {
    const val DEFAULT = "default"
    const val NONE = "none"
    const val ONSHAKE = "onshake"
    const val LIBRARY = "library"
    const val FLASH = "flashlight"
    const val SHOT = "screenshot"
    const val BACK = "back"
    const val RELOAD = "reload"
    const val APP = "app:"

    val options = listOf(
        ShakeOption(ONSHAKE, "Call window.onShake()", "Lets the page react itself"),
        ShakeOption(LIBRARY, "Open GameHub library", "Jump back to your apps"),
        ShakeOption(FLASH, "Toggle flashlight", "Works anywhere"),
        ShakeOption(SHOT, "Take screenshot", "Saved to Pictures/GameHub"),
        ShakeOption(BACK, "Go back", "Page history, then close"),
        ShakeOption(RELOAD, "Reload app", "Runs the page again"),
        ShakeOption(APP, "Open another app...", "Pick one from your library"),
        ShakeOption(NONE, "Do nothing", "")
    )
    val defaultOption = ShakeOption(DEFAULT, "Use global default", "Follows the Shake tab")

    fun isValid(a: String): Boolean =
        a == DEFAULT || a.startsWith(APP) || options.any { it.id == a }

    fun normalize(a: String): String {
        val x = a.trim().lowercase()
        return if (isValid(x)) x else DEFAULT
    }

    fun label(id: String, apps: List<AppEntity>): String = when {
        id == DEFAULT -> defaultOption.label
        id.startsWith(APP) -> {
            val target = apps.firstOrNull { it.id == id.removePrefix(APP) }
            if (target != null) "Open ${target.name}" else "Open another app..."
        }
        else -> options.firstOrNull { it.id == id }?.label ?: "Do nothing"
    }

    /** Called by ShakeHub on every detected shake while the meter is not being used. */
    fun dispatch(ctx: Context) {
        val runner = RunnerActivity.active
        val action = runner?.effectiveShakeAction() ?: Prefs.defaultShake.value
        perform(ctx, action, runner)
    }

    fun perform(ctx: Context, action: String, runner: RunnerActivity?) {
        when {
            action == NONE || action == DEFAULT -> Unit
            action == ONSHAKE -> runner?.callOnShake()
            action == LIBRARY -> openLibrary(ctx)
            action == FLASH -> Torch.toggle(ctx)
            action == SHOT -> runner?.screenshot()
            action == BACK -> runner?.goBack()
            action == RELOAD -> runner?.reload()
            action.startsWith(APP) -> openApp(ctx, action.removePrefix(APP))
        }
    }

    fun openLibrary(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    fun openApp(ctx: Context, id: String) {
        runCatching {
            ctx.startActivity(
                Intent(ctx, RunnerActivity::class.java)
                    .putExtra(RunnerActivity.EXTRA_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

object Torch {
    @Volatile private var on = false
    private var tracking = false
    private var torchId: String? = null

    private fun mgr(ctx: Context) = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun id(ctx: Context): String? {
        torchId?.let { return it }
        val m = mgr(ctx)
        torchId = runCatching {
            m.cameraIdList.firstOrNull {
                m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        return torchId
    }

    fun available(ctx: Context): Boolean =
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) && id(ctx) != null

    private fun track(ctx: Context) {
        if (tracking) return
        tracking = true
        runCatching {
            mgr(ctx).registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchId) on = enabled
                }
            }, null)
        }
    }

    fun turn(ctx: Context, enable: Boolean): Boolean {
        val cam = id(ctx) ?: return false
        track(ctx)
        return runCatching {
            mgr(ctx).setTorchMode(cam, enable)
            on = enable
        }.isSuccess
    }

    fun toggle(ctx: Context) {
        turn(ctx, !on)
    }
}

object Haptics {
    private fun vib(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun available(ctx: Context): Boolean = vib(ctx)?.hasVibrator() == true

    fun tick(ctx: Context) {
        val v = vib(ctx) ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(12, 60))
        }
    }

    fun buzz(ctx: Context, ms: Long) {
        val v = vib(ctx) ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1, 2000), VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
