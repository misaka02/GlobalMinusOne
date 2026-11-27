package com.example.globalminusone.ui.panel.container

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.globalminusone.data.*
import com.example.globalminusone.system.SystemController
import com.example.globalminusone.ui.motion.MotionTokens
import kotlin.math.roundToInt
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.policy.LEGACY_ACTION_VIEWPORT_MAX_DP
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.policy.commitContainerActionGeometry
import com.example.globalminusone.data.policy.commitContainerActionGridPlacement
import com.example.globalminusone.data.policy.containerActionGeometry
import com.example.globalminusone.data.policy.containerActionGridGeometry
import com.example.globalminusone.data.policy.containerGridContentWidthDp
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerIconVerticalSpacing
import com.example.globalminusone.data.policy.containerPopupColumns
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.policy.legacyContainerActionGridHeightDp
import com.example.globalminusone.data.policy.legacyContainerActionTileHeightDp
import com.example.globalminusone.ui.panel.cards.CardLayoutActions
import com.example.globalminusone.ui.panel.cards.ContainerChildTile
import com.example.globalminusone.ui.panel.cards.LegacyContainerActions
import com.example.globalminusone.ui.panel.gesture.LocalPanelGestureOwnership
import com.example.globalminusone.ui.panel.LocalPanelPalette
import com.example.globalminusone.ui.panel.gesture.PanelGestureAxis
import com.example.globalminusone.ui.panel.gesture.containerPanelScroll
import com.example.globalminusone.ui.panel.editor.detectStationaryDragGestures
import com.example.globalminusone.ui.panel.gesture.ownPanelScrollGestures
import com.example.globalminusone.ui.panel.cards.rememberCardLayoutActions
import com.example.globalminusone.ui.panel.gesture.rememberContainerScrollRoute
import com.example.globalminusone.ui.panel.editor.rememberEditDragViewport
import com.example.globalminusone.ui.panel.cards.rememberLegacyContainerActions
import com.example.globalminusone.ui.panel.gesture.shouldEnableChildHorizontalScroll
import com.example.globalminusone.ui.panel.editor.stableScreenPositionFactory

/** Legacy quick actions keep their IDs and data while gaining the same drag ordering as cards. */
@Composable
internal fun LegacyContainerActionGrid(item: ComponentItem, pageIndex: Int, state: GlobalMinusOneState,
    repository: MinusOneRepository, system: SystemController?, editing: Boolean, onClose: () -> Unit,
    onMain: () -> Unit, onEdit: (QuickActionItem) -> Unit) {
    LegacyContainerGridContent(item, pageIndex, state.advancedConfig.layoutLocked, state.advancedConfig.eyeProtectionEnabled,
        rememberCardLayoutActions(repository), rememberLegacyContainerActions(repository, system), editing, onClose, onMain, onEdit)
}

