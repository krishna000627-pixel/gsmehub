@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.krishna.gamehub.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krishna.gamehub.core.Actions
import com.krishna.gamehub.core.ShakeOption
import com.krishna.gamehub.data.AppEntity
import com.krishna.gamehub.data.AppRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

private val iconCache = LruCache<String, ImageBitmap>(64)

private fun decodeIcon(f: File): ImageBitmap? {
    if (!f.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    var s = 1
    while (bounds.outWidth / (s * 2) >= 192 && bounds.outHeight / (s * 2) >= 192) s *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
}

@Composable
fun AppIcon(app: AppEntity, size: Dp, modifier: Modifier = Modifier) {
    val rel = app.icon
    val id = app.id
    val key = "$id:$rel"
    val bmp by produceState<ImageBitmap?>(iconCache.get(key), key) {
        if (value == null && rel != null) {
            value = withContext(Dispatchers.IO) {
                decodeIcon(File(AppRepo.dirOf(id), rel))?.also { iconCache.put(key, it) }
            }
        }
    }
    val hue = (abs(app.name.hashCode()) % 360).toFloat()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Color.hsv(hue, 0.45f, 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                app.name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(140.dp).clip(CircleShape).background(cs.primaryContainer.copy(alpha = 0.35f)))
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(44.dp), tint = cs.onPrimaryContainer)
            }
            Box(
                Modifier.align(Alignment.TopEnd).offset((-8).dp, 8.dp).size(18.dp)
                    .clip(CircleShape).background(cs.tertiary.copy(alpha = 0.7f))
            )
            Box(
                Modifier.align(Alignment.BottomStart).offset(10.dp, (-6).dp).size(12.dp)
                    .clip(CircleShape).background(cs.primary.copy(alpha = 0.6f))
            )
        }
        Spacer(Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) { Column(content = content) }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit
) {
    val tick = rememberTick()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable { tick(); onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = { tick(); onChange(it) })
    }
}

@Composable
fun NavRow(title: String, value: String?, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SpinnerButton(
    text: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = { if (!loading) onClick() },
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ShakeActionPicker(
    current: String,
    apps: List<AppEntity>,
    includeDefault: Boolean,
    excludeId: String? = null,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var stage by remember { mutableIntStateOf(0) }
    val choices: List<ShakeOption> = if (includeDefault) listOf(Actions.defaultOption) + Actions.options else Actions.options
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (stage == 0) "Shake action" else "Open which app?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (stage == 0) {
                    choices.forEach { o ->
                        val selected = if (o.id == Actions.APP) current.startsWith(Actions.APP) else current == o.id
                        PickerRow(o.label, o.hint, selected) {
                            if (o.id == Actions.APP) stage = 1 else onPick(o.id)
                        }
                    }
                } else {
                    val others = apps.filter { it.id != excludeId }
                    if (others.isEmpty()) {
                        Text("No other apps yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    others.forEach { a ->
                        PickerRow(a.name, "", current == Actions.APP + a.id) { onPick(Actions.APP + a.id) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (stage == 1) stage = 0 else onDismiss() }) {
                Text(if (stage == 1) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun PickerRow(label: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint.isNotBlank()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.0f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

fun relTime(ts: Long): String {
    if (ts <= 0) return "Never opened"
    val m = (System.currentTimeMillis() - ts) / 60000
    return when {
        m < 1 -> "Opened just now"
        m < 60 -> "Opened $m min ago"
        m < 60 * 24 -> "Opened ${m / 60} h ago"
        m < 60 * 48 -> "Opened yesterday"
        m < 60 * 24 * 30 -> "Opened ${m / (60 * 24)} d ago"
        else -> "Opened " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    }
}
