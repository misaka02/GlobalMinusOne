package com.example.globalminusone.data.panels

import com.example.globalminusone.data.*
import kotlinx.serialization.json.*
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class PanelsPageChoice(val id: Int, val title: String, val setId: Int, val columns: Int,
    val itemCount: Int, val isAppDrawer: Boolean)
data class PanelsFolderChoice(val id: Int, val title: String, val panelId: Int, val columns: Int,
    val itemCount: Int)

enum class PanelsPageImportMode(val label: String) {
    STANDALONE_PAGE("独立页面"),
    GRID_CONTAINER("网格容器"),
    GRID_FOLDER("网格文件夹")
}

/** Parsed in memory only. Callers choose an append destination and persist assets after preview. */
class PanelsImportArchive internal constructor(
    private val tables: Map<String, JsonElement>,
    val iconAssets: Map<String, ByteArray>,
    val warnings: List<String>
) {
    private val items = tables.rows("items_table.json")
    private val panels = tables.rows("panels_table.json")
    private val sets = tables.rows("sets_table.json").associateBy { it.number("id") }
    private val screens = tables.rows("screens_table.json").associateBy { it.number("id") }
    private val byId = items.associateBy { it.number("id") }
    private val children = items.groupBy { it.number("parentFolderId", -1) }
    val itemCount: Int get() = items.size
    val subitemCount: Int get() = tables.rows("subitems_table.json").size
    val pages: List<PanelsPageChoice> = panels.sortedWith(compareBy({ it.number("setId") }, { it.number("index") }))
        .map { p -> PanelsPageChoice(p.number("id"), p.string("label").ifBlank { "Panels 页面" },
            p.number("setId"), p.number("counterSpanCount", 4).coerceIn(1, 12),
            items.count { it.number("panelId") == p.number("id") }, p.number("type") == 1) }
    val folders: List<PanelsFolderChoice> = items.filter { it.number("type") == 4 }.map {
        PanelsFolderChoice(it.number("id"), it.string("label").ifBlank { "文件夹" },
            it.number("panelId"), folderColumns(it.number("id")), descendants(it.number("id")))
    }

    fun convertPage(panelId: Int, iconUriFor: (String) -> String = { "" }, gridColumns: Int = 24): PageConfig {
        require(gridColumns in 8..64) { "目标画布列数无效" }
        val choice = pages.firstOrNull { it.id == panelId } ?: error("找不到 Panels 页面")
        val source = panels.first { it.number("id") == panelId }
        val sc = screenForPanel(source)
        val converted = pageItems(source, choice, iconUriFor, gridColumns)
        val theme = tables.rows("themes_table.json").firstOrNull {
            it.number("panelId") == panelId && !it.boolean("nightTheme")
        }
        val alpha = theme?.number("bgAlpha", 255)?.coerceIn(0, 255) ?: 255
        val color = theme?.get("colorPrimary")?.jsonPrimitive?.longOrNull
        return PageConfig(name = "Panels · ${choice.title}", layoutType = PageLayoutType.GRID,
            gridColumnCount = choice.columns, gridIconSizeDp = iconSizeDp(sc), gridFillGaps = false,
            gridItemGapDp = spacingDp(sc), components = converted,
            bgColorHex = color?.let { (it and 0x00ffffffL) or (alpha.toLong() shl 24) })
    }

    fun convertPageAsContainer(
        panelId: Int,
        asFolder: Boolean = false,
        iconUriFor: (String) -> String = { "" },
        gridColumns: Int = 24
    ): ComponentItem {
        require(gridColumns in 8..64) { "目标画布列数无效" }
        val choice = pages.firstOrNull { it.id == panelId } ?: error("找不到 Panels 页面")
        val source = panels.first { it.number("id") == panelId }
        val converted = pageItems(source, choice, iconUriFor, gridColumns)
        val sc = screenForPanel(source)
        val gapDp = spacingDp(sc)
        val cols = choice.columns.coerceAtMost(8)
        return if (asFolder) {
            ComponentItem(
                type = ComponentType.FOLDER,
                title = choice.title,
                showTitle = false,
                containerComponents = converted,
                containerColumns = cols,
                containerLayout = ContainerLayoutType.GRID,
                containerItemSizeDp = 0,
                containerIconSizeDp = iconSizeDp(sc),
                containerItemGapDp = gapDp,
                w = 8.coerceAtMost(gridColumns),
                h = 8,
                autoHeight = true
            )
        } else {
            ComponentItem(
                type = ComponentType.CONTAINER_BOX,
                title = "Panels · ${choice.title}",
                showTitle = true,
                containerComponents = converted,
                containerColumns = cols,
                containerLayout = ContainerLayoutType.GRID,
                containerCompact = false,
                containerItemSizeDp = 0,
                containerIconSizeDp = iconSizeDp(sc),
                containerItemGapDp = gapDp,
                w = gridColumns,
                h = 12,
                autoHeight = true
            )
        }
    }

    fun convertFolder(itemId: Int, iconUriFor: (String) -> String = { "" }, gridColumns: Int = 24): ComponentItem {
        require(gridColumns in 8..64) { "目标画布列数无效" }
        val source = byId[itemId]?.takeIf { it.number("type") == 4 } ?: error("找不到 Panels 文件夹")
        return convert(source, iconUriFor, gridColumns).copy(w = 8.coerceAtMost(gridColumns))
    }

    private fun descendants(id: Int): Int = children[id].orEmpty().sumOf { 1 + descendants(it.number("id")) }
    private fun folderColumns(id: Int): Int = (children[id].orEmpty().maxOfOrNull {
        it.number("column", -1) + 1
    } ?: 0).takeIf { it > 0 }?.coerceIn(1, 12) ?: 5
    private fun ordered(rows: List<JsonObject>): List<JsonObject> = rows.sortedWith(compareBy(
        { it.number("position", 0) }, { it.number("sortIndex", 0) }, { it.number("id") }))
    private fun screen(item: JsonObject): JsonObject? {
        return screenForPanel(panels.first { it.number("id") == item.number("panelId") })
    }
    private fun screenForPanel(panel: JsonObject): JsonObject? =
        screens[sets[panel.number("setId")]?.number("screenId")]
    private fun spacingDp(screen: JsonObject?): Int = screen?.number("spacing", 2)?.coerceIn(0, 32) ?: 2

    // Panels stores a multiplier of its 48dp launcher icon, not a grid-cell width.
    private fun iconSizeDp(screen: JsonObject?): Int {
        val scale = (screen?.get("iconSize") as? JsonPrimitive)?.floatOrNull
            ?.takeIf { it.isFinite() && it > 0f } ?: 1f
        return kotlin.math.round(48f * scale).toInt().coerceIn(16, 160)
    }

    private fun pageItems(source: JsonObject, choice: PanelsPageChoice,
        iconUriFor: (String) -> String, gridColumns: Int): List<ComponentItem> {
        val reverse = sets[source.number("setId")]?.number("side") == 1
        // Page coordinates are column-major; each counterSpanCount block is a new page.
        // Right-side sets mirror each block. Folder rows use position instead: their saved
        // row/column values can be stale or duplicated after sorting (genuine folder 409).
        val roots = items.filter { it.number("panelId") == choice.id && it.number("parentFolderId", -1) == -1 }
            .sortedWith(compareBy({ it.number("column", 0).coerceAtLeast(0) / choice.columns },
                { it.number("row", 0).coerceAtLeast(0) },
                { val col = it.number("column", 0).coerceAtLeast(0) % choice.columns; if (reverse) -col else col },
                { it.number("position") }, { it.number("id") }))
        val blockRowOffsets = mutableMapOf<Int, Int>()
        var cumulativeRows = 0
        roots.groupBy { it.number("column", 0).coerceAtLeast(0) / choice.columns.coerceAtLeast(1) }
            .toSortedMap()
            .forEach { (blockIdx, blockItems) ->
                blockRowOffsets[blockIdx] = cumulativeRows
                val maxRowInBlock = blockItems.maxOfOrNull { it.number("row", 0).coerceAtLeast(0) } ?: 0
                cumulativeRows += maxRowInBlock + 1
            }
        val converted = roots.map { sourceItem ->
            val rawCol = sourceItem.number("column", -1)
            val rawRow = sourceItem.number("row", -1)
            val base = convert(sourceItem, iconUriFor, gridColumns)
            if (rawCol >= 0 && rawRow >= 0) {
                val blockIdx = rawCol / choice.columns.coerceAtLeast(1)
                val localCol = rawCol % choice.columns.coerceAtLeast(1)
                val mappedCol = if (reverse) (choice.columns - 1 - localCol).coerceIn(0, choice.columns - 1) else localCol
                val mappedRow = (blockRowOffsets[blockIdx] ?: 0) + rawRow
                base.copy(gridCol = mappedCol, gridRow = mappedRow)
            } else base
        }.toMutableList()
        if (choice.isAppDrawer) converted.add(ComponentItem(type = ComponentType.APP_DRAWER_CARD,
            title = choice.title, showTitle = true, w = gridColumns, h = 28,
            gridSpanX = GRID_SPAN_FULL_ROW, gridSpanY = 4))
        return converted
    }

    private fun convert(item: JsonObject, iconUriFor: (String) -> String, gridColumns: Int): ComponentItem {
        val originalType = item.number("type")
        val intent = item.string("intent")
        val website = webAddress(intent)
        val type = when (originalType) {
            2 -> ComponentType.APP_ICON
            3 -> ComponentType.CUSTOM_SHORTCUT
            4 -> ComponentType.FOLDER
            8 -> if (intent.contains("S.systemShortcutType=settings.BRIGHTNESS_BAR;")) ComponentType.BRIGHTNESS_BAR else ComponentType.TEXT_LABEL
            12 -> if (website != null) ComponentType.WEBSITE_LINK else ComponentType.TEXT_LABEL
            13 -> ComponentType.APP_WIDGET
            else -> ComponentType.TEXT_LABEL
        }
        val title = item.string("label").ifBlank { "Panels 项目 ${item.number("id")}" }
        val provider = if (originalType == 13) Regex("S.flattenedComponentName=([^;]+)")
            .find(intent)?.groupValues?.get(1)?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }.orEmpty() else ""
        val subtitle = when {
            originalType == 13 -> "需要重新绑定小部件${if (provider.isNotBlank()) " · $provider" else ""}"
            originalType == 14 -> "Panels 快速搜索（暂不支持，保留占位）"
            type == ComponentType.TEXT_LABEL -> "Panels 类型 $originalType（暂不支持，保留占位）"
            else -> ""
        }
        val icon = item.string("iconName")
        val asset = listOf(
            "/altered/$icon.png", "altered/$icon.png",
            "/original/$icon.png", "original/$icon.png",
            "/gallery/$icon.png", "gallery/$icon.png"
        ).firstOrNull { it in iconAssets }
        val sc = screen(item)
        return ComponentItem(type = type, title = title, subtitle = subtitle,
            showTitle = type !in setOf(ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.FOLDER),
            packageName = item.string("packageName"),
            intentUri = when (type) {
                ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT -> intent
                ComponentType.WEBSITE_LINK -> website.orEmpty()
                else -> ""
            }, iconUri = asset?.let(iconUriFor).orEmpty(),
            textContent = if (type == ComponentType.TEXT_LABEL) "$title\n$subtitle" else "",
            widgetId = -1,
            containerComponents = ordered(children[item.number("id")].orEmpty()).map { convert(it, iconUriFor, gridColumns) },
            containerColumns = folderColumns(item.number("id")).coerceAtMost(8), containerLayout = ContainerLayoutType.GRID,
            containerItemSizeDp = 0, containerIconSizeDp = iconSizeDp(sc),
            containerItemGapDp = spacingDp(sc),
            // Source websites are launcher tiles, not full-width website cards. Bars,
            // unsupported-text placeholders and widgets need usable full-row surfaces.
            gridSpanX = if (type in setOf(ComponentType.BRIGHTNESS_BAR, ComponentType.TEXT_LABEL,
                ComponentType.APP_WIDGET)) GRID_SPAN_FULL_ROW else 1,
            gridSpanY = when {
                type == ComponentType.APP_WIDGET -> 2
                isContainerGridIcon(type) -> 0
                else -> 1
            },
            w = if (type in setOf(ComponentType.BRIGHTNESS_BAR, ComponentType.TEXT_LABEL,
                ComponentType.APP_WIDGET)) gridColumns else 1,
            h = if (type == ComponentType.APP_WIDGET) 14 else 8,
            autoHeight = type in setOf(ComponentType.APP_ICON, ComponentType.CUSTOM_SHORTCUT, ComponentType.FOLDER))
    }


}

