package com.example.globalminusone.ui.panel

import android.content.ComponentName
import androidx.compose.runtime.staticCompositionLocalOf

internal data class EmbeddedApplicationTarget(val component: ComponentName, val label: String)
internal val LocalOpenEmbeddedApplication = staticCompositionLocalOf<((EmbeddedApplicationTarget) -> Unit)?> { null }
internal val LocalClosePanelForExternalApp = staticCompositionLocalOf<(() -> Unit)?> { null }
