package com.example.globalminusone.regression.v2432

import com.example.globalminusone.MemoryPreferences
import com.example.globalminusone.data.*
import com.example.globalminusone.data.backup.NativeBackupCodec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.GRID_SPAN_FULL_ROW
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.MAX_GRID_GAP_DP
import com.example.globalminusone.data.MIN_GRID_GAP_DP
import com.example.globalminusone.data.MinusOneRepository
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.ROW_GAP_INHERIT
import com.example.globalminusone.data.policy.arrangeContainerChildren
import com.example.globalminusone.data.policy.arrangePageGridComponents
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.derivePageGridMetrics
import com.example.globalminusone.data.policy.fitPageGridColumns
import com.example.globalminusone.data.policy.resizedPageGridSpan

class PageGridFittingTest {
    @Test fun fixedColumnCountSurvivesIconAndSpacingChangesWithoutOverflow() {
        for (width in listOf(180f, 247f, 336f, 640f)) for (cols in 1..12) {
            val metrics = derivePageGridMetrics(width, itemGapDp = 48, columnCount = cols, iconSizeDp = 192)
            assertEquals(cols, metrics.columns)
            assertTrue(metrics.cellWidthDp > 0)
            assertEquals(width, metrics.cellWidthDp * cols + metrics.itemGapDp * (cols - 1), .01f)
            assertTrue(metrics.iconRenderSizeDp <= maxOf(1, (metrics.cellWidthDp - 4).toInt()))
        }
    }

    @Test fun choosingColumnsGeneratesWidthDependentRealIconAndGapValues() {
        val narrow = fitPageGridColumns(247f, 5)
        val wide = fitPageGridColumns(640f, 5)
        assertEquals(5, narrow.columnCount)
        assertTrue(wide.iconSizeDp > narrow.iconSizeDp)
        assertTrue(wide.itemGapDp > narrow.itemGapDp)
        for (style in listOf(narrow, wide)) {
            val width = if (style == narrow) 247f else 640f
            val actual = derivePageGridMetrics(width, style.itemSizeDp, style.itemGapDp,
                columnCount = style.columnCount, iconSizeDp = style.iconSizeDp)
            assertEquals(style.iconSizeDp, actual.iconRenderSizeDp)
        }
    }

    @Test fun automaticModeRetainsLegacyConfigurationsAndRespondsToActualIconInput() {
        assertEquals(4, derivePageGridMetrics(268f, 58, 8).columns)
        val small = derivePageGridMetrics(268f, iconSizeDp = 24)
        val large = derivePageGridMetrics(268f, iconSizeDp = 70)
        assertTrue(small.columns > large.columns)
        assertEquals(70, large.iconRenderSizeDp)
    }

    @Test fun importedReadingOrderDoesNotBackfillAheadOfFullWidthCards() {
        val cards = listOf(
            ComponentItem(id = "one", type = ComponentType.APP_ICON, gridSpanX = 1, gridSpanY = 1),
            ComponentItem(id = "two", type = ComponentType.APP_ICON, gridSpanX = 1, gridSpanY = 1),
            ComponentItem(id = "bar", type = ComponentType.BRIGHTNESS_BAR, gridSpanX = GRID_SPAN_FULL_ROW, gridSpanY = 1),
            ComponentItem(id = "site", type = ComponentType.WEBSITE_LINK, gridSpanX = 1, gridSpanY = 1))
        val ordered = arrangePageGridComponents(cards, 320f, columnCount = 4, fillGaps = false).placements
        assertEquals(cards.map { it.id }, ordered.map { it.id })
        assertTrue(ordered[3].row > ordered[2].row)
        val filled = arrangePageGridComponents(cards, 320f, columnCount = 4).placements
        assertEquals(0, filled[3].row) // Existing user grids still support deliberate gap filling.
    }

