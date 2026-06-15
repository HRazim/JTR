package com.jtr.app.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État UDF du verrou local pour l'écran Paramètres (carte Sécurité). La source de
 * vérité reste [SecurityManager] (stockage chiffré) ; ce ViewModel en expose des
 * miroirs réactifs et les rafraîchit après chaque opération.
 */
class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext

    private val _lockEnabled = MutableStateFlow(SecurityManager.isLockEnabled(ctx))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(SecurityManager.isBiometricEnabled(ctx))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    /** Disponibilité matérielle (biométrie ou identifiant d'appareil). */
    val biometricAvailable: Boolean = Biometrics.isAvailable(ctx)

    fun refresh() {
        _lockEnabled.value = SecurityManager.isLockEnabled(ctx)
        _biometricEnabled.value = SecurityManager.isBiometricEnabled(ctx)
    }

    /** Définit/remplace le schéma puis stocke le code de secours associé. */
    fun applyNewPattern(pattern: List<Int>, recoveryCode: String) {
        SecurityManager.setPattern(ctx, pattern)
        SecurityManager.storeRecoveryCode(ctx, recoveryCode)
        refresh()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        SecurityManager.setBiometricEnabled(ctx, enabled)
        refresh()
    }

    fun disableLock() {
        SecurityManager.disableLock(ctx)
        refresh()
    }
}
