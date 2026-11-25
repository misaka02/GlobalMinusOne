package com.example.globalminusone.ui.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.example.globalminusone.ui.motion.MotionTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class FreeGridCellBounds(val x: Int, val y: Int, val w: Int, val h: Int)

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
        val expansion = compactCurtainFractions(page.components)
        val displayLayout = remember(page.components, pageWidthPx, stepX, stepY, gridCols, stepYDp, density.fontScale, state.advancedConfig.heightUnitDp, expansion) {
            fun isSmoothCompact(item: ComponentItem): Boolean =
                item.type == ComponentType.CONTAINER_BOX && item.containerCompact

            fun measureRows(fractions: Map<String, Float>) = page.components.map { original ->
                val widthDp = with(density) { (original.w.coerceIn(1, gridCols) * stepX).toDp().value }
                original.copy(w = original.w.coerceIn(1, gridCols), h = componentDisplayRows(original, widthDp, stepYDp, gridCols,
                    state.advancedConfig.heightUnitDp, density.fontScale, fractions))
            }

            fun measureHeightPx(item: ComponentItem, fractions: Map<String, Float>): Float {
                val clampedW = item.w.coerceIn(1, gridCols)
                val widthDp = with(density) { (clampedW * stepX).toDp().value }
                return if (isSmoothCompact(item)) {
                    with(density) {
                        com.example.globalminusone.data.componentDisplayHeightDp(
                            item = item.copy(w = clampedW),
                            outerWidthDp = widthDp,
                            gridColumns = gridCols,
                            configuredHeightUnitDp = state.advancedConfig.heightUnitDp,
                            fontScale = density.fontScale,
                            compactExpansion = fractions
                        ).coerceAtLeast(36f).dp.toPx()
                    }
                } else {
                    componentDisplayRows(
                        item.copy(w = clampedW),
                        widthDp,
                        stepYDp,
                        gridCols,
                        state.advancedConfig.heightUnitDp,
                        density.fontScale,
                        fractions
                    ) * stepY
                }
            }

            val baseRows = measureRows(emptyMap())
            val baseHeightById = page.components.associate { it.id to measureHeightPx(it, emptyMap()) }
            val shownHeightById = page.components.associate { it.id to measureHeightPx(it, expansion) }
            val baseRowMap = baseRows.associateBy { it.id }

            val placedTopPx = HashMap<String, Float>(page.components.size)
            page.components.sortedBy { it.y }.forEach { item ->
                val shiftPx = page.components.asSequence().filter { other ->
                    other.id != item.id && other.y < item.y &&
                        other.y + (baseRowMap[other.id]?.h ?: other.h) <= item.y
                }.mapNotNull { other ->
                    val top = placedTopPx[other.id] ?: (other.y * stepY)
                    val shownH = shownHeightById[other.id] ?: (other.h * stepY)
                    val baseBottom = other.y * stepY + (baseHeightById[other.id] ?: (other.h * stepY))
                    top + shownH - baseBottom
                }.maxOrNull()?.coerceAtLeast(0f) ?: 0f
                placedTopPx[item.id] = item.y * stepY + shiftPx
            }

            val items = page.components.map { item ->
                val clampedW = item.w.coerceIn(1, gridCols)
                val hPx = shownHeightById[item.id] ?: (item.h * stepY)
                val hRows = kotlin.math.ceil(hPx / stepY.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                val topPx = placedTopPx[item.id] ?: (item.y * stepY)
                val yRows = (topPx / stepY.coerceAtLeast(1f)).roundToInt()
                item.copy(w = clampedW, h = hRows, y = yRows)
            }
            val bounds = page.components.map { item ->
                val clampedW = item.w.coerceIn(1, gridCols)
                val topPx = placedTopPx[item.id] ?: (item.y * stepY)
                val hPx = shownHeightById[item.id] ?: (item.h * stepY)
                androidx.compose.ui.geometry.Rect(
                    left = item.x * stepX,
                    top = topPx,
                    right = (item.x + clampedW) * stepX,
                    bottom = topPx + hPx
                )
            }
            items to bounds
        }
        val displayItems = displayLayout.first
        val displayBoundsList = displayLayout.second
        val maxContentPx = displayBoundsList.maxOfOrNull { it.bottom } ?: (35f * stepY)
        val maxRow = kotlin.math.ceil(maxContentPx / stepY.coerceAtLeast(1f)).toInt() + 12
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
        val baseCanvasHeightPx = maxRow * stepY + bottomSafePx
        val viewportMaxHeightPx = constraints.maxHeight.toFloat()
        val canvasHeightPx by remember(scrollState, dragCanvasEnd, baseCanvasHeightPx, viewportMaxHeightPx) {
            derivedStateOf {
                maxOf(dragCanvasEnd, stableContentExtent(baseCanvasHeightPx, scrollState.value, viewportMaxHeightPx))
            }
        }
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
                val displayBounds = displayBoundsList[itemIndex]
                val isSelected = isEditMode && activeSelId == item.id

                FreeLayoutComponentSlot(
                    item = item,
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
                    displayBoundsPx = displayBounds,
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
    ): FreeGridCellBounds {
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

        return FreeGridCellBounds(newX, newY, newW, newH)
    }

    val currentBounds by remember {
        derivedStateOf {
            when {
                isDragging -> FreeGridCellBounds(computeDraggedX(), computeDraggedY(), currentItem.w, currentItem.h)
                isResizing -> computeResizedBounds()
                else -> FreeGridCellBounds(currentItem.x, currentItem.y, currentItem.w, currentItem.h)
            }
        }
    }

    val liveX = currentBounds.x
    val liveY = currentBounds.y
    val liveW = currentBounds.w
    val liveH = currentBounds.h

    val displayOffset = displayBoundsPx?.let { Offset(it.left - item.x * stepX, it.top - item.y * stepY) } ?: Offset.Zero
    val displayWidthDelta = displayBoundsPx?.let { it.width - item.w * stepX } ?: 0f
    val displayHeightDelta = displayBoundsPx?.let { it.height - item.h * stepY } ?: 0f
    val animMs = LocalPanelPalette.current.themeConfig.animationDurationMs.coerceIn(0, 800)
    val latestAnimMs by rememberUpdatedState(animMs)
    val targetWidthPx = (liveW * stepX + displayWidthDelta).coerceAtLeast(1f)
    val targetHeightPx = (liveH * stepY + displayHeightDelta).coerceAtLeast(1f)
    val animateSize = (effectiveEditMode || animatePlacement) && animMs > 0
    val sizeAnimSpecFloat = if (isResizing || !animateSize) snap<Float>() else MotionTokens.cardSettleSpring(animMs)
    val animatedWidthState = if (animateSize) animateFloatAsState(targetWidthPx, sizeAnimSpecFloat, label = "card-width") else null
    val animatedHeightState = if (animateSize) animateFloatAsState(targetHeightPx, sizeAnimSpecFloat, label = "card-height") else null
    fun draggedPixels(): Offset = Offset(
        (dragOrigin.x + dragDx).coerceIn(0f, (pageWidthPx - (currentDisplayBounds?.width ?: (currentItem.w * currentStepX))).coerceAtLeast(0f)),
        (dragOrigin.y + dragDy + ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)).coerceAtLeast(0f))
    val positionTarget = if (isDragging) draggedPixels() else Offset(liveX * stepX, liveY * stepY) + displayOffset
    val animatePosition = dragEnabled && animatePlacement && animMs > 0
    val placementState = if (animatePosition) animateOffsetAsState(positionTarget,
        if (isDragging) snap() else MotionTokens.cardOffsetSpring(animMs), label = "container-position") else null
    val snapPosition = (if (dragReorders) Offset(item.x * stepX, item.y * stepY) else Offset(liveX * stepX, liveY * stepY)) + displayOffset
    LaunchedEffect(isDragging, liveX, liveY) { if (isDragging) currentOnDragPreview?.invoke(liveX, liveY) }
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        snapshotFlow { draggedPixels() }.collect { currentOnDragPreviewPx?.invoke(it.x, it.y) }
    }
    if (isDragging) CardDropPlaceholder(
        offsetProvider = { IntOffset(snapPosition.x.roundToInt(), snapPosition.y.roundToInt()) },
        widthPx = { (animatedWidthState?.value ?: targetWidthPx).roundToInt() },
        heightPx = { (animatedHeightState?.value ?: targetHeightPx).roundToInt() },
        accentColor = selectionColor,
        fillAlpha = 0.10f
    )

    Box(
        modifier = Modifier
            .offset {
                val drawn = if (isDragging) draggedPixels()
                    else if (animatePlacement) (placementState?.value ?: positionTarget)
                    else positionTarget + releaseOffset.value
                IntOffset(drawn.x.roundToInt(), drawn.y.roundToInt())
            }
            .layout { measurable, _ ->
                // Read animated bounds in layout phase to avoid recomposing the card while settling.
                val w = (animatedWidthState?.value ?: targetWidthPx).roundToInt().coerceAtLeast(1)
                val h = (animatedHeightState?.value ?: targetHeightPx).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.placeRelative(0, 0) }
            }
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
        CardSlotBodyLayer(
            item = item,
            widthDp = with(density) { targetWidthPx.toDp().value },
            heightDp = with(density) { targetHeightPx.toDp().value },
            isEditMode = effectiveEditMode,
            dragEnabled = dragEnabled,
            layoutLocked = layoutLocked,
            allowCardLongPress = currentOnEnterEditMode != null && !LocalInContainerChild.current,
            onEnterEditMode = { currentOnEnterEditMode?.invoke() },
            content = content
        )

        // 2. 编辑模式下：覆盖在 content() 上方的全尺寸透明触控拦截层
        // 核心：上层兄弟节点优先捕获触摸事件，杜绝子组件中 .clickable / Slider / BasicTextField 抢占拖拽！
        if (dragEnabled) {
            Box(
                modifier = Modifier
                    .then(if (effectiveEditMode) cardEditDragTarget(item) else Modifier.fillMaxSize())
                    .onGloballyPositioned { dragTargetCoordinates[0] = it }
                    .pointerInput(item.id, onStationaryLongPress != null) {
                        detectStationaryDragGestures(
                            ownership = holdOwnership,
                            positionInStableSpaceFactory = stableScreenPositionFactory(dragHostView) { dragTargetCoordinates[0] },
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
                                        releaseOffset.animateTo(Offset.Zero, MotionTokens.cardOffsetSpring(latestAnimMs))
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
                                        releaseOffset.animateTo(Offset.Zero, MotionTokens.cardOffsetSpring(latestAnimMs))
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
                val startResize = {
                    isResizing = true
                    currentOnInteracting(true)
                }
                val finishResize = {
                    val b = computeResizedBounds()
                    currentOnCommitLayout(b.x, b.y, b.w, b.h, b.w != currentItem.w, b.h != currentItem.h)
                    isResizing = false
                    resizeDx = 0f; resizeDy = 0f; resizeDw = 0f; resizeDh = 0f
                    currentOnInteracting(false)
                }
                val cancelResize = {
                    isResizing = false
                    resizeDx = 0f; resizeDy = 0f; resizeDw = 0f; resizeDh = 0f
                    currentOnInteracting(false)
                }
                FreeCardResizeHandle(
                    alignment = Alignment.TopCenter,
                    offsetY = (-2).dp,
                    description = "拖动调整卡片上下边缘",
                    selectionColor = selectionColor,
                    pillHorizontal = true,
                    itemId = item.id,
                    onStart = startResize,
                    onDrag = { resizeDy += it.y },
                    onEnd = finishResize,
                    onCancel = cancelResize
                )
                FreeCardResizeHandle(
                    alignment = Alignment.BottomCenter,
                    offsetY = 2.dp,
                    description = "拖动调整卡片上下边缘",
                    selectionColor = selectionColor,
                    pillHorizontal = true,
                    itemId = item.id,
                    onStart = startResize,
                    onDrag = { resizeDh += it.y },
                    onEnd = finishResize,
                    onCancel = cancelResize
                )
                FreeCardResizeHandle(
                    alignment = Alignment.CenterStart,
                    offsetX = (-2).dp,
                    description = "拖动调整卡片左右边缘",
                    selectionColor = selectionColor,
                    pillHorizontal = false,
                    itemId = item.id,
                    onStart = startResize,
                    onDrag = { resizeDx += it.x },
                    onEnd = finishResize,
                    onCancel = cancelResize
                )
                FreeCardResizeHandle(
                    alignment = Alignment.CenterEnd,
                    offsetX = 2.dp,
                    description = "拖动调整卡片左右边缘",
                    selectionColor = selectionColor,
                    pillHorizontal = false,
                    itemId = item.id,
                    onStart = startResize,
                    onDrag = { resizeDw += it.x },
                    onEnd = finishResize,
                    onCancel = cancelResize
                )
                FreeCardResizeHandle(Alignment.BottomEnd, description = "拖动调整卡片宽高",
                    selectionColor = selectionColor, pillHorizontal = null, itemId = item.id,
                    onStart = startResize, onDrag = { resizeDw += it.x; resizeDh += it.y },
                    onEnd = finishResize, onCancel = cancelResize) {
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

// -----------------------------------------------------------------------------------------
// 线性布局页面 (LinearLayoutPage)
// -----------------------------------------------------------------------------------------

// -----------------------------------------------------------------------------------------
// 底部上滑手势条 (BottomSwipeUpBar)
// -----------------------------------------------------------------------------------------
