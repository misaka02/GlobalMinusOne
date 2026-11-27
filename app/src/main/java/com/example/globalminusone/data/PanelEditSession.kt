package com.example.globalminusone.data

import com.example.globalminusone.data.operations.savedWidgetIds

/** Only panel configuration is transactional; file bookmarks and live records remain independent. */
internal data class PanelEditSnapshot(private val saved: GlobalMinusOneState) {
    fun widgetIds(): Set<Int> = saved.savedWidgetIds()
    fun restore(current: GlobalMinusOneState): GlobalMinusOneState = current.copy(
        pages = saved.pages, themeConfig = saved.themeConfig, advancedConfig = saved.advancedConfig,
        appearanceConfig = saved.appearanceConfig, appAppearanceOverrides = saved.appAppearanceOverrides,
        triggerConfig = saved.triggerConfig, suspensionBallConfig = saved.suspensionBallConfig,
        cards = saved.cards, quickActions = saved.quickActions, boundWidgets = saved.boundWidgets,
        currentPageIndex = current.currentPageIndex.coerceIn(saved.pages.indices)
    )

    fun changed(current: GlobalMinusOneState): Boolean {
        val original = restore(current)
        return original != current
    }
}

/** A drop belongs to one live edit session and one unchanged component tree. */
internal class LinearReorderSession(
    val pageId: String,
    internal val baseline: PanelEditSnapshot,
    internal val componentIds: List<String>
)