    @Test fun numericValuesAndExplicitColumnsSurviveRepositoryAndBackupRoundTrips() {
        val repository = MinusOneRepository(MemoryPreferences())
        repository.fitPageGridColumnCount(0, 5, 268f)
        repository.updatePageGridStyle(0, iconSizeDp = 37, itemGapDp = 9)
        repository.updatePageConfig(0) { it.copy(gridFillGaps = false, components = listOf(
            ComponentItem(type = ComponentType.CONTAINER_BOX, containerIconSizeDp = 47))) }
        val raw = repository.exportConfigCode()
        val restored = MinusOneRepository(MemoryPreferences())
        assertTrue(restored.importConfigCode(raw))
        val page = restored.state.value.pages[0]
        assertEquals(5, page.gridColumnCount)
        assertEquals(37, page.gridIconSizeDp)
        assertEquals(9, page.gridItemGapDp)
        assertFalse(page.gridFillGaps)
        assertEquals(47, page.components.single().containerIconSizeDp)
        val backup = NativeBackupCodec.json.encodeToString(restored.state.value)
        assertEquals(page, NativeBackupCodec.json.decodeFromString<GlobalMinusOneState>(backup).pages[0])
    }

    @Test fun oldStateJsonKeepsAutomaticColumnsAndLegacyContainerIconSizing() {
        val old = Json { ignoreUnknownKeys = true }.decodeFromString<GlobalMinusOneState>(
            """{"pages":[{"name":"old","components":[{"type":"CONTAINER_BOX","containerItemSizeDp":80}]}]}""")
        val page = old.pages.single()
        assertEquals(0, page.gridColumnCount)
        assertEquals(0, page.gridIconSizeDp)
        assertTrue(page.gridFillGaps)
        assertEquals(36, containerIconSizeDp(page.components.single()))
    }

    @Test fun actualContainerIconSizeControlsGridMeasurementWithoutChangingSavedChildren() {
        val original = List(5) { ComponentItem(id = "$it", type = ComponentType.APP_ICON, w = 1, h = 1) }
        val large = arrangeContainerChildren(original, 32, 360f, 10f, iconColumns = 4, iconSizeDp = 50)
        assertTrue(large.first().h * 10 >= 50 + 14 + 10)
        assertTrue(large.last().y >= large.first().h)
        assertTrue(original.all { it.w == 1 && it.h == 1 })
    }

    @Test fun invalidImportedNumericGridSettingsAreRejectedBeforeReplacingState() {
        val repo = MinusOneRepository(MemoryPreferences())
        val before = repo.state.value
        val invalid = before.copy(pages = before.pages.map { it.copy(gridColumnCount = 13) })
        assertFalse(repo.importConfigCode(NativeBackupCodec.json.encodeToString(invalid)))
        assertEquals(before, repo.state.value)
    }

    @Test fun legacyMissingRowGapInheritsEffectiveGapEvenOnNarrowPanels() {
        val old = Json.decodeFromString<PageConfig>("""{"name":"old","gridItemGapDp":48,"gridColumnCount":12}""")
        assertEquals(ROW_GAP_INHERIT, old.gridRowGapDp)
        val metrics = derivePageGridMetrics(180f, itemGapDp = old.gridItemGapDp,
            columnCount = old.gridColumnCount, rowGapDp = old.gridRowGapDp)
        assertTrue(metrics.itemGapDp < 48)
        assertEquals(metrics.itemGapDp, metrics.rowGapDp)
    }

    @Test fun explicitRowGapChangesOnlyVerticalPlacementSpansAndContentHeight() {
        val cards = listOf(
            ComponentItem(id = "tall", type = ComponentType.APP_ICON, gridSpanX = 1, gridSpanY = 2),
            ComponentItem(id = "next", type = ComponentType.APP_ICON, gridSpanX = 1, gridSpanY = 1))
        val zero = arrangePageGridComponents(cards, 240f, itemGapDp = 19, columnCount = 1, rowGapDp = 0)
        val spaced = arrangePageGridComponents(cards, 240f, itemGapDp = 19, columnCount = 1, rowGapDp = 31)
        assertEquals(0, zero.metrics.rowGapDp)
        assertEquals(19, spaced.metrics.itemGapDp)
        assertEquals(zero.metrics.cellWidthDp, spaced.metrics.cellWidthDp, 0f)
        assertEquals(zero.placements[0].widthDp, spaced.placements[0].widthDp, 0f)
        assertEquals(zero.placements[0].heightDp + 31, spaced.placements[0].heightDp, .01f)
        assertEquals(zero.placements[1].yDp + 62, spaced.placements[1].yDp, .01f)
        assertEquals(zero.contentHeightDp + 62, spaced.contentHeightDp, .01f)
        val resized = resizedPageGridSpan(1, 1, 0f,
            spaced.metrics.cellHeightDp + spaced.metrics.rowGapDp,
            spaced.metrics.cellWidthDp + spaced.metrics.itemGapDp,
            spaced.metrics.cellHeightDp + spaced.metrics.rowGapDp, 1)
        assertEquals(1 to 2, resized)
    }

