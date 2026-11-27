package com.example.globalminusone.ui.panel.container

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.ContainerPopupBounds

/** Presentation only: opening a curtain never rewrites saved card coordinates or sizes. */
internal class CompactCurtainState {
    var expanded by mutableStateOf(false)
    val progress = Animatable(0f)
}

internal class ContainerPresentation {
    private val curtains = mutableStateMapOf<String, CompactCurtainState>()
    var panelBounds by mutableStateOf<ContainerPopupBounds?>(null)
    fun curtain(id: String) = curtains.getOrPut(id) { CompactCurtainState() }
    fun fractions(): Map<String, Float> =
        curtains.mapValues { it.value.progress.value }.filterValues { it > 0f }

    fun fractionsFor(ids: Set<String>): Map<String, Float> {
        if (ids.isEmpty()) return emptyMap()
        val active = LinkedHashMap<String, Float>()
        for (id in ids) {
            val value = curtains[id]?.progress?.value ?: continue
            if (value > 0f) active[id] = value
        }
        return active
    }
}

internal val LocalContainerPresentation = staticCompositionLocalOf<ContainerPresentation?> { null }

@Composable
internal fun compactCurtainFractions(): Map<String, Float> =
    LocalContainerPresentation.current?.fractions() ?: emptyMap()

@Composable
internal fun compactCurtainFractions(components: List<ComponentItem>): Map<String, Float> {
    val compactIds = remember(components) {
        buildSet {
            for (item in components) {
                if (item.type == ComponentType.CONTAINER_BOX && item.containerCompact) add(item.id)
            }
        }
    }
    if (compactIds.isEmpty()) return emptyMap()
    return LocalContainerPresentation.current?.fractionsFor(compactIds) ?: emptyMap()
}
