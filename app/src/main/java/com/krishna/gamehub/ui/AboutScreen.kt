package com.krishna.gamehub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krishna.gamehub.BuildConfig
import com.krishna.gamehub.data.AppRepo

private val whatsNew = listOf(
    "New home screen with a Continue card, filters, sorting and a shuffle button",
    "About and Crash log are full pages now, and the log can be shared",
    "Add any app to your phone home screen from its menu",
    "Compact grid option in Settings",
    "The app is now named GameHub"
)

@Composable
fun AboutScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val apps by vm.apps.collectAsState()
    val stats by produceState<Pair<Long, Long>?>(null, apps?.size) { value = AppRepo.storageStats() }
    val info = "GameHub ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | " +
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ${Build.MANUFACTURER} ${Build.MODEL}"

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("About", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.size(104.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.SportsEsports, null, Modifier.size(56.dp), tint = cs.onPrimary) }
            Spacer(Modifier.height(16.dp))
            Text("GameHub", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your offline HTML games and apps, one tap away.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Apps", (apps?.size ?: 0).toString(), Modifier.weight(1f))
                Stat("Pinned", (apps?.count { it.pinned } ?: 0).toString(), Modifier.weight(1f))
                Stat("Storage", stats?.let { formatSize(it.first) } ?: "...", Modifier.weight(1f))
            }

            SectionTitle("What's new in ${BuildConfig.VERSION_NAME}")
            GroupCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    whatsNew.forEach { line ->
                        Row {
                            Text("\u2022", color = cs.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SectionTitle("How it works")
            GroupCard {
                InfoRow(Icons.Rounded.WifiOff, "Offline first", "Apps run from your device. Internet is off unless you allow it per app.")
                InfoRow(Icons.Rounded.Lock, "Private storage", "Every app has its own saved data that other apps cannot read.")
                InfoRow(Icons.Rounded.Vibration, "Shake actions", "Shake to open the library, take a screenshot, go back and more.")
                InfoRow(Icons.Rounded.Code, "JS bridge", "Apps can vibrate, toast, share, save files and more through window.Gamehub.")
            }

            SectionTitle("Support")
            GroupCard {
                NavRow("Copy version info", info, Icons.Rounded.ContentCopy) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("GameHub version info", info))
                    vm.messages.tryEmit("Version info copied")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Made with Kotlin and Jetpack Compose",
                style = MaterialTheme.typography.bodySmall,
                color = cs.outline
            )
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(vertical = 16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
