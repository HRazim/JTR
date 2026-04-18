package com.jtr.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ThemePreset(
    val displayName: String,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewTertiary: Color
) {
    AZURE(
        displayName = "Azure",
        previewPrimary = Color(0xFF1565C0),
        previewSecondary = Color(0xFF0288D1),
        previewTertiary = Color(0xFFBBDEFB)
    ),
    EMERALD(
        displayName = "Émeraude",
        previewPrimary = Color(0xFF00695C),
        previewSecondary = Color(0xFF26A69A),
        previewTertiary = Color(0xFFE0F2F1)
    ),
    CORAL(
        displayName = "Corail",
        previewPrimary = Color(0xFFBF360C),
        previewSecondary = Color(0xFFFF7043),
        previewTertiary = Color(0xFFFBE9E7)
    ),
    VIOLET(
        displayName = "Violet",
        previewPrimary = Color(0xFF6A1B9A),
        previewSecondary = Color(0xFFAB47BC),
        previewTertiary = Color(0xFFF3E5F5)
    ),
    SLATE(
        displayName = "Ardoise",
        previewPrimary = Color(0xFF37474F),
        previewSecondary = Color(0xFF546E7A),
        previewTertiary = Color(0xFFECEFF1)
    ),
    ROSE(
        displayName = "Rose",
        previewPrimary = Color(0xFFAD1457),
        previewSecondary = Color(0xFFE91E63),
        previewTertiary = Color(0xFFFCE4EC)
    )
}

fun ThemePreset.toLightColorScheme(): ColorScheme = when (this) {
    ThemePreset.AZURE -> lightColorScheme(
        primary = Color(0xFF1565C0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E4FF),
        onPrimaryContainer = Color(0xFF001C39),
        secondary = Color(0xFF0277BD),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCBE6FF),
        onSecondaryContainer = Color(0xFF001E31),
        background = Color(0xFFFAFDFF),
        onBackground = Color(0xFF001F2A),
        surface = Color(0xFFFAFDFF),
        onSurface = Color(0xFF001F2A),
        surfaceVariant = Color(0xFFDDE3EA),
        onSurfaceVariant = Color(0xFF41484D),
    )
    ThemePreset.EMERALD -> lightColorScheme(
        primary = Color(0xFF00695C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA7F3DC),
        onPrimaryContainer = Color(0xFF002117),
        secondary = Color(0xFF006A60),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF9FF2E3),
        onSecondaryContainer = Color(0xFF00201C),
        background = Color(0xFFF5FDFB),
        onBackground = Color(0xFF051F1C),
        surface = Color(0xFFF5FDFB),
        onSurface = Color(0xFF051F1C),
        surfaceVariant = Color(0xFFDAE5E1),
        onSurfaceVariant = Color(0xFF3F4946),
    )
    ThemePreset.CORAL -> lightColorScheme(
        primary = Color(0xFFBF360C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBD1),
        onPrimaryContainer = Color(0xFF3B0A00),
        secondary = Color(0xFFAD4300),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBCA),
        onSecondaryContainer = Color(0xFF3D1600),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF201100),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF201100),
        surfaceVariant = Color(0xFFF5DED5),
        onSurfaceVariant = Color(0xFF53433D),
    )
    ThemePreset.VIOLET -> lightColorScheme(
        primary = Color(0xFF6A1B9A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDD9FF),
        onPrimaryContainer = Color(0xFF250048),
        secondary = Color(0xFF7B1FA2),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E5F5),
        onSecondaryContainer = Color(0xFF1E0048),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1C0048),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1C0048),
        surfaceVariant = Color(0xFFEBDFEF),
        onSurfaceVariant = Color(0xFF4C4255),
    )
    ThemePreset.SLATE -> lightColorScheme(
        primary = Color(0xFF37474F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCDD7DF),
        onPrimaryContainer = Color(0xFF0C1316),
        secondary = Color(0xFF546E7A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD4E4EC),
        onSecondaryContainer = Color(0xFF0E1D23),
        background = Color(0xFFF8FAFB),
        onBackground = Color(0xFF191C1D),
        surface = Color(0xFFF8FAFB),
        onSurface = Color(0xFF191C1D),
        surfaceVariant = Color(0xFFDDE3E8),
        onSurfaceVariant = Color(0xFF41484D),
    )
    ThemePreset.ROSE -> lightColorScheme(
        primary = Color(0xFFAD1457),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E3),
        onPrimaryContainer = Color(0xFF3E0018),
        secondary = Color(0xFF9C274A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E3),
        onSecondaryContainer = Color(0xFF3E0018),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF201016),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF201016),
        surfaceVariant = Color(0xFFF3DEE5),
        onSurfaceVariant = Color(0xFF514349),
    )
}

