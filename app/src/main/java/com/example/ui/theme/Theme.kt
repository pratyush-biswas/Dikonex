package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = RetroGold,
    onPrimary = SpaceBlack,
    secondary = SlateGray,
    onSecondary = Color.White,
    tertiary = RetroGoldLight,
    background = SpaceBlack,
    onBackground = Color.White,
    surface = DarkCharcoal,
    onSurface = Color.White,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = RetroGoldDark,
    onPrimary = Color.White,
    secondary = WarmGray,
    onSecondary = Color.White,
    tertiary = RetroGold,
    background = LinenCream,
    onBackground = MutedCharcoal,
    surface = LightSurface,
    onSurface = MutedCharcoal,
    surfaceVariant = Color(0xFFF3F3EF),
    onSurfaceVariant = MutedCharcoal
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
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
