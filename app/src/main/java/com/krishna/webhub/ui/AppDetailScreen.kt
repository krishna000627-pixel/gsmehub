@file:OptIn(ExperimentalMaterial3Api::class)

package com.krishna.webhub.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.krishna.webhub.core.Actions

@Composable
fun AppDetailScreen(id: String, vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val app by remember(id) { vm.observe(id) }.collectAsState(initial = null)
    val apps by vm.apps.collectAsState()
    val tick = rememberTick()
    var showPicker by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportZip(id, uri)
    }
    val bg = MaterialTheme.colorScheme.background
    val a = app
    if (a == null) {
        Column(Modifier.fillMaxSize().background(bg)) {}
        return
    }

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text("App details", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(a, 88.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        (if (a.version.isBlank()) "No version" else "Version ${a.version}") + " \u2022 " + formatSize(a.sizeBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { tick(); vm.save(a.copy(pinned = !a.pinned)) }) {
                    Icon(
                        Icons.Rounded.PushPin, if (a.pinned) "Unpin" else "Pin",
                        tint = if (a.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { RunnerActivity.start(ctx, a.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Run")
            }

            SectionTitle("Permissions and display")
            GroupCard {
                ToggleRow(
                    "Allow internet", "Off by default. Blocks every request that leaves the device.",
                    a.allowInternet, Icons.Rounded.Language
                ) { vm.save(a.copy(allowInternet = it)) }
                ToggleRow("Fullscreen", "Hide the status and navigation bars", a.fullscreen, Icons.Rounded.Fullscreen) {
                    vm.save(a.copy(fullscreen = it))
                }
                ToggleRow("Keep screen on", "Stops the screen from sleeping while running", a.keepAwake, Icons.Rounded.Visibility) {
                    vm.save(a.copy(keepAwake = it))
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Orientation", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("auto" to "Auto", "portrait" to "Portrait", "landscape" to "Landscape").forEach { (k, label) ->
                            FilterChip(
                                selected = a.orientation == k,
                                onClick = { tick(); vm.save(a.copy(orientation = k)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            SectionTitle("Shake")
            GroupCard {
                NavRow("Shake action", Actions.label(a.shakeAction, apps ?: emptyList()), Icons.Rounded.Vibration) {
                    showPicker = true
                }
            }

            SectionTitle("Data")
            GroupCard {
                NavRow("Export ZIP", "Files and settings in one archive", Icons.Rounded.FileDownload) {
                    exportPicker.launch(a.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".zip")
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.DeleteSweep, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear app data")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Delete app", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showPicker) {
        ShakeActionPicker(
            current = a.shakeAction,
            apps = apps ?: emptyList(),
            includeDefault = true,
            excludeId = a.id,
            onPick = { vm.save(a.copy(shakeAction = it)); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear app data?") },
            text = { Text("Saved settings, localStorage and IndexedDB of ${a.name} are erased. The app files stay.") },
            confirmButton = { TextButton(onClick = { vm.clearData(a.id); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${a.name}?") },
            text = { Text("The app's files and saved data are removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onBack(); vm.delete(a.id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
