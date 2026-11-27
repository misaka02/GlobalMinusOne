package com.example.globalminusone.data.operations

import androidx.compose.runtime.Immutable
import java.util.UUID
import kotlinx.serialization.Serializable
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType

/**
 * Reusable snapshot template of a sub-panel's configuration and pages stored in
 * [GlobalMinusOneState.subPanelLibrary]. Selecting it creates an independent sub-panel copy.
 */
@Immutable
@Serializable
data class SubPanelTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "子面板模板",
    val config: SubPanelConfig = SubPanelConfig(),
    val pages: List<PageConfig> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun cardCount(): Int = pages.sumOf { it.components.flattenComponents().size }
}

internal object SubPanelLibraryOperations {
    const val MAX_TEMPLATES = 64

    fun saveSubPanelToLibrary(
        current: GlobalMinusOneState,
        subPanelId: String,
        templateName: String?,
        nowMs: Long,
        newId: () -> String
    ): Pair<GlobalMinusOneState, SubPanelTemplate>? {
        if (current.subPanelLibrary.size >= MAX_TEMPLATES) return null
        val source = current.subPanels.firstOrNull { it.id == subPanelId } ?: return null
        val cleanName = templateName?.trim().orEmpty().ifBlank { source.name.trim().ifBlank { "子面板模板" } }
        val templateId = newId()
        val sanitizedConfig = source.copy(id = templateId, name = cleanName)
        val snapshotPages = current.pages
            .filter { it.subPanelId == subPanelId }
            .map { page ->
                page.copy(
                    id = newId(),
                    subPanelId = "",
                    components = sanitizeComponentsForTemplate(page.components, subPanelId, newId)
                )
            }
        val template = SubPanelTemplate(
            id = templateId,
            name = cleanName,
            config = sanitizedConfig,
            pages = snapshotPages,
            updatedAt = nowMs
        )
        return current.copy(subPanelLibrary = current.subPanelLibrary + template) to template
    }

    fun instantiateFromLibrary(
        current: GlobalMinusOneState,
        templateId: String,
        customName: String?,
        newId: () -> String
    ): Pair<GlobalMinusOneState, SubPanelConfig>? {
        if (current.advancedConfig.layoutLocked || current.subPanels.size >= 64) return null
        val template = current.subPanelLibrary.firstOrNull { it.id == templateId } ?: return null
        val subPanelId = newId()
        val cleanName = customName?.trim().orEmpty().ifBlank { template.name.trim().ifBlank { "子面板 ${current.subPanels.size + 1}" } }
        val newSubPanel = template.config.copy(id = subPanelId, name = cleanName)
        val validExistingSubPanelIds = current.subPanels.mapTo(HashSet()) { it.id }
        val rawPages = template.pages.ifEmpty {
            listOf(PageOperations.createPage(PageLayoutType.GRID, cleanName, 1, subPanelId, newId))
        }
        val clonedPages = rawPages.mapIndexed { idx, page ->
            val pageName = page.name.trim().ifBlank { if (idx == 0) cleanName else "$cleanName ${idx + 1}" }
            page.copy(
                id = newId(),
                name = pageName,
                subPanelId = subPanelId,
                components = cloneComponentsForInstance(page.components, validExistingSubPanelIds, newId)
            )
        }
        val updated = current.copy(
            subPanels = current.subPanels + newSubPanel,
            pages = current.pages + clonedPages
        )
        return updated to newSubPanel
    }

    fun renameTemplate(current: GlobalMinusOneState, templateId: String, newName: String): GlobalMinusOneState? {
        val clean = newName.trim()
        if (clean.isEmpty()) return null
        val idx = current.subPanelLibrary.indexOfFirst { it.id == templateId }
        if (idx < 0) return null
        val existing = current.subPanelLibrary[idx]
        if (existing.name == clean) return null
        val next = existing.copy(name = clean, config = existing.config.copy(name = clean))
        val updated = current.subPanelLibrary.toMutableList().apply { this[idx] = next }
        return current.copy(subPanelLibrary = updated)
    }

    fun deleteTemplate(current: GlobalMinusOneState, templateId: String): GlobalMinusOneState? {
        if (current.subPanelLibrary.none { it.id == templateId }) return null
        return current.copy(subPanelLibrary = current.subPanelLibrary.filterNot { it.id == templateId })
    }

    private fun sanitizeComponentsForTemplate(
        items: List<ComponentItem>,
        sourceSubPanelId: String,
        newId: () -> String
    ): List<ComponentItem> = items
        .filterNot { it.type == ComponentType.SUB_PANEL_ENTRY && it.subPanelId == sourceSubPanelId }
        .map { item ->
            item.copy(
                id = newId(),
                widgetId = -1,
                containerComponents = sanitizeComponentsForTemplate(item.containerComponents, sourceSubPanelId, newId)
            )
        }

    private fun cloneComponentsForInstance(
        items: List<ComponentItem>,
        validSubPanelIds: Set<String>,
        newId: () -> String
    ): List<ComponentItem> = items.map { item ->
        val safeSubPanelId = if (item.type == ComponentType.SUB_PANEL_ENTRY && item.subPanelId !in validSubPanelIds) {
            ""
        } else {
            item.subPanelId
        }
        item.copy(
            id = newId(),
            widgetId = -1,
            subPanelId = safeSubPanelId,
            containerComponents = cloneComponentsForInstance(item.containerComponents, validSubPanelIds, newId)
        )
    }
}
