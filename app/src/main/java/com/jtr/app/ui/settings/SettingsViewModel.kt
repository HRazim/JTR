package com.jtr.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SettingsViewModel — Persiste les préférences de notification via SharedPreferences.
 *
 * Le rayon de proximité n'est plus configurable : il est fixe et automatique
 * (voir [com.jtr.app.JTRApplication.PROXIMITY_RADIUS_KM]).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean("notifications_enabled", false)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _proximityEnabled = MutableStateFlow(
        prefs.getBoolean("proximity_enabled", false)
    )
    val proximityEnabled: StateFlow<Boolean> = _proximityEnabled.asStateFlow()

    private val _birthdayEnabled = MutableStateFlow(
        prefs.getBoolean("birthday_enabled", false)
    )
    val birthdayEnabled: StateFlow<Boolean> = _birthdayEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun setProximityEnabled(enabled: Boolean) {
        _proximityEnabled.value = enabled
        prefs.edit().putBoolean("proximity_enabled", enabled).apply()
    }

    fun setBirthdayEnabled(enabled: Boolean) {
        _birthdayEnabled.value = enabled
        prefs.edit().putBoolean("birthday_enabled", enabled).apply()
    }
}
