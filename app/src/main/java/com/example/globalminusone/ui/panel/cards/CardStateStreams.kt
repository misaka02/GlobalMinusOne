package com.example.globalminusone.ui.panel.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import com.example.globalminusone.data.GlobalMinusOneState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Create once in the capability owner's remember block. These cold projections own no job or
 * repository state: each composition cancels its subscription when it leaves or changes owner.
 */
internal class CardStateStreams(private val source: StateFlow<GlobalMinusOneState>) {
    val currentMenu: AppMenuReadState get() = source.value.appMenuReadState()
    val currentLayout: CardLayoutSnapshot get() = source.value.cardLayoutSnapshot()

    val menu: Flow<AppMenuReadState> = source.map { it.appMenuReadState() }.distinctUntilChanged()
    val layout: Flow<CardLayoutSnapshot> = source.map { it.cardLayoutSnapshot() }
        .distinctUntilChanged { previous, next -> previous.hasSameLayoutInputs(next) }

    @Composable
    fun observeMenu(): AppMenuReadState = key(this) {
        menu.collectAsState(initial = currentMenu).value
    }

    @Composable
    fun observeLayout(): CardLayoutSnapshot {
        // Snapshot's data-class equals traverses pages. The flow has already compared the exact
        // layout inputs, so use reference equality here rather than repeating a tree comparison.
        // Key the initial value as well as the effect: a new owner must not render the old layout.
        val observed = remember(this) { mutableStateOf(currentLayout, referentialEqualityPolicy()) }
        LaunchedEffect(this) {
            layout.collect { next ->
                // The cold flow immediately replays source.value. Avoid invalidating the
                // synchronous initial layout again, without invoking data-class tree equality.
                if (!observed.value.hasSameLayoutInputs(next)) observed.value = next
            }
        }
        return observed.value
    }
}

private fun GlobalMinusOneState.appMenuReadState() = AppMenuReadState(
    advancedConfig.layoutLocked, advancedConfig.autoCloseOnWidgetClick
)

/** Pages are published by replacement, including nested edits; never recurse through leaf trees. */
internal fun CardLayoutSnapshot.hasSameLayoutInputs(other: CardLayoutSnapshot): Boolean =
    pages === other.pages && editMode == other.editMode && layoutLocked == other.layoutLocked &&
        selectedComponentId == other.selectedComponentId && gridColumns == other.gridColumns &&
        heightUnitDp == other.heightUnitDp && animationDurationMs == other.animationDurationMs