object PanelsBackupImporter {
    private const val MAX_ARCHIVE_BYTES = 40 * 1024 * 1024
    private const val MAX_ENTRIES = 256
    private const val MAX_ENTRY = 4 * 1024 * 1024
    private const val MAX_TOTAL = 16 * 1024 * 1024
    private const val MAX_ITEMS = 4096
    private val required = setOf("items_table.json", "panels_table.json", "sets_table.json", "screens_table.json")
    private val supportedTables = required + setOf("subitems_table.json", "themes_table.json", "gesture_table.json")
    private val imagePath = Regex("/?(?:original|altered|gallery)/[A-Za-z0-9_.-]+\\.png")

    fun parse(input: InputStream): PanelsImportArchive =
        parseBytes(SafeZipReader.readBoundedBytes(input, MAX_ARCHIVE_BYTES))

    fun parseBytes(archiveBytes: ByteArray): PanelsImportArchive {
        require(archiveBytes.size <= MAX_ARCHIVE_BYTES) { "Panels 备份压缩包过大" }
        val normalizedNames = HashSet<String>()
        var skippedAttachments = 0
        fun validate(entry: SafeZipReader.EntryMeta) {
            val name = entry.name
            val legacyImage = imagePath.matches(name)
            require(name.isNotEmpty() && name.length <= 240 && !name.contains('\u0000') &&
                !name.contains("..") && !name.contains('\\') && !name.contains(':') &&
                (!name.startsWith('/') || legacyImage)) { "不安全的备份路径" }
            require(normalizedNames.add(name.removePrefix("/"))) { "重复的备份文件" }
            if (!entry.isDirectory && name !in supportedTables && !legacyImage) skippedAttachments++
        }
        val files = SafeZipReader.readEntriesFromBytes(
            archiveBytes = archiveBytes,
            maxEntries = MAX_ENTRIES,
            maxEntryBytes = MAX_ENTRY,
            maxTotalUncompressedBytes = MAX_TOTAL,
            include = { it.name in supportedTables },
            validateEntry = ::validate
        )
        require(files.keys.containsAll(required)) { "缺少 Panels 必需数据表" }
        val json = Json { isLenient = false; allowSpecialFloatingPointValues = false }
        val tables = files.filterKeys { it.endsWith(".json") }.mapValues { (_, bytes) ->
            // UTF-8 replacement would silently corrupt labels; decode strictly.
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            validateJsonDepth(text)
            json.parseToJsonElement(text).also { validateJson(it) }
        }
        (required + "subitems_table.json" + "themes_table.json" + "gesture_table.json").forEach { name ->
            if (name in tables) require(tables[name] is JsonArray) { "Panels 数据表格式错误：$name" }
        }
        tables.filterKeys { it.endsWith("_table.json") }.forEach { (name, _) ->
            require(tables[name] is JsonArray) { "Panels 数据表格式错误：$name" }
            val rows = tables.rows(name)
            require(rows.size <= MAX_ITEMS) { "Panels 数据行数超限" }
            val ids = rows.map { it.requiredInt("id") }
            require(ids.distinct().size == ids.size) { "Panels 数据表包含重复 ID：$name" }
        }
        val items = tables.rows("items_table.json")
        val panels = tables.rows("panels_table.json")
        require(panels.isNotEmpty() && panels.size <= 128) { "Panels 页面数量无效" }
        val panelIds = panels.map { it.requiredInt("id") }.toSet()
        val setRows = tables.rows("sets_table.json").associateBy { it.requiredInt("id") }
        val screenIds = tables.rows("screens_table.json").map { it.requiredInt("id") }.toSet()
        panels.forEach { p -> require(p.requiredInt("setId") in setRows) { "Panels 页面所属集合缺失" } }
        setRows.values.forEach { require(it.requiredInt("screenId") in screenIds) { "Panels 屏幕配置缺失" } }
        val byId = items.associateBy { it.requiredInt("id") }
        items.forEach { item ->
            item.requiredInt("type")
            require(item.requiredInt("panelId") in panelIds) { "Panels 项目所属页面缺失" }
            var cursor = item
            val seen = mutableSetOf<Int>()
            while (cursor.number("parentFolderId", -1) != -1) {
                require(seen.add(cursor.requiredInt("id")) && seen.size <= 32) { "Panels 文件夹循环或嵌套过深" }
                val parent = byId[cursor.requiredInt("parentFolderId")] ?: error("Panels 文件夹父项缺失")
                require(parent.number("type") == 4 && parent.number("panelId") == item.number("panelId")) { "Panels 文件夹引用无效" }
                cursor = parent
            }
        }
        tables.rows("subitems_table.json").forEach { require(it.requiredInt("parentId") in byId) { "Panels 手势子项父项缺失" } }
        val refs = (items + tables.rows("subitems_table.json")).map { it.string("iconName") }.filter { it.isNotBlank() }.toSet()
        val assetNames = SafeZipReader.listByteEntries(archiveBytes, MAX_ENTRIES).entries.map { it.name }
            .filter { imagePath.matches(it) && it.substringAfterLast('/').removeSuffix(".png") in refs }.toSet()
        normalizedNames.clear()
        skippedAttachments = 0
        val assets = SafeZipReader.readEntriesFromBytes(
            archiveBytes = archiveBytes,
            maxEntries = MAX_ENTRIES,
            maxEntryBytes = MAX_ENTRY,
            maxTotalUncompressedBytes = MAX_TOTAL - files.values.sumOf { it.size },
            include = { it.name in assetNames },
            validateEntry = ::validate
        )
        assets.values.forEach(::validatePanelsPng)
        val warnings = mutableListOf("仅追加所选页面或文件夹；不导入触发器、授权、主题切换或系统设置。",
            "布局保留原网格列数、阅读顺序与图标尺寸；文字和主题仅部分映射，复杂卡片使用整行适配。",
            "应用未安装时保留应用卡片；导入本身不会启动应用或执行快捷方式。")
        if (panels.any { it.number("counterSpanCount", 4) > 8 } ||
            items.filter { it.number("type") == 4 }.any { folder ->
                items.any { it.number("parentFolderId", -1) == folder.number("id") && it.number("column", -1) >= 8 }
            }) warnings += "源布局超过 8 列：独立页面最多保留 12 列，容器和文件夹适配为最多 8 列；所有项目仍保留。"
        if (items.any { it.number("type") == 13 }) warnings += "小部件保留占位，外部 widgetId 已清除，需在本应用重新绑定。"
        val unsupported = items.filter { it.number("type") !in setOf(2, 3, 4, 8, 12, 13) ||
            (it.number("type") == 8 && !it.string("intent").contains("S.systemShortcutType=settings.BRIGHTNESS_BAR;")) ||
            (it.number("type") == 12 && webAddress(it.string("intent")) == null) }
        if (unsupported.isNotEmpty()) warnings += "${unsupported.size} 项暂不支持，保留可见文字占位。"
        if (tables.rows("subitems_table.json").isNotEmpty()) warnings += "${tables.rows("subitems_table.json").size} 个智能快捷方式附加手势暂不映射，主卡片仍保留。"
        if (items.any { it.string("label").contains('\uFFFD') }) warnings += "原备份部分标签已含乱码，按原文保留，可导入后编辑。"
        if (skippedAttachments > 0) warnings += "$skippedAttachments 个安全但不支持的附属文件已跳过。"
        return PanelsImportArchive(tables, assets, warnings)
    }

