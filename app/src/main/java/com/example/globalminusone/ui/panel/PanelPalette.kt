package com.example.globalminusone.ui.panel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.globalminusone.data.AppearanceConfig
import com.example.globalminusone.data.ThemeConfig

@Immutable
data class PanelPalette(
    val panelBackgroundBrush: Brush,
    val cardBackground: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val isDark: Boolean,
    val themeConfig: ThemeConfig = ThemeConfig()
)

val LocalPanelPalette = staticCompositionLocalOf<PanelPalette> {
    error("LocalPanelPalette not provided")
}

private fun Color.calcLuminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

fun calculatePanelPalette(theme: ThemeConfig, appearance: AppearanceConfig): PanelPalette {
    val accent = try {
        Color((theme.accentColorHex and 0x00FFFFFFL) or 0xFF000000L)
    } catch (_: Exception) {
        Color(0xFF2196F3)
    }

    val baseCardRgb = Color((theme.defaultCardBgColorHex and 0x00FFFFFFL) or 0xFF000000L)
    val cardAlpha = (theme.cardBgAlphaPercent / 100f).coerceIn(0f, 1f)
    val cardBackground = baseCardRgb.copy(alpha = cardAlpha)

    val baseTextRgb = Color((theme.defaultTextColorHex and 0x00FFFFFFL) or 0xFF000000L)
    val textAlpha = (theme.textAlphaPercent / 100f).coerceIn(0.1f, 1f)
    val textPrimary = baseTextRgb.copy(alpha = textAlpha)
    val textSecondary = baseTextRgb.copy(alpha = (textAlpha * 0.72f).coerceIn(0.1f, 1f))

    val isDarkCard = baseCardRgb.calcLuminance() < 0.45f
    val cardBorder = if (isDarkCard) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color(0xFFE5E6EB)
    }

    val basePanelBg = Color((theme.panelBgColorHex and 0x00FFFFFFL) or 0xFF000000L)
    val panelAlpha = (theme.panelBgAlphaPercent / 100f).coerceIn(0f, 1f)

    return PanelPalette(
        panelBackgroundBrush = if (panelAlpha <= 0f) {
            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    basePanelBg.copy(alpha = panelAlpha),
                    basePanelBg.copy(alpha = (panelAlpha * 1.03f).coerceAtMost(1f))
                )
            )
        },
        cardBackground = cardBackground,
        cardBorder = cardBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        accent = accent,
        isDark = isDarkCard,
        themeConfig = theme
    )
}

// -----------------------------------------------------------------------------------------
// 核心负一屏面板容器 (MinusOneOverlayContainer)
// -----------------------------------------------------------------------------------------
