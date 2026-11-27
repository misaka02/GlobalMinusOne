package com.example.globalminusone.data.policy

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.operations.SubPanelNavStack
import kotlinx.serialization.Serializable

/** Controls which page/panel opens each time the minus-one overlay is summoned. */
@Serializable
enum class PanelOpenPageMode(val label: String) {
    LAST_VISITED("打开上次离开的面板"),
    LOCKED_PAGE("锁定打开指定页面")
}

data class PanelOpenPageTarget(
    val pageIndex: Int,
    val subPanelStack: SubPanelNavStack
)

data class PanelOpenPageChoice(
    val pageId: String,
    val label: String,
    val subtitle: String,
    val isSubPanel: Boolean
)

/**
 * Reconstructs the shortest cycle-free ancestor [SubPanelNavStack] from root to [subPanelId]
 * so Back navigation inside nested sub-panels returns to the parent sub-panel instead of root.
 */
fun resolveSubPanelStackForId(
    state: GlobalMinusOneState,
    subPanelId: String
): SubPanelNavStack {
    if (subPanelId.isBlank() || state.subPanels.none { it.id == subPanelId }) {
        return SubPanelNavStack()
    }
    val validIds = state.subPanels.mapTo(HashSet()) { it.id }
    val adjacency = buildSubPanelAdjacency(state, validIds)
    val chain = findShortestSubPanelChain(adjacency, subPanelId) ?: listOf(subPanelId)
    return SubPanelNavStack(chain)
}

fun resolveInitialSubPanelStack(state: GlobalMinusOneState): SubPanelNavStack {
    resolvePanelOpenTarget(state)?.let { return it.subPanelStack }
    val activeSubId = state.pages.getOrNull(state.currentPageIndex)?.subPanelId.orEmpty()
    return resolveSubPanelStackForId(state, activeSubId)
}

/**
 * Resolves the ancestor `scopeId -> parentPageId` mapping along [stack] so Back navigation
 * returns to the exact page containing the child entry rather than always landing on page 0.
 */
fun resolveSubPanelEntryPageMemory(
    state: GlobalMinusOneState,
    stack: SubPanelNavStack
): Map<String, String> {
    if (stack.stack.isEmpty()) return emptyMap()
    val memory = LinkedHashMap<String, String>(stack.stack.size)
    var parentScope = ""
    for (childId in stack.stack) {
        val entryPageId = findEntryPageInScope(state, parentScope, childId)
        if (entryPageId != null) {
            memory[parentScope] = entryPageId
        }
        parentScope = childId
    }
    return memory
}

private fun findEntryPageInScope(
    state: GlobalMinusOneState,
    ownerScope: String,
    targetSubId: String
): String? = state.pages.firstOrNull { page ->
    page.subPanelId == ownerScope && containsSubPanelLink(page.components, targetSubId)
}?.id

private fun containsSubPanelLink(items: List<ComponentItem>, targetSubId: String): Boolean =
    items.any { item ->
        (item.type == ComponentType.SUB_PANEL_ENTRY && item.subPanelId == targetSubId) ||
            (item.containerComponents.isNotEmpty() && containsSubPanelLink(item.containerComponents, targetSubId))
    }

/**
 * Returns the exact target page and full ancestor sub-panel stack to snap to when
 * [PanelOpenPageMode.LOCKED_PAGE] is enabled, or `null` in [PanelOpenPageMode.LAST_VISITED].
 */
fun resolvePanelOpenTarget(state: GlobalMinusOneState): PanelOpenPageTarget? {
    if (state.advancedConfig.panelOpenPageMode != PanelOpenPageMode.LOCKED_PAGE) return null
    val lockedId = state.advancedConfig.lockedOpenPageId
    if (lockedId.isNotBlank()) {
        val targetIdx = state.pages.indexOfFirst { it.id == lockedId }
        if (targetIdx >= 0) {
            val targetPage = state.pages[targetIdx]
            if (targetPage.subPanelId.isEmpty()) {
                return PanelOpenPageTarget(targetIdx, SubPanelNavStack())
            }
            if (state.subPanels.any { it.id == targetPage.subPanelId }) {
                return PanelOpenPageTarget(
                    pageIndex = targetIdx,
                    subPanelStack = resolveSubPanelStackForId(state, targetPage.subPanelId)
                )
            }
        }
    }
    val fallbackRoot = state.pages.indexOfFirst { it.subPanelId.isEmpty() }.coerceAtLeast(0)
    return PanelOpenPageTarget(fallbackRoot, SubPanelNavStack())
}

