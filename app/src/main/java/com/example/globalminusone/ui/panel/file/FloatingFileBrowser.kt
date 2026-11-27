package com.example.globalminusone.ui.panel.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.ui.panel.FloatingPanelWindow

@Composable
fun FloatingFileBrowser(
    repository: MinusOneRepository,
    initialFile: ComponentItem? = null,
    onContentVisibilityChanged: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val state by repository.state.collectAsState()
    val config = state.fileBrowserConfig
    var closeRequest by remember { mutableStateOf<(() -> Unit)?>(null) }
    FloatingPanelWindow("files", "文件管理", config.windowWidthFraction, config.windowHeightFraction,
        config.windowXFraction, config.windowYFraction,
        minHeightDp = 420,
        allowCollapse = true,
        onContentVisibilityChanged = onContentVisibilityChanged,
        onClose = { (closeRequest ?: onDismiss).invoke() },
        onGeometryCommitted = { width, height, x, y ->
            repository.updateFileBrowserConfig { it.copy(windowWidthFraction = width,
                windowHeightFraction = height, windowXFraction = x, windowYFraction = y) }
        }) {
        FileBrowserPanel(repository, initialFile, compact = true, showCloseControl = false,
            onCloseHandlerChanged = { closeRequest = it }, onDismiss = onDismiss)
    }
}
