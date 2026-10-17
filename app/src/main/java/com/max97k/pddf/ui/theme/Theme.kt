package com.max97k.pddf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.max97k.pddf.data.ThemeMode

private val DarkColorScheme =
  darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
    surfaceDim = md_theme_dark_surfaceDim,
    surfaceBright = md_theme_dark_surfaceBright,
    surfaceTint = md_theme_dark_surfaceTint,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF272B2F),
    surfaceContainerHighest = Color(0xFF32353A)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
    surfaceDim = md_theme_light_surfaceDim,
    surfaceBright = md_theme_light_surfaceBright,
    surfaceTint = md_theme_light_surfaceTint,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFF1F4F9),
    surfaceContainerHigh = Color(0xFFEBEFF3),
    surfaceContainerHighest = Color(0xFFE5E9EE)
  )

private val AmoledColorScheme =
  DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF0C0C0E),
    surfaceContainerHigh = Color(0xFF121214),
    surfaceContainerHighest = Color(0xFF1E1E22),
    surfaceVariant = Color(0xFF1C1C1E),
    onBackground = Color.White,
    onSurface = Color.White,
    outlineVariant = Color(0xFF2E3138)
  )

private val OledBlackColorScheme =
  DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0E11),
    surfaceContainer = Color(0xFF13171C),
    surfaceContainerHigh = Color(0xFF1B2026),
    surfaceContainerHighest = Color(0xFF262C34),
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF171B20),
    onSurfaceVariant = Color.White,
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF383E48),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A3038)
  )

@Composable
fun MyApplicationTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
      ThemeMode.LIGHT -> false
      ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.OLED_BLACK -> true
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
  }

  val colorScheme =
    when {
      themeMode == ThemeMode.OLED_BLACK -> {
          if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              val context = LocalContext.current
              val dynamicDark = dynamicDarkColorScheme(context)
              dynamicDark.copy(
                  background = Color.Black,
                  surface = Color.Black,
                  surfaceContainerLowest = Color.Black,
                  surfaceContainerLow = Color(0xFF0B0E11),
                  surfaceContainer = Color(0xFF13171C),
                  surfaceContainerHigh = Color(0xFF1B2026),
                  surfaceContainerHighest = Color(0xFF262C34),
                  onBackground = Color.White,
                  onSurface = Color.White,
                  surfaceVariant = Color(0xFF171B20),
                  onSurfaceVariant = Color.White,
                  outlineVariant = Color(0xFF383E48)
              )
          } else {
              OledBlackColorScheme
          }
      }
      themeMode == ThemeMode.AMOLED -> {
          if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              val context = LocalContext.current
              val dynamicDark = dynamicDarkColorScheme(context)
              dynamicDark.copy(
                  background = Color.Black,
                  surface = Color.Black,
                  surfaceContainerLowest = Color.Black,
                  surfaceContainerLow = Color.Black,
                  surfaceContainer = Color(0xFF0C0C0E),
                  surfaceContainerHigh = Color(0xFF121214),
                  surfaceContainerHighest = Color(0xFF1E1E22),
                  onBackground = Color.White,
                  onSurface = Color.White,
                  surfaceVariant = Color(0xFF1C1C1E),
                  outlineVariant = Color(0xFF2E3138)
              )
          } else {
              AmoledColorScheme
          }
      }
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = Shapes, content = content)
}
