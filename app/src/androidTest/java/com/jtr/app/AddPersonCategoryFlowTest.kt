package com.jtr.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jtr.app.ui.person.AddPersonViewModel
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.NameDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flux « Création depuis l'onglet Catégorie » : quand l'écran d'ajout est ouvert
 * depuis une catégorie, la route passe un `categoryId` via le SavedStateHandle.
 *
 * Ce test garantit que la réception de cet ID parent ne perturbe PAS
 * l'initialisation des listes dynamiques (une ligne vide par groupe, avec le bon
 * type par défaut) ni de l'objet [NameDetails] (vide).
 */
@RunWith(AndroidJUnit4::class)
class AddPersonCategoryFlowTest {

    private fun newViewModel(handle: SavedStateHandle): AddPersonViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        lateinit var vm: AddPersonViewModel
        // Les AndroidViewModel sont construits sur le thread principal.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = AddPersonViewModel(app, handle)
        }
        return vm
    }

    @Test
    fun openedFromCategory_initializesEmptyDynamicLists() {
        val vm = newViewModel(SavedStateHandle(mapOf("categoryId" to "cat-parent-1")))

        // Une seule ligne vide par groupe, avec le type par défaut attendu.
        assertEquals(1, vm.phoneLines.value.size)
        assertTrue(vm.phoneLines.value.first().value.isEmpty())
        assertEquals(FieldTypes.PHONE_MOBILE, vm.phoneLines.value.first().label)

        assertEquals(1, vm.emailLines.value.size)
        assertEquals(FieldTypes.EMAIL_HOME, vm.emailLines.value.first().label)

        assertEquals(1, vm.dateLines.value.size)
        assertEquals(FieldTypes.DATE_BIRTHDAY, vm.dateLines.value.first().label)

        assertEquals(1, vm.relationLines.value.size)
        assertEquals(FieldTypes.RELATION_FRIEND, vm.relationLines.value.first().label)

        // NameDetails vierge.
        assertEquals(NameDetails(), vm.nameDetails.value)
    }

    @Test
    fun openedWithoutCategory_initializesIdenticalDefaults() {
        // Sans categoryId, l'initialisation des listes doit être strictement identique.
        val vm = newViewModel(SavedStateHandle())

        assertEquals(1, vm.phoneLines.value.size)
        assertEquals(FieldTypes.PHONE_MOBILE, vm.phoneLines.value.first().label)
        assertEquals(NameDetails(), vm.nameDetails.value)
    }
}
