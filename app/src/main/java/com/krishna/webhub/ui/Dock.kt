package com.krishna.webhub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class DockItemData(val label: String, val icon: ImageVector)

private val dockItems = listOf(
    DockItemData("Library", Icons.Rounded.Apps),
    DockItemData("Shake", Icons.Rounded.Vibration),
    DockItemData("Settings", Icons.Rounded.Settings)
)

@Composable
fun FloatingDock(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tick = rememberTick()
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 12.dp).height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            dockItems.forEachIndexed { i, item ->
                if (i > 0) Spacer(Modifier.width(4.dp))
                DockItem(item, i == selected) {
                    if (i != selected) tick()
                    onSelect(i)
                }
            }
        }
    }
}

@Composable
private fun DockItem(item: DockItemData, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "dockBg"
    )
    val pad by animateDpAsState(
        if (selected) 18.dp else 14.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "dockPad"
    )
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.height(48.dp).clip(CircleShape).background(bg).clickable(onClick = onClick)
            .padding(horizontal = pad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(item.label, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
