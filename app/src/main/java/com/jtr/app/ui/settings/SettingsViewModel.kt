package com.jtr.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.JTRApplication
import com.jtr.app.data.repository.PersonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SettingsViewModel — Persiste les préférences de notification via SharedPreferences.
 *
 * Rayon de proximité : valeur en km lue/écrite dans les prefs partagées "jtr_prefs".
 * Le ProximityCheckWorker lit la même clé pour adapter son rayon de détection.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
    private val personRepository = PersonRepository(application.applicationContext)

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean("notifications_enabled", true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _proximityEnabled = MutableStateFlow(
        prefs.getBoolean("proximity_enabled", true)
    )
    val proximityEnabled: StateFlow<Boolean> = _proximityEnabled.asStateFlow()

    private val _birthdayEnabled = MutableStateFlow(
        prefs.getBoolean("birthday_enabled", true)
    )
    val birthdayEnabled: StateFlow<Boolean> = _birthdayEnabled.asStateFlow()

    /** Rayon de détection de proximité en km (1 à 50 km, défaut : 5 km). */
    private val _proximityRadiusKm = MutableStateFlow(
        prefs.getFloat("proximity_radius_km", 5f)
    )
    val proximityRadiusKm: StateFlow<Float> = _proximityRadiusKm.asStateFlow()

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

    /** Enregistre le nouveau rayon et re-synchronise les geofences. */
    fun setProximityRadiusKm(km: Float) {
        _proximityRadiusKm.value = km
        prefs.edit().putFloat("proximity_radius_km", km).apply()
        viewModelScope.launch {
            val manager = JTRApplication.geofenceManager ?: return@launch
            val persons = personRepository.getAllActive().first()
            manager.unregisterAll()
            manager.registerAll(persons, km * 1000f)
        }
    }
}
