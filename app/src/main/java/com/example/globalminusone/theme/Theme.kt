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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

@Composable
fun GlobalMinusOneTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}


// Settings have opaque, fixed role colors. Panel colors and transparency remain in ThemeConfig.
internal fun settingsColorScheme(darkTheme: Boolean): ColorScheme =
  if (darkTheme) SettingsDarkColorScheme else SettingsLightColorScheme

private val SettingsLightColorScheme = lightColorScheme(
  primary = Color(0xFF1557A0), onPrimary = Color.White,
  primaryContainer = Color(0xFFD7E8FF), onPrimaryContainer = Color(0xFF102F52),
  inversePrimary = Color(0xFFA7C8FF),
  secondary = Color(0xFF465F7C), onSecondary = Color.White,
  secondaryContainer = Color(0xFFD9E7FA), onSecondaryContainer = Color(0xFF182D45),
  tertiary = Color(0xFF6B4F7C), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFF0DCFF), onTertiaryContainer = Color(0xFF301A41),
  background = Color(0xFFF8FAFD), onBackground = Color(0xFF1A1D22),
  surface = Color(0xFFF8FAFD), onSurface = Color(0xFF1A1D22),
  surfaceVariant = Color(0xFFE1E7EF), onSurfaceVariant = Color(0xFF454C57),
  surfaceTint = Color(0xFF1557A0),
  inverseSurface = Color(0xFF2E3137), inverseOnSurface = Color(0xFFF0F2F6),
  error = Color(0xFFB3261E), onError = Color.White,
  errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410E0B),
  outline = Color(0xFF737C89), outlineVariant = Color(0xFFC3CAD5), scrim = Color.Black,
  surfaceBright = Color(0xFFF8FAFD), surfaceDim = Color(0xFFD8DCE3),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F5FA),
  surfaceContainer = Color(0xFFECF0F6), surfaceContainerHigh = Color(0xFFE6EAF1),
  surfaceContainerHighest = Color(0xFFE0E5ED)
)

private val SettingsDarkColorScheme = darkColorScheme(
  primary = Color(0xFFA7C8FF), onPrimary = Color(0xFF0A2D59),
  primaryContainer = Color(0xFF22456F), onPrimaryContainer = Color(0xFFD7E8FF),
  inversePrimary = Color(0xFF1557A0),
  secondary = Color(0xFFB5C9E4), onSecondary = Color(0xFF203348),
  secondaryContainer = Color(0xFF374A61), onSecondaryContainer = Color(0xFFD9E7FA),
  tertiary = Color(0xFFD9BCEB), onTertiary = Color(0xFF3D284D),
  tertiaryContainer = Color(0xFF543F64), onTertiaryContainer = Color(0xFFF0DCFF),
  background = Color(0xFF11151B), onBackground = Color(0xFFE4E8EF),
  surface = Color(0xFF11151B), onSurface = Color(0xFFE4E8EF),
  surfaceVariant = Color(0xFF414853), onSurfaceVariant = Color(0xFFC1C9D5),
  surfaceTint = Color(0xFFA7C8FF),
  inverseSurface = Color(0xFFE4E8EF), inverseOnSurface = Color(0xFF2E3137),
  error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
  errorContainer = Color(0xFF7D2322), onErrorContainer = Color(0xFFFFDAD6),
  outline = Color(0xFF8B94A2), outlineVariant = Color(0xFF414853), scrim = Color.Black,
  surfaceBright = Color(0xFF363B43), surfaceDim = Color(0xFF11151B),
  surfaceContainerLowest = Color(0xFF0C1016), surfaceContainerLow = Color(0xFF191D24),
  surfaceContainer = Color(0xFF1E222A), surfaceContainerHigh = Color(0xFF282D35),
  surfaceContainerHighest = Color(0xFF333840)
)

@Composable
fun SettingsTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = settingsColorScheme(darkTheme), typography = Typography, content = content)
}
