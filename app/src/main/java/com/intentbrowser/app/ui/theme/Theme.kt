package com.intentbrowser.app.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = PremiumIndigoLight,
    secondary = PremiumIndigo,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = Color(0xFF374151),
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = TextMutedDark
)

private val LightColorScheme = lightColorScheme(
    primary = PremiumIndigo,
    secondary = PremiumIndigoDark,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = Color(0xFFE5E7EB),
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = TextMutedLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            DarkColorScheme
        }
    } else {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(LocalContext.current)
        } else {
            LightColorScheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
