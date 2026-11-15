package com.example.globalminusone.regression.v2424

import com.example.globalminusone.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FolderEditingPolicyTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test fun oldActionsKeepNullGeometryAndTheirGridFallback() {
        val action = json.decodeFromString<QuickActionItem>("""{"id":"legacy","title":"旧动作","intentUri":"action:test"}""")
        assertNull(action.xDp); assertNull(action.yDp); assertNull(action.wDp); assertNull(action.hDp)
        assertEquals(ContainerActionGeometry(100f, 88f, 100f, 88f),
            containerActionGeometry(action, 3, 2, 200f))
        assertEquals(action, json.decodeFromString<QuickActionItem>(json.encodeToString(action)))
    }

    @Test fun freeActionGeometryCommitsWithoutChangingIdentityOrLaunchData() {
        val action = QuickActionItem(id = "legacy", title = "原动作", intentUri = "action:test", packageName = "example.app")
        val moved = commitContainerActionGeometry(action, 35, 137, 120, 96)
        assertEquals(action.id, moved.id); assertEquals(action.intentUri, moved.intentUri)
        assertEquals(action.packageName, moved.packageName); assertEquals(action.title, moved.title)
        assertEquals(ContainerActionGeometry(35f, 137f, 120f, 96f), containerActionGeometry(moved, 0, 4, 360f))
        assertEquals(moved, json.decodeFromString<QuickActionItem>(json.encodeToString(moved)))
        // Narrow windows constrain display only; the saved geometry remains intact.
        assertEquals(ContainerActionGeometry(0f, 137f, 100f, 96f), containerActionGeometry(moved, 0, 4, 100f))
        assertEquals(120, moved.wDp)
        val reset = moved.copy(xDp = null, yDp = null, wDp = null, hDp = null)
        assertEquals(action, reset)
    }

    @Test fun explicitFreeFolderAndMixedNestedChildrenRoundTripWithoutMigration() {
        val nested = ComponentItem(type = ComponentType.CONTAINER_BOX, containerCompact = true,
            containerComponents = listOf(ComponentItem(type = ComponentType.TEXT_LABEL, textContent = "保留正文")))
        val folder = ComponentItem(type = ComponentType.FOLDER, containerLayout = ContainerLayoutType.FREE,
            containerPopupWidthDp = 420, containerPopupHeightDp = 360, containerItemSizeDp = 96,
            containerItemGapDp = 16, containerComponents = listOf(nested),
            containerActions = listOf(QuickActionItem(title = "旧动作", intentUri = "action:test")))
        assertEquals(folder, json.decodeFromString<ComponentItem>(json.encodeToString(folder)))
        val old = json.decodeFromString<ComponentItem>("""{"type":"FOLDER","containerLayout":"FREE"}""")
        assertEquals(ContainerLayoutType.FREE, old.containerLayout)
        assertEquals(0, old.containerPopupWidthDp)
        assertEquals(0, old.containerPopupHeightDp)
        assertEquals(0, old.containerItemGapDp)
    }

    @Test fun windowDimensionsAreAppliedAndClampedToAvailableSpace() {
        val folder = ComponentItem(type = ComponentType.FOLDER, containerLayout = ContainerLayoutType.FREE,
            containerPopupWidthDp = 420, containerPopupHeightDp = 360)
        assertEquals(420f, containerPopupWidthDp(folder, 600f), 0f)
        assertEquals(280f, containerPopupWidthDp(folder, 280f), 0f)
        assertEquals(360f, containerPopupHeightDp(folder, 600f), 0f)
        assertEquals(190f, containerPopupHeightDp(folder, 190f), 0f)
        assertEquals(600f, containerPopupHeightDp(folder.copy(containerPopupHeightDp = 0), 600f), 0f)
    }

    @Test fun gridReservesConfiguredHorizontalAndVerticalSpacingWithoutChangingSavedCards() {
        val original = List(3) { ComponentItem(type = ComponentType.APP_ICON, x = 7, y = 21) }
        val shown = arrangeContainerChildren(original, 24, 240f, 10f, iconColumns = 2, itemGapDp = 20)
        assertEquals(11, shown[0].w)
        assertEquals(13, shown[1].x)
        assertEquals(shown[0].h + 2, shown[2].y)
        assertTrue(original.all { it.x == 7 && it.y == 21 })
        val sized = arrangeContainerChildren(original, 24, 240f, 10f,
            iconColumns = 2, itemGapDp = 10, itemSizeDp = 80)
        assertEquals(8, sized.first().w)
        assertTrue(sized.first().h >= 8)
    }

    @Test fun singleColumnIsHonoredAndFreeCanvasDoesNotShrinkToNumberOfItems() {
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 1,
            containerComponents = listOf(ComponentItem(type = ComponentType.APP_ICON)))
        assertEquals(1, containerPopupColumns(folder))
        assertEquals(360f, containerPopupWidthDp(folder.copy(containerLayout = ContainerLayoutType.FREE), 500f), 0f)
    }
}
