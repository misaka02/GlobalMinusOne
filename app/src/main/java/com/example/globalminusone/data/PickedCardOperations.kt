package com.example.globalminusone.data

/** Picker target identity and content construction, without persistence or Android measurements. */
internal object PickedCardOperations {
    data class Target(val existing: ComponentItem?, val container: ComponentItem?)

    fun validContentUri(uri: String): Boolean = runCatching {
        java.net.URI(uri).let { it.scheme == "content" && !it.authority.isNullOrBlank() }
    }.getOrDefault(false)

    fun resolveTarget(page: PageConfig, componentId: String?, containerId: String?, type: ComponentType): Target? {
        val existing = componentId?.let { id -> page.components.findComponent(id)?.takeIf { it.type == type } }
        val container = containerId?.let { page.components.findComponent(it)?.takeIf { node -> node.type.isContainerType() } }
        if (containerId != null && container == null) return null
        if (container != null && existing != null && container.containerComponents.findComponent(existing.id) == null) return null
        if (componentId != null && existing == null) return null
        return Target(existing, container)
    }

    /** URI and target must be checked first; measurements/preparation run only for newly created cards. */
    fun build(page: PageConfig, target: Target, type: ComponentType, title: String, uri: String,
        uris: List<String>, columns: Int, nextY: (String?) -> Int,
        prepareNewFile: (ComponentItem) -> ComponentItem): ComponentItem {
        val base = target.existing ?: ComponentItem(
            type = type,
            w = type.defaultW.coerceAtMost(columns),
            y = if (target.container != null) nextY(target.container.id)
                else if (page.layoutType == PageLayoutType.FREE) nextY(null) else 0
        )
        val readableBase = if (target.existing == null && type == ComponentType.FILE_SHORTCUT)
            prepareNewFile(base) else base
        val named = if (type == ComponentType.PHOTO_CUBE && target.existing != null) readableBase
            else readableBase.copy(title = title.ifBlank { type.label })
        return when (type) {
            ComponentType.FILE_SHORTCUT -> named.copy(intentUri = uri)
            ComponentType.PHOTO_CUBE -> named.copy(photoCubeUris = uris.toList())
            else -> named.copy(imageUri = uri)
        }
    }
}
