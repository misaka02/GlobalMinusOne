package com.example.globalminusone.data.backup

import androidx.compose.runtime.Immutable
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.SubPanelConfig
import com.example.globalminusone.data.operations.flattenComponents
import com.example.globalminusone.data.policy.ShellCommandSpec
import com.example.globalminusone.data.policy.ShellExecutionMode
import java.util.UUID
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
enum class BackupExportMode(val label: String) {
    FULL("完整备份（全部页面 + 设置 + 浏览器 + 方案）"),
    SELECTED_PAGES("仅导出选定页面布局"),
    SETTINGS_ONLY("仅导出主题外观与手势配置")
}

enum class PageImportMode(val label: String) {
    REPLACE_ALL("整套替换全部页面"),
    APPEND("追加到当前面板末尾"),
    REPLACE_CURRENT("替换当前正在编辑的页面")
}

@Immutable
@Serializable
data class BackupSourceSummary(
    val panelWidthPercent: Int = 70,
    val panelHeightPercent: Int = 100,
    val gridColumns: Int = 32,
    val exportMode: String = "FULL"
)

@Immutable
data class BackupImportSelection(
    val importThemeAndPanel: Boolean = true,
    val importTriggersAndBall: Boolean = true,
    val importBrowserData: Boolean = true,
    val importProfiles: Boolean = true,
    val selectedPageIds: Set<String> = emptySet(),
    val pageImportMode: PageImportMode = PageImportMode.REPLACE_ALL,
    val applySourcePanelGeometry: Boolean = false,
    val restoreFullUserData: Boolean = false
)

/**
 * Distinguishes settings-only ZIP templates (which carry a synthetic placeholder page)
 * from legacy raw JSON files (`configurationOnly = true` with `exportSummary = null`).
 */
fun NativeBackupPayload.isSettingsOnlyExport(): Boolean =
    exportSummary?.exportMode == BackupExportMode.SETTINGS_ONLY.name

fun NativeBackupPayload.isFullBackupExport(): Boolean =
    exportSummary == null || exportSummary.exportMode == BackupExportMode.FULL.name

private fun sanitizeShellComponents(items: List<ComponentItem>): List<ComponentItem> =
    items.map { item ->
        val updatedChild = if (item.containerComponents.isEmpty()) {
            item.containerComponents
        } else {
            sanitizeShellComponents(item.containerComponents)
        }
        val nextIntentUri = if (item.type == ComponentType.SHELL_COMMAND) {
            val spec = ShellCommandSpec.fromComponent(item)
            if (spec.mode != ShellExecutionMode.NORMAL && !spec.requireConfirm) {
                spec.copy(requireConfirm = true).encodeToIntentUri()
            } else {
                item.intentUri
            }
        } else {
            item.intentUri
        }
        item.copy(intentUri = nextIntentUri, containerComponents = updatedChild)
    }

/** Forces explicit user confirmation on privileged Root/Shizuku shell cards imported from external archives. */
fun sanitizeExternalBackupShellCards(state: GlobalMinusOneState): GlobalMinusOneState {
    val sanitizedPages = state.pages.map { p -> p.copy(components = sanitizeShellComponents(p.components)) }
    val sanitizedLibrary = state.subPanelLibrary.map { tpl ->
        tpl.copy(pages = tpl.pages.map { p -> p.copy(components = sanitizeShellComponents(p.components)) })
    }
    val sanitizedProfiles = state.profiles.map { profile ->
        if (profile.stateJson.isBlank()) {
            profile
        } else {
            val decoded = runCatching { NativeBackupCodec.json.decodeFromString<GlobalMinusOneState>(profile.stateJson) }.getOrNull()
                ?: return@map profile
            val cleanedSnapshot = decoded.copy(
                pages = decoded.pages.map { p -> p.copy(components = sanitizeShellComponents(p.components)) },
                subPanelLibrary = decoded.subPanelLibrary.map { tpl ->
                    tpl.copy(pages = tpl.pages.map { p -> p.copy(components = sanitizeShellComponents(p.components)) })
                }
            )
            if (cleanedSnapshot == decoded) profile
            else profile.copy(stateJson = NativeBackupCodec.json.encodeToString(cleanedSnapshot))
        }
    }
    return state.copy(
        pages = sanitizedPages,
        subPanelLibrary = sanitizedLibrary,
        profiles = sanitizedProfiles
    )
}

@Immutable
data class BackupCompatibilityIssue(
    val title: String,
    val detail: String
)

