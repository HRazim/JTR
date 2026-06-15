package com.jtr.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.jtr.app.R

enum class ThemePreset(
    @StringRes val labelRes: Int,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewTertiary: Color
) {
    JTR_SIGNATURE(
        labelRes = R.string.theme_name_jtr,
        previewPrimary = Color(0xFF1A1A1A),
        previewSecondary = Color(0xFF444444),
        previewTertiary = Color(0xFFE0E0E0)
    ),
    AZURE(
        labelRes = R.string.theme_name_azure,
        previewPrimary = Color(0xFF1565C0),
        previewSecondary = Color(0xFF0288D1),
        previewTertiary = Color(0xFFBBDEFB)
    ),
    EMERALD(
        labelRes = R.string.theme_name_emerald,
        previewPrimary = Color(0xFF00695C),
        previewSecondary = Color(0xFF26A69A),
        previewTertiary = Color(0xFFE0F2F1)
    ),
    CORAL(
        labelRes = R.string.theme_name_coral,
        previewPrimary = Color(0xFFBF360C),
        previewSecondary = Color(0xFFFF7043),
        previewTertiary = Color(0xFFFBE9E7)
    ),
    VIOLET(
        labelRes = R.string.theme_name_violet,
        previewPrimary = Color(0xFF6A1B9A),
        previewSecondary = Color(0xFFAB47BC),
        previewTertiary = Color(0xFFF3E5F5)
    ),
    ROSE(
        labelRes = R.string.theme_name_rose,
        previewPrimary = Color(0xFFAD1457),
        previewSecondary = Color(0xFFE91E63),
        previewTertiary = Color(0xFFFCE4EC)
    )
}

fun ThemePreset.toLightColorScheme(): ColorScheme = when (this) {
    ThemePreset.JTR_SIGNATURE -> lightColorScheme(
        primary = Color(0xFF1A1A1A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEEEEEE),
        onPrimaryContainer = Color(0xFF0D0D0D),
        secondary = Color(0xFF444444),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8E8E8),
        onSecondaryContainer = Color(0xFF1A1A1A),
        background = Color(0xFFF1F1F3),
        onBackground = Color(0xFF0D0D0D),
        surface = Color(0xFFFBFBFC),
        onSurface = Color(0xFF0D0D0D),
        surfaceVariant = Color(0xFFE6E6EA),
        onSurfaceVariant = Color(0xFF444444),
    )
    ThemePreset.AZURE -> lightColorScheme(
        primary = Color(0xFF1565C0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E4FF),
        onPrimaryContainer = Color(0xFF001C39),
        secondary = Color(0xFF0277BD),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCBE6FF),
        onSecondaryContainer = Color(0xFF001E31),
        background = Color(0xFFE7EFFA),
        onBackground = Color(0xFF001F2A),
        surface = Color(0xFFF3F8FE),
        onSurface = Color(0xFF001F2A),
        surfaceVariant = Color(0xFFDCE6F3),
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
        background = Color(0xFFE3F3EC),
        onBackground = Color(0xFF051F1C),
        surface = Color(0xFFF0FAF5),
        onSurface = Color(0xFF051F1C),
        surfaceVariant = Color(0xFFD6E8DF),
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
        background = Color(0xFFFFEEE6),
        onBackground = Color(0xFF201100),
        surface = Color(0xFFFFF6F1),
        onSurface = Color(0xFF201100),
        surfaceVariant = Color(0xFFF8E2D8),
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
        background = Color(0xFFF1E9FB),
        onBackground = Color(0xFF1C0048),
        surface = Color(0xFFFAF4FE),
        onSurface = Color(0xFF1C0048),
        surfaceVariant = Color(0xFFE9DEF4),
        onSurfaceVariant = Color(0xFF4C4255),
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
        background = Color(0xFFFCEAF1),
        onBackground = Color(0xFF201016),
        surface = Color(0xFFFEF4F8),
        onSurface = Color(0xFF201016),
        surfaceVariant = Color(0xFFF5DEE7),
        onSurfaceVariant = Color(0xFF514349),
    )
}

fun ThemePreset.toDarkColorScheme(): ColorScheme = when (this) {
    ThemePreset.JTR_SIGNATURE -> darkColorScheme(
        primary = Color(0xFFECECEC),
        onPrimary = Color(0xFF121212),
        primaryContainer = Color(0xFF2A2A2A),
        onPrimaryContainer = Color(0xFFECECEC),
        secondary = Color(0xFFB0B0B0),
        onSecondary = Color(0xFF121212),
        secondaryContainer = Color(0xFF1E1E1E),
        onSecondaryContainer = Color(0xFFB0B0B0),
        background = Color(0xFF0D0D0D),
        onBackground = Color(0xFFECECEC),
        surface = Color(0xFF1C1C1C),
        onSurface = Color(0xFFECECEC),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB0B0B0),
        surfaceContainerLowest = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF161616),
        surfaceContainer = Color(0xFF1C1C1C),
        surfaceContainerHigh = Color(0xFF262626),
        surfaceContainerHighest = Color(0xFF303030),
    )
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
        surface = Color(0xFF0E2C3A),
        onSurface = Color(0xFFBFE9FF),
        surfaceVariant = Color(0xFF41484D),
        onSurfaceVariant = Color(0xFFC1C7CE),
        surfaceContainerLowest = Color(0xFF00161F),
        surfaceContainerLow = Color(0xFF0A2835),
        surfaceContainer = Color(0xFF0E2C3A),
        surfaceContainerHigh = Color(0xFF173646),
        surfaceContainerHighest = Color(0xFF204152),
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
        surface = Color(0xFF0F302B),
        onSurface = Color(0xFFBEEDE4),
        surfaceVariant = Color(0xFF3F4946),
        onSurfaceVariant = Color(0xFFBEC9C5),
        surfaceContainerLowest = Color(0xFF02100E),
        surfaceContainerLow = Color(0xFF0B2A26),
        surfaceContainer = Color(0xFF0F302B),
        surfaceContainerHigh = Color(0xFF173A34),
        surfaceContainerHighest = Color(0xFF20453E),
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
        surface = Color(0xFF33220F),
        onSurface = Color(0xFFFFDCCA),
        surfaceVariant = Color(0xFF53433D),
        onSurfaceVariant = Color(0xFFD7C2BB),
        surfaceContainerLowest = Color(0xFF160B00),
        surfaceContainerLow = Color(0xFF2B1B0A),
        surfaceContainer = Color(0xFF33220F),
        surfaceContainerHigh = Color(0xFF3F2C18),
        surfaceContainerHighest = Color(0xFF4A3622),
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
        surface = Color(0xFF2C1060),
        onSurface = Color(0xFFEBDBFF),
        surfaceVariant = Color(0xFF4C4255),
        onSurfaceVariant = Color(0xFFCFC3D9),
        surfaceContainerLowest = Color(0xFF130034),
        surfaceContainerLow = Color(0xFF250A56),
        surfaceContainer = Color(0xFF2C1060),
        surfaceContainerHigh = Color(0xFF371A6E),
        surfaceContainerHighest = Color(0xFF41237A),
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
        surface = Color(0xFF341E26),
        onSurface = Color(0xFFFFD9E3),
        surfaceVariant = Color(0xFF514349),
        onSurfaceVariant = Color(0xFFD5C2C8),
        surfaceContainerLowest = Color(0xFF160A0F),
        surfaceContainerLow = Color(0xFF2C1820),
        surfaceContainer = Color(0xFF341E26),
        surfaceContainerHigh = Color(0xFF3F2831),
        surfaceContainerHighest = Color(0xFF4A323C),
    )
}
