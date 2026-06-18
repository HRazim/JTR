package com.jtr.app.ui.person

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Auto-save fiable & transparent (v7.1.4), partagé par [AddPersonViewModel] et
 * [EditPersonViewModel]. Garantit qu'aucune saisie n'est perdue, sans bouton « Enregistrer ».
 *
 *  - [markDirty] : signale une modification. Après [debounceMs] d'inactivité, [persist] est appelé
 *    dans [scope] (= `viewModelScope`) → une SEULE écriture Room après une rafale de frappe
 *    (annulation/relance implicite via `debounce`, jamais une écriture par touche).
 *  - [flush] : exécute [persist] dans [appScope] — le scope APPLICATIF qui SURVIT à la fermeture de
 *    l'écran (le `viewModelScope` est annulé dès `onCleared`, ce qui couperait l'écriture). Le flush
 *    se termine donc même si le ViewModel est détruit juste après (retour, navigation, fermeture).
 *  - **Arrière-plan** : observe [ProcessLifecycleOwner] et déclenche [flush] sur `ON_STOP`.
 *  - Un [Mutex] sérialise les écritures → jamais deux `persist` concurrents sur la même fiche
 *    (debounce vs flush vs ON_STOP).
 *
 * [persist] reçoit `final` = true pour les sauvegardes de SORTIE/arrière-plan (étapes de
 * finalisation : photo définitive, réordonnancement des rappels…), false pour l'auto-save de frappe.
 * [persist] est responsable de ses propres gardes (rien à faire si « propre » / données identiques).
 */
@OptIn(FlowPreview::class)
class AutoSaveController(
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val debounceMs: Long = 700L,
    // Injectable pour les tests JVM (ProcessLifecycleOwner exige le framework Android) ; en prod,
    // résout le lifecycle PROCESS, `null` (et donc pas d'observateur ON_STOP) en test pur.
    private val lifecycle: Lifecycle? =
        runCatching { ProcessLifecycleOwner.get().lifecycle }.getOrNull(),
    private val persist: suspend (final: Boolean) -> Unit
) {
    private val signal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutex = Mutex()
    private val debounceJob: Job
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_STOP) flush()
    }

    init {
        lifecycle?.addObserver(observer)
        debounceJob = scope.launch {
            signal.debounce(debounceMs).collect {
                runCatching { mutex.withLock { persist(false) } }
            }
        }
    }

    /** À appeler à CHAQUE modification d'un champ éditable (idempotent, non bloquant). */
    fun markDirty() { signal.tryEmit(Unit) }

    /** Écriture immédiate du pending dans le scope SURVIVANT (sortie / arrière-plan / save manuel). */
    fun flush() {
        appScope.launch { runCatching { mutex.withLock { persist(true) } } }
    }

    /** À appeler depuis `onCleared` : flush final garanti + désenregistre l'observateur. */
    fun dispose() {
        flush()
        lifecycle?.removeObserver(observer)
        debounceJob.cancel()
    }
}
