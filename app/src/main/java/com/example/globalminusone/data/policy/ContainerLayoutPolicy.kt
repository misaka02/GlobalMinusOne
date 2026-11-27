package com.example.globalminusone.data.policy

import kotlin.math.max
import kotlin.math.ceil
import com.example.globalminusone.data.ComponentCategory
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.operations.MAX_COMPONENT_TREE_DEPTH
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.operations.componentPath
import com.example.globalminusone.data.isContainerType

const val CONTAINER_VIEWPORT_MAX_DP = 480f
const val TEXT_LABEL_FONT_SIZE_SP = 13f
const val TEXT_LABEL_LINE_HEIGHT_SP = 18f
const val COMPACT_CONTAINER_DIVIDER_DP = 1f
const val COMPACT_CONTAINER_PADDING_DP = 6f
const val COMPACT_CONTAINER_HORIZONTAL_PADDING_DP = 8f
const val COMPACT_CONTAINER_GAP_DP = 8f
const val COMPACT_CONTAINER_ACTION_LABEL_LINE_SP = 18f

/** GRID gives launcher icons a common cell; FREE continues to use saved geometry. */
fun isContainerGridIcon(type: ComponentType): Boolean = type in setOf(
    ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT, ComponentType.FILE_SHORTCUT, ComponentType.FOLDER)

fun isContainerGridIconItem(item: ComponentItem): Boolean = isContainerGridIcon(item.type) ||
    (item.type == ComponentType.WEBSITE_LINK && item.gridSpanX == 1 && item.gridSpanY == 1) ||
    isSingleIconLauncherItem(item)

/** Zero leaves legacy automatic/derived icon sizing intact. */
fun containerIconSizeDp(item: ComponentItem): Int? = item.containerIconSizeDp.takeIf { it > 0 }?.coerceIn(1, 192)
    ?: item.containerItemSizeDp.takeIf { it > 0 }?.let { (it * .45f).toInt().coerceIn(24, 96) }

/** Compact switch/action tiles snap to single-column grid cells unless manually resized. */
fun isContainerGridTile(type: ComponentType): Boolean =
    type.category == ComponentCategory.SWITCH ||
        (type.category == ComponentCategory.SYSTEM_ACTION && type != ComponentType.OPERATION_BAR) ||
        type == ComponentType.SHELL_COMMAND

/** Maps a desired 1..totalSlots column span inside a GRID container to the host page's gridColumns units. */
fun containerGridSpanWidth(
    spanCols: Int,
    totalSlots: Int,
    gridColumns: Int,
    itemGapDp: Int = 0,
    widthDp: Float = gridColumns * 10f
): Int {
    val count = gridColumns.coerceIn(8, 64)
    val slots = totalSlots.coerceIn(1, 8)
    val span = spanCols.coerceIn(1, slots)
    if (span >= slots) return count
    val unitX = (widthDp / count).coerceAtLeast(.1f)
    val gapX = ceil(itemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP) / unitX).toInt()
    val cellW = ((count - gapX * (slots - 1)) / slots).coerceIn(1, count)
    return (cellW * span + gapX * (span - 1)).coerceIn(1, count)
}

/** A folder scope overrides panel editing, including when that scope is currently idle. */
fun containerEditingMode(panelEditing: Boolean, folderEditing: Boolean?, locked: Boolean): Boolean =
    !locked && (folderEditing ?: panelEditing)

private fun validFontScale(fontScale: Float): Float = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f

/** Material's 48dp touch target, or the explicitly rendered 20sp label plus 4dp padding per side. */
fun compactContainerAddRowHeightDp(fontScale: Float = 1f): Float = max(48f, 20f * validFontScale(fontScale) + 8f)

