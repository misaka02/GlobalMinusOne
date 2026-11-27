package com.example.globalminusone.regression.v2432

import com.example.globalminusone.data.*
import com.example.globalminusone.data.panels.PanelsBackupImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.policy.GRID_SPAN_FULL_ROW
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.policy.containerIconSizeDp
import com.example.globalminusone.data.policy.isContainerGridIcon

class PanelsGridImportTest {
    private fun genuine() = PanelsBackupImporter.parse(requireNotNull(
        javaClass.getResourceAsStream("/panels/synthetic-panels-sample.bkp")))
    private fun flatten(items: List<ComponentItem>): List<ComponentItem> =
        items.flatMap { listOf(it) + flatten(it.containerComponents) }
    private fun content(items: List<ComponentItem>): List<String> =
        flatten(items).map { "${it.type}|${it.title}|${it.packageName}|${it.intentUri}|${it.iconUri}" }

    @Test fun allPageImportRoutesKeepTheSameSourceOrderColumnsIconsAndItems() {
        val archive = genuine()
        archive.pages.forEach { choice ->
            val page = archive.convertPage(choice.id)
            val container = archive.convertPageAsContainer(choice.id)
            val folder = archive.convertPageAsContainer(choice.id, asFolder = true)
            assertEquals(PageLayoutType.GRID, page.layoutType)
            assertEquals(choice.columns, page.gridColumnCount)
            assertFalse(page.gridFillGaps)
            assertEquals(choice.columns, container.containerColumns)
            assertEquals(choice.columns, folder.containerColumns)
            assertEquals(ContainerLayoutType.GRID, container.containerLayout)
            assertEquals(ContainerLayoutType.GRID, folder.containerLayout)
            assertEquals(page.gridIconSizeDp, container.containerIconSizeDp)
            assertEquals(page.gridIconSizeDp, folder.containerIconSizeDp)
            assertEquals(page.gridItemGapDp, container.containerItemGapDp)
            assertEquals(page.gridItemGapDp, folder.containerItemGapDp)
            assertEquals(content(page.components), content(container.containerComponents))
            assertEquals(content(page.components), content(folder.containerComponents))
            assertEquals(choice.itemCount + if (choice.isAppDrawer) 1 else 0,
                flatten(page.components).size)
            // GRID stores reading order/spans; no synthetic FREE coordinates survive.
            assertTrue(page.components.all { it.x == 0 && it.y == 0 })
        }
    }

    @Test fun screenshotReferenceKeepsRightPageMirrorAndFolderPositionOrder() {
        val archive = genuine()
        val page = archive.convertPage(2)
        assertEquals(listOf("DemoApp01", "DemoApp02", "DemoApp03", "DemoApp04"), page.components.take(4).map { it.title })
        val nested = flatten(page.components).single {
            it.type == ComponentType.FOLDER && it.containerComponents.size == 25
        }
        val standalone = archive.convertFolder(409)
        val expected = listOf("SampleApp01", "SampleApp02", "SampleApp03", "SampleApp04", "SampleApp05")
        assertEquals(expected, standalone.containerComponents.take(5).map { it.title })
        assertEquals(content(standalone.containerComponents), content(nested.containerComponents))
        assertEquals(5, standalone.containerColumns)
        assertEquals(38, standalone.containerIconSizeDp)
        assertEquals(38, page.gridIconSizeDp)
        assertEquals(48, archive.convertPage(6).gridIconSizeDp)
    }

    @Test fun mixedSourceTypesHaveDeliberateUsableSpansInEveryScope() {
        val archive = genuine()
        val all = archive.pages.flatMap { flatten(archive.convertPage(it.id).components) }
        assertTrue(all.filter { isContainerGridIcon(it.type) }.all { it.gridSpanX == 1 && it.w == 1 })
        assertTrue(all.filter { it.type == ComponentType.WEBSITE_LINK }.all {
            it.gridSpanX == 1 && it.gridSpanY == 1 && it.w == 1
        })
        assertTrue(all.filter { it.type in setOf(ComponentType.BRIGHTNESS_BAR, ComponentType.TEXT_LABEL) }.all {
            it.gridSpanX == GRID_SPAN_FULL_ROW && it.gridSpanY == 1 && it.w == 24
        })
        assertTrue(all.filter { it.type == ComponentType.APP_WIDGET }.all {
            it.gridSpanX == GRID_SPAN_FULL_ROW && it.gridSpanY == 2 && it.h == 14 && it.widgetId == -1
        })
        assertEquals(264, all.size)
        assertEquals(all.size, all.map { it.id }.distinct().size)
    }

    @Test fun twelveColumnSourceRemainsTwelveOnPageAndReportsContainerLimit() {
        val archive = synthetic(columns = 12, iconScale = "0.8")
        val page = archive.convertPage(1)
        assertEquals(12, page.gridColumnCount)
        assertEquals(12, page.components.size)
        assertEquals((0..11).map { "item$it" }, page.components.map { it.title })
        listOf(false, true).forEach { asFolder ->
            val result = archive.convertPageAsContainer(1, asFolder)
            assertEquals(8, result.containerColumns)
            assertEquals(12, result.containerComponents.size)
        }
        assertTrue(archive.warnings.any { it.contains("最多 8 列") })
        assertEquals(38, page.gridIconSizeDp)
    }

    @Test fun invalidAndMissingIconScalesUseSafeCompatibleSizes() {
        assertEquals(48, synthetic(4, "0").convertPage(1).gridIconSizeDp)
        assertEquals(48, synthetic(4, "-1").convertPage(1).gridIconSizeDp)
        assertEquals(160, synthetic(4, "100").convertPage(1).gridIconSizeDp)
        assertEquals(16, synthetic(4, "0.01").convertPage(1).gridIconSizeDp)
    }

    private fun synthetic(columns: Int, iconScale: String): com.example.globalminusone.data.panels.PanelsImportArchive {
        val items = (0 until columns).joinToString(",") {
            """{"id":$it,"type":2,"panelId":1,"column":$it,"row":0,"label":"item$it"}"""
        }
        val files = mapOf(
            "items_table.json" to "[$items]",
            "panels_table.json" to """[{"id":1,"setId":1,"type":2,"counterSpanCount":$columns}]""",
            "sets_table.json" to """[{"id":1,"screenId":1,"side":0}]""",
            "screens_table.json" to """[{"id":1,"iconSize":$iconScale,"spacing":2}]"""
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        } }
        return PanelsBackupImporter.parseBytes(out.toByteArray())
    }
}
