package com.example.globalminusone.ui.panel

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

internal data class PanelMenuPalette(
    val background: Color, val text: Color, val secondary: Color, val accent: Color,
    val chip: Color, val selectedChip: Color, val danger: Color, val disabled: Color,
    val divider: Color
)

internal fun menuContrast(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
}

/** Keep the requested hue when legible; otherwise move it toward the readable extreme. */
internal fun readableMenuColor(requested: Color, background: Color): Color {
    val opaque = requested.copy(alpha = 1f)
    if (menuContrast(opaque, background) >= 4.5f) return opaque
    val target = if (menuContrast(Color.Black, background) >= menuContrast(Color.White, background)) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(20) {
        val middle = (low + high) / 2f
        if (menuContrast(lerp(opaque, target, middle), background) >= 4.5f) high = middle else low = middle
    }
    return lerp(opaque, target, high).copy(alpha = 1f)
}

internal fun resolvePanelMenuPalette(base: PanelPalette): PanelMenuPalette {
    val background = base.cardBackground.copy(alpha = 1f)
    val extreme = if (menuContrast(Color.Black, background) >= menuContrast(Color.White, background)) Color.Black else Color.White
    fun chipAt(amount: Float): Color = lerp(background, base.accent.copy(alpha = 1f), amount)
        .takeIf { menuContrast(extreme, it) >= 4.5f } ?: background
    val chip = chipAt(.08f)
    val selected = chipAt(.18f)
    // The same text must remain legible on both normal and selected chips.
    fun readableOnAll(color: Color): Color {
        val opaque = color.copy(alpha = 1f)
        if (listOf(background, chip, selected).all { menuContrast(opaque, it) >= 4.5f }) return opaque
        return extreme
    }
    val text = readableOnAll(base.textPrimary)
    return PanelMenuPalette(background, text, readableOnAll(base.textSecondary), readableOnAll(base.accent),
        chip, selected, readableMenuColor(Color(0xFFEF4444), background),
        readableMenuColor(lerp(background, text, .65f), background), lerp(background, text, .22f))
}

internal val LocalPanelMenuPalette = staticCompositionLocalOf<PanelMenuPalette?> { null }

@Composable
internal fun PanelDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val palette = resolvePanelMenuPalette(LocalPanelPalette.current)
    val scheme = MaterialTheme.colorScheme.copy(surface = palette.background,
        surfaceContainer = palette.background, surfaceContainerHigh = palette.background,
        onSurface = palette.text, onSurfaceVariant = palette.secondary, primary = palette.accent,
        outlineVariant = palette.divider, error = palette.danger)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPanelMenuPalette provides palette) {
            DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest,
                containerColor = palette.background, tonalElevation = 0.dp, content = content)
        }
    }
}

@Composable
internal fun PanelMenuItem(text: @Composable () -> Unit, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = checkNotNull(LocalPanelMenuPalette.current)
    DropdownMenuItem(text = text, enabled = enabled, onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = palette.text, disabledTextColor = palette.disabled))
}