/** GRID budgets the rendered icon/label footprint. FREE retains its historical default geometry. */
fun legacyContainerActionTileHeightDp(item: ComponentItem, fontScale: Float = 1f): Float {
    if (item.containerLayout == ContainerLayoutType.GRID) {
        val spacing = containerIconVerticalSpacing(item)
        val content = ((containerIconSizeDp(item) ?: 38) + COMPACT_CONTAINER_ACTION_LABEL_LINE_SP * validFontScale(fontScale) +
            spacing.labelGapDp + 2f * spacing.paddingDp).coerceAtLeast(24f)
        return max(item.containerItemSizeDp.takeIf { it > 0 }?.coerceIn(48, 240)?.toFloat() ?: 0f, content)
    }
    val legacy = item.containerItemSizeDp.takeIf { it > 0 }?.coerceIn(48, 240)?.toFloat() ?: 88f
    return if (item.containerIconSizeDp > 0) max(legacy, item.containerIconSizeDp.coerceIn(1, 192) + 18f * validFontScale(fontScale) + 12f) else legacy
}

fun legacyContainerActionGridHeightDp(item: ComponentItem, fontScale: Float = 1f): Float {
    if (item.containerActions.isEmpty()) return 0f
    val cols = item.containerColumns.coerceIn(1, 8)
    val rows = if (item.containerLayout == ContainerLayoutType.GRID) {
        resolveContainerActionGridPlacements(item.containerActions, cols).maxOf { it.row + 1 }
    } else {
        (item.containerActions.size + cols - 1) / cols
    }
    val gap = if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item)
        else item.containerItemGapDp.coerceIn(MIN_GRID_GAP_DP, MAX_GRID_GAP_DP)
    val trailingGaps = if (item.containerLayout == ContainerLayoutType.GRID || item.containerCompact) (rows - 1).coerceAtLeast(0) else rows
    val tileHeight = legacyContainerActionTileHeightDp(item, fontScale)
    return (rows * tileHeight + trailingGaps * gap).coerceAtLeast(tileHeight)
}

fun compactContainerHeaderHeightDp(fontScale: Float = 1f): Float =
    maxOf(48f, 24f * (fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f) + 16f)

