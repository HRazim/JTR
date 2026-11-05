package com.jtr.app.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Session de déverrouillage (v7.1.143), à portée d'ACTIVITÉ et volontairement jamais
 * sauvegardée :
 *  - elle survit à une recréation (langue, rotation) : pas de re-verrouillage (v6.2.3) ;
 *  - elle meurt avec le processus : un processus neuf démarre VERROUILLÉ. L'ancien
 *    `rememberSaveable` restaurait « déverrouillé » après une mort du processus en
 *    arrière-plan, et la réouverture depuis les apps récentes contournait le verrou ;
 *  - la sortie de l'app n'exige l'authentification que si le verrou est actif À CE
 *    MOMENT : un verrou activé en cours de session s'applique dès la sortie suivante,
 *    sans renvoyer sur l'écran de verrou l'utilisateur qui vient de le configurer.
 *
 * L'app est verrouillée tant que [lockEnabled] ET [needsAuth] sont vrais.
 */
class LockViewModel(application: Application) : AndroidViewModel(application) {

    val lockEnabled: StateFlow<Boolean> = SecurityManager.lockEnabled(application)

    private val _needsAuth = MutableStateFlow(lockEnabled.value)
    val needsAuth: StateFlow<Boolean> = _needsAuth.asStateFlow()

    /** Le PROCESSUS est passé en arrière-plan (`ProcessLifecycleOwner`, ON_STOP). */
    fun onAppBackgrounded() {
        _needsAuth.value = lockEnabled.value
    }

    /** Schéma, biométrie ou code de secours validé sur l'écran de verrou. */
    fun onUnlocked() {
        _needsAuth.value = false
    }
}