private fun buildSubPanelAdjacency(
    state: GlobalMinusOneState,
    validIds: Set<String>
): Map<String, List<String>> {
    val outgoing = HashMap<String, MutableList<String>>()
    for (page in state.pages) {
        val scope = page.subPanelId
        if (scope.isNotEmpty() && scope !in validIds) continue
        val bucket = outgoing.getOrPut(scope) { ArrayList() }
        collectSubPanelLinks(page.components, scope, validIds, bucket)
    }
    return outgoing
}

private fun collectSubPanelLinks(
    items: List<ComponentItem>,
    ownerScope: String,
    validIds: Set<String>,
    out: MutableList<String>
) {
    for (item in items) {
        if (item.type == ComponentType.SUB_PANEL_ENTRY &&
            item.subPanelId in validIds &&
            item.subPanelId != ownerScope &&
            item.subPanelId !in out
        ) {
            out += item.subPanelId
        }
        if (item.containerComponents.isNotEmpty()) {
            collectSubPanelLinks(item.containerComponents, ownerScope, validIds, out)
        }
    }
}

private fun findShortestSubPanelChain(
    adjacency: Map<String, List<String>>,
    targetId: String
): List<String>? {
    val parentOf = HashMap<String, String>()
    val queue = ArrayDeque<String>()
    queue.addLast("")
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (next in adjacency[current].orEmpty()) {
            if (next in parentOf) continue
            parentOf[next] = current
            if (next == targetId) {
                return buildChainFromParents(parentOf, targetId)
            }
            queue.addLast(next)
        }
    }
    return null
}

private fun buildChainFromParents(
    parentOf: Map<String, String>,
    targetId: String
): List<String> {
    val chain = ArrayList<String>()
    var cursor: String? = targetId
    while (!cursor.isNullOrEmpty()) {
        chain += cursor
        cursor = parentOf[cursor]
    }
    chain.reverse()
    return chain
}

fun buildPanelOpenPageChoices(state: GlobalMinusOneState): List<PanelOpenPageChoice> {
    val choices = ArrayList<PanelOpenPageChoice>(state.pages.size)
    val rootPages = state.pages.filter { it.subPanelId.isEmpty() }
    rootPages.forEachIndexed { idx, page ->
        choices += PanelOpenPageChoice(
            pageId = page.id,
            label = formatRootPageLabel(idx, page),
            subtitle = "主面板 · ${page.layoutType.label}",
            isSubPanel = false
        )
    }
    state.subPanels.forEach { sub ->
        val subPages = state.pages.filter { it.subPanelId == sub.id }
        val subName = sub.name.ifBlank { "未命名子面板" }
        subPages.forEachIndexed { idx, page ->
            val suffix = page.name.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            choices += PanelOpenPageChoice(
                pageId = page.id,
                label = "子面板「$subName」第 ${idx + 1} 页$suffix",
                subtitle = "子面板页面 · ${page.layoutType.label}",
                isSubPanel = true
            )
        }
    }
    return choices
}

fun describePanelOpenPageSetting(state: GlobalMinusOneState): String {
    if (state.advancedConfig.panelOpenPageMode != PanelOpenPageMode.LOCKED_PAGE) {
        return "${PanelOpenPageMode.LAST_VISITED.label}（记住页码与子面板）"
    }
    val match = buildPanelOpenPageChoices(state).firstOrNull {
        it.pageId == state.advancedConfig.lockedOpenPageId
    }
    return if (match != null) {
        "固定打开：${match.label}"
    } else {
        "固定打开：第 1 页（默认主页）"
    }
}

private fun formatRootPageLabel(index: Int, page: PageConfig): String {
    val name = page.name.trim()
    return if (name.isNotEmpty()) "第 ${index + 1} 页 · $name" else "第 ${index + 1} 页"
}