fun buildBackupSourceSummary(state: GlobalMinusOneState, mode: BackupExportMode): BackupSourceSummary =
    BackupSourceSummary(
        panelWidthPercent = state.themeConfig.panelWidthPercent,
        panelHeightPercent = state.themeConfig.panelHeightPercent,
        gridColumns = state.advancedConfig.gridColumns,
        exportMode = mode.name
    )

/** Detects layout/dimension mismatches between imported backup and the host device's panel settings. */
fun analyzeBackupCompatibility(
    incoming: GlobalMinusOneState,
    current: GlobalMinusOneState,
    selectedPageIds: Set<String> = emptySet()
): List<BackupCompatibilityIssue> {
    val issues = mutableListOf<BackupCompatibilityIssue>()
    val inW = incoming.themeConfig.panelWidthPercent
    val curW = current.themeConfig.panelWidthPercent
    if (abs(inW - curW) >= 10) {
        issues += BackupCompatibilityIssue(
            title = "面板宽度差异较大",
            detail = "备份面板宽度为 ${inW}%（当前 ${curW}%），直接保留当前尺寸时部分卡片或文字可能折行/挤压。"
        )
    }
    val inH = incoming.themeConfig.panelHeightPercent
    val curH = current.themeConfig.panelHeightPercent
    if (abs(inH - curH) >= 12) {
        issues += BackupCompatibilityIssue(
            title = "面板高度差异较大",
            detail = "备份面板高度为 ${inH}%（当前 ${curH}%），内容上下留白或滚动区域将变化。"
        )
    }
    val inCols = incoming.advancedConfig.gridColumns
    val curCols = current.advancedConfig.gridColumns
    if (inCols != curCols) {
        issues += BackupCompatibilityIssue(
            title = "自由画布网格列基数不同",
            detail = "备份为 $inCols 列，当前为 $curCols 列；自由排版页面的坐标跨度比例可能偏移。"
        )
    }
    val inspectedPages = incoming.pages.filter { selectedPageIds.isEmpty() || it.id in selectedPageIds }
    val denseGrid = inspectedPages.firstOrNull { it.layoutType == PageLayoutType.GRID && it.gridColumnCount >= 6 }
    if (denseGrid != null && curW < 55) {
        issues += BackupCompatibilityIssue(
            title = "固定多列网格与窄面板冲突风险",
            detail = "所选网格页「${denseGrid.name}」固定了 ${denseGrid.gridColumnCount} 列，但当前面板宽仅 ${curW}%。"
        )
    }
    return issues
}

private fun collectReferencedSubPanels(
    seedPages: List<PageConfig>,
    allSubPanels: Map<String, SubPanelConfig>,
    allPages: List<PageConfig>
): Pair<List<PageConfig>, List<SubPanelConfig>> {
    val keptPages = LinkedHashMap<String, PageConfig>()
    val keptSubPanels = LinkedHashMap<String, SubPanelConfig>()
    val queue = ArrayDeque<String>()

    fun enqueueFromComponents(items: List<ComponentItem>) {
        items.forEach { item ->
            if (item.type == ComponentType.SUB_PANEL_ENTRY && item.subPanelId.isNotBlank()) {
                queue.addLast(item.subPanelId)
            }
            enqueueFromComponents(item.containerComponents)
        }
    }

    seedPages.forEach { p ->
        keptPages[p.id] = p
        if (p.subPanelId.isNotBlank()) queue.addLast(p.subPanelId)
        enqueueFromComponents(p.components)
    }
    while (queue.isNotEmpty()) {
        val spId = queue.removeFirst()
        if (spId in keptSubPanels) continue
        val sp = allSubPanels[spId] ?: continue
        keptSubPanels[spId] = sp
        allPages.filter { it.subPanelId == spId }.forEach { p ->
            if (p.id !in keptPages) {
                keptPages[p.id] = p
                enqueueFromComponents(p.components)
            }
        }
    }
    return keptPages.values.toList() to keptSubPanels.values.toList()
}

