package com.example.globalminusone.data

/** Tree migration only. A result is a candidate mutation, not persistence acceptance. */
internal object ComponentTransferOperations {
    data class Candidate(val pages: List<PageConfig>, val placedId: String)

    /** Page indices are checked by the repository; it validates the candidate tree before committing. */
    fun transfer(
        originalPages: List<PageConfig>, sourceIndex: Int, targetIndex: Int,
        componentId: String, targetContainerId: String?, copy: Boolean, columns: Int,
        nextY: (PageConfig, String?) -> Int,
        newId: () -> String = { java.util.UUID.randomUUID().toString() }
    ): Candidate? {
        val original = originalPages[sourceIndex].components.findComponent(componentId) ?: return null
        // A widget host ID cannot be shared by two cards; copying widgets requires a new system binding.
        if (copy && listOf(original).flattenComponents().any { it.widgetId >= 0 }) return null
        if (targetContainerId != null && listOf(original).findComponent(targetContainerId) != null) return null
        val destination = targetContainerId?.let { originalPages[targetIndex].components.findComponent(it) }
        if (targetContainerId != null && destination?.type?.isContainerType() != true) return null
        fun duplicate(node: ComponentItem): ComponentItem = node.copy(id = newId(),
            containerActions = node.containerActions.map { it.copy(id = newId()) },
            containerComponents = node.containerComponents.map(::duplicate))
        val picked = if (copy) duplicate(original) else original
        val pages = originalPages.toMutableList()
        if (!copy) pages[sourceIndex] = pages[sourceIndex].copy(components = pages[sourceIndex].components.removeComponentTree(componentId))
        val target = pages[targetIndex]
        if (targetContainerId != null && target.components.findComponent(targetContainerId) == null) return null
        val width = picked.w.coerceIn(1, columns)
        val placed = picked.copy(x = picked.x.coerceIn(0, columns - width), w = width,
            y = nextY(target, targetContainerId))
        val components = if (targetContainerId == null) target.components + placed else
            target.components.transformComponent(targetContainerId) { it.copy(containerComponents = it.containerComponents + placed) }
        pages[targetIndex] = target.copy(components = components)
        return Candidate(pages, placed.id)
    }
}
