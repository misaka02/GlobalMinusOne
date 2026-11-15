package com.example.globalminusone.ui.panel

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class PanelMenuPaletteTest {
    private fun base(background: Color, text: Color, accent: Color) = PanelPalette(
        Brush.verticalGradient(listOf(background, background)), background, text, text,
        text.copy(alpha = .12f), accent, background.red < .5f
    )

    @Test fun customAndTransparentColorsRemainReadableOnEveryMenuSurface() {
        val samples = listOf(Color.Black, Color.White, Color(0xFF777777), Color(0xFF20242C),
            Color(0xFFFCFCFD), Color.Red, Color.Green, Color.Blue, Color.Yellow)
        for (background in samples) for (text in samples) for (accent in samples) {
            val menu = resolvePanelMenuPalette(base(background.copy(alpha = .05f), text.copy(alpha = .1f), accent))
            for (surface in listOf(menu.background, menu.chip, menu.selectedChip)) {
                assertEquals(1f, surface.alpha, 0f)
                for (foreground in listOf(menu.text, menu.secondary, menu.accent)) {
                    assertEquals(1f, foreground.alpha, 0f)
                    assertTrue("$foreground on $surface", menuContrast(foreground, surface) >= 4.49f)
                }
            }
            for (foreground in listOf(menu.danger, menu.disabled)) {
                assertEquals(1f, foreground.alpha, 0f)
                assertTrue(menuContrast(foreground, menu.background) >= 4.49f)
            }
        }
    }

    @Test fun readableCustomColorsAndOpaqueGlobalBackgroundArePreserved() {
        val background = Color(0xFF16212D)
        val text = Color(0xFFD7F4E8)
        val menu = resolvePanelMenuPalette(base(background.copy(alpha = .2f), text, Color(0xFFA9C7FF)))
        assertEquals(background, menu.background)
        assertEquals(text, menu.text)
    }
}
