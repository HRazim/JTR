package com.jtr.app.security

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Session de déverrouillage (v7.1.143). Un [LockViewModel] neuf représente un PROCESSUS
 * neuf : c'est ainsi que se reproduit, sans appareil, la mort du processus en arrière-plan.
 */
class LockViewModelTest {

    private val lockEnabled = MutableStateFlow(false)

    @Before
    fun setUp() {
        mockkObject(SecurityManager)
        every { SecurityManager.lockEnabled(any()) } returns lockEnabled
    }

    @After
    fun tearDown() {
        unmockkObject(SecurityManager)
    }

    private fun newProcess() = LockViewModel(mockk<Application>(relaxed = true))

    @Test
    fun lockEnabled_aNewProcessStartsLocked() {
        lockEnabled.value = true

        assertThat(newProcess().needsAuth.value).isTrue()
    }

    @Test
    fun unlockedSession_isNotRestoredAfterProcessDeath() {
        lockEnabled.value = true
        newProcess().onUnlocked()

        // Le processus est tué en arrière-plan, l'app est rouverte depuis les récents.
        assertThat(newProcess().needsAuth.value).isTrue()
    }

    @Test
    fun unlockThenBackground_relocks() {
        lockEnabled.value = true
        val vm = newProcess()

        vm.onUnlocked()
        assertThat(vm.needsAuth.value).isFalse()

        vm.onAppBackgrounded()
        assertThat(vm.needsAuth.value).isTrue()
    }

    @Test
    fun lockDisabled_neverRequiresAuth() {
        val vm = newProcess()
        vm.onAppBackgrounded()

        assertThat(vm.needsAuth.value).isFalse()
    }

    @Test
    fun lockEnabledMidSession_appliesAtTheNextBackground() {
        val vm = newProcess()
        vm.onAppBackgrounded()

        // L'utilisateur active le verrou après une première sortie : pas d'écran de verrou
        // immédiat (il vient de le configurer)…
        lockEnabled.value = true
        assertThat(vm.lockEnabled.value).isTrue()
        assertThat(vm.needsAuth.value).isFalse()

        // … mais la sortie suivante verrouille, sans attendre une recomposition fortuite.
        vm.onAppBackgrounded()
        assertThat(vm.needsAuth.value).isTrue()
    }
}
