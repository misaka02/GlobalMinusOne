package com.example.globalminusone

import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.panels.PanelsBackupImporter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PanelsBackupImporterTest {
    private fun genuine() = PanelsBackupImporter.parse(requireNotNull(javaClass.getResourceAsStream("/panels/synthetic-panels-sample.bkp")))
    private fun flatten(items: List<ComponentItem>): List<ComponentItem> = items.flatMap { listOf(it) + flatten(it.containerComponents) }

    @Test fun genuineArchivePreservesEveryItemAndRemapsEveryId() {
        val archive = genuine()
        assertEquals(5, archive.pages.size)
        assertEquals(11, archive.folders.size)
        assertEquals(262, archive.itemCount)
        assertEquals(6, archive.subitemCount)
        assertEquals(21, archive.iconAssets.size)
        assertEquals(mapOf(11 to 119, 6 to 0, 1 to 0, 2 to 103, 13 to 40), archive.pages.associate { it.id to it.itemCount })
        val all = archive.pages.flatMap { flatten(archive.convertPage(it.id).components) }
        // Two synthesized app drawers; none of the 262 source items is discarded.
        assertEquals(264, all.size)
        assertEquals(241, all.count { it.type == ComponentType.APP_ICON })
        assertEquals(11, all.count { it.type == ComponentType.FOLDER })
        assertEquals(3, all.count { it.type == ComponentType.CUSTOM_SHORTCUT })
        assertEquals(2, all.count { it.type == ComponentType.BRIGHTNESS_BAR })
        assertEquals(2, all.count { it.type == ComponentType.WEBSITE_LINK })
        assertEquals(2, all.count { it.type == ComponentType.APP_WIDGET })
        assertEquals(1, all.count { it.type == ComponentType.TEXT_LABEL })
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val second = archive.pages.flatMap { flatten(archive.convertPage(it.id).components) }
        assertTrue(all.map { it.id }.toSet().intersect(second.map { it.id }.toSet()).isEmpty())
        assertTrue(all.filter { it.type == ComponentType.APP_WIDGET }.all { it.widgetId == -1 && it.intentUri.isEmpty() && it.subtitle.contains("重新绑定") })
    }

    @Test fun everyFolderConvertsIndependentlyWithAllDescendantsAndFiveColumnReference() {
        val archive = genuine()
        archive.folders.forEach { choice ->
            val converted = archive.convertFolder(choice.id)
            assertEquals(choice.itemCount, flatten(converted.containerComponents).size)
            assertEquals(choice.columns, converted.containerColumns)
        }
        val folder = archive.convertFolder(409)
        assertEquals(5, folder.containerColumns)
        assertEquals(25, folder.containerComponents.size)
        assertEquals(listOf("SampleApp01", "SampleApp02", "SampleApp03", "SampleApp04", "SampleApp05"), folder.containerComponents.take(5).map { it.title })
        val page = archive.convertPage(2)
        assertEquals(listOf("DemoApp01", "DemoApp02", "DemoApp03", "DemoApp04"), page.components.take(4).map { it.title })
    }

    @Test fun assetsAreOnlyMappedThroughExplicitSafeKeyCallback() {
        val archive = genuine()
        val callbacks = mutableListOf<String>()
        val all = archive.pages.flatMap { flatten(archive.convertPage(it.id, iconUriFor = { key ->
            assertTrue(key in archive.iconAssets)
            callbacks += key
            "private://test/${key.substringAfterLast('/')}"
        }).components) }
        assertTrue(callbacks.isNotEmpty())
        assertTrue(all.filter { it.iconUri.isNotEmpty() }.all { it.iconUri.startsWith("private://test/") })
        assertTrue(genuine().convertFolder(409).iconUri.isEmpty())
    }

    @Test fun importedGeometryFitsEverySupportedTargetGrid() {
        val archive = genuine()
        listOf(8, 24, 32, 64).forEach { columns ->
            archive.pages.forEach { choice ->
                val page = archive.convertPage(choice.id, gridColumns = columns)
                assertTrue(flatten(page.components).all { it.x >= 0 && it.w > 0 && it.x + it.w <= columns })
                assertTrue(page.components.filter { it.type == ComponentType.APP_DRAWER_CARD }.all { it.w == columns })
            }
            archive.folders.forEach { assertTrue(flatten(listOf(archive.convertFolder(it.id, gridColumns = columns))).all { it.x + it.w <= columns }) }
        }
    }

    @Test fun rejectsMissingTablesDuplicateIdsAndBrokenReferences() {
        expectReject(mapOf("items_table.json" to "[]"))
        expectReject(base("[{\"id\":1,\"type\":2,\"panelId\":1},{\"id\":1,\"type\":2,\"panelId\":1}]"))
        expectReject(base("[{\"id\":1,\"type\":2,\"panelId\":99}]"))
        expectReject(base("[{\"id\":1,\"type\":2,\"panelId\":1,\"parentFolderId\":99}]"))
    }

    @Test fun rejectsCyclesAndInvalidJsonNumbers() {
        expectReject(base("[{\"id\":1,\"type\":4,\"panelId\":1,\"parentFolderId\":2},{\"id\":2,\"type\":4,\"panelId\":1,\"parentFolderId\":1}]"))
        expectReject(base("[{\"id\":1,\"type\":2,\"panelId\":1,\"row\":1e9999}]"))
        expectReject(base("[broken"))
        expectReject(base("[null]"))
        expectReject(base() + ("items_table.json" to "[".repeat(10000) + "]".repeat(10000)))
    }

    @Test fun rejectsUnsafePathsAndExcessiveInflation() {
        listOf("../x.png", "/etc/x.png", "C:/x.png", "original/../../x.png", "original\\x.png").forEach { name ->
            expectReject(base() + (name to "x"))
        }
        expectReject(base() + ("items_table.json" to " ".repeat(4 * 1024 * 1024 + 1)))
        expectReject(base() + (1..257).associate { "extra_$it.json" to "{}" })
    }

    @Test fun unknownTypesAndUnsafeWebsitesRemainVisibleWithoutExecutablePayload() {
        val result = parse(base("[{\"id\":1,\"type\":999,\"panelId\":1,\"label\":\"Unknown\",\"intent\":\"dangerous\"}," +
            "{\"id\":2,\"type\":12,\"panelId\":1,\"label\":\"Bad URL\",\"intent\":\"javascript:alert(1)\"}]"))
        val items = result.convertPage(1).components
        assertEquals(2, items.size)
        assertTrue(items.all { it.type == ComponentType.TEXT_LABEL && it.intentUri.isEmpty() && it.textContent.isNotBlank() })
        assertTrue(result.warnings.any { it.contains("2 项暂不支持") })
    }

    @Test fun unreferencedImagesNeverReachAssetPlanAndReferencedBadImagesAreRejected() {
        assertTrue(parse(base() + ("/original/not_referenced.png" to "not an image")).iconAssets.isEmpty())
        expectReject(base("[{\"id\":1,\"type\":2,\"panelId\":1,\"iconName\":\"bad\"}]") + ("/original/bad.png" to "not an image"))
    }

    @Test fun safeUnusedAttachmentsAreSkippedWithWarning() {
        val result = parse(base() + ("notes/readme.txt" to "x".repeat(5 * 1024 * 1024)) +
            ("unrelated.json" to "[broken"))
        assertEquals(1, result.pages.size)
        assertTrue(result.warnings.any { it.contains("2 个安全但不支持") })
    }

    @Test fun malformedUtf8InRequiredTableIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            base().forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(if (name == "items_table.json") byteArrayOf(0x5b, 0xc3.toByte(), 0x28, 0x5d)
                    else text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        assertThrows(java.nio.charset.MalformedInputException::class.java) {
            PanelsBackupImporter.parseBytes(out.toByteArray())
        }
    }

    private fun base(items: String = "[]") = mapOf(
        "items_table.json" to items,
        "panels_table.json" to "[{\"id\":1,\"setId\":1,\"type\":2,\"counterSpanCount\":4}]",
        "sets_table.json" to "[{\"id\":1,\"screenId\":1}]",
        "screens_table.json" to "[{\"id\":1}]")
    private fun parse(files: Map<String, String>) = PanelsBackupImporter.parse(ByteArrayInputStream(zip(files)))
    private fun expectReject(files: Map<String, String>) {
        try { parse(files); fail("Expected invalid archive rejection") } catch (expected: Exception) { /* expected */ }
    }
    private fun zip(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> files.forEach { (name, text) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        } }
        return out.toByteArray()
    }
}
