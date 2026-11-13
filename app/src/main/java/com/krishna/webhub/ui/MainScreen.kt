package com.krishna.webhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastDetail by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }

    BackHandler(enabled = detailId != null || tab != 0) {
        if (detailId != null) detailId = null else tab = 0
    }

    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dockSpace = 64.dp + 12.dp + nav + 8.dp
    val d = detailId
    if (d != null) lastDetail = d

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(initialScale = 0.97f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))) togetherWith
                    fadeOut(tween(90))
            },
            label = "tabs"
        ) { t ->
            when (t) {
                0 -> LibraryScreen(vm, onDetail = { detailId = it }, bottomSpace = dockSpace)
                1 -> ShakeScreen(vm, bottomSpace = dockSpace)
                else -> SettingsScreen(vm, bottomSpace = dockSpace, onOpenShakeTab = { tab = 1 })
            }
        }

        AnimatedVisibility(
            visible = detailId == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            FloatingDock(selected = tab, onSelect = { tab = it })
        }

        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = dockSpace))

        AnimatedVisibility(
            visible = detailId != null,
            enter = slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(),
            exit = slideOutHorizontally { it / 3 } + fadeOut()
        ) {
            AppDetailScreen(lastDetail, vm) { detailId = null }
        }
    }
}