/** Shared display-only measurement for root and nested free-layout slots; never persists estimated dimensions. */
fun componentDisplayHeightDp(
    item: ComponentItem,
    outerWidthDp: Float,
    gridColumns: Int,
    configuredHeightUnitDp: Int = 0,
    fontScale: Float = 1f,
    depth: Int = 0,
    compactExpansion: Map<String, Float> = emptyMap()
): Float {
    val columns = gridColumns.coerceIn(8, 64)
    val outerUnit = if (configuredHeightUnitDp > 0) configuredHeightUnitDp.toFloat() else outerWidthDp / item.w.coerceAtLeast(1)
    val baseSavedHeight = item.h.coerceAtLeast(1) * outerUnit
    val savedHeight = if (item.orientation == ComponentOrientation.VERTICAL &&
        supportsVerticalOrientation(item.type) &&
        item.h <= item.type.defaultH
    ) {
        max(baseSavedHeight, verticalComponentMinHeightDp(item.type))
    } else {
        baseSavedHeight
    }
    val header = componentHeaderHeightDp(item, fontScale)
    if (item.type == ComponentType.CONTAINER_BOX && item.containerCompact) {
        val fraction = compactExpansion[item.id]?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val collapsed = compactContainerHeaderHeightDp(fontScale) + item.marginTopDp + item.marginBottomDp
        if (fraction == 0f || depth >= MAX_COMPONENT_TREE_DEPTH) return collapsed
        // The curtain has its own padding and no ordinary card wrapper/title. Measure exactly
        // the width supplied to its action grid and nested renderer, including their row snapping.
        val width = (outerWidthDp - item.marginLeftDp - item.marginRightDp -
            2f * COMPACT_CONTAINER_HORIZONTAL_PADDING_DP).coerceAtLeast(columns.toFloat())
        val content = containerContentHeightDp(item, width, columns, configuredHeightUnitDp, fontScale, depth, compactExpansion)
        val groups = (if (item.containerActions.isNotEmpty()) 1 else 0) +
            (if (item.containerComponents.isNotEmpty()) 1 else 0)
        // Non-empty collapsible containers place '+' inside the header so zero bottom add-bar space is wasted.
        // Empty containers keep a single compact empty-state touch target inside the curtain body.
        val viewport = if (groups == 0) {
            (compactContainerAddRowHeightDp(fontScale) + 2f * COMPACT_CONTAINER_PADDING_DP)
                .coerceAtMost(CONTAINER_VIEWPORT_MAX_DP)
        } else {
            (content + 2f * COMPACT_CONTAINER_PADDING_DP)
                .coerceAtMost(CONTAINER_VIEWPORT_MAX_DP)
        }
        return collapsed + (COMPACT_CONTAINER_DIVIDER_DP + viewport) * fraction
    }
    if (item.autoHeight && item.type == ComponentType.TEXT_LABEL) {
        val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val wrapperPadding = item.paddingTopDp.coerceIn(0, 48) + item.paddingBottomDp.coerceIn(0, 48) +
            item.marginTopDp + item.marginBottomDp
        val textWidth = (outerWidthDp - item.marginLeftDp - item.marginRightDp -
            item.paddingLeftDp.coerceIn(0, 48) - item.paddingRightDp.coerceIn(0, 48) - 16f).coerceAtLeast(1f)
        // Budget one full-width glyph per 13sp. Explicit breaks and narrow slots grow the
        // body; stop at twelve display lines so unbounded text cannot grow the page forever.
        val columnsPerLine = (textWidth / (TEXT_LABEL_FONT_SIZE_SP * scale)).toInt().coerceAtLeast(1)
        var lines = 1
        var column = 0
        for (char in item.textContent) {
            if (char == '\r') continue
            if (char == '\n') { lines++; column = 0 }
            else if (column >= columnsPerLine) { lines++; column = 1 }
            else column++
            if (lines >= 12) { lines = 12; break }
        }
        val body = (lines * TEXT_LABEL_LINE_HEIGHT_SP * scale).coerceAtMost(CONTAINER_VIEWPORT_MAX_DP)
            .coerceAtLeast(TEXT_LABEL_LINE_HEIGHT_SP * scale)
        return max(savedHeight, header + wrapperPadding + 16f + body)
    }
    val singleIconLauncher = isSingleIconLauncherItem(item)
    if (!singleIconLauncher &&
        (item.type == ComponentType.APP_DRAWER_CARD || item.type == ComponentType.APP_KEYBOARD_LAUNCHER || item.type == ComponentType.RECENT_APPS_CARD) &&
        (item.autoHeight || item.h !in 1..120)) {
        // Old configurations may contain enormous drawer heights. Valid manual
        // dimensions remain authoritative; the surrounding page supplies scrolling.
        return savedHeight.coerceAtMost(CONTAINER_VIEWPORT_MAX_DP)
    }
    if (item.autoHeight && item.type == ComponentType.FOLDER) {
        // Folder draws its own icon surface and has no wrapper padding. A small
        // panel must still leave room below the optional outer title for its icon.
        val icon = minOf(62f, outerWidthDp.coerceAtLeast(1f))
        val label = if (!item.showTitle && item.title.isNotBlank()) 20f * fontScale else 0f
        return max(savedHeight, icon + label + header + item.marginTopDp + item.marginBottomDp)
    }
    if (item.autoHeight && (item.type in setOf(ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.STATIC_SHORTCUT) || singleIconLauncher)) {
        val icon = if (item.type == ComponentType.APP_ICON) 36f else 34f
        // Explicit line heights match AppIconComponent/CustomShortcutComponent, even for an empty label.
        val label = 14f * fontScale
        val subtitle = if (item.type != ComponentType.APP_ICON && item.subtitle.isNotBlank()) 12f * fontScale else 0f
        val innerPadding = 8f + 2f
        val wrapperPadding = item.paddingTopDp.coerceIn(0, 48) + item.paddingBottomDp.coerceIn(0, 48) + item.marginTopDp + item.marginBottomDp
        return max(savedHeight, icon + label + subtitle + innerPadding + wrapperPadding + header)
    }
    if (item.type != ComponentType.CONTAINER_BOX || !item.autoHeight || item.containerCompact || depth >= MAX_COMPONENT_TREE_DEPTH) {
        return savedHeight
    }
    val paddingX = item.marginLeftDp + item.marginRightDp + item.paddingLeftDp.coerceIn(0, 48) + item.paddingRightDp.coerceIn(0, 48) + 4
    val contentWidth = (outerWidthDp - paddingX).coerceAtLeast(columns.toFloat())
    val body = containerContentHeightDp(item, contentWidth, columns, configuredHeightUnitDp, fontScale, depth, compactExpansion) + 4f
    val emptyHeight = if (item.containerComponents.isEmpty() && item.containerActions.isEmpty()) 24f + 18f * fontScale else 0f
    return max(body, emptyHeight).coerceAtMost(CONTAINER_VIEWPORT_MAX_DP) + header + item.paddingTopDp.coerceIn(0, 48) + item.paddingBottomDp.coerceIn(0, 48) + item.marginTopDp + item.marginBottomDp
}

