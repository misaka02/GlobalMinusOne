package com.example.globalminusone.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Configuration wire format and validation shared by persistence, import and restore. */
internal object ConfigurationCodec {
    // Reused by the section encoder so persisted fragments follow this exact v5 wire format.
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(state: GlobalMinusOneState, pretty: Boolean = false): String =
        if (pretty) prettyJson.encodeToString(state) else json.encodeToString(state)

    fun decodeValidatedState(raw: String): GlobalMinusOneState {
        checkConfigurationJsonEnvelope(raw)
        val hasSchemaVersion = runCatching {
            json.parseToJsonElement(raw).jsonObject.containsKey("schemaVersion")
        }.getOrDefault(true)
        val parsed = json.decodeFromString<GlobalMinusOneState>(raw).let {
            if (hasSchemaVersion) it else it.migrateLegacyRowGapSentinels()
        }
        require(parsed.advancedConfig.gridColumns in 8..64) { "网格列数必须在 8 到 64 之间" }
        require(parsed.pages.isNotEmpty()) { "配置必须至少包含一页" }
        require(parsed.currentPageIndex in parsed.pages.indices) { "当前页面下标无效" }
        require(parsed.triggerConfig.minimumSwipeDistanceDp in 16..160) { "最小滑动距离必须在 16 到 160 dp 之间" }
        val directoryUris = parsed.fileBrowserConfig.roots.map { it.uri }
        require(directoryUris.all { it.isNotBlank() }) { "文件目录地址不能为空" }
        require(directoryUris.distinct().size == directoryUris.size) { "文件目录地址不能重复" }
        validateComponentTrees(parsed.pages)
        val bounded = parsed.copy(
            themeConfig = parsed.themeConfig.normalizedPanelGeometry(),
            triggerConfig = parsed.triggerConfig.copy(
                sidePullHorizontalOffsetDp = parsed.triggerConfig.sidePullHorizontalOffsetDp.coerceIn(-1, 160)
            )
        )
        // A process restart never resumes an unconfirmed editing gesture/session.
        return bounded.copy(editMode = false, selectedComponentId = null)
    }
}