    @Test fun rowGapSurvivesHorizontalChangesColumnFittingAndBackup() {
        val prefs = MemoryPreferences()
        val repo = MinusOneRepository(prefs)
        repo.updatePageGridStyle(0, rowGapDp = 0, itemGapDp = 23)
        repo.fitPageGridColumnCount(0, 5, 320f)
        assertEquals(0, repo.state.value.pages[0].gridRowGapDp)
        repo.updatePageGridStyle(0, rowGapDp = 27)
        repo.updatePageGridStyle(0, itemGapDp = 3)
        assertEquals(27, MinusOneRepository(prefs).state.value.pages[0].gridRowGapDp)
        val restored = MinusOneRepository(MemoryPreferences())
        assertTrue(restored.importConfigCode(repo.exportConfigCode()))
        assertEquals(27, restored.state.value.pages[0].gridRowGapDp)
        assertEquals(5, restored.state.value.pages[0].gridColumnCount)
        val archive = java.io.ByteArrayOutputStream()
        NativeBackupCodec.write(archive, com.example.globalminusone.data.backup.DecodedNativeBackup(
            com.example.globalminusone.data.backup.NativeBackupPayload(repo.state.value), emptyMap()))
        val decoded = NativeBackupCodec.read(java.io.ByteArrayInputStream(archive.toByteArray()))
        assertEquals(27, decoded.payload.state.pages[0].gridRowGapDp)
    }

    @Test fun invalidRowGapImportIsRejectedWithoutPublishingState() {
        for (gap in listOf(MIN_GRID_GAP_DP - 1, -998, MAX_GRID_GAP_DP + 1)) {
            val repo = MinusOneRepository(MemoryPreferences())
            val before = repo.state.value
            val invalid = before.copy(pages = before.pages.map { it.copy(gridRowGapDp = gap) })
            assertFalse(repo.importConfigCode(NativeBackupCodec.json.encodeToString(invalid)))
            assertEquals(before, repo.state.value)
        }
    }

    @Test fun negativeRowAndHorizontalGapsPersistAndRemainFinite() {
        val repo = MinusOneRepository(MemoryPreferences())
        assertTrue(repo.updatePageGridStyle(0, itemGapDp = -8, rowGapDp = -12))
        assertEquals(-8, repo.state.value.pages[0].gridItemGapDp)
        assertEquals(-12, repo.state.value.pages[0].gridRowGapDp)
        val arranged = arrangePageGridComponents(
            components = listOf(
                ComponentItem(id = "a", type = ComponentType.APP_ICON),
                ComponentItem(id = "b", type = ComponentType.APP_ICON),
                ComponentItem(id = "c", type = ComponentType.APP_ICON)
            ),
            availableWidthDp = 320f,
            itemGapDp = -8,
            rowGapDp = -12,
            columnCount = 2
        )
        assertEquals(-8, arranged.metrics.itemGapDp)
        assertEquals(-12, arranged.metrics.rowGapDp)
        assertTrue(arranged.contentHeightDp > 0f && !arranged.contentHeightDp.isNaN())
        val restored = MinusOneRepository(MemoryPreferences())
        assertTrue(restored.importConfigCode(repo.exportConfigCode()))
        assertEquals(-8, restored.state.value.pages[0].gridItemGapDp)
        assertEquals(-12, restored.state.value.pages[0].gridRowGapDp)
    }

}
