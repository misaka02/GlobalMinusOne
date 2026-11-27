package com.example.globalminusone.data.operations

import androidx.compose.runtime.Immutable
import java.util.UUID
import kotlinx.serialization.Serializable
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.ThemeConfig

/**
 * Configuration for an isolated sub-panel reachable from a [ComponentType.SUB_PANEL_ENTRY] card.
 * Only one panel (root or active sub-panel) is ever displayed on screen at once.
 */
@Immutable
@Serializable
data class SubPanelConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "子面板",
    val inheritGeometry: Boolean = true,
    val panelWidthPercent: Int = 70,
    val panelHeightPercent: Int = 100,
    val panelHorizontalOffsetPercent: Int = 0,
    val panelVerticalPositionPercent: Int = 50,
    val edgeMarginPercent: Int = 2,
    val inheritAppearance: Boolean = true,
    val isDarkMode: Boolean = false,
    val panelBgColorHex: Long = 0x00000000,
    val panelBgAlphaPercent: Int = 0,
    val cardCornerRadiusDp: Int = 8
)

/** Loop-safe navigation stack across nested sub-panels (`emptyList()` = main panel). */
@Immutable
data class SubPanelNavStack(val stack: List<String> = emptyList()) {
    val currentId: String? get() = stack.lastOrNull()
    val depth: Int get() = stack.size

    fun push(
        subPanelId: String,
        validIds: Set<String>,
        maxDepth: Int = MAX_SUB_PANEL_DEPTH
    ): SubPanelNavStack {
        if (subPanelId.isBlank() || subPanelId !in validIds) return this
        val existingIdx = stack.indexOf(subPanelId)
        // Jumping to an ancestor unwinds back to that panel instead of creating a cycle.
        if (existingIdx >= 0) return SubPanelNavStack(stack.subList(0, existingIdx + 1))
        if (stack.size >= maxDepth.coerceAtLeast(1)) return this
        return SubPanelNavStack(stack + subPanelId)
    }

    fun pop(): SubPanelNavStack =
        if (stack.isEmpty()) this else SubPanelNavStack(stack.dropLast(1))

    fun pruneMissing(validIds: Set<String>): SubPanelNavStack {
        val kept = stack.filter { it in validIds }
        return if (kept.size == stack.size) this else SubPanelNavStack(kept)
    }

    companion object {
        const val MAX_SUB_PANEL_DEPTH = 6
    }
}

/** Projects [subPanel]'s geometry and appearance overrides onto [mainTheme] when inside it. */
fun resolveSubPanelTheme(mainTheme: ThemeConfig, subPanel: SubPanelConfig): ThemeConfig {
    val geom = if (subPanel.inheritGeometry) mainTheme else mainTheme.copy(
        panelWidthPercent = subPanel.panelWidthPercent.coerceIn(10, 100),
        panelHeightPercent = subPanel.panelHeightPercent.coerceIn(15, 100),
        panelHorizontalOffsetPercent = subPanel.panelHorizontalOffsetPercent.coerceIn(0, 80),
        panelVerticalPositionPercent = subPanel.panelVerticalPositionPercent.coerceIn(0, 100),
        edgeMarginPercent = subPanel.edgeMarginPercent.coerceIn(0, 20)
    )
    return if (subPanel.inheritAppearance) geom else geom.copy(
        isDarkMode = subPanel.isDarkMode,
        followSystemDark = false,
        panelBgColorHex = subPanel.panelBgColorHex,
        panelBgAlphaPercent = subPanel.panelBgAlphaPercent.coerceIn(0, 100),
        cardCornerRadiusDp = subPanel.cardCornerRadiusDp.coerceIn(0, 32)
    )
}

