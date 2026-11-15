package com.example.globalminusone.ui.panel

import androidx.compose.ui.draw.drawBehind

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.componentDisplayRows
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.SystemController
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FreeLayoutPage(
    page: PageConfig,
    pageIndex: Int,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    systemController: SystemController,
    widgetHostManager: WidgetHostManager,
    onBrightnessAdjusting: (Boolean) -> Unit,
    onRequestClose: () -> Unit,
    onOpenMainApp: () -> Unit,
    onOpenEditDialog: (ComponentItem) -> Unit,
    onOpenBottomNineKeyBar: () -> Unit,
    onOpenAddComponent: () -> Unit
) {
    val density = LocalDensity.current
    val isEditMode = state.editMode && !state.advancedConfig.layoutLocked
    val gestureOwnership = LocalPanelGestureOwnership.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val pageWidthPx = constraints.maxWidth.toFloat()
        SideEffect { repository.updatePanelLayoutMetrics(with(density) { pageWidthPx.toDp().value }, density.fontScale) }
        val gridCols = state.advancedConfig.gridColumns.coerceIn(8, 64)
        val stepX = pageWidthPx / gridCols.toFloat()
        val stepY = if (state.advancedConfig.heightUnitDp > 0) {
            with(density) { state.advancedConfig.heightUnitDp.dp.toPx() }
        } else stepX

        val stepYDp = with(density) { stepY.toDp().value }
        val expansion = compactCurtainFractions()
        val displayItems = remember(page.components, pageWidthPx, gridCols, stepYDp, density.fontScale, state.advancedConfig.heightUnitDp, expansion) {
            fun measure(fractions: Map<String, Float>) = page.components.map { original ->
                val widthDp = with(density) { (original.w.coerceIn(1, gridCols) * stepX).toDp().value }
                original.copy(w = original.w.coerceIn(1, gridCols), h = componentDisplayRows(original, widthDp, stepYDp, gridCols,
                    state.advancedConfig.heightUnitDp, density.fontScale, fractions))
            }
            val measured = measure(expansion)
            if (expansion.isEmpty()) measured else com.example.globalminusone.data.displaceExpandedContainerRows(
                page.components, measure(emptyMap()), measured)
        }
        val maxRow = (displayItems.maxOfOrNull { it.y + it.h } ?: 35) + 12
        val viewportMemory = LocalPageViewport.current ?: remember(page.id) { PageViewportMemory() }
        val pageActive = LocalPageViewportActive.current
        val viewportRevision = viewportMemory.restoreRevision
        val scrollState = remember(page.id, viewportRevision) { initialFreeViewport(viewportMemory) }
        val scrollTarget = rememberPanelScrollTarget(scrollState)
        val visit = remember(page.id) { ViewportVisit() }
        val resumeOffset = if (pageActive && !visit.active) viewportMemory.freeScrollPx else null
        SideEffect {
            resumeOffset?.let { scrollState.dispatchRawDelta((it - scrollState.value).toFloat()) }
            visit.active = pageActive
        }
        LaunchedEffect(page.id, pageActive, viewportRevision) {
            if (pageActive) snapshotFlow { scrollState.value }.collect {
                if (visit.active && viewportMemory.restoreRevision == viewportRevision) viewportMemory.freeScrollPx = it
            }
        }
        var dragCanvasEnd by remember(page.id) { mutableFloatStateOf(0f) }
        val dragViewport = rememberEditDragViewport(scrollBy = { scrollState.scrollBy(it) }, growDown = { delta ->
            if (scrollState.value >= scrollState.maxValue - 2) {
                dragCanvasEnd = maxOf(dragCanvasEnd, scrollState.value + constraints.maxHeight + delta + stepY * 3)
            }
        })
        LaunchedEffect(dragViewport.pointer == null) { if (dragViewport.pointer == null) dragCanvasEnd = 0f }
        // A smaller last-card footprint must not clamp scroll and move the released card away
        // from its live preview. This display floor shrinks naturally when the user scrolls up.
        val bottomSafePx = with(density) { 44.dp.toPx() }
        val canvasHeightPx = maxOf(dragCanvasEnd, stableContentExtent(maxRow * stepY + bottomSafePx, scrollState.value, constraints.maxHeight.toFloat()))
        val canvasHeightDp = with(density) { canvasHeightPx.toDp() }

        var activeInteractingId by remember { mutableStateOf<String?>(null) }
        // Resizing changes the visible extent, never the content origin or saved scroll offset.

        val scrollModifier = Modifier.verticalScroll(scrollState, flingBehavior = rememberPanelFlingBehavior(),
            enabled = activeInteractingId == null && !gestureOwnership.exclusiveVerticalActive)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dragViewport.bounds = it.boundsInRoot() }
                .then(scrollModifier)
                .then(
                    if (!state.advancedConfig.layoutLocked) {
                        Modifier.cardLongPressToEdit("free-background", !isEditMode, ignoreOwnedGesture = true) {
                            repository.setEditMode(true)
                        }
                    } else Modifier
                )
        ) {
            // Offset children do not contribute their positions to measurement. Keep the same
            // scrollable extent in both modes; decoration must never determine reachability.
            Spacer(Modifier.fillMaxWidth().height(canvasHeightDp))
            // 1. 点阵参考网格背景 (缓存坐标并通过单次 drawPoints 批量绘制，降低编辑态重绘开销)
            if (isEditMode) {
                val gridColor = LocalPanelPalette.current.textSecondary.copy(alpha = .18f)
                val gridDots = remember(gridCols, maxRow, stepX, stepY) {
                    val points = ArrayList<Offset>((gridCols + 1) * (maxRow + 1))
                    for (c in 0..gridCols) {
                        val cx = c * stepX
                        for (r in 0..maxRow) {
                            points.add(Offset(cx, r * stepY))
                        }
                    }
                    points
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp)
                ) {
                    if (gridDots.isNotEmpty()) {
                        drawPoints(
                            points = gridDots,
                            pointMode = androidx.compose.ui.graphics.PointMode.Points,
                            color = gridColor,
                            strokeWidth = 2.6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }

            val activeSelId = if (isEditMode) state.selectedComponentId else null

            // 3. 自由网格卡片列表渲染
            val folderReturn = LocalFolderReturnState.current
            CompositionLocalProvider(LocalEditDragViewport provides dragViewport,
                LocalPanelScrollTarget provides scrollTarget) {
            page.components.forEachIndexed { itemIndex, item -> key(item.id) {
                val displayItem = displayItems[itemIndex]
                val isSelected = isEditMode && activeSelId == item.id

                FreeLayoutComponentSlot(
                    item = displayItem,
                    itemIndex = itemIndex,
                    pageIndex = pageIndex,
                    gridCols = gridCols,
                    stepX = stepX,
                    stepY = stepY,
                    pageWidthPx = pageWidthPx,
                    canvasHeightPx = canvasHeightPx,
                    maxResizeRows = 120,
                    isEditMode = isEditMode,
                    isSelected = isSelected,
                    onSelect = {
                        if (isSelected && item.type == ComponentType.FOLDER) {
                            folderReturn?.openNow(item.id)
                        } else {
                            repository.selectComponent(item.id)
                        }
                    },
                    onInteracting = { interacting ->
                        activeInteractingId = if (interacting) item.id else null
                    },
                    onCommitLayout = { newX, newY, newW, newH, resizeWidth, resizeHeight ->
                        val latest = repository.state.value
                        val targetPage = latest.pages.indexOfFirst { it.id == page.id }
                        if (targetPage >= 0 && canCommitLayout(latest.editMode, latest.advancedConfig.layoutLocked)) repository.updateComponent(targetPage, item.id) {
                            commitCardGeometry(it, newX, (newY - (displayItem.y - item.y)).coerceAtLeast(0), newW, newH, resizeWidth, resizeHeight)
                        }
                    },
                    onOpenEditDialog = { onOpenEditDialog(item) },
                    onEnterEditMode = {
                        repository.setEditMode(true)
                        repository.selectComponent(item.id)
                    },
                    content = {
                        Box(
                            Modifier.fillMaxSize().ownPanelScrollGestures(
                                enabled = item.type.ownsPanelScrolling() && !isEditMode,
                                lockFromDown = com.example.globalminusone.data.isSliderBarType(item.type),
                                axis = panelScrollAxisForComponent(item)
                            )
                        ) {
                            RenderComponentItem(
                                item = item,
                                pageIndex = pageIndex,
                                state = state,
                                repository = repository,
                                systemController = systemController,
                                widgetHostManager = widgetHostManager,
                                isEditMode = isEditMode,
                                isSelected = isSelected,
                                onBrightnessAdjusting = onBrightnessAdjusting,
                                onRequestClose = onRequestClose,
                                onOpenMainApp = onOpenMainApp,
                                onOpenEditDialog = onOpenEditDialog,
                                onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                                onOpenAddComponent = onOpenAddComponent
                            )
                        }
                    }
                )
            } }
        }
    }
}

}

