package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.*
import com.example.globalminusone.data.ContainerPopupBounds

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
}

internal val LocalContainerPresentation = staticCompositionLocalOf<ContainerPresentation?> { null }

@Composable
internal fun compactCurtainFractions(): Map<String, Float> =
    LocalContainerPresentation.current?.fractions() ?: emptyMap()
