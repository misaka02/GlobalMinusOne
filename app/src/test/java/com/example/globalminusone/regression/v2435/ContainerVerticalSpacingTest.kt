package com.example.globalminusone.regression.v2435

import com.example.globalminusone.data.*
import com.example.globalminusone.data.backup.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.ContainerActionGeometry
import com.example.globalminusone.data.policy.ContainerIconVerticalSpacing
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.MIN_ICON_LABEL_GAP_DP
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.ROW_GAP_INHERIT
import com.example.globalminusone.data.policy.componentDisplayHeightDp
import com.example.globalminusone.data.policy.containerActionGeometry
import com.example.globalminusone.data.policy.containerActionGridGeometry
import com.example.globalminusone.data.policy.containerGridChildBounds
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.containerIconVerticalSpacing
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.policy.legacyContainerActionGridHeightDp
import com.example.globalminusone.data.policy.legacyContainerActionTileHeightDp
import com.example.globalminusone.data.policy.measureFreeContainerChildren

class ContainerVerticalSpacingTest {
    private val icons = List(6) { ComponentItem(id = "app-$it", type = ComponentType.APP_ICON,
        x = 7, y = 13, w = 3, h = 19, gridSpanX = 1, gridSpanY = 1, autoHeight = false) }
    private fun grid(rowGap: Int = 0, horizontalGap: Int = 8, padding: Int = 0, labelGap: Int = 2,
        children: List<ComponentItem> = icons, columns: Int = 2, stepY: Float = 10f) =
        containerGridChildBounds(children, 32, 320f, stepY, configuredHeightUnitDp = stepY.toInt(),
            iconColumns = columns, itemGapDp = horizontalGap, iconSizeDp = 48,
            rowGapDp = rowGap, itemPaddingVerticalDp = padding, iconLabelGapDp = labelGap)

    @Test fun vertical0Then4Then12ChangesExactRowPositionsWithoutChangingHorizontalPlacement() {
        val layouts = listOf(0, 4, 12).map { grid(rowGap = it) }
        assertEquals(listOf(64f, 68f, 76f), layouts.map { it[2].y })
        assertEquals(listOf(64f, 64f, 64f), layouts.map { it[0].height })
        assertEquals(1, layouts.map { it[1].x }.distinct().size)
        assertEquals(1, layouts.map { it[0].width }.distinct().size)
        layouts.forEach { assertEquals(icons, it.map { child -> child.item }) }
    }

    @Test fun horizontalGapsDoNotChangeIndependentVerticalGaps() {
        val layouts = listOf(0, 4, 12).map { grid(rowGap = 4, horizontalGap = it) }
        assertEquals(listOf(68f, 68f, 68f), layouts.map { it[2].y })
        assertEquals(listOf(0f, 4f, 12f), layouts.map { it[1].x - it[0].width })
        assertEquals(3, layouts.map { it[1].x }.distinct().size)
    }

    @Test fun importedOneRowSpanHasNoFourFineRowsFloorEvenAtLargeHeightUnits() {
        val shown = grid(stepY = 100f)
        assertTrue(shown.all { it.height == 64f }) // 48 icon + 14sp label + 2 gap; no unitY*4 floor.
        assertEquals(64f, shown[2].y, 0f)
        val spanning = icons.first().copy(gridSpanY = 2)
        val spanShown = grid(rowGap = 4, children = listOf(spanning, icons[1], icons[2]), stepY = 100f)
        assertEquals(132f, spanShown.first().height, 0f)
        assertEquals(136f, spanShown.last().y, 0f)
    }

    @Test fun paddingLabelGapFontScaleAndSubtitleAreCountedExactlyOnce() {
        val shown = grid(padding = 3, labelGap = 7)
        assertEquals(75f, shown.first().height, 0f) // 48 + 14 + 7 + 2*3.
        val subtitle = icons[0].copy(type = ComponentType.CUSTOM_SHORTCUT, subtitle = "second line")
        val scaled = containerGridChildBounds(listOf(subtitle), 32, 320f, 10f,
            fontScale = 1.5f, iconColumns = 2, iconSizeDp = 48,
            itemPaddingVerticalDp = 3, iconLabelGapDp = 7).single()
        assertEquals(100f, scaled.height, 0f) // 48 + 21 + 18 + 7 + 6.
    }