/** Captures geometry edits into [subPanel] and detaches inheritance so main panel is untouched. */
fun applyGeometryToSubPanel(subPanel: SubPanelConfig, editedTheme: ThemeConfig): SubPanelConfig =
    subPanel.copy(
        inheritGeometry = false,
        panelWidthPercent = editedTheme.panelWidthPercent.coerceIn(10, 100),
        panelHeightPercent = editedTheme.panelHeightPercent.coerceIn(15, 100),
        panelHorizontalOffsetPercent = editedTheme.panelHorizontalOffsetPercent.coerceIn(0, 80),
        panelVerticalPositionPercent = editedTheme.panelVerticalPositionPercent.coerceIn(0, 100),
        edgeMarginPercent = editedTheme.edgeMarginPercent.coerceIn(0, 20)
    )

/** Pure state operations for sub-panel CRUD, keeping lock and transaction boundaries in [MinusOneRepository]. */
internal object SubPanelOperations {
    fun create(
        current: GlobalMinusOneState,
        name: String,
        inheritGeometry: Boolean,
        defaultLayout: PageLayoutType,
        newId: () -> String
    ): Pair<GlobalMinusOneState, SubPanelConfig>? {
        if (current.advancedConfig.layoutLocked || current.subPanels.size >= 64) return null
        val subPanelId = newId()
        val cleanName = name.trim().ifBlank { "子面板 ${current.subPanels.size + 1}" }
        val sp = SubPanelConfig(
            id = subPanelId,
            name = cleanName,
            inheritGeometry = inheritGeometry,
            panelWidthPercent = current.themeConfig.panelWidthPercent,
            panelHeightPercent = current.themeConfig.panelHeightPercent,
            panelHorizontalOffsetPercent = current.themeConfig.panelHorizontalOffsetPercent,
            panelVerticalPositionPercent = current.themeConfig.panelVerticalPositionPercent,
            edgeMarginPercent = current.themeConfig.edgeMarginPercent,
            isDarkMode = current.themeConfig.isDarkMode,
            panelBgColorHex = current.themeConfig.panelBgColorHex,
            panelBgAlphaPercent = current.themeConfig.panelBgAlphaPercent,
            cardCornerRadiusDp = current.themeConfig.cardCornerRadiusDp
        )
        val initialPage = PageOperations.createPage(defaultLayout, cleanName, 1, subPanelId, newId)
        val updated = current.copy(subPanels = current.subPanels + sp, pages = current.pages + initialPage)
        return updated to sp
    }

    fun update(
        current: GlobalMinusOneState,
        subPanelId: String,
        transform: (SubPanelConfig) -> SubPanelConfig
    ): GlobalMinusOneState? {
        if (current.advancedConfig.layoutLocked) return null
        val idx = current.subPanels.indexOfFirst { it.id == subPanelId }
        if (idx < 0) return null
        val next = transform(current.subPanels[idx]).copy(id = subPanelId)
        val list = current.subPanels.toMutableList().apply { this[idx] = next }
        return current.copy(subPanels = list)
    }

    fun delete(current: GlobalMinusOneState, subPanelId: String): GlobalMinusOneState? {
        if (subPanelId.isBlank() || current.advancedConfig.layoutLocked) return null
        if (current.subPanels.none { it.id == subPanelId }) return null
        val remainingPages = current.pages.filterNot { it.subPanelId == subPanelId }
        if (remainingPages.none { it.subPanelId.isEmpty() }) return null
        fun pruneEntries(items: List<ComponentItem>): List<ComponentItem> = items
            .filterNot { it.type == ComponentType.SUB_PANEL_ENTRY && it.subPanelId == subPanelId }
            .map { it.copy(containerComponents = pruneEntries(it.containerComponents)) }
        val cleanedPages = remainingPages.map { it.copy(components = pruneEntries(it.components)) }
        return current.copy(
            subPanels = current.subPanels.filterNot { it.id == subPanelId },
            pages = cleanedPages,
            currentPageIndex = current.currentPageIndex.coerceIn(0, (cleanedPages.size - 1).coerceAtLeast(0)),
            selectedComponentId = null
        )
    }
}
