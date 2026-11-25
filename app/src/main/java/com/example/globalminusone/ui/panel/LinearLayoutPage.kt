package com.example.globalminusone.ui.panel

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.globalminusone.data.*
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.SystemController
import kotlinx.coroutines.flow.collect

@Composable
fun LinearLayoutPage(page: PageConfig, pageIndex: Int, state: GlobalMinusOneState, repository: MinusOneRepository,
    systemController: SystemController, widgetHostManager: WidgetHostManager,
    onBrightnessAdjusting: (Boolean) -> Unit, onRequestClose: () -> Unit, onOpenMainApp: () -> Unit,
    onOpenEditDialog: (ComponentItem) -> Unit, onOpenBottomNineKeyBar: () -> Unit, onOpenAddComponent: () -> Unit) {
    val editing = state.editMode && !state.advancedConfig.layoutLocked
    val viewport = LocalPageViewport.current ?: remember(page.id) { PageViewportMemory() }
    val active = LocalPageViewportActive.current
    val componentIds = remember(page.components) { page.components.map { it.id } }
    val list = remember(page.id) { initialLinearViewport(viewport, componentIds) }
    val scrollTarget = rememberPanelScrollTarget(list)
    val visit = remember(page.id) { ViewportVisit() }
    val restoreRevision = viewport.restoreRevision
    val needsRestore = active && !editing && (!visit.active || visit.editing || visit.restoreRevision != restoreRevision)
    val restorePosition = if (needsRestore) {
        viewport.linearAnchor?.let { lazyPositionForAnchor(it, componentIds) }
    } else null
    SideEffect {
        if (needsRestore) {
            // A remeasure request applies the saved position before painting; no visible scrollTo effect.
            restorePosition?.let { (index, offset) ->
                visit.layoutAtRequest = list.layoutInfo
                list.requestScrollToItem(index, offset)
            }
        }
        visit.active = active; visit.editing = editing
        visit.restoreRevision = restoreRevision
    }
    val render: @Composable (ComponentItem) -> Unit = { item ->
        RenderComponentItem(item, pageIndex, state, repository, systemController, widgetHostManager, editing,
            state.selectedComponentId == item.id, onBrightnessAdjusting, onRequestClose, onOpenMainApp,
            onOpenEditDialog, onOpenBottomNineKeyBar, onOpenAddComponent)
    }
    if (editing) {
        EditableLinearPage(page, pageIndex, state, repository, onOpenEditDialog, viewport.linearAnchor,
            onViewportAnchorChanged = { if (active) viewport.linearAnchor = it }, content = render)
        return
    }
    val density = LocalDensity.current
    val owner = LocalPanelGestureOwnership.current
    val expansion = compactCurtainFractions(page.components)
    LaunchedEffect(page.id, componentIds, active, restoreRevision) {
        if (!active) return@LaunchedEffect
        snapshotFlow { list.layoutInfo }.collect { info ->
            if (visit.active && !visit.editing && info !== visit.layoutAtRequest && info.visibleItemsInfo.isNotEmpty()) {
                anchorFromLazyPosition(componentIds, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)?.let {
                    viewport.recordLinearAnchor(it, restoreRevision)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = state.advancedConfig.gridColumns.coerceIn(8, 64)
        val width = maxWidth.value - 24f
        val unit = state.advancedConfig.heightUnitDp.takeIf { it > 0 }?.toFloat() ?: width / columns
        CompositionLocalProvider(LocalPanelScrollTarget provides scrollTarget) {
        LazyColumn(state = list, flingBehavior = rememberPanelFlingBehavior(), userScrollEnabled = !owner.exclusiveVerticalActive,
            modifier = Modifier.fillMaxSize().onSizeChanged { repository.updatePanelLayoutMetrics(with(density) { it.width.toDp().value }, density.fontScale) }
                .cardLongPressToEdit("linear-background", !state.advancedConfig.layoutLocked, ignoreOwnedGesture = true) { repository.setEditMode(true) },
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(state.themeConfig.cardSpacingDp.coerceIn(com.example.globalminusone.data.MIN_CARD_SPACING_DP, com.example.globalminusone.data.MAX_CARD_SPACING_DP).dp)) {
            items(page.components, key = { it.id }) { item ->
                val fraction = item.w.coerceIn(1, columns).toFloat() / columns
                val expected = componentDisplayHeightDp(item, width * fraction, columns, state.advancedConfig.heightUnitDp, density.fontScale, compactExpansion = expansion)
                Box(Modifier.fillMaxWidth(fraction).height(expected.dp)
                    .cardLongPressToEdit(
                        key = item.id,
                        enabled = !state.advancedConfig.layoutLocked && !item.type.isContainerType() &&
                            !com.example.globalminusone.data.isSingleIconLauncherItem(item) &&
                            item.type !in setOf(ComponentType.APP_ICON, ComponentType.APP_DRAWER_CARD, ComponentType.APP_KEYBOARD_LAUNCHER, ComponentType.RECENT_APPS_CARD),
                        requireIdleOwnership = !com.example.globalminusone.data.isSliderBarType(item.type) &&
                            item.type != ComponentType.APP_WIDGET
                    ) {
                        repository.setEditMode(true); repository.selectComponent(item.id)
                    }.ownPanelScrollGestures(
                        item.type.ownsPanelScrolling() && !com.example.globalminusone.data.isSingleIconLauncherItem(item),
                        lockFromDown = com.example.globalminusone.data.isSliderBarType(item.type) &&
                            !com.example.globalminusone.data.isSingleIconLauncherItem(item),
                        axis = panelScrollAxisForComponent(item, width * fraction, expected)
                    )) { render(item) }
            }
        }
        }
    }
}