    @Test fun aTallIconOnLaterRowsDoesNotInflateEarlierOrFollowingOrdinaryRows() {
        val children = icons.toMutableList().also { it[2] = it[2].copy(type = ComponentType.CUSTOM_SHORTCUT, subtitle = "second line") }
        val shown = grid(rowGap = 4, children = children)
        assertEquals(listOf(64f, 64f, 76f, 64f, 64f, 64f), shown.map { it.height })
        assertEquals(listOf(0f, 0f, 68f, 68f, 148f, 148f), shown.map { it.y })
    }

    @Test fun folderGridBudgetIncludesOnlyItsRenderedInternalOrExternalTitle() {
        val folder = ComponentItem(type = ComponentType.FOLDER, title = "Folder", showTitle = false)
        fun height(child: ComponentItem) = grid(children = listOf(child), padding = 3, labelGap = 7).single().height
        assertEquals(85f, height(folder), 0f) // 48 + 20 + 7 + 6 padding + 4 wrapper margins.
        assertEquals(89f, height(folder.copy(showTitle = true)), 0f) // 48 + (24+7 header) + 6 + 4.
        assertEquals(58f, height(folder.copy(title = "")), 0f) // no invisible internal label/gap.
    }

    @Test fun gridFolderExternalTitleUsesTheRequestedLabelGapAndParentHeightFollowsIt() {
        val child = ComponentItem(type = ComponentType.FOLDER, title = "Folder", showTitle = true)
        val children = listOf(child, child.copy(id = "second-folder"))
        val layouts = listOf(0, 8).map { gap -> grid(rowGap = 4, children = children,
            columns = 1, padding = 3, labelGap = gap) }
        assertEquals(listOf(82f, 90f), layouts.map { it.first().height })
        assertEquals(listOf(86f, 94f), layouts.map { it.last().y })
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, containerLayout = ContainerLayoutType.GRID,
            containerColumns = 1, containerComponents = children, containerIconSizeDp = 48,
            containerRowGapDp = 4, containerItemPaddingVerticalDp = 3)
        assertEquals(listOf(188f, 204f), listOf(0, 8).map { gap -> componentDisplayHeightDp(
            parent.copy(containerIconLabelGapDp = gap), 344f, 32, configuredHeightUnitDp = 100) })
    }

    @Test fun legacyGridActionsUseActualContentAndIndependentRowGapWithoutTrailingBlankRow() {
        val actions = List(5) { QuickActionItem(id = "action-$it", title = "Action", intentUri = "test://noop") }
        val folder = ComponentItem(type = ComponentType.FOLDER, containerColumns = 2,
            containerActions = actions, containerItemGapDp = 12, containerRowGapDp = 4)
        assertEquals(58f, legacyContainerActionTileHeightDp(folder), 0f)
        assertEquals(182f, legacyContainerActionGridHeightDp(folder), 0f) // 3*58 + 2*4.
        val secondRow = containerActionGridGeometry(folder, 2, 2, 320f)
        assertEquals(62f, secondRow.y, 0f)
        assertEquals(154f, secondRow.width, 0f)
        val padded = folder.copy(containerItemPaddingVerticalDp = 3, containerIconLabelGapDp = 7)
        assertEquals(69f, legacyContainerActionTileHeightDp(padded), 0f)
        assertEquals(73f, containerActionGridGeometry(padded, 2, 2, 320f).y, 0f)
        assertEquals(67f, legacyContainerActionTileHeightDp(folder, 1.5f), 0f)
    }

    @Test fun autoContainerHeightUsesTheExactIndependentGridBottom() {
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, containerLayout = ContainerLayoutType.GRID,
            containerColumns = 2, containerComponents = icons, containerIconSizeDp = 48,
            containerItemGapDp = 12, containerRowGapDp = 4)
        // 3 * 64dp rows + 2 * 4dp row gaps + existing 20dp wrapper.
        assertEquals(220f, componentDisplayHeightDp(parent, 344f, 32, configuredHeightUnitDp = 100), 0f)
    }

    @Test fun mixedLegacyActionsAndRealChildrenShareOneResolvedRowGapBetweenGroups() {
        val parent = ComponentItem(type = ComponentType.CONTAINER_BOX, containerLayout = ContainerLayoutType.GRID,
            containerColumns = 2, containerComponents = icons.take(2), containerIconSizeDp = 48,
            containerActions = List(2) { QuickActionItem(id = "mixed-$it", title = "Action", intentUri = "test://noop") },
            containerItemGapDp = 12, containerRowGapDp = 4)
        // One 68dp action row + one 4dp group gap + one 64dp child row + 20dp wrapper.
        assertEquals(156f, componentDisplayHeightDp(parent, 344f, 32, configuredHeightUnitDp = 100), 0f)
        assertEquals(152f, componentDisplayHeightDp(parent.copy(containerRowGapDp = 0), 344f, 32, configuredHeightUnitDp = 100), 0f)
    }

    @Test fun freeGeometryAndExplicitLegacyCellHeightsRemainAuthoritative() {
        val legacy = ComponentItem(type = ComponentType.FOLDER, containerLayout = ContainerLayoutType.FREE,
            containerItemGapDp = 12, containerRowGapDp = 0, containerItemPaddingVerticalDp = 48,
            containerIconLabelGapDp = 48)
        assertEquals(88f, legacyContainerActionTileHeightDp(legacy), 0f)
        val action = QuickActionItem(id = "manual", title = "Manual", intentUri = "test://noop", xDp = 17, yDp = 28, wDp = 83, hDp = 99)
        assertEquals(ContainerActionGeometry(17f, 28f, 83f, 99f), containerActionGeometry(action, 0, 2, 320f))
        val manual = icons.first().copy(h = 19, autoHeight = false)
        assertEquals(listOf(manual), measureFreeContainerChildren(listOf(manual), 32, 320f, 10f, configuredHeightUnitDp = 10))
        val gridManual = legacy.copy(containerLayout = ContainerLayoutType.GRID, containerItemSizeDp = 120,
            containerItemPaddingVerticalDp = 0, containerIconLabelGapDp = 2)
        assertEquals(120f, legacyContainerActionTileHeightDp(gridManual), 0f)
        assertEquals(96f, containerGridChildBounds(icons, 32, 320f, 10f, iconColumns = 2,
            itemSizeDp = 96, iconSizeDp = 48, rowGapDp = 4).first().height, 0f)
    }

    @Test fun oldJsonInheritsHorizontalGapWhileNewIndependentZeroAndSpacingRoundTrip() {
        val json = NativeBackupCodec.json
        val old = json.decodeFromString<GlobalMinusOneState>(
            """{"pages":[{"name":"Old","components":[{"type":"FOLDER","containerItemGapDp":9}]}],"profiles":[]}""")
        val oldFolder = old.pages.single().components.single()
        assertEquals(ROW_GAP_INHERIT, oldFolder.containerRowGapDp)
        assertEquals(9, containerRowGapDp(oldFolder))
        assertEquals(ContainerIconVerticalSpacing(0, 2), containerIconVerticalSpacing(oldFolder))
        val updated = oldFolder.copy(containerRowGapDp = -6, containerItemPaddingVerticalDp = 3,
            containerIconLabelGapDp = -4, containerComponents = listOf(oldFolder.copy(id = "nested", containerRowGapDp = 12)))
        val state = old.copy(pages = listOf(old.pages.single().copy(components = listOf(updated))))
        val output = ByteArrayOutputStream()
        NativeBackupCodec.write(output, DecodedNativeBackup(NativeBackupPayload(state), emptyMap()))
        val restored = NativeBackupCodec.read(ByteArrayInputStream(output.toByteArray())).payload.state
        assertEquals(state, restored)
        assertEquals(-6, containerRowGapDp(restored.pages.single().components.single()))
        assertEquals(-4, restored.pages.single().components.single().containerIconLabelGapDp)
        assertEquals(12, containerRowGapDp(restored.pages.single().components.single().containerComponents.single()))
        assertEquals(state, json.decodeFromString<GlobalMinusOneState>(json.encodeToString(state)))
    }

    @Test fun invalidSpacingRangesAreRejectedByNativeBackupValidation() {
        val base = ComponentItem(type = ComponentType.FOLDER)
        val invalid = listOf(
            base.copy(containerRowGapDp = MIN_GRID_GAP_DP - 1),
            base.copy(containerRowGapDp = -998),
            base.copy(containerRowGapDp = MAX_GRID_GAP_DP + 1),
            base.copy(containerItemPaddingVerticalDp = -1),
            base.copy(containerItemPaddingVerticalDp = 49),
            base.copy(containerIconLabelGapDp = MIN_ICON_LABEL_GAP_DP - 1),
            base.copy(containerIconLabelGapDp = 49)
        )
        invalid.forEach { child ->
            val state = GlobalMinusOneState(pages = listOf(PageConfig(name = "Invalid", components = listOf(child))), profiles = emptyList())
            assertTrue(runCatching { NativeBackupCodec.validateState(state) }.isFailure)
            assertTrue(runCatching { NativeBackupCodec.readCompatible(
                NativeBackupCodec.json.encodeToString(state).byteInputStream(), emptyMap(), emptyMap()) }.isFailure)
        }
    }
}
