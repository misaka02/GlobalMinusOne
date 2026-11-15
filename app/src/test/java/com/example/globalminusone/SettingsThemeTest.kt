package com.example.globalminusone

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.globalminusone.theme.settingsColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsThemeTest {
    @Test
    fun bothModes_keepSettingsTextReadableOnPagesDialogsAndControls() {
        listOf(false, true).forEach { dark ->
            val scheme = settingsColorScheme(dark)
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            surfaces.forEach { surface ->
                assertOpaque(surface)
                assertContrast(scheme.onSurface, surface)
                assertContrast(scheme.onSurfaceVariant, surface)
                assertContrast(scheme.primary, surface)
                assertContrast(scheme.error, surface)
            }
            listOf(
                scheme.onPrimary to scheme.primary,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onErrorContainer to scheme.errorContainer,
                scheme.onSecondaryContainer to scheme.secondaryContainer
            ).forEach { (text, surface) -> assertContrast(text, surface) }
        }
    }

    @Test
    fun settingsModes_haveDistinctOpaquePageSurfaces() {
        assertTrue(settingsColorScheme(false).background.luminance() > 0.9f)
        assertTrue(settingsColorScheme(true).background.luminance() < 0.02f)
    }

    private fun assertOpaque(color: Color) = assertEquals(1f, color.alpha, 0f)

    private fun assertContrast(text: Color, surface: Color) {
        assertOpaque(text)
        val luminances = listOf(text.luminance(), surface.luminance())
        val ratio = (luminances.max() + 0.05f) / (luminances.min() + 0.05f)
        assertTrue("Expected readable text, contrast was $ratio", ratio >= 4.5f)
    }
}
