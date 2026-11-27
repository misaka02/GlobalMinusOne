package com.example.globalminusone

import com.example.globalminusone.data.AdvancedConfig
import com.example.globalminusone.data.ComponentCategory
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ConfigProfile
import com.example.globalminusone.data.policy.DefaultPresets
import com.example.globalminusone.data.GestureAction
import com.example.globalminusone.data.GlobalMinusOneState
import com.example.globalminusone.data.PageConfig
import com.example.globalminusone.data.PageLayoutType
import com.example.globalminusone.data.PageTransition
import com.example.globalminusone.data.ShowSideMode
import com.example.globalminusone.data.SuspensionBallConfig
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.data.TriggerConfig
import com.example.globalminusone.data.TriggerPosition
import com.example.globalminusone.data.TriggerWay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MinusOneModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun oldContainersKeepAutomaticHeightAndManualHeightSurvivesSerialization() {
        val oldContainer = json.decodeFromString<ComponentItem>("""{"id":"old","type":"CONTAINER_BOX"}""")
        assertTrue(oldContainer.autoHeight)
        assertFalse(json.decodeFromString<ComponentItem>("""{"type":"NOTE_CARD"}""").autoHeight)
        val manual = oldContainer.copy(h = 18, autoHeight = false)
        assertEquals(manual, json.decodeFromString<ComponentItem>(json.encodeToString(manual)))
    }

    @Test
    fun defaultState_hasExpectedPagesAndComponents() {
        val state = GlobalMinusOneState()
        assertEquals("Default should provide exactly 2 pages", 2, state.pages.size)

        // 第 1 页：线性布局 (4 个容器卡片: 应用/快捷方式/支付/网站 + 1 个底部操作栏卡片)
        val page1 = state.pages[0]
        assertEquals("Page 1 name should be 线性布局", "线性布局", page1.name)
        assertEquals("Page 1 should be LINEAR layout", PageLayoutType.LINEAR, page1.layoutType)
        assertEquals("Page 1 should have 5 components", 5, page1.components.size)

        val p1Types = page1.components.map { it.type }
        assertEquals(4, p1Types.count { it == ComponentType.CONTAINER_BOX })
        assertTrue(p1Types.contains(ComponentType.OPERATION_BAR))

        // 验证第 1 页各容器标题及操作栏卡片
        val p1Titles = page1.components.map { it.title }
        assertTrue(p1Titles.contains("应用"))
        assertTrue(p1Titles.contains("快捷方式"))
        assertTrue(p1Titles.contains("支付"))
        assertTrue(p1Titles.contains("网站"))
        assertTrue(p1Titles.contains("操作栏"))

        // 第 2 页：自由布局 (32×48 二维不对称网格画布, 12 个组件)
        val page2 = state.pages[1]
        assertEquals("Page 2 name should be 自由布局", "自由布局", page2.name)
        assertEquals("Page 2 should be FREE layout", PageLayoutType.FREE, page2.layoutType)
        assertEquals("Page 2 should have 12 components", 12, page2.components.size)

        val p2Types = page2.components.map { it.type }
        assertTrue(p2Types.contains(ComponentType.HITOKOTO_CARD))
        assertTrue(p2Types.contains(ComponentType.STAT_BATTERY_BAR))
        assertTrue(p2Types.contains(ComponentType.SWITCH_WIFI))
        assertTrue(p2Types.contains(ComponentType.SWITCH_BLUETOOTH))
        assertTrue(p2Types.contains(ComponentType.SWITCH_TORCH))
        assertTrue(p2Types.contains(ComponentType.ACTION_LOCK))
        assertTrue(p2Types.contains(ComponentType.NOTE_CARD))
        assertTrue(p2Types.contains(ComponentType.STAT_RAM))
        assertTrue(p2Types.contains(ComponentType.STAT_ROM))
        assertTrue(p2Types.contains(ComponentType.BRIGHTNESS_BAR))
        assertTrue(p2Types.contains(ComponentType.VOLUME_BAR))
        assertTrue(p2Types.contains(ComponentType.OPERATION_BAR))

        // 验证第 2 页坐标排布
        val hitokoto = page2.components.first { it.type == ComponentType.HITOKOTO_CARD }
        assertEquals(1, hitokoto.x)
        assertEquals(2, hitokoto.y)
        assertEquals(17, hitokoto.w)
        assertEquals(8, hitokoto.h)

        val battery = page2.components.first { it.type == ComponentType.STAT_BATTERY_BAR }
        assertEquals(19, battery.x)
        assertEquals(2, battery.y)
        assertEquals(12, battery.w)
        assertEquals(8, battery.h)

        val brightness = page2.components.first { it.type == ComponentType.BRIGHTNESS_BAR }
        assertEquals(1, brightness.x)
        assertEquals(32, brightness.y)
        assertEquals(30, brightness.w)
        assertEquals(5, brightness.h)

        val volume = page2.components.first { it.type == ComponentType.VOLUME_BAR }
        assertEquals(1, volume.x)
        assertEquals(38, volume.y)
        assertEquals(30, volume.w)
        assertEquals(5, volume.h)

        val opBar2 = page2.components.first { it.type == ComponentType.OPERATION_BAR }
        assertEquals(1, opBar2.x)
        assertEquals(44, opBar2.y)
        assertEquals(30, opBar2.w)
        assertEquals(4, opBar2.h)

        // 快捷方式预设库验证
        assertTrue("Built-in shortcuts should contain preset actions", DefaultPresets.builtInShortcuts.size >= 15)
        val titles = DefaultPresets.builtInShortcuts.map { it.title }
        assertTrue(titles.contains("支付宝扫一扫"))
        assertTrue(titles.contains("支付宝付款码"))
        assertTrue(titles.contains("支付宝乘车码"))
        assertTrue(titles.contains("微信扫一扫"))
        assertTrue(titles.contains("酷安"))
        assertTrue(titles.contains("微博热搜"))
        assertTrue(titles.contains("每日推荐"))
        assertTrue(titles.contains("系统设置"))
    }

    @Test
    fun pageManagement_addDeleteSwap_logicCorrect() {
        val baseState = GlobalMinusOneState()
        assertEquals(2, baseState.pages.size)

        // 模拟新建一页
        val newPage = PageConfig(
            id = "test_page_3",
            name = "新建页面 3",
            layoutType = PageLayoutType.LINEAR,
            components = listOf(
                ComponentItem(type = ComponentType.OPERATION_BAR)
            )
        )
        val stateAfterAdd = baseState.copy(pages = baseState.pages + newPage, currentPageIndex = 2)
        assertEquals(3, stateAfterAdd.pages.size)
        assertEquals("新建页面 3", stateAfterAdd.pages[2].name)
        assertEquals(2, stateAfterAdd.currentPageIndex)

        // 交换左页 (下标 2 与 下标 1 交换)
        val mutablePages = stateAfterAdd.pages.toMutableList()
        val temp = mutablePages[2]
        mutablePages[2] = mutablePages[1]
        mutablePages[1] = temp
        val stateAfterSwap = stateAfterAdd.copy(pages = mutablePages, currentPageIndex = 1)

        assertEquals("新建页面 3", stateAfterSwap.pages[1].name)
        assertEquals("自由布局", stateAfterSwap.pages[2].name)
        assertEquals(1, stateAfterSwap.currentPageIndex)

        // 删除页面 (删除下标 1)
        val stateAfterDelete = stateAfterSwap.copy(
            pages = stateAfterSwap.pages.filterIndexed { index, _ -> index != 1 },
            currentPageIndex = 1
        )
        assertEquals(2, stateAfterDelete.pages.size)
        assertEquals("线性布局", stateAfterDelete.pages[0].name)
        assertEquals("自由布局", stateAfterDelete.pages[1].name)

        // 验证至少保留 1 页保护
        val singlePageState = stateAfterDelete.copy(pages = listOf(stateAfterDelete.pages[0]))
        assertEquals(1, singlePageState.pages.size)
        val attemptedDelete = if (singlePageState.pages.size > 1) {
            singlePageState.copy(pages = emptyList())
        } else {
            singlePageState
        }
        assertEquals(1, attemptedDelete.pages.size)
    }

    @Test
    fun freeLayout_coordinateUpdates_andStyleCustomization() {
        val initialComp = ComponentItem(
            id = "comp_test_1",
            type = ComponentType.SWITCH_TORCH,
            x = 0,
            y = 10,
            w = 8,
            h = 6,
            bgColorHex = null,
            cornerRadiusDp = -1,
            rotationDeg = 0
        )

        // 模拟自由拖动更新坐标与尺寸
        val draggedComp = initialComp.copy(
            x = 16,
            y = 20,
            w = 12,
            h = 8,
            bgColorHex = 0xFF334455,
            textColorHex = 0xFFFFFFFF,
            cornerRadiusDp = 16,
            rotationDeg = 45,
            paddingTopDp = 10,
            paddingBottomDp = 10,
            paddingLeftDp = 12,
            paddingRightDp = 12,
            marginTopDp = 4,
            marginBottomDp = 4,
            marginLeftDp = 4,
            marginRightDp = 4
        )

        assertEquals(16, draggedComp.x)
        assertEquals(20, draggedComp.y)
        assertEquals(12, draggedComp.w)
        assertEquals(8, draggedComp.h)
        assertEquals(0xFF334455L, draggedComp.bgColorHex)
        assertEquals(16, draggedComp.cornerRadiusDp)
        assertEquals(45, draggedComp.rotationDeg)
        assertEquals(10, draggedComp.paddingTopDp)
        assertEquals(4, draggedComp.marginTopDp)

        // 重置样式
        val resetComp = draggedComp.copy(
            bgColorHex = null,
            textColorHex = null,
            cornerRadiusDp = -1,
            rotationDeg = 0,
            paddingTopDp = 6,
            paddingBottomDp = 6,
            paddingLeftDp = 8,
            paddingRightDp = 8,
            marginTopDp = 2,
            marginBottomDp = 2,
            marginLeftDp = 2,
            marginRightDp = 2
        )

        assertNull(resetComp.bgColorHex)
        assertNull(resetComp.textColorHex)
        assertEquals(-1, resetComp.cornerRadiusDp)
        assertEquals(0, resetComp.rotationDeg)
        assertEquals(6, resetComp.paddingTopDp)
        assertEquals(2, resetComp.marginTopDp)
        // 坐标应保持拖拽后的位置
        assertEquals(16, resetComp.x)
        assertEquals(20, resetComp.y)
    }

    @Test
    fun gestureActionBinding_andSerialization_preservesAllConfigs() {
        val customState = GlobalMinusOneState(
            triggerConfig = TriggerConfig(
                serviceEnabled = true,
                triggerWay = TriggerWay.SECOND_SLIDE,
                triggerPosition = TriggerPosition.BOTH,
                defaultShowSide = ShowSideMode.RIGHT,
                touchWidthPercent = 5,
                touchHeightPercent = 20,
                touchOffsetPercent = 65,
                touchVisible = false,
                touchAlpha = 0.6f,
                touchColorHex = 0xFFE0E0E0,
                vibrate = true,
                avoidInputMethod = true,
                singleTapAction = GestureAction.BACK,
                doubleTapAction = GestureAction.SCREENSHOT,
                longPressAction = GestureAction.LOCK,
                swipeUpAction = GestureAction.HOME,
                swipeDownAction = GestureAction.NOTIFICATION,
                swipeLeftAction = GestureAction.RIGHT_OPEN_NOS,
                swipeRightAction = GestureAction.LEFT_OPEN_NOS
            ),
            suspensionBallConfig = SuspensionBallConfig(
                radiusDp = 26,
                alpha = 0.8f,
                breatheMode = true,
                autoFadeSeconds = 5,
                mouseMode = true,
                posXPercent = 0.85f,
                posYPercent = 0.50f,
                singleTapAction = GestureAction.LEFT_OPEN_NOS,
                doubleTapAction = GestureAction.RECENT,
                longPressAction = GestureAction.POWER,
                swipeUpAction = GestureAction.HOME,
                swipeDownAction = GestureAction.QUICK_SETTINGS,
                swipeLeftAction = GestureAction.LAST_APP,
                swipeRightAction = GestureAction.RIGHT_OPEN_NOS
            ),
            themeConfig = ThemeConfig(
                panelWidthPercent = 82,
                edgeMarginPercent = 3,
                maskAlphaPercent = 50,
                pageTransition = PageTransition.OUT_CUBE,
                animationDurationMs = 300,
                coverStatusBar = true,
                backgroundBlurRadius = 30,
                cardBackground = true,
                cardCornerRadiusDp = 16,
                cardSpacingDp = 10,
                panelBgColorHex = 0xE61A1D24,
                defaultCardBgColorHex = 0xCC252932,
                defaultTextColorHex = 0xFFF0F2F5,
                accentColorHex = 0xFF1976D2
            ),
            advancedConfig = AdvancedConfig(
                gridColumns = 32,
                heightUnitDp = 0,
                autoCloseInLandscape = true,
                autoCloseOnWidgetClick = true,
                autoStartOnBoot = true,
                blacklistPackages = setOf("com.tencent.tmgp.sgame"),
                eyeProtectionEnabled = true,
                eyeProtectionColorHex = 0x33FFB100,
                autoCopyVerificationCode = true
            )
        )

        val serialized = json.encodeToString(customState)
        val deserialized = json.decodeFromString<GlobalMinusOneState>(serialized)

        // 验证 TriggerConfig
        assertEquals(TriggerWay.SECOND_SLIDE, deserialized.triggerConfig.triggerWay)
        assertEquals(TriggerPosition.BOTH, deserialized.triggerConfig.triggerPosition)
        assertEquals(ShowSideMode.RIGHT, deserialized.triggerConfig.defaultShowSide)
        assertEquals(5, deserialized.triggerConfig.touchWidthPercent)
        assertEquals(20, deserialized.triggerConfig.touchHeightPercent)
        assertEquals(65, deserialized.triggerConfig.touchOffsetPercent)
        assertFalse(deserialized.triggerConfig.touchVisible)
        assertEquals(GestureAction.BACK, deserialized.triggerConfig.singleTapAction)
        assertEquals(GestureAction.SCREENSHOT, deserialized.triggerConfig.doubleTapAction)
        assertEquals(GestureAction.LOCK, deserialized.triggerConfig.longPressAction)
        assertEquals(GestureAction.HOME, deserialized.triggerConfig.swipeUpAction)
        assertEquals(GestureAction.NOTIFICATION, deserialized.triggerConfig.swipeDownAction)

        // 验证 SuspensionBallConfig
        assertEquals(26, deserialized.suspensionBallConfig.radiusDp)
        assertTrue(deserialized.suspensionBallConfig.breatheMode)
        assertTrue(deserialized.suspensionBallConfig.mouseMode)
        assertEquals(5, deserialized.suspensionBallConfig.autoFadeSeconds)
        assertEquals(GestureAction.POWER, deserialized.suspensionBallConfig.longPressAction)
        assertEquals(GestureAction.LAST_APP, deserialized.suspensionBallConfig.swipeLeftAction)

        // 验证 ThemeConfig
        assertEquals(82, deserialized.themeConfig.panelWidthPercent)
        assertEquals(PageTransition.OUT_CUBE, deserialized.themeConfig.pageTransition)
        assertEquals(300, deserialized.themeConfig.animationDurationMs)
        assertEquals(16, deserialized.themeConfig.cardCornerRadiusDp)

        // 验证 AdvancedConfig
        assertEquals(32, deserialized.advancedConfig.gridColumns)
        assertTrue(deserialized.advancedConfig.blacklistPackages.contains("com.tencent.tmgp.sgame"))
        assertTrue(deserialized.advancedConfig.eyeProtectionEnabled)
        assertTrue(deserialized.advancedConfig.autoCopyVerificationCode)
    }

    @Test
    fun triggerConfig_tempDisabledUntil_worksCorrectly() {
        val now = 1000000L
        val activeCfg = TriggerConfig(tempDisabledUntilMs = 0L)
        assertFalse("Should not be temp disabled when 0", activeCfg.isTempDisabled(now))

        val disabledCfg = TriggerConfig(tempDisabledUntilMs = now + 60000L)
        assertTrue("Should be temp disabled when within duration", disabledCfg.isTempDisabled(now))
        assertFalse("Should not be temp disabled after duration passed", disabledCfg.isTempDisabled(now + 60001L))
    }

    @Test
    fun componentType_fromCnum_resolvesCorrectly() {
        assertEquals(ComponentType.SWITCH_WIFI, ComponentType.fromCnum(18))
        assertEquals(ComponentType.SWITCH_TORCH, ComponentType.fromCnum(15))
        assertEquals(ComponentType.ACTION_LOCK, ComponentType.fromCnum(19))
        assertEquals(ComponentType.APP_ICON, ComponentType.fromCnum(42))
        assertEquals(ComponentType.CONTAINER_BOX, ComponentType.fromCnum(48))
        assertEquals(ComponentType.APP_WIDGET, ComponentType.fromCnum(44))
        assertEquals(ComponentType.BRIGHTNESS_BAR, ComponentType.fromCnum(46))
        assertEquals(ComponentType.VOLUME_BAR, ComponentType.fromCnum(58))
        assertEquals(ComponentType.STAT_BATTERY_BAR, ComponentType.fromCnum(34))
        assertEquals(ComponentType.HITOKOTO_CARD, ComponentType.fromCnum(53))
        assertEquals(ComponentType.NOTE_CARD, ComponentType.fromCnum(52))
        assertEquals(ComponentType.OPERATION_BAR, ComponentType.fromCnum(60))
        assertNull(ComponentType.fromCnum(999))
    }

    @Test
    fun newSettingsAndOverlayFields_preserveBackwardCompatibilityAndRoundTrip() {
        // Old JSON without new fields decodes with safe defaults
        val legacyDecoded = json.decodeFromString<GlobalMinusOneState>("""{"currentPageIndex":0}""")
        assertFalse(legacyDecoded.advancedConfig.settingsFollowSystemTheme)
        assertFalse(legacyDecoded.themeConfig.followSystemDark)
        assertFalse(legacyDecoded.suspensionBallConfig.snapToEdge)
        assertFalse(legacyDecoded.suspensionBallConfig.showIcon)
        assertEquals(2, legacyDecoded.suspensionBallConfig.breatheFrequencySeconds)
        assertEquals(1200, legacyDecoded.triggerConfig.secondSlideTimeoutMs)
        assertFalse(legacyDecoded.triggerConfig.useSystemVibrate)

        // Modified state round-trips all newly added persistent fields
        val updated = legacyDecoded.copy(
            triggerConfig = legacyDecoded.triggerConfig.copy(useSystemVibrate = true, secondSlideTimeoutMs = 1800),
            suspensionBallConfig = legacyDecoded.suspensionBallConfig.copy(
                snapToEdge = true, showIcon = true, breatheFrequencySeconds = 4, mouseSpeed = "快", mouseRange = 65
            ),
            themeConfig = legacyDecoded.themeConfig.copy(followSystemDark = true),
            advancedConfig = legacyDecoded.advancedConfig.copy(
                settingsFollowSystemTheme = true, rootModeEnabled = true, autoJumpAccessibility = false, highestOverlayLevel = true
            )
        )
        val redecoded = json.decodeFromString<GlobalMinusOneState>(json.encodeToString(updated))
        assertTrue(redecoded.triggerConfig.useSystemVibrate)
        assertEquals(1800, redecoded.triggerConfig.secondSlideTimeoutMs)
        assertTrue(redecoded.suspensionBallConfig.snapToEdge)
        assertTrue(redecoded.suspensionBallConfig.showIcon)
        assertEquals(4, redecoded.suspensionBallConfig.breatheFrequencySeconds)
        assertEquals("快", redecoded.suspensionBallConfig.mouseSpeed)
        assertEquals(65, redecoded.suspensionBallConfig.mouseRange)
        assertTrue(redecoded.themeConfig.followSystemDark)
        assertTrue(redecoded.advancedConfig.settingsFollowSystemTheme)
        assertTrue(redecoded.advancedConfig.rootModeEnabled)
        assertFalse(redecoded.advancedConfig.autoJumpAccessibility)
        assertTrue(redecoded.advancedConfig.highestOverlayLevel)
    }
}
