package com.example.globalminusone

import androidx.compose.ui.graphics.Color
import com.example.globalminusone.data.ComponentItem
import com.example.globalminusone.data.ComponentType
import com.example.globalminusone.data.ThemeConfig
import com.example.globalminusone.ui.panel.cards.resolveCardPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardColorPaletteTest {
    private val component = ComponentItem(type = ComponentType.NOTE_CARD)

    @Test
    fun darkTheme_keepsLightTextAndSeparatesTextFromTitleOpacity() {
        val palette = resolveCardPalette(
            component,
            ThemeConfig(
                isDarkMode = true,
                defaultCardBgColorHex = 0xFF14171D,
                defaultTextColorHex = 0xFFFFFFFF,
                textAlphaPercent = 60,
                titleColorHex = 0xFF88BBFF,
                titleAlphaPercent = 35
            )
        )

        assertColor(Color.White, palette.textPrimary, 0.60f)
        assertColor(Color(0xFFD0D5DD), palette.textSecondary, 0.60f * 0.82f)
        assertColor(Color(0xFF88BBFF), palette.titleColor, 0.35f)
        assertColor(Color.White, palette.iconBackground, 0.14f)
        assertColor(Color.White, palette.trackColor, 0.20f)
    }

    @Test
    fun componentOverrides_replaceGlobalTextColorOpacityAndSurfaceTone() {
        val palette = resolveCardPalette(
            component.copy(
                bgColorHex = 0xFF171A20,
                textColorHex = 0xFF66CC99,
                textAlphaPercent = 40
            ),
            ThemeConfig(
                defaultCardBgColorHex = 0xFFFFFFFF,
                defaultTextColorHex = 0xFF1F2329,
                textAlphaPercent = 90,
                titleColorHex = 0xFF2196F3,
                titleAlphaPercent = 80
            )
        )

        val text = Color(0xFF66CC99)
        assertColor(text, palette.textPrimary, 0.40f)
        assertColor(text, palette.titleColor, 0.40f)
        assertColor(text, palette.textSecondary, 0.40f * 0.82f)
        assertColor(text, palette.textPlaceholder, 0.40f * 0.60f)
        assertColor(Color.White, palette.trackColor, 0.20f)
    }

    @Test
    fun followingGlobalSettings_usesLightSurfaceAndConfiguredAccent() {
        val palette = resolveCardPalette(
            component.copy(bgAlphaPercent = -1, textAlphaPercent = -1),
            ThemeConfig(
                cardBgAlphaPercent = 70,
                textAlphaPercent = 50,
                titleAlphaPercent = 75,
                accentColorHex = 0xFFAA55CC
            )
        )

        assertColor(Color(0xFF1F2329), palette.textPrimary, 0.50f)
        assertColor(Color(0xFF1F2329), palette.titleColor, 0.75f)
        assertColor(Color(0xFFF2F3F5), palette.iconBackground, 0.70f)
        assertColor(Color(0xFFE5E6EB), palette.trackColor, 0.70f)
        assertColor(Color(0xFFAA55CC), palette.accent, 1f)
    }

    @Test
    fun transparentCard_usesUnderlyingDarkModeForReadableTracks() {
        val palette = resolveCardPalette(
            component.copy(bgColorHex = 0xFFFFFFFF, bgAlphaPercent = 0),
            ThemeConfig(isDarkMode = true, defaultTextColorHex = 0xFFFFFFFF)
        )

        assertTrue(palette.textPrimary.red > 0.9f)
        assertColor(Color.White, palette.iconBackground, 0.14f)
        assertColor(Color.White, palette.trackColor, 0.20f)
    }

    private fun assertColor(expectedRgb: Color, actual: Color, expectedAlpha: Float) {
        assertEquals(expectedRgb.red, actual.red, 0.005f)
        assertEquals(expectedRgb.green, actual.green, 0.005f)
        assertEquals(expectedRgb.blue, actual.blue, 0.005f)
        assertEquals(expectedAlpha, actual.alpha, 0.005f)
    }
}
