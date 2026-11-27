package com.example.globalminusone.data

import org.junit.Assert.*
import org.junit.Test
import com.example.globalminusone.data.storage.ConfigurationCodec
import com.example.globalminusone.data.operations.MAX_COMPONENT_TREE_DEPTH
import com.example.globalminusone.data.policy.containerRowGapDp
import com.example.globalminusone.data.operations.flattenComponents

class ConfigurationCodecTest {
    @Test fun absentSchemaMigratesLegacyGapsRecursivelyButExplicitSchemaRetainsNegativeGap() {
        val legacy = """{"futureSetting":true,"pages":[{"id":"page","name":"旧配置","gridRowGapDp":-1,
            "components":[{"id":"outer","type":"CONTAINER_BOX","containerRowGapDp":-1,
              "containerComponents":[{"id":"inner","type":"CONTAINER_BOX","containerRowGapDp":-1}]}]}]}"""
        val migrated = ConfigurationCodec.decodeValidatedState(legacy)
        assertEquals(CURRENT_STATE_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(ROW_GAP_INHERIT, migrated.pages.single().gridRowGapDp)
        assertTrue(migrated.pages.single().components.flattenComponents().all { it.containerRowGapDp == ROW_GAP_INHERIT })
        val current = ConfigurationCodec.decodeValidatedState(legacy.replaceFirst("{", "{\"schemaVersion\":2,"))
        assertEquals(-1, current.pages.single().gridRowGapDp)
        assertTrue(current.pages.single().components.flattenComponents().all { it.containerRowGapDp == -1 })
    }

    @Test fun invalidNestedTreesFailInsteadOfSilentlyDiscardingChildren() {
        val child = ComponentItem(id = "child", type = ComponentType.CONTAINER_BOX)
        val duplicate = ComponentItem(id = "child", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(child))
        fails("组件ID重复、为空或存在循环", stateWith(duplicate))
        val nonContainer = child.copy(type = ComponentType.APP_ICON, containerComponents = listOf(child.copy(id = "nested")))
        fails("仅容器可包含子卡片", stateWith(nonContainer))
        var nested = child
        repeat(MAX_COMPONENT_TREE_DEPTH) { index ->
            nested = ComponentItem(id = "box-$index", type = ComponentType.CONTAINER_BOX, containerComponents = listOf(nested))
        }
        fails("容器嵌套超过安全深度", stateWith(nested))
    }

    @Test fun emptyWhitespaceAndDuplicateDirectoryUrisRetainTheirValidationErrors() {
        for (uri in listOf("", "  ")) {
            fails("文件目录地址不能为空", stateWith().copy(fileBrowserConfig = FileBrowserConfig(roots = listOf(FileBrowserRoot(uri, "目录")))))
        }
        fails("文件目录地址不能重复", stateWith().copy(fileBrowserConfig = FileBrowserConfig(roots = listOf(
            FileBrowserRoot("content://tree/same", "一"), FileBrowserRoot("content://tree/same", "二")
        ))))
    }

    @Test fun unsafeEnvelopeAndInvalidStateBoundsFailWithExistingMessages() {
        expectFailure("配置嵌套超过安全深度") { ConfigurationCodec.decodeValidatedState("[".repeat(129)) }
        expectFailure("配置文件过大") { ConfigurationCodec.decodeValidatedState(" ".repeat(8_000_001)) }
        fails("网格列数必须在 8 到 64 之间", stateWith().copy(advancedConfig = AdvancedConfig(gridColumns = 7)))
        fails("配置必须至少包含一页", stateWith().copy(pages = emptyList()))
        fails("当前页面下标无效", stateWith().copy(currentPageIndex = 1))
        fails("最小滑动距离必须在 16 到 160 dp 之间", stateWith().copy(triggerConfig = TriggerConfig(minimumSwipeDistanceDp = 161)))
    }

    @Test fun restoredStateBoundsGeometryAndClearsOnlyTransientEditingAfterValidation() {
        val incoming = stateWith(ComponentItem(id = "selected", type = ComponentType.CONTAINER_BOX)).copy(
            editMode = true, selectedComponentId = "selected", noteText = "恢复正文",
            advancedConfig = AdvancedConfig(layoutLocked = true),
            themeConfig = ThemeConfig(panelWidthPercent = -9, panelHeightPercent = 500,
                panelHorizontalOffsetPercent = 900, panelVerticalPositionPercent = -20, cardSpacingDp = 999),
            triggerConfig = TriggerConfig(sidePullHorizontalOffsetDp = 999),
        )
        val restored = ConfigurationCodec.decodeValidatedState(ConfigurationCodec.encode(incoming))
        assertFalse(restored.editMode)
        assertNull(restored.selectedComponentId)
        assertTrue(restored.advancedConfig.layoutLocked)
        assertEquals(incoming.pages, restored.pages)
        assertEquals("恢复正文", restored.noteText)
        assertEquals(30, restored.themeConfig.panelWidthPercent)
        assertEquals(100, restored.themeConfig.panelHeightPercent)
        assertEquals(100, restored.themeConfig.panelHorizontalOffsetPercent)
        assertEquals(0, restored.themeConfig.panelVerticalPositionPercent)
        assertEquals(32, restored.themeConfig.cardSpacingDp)
        assertEquals(160, restored.triggerConfig.sidePullHorizontalOffsetDp)
        val lower = ConfigurationCodec.decodeValidatedState(ConfigurationCodec.encode(incoming.copy(
            triggerConfig = incoming.triggerConfig.copy(sidePullHorizontalOffsetDp = -100)
        )))
        assertEquals(-1, lower.triggerConfig.sidePullHorizontalOffsetDp)
    }

    @Test fun exportPreservesDefaultFieldsUnicodeEscapingAndExistingCompactOrPrettyFormat() {
        val state = stateWith().copy(noteText = "中文\n\"引号\"\\路径")
        val compact = ConfigurationCodec.encode(state)
        val pretty = ConfigurationCodec.encode(state, pretty = true)
        assertTrue(compact.startsWith("{\"schemaVersion\":2,\"triggerConfig\":{"))
        assertFalse(compact.contains('\n'))
        assertTrue(compact.contains("\"editMode\":false,\"selectedComponentId\":null"))
        assertTrue(compact.contains("\"noteText\":\"中文\\n\\\"引号\\\"\\\\路径\""))
        assertTrue(pretty.startsWith("{\n    \"schemaVersion\": 2,\n    \"triggerConfig\": {\n"))
        assertTrue(pretty.contains("\n    \"selectedComponentId\": null,"))
        assertEquals(state, ConfigurationCodec.decodeValidatedState(pretty))
        assertEquals(compact, ConfigurationCodec.encode(ConfigurationCodec.decodeValidatedState(compact)))
        assertEquals(pretty, ConfigurationCodec.encode(ConfigurationCodec.decodeValidatedState(pretty), pretty = true))
        val editingExport = ConfigurationCodec.encode(state.copy(editMode = true, selectedComponentId = "selection"))
        assertTrue(editingExport.contains("\"editMode\":true,\"selectedComponentId\":\"selection\""))
    }

    private fun stateWith(vararg components: ComponentItem) = GlobalMinusOneState(
        pages = listOf(PageConfig(id = "page", name = "测试页", components = components.toList())),
        profiles = emptyList(), todos = emptyList(), clipboardHistory = emptyList(), cards = emptyList(), quickActions = emptyList(),
    )

    private fun fails(message: String, state: GlobalMinusOneState) = expectFailure(message) {
        ConfigurationCodec.decodeValidatedState(ConfigurationCodec.encode(state))
    }

    private fun expectFailure(message: String, action: () -> Unit) {
        try {
            action()
            fail("Expected validation failure: $message")
        } catch (expected: IllegalArgumentException) {
            assertEquals(message, expected.message)
        }
    }
}
