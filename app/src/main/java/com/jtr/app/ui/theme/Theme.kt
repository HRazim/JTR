package com.jtr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun JTRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    preset: ThemePreset = ThemePreset.JTR_SIGNATURE,
    customColor: Long = 0xFF1565C0L,
    content: @Composable () -> Unit
) {
    val colorScheme = if (preset == ThemePreset.CUSTOM) {
        buildCustomColorScheme(Color(customColor), darkTheme)
    } else {
        if (darkTheme) preset.toDarkColorScheme() else preset.toLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
