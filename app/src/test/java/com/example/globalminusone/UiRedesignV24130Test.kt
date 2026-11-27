package com.example.globalminusone

import com.example.globalminusone.data.ComponentOrientation
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.policy.LauncherCardPresentationMode
import com.example.globalminusone.data.policy.parseCardPresentationMode
import com.example.globalminusone.ui.panel.editor.SliderCardStyle
import com.example.globalminusone.ui.panel.editor.buildSliderComponentItem
import com.example.globalminusone.ui.panel.editor.normalizeThemeAppearanceTab
import com.example.globalminusone.ui.panel.editor.resolveInitialComponentEditTab
import com.example.globalminusone.ui.settings.toggleExpandedGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRedesignV24130Test {
    @Test
    fun toggleExpandedGroup_supportsMultiExpandAndCollapse() {
        val step1 = toggleExpandedGroup(emptySet(), "触发")
        val step2 = toggleExpandedGroup(step1, "位置")
        assertTrue("触发" in step2)
        assertTrue("位置" in step2)

        val step3 = toggleExpandedGroup(step2, "触发")
        assertFalse("触发" in step3)
        assertTrue("位置" in step3)
    }

    @Test
    fun resolveInitialComponentEditTab_flattensEmptyToContentAndMapsLegacySpacing() {
        assertEquals("内容", resolveInitialComponentEditTab(""))
        assertEquals("内容", resolveInitialComponentEditTab("内容"))
        assertEquals("位置与尺寸", resolveInitialComponentEditTab("间距"))
        assertEquals("文件夹窗口", resolveInitialComponentEditTab("文件夹窗口"))
    }

    @Test
    fun normalizeThemeAppearanceTab_consolidatesSixLegacyTabsToFour() {
        assertEquals("面板", normalizeThemeAppearanceTab(""))
        assertEquals("面板", normalizeThemeAppearanceTab("面板尺寸与布局"))
        assertEquals("面板", normalizeThemeAppearanceTab("手势与行为"))
        assertEquals("背景", normalizeThemeAppearanceTab("主题与背景"))
        assertEquals("卡片", normalizeThemeAppearanceTab("卡片外观"))
        assertEquals("卡片", normalizeThemeAppearanceTab("文字与标题"))
        assertEquals("动效", normalizeThemeAppearanceTab("转场动画"))
    }

    @Test
    fun buildSliderComponentItem_coversHorizontalVerticalAndPopupIconForVolumeAndBrightness() {
        val hVol = buildSliderComponentItem(isVolume = true, SliderCardStyle.HORIZONTAL)
        assertEquals(ComponentType.VOLUME_BAR, hVol.type)
        assertEquals(ComponentOrientation.HORIZONTAL, hVol.orientation)

        val vBright = buildSliderComponentItem(isVolume = false, SliderCardStyle.VERTICAL)
        assertEquals(ComponentType.BRIGHTNESS_BAR, vBright.type)
        assertEquals(ComponentOrientation.VERTICAL, vBright.orientation)
        assertEquals(1, vBright.gridSpanX)
        assertEquals(3, vBright.gridSpanY)

        val pVol = buildSliderComponentItem(isVolume = true, SliderCardStyle.POPUP_ICON)
        assertEquals(ComponentType.VOLUME_BAR, pVol.type)
        assertEquals(LauncherCardPresentationMode.POPUP_ICON, parseCardPresentationMode(pVol))
    }
}
