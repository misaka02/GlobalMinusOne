package com.example.globalminusone.ui.panel.layout

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
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MAX_CARD_SPACING_DP
import com.example.globalminusone.data.MIN_CARD_SPACING_DP
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.policy.componentDisplayHeightDp
import com.example.globalminusone.data.isContainerType
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.isSliderBarType
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.gesture.LocalPanelScrollTarget
import com.example.globalminusone.ui.panel.cards.RenderComponentItem
import com.example.globalminusone.ui.panel.editor.cardLongPressToEdit
import com.example.globalminusone.ui.panel.container.compactCurtainFractions
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.gesture.ownsPanelScrolling
import com.example.globalminusone.ui.panel.gesture.panelScrollAxisForComponent
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingBehavior
import com.example.globalminusone.ui.panel.gesture.rememberPanelScrollTarget

@Composable
fun LinearLayoutPage(page: PageConfig, pageIndex: Int, state: GlobalMinusOneState, repository: MinusOneRepository,
    systemController: SystemController, widgetHostManager: WidgetHostManager,
    onBrightnessAdjusting: (Boolean) -> Unit, onRequestClose: () -> Unit, onOpenMainApp: () -> Unit,
    onOpenEditDialog: (ComponentItem) -> Unit, onOpenBottomNineKeyBar: () -> Unit, onOpenAddComponent: () -> Unit) {
    val editing = state.editMode && !state.advancedConfig.layoutLocked
    val viewport = LocalPageViewport.current ?: remember(page.id) { PageViewportMemory() }
    val componentIds = remember(page.components) { page.components.map { it.id } }
    val list = remember(page.id) { initialLinearViewport(viewport, componentIds) }
    val scrollTarget = rememberPanelScrollTarget(list)
    val visit = remember(page.id) { ViewportVisit() }
    val render: @Composable (ComponentItem) -> Unit = { item ->
        RenderComponentItem(item, pageIndex, state, repository, systemController, widgetHostManager, editing,
            state.selectedComponentId == item.id, onBrightnessAdjusting, onRequestClose, onOpenMainApp,
            onOpenEditDialog, onOpenBottomNineKeyBar, onOpenAddComponent)
    }
    if (editing) {
        val active = LocalPageViewportActive.current
        SideEffect {
            visit.active = active
            visit.editing = true
            visit.restoreRevision = viewport.restoreRevision
        }
        EditableLinearPage(page, pageIndex, state, repository, onOpenEditDialog, viewport.linearAnchor,
            onViewportAnchorChanged = { if (active) viewport.linearAnchor = it }, content = render)
        return
    }
    LinearPageViewportObserver(page.id, componentIds, viewport, list, visit)
    val density = LocalDensity.current
    val owner = LocalPanelGestureOwnership.current
    val expansion = compactCurtainFractions(page.components)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = state.advancedConfig.gridColumns.coerceIn(8, 64)
        val width = maxWidth.value - 24f
        val unit = state.advancedConfig.heightUnitDp.takeIf { it > 0 }?.toFloat() ?: width / columns
        val canScrollVertically by remember(list) {
            derivedStateOf { list.canScrollForward || list.canScrollBackward }
        }
        CompositionLocalProvider(LocalPanelScrollTarget provides scrollTarget) {
        LazyColumn(state = list, flingBehavior = rememberPanelFlingBehavior(), userScrollEnabled = !owner.exclusiveVerticalActive && canScrollVertically,
            modifier = Modifier.fillMaxSize().onSizeChanged { repository.updatePanelLayoutMetrics(with(density) { it.width.toDp().value }, density.fontScale) }
                .cardLongPressToEdit("linear-background", !state.advancedConfig.layoutLocked, ignoreOwnedGesture = true) { repository.setEditMode(true) },
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(state.themeConfig.cardSpacingDp.coerceIn(com.example.globalminusone.data.MIN_CARD_SPACING_DP, com.example.globalminusone.data.MAX_CARD_SPACING_DP).dp)) {
            items(page.components, key = { it.id }) { item ->
                val fraction = item.w.coerceIn(1, columns).toFloat() / columns
                val expected = componentDisplayHeightDp(item, width * fraction, columns, state.advancedConfig.heightUnitDp, density.fontScale, compactExpansion = expansion)
                Box(Modifier.fillMaxWidth(fraction).height(expected.dp)
                    .cardLongPressToEdit(
                        key = item.id,
                        enabled = !state.advancedConfig.layoutLocked && !item.type.isContainerType() &&
                            !com.example.globalminusone.data.policy.isSingleIconLauncherItem(item) &&
                            item.type !in setOf(ComponentType.APP_ICON, ComponentType.APP_DRAWER_CARD, ComponentType.APP_KEYBOARD_LAUNCHER, ComponentType.RECENT_APPS_CARD),
                        requireIdleOwnership = !com.example.globalminusone.data.policy.isSliderBarType(item.type) &&
                            item.type != ComponentType.APP_WIDGET
                    ) {
                        repository.setEditMode(true); repository.selectComponent(item.id)
                    }.ownPanelScrollGestures(
                        item.type.ownsPanelScrolling() && !com.example.globalminusone.data.policy.isSingleIconLauncherItem(item),
                        lockFromDown = com.example.globalminusone.data.policy.isSliderBarType(item.type) &&
                            !com.example.globalminusone.data.policy.isSingleIconLauncherItem(item),
                        axis = panelScrollAxisForComponent(item, width * fraction, expected)
                    )) { render(item) }
            }
        }
        }
    }
}
