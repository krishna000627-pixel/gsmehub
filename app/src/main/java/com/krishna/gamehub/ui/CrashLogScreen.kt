package com.krishna.gamehub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.gamehub.core.CrashLog

data class CrashEntry(val time: String, val title: String, val trace: String)

private val entryStart = Regex("(?m)^(?=\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\] )")
private val stampLine = Regex("^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})\\] (.*)$")

/** Newest first. */
fun parseCrashLog(raw: String): List<CrashEntry> {
    if (raw.isBlank()) return emptyList()
    return raw.split(entryStart).mapNotNull { chunk ->
        if (chunk.isBlank()) return@mapNotNull null
        val lines = chunk.trimEnd().lines()
        val m = stampLine.matchEntire(lines.first())
        if (m != null) {
            CrashEntry(m.groupValues[1], m.groupValues[2], lines.drop(1).joinToString("\n").trim())
        } else {
            CrashEntry("", lines.first(), lines.drop(1).joinToString("\n").trim())
        }
    }.reversed()
}

@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var raw by remember { mutableStateOf(CrashLog.read()) }
    val entries = remember(raw) { parseCrashLog(raw) }
    var confirmClear by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Crash log", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (entries.isEmpty()) "No errors logged" else "${entries.size} logged, newest first",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (entries.isNotEmpty()) {
                IconButton(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("GameHub crash log", raw))
                }) { Icon(Icons.Rounded.ContentCopy, "Copy") }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "GameHub crash log")
                        .putExtra(Intent.EXTRA_TEXT, raw)
                    ctx.startActivity(Intent.createChooser(send, "Share crash log"))
                }) { Icon(Icons.Rounded.Share, "Share") }
                IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "Clear") }
            }
        }

        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(96.dp).background(cs.primaryContainer, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(44.dp), tint = cs.onPrimaryContainer) }
                Spacer(Modifier.size(20.dp))
                Text("All clear", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Nothing has crashed or failed on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries) { e -> EntryCard(e) }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the crash log?") },
            text = { Text("All logged errors are removed from this device.") },
            confirmButton = {
                TextButton(onClick = { CrashLog.clear(); raw = ""; confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EntryCard(e: CrashEntry) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(enabled = e.trace.isNotBlank()) { open = !open }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = cs.errorContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.BugReport, null, tint = cs.onErrorContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = if (open) 6 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (e.time.isNotBlank()) {
                        Text(e.time, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
                if (e.trace.isNotBlank()) {
                    Icon(
                        if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null, tint = cs.onSurfaceVariant
                    )
                }
            }
            if (open && e.trace.isNotBlank()) {
                Spacer(Modifier.size(12.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        e.trace,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
