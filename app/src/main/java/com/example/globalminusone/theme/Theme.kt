package com.example.globalminusone.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun GlobalMinusOneTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Unified cobalt/slate palette by default; callers may still opt into system dynamic color explicitly.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      else -> settingsColorScheme(darkTheme)
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

// Settings and tool surfaces use opaque, unified ColorOS-inspired slate + cobalt role colors.
internal fun settingsColorScheme(darkTheme: Boolean): ColorScheme =
  if (darkTheme) SettingsDarkColorScheme else SettingsLightColorScheme

private val SettingsLightColorScheme = lightColorScheme(
  primary = BrandCobaltLight, onPrimary = Color.White,
  primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF0F295C),
  inversePrimary = BrandCobaltDark,
  secondary = Color(0xFF475569), onSecondary = Color.White,
  secondaryContainer = Color(0xFFE2EAF6), onSecondaryContainer = Color(0xFF1E293B),
  tertiary = Color(0xFF6D28D9), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFEDE9FE), onTertiaryContainer = Color(0xFF3B0764),
  background = SlateBgLight, onBackground = SlateTextLight,
  surface = SlateBgLight, onSurface = SlateTextLight,
  surfaceVariant = Color(0xFFE2E8F0), onSurfaceVariant = Color(0xFF475569),
  surfaceTint = BrandCobaltLight,
  inverseSurface = Color(0xFF1E293B), inverseOnSurface = Color(0xFFF1F5F9),
  error = Color(0xFFB91C1C), onError = Color.White,
  errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF5C1111),
  outline = Color(0xFF64748B), outlineVariant = Color(0xFFD5DCE6), scrim = Color.Black,
  surfaceBright = SlateBgLight, surfaceDim = Color(0xFFE2E8F0),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
  surfaceContainer = Color(0xFFEEF2F7), surfaceContainerHigh = Color(0xFFE8EDF5),
  surfaceContainerHighest = Color(0xFFE2E8F0)
)

private val SettingsDarkColorScheme = darkColorScheme(
  primary = BrandCobaltDark, onPrimary = Color(0xFF092452),
  primaryContainer = Color(0xFF1E3A6E), onPrimaryContainer = Color(0xFFDCE8FF),
  inversePrimary = BrandCobaltLight,
  secondary = Color(0xFF94A3B8), onSecondary = Color(0xFF182232),
  secondaryContainer = Color(0xFF273447), onSecondaryContainer = Color(0xFFE2E8F0),
  tertiary = Color(0xFFC4B5FD), onTertiary = Color(0xFF2E1065),
  tertiaryContainer = Color(0xFF4C1D95), onTertiaryContainer = Color(0xFFEDE9FE),
  background = SlateBgDark, onBackground = SlateTextDark,
  surface = SlateBgDark, onSurface = SlateTextDark,
  surfaceVariant = Color(0xFF333C4B), onSurfaceVariant = Color(0xFFB8C2D1),
  surfaceTint = BrandCobaltDark,
  inverseSurface = SlateTextDark, inverseOnSurface = Color(0xFF1E293B),
  error = Color(0xFFFCA5A5), onError = Color(0xFF5B0B0B),
  errorContainer = Color(0xFF6B1D1D), onErrorContainer = Color(0xFFFEE2E2),
  outline = Color(0xFF8B95A5), outlineVariant = Color(0xFF333C4B), scrim = Color.Black,
  surfaceBright = Color(0xFF2B3341), surfaceDim = SlateBgDark,
  surfaceContainerLowest = Color(0xFF090C11), surfaceContainerLow = Color(0xFF161B23),
  surfaceContainer = Color(0xFF1C222C), surfaceContainerHigh = Color(0xFF232A36),
  surfaceContainerHighest = Color(0xFF2B3341)
)

@Composable
fun SettingsTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = settingsColorScheme(darkTheme), typography = Typography, content = content)
}
