package com.example.globalminusone.data.policy

import com.example.globalminusone.data.MAX_CARD_SPACING_DP
import com.example.globalminusone.data.MIN_CARD_SPACING_DP
import com.example.globalminusone.data.ThemeConfig

data class PanelBounds(val x: Float, val y: Float, val width: Float, val height: Float)

fun ThemeConfig.normalizedPanelGeometry() = copy(
    panelWidthPercent = panelWidthPercent.coerceIn(30, 100),
    panelHeightPercent = panelHeightPercent.coerceIn(30, 100),
    panelHorizontalOffsetPercent = panelHorizontalOffsetPercent.coerceIn(0, 100),
    panelVerticalPositionPercent = panelVerticalPositionPercent.coerceIn(0, 100),
    cardSpacingDp = cardSpacingDp.coerceIn(MIN_CARD_SPACING_DP, MAX_CARD_SPACING_DP)
)

fun panelBounds(screenWidth: Float, screenHeight: Float, theme: ThemeConfig, right: Boolean): PanelBounds {
    val t = theme.normalizedPanelGeometry()
    val width = screenWidth * t.panelWidthPercent / 100f
    val height = screenHeight * t.panelHeightPercent / 100f
    val horizontal = (screenWidth - width) * t.panelHorizontalOffsetPercent / 100f
    return PanelBounds(if (right) screenWidth - width - horizontal else horizontal,
        (screenHeight - height) * t.panelVerticalPositionPercent / 100f, width, height)
}

fun movedPanelTheme(theme: ThemeConfig, screenWidth: Float, screenHeight: Float,
    right: Boolean, dx: Float, dy: Float): ThemeConfig {
    val b = panelBounds(screenWidth, screenHeight, theme, right)
    val gapX = (screenWidth - b.width).coerceAtLeast(0f)
    val gapY = (screenHeight - b.height).coerceAtLeast(0f)
    val x = (b.x + dx).coerceIn(0f, gapX)
    val y = (b.y + dy).coerceIn(0f, gapY)
    return theme.copy(
        panelHorizontalOffsetPercent = if (gapX < 1f) 0 else (((if (right) gapX - x else x) / gapX) * 100).toInt(),
        panelVerticalPositionPercent = if (gapY < 1f) 50 else ((y / gapY) * 100).toInt()
    ).normalizedPanelGeometry()
}