private fun containerContentHeightDp(item: ComponentItem, contentWidth: Float, columns: Int,
    configuredHeightUnitDp: Int, fontScale: Float, depth: Int, compactExpansion: Map<String, Float>): Float {
    val stepX = contentWidth / columns
    val stepY = if (configuredHeightUnitDp > 0) configuredHeightUnitDp.toFloat() else stepX
    val gridWidth = containerGridContentWidthDp(item, contentWidth)
    val gridStepY = if (configuredHeightUnitDp > 0) stepY else gridWidth / columns
    val gridChildren = if (item.containerLayout == ContainerLayoutType.GRID) containerGridChildBounds(item.containerComponents,
        columns, gridWidth, gridStepY, configuredHeightUnitDp, fontScale, item.containerColumns, compactExpansion,
        item.containerItemGapDp, item.containerItemSizeDp, item.containerIconSizeDp,
        containerRowGapDp(item), item.containerItemPaddingVerticalDp, item.containerIconLabelGapDp) else emptyList()
    fun measured(children: List<ComponentItem>, fractions: Map<String, Float>) = children.map { child ->
        val height = componentDisplayHeightDp(child,
            child.w.coerceIn(1, columns) * stepX, columns, configuredHeightUnitDp, fontScale, depth + 1, fractions)
        // Nested slots snap their display height to whole local rows; parent must budget the same rows.
        child.copy(h = ceil(height / stepY.coerceAtLeast(.1f)).toInt().coerceAtLeast(1))
    }
    // GRID bounds already contain the final bare-icon footprint in exact dp.
    // FREE still measures and displaces saved logical-grid rows without rewriting them.
    val measuredChildren = if (item.containerLayout == ContainerLayoutType.GRID) emptyList()
        else measured(item.containerComponents, compactExpansion)
    val positioned = if (item.containerLayout == ContainerLayoutType.GRID || compactExpansion.isEmpty()) measuredChildren
        else displaceExpandedContainerRows(item.containerComponents, measured(item.containerComponents, emptyMap()), measuredChildren)
    val childBottom = if (item.containerLayout == ContainerLayoutType.GRID) gridChildren.maxOfOrNull { it.y + it.height } ?: 0f
        else positioned.maxOfOrNull { (it.y.coerceAtLeast(0) + it.h) * stepY } ?: 0f
    val actionColumns = item.containerColumns.coerceIn(1, 8)
    val actionRows = if (item.containerActions.isEmpty()) 0
        else if (item.containerLayout == ContainerLayoutType.GRID) resolveContainerActionGridPlacements(item.containerActions, actionColumns).maxOf { it.row + 1 }
        else (item.containerActions.size + actionColumns - 1) / actionColumns
    val actionHeight = (if (item.containerLayout == ContainerLayoutType.FREE) item.containerActions.mapIndexed { index, action ->
        containerActionGeometry(action, index, actionColumns, contentWidth, item.containerItemSizeDp, item.containerItemGapDp)
    }.maxOfOrNull { it.y + it.height } ?: 0f else
        legacyContainerActionGridHeightDp(item, fontScale)).coerceAtMost(352f)
    val childViewport = if (item.containerCompact) childBottom.coerceAtMost(CONTAINER_VIEWPORT_MAX_DP) else childBottom
    val groupGap = if (item.containerLayout == ContainerLayoutType.GRID) containerRowGapDp(item).toFloat() else COMPACT_CONTAINER_GAP_DP
    return actionHeight + childViewport + (if (actionRows > 0 && childBottom > 0f) groupGap else 0f)
}