// -----------------------------------------------------------------------------------------
// 单个自由网格组件挂载槽位 (含吸附、拖拽、缩放手柄、十字参考线、绿色 HUD)
// -----------------------------------------------------------------------------------------

@Composable
fun FreeLayoutComponentSlot(
    item: ComponentItem,
    itemIndex: Int,
    pageIndex: Int,
    gridCols: Int,
    stepX: Float,
    stepY: Float,
    pageWidthPx: Float,
    canvasHeightPx: Float,
    maxResizeRows: Int = 120,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInteracting: (Boolean) -> Unit,
    onCommitLayout: (newX: Int, newY: Int, newW: Int, newH: Int, resizeWidth: Boolean, resizeHeight: Boolean) -> Unit,
    onOpenEditDialog: () -> Unit,
    onEnterEditMode: (() -> Unit)? = null,
    dragReorders: Boolean = false,
    allowResize: Boolean = true,
    animatePlacement: Boolean = false,
    onStationaryLongPress: (() -> Unit)? = null,
    onLongPressPreview: (() -> Unit)? = null,
    onLongPressCancel: (() -> Unit)? = null,
    dragOutsideEdit: Boolean = false,
    onContentTap: (() -> Unit)? = null,
    onDragPreview: ((Int, Int) -> Unit)? = null,
    displayBoundsPx: androidx.compose.ui.geometry.Rect? = null,
    onDragPreviewPx: ((Float, Float) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val layoutLocked = LocalLayoutLocked.current
    val effectiveEditMode = isEditMode && !layoutLocked
    val dragEnabled = effectiveEditMode || (dragOutsideEdit && !layoutLocked)
    val selectionColor = LocalPanelPalette.current.accent
    val density = LocalDensity.current
    val holdOwnership = LocalPanelGestureOwnership.current

    val currentItem by rememberUpdatedState(item)
    val currentStepX by rememberUpdatedState(stepX)
    val currentStepY by rememberUpdatedState(stepY)
    val currentGridCols by rememberUpdatedState(gridCols)
    val currentOnCommitLayout by rememberUpdatedState(onCommitLayout)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnInteracting by rememberUpdatedState(onInteracting)
    val currentOnOpenEditDialog by rememberUpdatedState(onOpenEditDialog)
    val currentOnEnterEditMode by rememberUpdatedState(onEnterEditMode)
    val currentOnStationaryLongPress by rememberUpdatedState(onStationaryLongPress)
    val currentOnLongPressPreview by rememberUpdatedState(onLongPressPreview)
    val currentOnLongPressCancel by rememberUpdatedState(onLongPressCancel)
    val currentOnContentTap by rememberUpdatedState(onContentTap)
    val currentEditMode by rememberUpdatedState(effectiveEditMode)
    val currentOnDragPreview by rememberUpdatedState(onDragPreview)
    val currentOnDragPreviewPx by rememberUpdatedState(onDragPreviewPx)
    val currentDisplayBounds by rememberUpdatedState(displayBoundsPx)
    val dragViewport = LocalEditDragViewport.current
    val currentViewport by rememberUpdatedState(dragViewport)
    val dragTargetCoordinates = remember(item.id) { arrayOfNulls<LayoutCoordinates>(1) }
    val dragHostView = androidx.compose.ui.platform.LocalView.current
    val motionScope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val releaseOffset = remember(item.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragScrollStart by remember(item.id) { mutableFloatStateOf(0f) }
    var dragOrigin by remember(item.id) { mutableStateOf(Offset.Zero) }

    var dragDx by remember(item.id) { mutableFloatStateOf(0f) }
    var dragDy by remember(item.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(item.id) { mutableStateOf(false) }

    var resizeDw by remember(item.id) { mutableFloatStateOf(0f) }
    var resizeDh by remember(item.id) { mutableFloatStateOf(0f) }
    var resizeDx by remember(item.id) { mutableFloatStateOf(0f) }
    var resizeDy by remember(item.id) { mutableFloatStateOf(0f) }
    var isResizing by remember(item.id) { mutableStateOf(false) }

    val pageActive = LocalPageViewportActive.current
    LaunchedEffect(effectiveEditMode, isSelected, pageActive) {
        if (!effectiveEditMode || !isSelected || !pageActive) {
            if (isDragging) currentViewport?.end()
            isDragging = false; dragDx = 0f; dragDy = 0f
            isResizing = false; resizeDx = 0f; resizeDy = 0f; resizeDw = 0f; resizeDh = 0f
            currentOnInteracting(false)
        }
    }
    DisposableEffect(item.id) { onDispose {
        if (isDragging) currentViewport?.end()
        currentOnInteracting(false)
    } }

    fun computeDraggedX(dx: Float = dragDx): Int {
        val sX = if (currentStepX > 0f) currentStepX else 1f
        val cols = currentGridCols
        val itemW = currentItem.w
        val rawX = (((if (isDragging) dragOrigin.x else currentItem.x * sX) + dx) / sX).roundToInt()
        return rawX.coerceIn(0, (cols - itemW).coerceAtLeast(0))
    }

    fun computeDraggedY(dy: Float = dragDy + ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)): Int {
        val sY = if (currentStepY > 0f) currentStepY else 1f
        val rawY = (((if (isDragging) dragOrigin.y else currentItem.y * sY) + dy) / sY).roundToInt()
        return rawY.coerceAtLeast(0)
    }

    fun computeResizedBounds(
        dx: Float = resizeDx,
        dy: Float = resizeDy,
        dw: Float = resizeDw,
        dh: Float = resizeDh
    ): IntArray {
        val sX = if (currentStepX > 0f) currentStepX else 1f
        val sY = if (currentStepY > 0f) currentStepY else 1f
        val cols = currentGridCols

        val newX = if (dx != 0f) {
            val calcX = ((currentItem.x * sX + dx) / sX).roundToInt()
            calcX.coerceIn(0, (currentItem.x + currentItem.w - 2).coerceAtLeast(0))
        } else {
            currentItem.x
        }

        val newY = if (dy != 0f) {
            val calcY = ((currentItem.y * sY + dy) / sY).roundToInt()
            calcY.coerceIn(0, (currentItem.y + currentItem.h - 2).coerceAtLeast(0))
        } else {
            currentItem.y
        }

        val deltaLeft = currentItem.x - newX
        val deltaRight = (dw / sX).roundToInt()
        val newW = (currentItem.w + deltaLeft + deltaRight).coerceIn(2, (cols - newX).coerceAtLeast(2))

        val deltaTop = currentItem.y - newY
        val deltaBottom = (dh / sY).roundToInt()
        val newH = (currentItem.h + deltaTop + deltaBottom).coerceIn(2, maxResizeRows.coerceIn(2, 120))

        return intArrayOf(newX, newY, newW, newH)
    }

    val currentBounds = if (isDragging) {
        intArrayOf(computeDraggedX(), computeDraggedY(), currentItem.w, currentItem.h)
    } else if (isResizing) {
        computeResizedBounds()
    } else {
        intArrayOf(currentItem.x, currentItem.y, currentItem.w, currentItem.h)
    }

    val liveX = currentBounds[0]
    val liveY = currentBounds[1]
    val liveW = currentBounds[2]
    val liveH = currentBounds[3]

    val displayOffset = displayBoundsPx?.let { Offset(it.left - item.x * stepX, it.top - item.y * stepY) } ?: Offset.Zero
    val displayWidthDelta = displayBoundsPx?.let { it.width - item.w * stepX } ?: 0f
    val displayHeightDelta = displayBoundsPx?.let { it.height - item.h * stepY } ?: 0f
    val animMs = LocalPanelPalette.current.themeConfig.animationDurationMs.coerceIn(0, 800)
    val latestAnimMs by rememberUpdatedState(animMs)
    val sizeAnimSpecFloat = if (isResizing || animMs <= 0) snap<Float>() else tween<Float>(durationMillis = animMs)
    val animatedWidth by animateFloatAsState((liveW * stepX + displayWidthDelta).coerceAtLeast(1f), sizeAnimSpecFloat, label = "card-width")
    val animatedHeight by animateFloatAsState((liveH * stepY + displayHeightDelta).coerceAtLeast(1f), sizeAnimSpecFloat, label = "card-height")
    val itemWidthDp = with(density) { animatedWidth.toDp() }
    val itemHeightDp = with(density) { animatedHeight.toDp() }
    fun draggedPixels(): Offset = Offset(
        (dragOrigin.x + dragDx).coerceIn(0f, (pageWidthPx - (currentDisplayBounds?.width ?: (currentItem.w * currentStepX))).coerceAtLeast(0f)),
        (dragOrigin.y + dragDy + ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)).coerceAtLeast(0f))
    val positionTarget = if (isDragging) draggedPixels() else Offset(liveX * stepX, liveY * stepY) + displayOffset
    val placement by animateOffsetAsState(positionTarget,
        if (isDragging || !dragEnabled || !animatePlacement || animMs <= 0) snap() else tween(durationMillis = animMs), label = "container-position")
    val drawnPosition = if (isDragging) draggedPixels() else if (animatePlacement) placement else positionTarget + releaseOffset.value
    val snapPosition = (if (dragReorders) Offset(item.x * stepX, item.y * stepY) else Offset(liveX * stepX, liveY * stepY)) + displayOffset
    LaunchedEffect(isDragging, liveX, liveY) { if (isDragging) currentOnDragPreview?.invoke(liveX, liveY) }
    LaunchedEffect(isDragging, drawnPosition) { if (isDragging) currentOnDragPreviewPx?.invoke(drawnPosition.x, drawnPosition.y) }
    if (isDragging) Box(Modifier.offset { IntOffset(snapPosition.x.roundToInt(), snapPosition.y.roundToInt()) }
        .size(itemWidthDp, itemHeightDp).background(LocalPanelPalette.current.accent.copy(alpha = .10f), RoundedCornerShape(10.dp))
        .border(2.dp, LocalPanelPalette.current.accent.copy(alpha = .55f), RoundedCornerShape(10.dp)))

    Box(
        modifier = Modifier
            .offset { IntOffset(drawnPosition.x.roundToInt(), drawnPosition.y.roundToInt()) }
            .size(width = itemWidthDp, height = itemHeightDp)
            .zIndex(if (isDragging || isResizing) 2f else if (isSelected) 1f else 0f)
            // The edit detector claims scrolling only after a stationary hold.
            // An ordinary swipe on a card must still scroll or change pages.
            .graphicsLayer {
                rotationZ = item.rotationDeg.toFloat()
                shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                shape = RoundedCornerShape(10.dp)
            }
    ) {
        // 1. 卡片主体内容（底层渲染，普通模式下长按可唤起编辑模式）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .ownPanelScrollGestures(!effectiveEditMode && item.type.ownsPanelScrolling(),
                    lockFromDown = com.example.globalminusone.data.isSliderBarType(item.type),
                    axis = panelScrollAxisForComponent(item, itemWidthDp.value, itemHeightDp.value))
                .cardLongPressToEdit(item.id, !dragEnabled && !layoutLocked &&
                    currentOnEnterEditMode != null && !LocalInContainerChild.current &&
                    item.type !in setOf(ComponentType.APP_ICON, ComponentType.APP_DRAWER_CARD, ComponentType.RECENT_APPS_CARD, ComponentType.CONTAINER_BOX, ComponentType.FOLDER)) {
                    currentOnEnterEditMode?.invoke()
                }
        ) {
            content()
        }

        // 2. 编辑模式下：覆盖在 content() 上方的全尺寸透明触控拦截层
        // 核心：上层兄弟节点优先捕获触摸事件，杜绝子组件中 .clickable / Slider / BasicTextField 抢占拖拽！
        if (dragEnabled) {
            Box(
                modifier = Modifier
                    .then(if (effectiveEditMode) cardEditDragTarget(item) else Modifier.fillMaxSize())
                    .onGloballyPositioned { dragTargetCoordinates[0] = it }
                    .pointerInput(item.id, onStationaryLongPress != null) {
                        val screen = IntArray(2); val window = IntArray(2)
                        detectStationaryDragGestures(
                            ownership = holdOwnership,
                            positionInStableSpace = { point ->
                                dragHostView.getLocationOnScreen(screen)
                                dragHostView.getLocationInWindow(window)
                                val inWindow = dragTargetCoordinates[0]?.takeIf { it.isAttached }?.localToWindow(point) ?: point
                                inWindow + Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
                            },
                            onTap = { if (currentEditMode) currentOnSelect() else currentOnContentTap?.invoke() },
                            onStationaryLongPress = onStationaryLongPress?.let { { currentOnStationaryLongPress?.invoke() } },
                            onLongPressPreview = onLongPressPreview?.let { { currentOnLongPressPreview?.invoke() } },
                            onLongPressCancel = { currentOnLongPressCancel?.invoke() },
                            onDragStart = { point ->
                                if (currentEditMode) currentOnSelect()
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                dragScrollStart = currentViewport?.consumedScroll ?: 0f
                                dragOrigin = currentDisplayBounds?.let { Offset(it.left, it.top) }
                                    ?: Offset(currentItem.x * currentStepX, currentItem.y * currentStepY)
                                dragTargetCoordinates[0]?.takeIf { it.isAttached }?.localToRoot(point)?.let { currentViewport?.begin(it) }
                                motionScope.launch(start = CoroutineStart.UNDISPATCHED) { releaseOffset.snapTo(Offset.Zero) }
                                isDragging = true
                                currentOnInteracting(true)
                            },
                            onDragEnd = {
                                // UP may carry the final movement before a composition/frame can
                                // publish its preview. Commit that destination synchronously.
                                currentOnDragPreview?.invoke(computeDraggedX(), computeDraggedY())
                                draggedPixels().let { currentOnDragPreviewPx?.invoke(it.x, it.y) }
                                val finalX = if (dragReorders) currentItem.x else computeDraggedX()
                                val finalY = if (dragReorders) currentItem.y else computeDraggedY()
                                val releasedFrom = draggedPixels() - Offset(finalX * currentStepX, finalY * currentStepY)
                                motionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    if (latestAnimMs <= 0) {
                                        releaseOffset.snapTo(Offset.Zero)
                                    } else {
                                        releaseOffset.snapTo(releasedFrom)
                                        releaseOffset.animateTo(Offset.Zero, tween(durationMillis = latestAnimMs))
                                    }
                                }
                                currentOnCommitLayout(finalX, finalY, currentItem.w, currentItem.h, false, false)
                                currentViewport?.end()
                                isDragging = false
                                dragDx = 0f
                                dragDy = 0f
                                currentOnInteracting(false)
                            },
                            onDragCancel = {
                                val releasedFrom = draggedPixels() - Offset(currentItem.x * currentStepX, currentItem.y * currentStepY)
                                motionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    if (latestAnimMs <= 0) {
                                        releaseOffset.snapTo(Offset.Zero)
                                    } else {
                                        releaseOffset.snapTo(releasedFrom)
                                        releaseOffset.animateTo(Offset.Zero, tween(durationMillis = latestAnimMs))
                                    }
                                }
                                currentViewport?.end()
                                isDragging = false
                                dragDx = 0f
                                dragDy = 0f
                                currentOnInteracting(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDx += dragAmount.x
                                dragDy += dragAmount.y
                                currentViewport?.moveBy(dragAmount)
                            }
                        )
                    }
            ) { ContainerDragHint(item) }
        }

        // 4. 选中卡片视觉：红色高亮边框、四边缩放拉伸手柄与右下角缩放控制圆点、绿色 HUD 实时坐标显示
        if (effectiveEditMode && isSelected) {
            // 红色高亮边框
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(width = 2.dp, color = selectionColor, shape = RoundedCornerShape(8.dp))
            )

            if (allowResize) {
            // 顶部居中调节手柄
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-2).dp)
                    .size(48.dp)
                    .semantics { contentDescription = "拖动调整卡片上下边缘" }
                    .ownPanelScrollGestures(true, lockFromDown = true)
                    .drawSlimResizePill(selectionColor, horizontal = true)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                                currentOnInteracting(true)
                            },
                            onDragEnd = {
                                val bounds = computeResizedBounds()
                                currentOnCommitLayout(bounds[0], bounds[1], bounds[2], bounds[3],
                                    bounds[2] != currentItem.w, bounds[3] != currentItem.h)
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDragCancel = {
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeDy += dragAmount.y
                            }
                        )
                    }
            )

            // 底部居中调节手柄
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 2.dp)
                    .size(48.dp)
                    .semantics { contentDescription = "拖动调整卡片上下边缘" }
                    .ownPanelScrollGestures(true, lockFromDown = true)
                    .drawSlimResizePill(selectionColor, horizontal = true)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                                currentOnInteracting(true)
                            },
                            onDragEnd = {
                                val bounds = computeResizedBounds()
                                currentOnCommitLayout(bounds[0], bounds[1], bounds[2], bounds[3],
                                    bounds[2] != currentItem.w, bounds[3] != currentItem.h)
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDragCancel = {
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeDh += dragAmount.y
                            }
                        )
                    }
            )

            // 左侧居中调节手柄
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-2).dp)
                    .size(48.dp)
                    .semantics { contentDescription = "拖动调整卡片左右边缘" }
                    .ownPanelScrollGestures(true, lockFromDown = true)
                    .drawSlimResizePill(selectionColor, horizontal = false)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                                currentOnInteracting(true)
                            },
                            onDragEnd = {
                                val bounds = computeResizedBounds()
                                currentOnCommitLayout(bounds[0], bounds[1], bounds[2], bounds[3],
                                    bounds[2] != currentItem.w, bounds[3] != currentItem.h)
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDragCancel = {
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeDx += dragAmount.x
                            }
                        )
                    }
            )

            // 右侧居中调节手柄
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 2.dp)
                    .size(48.dp)
                    .semantics { contentDescription = "拖动调整卡片左右边缘" }
                    .ownPanelScrollGestures(true, lockFromDown = true)
                    .drawSlimResizePill(selectionColor, horizontal = false)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                                currentOnInteracting(true)
                            },
                            onDragEnd = {
                                val bounds = computeResizedBounds()
                                currentOnCommitLayout(bounds[0], bounds[1], bounds[2], bounds[3],
                                    bounds[2] != currentItem.w, bounds[3] != currentItem.h)
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDragCancel = {
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeDw += dragAmount.x
                            }
                        )
                    }
            )

            // Compact visual grip with an independent transparent touch target.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(48.dp)
                    .semantics { contentDescription = "拖动调整卡片宽高" }
                    .ownPanelScrollGestures(true, lockFromDown = true)
                    .pointerInput(item.id) {
                        detectDragGestures(
                            onDragStart = {
                                isResizing = true
                                currentOnInteracting(true)
                            },
                            onDragEnd = {
                                val bounds = computeResizedBounds()
                                currentOnCommitLayout(bounds[0], bounds[1], bounds[2], bounds[3],
                                    bounds[2] != currentItem.w, bounds[3] != currentItem.h)
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDragCancel = {
                                isResizing = false
                                resizeDx = 0f
                                resizeDy = 0f
                                resizeDw = 0f
                                resizeDh = 0f
                                currentOnInteracting(false)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeDw += dragAmount.x
                                resizeDh += dragAmount.y
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                CardResizeGripMark(selectionColor)
            }

            }
            // Coordinates are useful during a gesture; at rest they must not cover the title.
            if (isDragging || isResizing) {
            val hudOffsetY = if (liveY == 0) 4.dp else (-24).dp

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 2.dp, y = hudOffsetY),
                shape = RoundedCornerShape(4.dp),
                color = LocalPanelPalette.current.cardBackground
            ) {
                Text(
                    text = "位置 $liveX, $liveY · 尺寸 $liveW × $liveH",
                    color = LocalPanelPalette.current.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            }
        }
    }
}

private fun Modifier.drawSlimResizePill(color: Color, horizontal: Boolean): Modifier =
    drawBehind {
        val pillW = (if (horizontal) 20.dp else 4.dp).toPx()
        val pillH = (if (horizontal) 4.dp else 20.dp).toPx()
        val left = (size.width - pillW) * 0.5f
        val top = (size.height - pillH) * 0.5f
        val radius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(pillW, pillH),
            cornerRadius = radius
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(pillW, pillH),
            cornerRadius = radius,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8.dp.toPx())
        )
    }

// -----------------------------------------------------------------------------------------
// 线性布局页面 (LinearLayoutPage)
// -----------------------------------------------------------------------------------------

// -----------------------------------------------------------------------------------------
// 底部上滑手势条 (BottomSwipeUpBar)
// -----------------------------------------------------------------------------------------
