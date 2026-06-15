package com.jtr.app.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.worker.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SettingsViewModel — Persiste les préférences de notification via SharedPreferences.
 *
 * Le rayon de proximité n'est plus configurable : il est fixe et automatique
 * (voir [com.jtr.app.JTRApplication.PROXIMITY_RADIUS_KM]).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    // Défauts HARMONISÉS avec les Workers (v6.2.7) : notifications + dates importantes
    // activées par défaut (l'effet réel reste borné par la permission POST_NOTIFICATIONS),
    // proximité opt-in (nécessite la localisation). Le toggle affiché reflète donc
    // exactement le comportement des Workers — plus d'incohérence true/false.
    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean("notifications_enabled", true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _proximityEnabled = MutableStateFlow(
        prefs.getBoolean("proximity_enabled", false)
    )
    val proximityEnabled: StateFlow<Boolean> = _proximityEnabled.asStateFlow()

    private val _birthdayEnabled = MutableStateFlow(
        prefs.getBoolean("birthday_enabled", true)
    )
    val birthdayEnabled: StateFlow<Boolean> = _birthdayEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        // (Dés)active immédiatement les alarmes de rappel (annulation si coupé, réarmement
        // sinon) — la modification du toggle prend effet sans attendre l'ouverture suivante.
        rescheduleReminders()
    }

    fun setProximityEnabled(enabled: Boolean) {
        _proximityEnabled.value = enabled
        prefs.edit().putBoolean("proximity_enabled", enabled).apply()
    }

    fun setBirthdayEnabled(enabled: Boolean) {
        _birthdayEnabled.value = enabled
        prefs.edit().putBoolean("birthday_enabled", enabled).apply()
        rescheduleReminders()
    }

    private fun rescheduleReminders() {
        viewModelScope.launch { ReminderScheduler.rescheduleAll(getApplication()) }
    }

    /**
     * Drapeau « cette permission a déjà été demandée au moins une fois ». Indispensable
     * pour distinguer « jamais demandée » de « refusée définitivement » : les deux
     * donnent `shouldShowRequestPermissionRationale = false`. Persisté dans les mêmes
     * SharedPreferences (clé `asked_<permission>`).
     */
    fun wasPermissionAsked(permission: String): Boolean =
        prefs.getBoolean("asked_$permission", false)

    fun markPermissionAsked(permission: String) {
        prefs.edit().putBoolean("asked_$permission", true).apply()
    }
}
