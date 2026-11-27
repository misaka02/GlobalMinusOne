package com.example.globalminusone.ui.panel.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.globalminusone.data.*
import com.example.globalminusone.service.WidgetHostManager
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.ui.motion.MotionTokens
import com.example.globalminusone.data.policy.computeEmptyGridCells
import com.example.globalminusone.data.policy.computeGridRowBands
import com.example.globalminusone.data.policy.resolveGridDropCellWithHysteresis
import com.example.globalminusone.ui.panel.editor.CardReorderAnimationSyncBlock
import kotlin.math.roundToInt
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.GRID_SPAN_FULL_ROW
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.policy.PageGridMetrics
import com.example.globalminusone.data.policy.PageGridPlacement
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.isSingleIconLauncherItem
import com.example.globalminusone.data.policy.resizedPageGridSpan
import com.example.globalminusone.data.policy.resolvePageGridDropPlacements
import com.example.globalminusone.data.policy.resolveVisibleGridRows
import com.example.globalminusone.ui.panel.editor.CardDropPlaceholder
import com.example.globalminusone.ui.panel.editor.CardResizeGripMark
import com.example.globalminusone.ui.panel.editor.CardSlotBodyLayer
import com.example.globalminusone.ui.panel.editor.ContainerDragHint
import com.example.globalminusone.ui.panel.container.FolderItemInteraction
import com.example.globalminusone.ui.panel.editor.GridDimensionControls
import com.example.globalminusone.ui.panel.gesture.LocalAddAboveComponent
import com.example.globalminusone.ui.panel.cards.LocalContainerGridIconCell
import com.example.globalminusone.ui.panel.editor.LocalEditDragViewport
import com.example.globalminusone.ui.panel.container.LocalFolderIconSizeDp
import com.example.globalminusone.ui.panel.container.LocalFolderItemInteraction
import com.example.globalminusone.ui.panel.container.LocalFolderReturnState
import com.example.globalminusone.ui.panel.cards.LocalLayoutLocked
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.gesture.LocalPanelScrollTarget
import com.example.globalminusone.ui.panel.cards.RenderComponentItem
import com.example.globalminusone.ui.panel.gesture.canCommitLayout
import com.example.globalminusone.ui.panel.editor.cardEditDragTarget
import com.example.globalminusone.ui.panel.editor.cardLongPressToEdit
import com.example.globalminusone.ui.panel.container.compactCurtainFractions
import com.example.globalminusone.ui.panel.editor.detectStationaryDragGestures
import com.example.globalminusone.ui.panel.cards.isAppContextMenuCardType
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.editor.rememberEditDragViewport
import com.example.globalminusone.ui.panel.gesture.rememberPanelFlingBehavior
import com.example.globalminusone.ui.panel.gesture.rememberPanelScrollTarget
import com.example.globalminusone.ui.panel.reportLayoutMutation
import com.example.globalminusone.ui.panel.editor.stableScreenPositionFactory