    private fun validateJson(element: JsonElement, depth: Int = 0) {
        require(depth <= 64) { "Panels JSON 嵌套过深" }
        when (element) {
            is JsonObject -> element.values.forEach { validateJson(it, depth + 1) }
            is JsonArray -> element.forEach { validateJson(it, depth + 1) }
            is JsonPrimitive -> if (!element.isString && element != JsonNull && element.booleanOrNull == null)
                require(element.doubleOrNull?.isFinite() == true) { "Panels JSON 数值无效" }
        }
    }

    // Reject excessive depth before the JSON parser can recurse into untrusted input.
    private fun validateJsonDepth(text: String) {
        var quoted = false
        var escaped = false
        var depth = 0
        text.forEach { char ->
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 64) { "Panels JSON 嵌套过深" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Panels JSON 损坏" } }
            }
        }
        require(!quoted && depth == 0) { "Panels JSON 损坏" }
    }
}

private fun Map<String, JsonElement>.rows(name: String): List<JsonObject> = (this[name] as? JsonArray)
    ?.map { it as? JsonObject ?: error("Panels 数据行不是对象：$name") }.orEmpty()
private fun JsonObject.string(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.number(key: String, default: Int = 0): Int = (get(key) as? JsonPrimitive)?.intOrNull ?: default
private fun JsonObject.requiredInt(key: String): Int = (get(key) as? JsonPrimitive)?.intOrNull ?: error("Panels 缺少整数：$key")
private fun JsonObject.boolean(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull ?: false
private fun webAddress(value: String): String? {
    val normalized = if (value.startsWith("intent://") && value.contains("#Intent;")) {
        val scheme = Regex("(?:^|;)scheme=(https?);").find(value.substringAfter("#Intent;"))?.groupValues?.get(1) ?: return null
        "$scheme://${value.removePrefix("intent://").substringBefore("#Intent;")}"
    } else value
    return runCatching { URI(normalized).takeIf { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null }?.toASCIIString() }.getOrNull()
}
