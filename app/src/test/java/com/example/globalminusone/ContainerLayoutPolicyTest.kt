package com.example.globalminusone

import com.example.globalminusone.data.*
import org.junit.Assert.*
import org.junit.Test

class ContainerLayoutPolicyTest {
    @Test fun blankFolderDoesNotReserveAnInvisibleGridLabel() {
        val folder = ComponentItem(type = ComponentType.FOLDER, title = "", showTitle = false,
            w = 8, h = 1, autoHeight = true)
        val margins = folder.marginTopDp + folder.marginBottomDp
        assertEquals(62f + margins, componentDisplayHeightDp(folder, 80f, 32), .01f)
        assertEquals(82f + margins, componentDisplayHeightDp(folder.copy(title = "文件夹"), 80f, 32), .01f)
    }

    @Test fun newAutomaticTextFitsHeaderBodyAndScaledFirstLineAtSmallGridSteps() {
        // API31 70%-width panel: the old five-row slot is only 31.25dp, less than
        // even the 30dp header plus wrapper padding, so the first body line disappeared.
        val text = ComponentItem(type = ComponentType.TEXT_LABEL, title = "文字", textContent = "自定义文字",
            showTitle = true, w = 32, h = 5, autoHeight = true)
        assertTrue(componentDisplayHeightDp(text, 200f, 32) >= 80f)
        assertTrue(componentDisplayHeightDp(text, 200f, 32, fontScale = 2f) >= 114f)
        assertEquals(31.25f, componentDisplayHeightDp(text.copy(autoHeight = false), 200f, 32, fontScale = 2f), .01f)
        assertEquals(5, text.h)
    }

    @Test fun automaticTextBudgetsExplicitLinesButCapsLongContent() {
        val text = ComponentItem(type = ComponentType.TEXT_LABEL, title = "文字", textContent = "一\n二\n三",
            showTitle = true, w = 32, h = 5, autoHeight = true)
        assertTrue(componentDisplayHeightDp(text, 200f, 32) >= 116f)
        val long = text.copy(textContent = "一\n".repeat(10000))
        assertTrue(componentDisplayHeightDp(long, 200f, 32) <= 542f)
        assertEquals(31.25f, componentDisplayHeightDp(long.copy(autoHeight = false), 200f, 32), .01f)
    }

