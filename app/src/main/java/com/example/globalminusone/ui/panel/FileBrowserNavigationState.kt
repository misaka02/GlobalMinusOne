package com.example.globalminusone.ui.panel

import androidx.compose.runtime.mutableStateOf
import com.example.globalminusone.system.BrowserLocation

/** Breadcrumb ownership; preview/editor/back priority stays with the pane hosting those surfaces. */
internal class FileBrowserNavigationState {
    val stack = mutableStateOf<List<BrowserLocation>>(emptyList())
    val section = mutableStateOf("常用")

    /** A same-location navigation still selects the directory and exits its previous query. */
    fun navigate(value: List<BrowserLocation>): Boolean {
        val changed = value != stack.value
        stack.value = value
        section.value = if (value.isEmpty()) "常用" else "目录"
        return changed
    }

    fun parentStack(): List<BrowserLocation> = stack.value.dropLast(1)
}
