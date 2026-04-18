package com.jtr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun JTRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePreset.AZURE,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) preset.toDarkColorScheme() else preset.toLightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