/** Filters [state] cleanly according to export [mode] and [selectedPageIds]. */
fun scopeStateForExport(
    state: GlobalMinusOneState,
    mode: BackupExportMode,
    selectedPageIds: Set<String> = emptySet()
): GlobalMinusOneState {
    val clean = state.copy(editMode = false, selectedComponentId = null)
    return when (mode) {
        BackupExportMode.FULL -> clean
        BackupExportMode.SETTINGS_ONLY -> {
            val placeholder = PageConfig(name = "设置模板占位页", layoutType = PageLayoutType.GRID)
            clean.copy(
                pages = listOf(placeholder),
                currentPageIndex = 0,
                subPanels = emptyList(),
                todos = emptyList(),
                clipboardHistory = emptyList(),
                boundWidgets = emptyList()
            )
        }
        BackupExportMode.SELECTED_PAGES -> {
            val seed = clean.pages.filter { it.id in selectedPageIds }.ifEmpty {
                listOfNotNull(clean.pages.getOrNull(clean.currentPageIndex) ?: clean.pages.firstOrNull())
            }
            val (collectedPages, collectedSubPanels) = collectReferencedSubPanels(
                seed,
                clean.subPanels.associateBy { it.id },
                clean.pages
            )
            val normalizedPages = if (collectedPages.any { it.subPanelId.isEmpty() }) {
                collectedPages
            } else {
                listOf(collectedPages.first().copy(subPanelId = "")) + collectedPages.drop(1)
            }
            clean.copy(
                pages = normalizedPages,
                currentPageIndex = 0,
                subPanels = collectedSubPanels,
                todos = emptyList(),
                clipboardHistory = emptyList()
            )
        }
    }
}

private fun remapComponentTreeIds(items: List<ComponentItem>, subPanelIdMap: Map<String, String>): List<ComponentItem> =
    items.map { item ->
        item.copy(
            id = UUID.randomUUID().toString(),
            subPanelId = subPanelIdMap[item.subPanelId] ?: item.subPanelId,
            containerActions = item.containerActions.map { it.copy(id = UUID.randomUUID().toString()) },
            containerComponents = remapComponentTreeIds(item.containerComponents, subPanelIdMap)
        )
    }

/**
 * Merges user-selected modules and pages from [incoming] into [current] cleanly while preserving
 * `validateState` invariants (at least one main page, unique IDs, valid sub-panel references).
 */
