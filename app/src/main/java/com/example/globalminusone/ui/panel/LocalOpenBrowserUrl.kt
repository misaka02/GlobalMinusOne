package com.example.globalminusone.ui.panel

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalOpenBrowserUrl = staticCompositionLocalOf<(String) -> Unit> { {} }
