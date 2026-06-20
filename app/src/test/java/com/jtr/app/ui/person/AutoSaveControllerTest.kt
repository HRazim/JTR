package com.jtr.app.ui.person

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests de l'auto-save (v7.1.4) au niveau de [AutoSaveController] — le cœur réutilisé par les deux
 * ViewModels. On y vérifie les GARANTIES anti-perte sans dépendre de Room :
 *  - debounce : une rafale de frappe ne déclenche PAS une écriture par touche, mais UNE seule après
 *    le délai (pas d'écritures Room excessives) ;
 *  - flush : un retour/sortie AVANT le debounce persiste tout de même (final) ;
 *  - cycle de vie : `ON_STOP` (arrière-plan) déclenche un flush.
 *
 * Dispatcher NON confiné (exécution immédiate, horloge virtuelle pour les délais) → déterministe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaveControllerTest {

    /** LifecycleOwner minimal pour piloter ON_STOP en test JVM (createUnsafe = sans main thread). */
    private class FakeOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun debounce_noWritePerKeystroke_singleWriteAfterDelay() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val writes = mutableListOf<Boolean>()
        val controller = AutoSaveController(scope, scope, debounceMs = 700L, lifecycle = null) {
            writes.add(it)
        }
        try {
            // Rafale de « frappe » : 10 modifications quasi simultanées.
            repeat(10) { controller.markDirty() }
            advanceTimeBy(300)            // < debounce
            assertThat(writes).isEmpty()  // AUCUNE écriture pendant la frappe

            advanceTimeBy(500)            // total 800 ms > debounce
            assertThat(writes).containsExactly(false) // UNE seule écriture, auto-save (final = false)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun flush_persistsImmediatelyAsFinal_evenBeforeDebounce() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val writes = mutableListOf<Boolean>()
        val controller = AutoSaveController(scope, scope, 700L, null) { writes.add(it) }
        try {
            controller.markDirty()
            controller.flush()           // retour/sortie AVANT que le debounce ne se déclenche
            assertThat(writes).contains(true)   // le flush a bien écrit en « final »
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun onStop_triggersFlush() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val writes = mutableListOf<Boolean>()
        val owner = FakeOwner()
        val controller = AutoSaveController(scope, scope, 700L, owner.lifecycle) { writes.add(it) }
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            controller.markDirty()
            // Passage en arrière-plan : ON_STOP doit déclencher un flush (final).
            owner.registry.currentState = Lifecycle.State.CREATED
            assertThat(writes).contains(true)
        } finally {
            scope.cancel()
        }
    }
}
