package com.example.globalminusone.ui.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.globalminusone.theme.SettingsTheme
import com.example.globalminusone.ui.motion.AnimatedOverlayTarget
import com.example.globalminusone.ui.motion.overlayDialogMotion
import com.example.globalminusone.ui.motion.overlayScrim

/** Render service modals in the existing full-size overlay, outside card/pager constraints. */
internal class PanelModalHostState {
    var entry by mutableStateOf<PanelModalEntry?>(null)
        private set
    fun show(value: PanelModalEntry) { entry = value }
    fun remove(owner: Any) { if (entry?.owner === owner) entry = null }
    fun dismiss(): Boolean {
        val current = entry ?: return false
        current.onDismiss()
        return true
    }
}

internal class PanelModalEntry(val owner: Any, val onDismiss: () -> Unit, val content: @Composable () -> Unit)
internal val LocalPanelModalHost = staticCompositionLocalOf<PanelModalHostState> { error("Panel modal host missing") }

internal object PanelModalBackHandler {
    const val PRIORITY_FOLDER_BASE = 10
    const val PRIORITY_FOLDER_INNER = 20
    const val PRIORITY_PROPERTY_MODAL = 30
    const val PRIORITY_DIALOG = 40

    private data class BackRegistration(
        val owner: Any,
        val priority: Int,
        val order: Long,
        val callback: () -> Boolean
    )

    private var sequence = 0L
    private val registrations = mutableListOf<BackRegistration>()
    private val legacyOwner = Any()

    var handler: (() -> Boolean)?
        get() = registrations.lastOrNull { it.owner === legacyOwner }?.callback
        set(value) {
            if (value == null) unregister(legacyOwner) else register(legacyOwner, PRIORITY_DIALOG, value)
        }

    fun register(owner: Any, priority: Int = PRIORITY_DIALOG, callback: () -> Boolean) {
        registrations.removeAll { it.owner === owner }
        registrations.add(BackRegistration(owner, priority, ++sequence, callback))
    }

    fun unregister(owner: Any) {
        registrations.removeAll { it.owner === owner }
    }

    fun clearForTest() {
        registrations.clear()
        sequence = 0L
    }

    fun handleBack(): Boolean {
        val snapshot = registrations.sortedWith(
            compareByDescending<BackRegistration> { it.priority }.thenByDescending { it.order }
        )
        return snapshot.any { it.callback() }
    }
}

@Composable
internal fun AnimatedPanelModalHostContent(entry: PanelModalEntry?) {
    val animMs = LocalPanelPalette.current.themeConfig.animationDurationMs.coerceIn(0, 800)
    AnimatedOverlayTarget(targetState = entry, animMs = animMs, keyOf = { it.owner }) { active ->
        active.content()
    }
}

@Composable
internal fun PanelModalHost(state: PanelModalHostState) {
    val owner = remember(state) { Any() }
    DisposableEffect(state, owner) {
        PanelModalBackHandler.register(owner, PanelModalBackHandler.PRIORITY_DIALOG) { state.dismiss() }
        onDispose { PanelModalBackHandler.unregister(owner) }
    }
    AnimatedPanelModalHostContent(state.entry)
}

@Composable
internal fun PanelAlertDialog(onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null) {
    val host = LocalPanelModalHost.current
    val owner = remember { Any() }
    val dark = LocalPanelPalette.current.isDark
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestContent by rememberUpdatedState<@Composable () -> Unit>({
        PanelDialogContent(dark, onDismissRequest, title, text, confirmButton, dismissButton)
    })
    DisposableEffect(host, owner) {
        host.show(PanelModalEntry(owner, { latestDismiss() }) { latestContent() })
        onDispose { host.remove(owner) }
    }
}

@Composable
private fun PanelDialogContent(dark: Boolean, onDismissRequest: () -> Unit, title: @Composable () -> Unit,
    text: @Composable () -> Unit, confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?) {
    val configuration = LocalConfiguration.current
    val dialogWidth = (configuration.screenWidthDp.dp * .92f).coerceAtMost(560.dp)
    val dialogHeight = configuration.screenHeightDp.dp * .88f
    SettingsTheme(darkTheme = dark) {
            Box(Modifier.fillMaxSize().imePadding().overlayScrim(Color.Black, 0.4f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = onDismissRequest), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.width(dialogWidth)
                    .heightIn(min = 120.dp, max = dialogHeight)
                    .overlayDialogMotion()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 8.dp) {
                    Column(Modifier.padding(20.dp)) {
                        title()
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth().heightIn(max = (dialogHeight - 160.dp).coerceAtLeast(60.dp))) { text() }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
    }
}
