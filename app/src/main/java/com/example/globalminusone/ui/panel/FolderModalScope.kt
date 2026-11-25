package com.example.globalminusone.ui.panel

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/** One full-window Compose host inside the existing folder Popup. Later children
 * cover properties/menus without creating a second focusable Android window. */
@Composable
internal fun FolderModalScope(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val host = remember { PanelModalHostState() }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val dialogOwner = remember(host) { Any() }
    val baseOwner = remember(host) { Any() }
    DisposableEffect(host, dialogOwner, baseOwner) {
        PanelModalBackHandler.register(dialogOwner, PanelModalBackHandler.PRIORITY_DIALOG) { host.dismiss() }
        PanelModalBackHandler.register(baseOwner, PanelModalBackHandler.PRIORITY_FOLDER_BASE) { latestDismiss(); true }
        onDispose {
            PanelModalBackHandler.unregister(dialogOwner)
            PanelModalBackHandler.unregister(baseOwner)
        }
    }
    CompositionLocalProvider(LocalPanelModalHost provides host) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedPanelModalHostContent(host.entry)
        }
    }
}
