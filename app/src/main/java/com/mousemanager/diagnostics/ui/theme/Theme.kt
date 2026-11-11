package com.mousemanager.diagnostics.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5BD9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF00164E),
    secondary = Color(0xFF0E8F80),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA8F2E6),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFB26A00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFFAF9FC),
    onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFE4E4EC),
    onSurfaceVariant = Color(0xFF474A55),
    outline = Color(0xFF777B86),
    outlineVariant = Color(0xFFC7C9D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF0B2280),
    primaryContainer = Color(0xFF2C47AE),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFF8BD6C9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFA8F2E6),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF653E00),
    onTertiaryContainer = Color(0xFFFFDDB6),
    background = Color(0xFF121317),
    onBackground = Color(0xFFE3E2E7),
    surface = Color(0xFF1A1B20),
    onSurface = Color(0xFFE3E2E7),
    surfaceVariant = Color(0xFF2A2C33),
    onSurfaceVariant = Color(0xFFC4C6D1),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF3E4048),
)

@Composable
fun MouseManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