fun mergeSelectedBackupState(
    current: GlobalMinusOneState,
    incoming: GlobalMinusOneState,
    selection: BackupImportSelection
): GlobalMinusOneState {
    val baseTheme = if (selection.importThemeAndPanel) {
        if (selection.applySourcePanelGeometry) {
            incoming.themeConfig
        } else {
            incoming.themeConfig.copy(
                panelWidthPercent = current.themeConfig.panelWidthPercent,
                panelHeightPercent = current.themeConfig.panelHeightPercent,
                panelHorizontalOffsetPercent = current.themeConfig.panelHorizontalOffsetPercent,
                panelVerticalPositionPercent = current.themeConfig.panelVerticalPositionPercent,
                edgeMarginPercent = current.themeConfig.edgeMarginPercent,
                coverStatusBar = current.themeConfig.coverStatusBar
            )
        }
    } else if (selection.applySourcePanelGeometry) {
        current.themeConfig.copy(
            panelWidthPercent = incoming.themeConfig.panelWidthPercent,
            panelHeightPercent = incoming.themeConfig.panelHeightPercent,
            panelHorizontalOffsetPercent = incoming.themeConfig.panelHorizontalOffsetPercent,
            panelVerticalPositionPercent = incoming.themeConfig.panelVerticalPositionPercent,
            edgeMarginPercent = incoming.themeConfig.edgeMarginPercent,
            coverStatusBar = incoming.themeConfig.coverStatusBar
        )
    } else {
        current.themeConfig
    }

    val chosenSeed = if (selection.selectedPageIds.isEmpty()) emptyList() else incoming.pages.filter { it.id in selection.selectedPageIds }
    val (nextPages, nextSubPanels, nextPageIndex) = if (chosenSeed.isEmpty()) {
        Triple(current.pages, current.subPanels, current.currentPageIndex)
    } else if (selection.pageImportMode == PageImportMode.REPLACE_ALL) {
        val (keptPages, keptSubs) = collectReferencedSubPanels(
            chosenSeed,
            incoming.subPanels.associateBy { it.id },
            incoming.pages
        )
        val mainSafePages = if (keptPages.any { it.subPanelId.isEmpty() }) {
            keptPages
        } else {
            listOf(keptPages.first().copy(subPanelId = "")) + keptPages.drop(1)
        }
        val referencedSubIds = mainSafePages.mapNotNullTo(HashSet()) { it.subPanelId.takeIf(String::isNotEmpty) }
        Triple(mainSafePages, keptSubs.filter { it.id in referencedSubIds }, 0)
    } else {
        val (keptPages, keptSubs) = collectReferencedSubPanels(
            chosenSeed,
            incoming.subPanels.associateBy { it.id },
            incoming.pages
        )
        val subPanelIdMap = keptSubs.associate { it.id to UUID.randomUUID().toString() }
        val remappedSubs = keptSubs.map { sp -> sp.copy(id = subPanelIdMap.getValue(sp.id)) }
        val curMainIndex = current.currentPageIndex.coerceIn(0, (current.pages.size - 1).coerceAtLeast(0))
        val targetOwnerSubPanel = current.pages.getOrNull(curMainIndex)?.subPanelId.orEmpty()
        val remappedPages = keptPages.mapIndexed { idx, p ->
            val mappedOwner = when {
                selection.pageImportMode == PageImportMode.REPLACE_CURRENT && idx == 0 -> targetOwnerSubPanel
                p.subPanelId.isNotEmpty() -> subPanelIdMap[p.subPanelId] ?: p.subPanelId
                else -> ""
            }
            p.copy(
                id = UUID.randomUUID().toString(),
                subPanelId = mappedOwner,
                components = remapComponentTreeIds(p.components, subPanelIdMap)
            )
        }
        val combinedPages = if (selection.pageImportMode == PageImportMode.REPLACE_CURRENT) {
            val head = remappedPages.first()
            val tail = remappedPages.drop(1)
            val replaced = current.pages.toMutableList().apply { this[curMainIndex] = head }
            replaced + tail
        } else {
            current.pages + remappedPages
        }
        val referencedSubIds = combinedPages.mapNotNullTo(HashSet()) { it.subPanelId.takeIf(String::isNotEmpty) }
        val combinedSubs = (current.subPanels + remappedSubs)
            .distinctBy { it.id }
            .filter { it.id in referencedSubIds }
        val focusIdx = if (selection.pageImportMode == PageImportMode.REPLACE_CURRENT) {
            curMainIndex
        } else {
            current.pages.size.coerceAtMost(combinedPages.lastIndex)
        }
        Triple(combinedPages, combinedSubs, focusIdx)
    }

    val fullRestore = selection.restoreFullUserData
    val nextCards = if (fullRestore) incoming.cards else current.cards
    val activeWidgetIds = buildSet {
        nextPages.flatMap { it.components.flattenComponents() }.forEach { item ->
            if (item.type == ComponentType.APP_WIDGET && item.widgetId >= 0) add(item.widgetId)
        }
        nextCards.forEach { card ->
            val id = card.widgetId
            if (card.type == com.example.globalminusone.data.CardType.APP_WIDGET && id != null && id >= 0) add(id)
        }
    }
    val mergedWidgets = if (chosenSeed.isEmpty()) {
        current.boundWidgets
    } else {
        (current.boundWidgets + incoming.boundWidgets)
            .distinctBy { it.appWidgetId }
            .filter { it.appWidgetId in activeWidgetIds }
    }

    return current.copy(
        editMode = false,
        selectedComponentId = null,
        themeConfig = baseTheme,
        appearanceConfig = if (selection.importThemeAndPanel) incoming.appearanceConfig else current.appearanceConfig,
        advancedConfig = if (selection.importThemeAndPanel) incoming.advancedConfig else current.advancedConfig,
        triggerConfig = if (selection.importTriggersAndBall) incoming.triggerConfig else current.triggerConfig,
        suspensionBallConfig = if (selection.importTriggersAndBall) incoming.suspensionBallConfig else current.suspensionBallConfig,
        profiles = if (selection.importProfiles) incoming.profiles else current.profiles,
        activeProfileId = if (selection.importProfiles) incoming.activeProfileId else current.activeProfileId,
        favoriteApps = if (fullRestore) incoming.favoriteApps else current.favoriteApps,
        appAppearanceOverrides = if (fullRestore) incoming.appAppearanceOverrides else current.appAppearanceOverrides,
        todos = if (fullRestore) incoming.todos else current.todos,
        clipboardHistory = if (fullRestore) incoming.clipboardHistory else current.clipboardHistory,
        fileBrowserConfig = if (fullRestore) incoming.fileBrowserConfig else current.fileBrowserConfig,
        subPanelLibrary = if (fullRestore) incoming.subPanelLibrary else current.subPanelLibrary,
        noteText = if (fullRestore) incoming.noteText else current.noteText,
        cards = nextCards,
        quickActions = if (fullRestore) incoming.quickActions else current.quickActions,
        pages = nextPages,
        subPanels = nextSubPanels,
        currentPageIndex = nextPageIndex.coerceIn(0, nextPages.lastIndex),
        boundWidgets = mergedWidgets
    )
}
