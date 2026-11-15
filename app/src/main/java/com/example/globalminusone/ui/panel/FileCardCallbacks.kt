package com.example.globalminusone.ui.panel

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.globalminusone.data.ComponentItem

internal val LocalOpenFileShortcut = staticCompositionLocalOf<(ComponentItem) -> Unit> { {} }
internal val LocalSelectImage = staticCompositionLocalOf<(ComponentItem) -> Unit> { {} }