@Composable
fun GridLayoutPage(
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
    onOpenAddComponent: () -> Unit,
    onAddAboveComponent: ((String) -> Unit)? = null,
    onAddAtGridCell: ((col: Int, row: Int) -> Unit)? = null
) {
    val isEditMode = state.editMode && !state.advancedConfig.layoutLocked
    val density = LocalDensity.current
    val commandContext = androidx.compose.ui.platform.LocalContext.current
    val palette = LocalPanelPalette.current
    val haptics = LocalHapticFeedback.current
    val gestureOwnership = LocalPanelGestureOwnership.current
    val folderReturn = LocalFolderReturnState.current
    val scrollState = rememberScrollState()
    val scrollTarget = rememberPanelScrollTarget(scrollState)
    val expansion = compactCurtainFractions(page.components)

    var activeInteractingId by remember(page.id) { mutableStateOf<String?>(null) }
    var animateGridSettle by remember(page.id) { mutableStateOf(false) }
    var previewDropCell by remember(page.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val dropCellRef = remember(page.id) { arrayOfNulls<Pair<Int, Int>>(1) }
    var liveSpanOverride by remember(page.id) { mutableStateOf<Pair<String, Pair<Int, Int>>?>(null) }

    LaunchedEffect(activeInteractingId, isEditMode) {
        if (activeInteractingId != null) {
            animateGridSettle = true
        } else if (animateGridSettle) {
            val settleMs = state.themeConfig.resolvedCardReorderMs().toLong()
            kotlinx.coroutines.delay(maxOf(settleMs * 3L, 900L))
            animateGridSettle = false
        }
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            activeInteractingId = null
            previewDropCell = null
            dropCellRef[0] = null
            liveSpanOverride = null
        }
    }

    val dragViewport = rememberEditDragViewport(
        scrollBy = { delta -> scrollState.scrollBy(delta) }
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                repository.updatePanelLayoutMetrics(with(density) { it.width.toDp().value }, density.fontScale)
            }
    ) {
        val horizontalPaddingDp = 10f
        val availableWidthDp = (maxWidth.value - horizontalPaddingDp * 2f).coerceAtLeast(64f)
        val orderedComponents = remember(
            page.components,
            activeInteractingId,
            previewDropCell,
            availableWidthDp,
            page.gridItemSizeDp,
            page.gridItemGapDp,
            page.gridRowGapDp,
            page.gridColumnCount,
            page.gridIconSizeDp,
            page.gridFillGaps,
            state.advancedConfig.gridColumns,
            state.advancedConfig.heightUnitDp,
            density.fontScale,
            expansion
        ) {
            val dragId = activeInteractingId
            val dropCell = previewDropCell
            if (dragId != null && dropCell != null) {
                resolvePageGridDropPlacements(
                    components = page.components,
                    availableWidthDp = availableWidthDp,
                    itemSizeDp = page.gridItemSizeDp,
                    itemGapDp = page.gridItemGapDp,
                    gridColumns = state.advancedConfig.gridColumns,
                    configuredHeightUnitDp = state.advancedConfig.heightUnitDp,
                    fontScale = density.fontScale,
                    compactExpansion = expansion,
                    columnCount = page.gridColumnCount,
                    iconSizeDp = page.gridIconSizeDp,
                    fillGaps = page.gridFillGaps,
                    rowGapDp = page.gridRowGapDp,
                    movedId = dragId,
                    targetCol = dropCell.first,
                    targetRow = dropCell.second
                )
            } else {
                page.components
            }
        }

        val spanOverrides = remember(liveSpanOverride) {
            liveSpanOverride?.let { mapOf(it.first to it.second) } ?: emptyMap()
        }

        val layoutResult = remember(
            orderedComponents,
            availableWidthDp,
            page.gridItemSizeDp,
            page.gridItemGapDp, page.gridRowGapDp, page.gridColumnCount, page.gridIconSizeDp, page.gridFillGaps,
            state.advancedConfig.gridColumns,
            state.advancedConfig.heightUnitDp,
            density.fontScale,
            expansion,
            spanOverrides
        ) {
            arrangePageGridComponents(
                components = orderedComponents,
                availableWidthDp = availableWidthDp,
                itemSizeDp = page.gridItemSizeDp,
                itemGapDp = page.gridItemGapDp,
                rowGapDp = page.gridRowGapDp,
                gridColumns = state.advancedConfig.gridColumns,
                configuredHeightUnitDp = state.advancedConfig.heightUnitDp,
                fontScale = density.fontScale,
                compactExpansion = expansion,
                spanOverrides = spanOverrides,
                columnCount = page.gridColumnCount, iconSizeDp = page.gridIconSizeDp, fillGaps = page.gridFillGaps
            )
        }

        val metrics = layoutResult.metrics
        val placementsById = remember(layoutResult.placements) {
            layoutResult.placements.associateBy { it.id }
        }
        val selectedId = if (isEditMode) state.selectedComponentId else null
        val selectedPlacement = selectedId?.let { placementsById[it] }

        Column(modifier = Modifier.fillMaxSize()) {
            val canScrollVertically by remember(scrollState) {
                derivedStateOf { scrollState.maxValue > 0 || scrollState.value > 0 }
            }
            val scrollModifier = Modifier.verticalScroll(
                state = scrollState,
                flingBehavior = rememberPanelFlingBehavior(),
                enabled = activeInteractingId == null && !gestureOwnership.exclusiveVerticalActive && canScrollVertically
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { dragViewport.bounds = it.boundsInRoot() }
                    .then(scrollModifier)
                    .then(
                        if (!state.advancedConfig.layoutLocked) {
                            Modifier.cardLongPressToEdit(
                                key = "grid-page-background",
                                enabled = !isEditMode,
                                ignoreOwnedGesture = true
                            ) {
                                repository.setEditMode(true)
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = horizontalPaddingDp.dp)
            ) {
                if (isEditMode) {
                    Spacer(Modifier.height(2.dp))
                    PageGridStyleControlCard(
                        page = page,
                        metrics = metrics,
                        occupiedRows = layoutResult.totalRows,
                        themeConfig = state.themeConfig,
                        repository = repository,
                        onColumns = { reportLayoutMutation(commandContext, repository.tryFitPageGridColumnCount(page.id, it, availableWidthDp)) },
                        onChangeSize = { repository.updatePageGridStyle(pageIndex, iconSizeDp = it) },
                        onChangeGap = { repository.updatePageGridStyle(pageIndex, itemGapDp = it) },
                        onChangeRowGap = { repository.updatePageGridStyle(pageIndex, rowGapDp = it) },
                        onRowCount = { count -> repository.updatePageGridStyle(pageIndex, rowCount = count, autoRows = count <= 0) },
                        onAutoRows = { auto -> repository.updatePageGridStyle(pageIndex, rowCount = if (auto) 0 else maxOf(layoutResult.totalRows, 1), autoRows = auto) },
                        onCompactGrid = { repository.compactPageGridPlacements(pageIndex, availableWidthDp, density.fontScale) }
                    )
                    Spacer(Modifier.height(8.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                val showGridGuides = isEditMode || activeInteractingId != null
                val visualRows = resolveVisibleGridRows(
                    occupiedRows = layoutResult.totalRows,
                    configuredRowCount = page.gridRowCount,
                    autoRows = page.gridAutoRows,
                    isDragging = activeInteractingId != null,
                    previewDropRow = previewDropCell?.second
                )
                val pitchYDp = (metrics.cellHeightDp + metrics.rowGapDp).coerceAtLeast(16f)
                val manualGridHeightDp = ((visualRows - 1).coerceAtLeast(0) * pitchYDp + metrics.cellHeightDp)
                    .coerceAtLeast(16f)
                val canvasHeightDp = (
                    when {
                        showGridGuides -> maxOf(layoutResult.contentHeightDp, manualGridHeightDp) + 16f
                        !page.gridAutoRows -> maxOf(layoutResult.contentHeightDp, manualGridHeightDp)
                        else -> layoutResult.contentHeightDp
                    }
                ).coerceAtLeast(0f).dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeightDp)
                ) {
                    if (showGridGuides) {
                        val gridStrokeColor = palette.textSecondary.copy(alpha = 0.16f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(manualGridHeightDp.dp)
                        ) {
                            val cellW = metrics.cellWidthDp.dp.toPx()
                            val cellH = metrics.cellHeightDp.dp.toPx()
                            val gapPx = metrics.itemGapDp.dp.toPx()
                            val rowGapPx = metrics.rowGapDp.dp.toPx()
                            val pitchXPx = (cellW + gapPx).coerceAtLeast(16.dp.toPx())
                            val pitchYPx = (cellH + rowGapPx).coerceAtLeast(16.dp.toPx())
                            val strokeW = 1.dp.toPx()
                            val radius = 8.dp.toPx()
                            for (r in 0 until visualRows) {
                                val top = r * pitchYPx
                                for (c in 0 until metrics.columns) {
                                    val left = c * pitchXPx
                                    drawRoundRect(
                                        color = gridStrokeColor,
                                        topLeft = Offset(left, top),
                                        size = Size(cellW, cellH),
                                        cornerRadius = CornerRadius(radius, radius),
                                        style = Stroke(width = strokeW)
                                    )
                                }
                            }
                        }
                    }

                    val rowBands = remember(layoutResult, visualRows) { computeGridRowBands(layoutResult, visualRows) }
                    if (isEditMode && activeInteractingId == null && onAddAtGridCell != null) {
                        val emptyCells = remember(layoutResult, visualRows) { computeEmptyGridCells(layoutResult, visualRows) }
                        GridEmptyCellAddLayer(emptyCells = emptyCells, palette = palette, onAddAtCell = onAddAtGridCell)
                    }

                    CompositionLocalProvider(
                        LocalEditDragViewport provides dragViewport,
                        LocalPanelScrollTarget provides scrollTarget,
                        LocalAddAboveComponent provides onAddAboveComponent
                    ) {
                        page.components.forEach { savedItem ->
                            val placement = placementsById[savedItem.id]
                            if (placement != null) key(savedItem.id) {
                                val appTarget = isAppContextMenuCardType(savedItem.type) ||
                                    isSingleIconLauncherItem(savedItem)
                                val canDirectDrag = !isEditMode && !state.advancedConfig.layoutLocked &&
                                    (appTarget || savedItem.type == ComponentType.FOLDER)
                                val interaction = remember(savedItem.id) { FolderItemInteraction() }
                                val isBareSingleCellIcon = placement.spanX == 1 && placement.spanY == 1 && (
                                    savedItem.type in setOf(
                                        ComponentType.APP_ICON,
                                        ComponentType.CUSTOM_SHORTCUT,
                                        ComponentType.STATIC_SHORTCUT,
                                        ComponentType.WEBSITE_LINK,
                                        ComponentType.FOLDER,
                                        ComponentType.SUB_PANEL_ENTRY
                                    ) || isSingleIconLauncherItem(savedItem)
                                    )
                                CompositionLocalProvider(
                                    LocalContainerGridIconCell provides isBareSingleCellIcon,
                                    LocalFolderIconSizeDp provides metrics.iconRenderSizeDp
                                ) {
                                    PageGridComponentSlot(
                                        placement = placement,
                                        metrics = metrics,
                                        canvasWidthDp = availableWidthDp,
                                        isEditMode = isEditMode,
                                        animateTransitions = isEditMode || animateGridSettle,
                                        isSelected = selectedId == savedItem.id,
                                        onSelect = {
                                            if (isEditMode && selectedId == savedItem.id && savedItem.type == ComponentType.FOLDER) {
                                                folderReturn?.openNow(savedItem.id)
                                            } else {
                                                repository.selectComponent(savedItem.id)
                                            }
                                        },
                                        onEnterEditMode = {
                                            repository.setEditMode(true)
                                            repository.selectComponent(savedItem.id)
                                        },
                                        onOpenEditDialog = { onOpenEditDialog(savedItem) },
                                        dragOutsideEdit = canDirectDrag,
                                        onContentTap = if (canDirectDrag) ({
                                            if (appTarget) interaction.tap?.invoke()
                                            else if (savedItem.type == ComponentType.FOLDER) {
                                                interaction.tap?.invoke() ?: folderReturn?.openNow(savedItem.id)
                                            }
                                        }) else null,
                                        onLongPressPreview = if (canDirectDrag && appTarget) ({
                                            interaction.openMenu?.invoke()
                                        }) else null,
                                        onLongPressCancel = if (canDirectDrag && appTarget) ({
                                            interaction.dismissHeldMenu?.invoke()
                                        }) else null,
                                        onStationaryLongPress = if (canDirectDrag) ({
                                            if (appTarget) {
                                                interaction.finishHoldMenu?.invoke()
                                            } else {
                                                repository.setEditMode(true)
                                                repository.selectComponent(savedItem.id)
                                            }
                                        }) else null,
                                        onStartReorder = {
                                            activeInteractingId = savedItem.id
                                            val initCell = placement.col to placement.row
                                            dropCellRef[0] = initCell
                                            previewDropCell = initCell
                                        },
                                        onDragPreviewDp = { xDp, yDp ->
                                            val nextCell = resolveGridDropCellWithHysteresis(
                                                xDp = xDp,
                                                yDp = yDp,
                                                spanX = placement.spanX,
                                                metrics = metrics,
                                                rowBands = rowBands,
                                                previousCell = dropCellRef[0]
                                            )
                                            if (dropCellRef[0] != nextCell) {
                                                dropCellRef[0] = nextCell
                                                previewDropCell = nextCell
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        onFinishReorder = { commit ->
                                            val cellToCommit = dropCellRef[0] ?: previewDropCell
                                            activeInteractingId = null
                                            previewDropCell = null
                                            dropCellRef[0] = null
                                            if (commit && cellToCommit != null) {
                                                val latest = repository.state.value
                                                val allowDirect = !latest.editMode &&
                                                    (appTarget || savedItem.type == ComponentType.FOLDER)
                                                if (canCommitLayout(latest.editMode || allowDirect, latest.advancedConfig.layoutLocked)) {
                                                    repository.placeComponentInPageGrid(
                                                        pageIndex = pageIndex,
                                                        movedId = savedItem.id,
                                                        targetCol = cellToCommit.first,
                                                        targetRow = cellToCommit.second,
                                                        availableWidthDp = availableWidthDp,
                                                        fontScale = density.fontScale
                                                    )
                                                }
                                            }
                                        },
                                        onPreviewSpan = { newSpanX, newSpanY ->
                                            activeInteractingId = savedItem.id
                                            liveSpanOverride = savedItem.id to (newSpanX to newSpanY)
                                        },
                                        onCommitSpan = { newSpanX, newSpanY ->
                                            activeInteractingId = null
                                            liveSpanOverride = null
                                            val latest = repository.state.value
                                            if (canCommitLayout(latest.editMode, latest.advancedConfig.layoutLocked)) {
                                                repository.updateComponent(pageIndex, savedItem.id) { comp ->
                                                    val storedSpanX = if (newSpanX >= metrics.columns && metrics.columns > 1 &&
                                                        comp.gridSpanX >= GRID_SPAN_FULL_ROW
                                                    ) {
                                                        GRID_SPAN_FULL_ROW
                                                    } else {
                                                        newSpanX
                                                    }
                                                    comp.copy(
                                                        gridSpanX = storedSpanX,
                                                        gridSpanY = newSpanY,
                                                        autoHeight = if (newSpanY != placement.spanY) false else comp.autoHeight
                                                    )
                                                }
                                            }
                                        },
                                        onCancelSpan = {
                                            activeInteractingId = null
                                            liveSpanOverride = null
                                        }
                                    ) {
                                        CompositionLocalProvider(
                                            LocalFolderItemInteraction provides if (canDirectDrag && (appTarget || savedItem.type == ComponentType.FOLDER)) interaction else null
                                        ) {
                                            RenderComponentItem(
                                                item = savedItem,
                                                pageIndex = pageIndex,
                                                state = state,
                                                repository = repository,
                                                systemController = systemController,
                                                widgetHostManager = widgetHostManager,
                                                isEditMode = isEditMode,
                                                isSelected = selectedId == savedItem.id,
                                                onBrightnessAdjusting = onBrightnessAdjusting,
                                                onRequestClose = onRequestClose,
                                                onOpenMainApp = onOpenMainApp,
                                                onOpenEditDialog = onOpenEditDialog,
                                                onOpenBottomNineKeyBar = onOpenBottomNineKeyBar,
                                                onOpenAddComponent = onOpenAddComponent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(
                    Modifier.height(
                        when {
                            isEditMode && selectedPlacement != null -> 60.dp
                            isEditMode -> 12.dp
                            else -> 0.dp
                        }
                    )
                )
            }

            if (isEditMode && selectedPlacement != null) {
                PageGridSelectedCardBar(
                    placement = selectedPlacement,
                    totalColumns = metrics.columns,
                    onClearSelection = { repository.selectComponent(null) },
                    onApplySpan = { spanX, spanY ->
                        repository.updateComponent(pageIndex, selectedPlacement.id) { comp ->
                            comp.copy(
                                gridSpanX = spanX,
                                gridSpanY = spanY,
                                autoHeight = if (spanY > 0) false else comp.autoHeight
                            )
                        }
                    },
                    onNudgeCell = { dCol, dRow ->
                        repository.nudgeComponentInPageGrid(
                            pageIndex = pageIndex,
                            movedId = selectedPlacement.id,
                            deltaCol = dCol,
                            deltaRow = dRow,
                            availableWidthDp = availableWidthDp,
                            fontScale = density.fontScale
                        )
                    },
                    onCompactGrid = {
                        repository.compactPageGridPlacements(pageIndex, availableWidthDp, density.fontScale)
                    },
                    onOpenFolder = if (selectedPlacement.item.type == ComponentType.FOLDER) ({
                        folderReturn?.openNow(selectedPlacement.id)
                    }) else null,
                    onOpenProperties = { onOpenEditDialog(selectedPlacement.item) }
                )
            }
        }
    }
}

@Composable
private fun PageGridStyleControlCard(
    page: PageConfig,
    metrics: PageGridMetrics,
    occupiedRows: Int,
    themeConfig: ThemeConfig,
    repository: MinusOneRepository,
    onColumns: (Int) -> Unit,
    onChangeSize: (Int) -> Unit,
    onChangeGap: (Int) -> Unit,
    onChangeRowGap: (Int) -> Unit,
    onRowCount: (Int) -> Unit,
    onAutoRows: (Boolean) -> Unit,
    onCompactGrid: () -> Unit
) {
    val palette = LocalPanelPalette.current
    var expanded by remember(page.id) { mutableStateOf(false) }
    val cardBg = if (palette.isDark) Color(0xFF1F242E).copy(alpha = 0.96f) else Color(0xFFFCFDFF).copy(alpha = 0.96f)
    val subChipBg = if (palette.isDark) Color(0xFF2B313D) else Color(0xFFF1F4F8)
    val borderStroke = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.65f))
    val shownRows = resolveVisibleGridRows(occupiedRows, page.gridRowCount, page.gridAutoRows, isDragging = false)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = borderStroke
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "网格 ${metrics.columns}列 × ${shownRows}行" + if (page.gridAutoRows) "（自动）" else "（固定）",
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "图标${metrics.iconRenderSizeDp}dp · 横${metrics.itemGapDp}/纵${metrics.rowGapDp}dp",
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                // 单行快速列数微调与一键对齐
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onCompactGrid,
                        shape = RoundedCornerShape(7.dp),
                        color = palette.accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "紧凑对齐",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accent,
                            maxLines = 1
                        )
                    }
                    Surface(
                        onClick = { if (metrics.columns > 1) onColumns(metrics.columns - 1) },
                        enabled = metrics.columns > 1,
                        shape = RoundedCornerShape(7.dp),
                        color = subChipBg
                    ) {
                        Box(Modifier.size(width = 28.dp, height = 28.dp), contentAlignment = Alignment.Center) {
                            Text("−", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        }
                    }
                    Surface(
                        onClick = { if (metrics.columns < 12) onColumns(metrics.columns + 1) },
                        enabled = metrics.columns < 12,
                        shape = RoundedCornerShape(7.dp),
                        color = subChipBg
                    ) {
                        Box(Modifier.size(width = 28.dp, height = 28.dp), contentAlignment = Alignment.Center) {
                            Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        }
                    }
                    Surface(
                        onClick = { expanded = !expanded },
                        shape = RoundedCornerShape(8.dp),
                        color = if (expanded) palette.accent else palette.accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (expanded) "收起参数 ▴" else "网格参数 ▾",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (expanded) Color.White else palette.accent,
                            maxLines = 1
                        )
                    }
                }
            }

            if (expanded) {
                GridDimensionControls(
                    page.gridColumnCount,
                    page.gridIconSizeDp,
                    page.gridItemGapDp,
                    onColumns,
                    onChangeSize,
                    onChangeGap,
                    rowGapDp = metrics.rowGapDp,
                    onRowGap = onChangeRowGap,
                    rowCount = page.gridRowCount,
                    autoRows = page.gridAutoRows,
                    onRowCount = onRowCount,
                    onAutoRows = onAutoRows
                )
                CardReorderAnimationSyncBlock(themeConfig, repository, palette)
            }
        }
    }
}

@Composable
private fun PageGridSelectedCardBar(
    placement: PageGridPlacement,
    totalColumns: Int,
    onApplySpan: (spanX: Int, spanY: Int) -> Unit,
    onNudgeCell: (deltaCol: Int, deltaRow: Int) -> Unit,
    onCompactGrid: () -> Unit,
    onOpenFolder: (() -> Unit)?,
    onOpenProperties: () -> Unit,
    onClearSelection: () -> Unit = {}
) {
    val palette = LocalPanelPalette.current
    val isFullRow = placement.item.gridSpanX >= GRID_SPAN_FULL_ROW ||
        (placement.spanX == totalColumns && totalColumns > 1)
    val sheetBg = if (palette.isDark) Color(0xFF1F242E).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
    val chipGroupBg = if (palette.isDark) Color(0xFF282E38) else Color(0xFFF3F5F8)
    var toolsExpanded by remember(placement.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(14.dp),
        color = sheetBg,
        border = BorderStroke(0.8.dp, palette.cardBorder.copy(alpha = 0.72f)),
        shadowElevation = 5.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = placement.item.title.ifBlank { placement.item.type.label },
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.accent.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "${placement.spanX}×${placement.spanY} · ${placement.row + 1}-${placement.col + 1}",
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            color = palette.accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onOpenFolder != null) {
                        GridQuickChip(label = "打开", selected = false, onClick = onOpenFolder)
                    }
                    GridQuickChip(label = "属性", selected = true, onClick = onOpenProperties)
                    GridQuickChip(
                        label = if (toolsExpanded) "收起" else "调整 ▾",
                        selected = toolsExpanded
                    ) { toolsExpanded = !toolsExpanded }
                    GridQuickChip(label = "✕", selected = false, onClick = onClearSelection)
                }
            }

            if (toolsExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = chipGroupBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("位置", color = palette.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            GridQuickChip(label = "←", selected = false, enabled = placement.col > 0) { onNudgeCell(-1, 0) }
                            GridQuickChip(label = "↑", selected = false, enabled = placement.row > 0) { onNudgeCell(0, -1) }
                            GridQuickChip(label = "↓", selected = false) { onNudgeCell(0, 1) }
                            GridQuickChip(label = "→", selected = false, enabled = placement.col + placement.spanX < totalColumns) { onNudgeCell(1, 0) }
                            GridQuickChip(label = "紧凑对齐", selected = false, onClick = onCompactGrid)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("占格", color = palette.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            GridQuickChip(
                                label = "1×1",
                                selected = placement.spanX == 1 && placement.spanY == 1 && !isFullRow
                            ) { onApplySpan(1, 1) }
                            if (totalColumns >= 2) {
                                GridQuickChip(
                                    label = "2×1",
                                    selected = placement.spanX == 2 && placement.spanY == 1 && !isFullRow
                                ) { onApplySpan(2, 1) }
                                GridQuickChip(
                                    label = "1×3",
                                    selected = placement.spanX == 1 && placement.spanY == 3 && !isFullRow
                                ) { onApplySpan(1, 3) }
                                GridQuickChip(
                                    label = "2×2",
                                    selected = placement.spanX == 2 && placement.spanY == 2 && !isFullRow
                                ) { onApplySpan(2, 2) }
                            }
                            if (totalColumns >= 3) {
                                GridQuickChip(
                                    label = "3×2",
                                    selected = placement.spanX == 3 && placement.spanY == 2 && !isFullRow
                                ) { onApplySpan(3, 2) }
                            }
                            GridQuickChip(
                                label = "整行×1",
                                selected = isFullRow && placement.spanY == 1
                            ) { onApplySpan(GRID_SPAN_FULL_ROW, 1) }
                            GridQuickChip(
                                label = "整行×2",
                                selected = isFullRow && placement.spanY == 2
                            ) { onApplySpan(GRID_SPAN_FULL_ROW, 2) }
                            GridQuickChip(
                                label = "列−",
                                selected = false,
                                enabled = placement.spanX > 1
                            ) { onApplySpan((placement.spanX - 1).coerceAtLeast(1), placement.spanY) }
                            GridQuickChip(
                                label = "列＋",
                                selected = false,
                                enabled = placement.spanX < totalColumns
                            ) { onApplySpan(if (placement.spanX + 1 >= totalColumns) GRID_SPAN_FULL_ROW else placement.spanX + 1, placement.spanY) }
                            GridQuickChip(
                                label = "行−",
                                selected = false,
                                enabled = placement.spanY > 1
                            ) { onApplySpan(if (isFullRow) GRID_SPAN_FULL_ROW else placement.spanX, (placement.spanY - 1).coerceAtLeast(1)) }
                            GridQuickChip(
                                label = "行＋",
                                selected = false,
                                enabled = placement.spanY < 10
                            ) { onApplySpan(if (isFullRow) GRID_SPAN_FULL_ROW else placement.spanX, (placement.spanY + 1).coerceAtMost(10)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridQuickChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = LocalPanelPalette.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(7.dp),
        color = when {
            !enabled -> palette.textSecondary.copy(alpha = 0.06f)
            selected -> palette.accent
            else -> palette.accent.copy(alpha = 0.10f)
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> palette.textSecondary.copy(alpha = 0.40f)
                selected -> Color.White
                else -> palette.textPrimary
            },
            maxLines = 1
        )
    }
}

@Composable
private fun PageGridComponentSlot(
    placement: PageGridPlacement,
    metrics: PageGridMetrics,
    canvasWidthDp: Float,
    isEditMode: Boolean,
    animateTransitions: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEnterEditMode: () -> Unit,
    onOpenEditDialog: () -> Unit,
    dragOutsideEdit: Boolean = false,
    onContentTap: (() -> Unit)? = null,
    onLongPressPreview: (() -> Unit)? = null,
    onLongPressCancel: (() -> Unit)? = null,
    onStationaryLongPress: (() -> Unit)? = null,
    onStartReorder: () -> Unit,
    onDragPreviewDp: (Float, Float) -> Unit,
    onFinishReorder: (commit: Boolean) -> Unit,
    onPreviewSpan: (Int, Int) -> Unit,
    onCommitSpan: (Int, Int) -> Unit,
    onCancelSpan: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val palette = LocalPanelPalette.current
    val layoutLocked = LocalLayoutLocked.current
    val effectiveEditMode = isEditMode && !layoutLocked
    val dragEnabled = effectiveEditMode || (dragOutsideEdit && !layoutLocked)
    val holdOwnership = LocalPanelGestureOwnership.current
    val dragViewport = LocalEditDragViewport.current
    val dragHostView = LocalView.current
    val haptics = LocalHapticFeedback.current

    val currentPlacement by rememberUpdatedState(placement)
    val currentMetrics by rememberUpdatedState(metrics)
    val currentEditMode by rememberUpdatedState(effectiveEditMode)
    val currentDragOutsideEdit by rememberUpdatedState(dragOutsideEdit && !layoutLocked)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnEnterEditMode by rememberUpdatedState(onEnterEditMode)
    val currentOnContentTap by rememberUpdatedState(onContentTap)
    val currentOnLongPressPreview by rememberUpdatedState(onLongPressPreview)
    val currentOnLongPressCancel by rememberUpdatedState(onLongPressCancel)
    val currentOnStationaryLongPress by rememberUpdatedState(onStationaryLongPress)
    val currentOnStartReorder by rememberUpdatedState(onStartReorder)
    val currentOnDragPreviewDp by rememberUpdatedState(onDragPreviewDp)
    val currentOnFinishReorder by rememberUpdatedState(onFinishReorder)
    val currentOnPreviewSpan by rememberUpdatedState(onPreviewSpan)
    val currentOnCommitSpan by rememberUpdatedState(onCommitSpan)
    val currentOnCancelSpan by rememberUpdatedState(onCancelSpan)
    val currentViewport by rememberUpdatedState(dragViewport)

    var isDragging by remember(placement.id) { mutableStateOf(false) }
    var dragOriginPx by remember(placement.id) { mutableStateOf(Offset.Zero) }
    var dragDeltaPx by remember(placement.id) { mutableStateOf(Offset.Zero) }
    var dragScrollStart by remember(placement.id) { mutableFloatStateOf(0f) }

    var isResizing by remember(placement.id) { mutableStateOf(false) }
    var resizeStartSpan by remember(placement.id) { mutableStateOf(placement.spanX to placement.spanY) }
    var resizeAccumPx by remember(placement.id) { mutableStateOf(Offset.Zero) }

    val pageActive = if (isDragging || isResizing) LocalPageViewportActive.current else true
    LaunchedEffect(pageActive, isEditMode, dragOutsideEdit, layoutLocked) {
        if (!pageActive || layoutLocked || (!isEditMode && !dragOutsideEdit)) {
            if (isDragging) currentOnFinishReorder(false)
            currentViewport?.end()
            isDragging = false
            dragDeltaPx = Offset.Zero
        }
    }
    LaunchedEffect(pageActive, isEditMode, isSelected, layoutLocked) {
        if (!pageActive || layoutLocked || !isEditMode || !isSelected) {
            if (isResizing) currentOnCancelSpan()
            isResizing = false; resizeAccumPx = Offset.Zero
        }
    }
    DisposableEffect(placement.id) { onDispose { if (isDragging) currentViewport?.end() } }
    val dragTargetCoords = remember(placement.id) { arrayOfNulls<LayoutCoordinates>(1) }

    val reorderAnimMs = palette.themeConfig.resolvedCardReorderMs()
    val reorderStyle = palette.themeConfig.moduleAnimations.cardReorderStyle
    val animateSlot = animateTransitions && reorderAnimMs > 0
    val targetOffsetPx = Offset(with(density) { placement.xDp.dp.toPx() }, with(density) { placement.yDp.dp.toPx() })
    val targetWidthPx = with(density) { placement.widthDp.dp.toPx() }
    val targetHeightPx = with(density) { placement.heightDp.dp.toPx() }
    val motionController = rememberCardSlotMotionController(
        key = placement.id,
        initialOffsetPx = targetOffsetPx,
        targetOffsetPx = targetOffsetPx,
        isDragging = isDragging,
        isResizing = isResizing,
        animateMoves = animateSlot,
        animMs = reorderAnimMs,
        style = reorderStyle
    )
    val animatedWidthState = if (animateSlot) animateFloatAsState(
        targetValue = targetWidthPx,
        animationSpec = if (isResizing) snap() else MotionTokens.cardSettleSpring(reorderAnimMs, reorderStyle),
        label = "grid-slot-width"
    ) else null
    val animatedHeightState = if (animateSlot) animateFloatAsState(
        targetValue = targetHeightPx,
        animationSpec = if (isResizing) snap() else MotionTokens.cardSettleSpring(reorderAnimMs, reorderStyle),
        label = "grid-slot-height"
    ) else null

    val maxCanvasXPx = with(density) { (canvasWidthDp - placement.widthDp).coerceAtLeast(0f).dp.toPx() }

    if (isDragging) {
        val animatedTargetPx by animateOffsetAsState(
            targetValue = targetOffsetPx,
            animationSpec = if (!animateSlot) snap() else MotionTokens.cardOffsetSpring(reorderAnimMs, reorderStyle),
            label = "grid-drop-placeholder"
        )
        CardDropPlaceholder(
            offsetProvider = { IntOffset(animatedTargetPx.x.roundToInt(), animatedTargetPx.y.roundToInt()) },
            widthPx = { targetWidthPx.roundToInt() },
            heightPx = { targetHeightPx.roundToInt() },
            accentColor = palette.accent,
            fillAlpha = 0.14f
        )
    }

    Box(
        modifier = Modifier
            .offset {
                val offset = if (isDragging) {
                    Offset(
                        (dragOriginPx.x + dragDeltaPx.x).coerceIn(0f, maxCanvasXPx),
                        (dragOriginPx.y + dragDeltaPx.y + ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)).coerceAtLeast(0f)
                    )
                } else {
                    motionController.currentOffsetPx
                }
                IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
            }
            .layout { measurable, _ ->
                // Read animated bounds in layout phase so size animations never recompose children.
                val w = (animatedWidthState?.value ?: targetWidthPx).roundToInt().coerceAtLeast(1)
                val h = (animatedHeightState?.value ?: targetHeightPx).roundToInt().coerceAtLeast(1)
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.placeRelative(0, 0) }
            }
            .zIndex(if (isDragging || isResizing) 3f else if (isSelected) 1.5f else 0f)
            .cardPickupVisuals(motionController, placement.item.rotationDeg.toFloat(), isDragging = isDragging)
    ) {
        CardSlotBodyLayer(
            item = placement.item,
            widthDp = placement.widthDp,
            heightDp = placement.heightDp,
            isEditMode = isEditMode,
            dragEnabled = dragEnabled,
            layoutLocked = layoutLocked,
            allowCardLongPress = true,
            onEnterEditMode = { currentOnEnterEditMode() },
            content = content
        )

        if (dragEnabled) {
            Box(
                modifier = Modifier
                    .then(if (effectiveEditMode) cardEditDragTarget(placement.item) else Modifier.fillMaxSize())
                    .onGloballyPositioned { dragTargetCoords[0] = it }
                    .pointerInput(placement.id, onStationaryLongPress != null, onLongPressPreview != null) {
                        detectStationaryDragGestures(
                            ownership = holdOwnership,
                            positionInStableSpaceFactory = stableScreenPositionFactory(dragHostView) { dragTargetCoords[0] },
                            onTap = {
                                if (currentEditMode) currentOnSelect()
                                else currentOnContentTap?.invoke()
                            },
                            onStationaryLongPress = if (!effectiveEditMode && onStationaryLongPress != null) ({
                                if (!currentEditMode) currentOnStationaryLongPress?.invoke()
                            }) else null,
                            onLongPressPreview = if (!effectiveEditMode && onLongPressPreview != null) ({
                                if (!currentEditMode) currentOnLongPressPreview?.invoke()
                            }) else null,
                            onLongPressCancel = { currentOnLongPressCancel?.invoke() },
                            onDragStart = { point ->
                                if (!currentEditMode && !currentDragOutsideEdit) return@detectStationaryDragGestures
                                if (currentEditMode) currentOnSelect()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isDragging = true
                                dragOriginPx = Offset(
                                    with(density) { currentPlacement.xDp.dp.toPx() },
                                    with(density) { currentPlacement.yDp.dp.toPx() }
                                )
                                dragDeltaPx = Offset.Zero
                                dragScrollStart = currentViewport?.consumedScroll ?: 0f
                                dragTargetCoords[0]?.takeIf { it.isAttached }?.localToRoot(point)?.let {
                                    currentViewport?.begin(it)
                                }
                                currentOnStartReorder()
                            },
                            onDragEnd = {
                                val fingerOffset = Offset(
                                    (dragOriginPx.x + dragDeltaPx.x).coerceIn(0f, maxCanvasXPx),
                                    (dragOriginPx.y + dragDeltaPx.y + ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)).coerceAtLeast(0f)
                                )
                                if (isDragging) {
                                    currentOnDragPreviewDp(
                                        with(density) { fingerOffset.x.toDp().value },
                                        with(density) { fingerOffset.y.toDp().value }
                                    )
                                }
                                motionController.seedDragOffset(fingerOffset)
                                currentViewport?.end()
                                isDragging = false
                                dragDeltaPx = Offset.Zero
                                currentOnFinishReorder(true)
                            },
                            onDragCancel = {
                                val fingerOffset = Offset(
                                    (dragOriginPx.x + dragDeltaPx.x).coerceIn(0f, maxCanvasXPx),
                                    (dragOriginPx.y + dragDeltaPx.y + ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)).coerceAtLeast(0f)
                                )
                                motionController.seedDragOffset(fingerOffset)
                                currentViewport?.end()
                                isDragging = false
                                dragDeltaPx = Offset.Zero
                                currentOnFinishReorder(false)
                            },
                            onDrag = { _, dragAmount ->
                                if (!isDragging) return@detectStationaryDragGestures
                                currentViewport?.moveBy(dragAmount)
                                dragDeltaPx += dragAmount
                                val curX = (dragOriginPx.x + dragDeltaPx.x).coerceIn(0f, maxCanvasXPx)
                                val curY = (dragOriginPx.y + dragDeltaPx.y +
                                    ((currentViewport?.consumedScroll ?: 0f) - dragScrollStart)).coerceAtLeast(0f)
                                currentOnDragPreviewDp(
                                    with(density) { curX.toDp().value },
                                    with(density) { curY.toDp().value }
                                )
                            }
                        )
                    }
            ) {
                if (effectiveEditMode) {
                    ContainerDragHint(placement.item)
                }
            }

            if (effectiveEditMode && isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, palette.accent, RoundedCornerShape(10.dp))
                )

                // 右下角拖拽缩放手柄：可直接拖拽调整卡片占用 N列 × M行 多格跨度
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .semantics { contentDescription = "拖动调整网格卡片跨度" }
                        .ownPanelScrollGestures(true, lockFromDown = true)
                        .pointerInput(placement.id) {
                            detectDragGestures(
                                onDragStart = {
                                    isResizing = true
                                    resizeStartSpan = currentPlacement.spanX to currentPlacement.spanY
                                    resizeAccumPx = Offset.Zero
                                },
                                onDragEnd = {
                                    val pitchXPx = with(density) {
                                        (currentMetrics.cellWidthDp + currentMetrics.itemGapDp).coerceAtLeast(16f).dp.toPx()
                                    }
                                    val pitchYPx = with(density) {
                                        (currentMetrics.cellHeightDp + currentMetrics.rowGapDp).coerceAtLeast(16f).dp.toPx()
                                    }
                                    val (endX, endY) = resizedPageGridSpan(
                                        startSpanX = resizeStartSpan.first,
                                        startSpanY = resizeStartSpan.second,
                                        dxPx = resizeAccumPx.x,
                                        dyPx = resizeAccumPx.y,
                                        pitchXPx = pitchXPx,
                                        pitchYPx = pitchYPx,
                                        maxColumns = currentMetrics.columns
                                    )
                                    isResizing = false
                                    resizeAccumPx = Offset.Zero
                                    currentOnCommitSpan(endX, endY)
                                },
                                onDragCancel = {
                                    isResizing = false
                                    resizeAccumPx = Offset.Zero
                                    currentOnCancelSpan()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    resizeAccumPx += dragAmount
                                    val pitchXPx = with(density) {
                                        (currentMetrics.cellWidthDp + currentMetrics.itemGapDp).coerceAtLeast(16f).dp.toPx()
                                    }
                                    val pitchYPx = with(density) {
                                        (currentMetrics.cellHeightDp + currentMetrics.rowGapDp).coerceAtLeast(16f).dp.toPx()
                                    }
                                    val (liveX, liveY) = resizedPageGridSpan(
                                        startSpanX = resizeStartSpan.first,
                                        startSpanY = resizeStartSpan.second,
                                        dxPx = resizeAccumPx.x,
                                        dyPx = resizeAccumPx.y,
                                        pitchXPx = pitchXPx,
                                        pitchYPx = pitchYPx,
                                        maxColumns = currentMetrics.columns
                                    )
                                    currentOnPreviewSpan(liveX, liveY)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CardResizeGripMark(palette.accent)
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 3.dp, y = 3.dp),
                    shape = RoundedCornerShape(5.dp),
                    color = palette.accent.copy(alpha = 0.90f)
                ) {
                    Text(
                        text = "${placement.spanX}×${placement.spanY}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
