package com.jtr.app.ui.theme

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _selectedPreset = MutableStateFlow(
        ThemePreset.values().firstOrNull { it.name == prefs.getString("theme_preset", null) }
            ?: ThemePreset.JTR_SIGNATURE
    )
    val selectedPreset: StateFlow<ThemePreset> = _selectedPreset.asStateFlow()

    // Facteur d'échelle de la police appliqué à TOUTE l'app (accessibilité), borné
    // à [MIN_FONT_SCALE, MAX_FONT_SCALE] : un plafond strict évite que de très
    // grandes polices ne cassent les mises en page.
    private val _fontScale = MutableStateFlow(
        prefs.getFloat("font_scale", 1f).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    )
    val fontScale: StateFlow<Float> = _fontScale.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun setPreset(preset: ThemePreset) {
        _selectedPreset.value = preset
        prefs.edit().putString("theme_preset", preset.name).apply()
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        _fontScale.value = clamped
        prefs.edit().putFloat("font_scale", clamped).apply()
    }

    companion object {
        const val MIN_FONT_SCALE = 0.9f

        /** Plafond STRICT de la taille de police (cf. cahier des charges v6.1.0). */
        const val MAX_FONT_SCALE = 1.30f
    }
}
