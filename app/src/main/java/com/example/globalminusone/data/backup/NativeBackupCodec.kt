package com.example.globalminusone.data.backup

import com.example.globalminusone.data.*
import com.example.globalminusone.data.panels.SafeZipReader
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class BackupPreference(val type: String, val value: String = "", val values: List<String> = emptyList())

@Serializable
data class NativeBackupPayload(
    val state: GlobalMinusOneState,
    val browserLibrary: Map<String, BackupPreference> = emptyMap(),
    val browserSettings: Map<String, BackupPreference> = emptyMap(),
    /** Original image reference to ZIP entry. References are rewritten only after validation. */
    val assets: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val installationId: String = "",
    val configurationOnly: Boolean = false
)

@Serializable
private data class BackupManifest(val format: String = "GlobalMinusOne", val version: Int = 1,
    val hashes: Map<String, String>)

data class DecodedNativeBackup(val payload: NativeBackupPayload, val assets: Map<String, ByteArray>)

/** No Android calls: archive validation always finishes before the caller touches live preferences. */
object NativeBackupCodec {
    const val MAX_ARCHIVE_BYTES = 48 * 1024 * 1024
    const val MAX_ASSET_BYTES = 4 * 1024 * 1024
    const val MAX_ENTRIES = 260
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val PAYLOAD = "configuration.json"
    private const val MANIFEST = "manifest.json"
    private val assetPath = Regex("assets/[0-9a-f]{64}\\.bin")

    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun readBounded(input: InputStream, limit: Int): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(result.size().toLong() + count <= limit) { "备份内容超过安全大小限制" }
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    fun write(output: OutputStream, backup: DecodedNativeBackup) {
        validatePayload(backup.payload)
        require(backup.assets.keys == backup.payload.assets.values.toSet()) { "资源清单不匹配" }
        val entries = linkedMapOf(PAYLOAD to json.encodeToString(backup.payload).toByteArray(Charsets.UTF_8))
        require(entries.getValue(PAYLOAD).size <= 8_000_000) { "配置文件过大" }
        backup.assets.forEach { (name, bytes) ->
            require(assetPath.matches(name) && bytes.size <= MAX_ASSET_BYTES && name == "assets/${sha256(bytes)}.bin") { "资源无效" }
            entries[name] = bytes
        }
        require(entries.size + 1 <= MAX_ENTRIES && entries.values.sumOf { it.size.toLong() } <= MAX_ARCHIVE_BYTES - 128_000) { "备份过大" }
        val manifest = json.encodeToString(BackupManifest(hashes = entries.mapValues { sha256(it.value) })).toByteArray()
        require(manifest.size <= 128_000) { "备份清单过大" }
        ZipOutputStream(output).use { zip ->
            (linkedMapOf(MANIFEST to manifest) + entries).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream): DecodedNativeBackup = readArchive(readBounded(input, MAX_ARCHIVE_BYTES))

    private fun readArchive(archive: ByteArray): DecodedNativeBackup {
        // Streaming ZIP traversal accepts complete local entries even if the central directory
        // or its tail was lost. Require the same complete archive structure as Panels imports
        // before decoding configuration or offering a restore preview.
        val entries = SafeZipReader.readEntriesFromBytes(
            archiveBytes = archive,
            maxEntries = MAX_ENTRIES,
            maxEntryBytes = 8_000_000,
            maxTotalUncompressedBytes = MAX_ARCHIVE_BYTES
        ) { entry ->
            val name = entry.name
            require(!entry.isDirectory && (name == MANIFEST || name == PAYLOAD || assetPath.matches(name))) {
                "备份含有不支持的路径"
            }
            val limit = when (name) { MANIFEST -> 128_000; PAYLOAD -> 8_000_000; else -> MAX_ASSET_BYTES }
            require(entry.uncompressedSize in 0..limit.toLong()) { "备份内容超过安全大小限制" }
        }
        val manifestRaw = requireNotNull(entries.remove(MANIFEST)) { "缺少备份清单" }.toString(Charsets.UTF_8)
        checkConfigurationJsonEnvelope(manifestRaw)
        val manifest = json.decodeFromString<BackupManifest>(manifestRaw)
        require(manifest.format == "GlobalMinusOne" && manifest.version == 1) { "不支持此备份版本" }
        require(manifest.hashes.keys == entries.keys && PAYLOAD in entries) { "备份文件缺失或存在多余文件" }
        entries.forEach { (name, bytes) -> require(sha256(bytes) == manifest.hashes[name]) { "备份校验失败：$name" } }
        val raw = requireNotNull(entries.remove(PAYLOAD)).toString(Charsets.UTF_8)
        checkConfigurationJsonEnvelope(raw)
        val decodedPayload = json.decodeFromString<NativeBackupPayload>(raw)
        val hasSchemaVersion = runCatching {
            json.parseToJsonElement(raw).jsonObject["state"]?.jsonObject?.containsKey("schemaVersion") == true
        }.getOrDefault(true)
        val payload = if (hasSchemaVersion) decodedPayload else decodedPayload.copy(state = decodedPayload.state.migrateLegacyRowGapSentinels())
        validatePayload(payload)
        require(entries.keys == payload.assets.values.toSet()) { "资源清单不匹配" }
        entries.forEach { (name, bytes) -> require(name == "assets/${sha256(bytes)}.bin") { "资源摘要不符" } }
        return DecodedNativeBackup(payload, entries)
    }

    /** Old JSON files replace configuration only; absent browser data must never mean clear it. */
    fun readCompatible(input: InputStream, currentBrowserLibrary: Map<String, BackupPreference>,
        currentBrowserSettings: Map<String, BackupPreference>): DecodedNativeBackup {
        val bytes = readBounded(input, MAX_ARCHIVE_BYTES)
        val start = if (bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte()) 3 else 0
        val first = (start until bytes.size).firstOrNull { when (bytes[it].toInt()) { 9, 10, 13, 32 -> false; else -> true } }
        if (first == null || bytes[first] != '{'.code.toByte()) return readArchive(bytes)
        require(bytes.size <= 8_000_000) { "旧配置文件过大" }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        checkConfigurationJsonEnvelope(raw)
        val hasSchemaVersion = runCatching {
            json.parseToJsonElement(raw).jsonObject.containsKey("schemaVersion")
        }.getOrDefault(true)
        val decodedState = json.decodeFromString<GlobalMinusOneState>(raw)
        val state = if (hasSchemaVersion) decodedState else decodedState.migrateLegacyRowGapSentinels()
        val payload = NativeBackupPayload(state, currentBrowserLibrary, currentBrowserSettings,
            warnings = listOf("旧格式仅配置，现有书签/历史/浏览器偏好保留；图片仅保留原引用，不包含图片文件。", "系统授权与小部件绑定不随旧配置文件迁移。"), configurationOnly = true)
        validatePayload(payload)
        return DecodedNativeBackup(payload, emptyMap())
    }

    fun validateState(state: GlobalMinusOneState, snapshot: Boolean = false) {
        require(state.pages.isNotEmpty() && state.pages.size <= 1000 && state.currentPageIndex in state.pages.indices) { "页面配置无效" }
        require(state.pages.all { it.id.isNotBlank() } && state.pages.map { it.id }.distinct().size == state.pages.size) { "页面 ID 无效" }
        require(state.advancedConfig.gridColumns in 8..64 && state.triggerConfig.minimumSwipeDistanceDp in 16..160) { "网格或触发设置无效" }
        require(state.fileBrowserConfig.roots.all { it.uri.isNotBlank() } && state.fileBrowserConfig.roots.map { it.uri }.distinct().size == state.fileBrowserConfig.roots.size) { "文件目录配置无效" }
        require(state.pages.all {
            it.gridColumnCount in 0..12 && it.gridIconSizeDp in 0..192 &&
                it.gridItemGapDp in MIN_GRID_GAP_DP..MAX_GRID_GAP_DP &&
                (it.gridRowGapDp == ROW_GAP_INHERIT || it.gridRowGapDp in MIN_GRID_GAP_DP..MAX_GRID_GAP_DP)
        }) { "页面网格参数无效" }
        require(state.themeConfig.cardSpacingDp in MIN_CARD_SPACING_DP..MAX_CARD_SPACING_DP) { "主题卡片间距参数无效" }
        require(state.pages.flatMap { it.components.flattenComponents() }.all { it.containerIconSizeDp in 0..192 }) { "容器图标参数无效" }
        require(state.pages.flatMap { it.components.flattenComponents() }.all {
            it.containerItemGapDp in MIN_GRID_GAP_DP..MAX_GRID_GAP_DP &&
                (it.containerRowGapDp == ROW_GAP_INHERIT || it.containerRowGapDp in MIN_GRID_GAP_DP..MAX_GRID_GAP_DP) &&
                it.containerItemPaddingVerticalDp in 0..48 &&
                it.containerIconLabelGapDp in MIN_ICON_LABEL_GAP_DP..MAX_ICON_LABEL_GAP_DP
        }) { "容器纵向间距参数无效" }
        validateComponentTrees(state.pages)
        state.pages.forEach { page -> page.components.flattenComponents().forEach {
            require(it.w in 1..100_000 && it.h in 1..100_000 && it.x in 0..1_000_000 && it.y in 0..1_000_000) { "卡片尺寸或位置无效" }
        } }
        require(state.profiles.size <= 200 && state.profiles.map { it.id }.distinct().size == state.profiles.size) { "方案库无效" }
        if (snapshot) require(state.profiles.isEmpty()) { "方案快照不能嵌套方案库" }
        state.profiles.forEach { profile -> if (profile.stateJson.isNotBlank()) {
            checkConfigurationJsonEnvelope(profile.stateJson)
            validateState(json.decodeFromString(profile.stateJson), snapshot = true)
        } }
    }

    fun validatePayload(payload: NativeBackupPayload) {
        validateState(payload.state)
        validatePreferences(payload.browserLibrary, library = true)
        validatePreferences(payload.browserSettings, library = false)
        require(payload.assets.size <= 256 && payload.assets.all { it.key.length <= 8192 && assetPath.matches(it.value) }) { "资源引用无效" }
        require(payload.warnings.size <= 512 && payload.warnings.all { it.length <= 1000 }) { "警告清单过大" }
        require(payload.installationId.length <= 128) { "安装标识无效" }
        require(!payload.configurationOnly || payload.assets.isEmpty()) { "旧配置不能携带备份资源" }
        require(payload.assets.keys.all { it in collectImageReferences(payload.state) }) { "资源不是配置引用的图片" }
    }

    fun validatePreferences(values: Map<String, BackupPreference>, library: Boolean) {
        require(values.size <= 256) { "浏览器设置过多" }
        values.forEach { (key, pref) ->
            require(key.length in 1..256 && pref.value.length <= 512_000 && pref.values.size <= 1000) { "浏览器设置过大" }
            when (pref.type) {
                "string" -> Unit
                "boolean" -> require(pref.value == "true" || pref.value == "false")
                "int" -> require(pref.value.toIntOrNull() != null)
                "long" -> require(pref.value.toLongOrNull() != null)
                "float" -> require(pref.value.toFloatOrNull()?.isFinite() == true)
                "set" -> require(pref.values.all { it.length <= 8192 })
                else -> error("不支持的浏览器设置类型")
            }
            if (library && key in setOf("bookmarks", "history")) {
                require(pref.type == "string") { "浏览器记录类型无效" }
                checkConfigurationJsonEnvelope(pref.value)
                val array = json.parseToJsonElement(pref.value) as? JsonArray ?: error("浏览器记录无效")
                require(array.size <= 100) { "浏览器记录过多" }
                array.forEach { item ->
                    val obj = item as? JsonObject ?: error("浏览器记录无效")
                    require(obj["url"] is JsonPrimitive && obj["url"]!!.jsonPrimitive.isString &&
                        obj["title"] is JsonPrimitive && obj["title"]!!.jsonPrimitive.isString) { "浏览器记录字段无效" }
                }
            }
            if (!library) {
                val expected = when (key) {
                    "search_engine" -> "string"
                    "night_mode", "ad_block", "bottom_bar", "no_image" -> "boolean"
                    "reader_font_sp" -> "int"
                    "window_width_fraction", "window_height_fraction", "window_x_fraction", "window_y_fraction" -> "float"
                    else -> null
                }
                require(expected == null || pref.type == expected) { "浏览器设置类型无效：$key" }
            }
        }
    }

    fun collectImageReferences(state: GlobalMinusOneState): Set<String> {
        val refs = linkedSetOf<String>()
        mapImages(state) { it.also { value -> if (value.isNotBlank()) refs += value } }
        return refs
    }

    /** Explicit image fields only. Intent strings, commands and arbitrary text are never rewritten. */
    fun mapImages(state: GlobalMinusOneState, transform: (String) -> String): GlobalMinusOneState {
        fun components(items: List<ComponentItem>): List<ComponentItem> = items.map { item -> item.copy(
            iconUri = transform(item.iconUri), imageUri = transform(item.imageUri),
            photoCubeUris = item.photoCubeUris.map(transform), containerComponents = components(item.containerComponents)) }
        return state.copy(themeConfig = state.themeConfig.copy(bgImageUri = transform(state.themeConfig.bgImageUri)),
            pages = state.pages.map { it.copy(bgImageUri = transform(it.bgImageUri), components = components(it.components)) },
            appAppearanceOverrides = state.appAppearanceOverrides.mapValues { (_, value) -> value.copy(iconUri = transform(value.iconUri)) },
            cards = state.cards.map { it.copy(customImageUri = it.customImageUri?.let(transform)) },
            profiles = state.profiles.map { profile -> if (profile.stateJson.isBlank()) profile else profile.copy(
                stateJson = json.encodeToString(mapImages(json.decodeFromString(profile.stateJson), transform))) })
    }

    /** Widget host IDs and URI grants belong to an installation; preserve cards for rebinding. */
    fun verifiedWidgetBindings(state: GlobalMinusOneState, isValid: (BoundWidgetInfo) -> Boolean): GlobalMinusOneState {
        val bindings = state.boundWidgets.filter(isValid)
        val validIds = bindings.map { it.appWidgetId }.toSet()
        fun components(items: List<ComponentItem>): List<ComponentItem> = items.map { it.copy(widgetId = it.widgetId.takeIf { id -> id in validIds } ?: -1,
            containerComponents = components(it.containerComponents)) }
        return state.copy(editMode = false, selectedComponentId = null, boundWidgets = bindings,
            pages = state.pages.map { it.copy(components = components(it.components)) }, cards = state.cards.map { it.copy(widgetId = it.widgetId?.takeIf { id -> id in validIds }) },
            profiles = state.profiles.map { if (it.stateJson.isBlank()) it else it.copy(
                stateJson = json.encodeToString(verifiedWidgetBindings(json.decodeFromString(it.stateJson), isValid))) })
    }
}
