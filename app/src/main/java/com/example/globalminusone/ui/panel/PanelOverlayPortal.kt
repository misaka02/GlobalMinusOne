package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

internal class PortalLayer(
    val key: Any,
    val content: @Composable () -> Unit
)

/** Hosts floating folders in-window above cards, eliminating cross-process Popup window creation. */
internal class PanelOverlayPortalState {
    private val _entries = mutableStateListOf<PortalLayer>()
    val entries: List<PortalLayer> get() = _entries
    val hasEntries: Boolean get() = _entries.isNotEmpty()
    var clearGeneration by mutableIntStateOf(0)
        private set

    fun contains(key: Any): Boolean = _entries.any { it.key == key }

    fun upsert(key: Any, content: @Composable () -> Unit) {
        val index = _entries.indexOfFirst { it.key == key }
        val layer = PortalLayer(key, content)
        if (index >= 0) {
            _entries[index] = layer
        } else {
            _entries.add(layer)
        }
    }

    fun remove(key: Any) {
        _entries.removeAll { it.key == key }
    }

    fun clear() {
        _entries.clear()
        clearGeneration++
    }
}

internal val LocalPanelOverlayPortal = staticCompositionLocalOf<PanelOverlayPortalState?> { null }

@Composable
internal fun PanelOverlayPortalHost(state: PanelOverlayPortalState) {
    if (!state.hasEntries) return
    Box(Modifier.fillMaxSize()) {
        state.entries.forEach { layer ->
            key(layer.key) {
                layer.content()
            }
        }
    }
}
