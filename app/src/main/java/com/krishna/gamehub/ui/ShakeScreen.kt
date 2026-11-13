@file:OptIn(ExperimentalMaterial3Api::class)

package com.krishna.gamehub.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.krishna.gamehub.core.Actions
import com.krishna.gamehub.core.ShakeHub
import com.krishna.gamehub.core.ShakeService
import com.krishna.gamehub.data.Prefs
import kotlinx.coroutines.delay

/** Shared by the Shake and Settings tabs. Returns a handler for the background switch. */
@Composable
fun rememberBgShakeToggle(): (Boolean) -> Unit {
    val ctx = LocalContext.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(ctx) {
        { on: Boolean ->
            Prefs.updateBackgroundShake(on)
            if (on) {
                if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                    perm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                ShakeService.start(ctx)
            } else {
                ShakeService.stop(ctx)
            }
        }
    }
}

@Composable
fun ShakeScreen(vm: MainViewModel, bottomSpace: Dp) {
    val threshold by Prefs.sensitivity.collectAsState()
    val bg by Prefs.backgroundShake.collectAsState()
    val defaultAction by Prefs.defaultShake.collectAsState()
    val apps by vm.apps.collectAsState()
    val toggleBg = rememberBgShakeToggle()
    var showPicker by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        ShakeHub.meterActive = true
        onDispose {
            ShakeHub.meterActive = false
            ShakeHub.force.value = 0f
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Shake",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp)
        )
        Meter(threshold)

        SectionTitle("Sensitivity")
        GroupCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Threshold", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "%.1f m/s\u00B2".format(threshold),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = threshold,
                    onValueChange = { Prefs.updateSensitivity(it) },
                    valueRange = 10f..30f,
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                Row {
                    Text("More sensitive", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Needs a firm shake", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionTitle("Action")
        GroupCard {
            NavRow("Default shake action", Actions.label(defaultAction, apps ?: emptyList()), Icons.Rounded.Vibration) {
                showPicker = true
            }
        }

        SectionTitle("Background")
        GroupCard {
            ToggleRow(
                "Shake in the background",
                "Keeps a notification so Android lets GameHub listen while the app is closed.",
                bg, Icons.Rounded.Vibration
            ) { toggleBg(it) }
        }
        if (bg) {
            Spacer(Modifier.height(12.dp))
            BackgroundGuide()
        }
        Text(
            "With another app on screen, a shake can open the library or another app and toggle the flashlight. " +
                "Screenshot, back, reload and window.onShake() need an app open in GameHub.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp)
        )
        Spacer(Modifier.height(bottomSpace + 16.dp))
    }

    if (showPicker) {
        ShakeActionPicker(
            current = defaultAction,
            apps = apps ?: emptyList(),
            includeDefault = false,
            onPick = { Prefs.updateDefaultShake(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun Meter(threshold: Float) {
    val force by ShakeHub.force.collectAsState()
    val lastShake by ShakeHub.lastShake.collectAsState()
    val enteredAt = remember { SystemClock.elapsedRealtime() }
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(lastShake) {
        if (lastShake > enteredAt) {
            flash = true
            delay(700)
            flash = false
        }
    }
    val cs = MaterialTheme.colorScheme
    val fill by animateFloatAsState((force / 40f).coerceIn(0f, 1f), tween(60), label = "meter")
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.1f".format(force),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "m/s\u00B2",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = if (flash) cs.primary else cs.surfaceContainerHighest
                ) {
                    Text(
                        if (flash) "Shake detected" else "Waiting",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (flash) cs.onPrimary else cs.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(16.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                Box(
                    Modifier.fillMaxWidth(fill).fillMaxHeight().clip(CircleShape)
                        .background(if (force > threshold) cs.primary else cs.outline)
                )
                Box(Modifier.fillMaxWidth((threshold / 40f).coerceIn(0f, 1f))) {
                    Box(Modifier.align(Alignment.CenterEnd).width(3.dp).fillMaxHeight().background(cs.onSurface))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Shake the phone. The line marks your threshold; cross it twice within half a second to trigger.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BackgroundGuide() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    val notif = remember(refresh) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val battery = remember(refresh) {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val overlay = remember(refresh) { Settings.canDrawOverlays(ctx) }
    val autoDone by Prefs.autoStartDone.collectAsState()
    val pkg = ctx.packageName

    GroupCard {
        Text(
            "Setup guide",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
        )
        Text(
            "OPPO and ColorOS stop background apps aggressively. Do all four steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
        )
        GuideStep(
            Icons.Rounded.Notifications, "Allow notifications",
            "Android needs the notification to keep the listener alive.", notif, "Allow"
        ) {
            launch(ctx, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
        }
        GuideStep(
            Icons.Rounded.BatteryChargingFull, "Remove battery limits",
            "Choose Allow, then in App battery management set GameHub to Don't optimize.", battery, "Exempt"
        ) {
            if (!launch(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))) {
                launch(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        GuideStep(
            Icons.Rounded.Layers, "Display over other apps",
            "Lets a shake open the library or an app from the background.", overlay, "Allow"
        ) {
            launch(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")))
        }
        GuideStep(
            Icons.Rounded.RocketLaunch, "Allow auto-start",
            "Turn on Auto-start (or Allow auto-launch) for GameHub, then lock GameHub in the recent apps list.",
            autoDone, "Open"
        ) { openAutoStart(ctx) }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { Prefs.updateAutoStartDone(!autoDone) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = autoDone, onCheckedChange = { Prefs.updateAutoStartDone(it) })
            Text("I did the auto-start step", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GuideStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    done: Boolean,
    button: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = if (done) cs.primary else cs.surfaceContainerHighest,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (done) Icons.Rounded.Check else icon, null,
                    tint = if (done) cs.onPrimary else cs.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(body, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (!done) {
                OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                    Text(button)
                }
            }
        }
    }
}

private fun launch(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

private fun openAutoStart(ctx: Context) {
    val targets = listOf(
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.battery" to "com.oplus.startupapp.view.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity"
    )
    for ((p, c) in targets) {
        if (launch(ctx, Intent().setComponent(ComponentName(p, c)))) return
    }
    launch(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
}