fun componentDisplayRows(item: ComponentItem, outerWidthDp: Float, stepYDp: Float, gridColumns: Int,
    configuredHeightUnitDp: Int = 0, fontScale: Float = 1f, compactExpansion: Map<String, Float> = emptyMap()): Int =
    ceil(componentDisplayHeightDp(item, outerWidthDp, gridColumns, configuredHeightUnitDp, fontScale,
        compactExpansion = compactExpansion) / stepYDp.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1)

/** FREE measures only the rendered height; saved x/y/w/h remain the source of truth. */
fun measureFreeContainerChildren(children: List<ComponentItem>, columns: Int, widthDp: Float, stepYDp: Float,
    configuredHeightUnitDp: Int = 0, fontScale: Float = 1f,
    compactExpansion: Map<String, Float> = emptyMap()): List<ComponentItem> {
    val count = columns.coerceIn(8, 64)
    fun measure(fractions: Map<String, Float>) = children.map { child -> child.copy(h = componentDisplayRows(child,
        widthDp * child.w.coerceIn(1, count) / count, stepYDp, count, configuredHeightUnitDp, fontScale, fractions)) }
    val shown = measure(compactExpansion)
    return if (compactExpansion.isEmpty()) shown else displaceExpandedContainerRows(children, measure(emptyMap()), shown)
}

