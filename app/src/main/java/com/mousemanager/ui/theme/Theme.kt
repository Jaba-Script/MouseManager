package com.mousemanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F5F90),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF56606F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF10243F),
    secondary = Color(0xFFBEC6D6),
    onSecondary = Color(0xFF283141),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
)

/** Тема Material3: цвета заданы явно, динамические цвета не используются. */
@Composable
fun MouseManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
