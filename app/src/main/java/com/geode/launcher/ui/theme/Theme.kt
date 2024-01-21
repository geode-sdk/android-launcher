package com.geode.launcher.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40

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

enum class Theme {
    LIGHT, DARK;

    companion object {
        @Composable
        fun fromInt(value: Int) = when (value) {
            1 -> LIGHT
            2 -> DARK
            else -> if (isSystemInDarkTheme()) DARK else LIGHT
        }
    }
}

val LocalTheme = compositionLocalOf { Theme.LIGHT }

@Composable
fun GeodeLauncherTheme(
    theme: Theme = Theme.fromInt(0),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    blackBackground: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            when {
                theme == Theme.DARK && blackBackground ->
                    dynamicDarkColorScheme(context).copy(surface = Color.Black, background = Color.Black)
                theme == Theme.DARK -> dynamicDarkColorScheme(context)
                else -> dynamicLightColorScheme(context)
            }
        }
        theme == Theme.DARK && blackBackground ->
            DarkColorScheme.copy(surface = Color.Black, background = Color.Black)
        theme == Theme.DARK -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
    )
}