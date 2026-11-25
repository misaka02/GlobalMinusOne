package com.example.globalminusone.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.globalminusone.browser.IndependentBrowser
import com.example.globalminusone.browser.resolveBrowserDestination

internal typealias BrowserTab = com.example.globalminusone.browser.BrowserTab
internal typealias IndependentBrowserSession = com.example.globalminusone.browser.IndependentBrowserSession

internal object PanelBrowserBackHandler {
    var handler: (() -> Boolean)? = null
    fun handleBack() = handler?.invoke() == true
}

internal fun browserDestination(input: String): String? = resolveBrowserDestination(input)

@Composable
internal fun BrowserPanel(
    session: IndependentBrowserSession,
    visible: Boolean,
    onHostInputAllowed: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val useDarkChrome = session.nightMode
    var independentBack by remember { mutableStateOf<(() -> Boolean)?>(null) }
    val latestClose by rememberUpdatedState(onClose)
    val latestBack by rememberUpdatedState(independentBack)
    val closePanelForExternalApp = LocalPanelActions.current.closePanelForExternalApp
    DisposableEffect(visible) {
        val back: () -> Boolean = { if (latestBack?.invoke() != true) latestClose(); true }
        if (visible) PanelBrowserBackHandler.handler = back
        onDispose { if (PanelBrowserBackHandler.handler === back) PanelBrowserBackHandler.handler = null }
    }
    MaterialTheme(colorScheme = if (useDarkChrome) darkColorScheme() else lightColorScheme()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            key(session.activeId) {
                IndependentBrowser(
                    session = session,
                    visible = visible,
                    onHostInputAllowed = { if (visible) onHostInputAllowed(it) },
                    onBackHandlerChanged = { independentBack = it },
                    modifier = Modifier.fillMaxSize(),
                    onClosePanelForExternalApp = closePanelForExternalApp,
                    webViewportModifier = Modifier.ownPanelScrollGestures(visible, lockFromDown = true)
                )
            }
        }
    }
}