fun ThemePreset.toDarkColorScheme(): ColorScheme = when (this) {
    ThemePreset.AZURE -> darkColorScheme(
        primary = Color(0xFF9ECAFF),
        onPrimary = Color(0xFF003060),
        primaryContainer = Color(0xFF004789),
        onPrimaryContainer = Color(0xFFD3E4FF),
        secondary = Color(0xFF90C8FF),
        onSecondary = Color(0xFF003353),
        secondaryContainer = Color(0xFF004B76),
        onSecondaryContainer = Color(0xFFCBE6FF),
        background = Color(0xFF001F2A),
        onBackground = Color(0xFFBFE9FF),
        surface = Color(0xFF001F2A),
        onSurface = Color(0xFFBFE9FF),
        surfaceVariant = Color(0xFF41484D),
        onSurfaceVariant = Color(0xFFC1C7CE),
    )
    ThemePreset.EMERALD -> darkColorScheme(
        primary = Color(0xFF8CD8C3),
        onPrimary = Color(0xFF003829),
        primaryContainer = Color(0xFF00513E),
        onPrimaryContainer = Color(0xFFA7F3DC),
        secondary = Color(0xFF84D6C7),
        onSecondary = Color(0xFF003731),
        secondaryContainer = Color(0xFF005149),
        onSecondaryContainer = Color(0xFF9FF2E3),
        background = Color(0xFF051F1C),
        onBackground = Color(0xFFBEEDE4),
        surface = Color(0xFF051F1C),
        onSurface = Color(0xFFBEEDE4),
        surfaceVariant = Color(0xFF3F4946),
        onSurfaceVariant = Color(0xFFBEC9C5),
    )
    ThemePreset.CORAL -> darkColorScheme(
        primary = Color(0xFFFFB59D),
        onPrimary = Color(0xFF651E03),
        primaryContainer = Color(0xFF8B2E0E),
        onPrimaryContainer = Color(0xFFFFDBD1),
        secondary = Color(0xFFFFB78E),
        onSecondary = Color(0xFF5E2300),
        secondaryContainer = Color(0xFF833309),
        onSecondaryContainer = Color(0xFFFFDBCA),
        background = Color(0xFF201100),
        onBackground = Color(0xFFFFDCCA),
        surface = Color(0xFF201100),
        onSurface = Color(0xFFFFDCCA),
        surfaceVariant = Color(0xFF53433D),
        onSurfaceVariant = Color(0xFFD7C2BB),
    )
    ThemePreset.VIOLET -> darkColorScheme(
        primary = Color(0xFFD9BBFF),
        onPrimary = Color(0xFF3E0075),
        primaryContainer = Color(0xFF5A0F8E),
        onPrimaryContainer = Color(0xFFEDD9FF),
        secondary = Color(0xFFDBB6FF),
        onSecondary = Color(0xFF3A006E),
        secondaryContainer = Color(0xFF590085),
        onSecondaryContainer = Color(0xFFF3E5F5),
        background = Color(0xFF1C0048),
        onBackground = Color(0xFFEBDBFF),
        surface = Color(0xFF1C0048),
        onSurface = Color(0xFFEBDBFF),
        surfaceVariant = Color(0xFF4C4255),
        onSurfaceVariant = Color(0xFFCFC3D9),
    )
    ThemePreset.SLATE -> darkColorScheme(
        primary = Color(0xFFB2C8D4),
        onPrimary = Color(0xFF1E3038),
        primaryContainer = Color(0xFF2D4550),
        onPrimaryContainer = Color(0xFFCDD7DF),
        secondary = Color(0xFFB0C8D4),
        onSecondary = Color(0xFF1C3038),
        secondaryContainer = Color(0xFF2B454F),
        onSecondaryContainer = Color(0xFFD4E4EC),
        background = Color(0xFF191C1D),
        onBackground = Color(0xFFE0E3E5),
        surface = Color(0xFF191C1D),
        onSurface = Color(0xFFE0E3E5),
        surfaceVariant = Color(0xFF41484D),
        onSurfaceVariant = Color(0xFFC1C7CE),
    )
    ThemePreset.ROSE -> darkColorScheme(
        primary = Color(0xFFFFB0C9),
        onPrimary = Color(0xFF64002D),
        primaryContainer = Color(0xFF8C0041),
        onPrimaryContainer = Color(0xFFFFD9E3),
        secondary = Color(0xFFFFB0C9),
        onSecondary = Color(0xFF64002D),
        secondaryContainer = Color(0xFF7E0039),
        onSecondaryContainer = Color(0xFFFFD9E3),
        background = Color(0xFF201016),
        onBackground = Color(0xFFFFD9E3),
        surface = Color(0xFF201016),
        onSurface = Color(0xFFFFD9E3),
        surfaceVariant = Color(0xFF514349),
        onSurfaceVariant = Color(0xFFD5C2C8),
    )
}
