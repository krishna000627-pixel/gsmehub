@file:OptIn(ExperimentalMaterial3Api::class)

package com.krishna.webhub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.webhub.BuildConfig
import com.krishna.webhub.core.Actions
import com.krishna.webhub.core.CrashLog
import com.krishna.webhub.data.AppRepo
import com.krishna.webhub.data.Prefs
import com.krishna.webhub.web.WebViewPool
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, bottomSpace: Dp, onOpenShakeTab: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by Prefs.theme.collectAsState()
    val dynamic by Prefs.dynamicColor.collectAsState()
    val defaultAction by Prefs.defaultShake.collectAsState()
    val threshold by Prefs.sensitivity.collectAsState()
    val bg by Prefs.backgroundShake.collectAsState()
    val apps by vm.apps.collectAsState()
    val toggleBg = rememberBgShakeToggle()
    val tick = rememberTick()

    var showPicker by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var statsTick by remember { mutableIntStateOf(0) }
    var clearing by remember { mutableStateOf(false) }
    val stats by produceState<Pair<Long, Long>?>(null, statsTick, apps?.size) { value = AppRepo.storageStats() }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(vm::exportBackup)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::importBackup)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
        )

        SectionTitle("Appearance")
        GroupCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (k, label) ->
                        FilterChip(
                            selected = theme == k,
                            onClick = { tick(); Prefs.updateTheme(k) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                ToggleRow("Dynamic color", "Use colors from your wallpaper", dynamic, Icons.Rounded.Palette) {
                    Prefs.updateDynamicColor(it)
                }
            }
        }

        SectionTitle("Shake")
        GroupCard {
            NavRow("Default shake action", Actions.label(defaultAction, apps ?: emptyList()), Icons.Rounded.Vibration) {
                showPicker = true
            }
            NavRow("Sensitivity", "%.1f m/s\u00B2, tune it live on the Shake tab".format(threshold), Icons.Rounded.Tune) {
                onOpenShakeTab()
            }
            ToggleRow("Shake in the background", "Foreground service with a notification", bg, Icons.Rounded.Vibration) {
                toggleBg(it)
            }
        }

        SectionTitle("Storage")
        GroupCard {
            val s = stats
            NavRow("Apps", if (s != null) formatSize(s.first) else "Calculating", Icons.Rounded.Storage) {}
            NavRow("Cache", if (s != null) formatSize(s.second) else "Calculating", null) {}
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SpinnerButton("Clear cache", clearing) {
                    scope.launch {
                        clearing = true
                        AppRepo.clearCache()
                        WebViewPool.clearCache()
                        statsTick++
                        clearing = false
                    }
                }
            }
        }

        SectionTitle("Backup")
        GroupCard {
            NavRow("Export backup", "Apps list and settings as JSON", Icons.Rounded.FileDownload) {
                exportPicker.launch("webhub-backup.json")
            }
            NavRow("Import backup", "Restores settings and per-app options", Icons.Rounded.Restore) {
                importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }

        SectionTitle("About")
        GroupCard {
            NavRow("About WebHub", "Version ${BuildConfig.VERSION_NAME}", Icons.Rounded.Info) { showAbout = true }
            NavRow("Crash log", "Errors WebHub caught on this device", Icons.Rounded.BugReport) { showLog = true }
        }
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

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("WebHub ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "Imports, stores and runs offline HTML apps in a fast WebView. " +
                        "Every app has its own storage and no internet unless you allow it."
                )
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } }
        )
    }

    if (showLog) {
        var log by remember { mutableStateOf(CrashLog.read()) }
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text("Crash log") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        log.ifBlank { "Nothing logged." },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showLog = false }) { Text("Close") } },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("WebHub crash log", log))
                    }) { Text("Copy") }
                    TextButton(onClick = { CrashLog.clear(); log = "" }) { Text("Clear") }
                }
            }
        )
    }
}