@Composable
internal fun LegacyContainerGridContent(item: ComponentItem, pageIndex: Int, layoutLocked: Boolean, eyeProtectionEnabled: Boolean,
    layoutActions: CardLayoutActions, legacyActions: LegacyContainerActions, editing: Boolean, onClose: () -> Unit,
    onMain: () -> Unit, onEdit: (QuickActionItem) -> Unit
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalPanelGestureOwnership.current
    val insideFolder = LocalFolderEditAction.current != null
    val scroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val scrollRoute = rememberContainerScrollRoute(scroll)
    val viewport = rememberEditDragViewport(scrollBy = { scroll.scrollBy(it) })
    var preview by remember(item.id) { mutableStateOf<List<String>?>(null) }
    var moving by remember(item.id) { mutableStateOf<String?>(null) }
    var dragOrder by remember(item.id) { mutableStateOf<FolderDragOrder?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    var scrollStart by remember { mutableFloatStateOf(0f) }
    val order = preview?.takeIf { it.toSet() == item.containerActions.map { a -> a.id }.toSet() } ?: item.containerActions.map { it.id }
    val latestOrder by rememberUpdatedState(order)
    val free = item.containerLayout == ContainerLayoutType.FREE
    var previewDropCell by remember(item.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val dropCellRef = remember(item.id) { arrayOfNulls<Pair<Int, Int>>(1) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (LocalContainerPopupId.current == item.id && !editing && moving == null) containerPopupColumns(item)
            else item.containerColumns.coerceIn(1, 8)
        val viewportWidthDp = with(density) { constraints.maxWidth.toDp().value }
        val widthDp = if (free) viewportWidthDp else containerGridContentWidthDp(item, viewportWidthDp, cols)
        val availableWidthPx = with(density) { widthDp.dp.toPx() }
        val tileDp = legacyContainerActionTileHeightDp(item, density.fontScale).let { if (free) it.toInt().toFloat() else it }
        val gapDp = item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
        val rowGapDp = containerRowGapDp(item)
        val previewItem = if (!free && moving != null && previewDropCell != null) {
            item.copy(
                containerActions = commitContainerActionGridPlacement(
                    actions = item.containerActions,
                    movedId = moving!!,
                    columns = cols,
                    targetCol = previewDropCell!!.first,
                    targetRow = previewDropCell!!.second
                )
            )
        } else item
        val geometries = previewItem.containerActions.mapIndexed { index, action -> action.id to
            if (free) containerActionGeometry(action, index, cols, widthDp, tileDp.toInt(), gapDp)
            else containerActionGridGeometry(previewItem, index, cols, widthDp, density.fontScale) }.toMap()
        val raw = origin + delta + Offset(0f, viewport.consumedScroll - scrollStart)
        Box(Modifier.fillMaxWidth().heightIn(max = LEGACY_ACTION_VIEWPORT_MAX_DP.dp).onGloballyPositioned { viewport.bounds = it.boundsInRoot() }
            .containerPanelScroll(scrollRoute, enabled = true)) {
            val freeBottom = geometries.values.maxOfOrNull { it.y + it.height } ?: 0f
            val movingMaxY = (LEGACY_ACTION_VIEWPORT_MAX_DP - (moving?.let { geometries[it]?.height } ?: tileDp.toFloat())).coerceAtLeast(0f)
            val movingBottom = if (moving != null) with(density) { raw.y.toDp().value }.coerceIn(0f, movingMaxY) + (moving?.let { geometries[it]?.height } ?: tileDp.toFloat()) else 0f
            val gridHeightBase = legacyContainerActionGridHeightDp(previewItem.copy(containerColumns = cols), density.fontScale)
            val extraGridRowDp = if (!free && (editing || moving != null)) (tileDp + rowGapDp).coerceAtLeast(48f) else 0f
            val height = if (free) maxOf(freeBottom, movingBottom, 1f).coerceAtMost(LEGACY_ACTION_VIEWPORT_MAX_DP)
                else (gridHeightBase + extraGridRowDp).coerceAtMost(LEGACY_ACTION_VIEWPORT_MAX_DP)
            val actionScrollEnabled = shouldEnableChildHorizontalScroll(!free, horizontalScroll.maxValue)
            Box(Modifier.fillMaxWidth().ownPanelScrollGestures(actionScrollEnabled,
                axis = PanelGestureAxis.Horizontal).horizontalScroll(horizontalScroll, enabled = actionScrollEnabled)) {
            Box(Modifier.width(widthDp.dp).height(height.dp)) {
                item.containerActions.forEach { action -> key(action.id) {
                    val interaction = remember(action.id) { FolderItemInteraction() }
                    val inputCoordinates = remember(action.id) { arrayOfNulls<LayoutCoordinates>(1) }
                    val dragging = moving == action.id
                    val geometry = geometries[action.id]
                        ?: containerActionGridGeometry(previewItem, previewItem.containerActions.indexOfFirst { it.id == action.id }.coerceAtLeast(0), cols, widthDp, density.fontScale)
                    val width = geometry.width.dp
                    val tileHeight = geometry.height.dp
                    val savedPosition = with(density) { Offset(geometry.x.dp.toPx(), geometry.y.dp.toPx()) }
                    val maxDragYPx = if (free) with(density) { (LEGACY_ACTION_VIEWPORT_MAX_DP - geometry.height).coerceAtLeast(0f).dp.toPx() } else Float.MAX_VALUE
                    val goal = if (dragging) Offset(
                        raw.x.coerceIn(0f, (availableWidthPx - with(density) { width.toPx() }).coerceAtLeast(0f)),
                        raw.y.coerceIn(0f, maxDragYPx)
                    ) else savedPosition
                    val themeCfg = LocalPanelPalette.current.themeConfig
                    val animMs = themeCfg.resolvedCardReorderMs()
                    val reorderStyle = themeCfg.moduleAnimations.cardReorderStyle
                    val animated by animateOffsetAsState(
                        goal,
                        if (dragging || animMs <= 0) snap() else MotionTokens.cardOffsetSpring(animMs, reorderStyle),
                        label = "legacy-action"
                    )
                    val latestPosition by rememberUpdatedState(savedPosition)
                    val latestGeometry by rememberUpdatedState(geometry)
                    Box(Modifier.offset { val p = if (dragging) goal else animated; IntOffset(p.x.roundToInt(), p.y.roundToInt()) }
                        .size(width, tileHeight).zIndex(if (dragging) 2f else 0f)) {
                        CompositionLocalProvider(LocalFolderItemInteraction provides if (insideFolder && !editing) interaction else null,
                            LocalFolderGridSpacing provides if (free) null else containerIconVerticalSpacing(item),
                            LocalFolderIconSizeDp provides if (free) null else containerIconSizeDp(item)) {
                        ContainerChildTile(action, editing, eyeProtectionEnabled, Modifier.fillMaxSize(),
                            onEdit = { onEdit(action) }, onClick = { legacyActions.execute(action, context, scope, onClose, onMain) })
                        }
                        if ((editing || insideFolder) && !layoutLocked) Box(Modifier.matchParentSize()
                            .onGloballyPositioned { inputCoordinates[0] = it }
                            .pointerInput(action.id, free, cols, widthDp, tileDp, gapDp, rowGapDp, density.density, editing, insideFolder) {
                            detectStationaryDragGestures(owner,
                                positionInStableSpaceFactory = stableScreenPositionFactory(hostView) { inputCoordinates[0] },
                                onTap = { if (insideFolder && !editing) interaction.tap?.invoke() else onEdit(action) },
                                onLongPressPreview = if (insideFolder && !editing) ({ interaction.openMenu?.invoke() }) else null,
                                onLongPressCancel = if (insideFolder && !editing) ({ interaction.dismissHeldMenu?.invoke() }) else null,
                                onStationaryLongPress = if (insideFolder && !editing) ({ interaction.finishHoldMenu?.invoke() }) else null,
                                onDragStart = { point ->
                                    origin = latestPosition; delta = Offset.Zero
                                    scrollStart = viewport.consumedScroll; moving = action.id; preview = latestOrder
                                    val pitchX = with(density) { (latestGeometry.width + gapDp).dp.toPx() }.coerceAtLeast(8f)
                                    val pitchY = with(density) { (tileDp + rowGapDp).dp.toPx() }.coerceAtLeast(12f)
                                    val initCell = (latestPosition.x / pitchX).roundToInt().coerceIn(0, cols - 1) to
                                        (latestPosition.y / pitchY).roundToInt().coerceAtLeast(0)
                                    dropCellRef[0] = initCell
                                    previewDropCell = initCell
                                    viewport.begin(Offset(viewport.bounds.left + origin.x + point.x, viewport.bounds.top + origin.y - scroll.value + point.y))
                                },
                                onDrag = { change, amount ->
                                    change.consume(); delta += amount; viewport.moveBy(amount)
                                    if (!free) {
                                        val cur = origin + delta + Offset(0f, viewport.consumedScroll - scrollStart)
                                        val pitchX = with(density) { (latestGeometry.width + gapDp).dp.toPx() }.coerceAtLeast(8f)
                                        val pitchY = with(density) { (tileDp + rowGapDp).dp.toPx() }.coerceAtLeast(12f)
                                        val cell = (cur.x / pitchX).roundToInt().coerceIn(0, cols - 1) to
                                            (cur.y / pitchY).roundToInt().coerceIn(0, 64)
                                        dropCellRef[0] = cell
                                        if (previewDropCell != cell) previewDropCell = cell
                                    }
                                },
                                onDragEnd = {
                                    val released = origin + delta + Offset(0f, viewport.consumedScroll - scrollStart)
                                    val releasedX = with(density) { released.x.toDp().value }.coerceIn(0f, (widthDp - latestGeometry.width).coerceAtLeast(0f))
                                    val releasedY = with(density) { released.y.toDp().value }
                                        .coerceIn(0f, (LEGACY_ACTION_VIEWPORT_MAX_DP - latestGeometry.height).coerceAtLeast(0f))
                                    val cell = dropCellRef[0] ?: previewDropCell
                                    if (!free && cell != null) {
                                        if (!layoutActions.current.layoutLocked) {
                                            layoutActions.placeActionInContainerGrid(pageIndex, item.id, action.id, cell.first, cell.second, cols)
                                        }
                                    } else {
                                        layoutActions.updateComponent(pageIndex, item.id) { parent ->
                                            if (layoutActions.current.layoutLocked) parent
                                            else parent.copy(containerActions = parent.containerActions.map { current ->
                                                if (current.id == action.id) commitContainerActionGeometry(current,
                                                    releasedX.roundToInt(), releasedY.roundToInt(),
                                                    latestGeometry.width.roundToInt(), latestGeometry.height.roundToInt()) else current
                                            })
                                        }
                                    }
                                    moving = null; preview = null; dragOrder = null; previewDropCell = null; dropCellRef[0] = null; viewport.end()
                                },
                                onDragCancel = {
                                    moving = null; preview = null; dragOrder = null; previewDropCell = null; dropCellRef[0] = null; viewport.end()
                                })
                        })
                    }
                } }
            }
            }
        }
    }
}
