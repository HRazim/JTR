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
            ?: ThemePreset.AZURE
    )
    val selectedPreset: StateFlow<ThemePreset> = _selectedPreset.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun setPreset(preset: ThemePreset) {
        _selectedPreset.value = preset
        prefs.edit().putString("theme_preset", preset.name).apply()
    }
}
