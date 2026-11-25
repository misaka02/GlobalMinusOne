package com.example.globalminusone.regression.v2478

import com.example.globalminusone.data.AppKeyboardLauncherSpec
import com.example.globalminusone.data.AppQuickSearchMatcher
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ContainerLayoutType
import com.example.globalminusone.data.GRID_SPAN_FULL_ROW
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.KeyboardCardLayout
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.PinyinIndexProvider
import com.example.globalminusone.data.containerGridChildBounds
import com.example.globalminusone.data.containerPopupColumns
import com.example.globalminusone.data.defaultPageGridSpan
import com.example.globalminusone.ui.panel.commitContainerGridChildResize
import com.example.globalminusone.ui.settings.SettingsSearchIndex
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2478SettingsSearchFolderResizeAndKeyboardLauncherTest {

    // =========================================================================
    // 1. Settings search completeness, Pinyin/initials & direct jump targetGroups
    // =========================================================================

    @Test
    fun settingsSearchCoversAllSectionsAndSupportsPinyinAndCompositeQueries() {
        assertTrue(SettingsSearchIndex.entries.size >= 50)
        assertEquals(
            SettingsSearchIndex.entries.size,
            SettingsSearchIndex.entries.map { it.id }.toSet().size
        )

        val checks = listOf(
            "无障碍" to ("PERMISSIONS" to ""),
            "电池优化" to ("PERMISSIONS" to ""),
            "手势条动作" to ("OPERATION" to "手势条动作"),
            "鼠标" to ("SB_SETTINGS" to "鼠标"),
            "异形屏" to ("THEME" to "动画与其它"),
            "护眼" to ("EXTEND" to "护眼与滤色"),
            "验证码" to ("EXTEND" to "其它"),
            "离线词库" to ("SCREEN_ASSISTANT" to "ATTACHMENTS"),
            "小布" to ("SCREEN_ASSISTANT" to "BREENO"),
            "备份" to ("CONFIG" to ""),
            "使用教程" to ("TUTORIAL" to ""),
            "开源" to ("ABOUT" to ""),
            "高斯模糊" to ("THEME" to "背景遮罩"),
            "面板透明度" to ("THEME" to "面板底框"),
            "Root" to ("ADVANCE" to "Root"),
            "OCR" to ("SCREEN_ASSISTANT" to "OCR")
        )
        for ((query, expected) in checks) {
            val hits = SettingsSearchIndex.search(query)
            assertTrue("Query '$query' should return results", hits.isNotEmpty())
            assertTrue(
                "Query '$query' expected targetScreen=${expected.first} targetGroup=${expected.second}, got ${hits.take(3).map { it.id to (it.targetScreen to it.targetGroup) }}",
                hits.any {
                    it.targetScreen == expected.first &&
                        (expected.second.isEmpty() || it.targetGroup == expected.second)
                }
            )
        }

        // Pinyin initials & full spelling
        assertTrue(SettingsSearchIndex.search("xfq").any { it.targetScreen == "SB_SETTINGS" || it.targetGroup == "悬浮球" })
        assertTrue(SettingsSearchIndex.search("gsmh").any { it.targetScreen == "THEME" && it.targetGroup == "背景遮罩" })
        assertTrue(SettingsSearchIndex.search("hy").any { it.targetScreen == "EXTEND" && it.targetGroup == "护眼与滤色" })
        assertTrue(SettingsSearchIndex.search("huyan").any { it.targetScreen == "EXTEND" && it.targetGroup == "护眼与滤色" })
        assertTrue(SettingsSearchIndex.search("beifen").any { it.targetScreen == "CONFIG" })

        // Composite multi-token queries
        val sbBreath = SettingsSearchIndex.search("悬浮球 呼吸")
        assertTrue(sbBreath.any { it.targetScreen == "SB_SETTINGS" && it.targetGroup == "呼吸" })

        val translateHit = SettingsSearchIndex.search("识屏 翻译")
        assertTrue(translateHit.any { it.targetScreen == "SCREEN_ASSISTANT" && it.targetGroup == "TRANSLATE" })
    }

    // =========================================================================
    // 2. Folder non-icon child resize & popup column preservation
    // =========================================================================

    @Test
    fun folderContainingWideCardKeepsConfiguredColumnsWhileIconOnlyFolderStaysCompact() {
        val drawer = ComponentItem(id = "drawer1", type = ComponentType.APP_DRAWER_CARD, w = 32, h = 40)
        val drawerFolder = ComponentItem(
            id = "f_drawer",
            type = ComponentType.FOLDER,
            containerLayout = ContainerLayoutType.GRID,
            containerColumns = 4,
            containerComponents = listOf(drawer)
        )
        assertEquals(4, containerPopupColumns(drawerFolder))

        val keyboard = ComponentItem(id = "kbd1", type = ComponentType.APP_KEYBOARD_LAUNCHER, w = 32, h = 34)
        val keyboardFolder = drawerFolder.copy(id = "f_kbd", containerComponents = listOf(keyboard))
        assertEquals(4, containerPopupColumns(keyboardFolder))

        val iconOnlyFolder = drawerFolder.copy(
            id = "f_icon",
            containerComponents = listOf(ComponentItem(id = "ic1", type = ComponentType.APP_ICON))
        )
        assertEquals(2, containerPopupColumns(iconOnlyFolder))
    }

    @Test
    fun folderGridChildBoundsRespectsExplicitSpanHeightAndManualHeightResize() {
        val drawerDefaultSpan = ComponentItem(
            id = "drawer_span",
            type = ComponentType.APP_DRAWER_CARD,
            w = 32,
            h = 40, // default 400dp would inflate height unless gridSpanY overrides it
            gridSpanX = GRID_SPAN_FULL_ROW,
            gridSpanY = 2
        )
        val fastBounds = containerGridChildBounds(
            children = listOf(drawerDefaultSpan),
            columns = 32,
            widthDp = 360f,
            stepYDp = 10f,
            iconColumns = 4,
            itemGapDp = 8
        ).single()
        assertTrue("2-row gridSpanY drawer height=${fastBounds.height} should shrink below 300dp", fastBounds.height < 300f)
        assertTrue("Authoritative span height must stay >= 44dp", fastBounds.height >= 44f)

        // Manual height after resize (gridSpanY cleared to 0, autoHeight = false) renders exactly at h * stepYDp.
        val resizedChild = commitContainerGridChildResize(
            drawerDefaultSpan,
            w = 16,
            h = 18,
            resizeWidth = true,
            resizeHeight = true
        )
        assertEquals(16, resizedChild.w)
        assertEquals(18, resizedChild.h)
        assertEquals(0, resizedChild.gridSpanX)
        assertEquals(0, resizedChild.gridSpanY)
        assertFalse(resizedChild.autoHeight)

        val manualFixedUnitBounds = containerGridChildBounds(
            children = listOf(resizedChild),
            columns = 32,
            widthDp = 360f,
            stepYDp = 10f,
            configuredHeightUnitDp = 10,
            iconColumns = 4,
            itemGapDp = 8
        ).single()
        assertEquals(180f, manualFixedUnitBounds.height, 0.5f)

        val manualAutoUnitBounds = containerGridChildBounds(
            children = listOf(resizedChild),
            columns = 32,
            widthDp = 360f,
            stepYDp = 10f,
            configuredHeightUnitDp = 0,
            iconColumns = 4,
            itemGapDp = 8
        ).single()
        assertEquals(202.5f, manualAutoUnitBounds.height, 0.5f)

        // Explicit 2D cell placement path (gridCol >= 0 && gridRow >= 0) behaves identically.
        val cellPlacedDrawer = drawerDefaultSpan.copy(gridCol = 0, gridRow = 0, gridSpanX = 3, gridSpanY = 2)
        val cellIcon = ComponentItem(id = "ic", type = ComponentType.APP_ICON, gridCol = 3, gridRow = 0)
        val bounds2d = containerGridChildBounds(
            children = listOf(cellPlacedDrawer, cellIcon),
            columns = 32,
            widthDp = 360f,
            stepYDp = 10f,
            iconColumns = 4,
            itemGapDp = 8
        ).associateBy { it.item.id }
        val drawer2d = bounds2d.getValue("drawer_span")
        assertTrue("2D 2-row drawer height=${drawer2d.height} must be < 300dp", drawer2d.height < 300f)

        // Partial axis resize preserves the non-resized axis span.
        val widthOnly = commitContainerGridChildResize(
            drawerDefaultSpan,
            w = 24,
            h = 99,
            resizeWidth = true,
            resizeHeight = false
        )
        assertEquals(24, widthOnly.w)
        assertEquals(40, widthOnly.h)
        assertEquals(0, widthOnly.gridSpanX)
        assertEquals(2, widthOnly.gridSpanY)

        val heightOnly = commitContainerGridChildResize(
            drawerDefaultSpan,
            w = 8,
            h = 22,
            resizeWidth = false,
            resizeHeight = true
        )
        assertEquals(32, heightOnly.w)
        assertEquals(22, heightOnly.h)
        assertEquals(GRID_SPAN_FULL_ROW, heightOnly.gridSpanX)
        assertEquals(0, heightOnly.gridSpanY)
        assertFalse(heightOnly.autoHeight)
    }

    // =========================================================================
    // 3. APP_KEYBOARD_LAUNCHER spec, serialization & AppQuickSearchMatcher
    // =========================================================================

    @Test
    fun keyboardLauncherTypeSpecAndStateV5RoundTrip() {
        assertEquals(ComponentType.APP_KEYBOARD_LAUNCHER, ComponentType.fromCnum(68))
        assertEquals(4 to 4, defaultPageGridSpan(ComponentType.APP_KEYBOARD_LAUNCHER, 4))

        val spec = AppKeyboardLauncherSpec(
            layout = KeyboardCardLayout.T9,
            showNumberRow = false,
            clearAfterLaunch = false,
            autoLaunchSingleMatch = true,
            maxCandidates = 12,
            showAppLabels = false
        )
        val encoded = spec.encodeToIntentUri()
        assertTrue(encoded.startsWith(AppKeyboardLauncherSpec.URI_PREFIX))
        assertEquals(spec, AppKeyboardLauncherSpec.fromIntentUri(encoded))

        val clampedSpec = AppKeyboardLauncherSpec(maxCandidates = 99)
        assertEquals(16, AppKeyboardLauncherSpec.fromIntentUri(clampedSpec.encodeToIntentUri()).maxCandidates)
        assertEquals(AppKeyboardLauncherSpec(), AppKeyboardLauncherSpec.fromIntentUri(""))
        assertEquals(AppKeyboardLauncherSpec(), AppKeyboardLauncherSpec.fromIntentUri("invalid://uri"))

        val kbdItem = ComponentItem(
            id = "kbd_card",
            type = ComponentType.APP_KEYBOARD_LAUNCHER,
            title = "键盘快启",
            showTitle = false,
            w = 32,
            h = 34,
            intentUri = encoded
        )
        val state = GlobalMinusOneState(
            pages = listOf(
                PageConfig(
                    id = "page1",
                    name = "测试页",
                    layoutType = PageLayoutType.GRID,
                    components = listOf(kbdItem)
                )
            )
        )
        val json = Json { ignoreUnknownKeys = true }
        val payload = json.encodeToString(GlobalMinusOneState.serializer(), state)
        val restored = json.decodeFromString(GlobalMinusOneState.serializer(), payload)
        val restoredItem = restored.pages.single().components.single()
        assertEquals(ComponentType.APP_KEYBOARD_LAUNCHER, restoredItem.type)
        assertEquals(spec, AppKeyboardLauncherSpec.fromComponent(restoredItem))
    }

    @Test
    fun quickSearchMatcherSupportsInitialsPinyinPolyphonesDigitsCamelCaseAndT9() {
        data class SampleApp(val pkg: String, val name: String)
        val sampleApps = listOf(
            SampleApp("com.tencent.mm", "微信"),
            SampleApp("com.eg.android.AlipayGphone", "支付宝"),
            SampleApp("com.netease.cloudmusic", "网易云音乐"),
            SampleApp("com.ccb.bank", "建设银行"),
            SampleApp("com.autonavi.minimap", "高德地图"),
            SampleApp("tv.danmaku.bili", "哔哩哔哩"),
            SampleApp("com.MobileTicket", "铁路12306"),
            SampleApp("com.qihoo360.mobilesafe", "360安全卫士"),
            SampleApp("com.vscode.app", "VSCode"),
            SampleApp("com.adobe.ps", "PhotoShop"),
            SampleApp("com.github.android", "GitHub"),
            SampleApp("com.wps.moffice_eng", "WPS Office")
        )
        val indexed = AppQuickSearchMatcher.indexApps(
            apps = sampleApps,
            label = { it.name },
            packageName = { it.pkg },
            provider = PinyinIndexProvider()
        )

        // Initials
        assertEquals("com.tencent.mm", AppQuickSearchMatcher.matchApps(indexed, "wx").first().pkg)
        assertEquals("com.eg.android.AlipayGphone", AppQuickSearchMatcher.matchApps(indexed, "zfb").first().pkg)
        assertEquals("com.netease.cloudmusic", AppQuickSearchMatcher.matchApps(indexed, "wy").first().pkg)

        // Full Pinyin & mixed syllable prefix
        assertEquals("com.tencent.mm", AppQuickSearchMatcher.matchApps(indexed, "weixin").first().pkg)
        assertEquals("com.autonavi.minimap", AppQuickSearchMatcher.matchApps(indexed, "gaode").first().pkg)
        assertEquals("com.tencent.mm", AppQuickSearchMatcher.matchApps(indexed, "weix").first().pkg)
        assertEquals("com.eg.android.AlipayGphone", AppQuickSearchMatcher.matchApps(indexed, "zhifb").first().pkg)

        // Polyphones: 乐 (yue/le), 行 (hang/xing)
        assertTrue(AppQuickSearchMatcher.matchApps(indexed, "wyy").any { it.pkg == "com.netease.cloudmusic" })
        assertTrue(AppQuickSearchMatcher.matchApps(indexed, "wyl").any { it.pkg == "com.netease.cloudmusic" })
        assertEquals("com.ccb.bank", AppQuickSearchMatcher.matchApps(indexed, "jsyh").first().pkg)

        // Digits & numbers
        assertEquals("com.MobileTicket", AppQuickSearchMatcher.matchApps(indexed, "12306").first().pkg)
        assertEquals("com.qihoo360.mobilesafe", AppQuickSearchMatcher.matchApps(indexed, "360").first().pkg)

        // CamelCase & abbreviation
        assertTrue(AppQuickSearchMatcher.matchApps(indexed, "vs").any { it.pkg == "com.vscode.app" })
        assertEquals("com.vscode.app", AppQuickSearchMatcher.matchApps(indexed, "vsc").first().pkg)
        assertTrue(AppQuickSearchMatcher.matchApps(indexed, "ps").any { it.pkg == "com.adobe.ps" })
        assertEquals("com.github.android", AppQuickSearchMatcher.matchApps(indexed, "gh").first().pkg)
        assertEquals("com.wps.moffice_eng", AppQuickSearchMatcher.matchApps(indexed, "wps").first().pkg)

        // T9 numeric mode
        assertTrue(AppQuickSearchMatcher.matchApps(indexed, "99", t9Mode = true).any { it.pkg == "com.tencent.mm" })
        assertEquals("com.tencent.mm", AppQuickSearchMatcher.matchApps(indexed, "934946", t9Mode = true).first().pkg)
        assertEquals("tv.danmaku.bili", AppQuickSearchMatcher.matchApps(indexed, "2454", t9Mode = true).first().pkg)

        // Next reachable keys after "w" (微信 -> x/e, 网易 -> y/a, WPS -> p)
        val nextKeys = AppQuickSearchMatcher.nextReachableKeys(
            indexed = indexed,
            currentQuery = "w",
            candidateKeys = ('a'..'z').toList() + ('0'..'9').toList(),
            t9Mode = false
        )
        assertTrue('x' in nextKeys)
        assertTrue('y' in nextKeys)
        assertFalse('q' in nextKeys)

        // Recency tie-breaker promotes recently launched app to #1 when query is empty
        val emptyWithRecency = AppQuickSearchMatcher.matchApps(
            indexed = indexed,
            rawQuery = "",
            recentPackages = listOf("com.github.android", "com.tencent.mm")
        )
        assertEquals("com.github.android", emptyWithRecency.first().pkg)
        assertEquals("com.tencent.mm", emptyWithRecency[1].pkg)
    }
}
