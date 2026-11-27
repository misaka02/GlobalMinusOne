package com.example.globalminusone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.globalminusone.data.AppAppearanceOverride
import com.example.globalminusone.data.AppearanceConfig
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.QuickActionItem
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.ui.panel.calculatePanelPalette
import com.example.globalminusone.ui.panel.cards.resolveAppContextRestoreVisibility
import com.example.globalminusone.ui.panel.editor.ComponentEditDraft
import com.example.globalminusone.ui.panel.panelOverlayTokens
import com.example.globalminusone.ui.settings.PermissionVisualKind
import com.example.globalminusone.ui.settings.resolvePermissionVisualKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRedesignV24129Test {
    @Test
    fun componentEditDraft_preservesInheritedRowGapSentinel() {
        val original = ComponentItem(
            id = "folder-1",
            type = ComponentType.FOLDER,
            title = "工具夹",
            containerItemGapDp = 10,
            containerRowGapDp = -1
        )
        val draft = ComponentEditDraft(original)
        val unedited = draft.applyTo(original)
        assertEquals(original, unedited)
        assertEquals(-1, unedited.containerRowGapDp)

        draft.rowGap = 16
        assertEquals(16, draft.applyTo(original).containerRowGapDp)
    }
    @Test
    fun panelOverlayTokens_keepOpaqueReadableSurfacesInLightAndDark() {
        val lightPalette = calculatePanelPalette(
            ThemeConfig(defaultCardBgColorHex = 0xFFFFFFFFL),
            AppearanceConfig()
        )
        val darkPalette = calculatePanelPalette(
            ThemeConfig(defaultCardBgColorHex = 0xFF1E222AL),
            AppearanceConfig()
        )

        listOf(lightPalette, darkPalette).forEach { palette ->
            val tokens = panelOverlayTokens(palette)
            assertEquals(1f, tokens.sheetBg.alpha, 0f)
            assertEquals(1f, tokens.elevatedSurface.alpha, 0f)
            assertEquals(1f, tokens.cardBg.alpha, 0f)
            assertTrue(contrast(tokens.textPrimary, tokens.sheetBg) >= 7f)
            assertTrue(contrast(tokens.textPrimary, tokens.elevatedSurface) >= 7f)
            assertTrue(contrast(tokens.textSecondary, tokens.cardBg) >= 4.5f)
        }
    }

    @Test
    fun resolveAppContextRestoreVisibility_hidesDefaultsAndShowsCustomOverrides() {
        val defaultApp = resolveAppContextRestoreVisibility(null, null, null)
        assertFalse(defaultApp.first)
        assertFalse(defaultApp.second)

        val renamedApp = resolveAppContextRestoreVisibility(
            card = null,
            legacyAction = null,
            appearance = AppAppearanceOverride(displayName = "别名", iconUri = "")
        )
        assertFalse(renamedApp.first)
        assertTrue(renamedApp.second)

        val customIconCard = resolveAppContextRestoreVisibility(
            card = ComponentItem(type = ComponentType.APP_ICON, iconUri = "content://icon"),
            legacyAction = null,
            appearance = null
        )
        assertTrue(customIconCard.first)
        assertFalse(customIconCard.second)

        val legacyDefault = resolveAppContextRestoreVisibility(
            card = null,
            legacyAction = QuickActionItem(id = "a1", title = "测试", packageName = "pkg", intentUri = "", iconName = "bolt"),
            appearance = null
        )
        assertFalse(legacyDefault.first)

        val legacyCustom = resolveAppContextRestoreVisibility(
            card = null,
            legacyAction = QuickActionItem(id = "a1", title = "测试", packageName = "pkg", intentUri = "", iconName = "custom:explore"),
            appearance = null
        )
        assertTrue(legacyCustom.first)
    }

    @Test
    fun resolvePermissionVisualKind_preservesCleanTitlesWithoutGuillemets() {
        assertEquals(PermissionVisualKind.WARNING, resolvePermissionVisualKind("辅助(需重开)", granted = false))
        assertEquals(PermissionVisualKind.SYSTEM_PAGE, resolvePermissionVisualKind("设备管理（锁屏备用）", granted = false))
        assertEquals(PermissionVisualKind.SYSTEM_PAGE, resolvePermissionVisualKind("后台弹出界面（ColorOS）", granted = true))
        assertEquals(PermissionVisualKind.GRANTED, resolvePermissionVisualKind("辅助服务", granted = true))
        assertEquals(PermissionVisualKind.MISSING, resolvePermissionVisualKind("悬浮窗", granted = false))
    }

    private fun contrast(fg: Color, bg: Color): Float {
        val l1 = maxOf(fg.luminance(), bg.luminance())
        val l2 = minOf(fg.luminance(), bg.luminance())
        return (l1 + 0.05f) / (l2 + 0.05f)
    }
}
