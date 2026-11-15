package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A modal is a later child of the folder's full-window host, never another native
 * Popup attached to the icon. ComponentEditModal owns its scrim, input and back stack. */
@Composable
internal fun FolderWindowLayer(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
