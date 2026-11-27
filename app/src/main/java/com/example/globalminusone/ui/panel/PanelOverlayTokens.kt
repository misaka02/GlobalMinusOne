package com.example.globalminusone.ui.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.globalminusone.ui.AppIcons as Icons

/** Unified overlay modal, sheet, and floating chrome surface tokens for panel editors and menus. */
@Immutable
internal data class PanelOverlayTokens(
    val sheetBg: Color,
    val elevatedSurface: Color,
    val cardBg: Color,
    val chipBg: Color,
    val inputBg: Color,
    val border: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val handle: Color,
    val danger: Color,
    val isDark: Boolean
)

internal object PanelOverlayRadii {
    val Sheet = 22.dp
    val Card = 16.dp
    val Control = 12.dp
    val Chip = 10.dp
}

private val DarkOverlayTokens = PanelOverlayTokens(
    sheetBg = Color(0xFF171B22),
    elevatedSurface = Color(0xFF1F242E),
    cardBg = Color(0xFF252B36),
    chipBg = Color(0xFF2B3240),
    inputBg = Color(0xFF2B3240),
    border = Color(0xFF323A47),
    divider = Color(0xFF323A47),
    textPrimary = Color(0xFFF3F6FA),
    textSecondary = Color(0xFF9EA8B8),
    textDisabled = Color(0xFF566070),
    handle = Color(0xFF434C5C),
    danger = Color(0xFFF87171),
    isDark = true
)

private val LightOverlayTokens = PanelOverlayTokens(
    sheetBg = Color(0xFFFFFFFF),
    elevatedSurface = Color(0xFFF5F7FB),
    cardBg = Color(0xFFFFFFFF),
    chipBg = Color(0xFFEEF2F7),
    inputBg = Color(0xFFEEF2F7),
    border = Color(0xFFE2E8F0),
    divider = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF526075),
    textDisabled = Color(0xFFAEB8C6),
    handle = Color(0xFFD2DAE6),
    danger = Color(0xFFDC2626),
    isDark = false
)

internal fun panelOverlayTokens(palette: PanelPalette): PanelOverlayTokens =
    if (palette.isDark) DarkOverlayTokens else LightOverlayTokens

@Composable
internal fun PeekCanvasOverlayBar(
    label: String,
    accent: Color,
    tokens: PanelOverlayTokens,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onRestore,
        modifier = modifier.padding(bottom = 14.dp),
        shape = RoundedCornerShape(999.dp),
        color = tokens.elevatedSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Visibility, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Text(label, color = tokens.textPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
