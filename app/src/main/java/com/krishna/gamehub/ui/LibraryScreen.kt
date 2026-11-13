@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.krishna.gamehub.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilterChip
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.krishna.gamehub.R
import com.krishna.gamehub.data.AppRepo
import com.krishna.gamehub.data.Prefs
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krishna.gamehub.data.AppEntity

@Composable
fun LibraryScreen(vm: MainViewModel, onDetail: (String) -> Unit, bottomSpace: Dp) {
    val ctx = LocalContext.current
    val apps by vm.apps.collectAsState()
    val importing by vm.importing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var sortMenu by remember { mutableStateOf(false) }
    val sort by Prefs.librarySort.collectAsState()
    val compact by Prefs.compactGrid.collectAsState()
    var menuFor by remember { mutableStateOf<String?>(null) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var editApp by remember { mutableStateOf<AppEntity?>(null) }
    var deleteApp by remember { mutableStateOf<AppEntity?>(null) }
    var shakeApp by remember { mutableStateOf<AppEntity?>(null) }
    var exportId by remember { mutableStateOf<String?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importZip) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::importFolder) }
    val htmlPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importHtml) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = exportId
        exportId = null
        if (uri != null && id != null) vm.exportZip(id, uri)
    }

    LaunchedEffect(Unit) { vm.importDone.collect { showSheet = false; showPaste = false } }

    Box(Modifier.fillMaxSize()) {
        val list = apps
        if (list != null) {
            if (list.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Apps,
                    title = "No apps yet",
                    body = "Import an HTML app from a ZIP, a folder, a single file or pasted code. It runs offline.",
                    action = "Import an app",
                    onAction = { showSheet = true }
                )
            } else {
                val filtered = remember(list, query, filter, sort) {
                    var r = if (query.isBlank()) list else list.filter { it.name.contains(query.trim(), ignoreCase = true) }
                    r = when (filter) {
                        "pinned" -> r.filter { it.pinned }
                        "recent" -> r.filter { it.lastOpened > 0 }.sortedByDescending { it.lastOpened }
                        else -> r
                    }
                    if (filter == "recent") r else when (sort) {
                        "name" -> r.sortedWith(compareByDescending<AppEntity> { it.pinned }.thenBy { it.name.lowercase() })
                        "added" -> r.sortedWith(compareByDescending<AppEntity> { it.pinned }.thenByDescending { it.createdAt })
                        else -> r
                    }
                }
                val continueApp = remember(list) { list.filter { it.lastOpened > 0 }.maxByOrNull { it.lastOpened } }
                val tick = rememberTick()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (compact) 112.dp else 156.dp),
                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, bottomSpace + 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                    modifier = Modifier.fillMaxSize().statusBarsPadding()
                ) {
                    item(key = "hdr_title", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("GameHub", style = MaterialTheme.typography.headlineMedium)
                                val pins = list.count { it.pinned }
                                Text(
                                    if (pins > 0) "${list.size} apps, $pins pinned" else "${list.size} apps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { tick(); RunnerActivity.start(ctx, list.random().id) }) {
                                Icon(Icons.Rounded.Shuffle, "Open a random app")
                            }
                        }
                    }
                    item(key = "hdr_search", span = { GridItemSpan(maxLineSpan) }) {
                        SearchField(query, Modifier.fillMaxWidth()) { query = it }
                    }
                    if (continueApp != null && query.isBlank() && filter == "all") {
                        item(key = "hdr_continue", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.padding(top = 16.dp)) {
                                ContinueCard(continueApp) { RunnerActivity.start(ctx, continueApp.id) }
                            }
                        }
                    }
                    item(key = "hdr_chips", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("all" to "All", "pinned" to "Pinned", "recent" to "Recent").forEach { (k, label) ->
                                FilterChip(
                                    selected = filter == k,
                                    onClick = { tick(); filter = k },
                                    label = { Text(label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.SwapVert, "Sort") }
                                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                    listOf("recent" to "Recently opened", "name" to "Name A to Z", "added" to "Newest added")
                                        .forEach { (k, label) ->
                                            DropdownMenuItem(
                                                text = { Text(if (sort == k) "\u2713  $label" else label) },
                                                onClick = { Prefs.updateSort(k); sortMenu = false }
                                            )
                                        }
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "hdr_empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                when {
                                    query.isNotBlank() -> "No app matches \"${query.trim()}\""
                                    filter == "pinned" -> "Nothing pinned yet. Long-press an app and choose Pin to top."
                                    else -> "Nothing opened yet. Run an app and it shows up here."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { app ->
                        Box(Modifier.animateItem()) {
                            AppCard(
                                app,
                                compact,
                                onClick = { RunnerActivity.start(ctx, app.id) },
                                onLong = { menuFor = app.id }
                            )
                            DropdownMenu(expanded = menuFor == app.id, onDismissRequest = { menuFor = null }) {
                                MenuItem("Run", Icons.Rounded.PlayArrow) {
                                    menuFor = null
                                    RunnerActivity.start(ctx, app.id)
                                }
                                MenuItem("Add to home screen", Icons.Rounded.AddToHomeScreen) {
                                    menuFor = null
                                    val ok = pinShortcut(ctx, app)
                                    vm.messages.tryEmit(
                                        if (ok) "Confirm the shortcut in your launcher" else "Your launcher cannot add shortcuts"
                                    )
                                }
                                MenuItem("Edit info", Icons.Rounded.Edit) { menuFor = null; editApp = app }
                                MenuItem("Shake action", Icons.Rounded.Vibration) { menuFor = null; shakeApp = app }
                                MenuItem("Permissions", Icons.Rounded.Shield) { menuFor = null; onDetail(app.id) }
                                MenuItem(if (app.pinned) "Unpin" else "Pin to top", Icons.Rounded.PushPin) {
                                    menuFor = null
                                    vm.save(app.copy(pinned = !app.pinned))
                                }
                                MenuItem("Export ZIP", Icons.Rounded.FileDownload) {
                                    menuFor = null
                                    exportId = app.id
                                    exportPicker.launch(app.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".zip")
                                }
                                MenuItem("Delete", Icons.Rounded.Delete) { menuFor = null; deleteApp = app }
                            }
                        }
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = { showSheet = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Import") },
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = bottomSpace + 8.dp)
                        .heightIn(min = 56.dp)
                )
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Text(
                "Import an app",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            val busy = importing != null
            ImportRow(Icons.Rounded.Archive, "ZIP archive", "An app packed as .zip", importing == "zip", !busy) {
                zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }
            ImportRow(Icons.Rounded.FolderOpen, "Folder", "Pick a folder that holds index.html", importing == "folder", !busy) {
                folderPicker.launch(null)
            }
            ImportRow(Icons.Rounded.Code, "HTML file", "A single .html file", importing == "html", !busy) {
                htmlPicker.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain"))
            }
            ImportRow(Icons.Rounded.ContentPaste, "Paste HTML", "Paste code from your clipboard", false, !busy) {
                showPaste = true
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }

    if (showPaste) {
        PasteDialog(importing == "paste", onDismiss = { showPaste = false }, onImport = vm::importPasted)
    }

    editApp?.let { a ->
        EditDialog(a, onDismiss = { editApp = null }) { name, version ->
            vm.save(a.copy(name = name, version = version))
            editApp = null
        }
    }

    deleteApp?.let { a ->
        AlertDialog(
            onDismissRequest = { deleteApp = null },
            title = { Text("Delete ${a.name}?") },
            text = { Text("The app's files and saved data are removed from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(a.id); deleteApp = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteApp = null }) { Text("Cancel") } }
        )
    }

    shakeApp?.let { a ->
        ShakeActionPicker(
            current = a.shakeAction,
            apps = apps ?: emptyList(),
            includeDefault = true,
            excludeId = a.id,
            onPick = { vm.save(a.copy(shakeAction = it)); shakeApp = null },
            onDismiss = { shakeApp = null }
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

@Composable
private fun SearchField(value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.heightIn(min = 56.dp)
    )
}

@Composable
private fun ContinueCard(app: AppEntity, onRun: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = cs.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRun)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 60.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Continue",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    relTime(app.lastOpened),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(shape = CircleShape, color = cs.primary, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "Run", tint = cs.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppEntity, compact: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().scale(scale).combinedClickable(
            interactionSource = src,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLong()
            }
        )
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AppIcon(app, if (compact) 44.dp else 56.dp)
                Spacer(Modifier.weight(1f))
                if (app.pinned) {
                    Icon(
                        Icons.Rounded.PushPin, "Pinned", Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            Text(
                app.name,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    relTime(app.lastOpened),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    app.version.takeIf { it.isNotBlank() },
                    app.sizeBytes.takeIf { it > 0 }?.let { formatSize(it) }
                ).joinToString(" \u00B7 ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun pinShortcut(ctx: Context, app: AppEntity): Boolean {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
    val bmp = app.icon?.let { BitmapFactory.decodeFile(File(AppRepo.dirOf(app.id), it).path) }
    val icon = if (bmp != null) IconCompat.createWithBitmap(bmp)
    else IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)
    val intent = Intent(ctx, RunnerActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(RunnerActivity.EXTRA_ID, app.id)
    val info = ShortcutInfoCompat.Builder(ctx, "app_" + app.id)
        .setShortLabel(app.name.take(24))
        .setLongLabel(app.name)
        .setIcon(icon)
        .setIntent(intent)
        .build()
    return ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
}

@Composable
private fun ImportRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasteDialog(loading: Boolean, onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Paste HTML") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("<!doctype html>...") },
                minLines = 6,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { SpinnerButton("Import", loading, enabled = text.isNotBlank()) { onImport(text) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditDialog(app: AppEntity, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(app.name) }
    var version by remember { mutableStateOf(app.version) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit info") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = version, onValueChange = { version = it }, label = { Text("Version") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifBlank { app.name }, version.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