    @Test fun deeplyPositionedDrawerDoesNotExpandAutoContainerViewport() {
        val drawer = ComponentItem(type = ComponentType.APP_DRAWER_CARD, y = 1000, h = 10000)
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, title = "Container", w = 32,
            containerComponents = listOf(drawer))
        val height = componentDisplayHeightDp(parent, 320f, 32)
        assertTrue(height <= CONTAINER_VIEWPORT_MAX_DP + 30f + 16f)
        assertEquals(10000, drawer.h); assertEquals(1000, drawer.y)
        assertEquals(CONTAINER_VIEWPORT_MAX_DP, componentDisplayHeightDp(drawer, 320f, 32), .01f)
        val manual = parent.copy(autoHeight = false, h = 12)
        assertEquals(120f, componentDisplayHeightDp(manual, 320f, 32), .01f)
    }

    @Test fun autoHeightAccountsForNestedChildPositionHeaderAndPadding() {
        val child = ComponentItem(id = "note", type = ComponentType.NOTE_CARD, y = 4, w = 16, h = 10)
        val container = ComponentItem(type = ComponentType.CONTAINER_BOX, showTitle = true, containerComponents = listOf(child))
        val height = componentDisplayHeightDp(container, 320f, 32)
        assertTrue(height > (4 + 10) * 8f)
        assertTrue(height > componentDisplayHeightDp(container.copy(showTitle = false), 320f, 32))
        val manual = container.copy(autoHeight = false, h = 6)
        assertEquals(60f, componentDisplayHeightDp(manual, 320f, 32), .01f)
    }
    @Test fun lookupAndPathsRemainPreciseForDeepMixedTypeTrees() {
        val note = ComponentItem(id = "note", type = ComponentType.NOTE_CARD)
        val nested = ComponentItem(id = "inner", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(note))
        val roots = listOf(ComponentItem(id = "outer", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(nested)))
        assertEquals(note, roots.findComponent("note"))
        assertEquals(listOf("outer", "inner", "note"), roots.componentPath("note"))
        assertNull(roots.findComponent("missing"))
        assertEquals(3, roots.flattenComponents().size)
    }
    @Test fun jsonNestingEnvelopeIgnoresBracesInEscapedStrings() {
        checkConfigurationJsonEnvelope("{\"text\":\"" + "[".repeat(200) + "\"}")
        var rejected = false
        try { checkConfigurationJsonEnvelope("[".repeat(129) + "]".repeat(129)) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
    @Test fun automaticAppHeightFitsIconAndLabelAtSmallLocalGridSteps() {
        val app = ComponentItem(type = ComponentType.APP_ICON, title = "", w = 8, h = 7)
        assertTrue(app.autoHeight)
        val automatic = componentDisplayHeightDp(app, 32f, 32)
        assertTrue(automatic >= 36f + 14f + 10f + 16f)
        assertEquals(28f, componentDisplayHeightDp(app.copy(autoHeight = false), 32f, 32), .01f)
        // Body name is still measured when empty, and does not depend on a visible Text semantics node.
        assertEquals(automatic, componentDisplayHeightDp(app.copy(title = "Clock"), 32f, 32), .01f)
    }
    @Test fun shortcutSubtitleHeaderAndLargeFontAreIncludedWithoutChangingSavedHeight() {
        val shortcut = ComponentItem(type = ComponentType.CUSTOM_SHORTCUT, title = "Action", subtitle = "Details", showTitle = true, w = 8, h = 7)
        val normal = componentDisplayHeightDp(shortcut, 32f, 32, fontScale = 1f)
        val large = componentDisplayHeightDp(shortcut, 32f, 32, fontScale = 1.8f)
        assertTrue(large > normal)
        assertTrue(normal > componentDisplayHeightDp(shortcut.copy(subtitle = "", showTitle = false), 32f, 32))
        assertEquals(7, shortcut.h)
        assertEquals(28f, componentDisplayHeightDp(shortcut.copy(autoHeight = false), 32f, 32, fontScale = 2f), .01f)
    }
    @Test fun parentBudgetsTheSameRoundedChildRowsAsNestedSlots() {
        val child = ComponentItem(type = ComponentType.APP_ICON, w = 8, h = 7, y = 3)
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, title = "Parent", showTitle = true, w = 32,
            containerComponents = listOf(child))
        val width = 160f
        // Wrapper margins4 + wrapper padding16 + container padding4 give nested width136, local step4.25.
        val localStep = (width - 24f) / 32f
        val childRows = componentDisplayRows(child, 8 * localStep, localStep, 32, fontScale = 1.4f)
        val header = kotlin.math.max(24f, 20f * 1.4f) + 6f
        val expected = (child.y + childRows) * localStep + 4f + header + 16f
        assertEquals(expected, componentDisplayHeightDp(parent, width, 32, fontScale = 1.4f), .01f)
        assertEquals(7, child.h)
    }

    @Test fun nextPlacementUsesAutomaticDisplayFootprintInsteadOfSavedSevenRows() {
        val app = ComponentItem(id = "app", type = ComponentType.APP_ICON, w = 8, h = 7)
        val container = ComponentItem(id = "container", type = ComponentType.CONTAINER_BOX, w = 32, containerComponents = listOf(app))
        val page = PageConfig(name = "Page", components = listOf(container))
        val localStep = (160f - 24f) / 32f
        val renderedRows = componentDisplayRows(app, app.w * localStep, localStep, 32, fontScale = 1.6f)
        val next = nextComponentPlacementY(page, container.id, 32, 160f, fontScale = 1.6f)
        assertEquals(app.y + renderedRows + 1, next)
        assertTrue(next > app.y + app.h + 1)
        assertEquals(7, app.h)
    }

}
