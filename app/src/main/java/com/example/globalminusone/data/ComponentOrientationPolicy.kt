package com.example.globalminusone.data

/**
 * Pure policies for component orientation (horizontal vs vertical sliders/bars)
 * and folder external preview mode.
 */
fun supportsVerticalOrientation(type: ComponentType): Boolean = type in setOf(
    ComponentType.BRIGHTNESS_BAR,
    ComponentType.VOLUME_BAR,
    ComponentType.STAT_BATTERY_BAR,
    ComponentType.STAT_RAM,
    ComponentType.STAT_ROM
)

fun resolveComponentVertical(
    item: ComponentItem,
    widthDp: Float = 0f,
    heightDp: Float = 0f
): Boolean {
    if (!supportsVerticalOrientation(item.type)) return false
    return when (item.orientation) {
        ComponentOrientation.VERTICAL -> true
        ComponentOrientation.HORIZONTAL -> false
        ComponentOrientation.AUTO -> when {
            widthDp > 0f && heightDp > 0f -> heightDp > widthDp * 1.15f
            item.gridSpanX > 0 && item.gridSpanY > 0 -> item.gridSpanY > item.gridSpanX
            else -> item.h > item.w
        }
    }
}

fun isSliderBarType(type: ComponentType): Boolean =
    type == ComponentType.BRIGHTNESS_BAR || type == ComponentType.VOLUME_BAR

fun verticalComponentMinHeightDp(type: ComponentType): Float = when (type) {
    ComponentType.BRIGHTNESS_BAR, ComponentType.VOLUME_BAR -> 132f
    ComponentType.STAT_BATTERY_BAR, ComponentType.STAT_RAM, ComponentType.STAT_ROM -> 104f
    else -> 0f
}

fun defaultGridSpanForOrientation(type: ComponentType, orientation: ComponentOrientation): Pair<Int, Int>? {
    if (orientation != ComponentOrientation.VERTICAL || !supportsVerticalOrientation(type)) return null
    return when (type) {
        ComponentType.BRIGHTNESS_BAR, ComponentType.VOLUME_BAR -> 1 to 3
        ComponentType.STAT_BATTERY_BAR, ComponentType.STAT_RAM, ComponentType.STAT_ROM -> 1 to 2
        else -> null
    }
}

enum class FolderPreviewMode {
    EMPTY,
    SINGLE,
    GRID_2X2
}

fun resolveFolderPreviewMode(itemCount: Int): FolderPreviewMode = when {
    itemCount <= 0 -> FolderPreviewMode.EMPTY
    itemCount == 1 -> FolderPreviewMode.SINGLE
    else -> FolderPreviewMode.GRID_2X2
}
