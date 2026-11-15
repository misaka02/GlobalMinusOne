package com.example.globalminusone.data

import java.util.ArrayDeque
import java.util.IdentityHashMap

const val MAX_COMPONENT_TREE_DEPTH = 32
private const val MAX_COMPONENT_TREE_NODES = 10_000

/** Iterative traversal also terminates for an invalid in-memory object cycle. */
fun List<ComponentItem>.flattenComponents(): List<ComponentItem> {
    val result = mutableListOf<ComponentItem>()
    val pending = ArrayDeque<ComponentItem>()
    val visited = IdentityHashMap<ComponentItem, Boolean>()
    asReversed().forEach(pending::addLast)
    while (pending.isNotEmpty()) {
        val item = pending.removeLast()
        if (visited.put(item, true) != null) continue
        result += item
        item.containerComponents.asReversed().forEach(pending::addLast)
    }
    return result
}

fun List<ComponentItem>.findComponent(id: String): ComponentItem? {
    val pending = ArrayDeque<ComponentItem>()
    val visited = IdentityHashMap<ComponentItem, Boolean>()
    asReversed().forEach(pending::addLast)
    while (pending.isNotEmpty()) {
        val item = pending.removeLast()
        if (visited.put(item, true) != null) continue
        if (item.id == id) return item
        item.containerComponents.asReversed().forEach(pending::addLast)
    }
    return null
}

fun List<ComponentItem>.componentPath(id: String): List<String>? {
    val pending = ArrayDeque<Pair<ComponentItem, List<String>>>()
    asReversed().forEach { pending.addLast(it to emptyList()) }
    val visited = IdentityHashMap<ComponentItem, Boolean>()
    while (pending.isNotEmpty()) {
        val (item, ancestors) = pending.removeLast()
        if (visited.put(item, true) != null) continue
        val path = ancestors + item.id
        if (item.id == id) return path
        item.containerComponents.asReversed().forEach { pending.addLast(it to path) }
    }
    return null
}

fun validateComponentTrees(pages: List<PageConfig>) {
    val ids = mutableSetOf<String>()
    val pending = ArrayDeque<Pair<ComponentItem, Int>>()
    pages.forEach { page -> page.components.forEach { pending.addLast(it to 1) } }
    var count = 0
    while (pending.isNotEmpty()) {
        val (item, depth) = pending.removeLast()
        require(++count <= MAX_COMPONENT_TREE_NODES) { "组件数量超出安全上限" }
        require(depth <= MAX_COMPONENT_TREE_DEPTH) { "容器嵌套超过安全深度" }
        require(item.id.isNotBlank() && ids.add(item.id)) { "组件ID重复、为空或存在循环" }
        require(item.containerComponents.isEmpty() || item.type.isContainerType()) { "仅容器可包含子卡片" }
        item.containerComponents.forEach { pending.addLast(it to depth + 1) }
    }
}

internal fun List<ComponentItem>.transformComponent(id: String, transform: (ComponentItem) -> ComponentItem): List<ComponentItem> = map { item ->
    if (item.id == id) transform(item).also { require(it.id == id) { "不能修改组件ID" } }
    else if (item.containerComponents.isEmpty()) item
    else item.copy(containerComponents = item.containerComponents.transformComponent(id, transform))
}

internal fun List<ComponentItem>.removeComponentTree(id: String): List<ComponentItem> = filterNot { it.id == id }.map { item ->
    if (item.containerComponents.isEmpty()) item else item.copy(containerComponents = item.containerComponents.removeComponentTree(id))
}

internal fun List<ComponentItem>.mapComponentTree(transform: (ComponentItem) -> ComponentItem): List<ComponentItem> = map { item ->
    transform(if (item.containerComponents.isEmpty()) item else item.copy(containerComponents = item.containerComponents.mapComponentTree(transform)))
}

fun GlobalMinusOneState.savedWidgetIds(): Set<Int> =
    (pages.flatMap { it.components.flattenComponents() }.map { it.widgetId } + cards.mapNotNull { it.widgetId })
        .filter { it >= 0 }.toSet()

/** Remove legacy mirrors and bindings only for widget IDs no longer reachable after this mutation. */
internal fun GlobalMinusOneState.withRemovedComponentWidgets(previous: GlobalMinusOneState): GlobalMinusOneState {
    val previousIds = previous.pages.flatMap { it.components.flattenComponents() }.map { it.widgetId }.filter { it >= 0 }.toSet()
    val remainingIds = pages.flatMap { it.components.flattenComponents() }.map { it.widgetId }.filter { it >= 0 }.toSet()
    val removed = previousIds - remainingIds
    return if (removed.isEmpty()) this else copy(
        cards = cards.filterNot { it.widgetId in removed },
        boundWidgets = boundWidgets.filterNot { it.appWidgetId in removed },
        selectedComponentId = selectedComponentId?.takeIf { id -> pages.any { it.components.findComponent(id) != null } }
    )
}


/** Check nesting before the recursive serializer sees untrusted imported JSON. Braces inside strings do not count. */
internal fun checkConfigurationJsonEnvelope(raw: String) {
    require(raw.length <= 8_000_000) { "配置文件过大" }
    var depth = 0
    var quoted = false
    var escaped = false
    raw.forEach { char ->
        if (quoted) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') quoted = false
        } else when (char) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 128) { "配置嵌套超过安全深度" } }
            '}', ']' -> depth--
        }
    }
}
