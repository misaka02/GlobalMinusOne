package com.example.globalminusone.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.globalminusone.ui.AppIcons as Icons
import com.example.globalminusone.data.*
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun NestedContainerComponents(
    container: ComponentItem,
    pageIndex: Int,
    state: GlobalMinusOneState,
    repository: MinusOneRepository,
    onOpenEditDialog: (ComponentItem) -> Unit,
    content: @Composable (ComponentItem) -> Unit
) {
    NestedContainerContent(container, pageIndex, state.cardLayoutSnapshot(), rememberCardLayoutActions(repository),
        rememberCardDestinationPicker(repository), onOpenEditDialog, content)
}

@Composable
internal fun NestedContainerContent(
    container: ComponentItem, pageIndex: Int, state: CardLayoutSnapshot, layoutActions: CardLayoutActions,
    destinationPicker: CardDestinationPicker, onOpenEditDialog: (ComponentItem) -> Unit,
    content: @Composable (ComponentItem) -> Unit
) {
    val density = LocalDensity.current
    val folderEditing = LocalFolderEditing.current
    val folderEditAction = LocalFolderEditAction.current
    val openFolderProperties = LocalFolderOpenProperties.current
    val folderReturn = LocalFolderReturnState.current
    val openProperties: (ComponentItem) -> Unit = openFolderProperties ?: onOpenEditDialog
    val editing = containerEditingMode(state.editMode, if (folderEditAction != null) folderEditing else null,
        state.layoutLocked)
    val expansion = compactCurtainFractions(container.containerComponents)
    val scroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val scrollRoute = rememberContainerScrollRoute(scroll)
    val context = LocalContext.current
    var activeId by remember(container.id) { mutableStateOf<String?>(null) }
    var previewDropCell by remember(container.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val dropCellRef = remember(container.id) { arrayOfNulls<Pair<Int, Int>>(1) }
    var quickMenuChildId by remember(container.id) { mutableStateOf<String?>(null) }
    var transferChildRequest by remember(container.id) { mutableStateOf<Pair<String, Boolean>?>(null) }
    var destructiveRequest by remember(container.id) { mutableStateOf<CardDestructiveRequest?>(null) }
    val currentPageId = state.pages.getOrNull(pageIndex)?.id.orEmpty()
    LaunchedEffect(state.layoutLocked) {
        if (state.layoutLocked) {
            activeId = null
            previewDropCell = null
            dropCellRef[0] = null
            quickMenuChildId = null
            transferChildRequest = null
            destructiveRequest = null
        }
    }
    destructiveRequest?.let { request ->
        CardDestructiveConfirmationContent(request, layoutActions) { destructiveRequest = null }
    }
    transferChildRequest?.let { (childId, moving) ->
        destinationPicker.Show(
            excludedSubtreeId = childId,
            title = if (moving) "移动到" else "复制到",
            onDismiss = { transferChildRequest = null }
        ) { targetPageId, targetContainerId ->
            val saved = layoutActions.transferComponent(currentPageId, childId, targetPageId, targetContainerId, copy = !moving)
            if (!saved) Toast.makeText(context, "布局已锁定或目标已失效", Toast.LENGTH_SHORT).show()
            else Toast.makeText(context, "已保存到所选位置", Toast.LENGTH_SHORT).show()
            transferChildRequest = null
        }
    }
    val grid = container.containerLayout == ContainerLayoutType.GRID
    val iconColumns = if (LocalContainerPopupId.current == container.id && !editing && activeId == null) {
        containerPopupColumns(container, gridColumns = state.gridColumns)
    } else {
        container.containerColumns.coerceIn(1, 8)
    }
    // The viewport stays finite; child geometry is independent of that viewport.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = state.gridColumns.coerceIn(8, 64)
        val viewportWidthPx = (if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else with(density) { 320.dp.toPx() }).coerceAtLeast(columns.toFloat())
        val pageWidthDp = if (grid) containerGridContentWidthDp(container, with(density) { viewportWidthPx.toDp().value }, iconColumns)
            else with(density) { viewportWidthPx.toDp().value }
        val pageWidthPx = with(density) { pageWidthDp.dp.toPx() }
        val stepX = pageWidthPx / columns
        val stepY = if (state.heightUnitDp > 0) with(density) { state.heightUnitDp.dp.toPx() } else stepX
        val stepYDpValue = with(density) { stepY.toDp().value }
        val insideOwnPopup = LocalContainerPopupId.current == container.id
        val adaptiveRefWidthDp = if (grid && insideOwnPopup && !editing && activeId == null) {
            containerPopupAdaptiveReferenceInnerWidthDp(
                item = container,
                availableWidthDp = 1200f,
                gridColumns = columns
            )
        } else 0f
        val fallbackViewportPx = with(density) { CONTAINER_VIEWPORT_MAX_DP.dp.toPx() }
        val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.coerceIn(480, 2000).dp.toPx() }
        val viewportHeightPx = resolveContainerViewportHeightPx(
            hasBoundedHeight = constraints.hasBoundedHeight,
            maxConstraintHeightPx = constraints.maxHeight.toFloat(),
            insideOwnPopup = insideOwnPopup,
            screenHeightPx = screenHeightPx,
            maxFallbackPx = fallbackViewportPx
        )
        val resolvedRowGap = containerRowGapDp(container)
        val ordered = remember(
            container.containerComponents,
            grid,
            activeId,
            previewDropCell,
            columns,
            pageWidthDp,
            stepYDpValue,
            state.heightUnitDp,
            density.fontScale,
            iconColumns,
            expansion,
            container.containerItemGapDp,
            container.containerItemSizeDp,
            container.containerIconSizeDp,
            resolvedRowGap,
            container.containerItemPaddingVerticalDp,
            container.containerIconLabelGapDp
        ) {
            val dragId = activeId
            val cell = previewDropCell
            if (grid && dragId != null && cell != null) {
                resolveContainerGridDropPlacements(
                    children = container.containerComponents,
                    columns = columns,
                    widthDp = pageWidthDp,
                    stepYDp = stepYDpValue,
                    configuredHeightUnitDp = state.heightUnitDp,
                    fontScale = density.fontScale,
                    iconColumns = iconColumns,
                    compactExpansion = expansion,
                    itemGapDp = container.containerItemGapDp,
                    itemSizeDp = container.containerItemSizeDp,
                    iconSizeDp = container.containerIconSizeDp,
                    rowGapDp = resolvedRowGap,
                    itemPaddingVerticalDp = container.containerItemPaddingVerticalDp,
                    iconLabelGapDp = container.containerIconLabelGapDp,
                    movedId = dragId,
                    targetCol = cell.first,
                    targetRow = cell.second
                )
            } else {
                container.containerComponents
            }
        }
        val baseGridBounds = remember(
            container.containerComponents,
            grid,
            columns,
            pageWidthDp,
            stepYDpValue,
            state.heightUnitDp,
            density.fontScale,
            iconColumns,
            expansion,
            container.containerItemGapDp,
            container.containerItemSizeDp,
            container.containerIconSizeDp,
            resolvedRowGap,
            container.containerItemPaddingVerticalDp,
            container.containerIconLabelGapDp,
            adaptiveRefWidthDp
        ) {
            if (grid) {
                containerGridChildBounds(
                    container.containerComponents, columns, pageWidthDp, stepYDpValue, state.heightUnitDp,
                    density.fontScale, iconColumns, expansion, container.containerItemGapDp,
                    container.containerItemSizeDp, container.containerIconSizeDp, resolvedRowGap,
                    container.containerItemPaddingVerticalDp, container.containerIconLabelGapDp,
                    wReferenceWidthDp = adaptiveRefWidthDp
                ).associateBy { it.item.id }
            } else emptyMap()
        }
        val gridBounds = remember(
            ordered,
            grid,
            columns,
            pageWidthDp,
            stepYDpValue,
            state.heightUnitDp,
            density.fontScale,
            iconColumns,
            expansion,
            container.containerItemGapDp,
            container.containerItemSizeDp,
            container.containerIconSizeDp,
            resolvedRowGap,
            container.containerItemPaddingVerticalDp,
            container.containerIconLabelGapDp,
            adaptiveRefWidthDp
        ) {
            if (grid) {
                containerGridChildBounds(
                    ordered, columns, pageWidthDp, stepYDpValue, state.heightUnitDp,
                    density.fontScale, iconColumns, expansion, container.containerItemGapDp,
                    container.containerItemSizeDp, container.containerIconSizeDp, resolvedRowGap,
                    container.containerItemPaddingVerticalDp, container.containerIconLabelGapDp,
                    wReferenceWidthDp = adaptiveRefWidthDp
                ).associateBy { it.item.id }
            } else emptyMap()
        }
        val arranged = remember(gridBounds, columns, stepX, stepY, density.density) {
            gridBounds.mapValues { (_, bounds) ->
                bounds.item.copy(
                    x = (bounds.x * density.density / stepX).roundToInt(),
                    y = (bounds.y * density.density / stepY).roundToInt(),
                    w = ceil(bounds.width * density.density / stepX).toInt().coerceIn(1, columns),
                    h = ceil(bounds.height * density.density / stepY).toInt().coerceAtLeast(1)
                )
            }
        }
        val positionedChildren = remember(
            grid,
            arranged,
            container.containerComponents,
            columns,
            pageWidthPx,
            stepYDpValue,
            state.heightUnitDp,
            density.fontScale,
            expansion
        ) {
            if (grid) arranged else measureFreeContainerChildren(
                container.containerComponents,
                columns,
                with(density) { pageWidthPx.toDp().value },
                stepYDpValue,
                state.heightUnitDp,
                density.fontScale,
                expansion
            ).associateBy { it.id }
        }
        val childRows = remember(positionedChildren) { positionedChildren.mapValues { it.value.h } }
        val rows = (positionedChildren.values.maxOfOrNull { it.y + it.h } ?: 1).coerceAtLeast(1)
        var dragCanvasEnd by remember(container.id) { mutableFloatStateOf(0f) }
        val dragViewport = rememberEditDragViewport(scrollBy = { scroll.scrollBy(it) }, growDown = { delta ->
            if (scroll.value >= scroll.maxValue - 2) dragCanvasEnd = maxOf(dragCanvasEnd, scroll.value + viewportHeightPx + delta + stepY * 3)
        })
        LaunchedEffect(dragViewport.pointer == null) { if (dragViewport.pointer == null) dragCanvasEnd = 0f }
        // Preserve a scrolled drag canvas without inflating every short folder to the 480dp limit.
        val extraEditGridPx = if (grid && (editing || activeId != null)) with(density) { 84.dp.toPx() } else 0f
        val contentHeightPx = if (grid) ((gridBounds.values.maxOfOrNull { it.y + it.height } ?: 1f) * density.density + extraEditGridPx) else rows * stepY
        val effectiveViewportPx = minOf(contentHeightPx, viewportHeightPx)
        val canvasHeightPx by remember(scroll, dragCanvasEnd, contentHeightPx, effectiveViewportPx) {
            derivedStateOf {
                maxOf(dragCanvasEnd, stableContentExtent(contentHeightPx, scroll.value, effectiveViewportPx))
            }
        }
        val canvasHeightDp = with(density) { canvasHeightPx.toDp() }
        val viewportMaxHeight = with(density) { viewportHeightPx.toDp() }
        // Hold confirmation changes ownership before the release/menu or drag callback.
        // Keep the scroll/pointer nodes mounted across that transition; the confirmed
        // child detector consumes movement, so native scrolling yields without a restart.
        val viewport = Modifier.heightIn(max = viewportMaxHeight).containerPanelScroll(scrollRoute, enabled = true)
        Box(Modifier.fillMaxWidth().onGloballyPositioned { dragViewport.bounds = it.boundsInRoot() }.then(viewport).ownPanelScrollGestures(true, canScrollVertically = { dy -> if (dy < 0f) scroll.canScrollForward else scroll.canScrollBackward })) {
            val canScrollHorizontally = shouldEnableChildHorizontalScroll(grid, horizontalScroll.maxValue)
            val horizontalScrollEnabled = shouldEnableChildHorizontalScroll(
                allowed = grid,
                maxValue = horizontalScroll.maxValue,
                exclusiveBlocked = LocalPanelGestureOwnership.current.exclusiveHorizontalActive
            )
            Box(Modifier.fillMaxWidth().ownPanelScrollGestures(canScrollHorizontally,
                axis = PanelGestureAxis.Horizontal).horizontalScroll(horizontalScroll, enabled = horizontalScrollEnabled)) {
            Box(Modifier.width(pageWidthDp.dp).height(canvasHeightDp)) {
                if (grid && (editing || activeId != null)) {
                    val cellColor = LocalPanelPalette.current.textSecondary.copy(alpha = 0.16f)
                    val dropHighlight = LocalPanelPalette.current.accent
                    Canvas(Modifier.fillMaxSize()) {
                        val slots = iconColumns.coerceIn(1, 8)
                        val gapPx = container.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP).dp.toPx()
                        val rowGapPx = resolvedRowGap.dp.toPx()
                        val cellWidthPx = ((pageWidthPx - gapPx * (slots - 1)) / slots).coerceAtLeast(16f)
                        val defaultCellHeightPx = (baseGridBounds.values.firstOrNull()?.height ?: 74f) * density.density
                        val pitchXPx = (cellWidthPx + gapPx).coerceAtLeast(8.dp.toPx())
                        val pitchYPx = (defaultCellHeightPx + rowGapPx).coerceAtLeast(12.dp.toPx())
                        val totalGridRows = maxOf(
                            (baseGridBounds.values.maxOfOrNull { it.row + it.spanRows } ?: 0) + 1,
                            (previewDropCell?.second ?: 0) + 2,
                            2
                        )
                        val strokeW = 1.dp.toPx()
                        val radius = 8.dp.toPx()
                        for (r in 0 until totalGridRows) {
                            val rowEntries = baseGridBounds.values.filter { it.row == r }
                            val top = rowEntries.minOfOrNull { it.y * density.density } ?: (r * pitchYPx)
                            val cellH = rowEntries.maxOfOrNull { it.height * density.density } ?: defaultCellHeightPx
                            for (c in 0 until slots) {
                                val left = c * pitchXPx
                                drawRoundRect(
                                    color = cellColor,
                                    topLeft = Offset(left, top),
                                    size = Size(cellWidthPx, cellH),
                                    cornerRadius = CornerRadius(radius, radius),
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }
                        if (activeId != null && previewDropCell != null) {
                            val dropBounds = gridBounds[activeId]
                            val dc = previewDropCell!!.first.coerceIn(0, slots - 1)
                            val dr = previewDropCell!!.second.coerceAtLeast(0)
                            val spanC = dropBounds?.spanCols ?: 1
                            val left = dc * pitchXPx
                            val w = (cellWidthPx * spanC + gapPx * (spanC - 1)).coerceAtLeast(cellWidthPx)
                            val rowEntries = baseGridBounds.values.filter { it.row == dr }
                            val top = rowEntries.minOfOrNull { it.y * density.density } ?: (dr * pitchYPx)
                            val h = dropBounds?.height?.times(density.density) ?: defaultCellHeightPx
                            drawRoundRect(
                                color = dropHighlight.copy(alpha = 0.16f),
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(radius, radius)
                            )
                            drawRoundRect(
                                color = dropHighlight.copy(alpha = 0.72f),
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(radius, radius),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                        }
                    }
                }
                CompositionLocalProvider(LocalEditDragViewport provides dragViewport,
                    LocalPanelScrollTarget provides scrollRoute.target) {
                container.containerComponents.forEachIndexed { index, child -> key(child.id) {
                    val insideFolder = folderEditAction != null
                    val appTarget = isAppContextMenuCardType(child.type) ||
                        com.example.globalminusone.data.isSingleIconLauncherItem(child)
                    val interaction = remember(child.id) { FolderItemInteraction() }
                    val shownRows = childRows[child.id] ?: child.h
                    val positioned = positionedChildren[child.id] ?: child
                    val displayed = positioned.copy(h = shownRows)
                    val currentSlotBounds = gridBounds[child.id]
                    CompositionLocalProvider(LocalContainerGridIconCell provides (grid && isContainerGridIconItem(child))) {
                    FreeLayoutComponentSlot(
                        item = displayed, itemIndex = index, pageIndex = pageIndex, gridCols = columns,
                        stepX = stepX, stepY = stepY, pageWidthPx = pageWidthPx,
                        displayBoundsPx = currentSlotBounds?.let { Rect(it.x * density.density, it.y * density.density,
                            (it.x + it.width) * density.density, (it.y + it.height) * density.density) },
                        canvasHeightPx = canvasHeightPx + 12 * stepY, maxResizeRows = 120, isEditMode = editing,
                        isSelected = editing && state.selectedComponentId == child.id,
                        onSelect = {
                            if (editing && state.selectedComponentId == child.id && child.type == ComponentType.FOLDER) {
                                folderReturn?.openNow(child.id)
                            } else {
                                layoutActions.selectComponent(child.id)
                            }
                        },
                        onInteracting = { interacting ->
                            if (interacting) {
                                activeId = child.id
                                if (grid) {
                                    val initCell = (currentSlotBounds?.col ?: 0) to (currentSlotBounds?.row ?: 0)
                                    dropCellRef[0] = initCell
                                    previewDropCell = initCell
                                }
                            } else if (activeId == child.id) {
                                activeId = null
                                previewDropCell = null
                                dropCellRef[0] = null
                            }
                        },
                        onCommitLayout = { x, y, w, h, resizeWidth, resizeHeight ->
                            val latest = layoutActions.current
                            val targetPage = latest.pages.indexOfFirst { it.id == state.pages.getOrNull(pageIndex)?.id }
                            if (targetPage >= 0 && canCommitLayout(latest.editMode || insideFolder, latest.layoutLocked)) {
                                if (grid && !resizeWidth && !resizeHeight) {
                                    val cellToCommit = dropCellRef[0] ?: previewDropCell
                                    dropCellRef[0] = null
                                    if (cellToCommit != null) {
                                        layoutActions.placeChildInContainerGrid(
                                            pageIndex = targetPage,
                                            containerId = container.id,
                                            movedId = child.id,
                                            targetCol = cellToCommit.first,
                                            targetRow = cellToCommit.second,
                                            widthDp = pageWidthDp,
                                            stepYDp = stepYDpValue,
                                            iconColumns = iconColumns,
                                            fontScale = density.fontScale
                                        )
                                    }
                                } else if (grid && (resizeWidth || resizeHeight)) {
                                    layoutActions.updateComponent(targetPage, child.id) { actual ->
                                        commitContainerGridChildResize(actual, w = w, h = h, resizeWidth = resizeWidth, resizeHeight = resizeHeight)
                                    }
                                } else layoutActions.updateComponent(targetPage, child.id) { actual ->
                                    commitCardGeometry(actual, if (grid) actual.x else x, if (grid) actual.y else (y - (positioned.y - child.y)).coerceAtLeast(0), w, h, resizeWidth, resizeHeight)
                                }
                            }
                        },
                        onOpenEditDialog = { openProperties(child) },
                        onEnterEditMode = { if (folderEditAction != null) folderEditAction() else layoutActions.setEditMode(true); layoutActions.selectComponent(child.id) },
                        dragOutsideEdit = insideFolder && !state.layoutLocked && (appTarget || child.type == ComponentType.FOLDER),
                        onContentTap = if (insideFolder && !editing) ({
                            if (appTarget) interaction.tap?.invoke()
                            else if (child.type == ComponentType.FOLDER) {
                                interaction.tap?.invoke() ?: folderReturn?.openNow(child.id)
                            }
                        }) else null,
                        onLongPressPreview = if (insideFolder && !editing && appTarget) ({ interaction.openMenu?.invoke() }) else null,
                        onLongPressCancel = if (insideFolder && !editing && appTarget) ({ interaction.dismissHeldMenu?.invoke() }) else null,
                        onStationaryLongPress = when {
                            editing -> ({
                                layoutActions.selectComponent(child.id)
                                quickMenuChildId = child.id
                            })
                            insideFolder -> ({
                                if (appTarget) interaction.finishHoldMenu?.invoke()
                                else {
                                    layoutActions.selectComponent(child.id)
                                    quickMenuChildId = child.id
                                }
                            })
                            else -> null
                        },
                        dragReorders = grid, allowResize = !grid || !isContainerGridIconItem(child), animatePlacement = grid,
                        onDragPreviewPx = if (!grid) null else ({ xPx, yPx ->
                            val slots = iconColumns.coerceIn(1, 8)
                            val gapPx = with(density) { container.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP).dp.toPx() }
                            val rowGapPx = with(density) { resolvedRowGap.dp.toPx() }
                            val cellWidthPx = ((pageWidthPx - gapPx * (slots - 1)) / slots).coerceAtLeast(1f)
                            val pitchXPx = (cellWidthPx + gapPx).coerceAtLeast(8f)
                            val spanCols = currentSlotBounds?.spanCols ?: 1
                            val targetCol = (xPx / pitchXPx).roundToInt().coerceIn(0, (slots - spanCols).coerceAtLeast(0))
                            val targetRow = snapContainerGridRow(yPx, baseGridBounds, density.density, rowGapPx)
                            val nextCell = targetCol to targetRow
                            dropCellRef[0] = nextCell
                            if (previewDropCell != nextCell) previewDropCell = nextCell
                        })
                    ) {
                        Box(
                            Modifier.fillMaxSize().cardLongPressToEdit(
                                key = Triple(container.id, child.id, editing),
                                enabled = !state.layoutLocked && !editing && !appTarget && !child.type.isContainerType(),
                                ignoreOwnedGesture = false,
                                requireIdleOwnership = false
                            ) {
                                layoutActions.selectComponent(child.id)
                                quickMenuChildId = child.id
                            }
                        ) {
                            CompositionLocalProvider(
                                LocalInContainerChild provides true,
                                LocalFolderItemInteraction provides if (insideFolder && !editing && (appTarget || child.type == ComponentType.FOLDER)) interaction else null,
                                LocalFolderGridSpacing provides if (grid && isContainerGridIconItem(child)) containerIconVerticalSpacing(container) else null,
                                LocalFolderIconSizeDp provides if (grid) gridBounds[child.id]?.iconRenderSizeDp
                                    ?: containerIconSizeDp(container) else null
                            ) {
                                content(child)
                            }
                            if (editing && !(grid && isContainerGridIconItem(child))) IconButton(
                                onClick = {
                                    layoutActions.selectComponent(child.id)
                                    quickMenuChildId = child.id
                                },
                                modifier = Modifier.align(Alignment.TopEnd).size(48.dp)
                            ) {
                                Icon(Icons.Rounded.MoreVert, "编辑子卡片：${child.title}", Modifier.size(20.dp)
                                    .background(LocalCardColors.current.iconBackground.copy(alpha = 1f), CircleShape).padding(2.dp),
                                    tint = LocalCardColors.current.textSecondary)
                            }
                            if (quickMenuChildId == child.id && !state.layoutLocked) {
                                PanelDropdownMenu(expanded = true, onDismissRequest = { quickMenuChildId = null }) {
                                    com.example.globalminusone.ui.Android12WindowKeyGuard()
                                    if (child.type == ComponentType.FOLDER && folderReturn != null && !editing) {
                                        PanelMenuItem(text = { Text("打开文件夹") }) {
                                            quickMenuChildId = null
                                            folderReturn.openNow(child.id)
                                        }
                                    }
                                    PanelMenuItem(text = { Text("编辑卡片属性") }) {
                                        quickMenuChildId = null
                                        openProperties(child)
                                    }
                                    PanelMenuItem(text = {
                                        Text(
                                            when {
                                                insideFolder && editing -> "完成整理"
                                                insideFolder -> "整理文件夹"
                                                editing -> "退出编辑"
                                                else -> "进入编辑模式"
                                            }
                                        )
                                    }) {
                                        quickMenuChildId = null
                                        if (folderEditAction != null) folderEditAction() else layoutActions.setEditMode(!editing)
                                        layoutActions.selectComponent(child.id)
                                    }
                                    PanelMenuItem(text = { Text("复制到…") }) {
                                        quickMenuChildId = null
                                        transferChildRequest = child.id to false
                                    }
                                    PanelMenuItem(text = { Text("移动到…") }) {
                                        quickMenuChildId = null
                                        transferChildRequest = child.id to true
                                    }
                                    HorizontalDivider()
                                    if (child.type.isContainerType() &&
                                        (child.containerComponents.isNotEmpty() || child.containerActions.isNotEmpty())
                                    ) {
                                        PanelMenuItem(text = {
                                            Text("清空容器内卡片…", color = LocalPanelMenuPalette.current?.danger ?: Color.Red)
                                        }) {
                                            quickMenuChildId = null
                                            destructiveRequest = CardDestructiveRequest(
                                                pageId = currentPageId,
                                                componentId = child.id,
                                                title = child.title.ifBlank { "卡片" },
                                                action = CardDestructiveAction.CLEAR_CHILDREN
                                            )
                                        }
                                    }
                                    PanelMenuItem(text = {
                                        Text("删除卡片…", color = LocalPanelMenuPalette.current?.danger ?: Color.Red)
                                    }) {
                                        quickMenuChildId = null
                                        destructiveRequest = CardDestructiveRequest(
                                            pageId = currentPageId,
                                            componentId = child.id,
                                            title = child.title.ifBlank { "卡片" },
                                            action = CardDestructiveAction.DELETE
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                } }
                }
            }
            }
        }
    }
}

private fun snapContainerGridRow(
    yPx: Float,
    gridBounds: Map<String, ContainerGridChildBounds>,
    density: Float,
    rowGapPx: Float
): Int {
    val defaultPitchPx = (74f * density + rowGapPx).coerceAtLeast(24f)
    if (gridBounds.isEmpty()) {
        return (yPx / defaultPitchPx).roundToInt().coerceIn(0, 64)
    }
    data class RowSpan(val row: Int, val topPx: Float, val heightPx: Float)
    val rows = gridBounds.values
        .groupBy { it.row }
        .map { (row, entries) ->
            RowSpan(
                row = row,
                topPx = entries.minOf { it.y * density },
                heightPx = entries.maxOf { it.height * density }
            )
        }
        .sortedBy { it.row }
    val last = rows.last()
    val lastBottomThreshold = last.topPx + last.heightPx * 0.65f
    if (yPx > lastBottomThreshold) {
        val pitchPx = (last.heightPx + rowGapPx).coerceAtLeast(defaultPitchPx)
        val extraRows = ((yPx - last.topPx) / pitchPx).roundToInt().coerceAtLeast(1)
        return (last.row + extraRows).coerceIn(0, 64)
    }
    for (i in 0 until rows.size - 1) {
        val prev = rows[i]
        val next = rows[i + 1]
        if (next.row > prev.row + 1 &&
            yPx > prev.topPx + prev.heightPx * 0.65f &&
            yPx < next.topPx - defaultPitchPx * 0.35f
        ) {
            val step = ((yPx - prev.topPx) / defaultPitchPx).roundToInt()
            return (prev.row + step).coerceIn(prev.row + 1, next.row - 1)
        }
    }
    return rows.minByOrNull { kotlin.math.abs(yPx - it.topPx) }?.row?.coerceIn(0, 64) ?: 0
}