/** Legacy logical-grid adapter. Rendering and height measurement use containerGridChildBounds for exact dp. */
fun arrangeContainerChildren(children: List<ComponentItem>, columns: Int, widthDp: Float, stepYDp: Float,
    configuredHeightUnitDp: Int = 0, fontScale: Float = 1f, iconColumns: Int = 0,
    compactExpansion: Map<String, Float> = emptyMap(), itemGapDp: Int = -1, itemSizeDp: Int = 0, iconSizeDp: Int = 0): List<ComponentItem> {
    val count = columns.coerceIn(8, 64)
    val unitX = (widthDp / count).coerceAtLeast(.1f)
    val gapX = ceil(itemGapDp.coerceIn(0, 48) / unitX).toInt()
    val gapY = if (itemGapDp >= 0) ceil(itemGapDp.coerceIn(0, 48) / stepYDp.coerceAtLeast(.1f)).toInt() else 1
    val slots = iconColumns.coerceIn(1, 8)
    val slotMaxW = ((count - gapX * (slots - 1)) / slots).coerceIn(1, count)
    val iconWidth = (if (itemSizeDp > 0 && iconSizeDp <= 0) minOf(slotMaxW, ceil(itemSizeDp.coerceIn(48, 240) / unitX).toInt())
        else slotMaxW).coerceIn(1, count)
    // Match the bare icon renderer: no generic card margins, title or padding.
    // Folder retains its own surface/header measurement; saved icon geometry is ignored.
    val iconRows = if (iconColumns >= 1) children.filter { isContainerGridIconItem(it) }.maxOfOrNull { child ->
        if (child.type == ComponentType.FOLDER) componentDisplayRows(child.copy(w = iconWidth, h = 1, autoHeight = true),
            widthDp * iconWidth / count, stepYDp, count, configuredHeightUnitDp, fontScale, compactExpansion)
        else {
            val requestedIcon = if (iconSizeDp > 0) iconSizeDp.coerceIn(1, 192).toFloat() else if (itemSizeDp > 0) (itemSizeDp * .45f).toInt().coerceIn(24, 96).toFloat()
                else if (child.type == ComponentType.APP_ICON) 36f else 34f
            val icon = minOf(requestedIcon, (widthDp * iconWidth / count - 4f).coerceAtLeast(1f))
            val subtitle = if (child.type != ComponentType.APP_ICON && child.subtitle.isNotBlank()) 12f * validFontScale(fontScale) else 0f
            ceil((icon + 14f * validFontScale(fontScale) + subtitle + 8f + 2f) /
                stepYDp.coerceAtLeast(.1f)).toInt().coerceAtLeast(1)
        }
    } ?: 1 else 1
    var x = 0; var y = 0; var rowHeight = 0; var slotsInRow = 0
    return children.map { child ->
        val standardIcon = isContainerGridIconItem(child)
        val explicitSpanCols = if (iconColumns >= 1 && child.gridSpanX > 0) {
            if (child.gridSpanX >= GRID_SPAN_FULL_ROW) slots else child.gridSpanX.coerceIn(1, slots)
        } else 0
        val singleSlotTile = !standardIcon && iconColumns >= 1 && explicitSpanCols == 0 &&
            isContainerGridTile(child.type) && child.w == child.type.defaultW
        val w = when {
            explicitSpanCols > 0 -> containerGridSpanWidth(explicitSpanCols, slots, count, itemGapDp.coerceAtLeast(0), widthDp)
            (standardIcon && (iconColumns >= 1 || itemSizeDp > 0)) || singleSlotTile -> iconWidth
            else -> child.w
        }.coerceIn(1, count)
        val occupiedSlots = when {
            explicitSpanCols > 0 -> explicitSpanCols
            standardIcon || singleSlotTile -> 1
            else -> 0
        }
        if (x + w > count || (occupiedSlots > 0 && iconColumns >= 1 && slotsInRow + occupiedSlots > slots)) {
            x = 0; y += rowHeight + gapY; rowHeight = 0; slotsInRow = 0
        }
        // The width supplied below belongs to the arranged child, not its saved
        // default width. Otherwise e.g. a six-column Folder is measured shorter
        // than its actual slot and the following row overlaps it.
        val h = if (standardIcon && iconColumns >= 1 && child.gridSpanY <= 1) iconRows
            else componentDisplayRows(child.copy(w = w), widthDp * w / count, stepYDp, count, configuredHeightUnitDp, fontScale, compactExpansion)
        val sizeRows = if (standardIcon && itemSizeDp > 0) ceil(itemSizeDp.coerceIn(48, 240) / stepYDp.coerceAtLeast(.1f)).toInt() else 0
        val unitCellRows = maxOf(iconRows, sizeRows.coerceAtLeast(1)).coerceAtLeast(4)
        val spanRows = if (iconColumns >= 1 && child.gridSpanY > 0) {
            val sy = child.gridSpanY.coerceIn(1, 12)
            sy * unitCellRows + (sy - 1) * gapY
        } else 0
        child.copy(x = x, y = y, w = w, h = maxOf(h, sizeRows, spanRows)).also {
            x += w + gapX; rowHeight = maxOf(rowHeight, it.h)
            if (occupiedSlots > 0) slotsInRow += occupiedSlots
        }
    }
}

/** Display-only push-down for free layouts; collapse restores the exact saved positions. */
fun displaceExpandedContainerRows(original: List<ComponentItem>, collapsed: List<ComponentItem>,
    expanded: List<ComponentItem>): List<ComponentItem> {
    val base = collapsed.associateBy { it.id }
    val shown = expanded.associateBy { it.id }
    val placed = mutableMapOf<String, ComponentItem>()
    original.sortedBy { it.y }.forEach { item ->
        val shift = original.asSequence().filter { other -> other.id != item.id && other.y < item.y &&
            other.y + (base[other.id]?.h ?: other.h) <= item.y }.mapNotNull { other ->
            placed[other.id]?.let { it.y + it.h - (other.y + (base[other.id]?.h ?: other.h)) }
        }.maxOrNull()?.coerceAtLeast(0) ?: 0
        placed[item.id] = (shown[item.id] ?: item).copy(y = item.y + shift)
    }
    return original.map { placed.getValue(it.id) }
}

private fun componentHeaderHeightDp(item: ComponentItem, fontScale: Float): Float =
    if (item.showTitle && item.title.isNotBlank()) max(24f, 20f * fontScale) + 6f else 0f

/** New placement uses the same display footprint as slots, without rewriting any saved sibling. */
fun nextComponentPlacementY(page: PageConfig, containerId: String?, gridColumns: Int,
    panelWidthDp: Float, configuredHeightUnitDp: Int = 0, fontScale: Float = 1f): Int {
    val columns = gridColumns.coerceIn(8, 64)
    var width = (panelWidthDp - if (page.layoutType == PageLayoutType.LINEAR) 24f else 0f).coerceAtLeast(1f)
    var stepX = width / columns
    var siblings = page.components
    if (containerId != null) {
        val path = page.components.componentPath(containerId) ?: return 0
        for (id in path) {
            val container = siblings.firstOrNull { it.id == id } ?: return 0
            if (!container.type.isContainerType()) return 0
            val paddingX = container.marginLeftDp + container.marginRightDp +
                max(8, container.paddingLeftDp) + max(8, container.paddingRightDp) + 4
            width = (container.w.coerceIn(1, columns) * stepX - paddingX).coerceAtLeast(columns.toFloat())
            stepX = width / columns
            siblings = container.containerComponents
        }
    }
    val stepY = if (configuredHeightUnitDp > 0) configuredHeightUnitDp.toFloat() else stepX
    val bottom = siblings.maxOfOrNull { item ->
        item.y.toLong() + componentDisplayRows(item, item.w.coerceIn(1, columns) * stepX,
            stepY, columns, configuredHeightUnitDp, fontScale)
    } ?: return 0
    return (bottom + 1).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
}

/**
 * Resolves a finite Compose viewport height ceiling for container rendering.
 * Never returns Float.MAX_VALUE when inside a popup's verticalScroll (unbounded height constraint),
 * preventing Compose Constraints(maxHeight) overflow crashes on opening folders.
 */
fun resolveContainerViewportHeightPx(
    hasBoundedHeight: Boolean,
    maxConstraintHeightPx: Float,
    insideOwnPopup: Boolean,
    screenHeightPx: Float,
    maxFallbackPx: Float
): Float {
    val safeFallback = maxFallbackPx.takeIf { it.isFinite() && it > 0f } ?: 1440f
    val popupCap = if (insideOwnPopup) {
        val safeScreen = screenHeightPx.takeIf { it.isFinite() && it > 0f } ?: safeFallback
        maxOf(safeFallback, safeScreen.coerceAtMost(safeFallback * 5f))
    } else {
        safeFallback
    }
    val bounded = if (hasBoundedHeight && maxConstraintHeightPx.isFinite() && maxConstraintHeightPx > 0f) {
        maxConstraintHeightPx
    } else {
        popupCap
    }
    return minOf(bounded, popupCap).coerceAtLeast(1f)
}

/**
 * Resolves effective animation duration in milliseconds while preserving 0 = snap (off)
 * and eliminating sluggish artificial 60-130ms floors on fast settings.
 */
fun resolveEffectiveAnimationMs(configuredMs: Int, scale: Float = 1f, maxMs: Int = 800): Int {
    if (configuredMs <= 0) return 0
    val ceiling = maxMs.coerceAtLeast(16)
    return kotlin.math.round(configuredMs * scale).toInt().coerceIn(16, ceiling)
}
