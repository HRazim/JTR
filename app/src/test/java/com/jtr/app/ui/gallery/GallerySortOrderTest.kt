package com.jtr.app.ui.gallery

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jtr.app.R
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.GalleryImageDao
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.GalleryImage
import com.jtr.app.ui.components.JtrSortDirection
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * v7.1.101 — tri d'AFFICHAGE de la galerie par date de création (aucune migration) :
 *
 *  - [sortGalleryImages] ordonne par `createdAt` dans le sens choisi, et le tri est STABLE :
 *    à date égale, l'ordre DAO (`position` puis `createdAt`) départage ;
 *  - [gallerySortCriteria] expose l'UNIQUE critère « Date de création » dans le même modèle
 *    à deux axes que l'Accueil/Catégories : toujours sélectionné, re-tap = inversion du sens ;
 *  - pref `gallery_sort` : inconnue → fallback CREATED_ASC (l'ordre historique du DAO,
 *    aucun changement perçu) ; persistée → restaurée ; [GalleryViewModel.setSortOrder]
 *    écrit la pref ; le flow [GalleryViewModel.images] rediffuse la liste dans le tri choisi.
 *
 * Pilote le VRAI GalleryViewModel contre des DAO mockés (même grammaire que
 * CategorySortOrderTest / SearchViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GallerySortOrderTest {

    // ── Fabrique d'images ───────────────────────────────────────────────────────

    private fun img(id: Long, createdAt: Long, position: Int = 0) = GalleryImage(
        id = id, personId = "p1", path = "f$id.jpg", position = position, createdAt = createdAt
    )

    // ── sortGalleryImages ───────────────────────────────────────────────────────

    @Test
    fun createdAsc_oldestFirst() {
        val images = listOf(img(1, createdAt = 300), img(2, createdAt = 100), img(3, createdAt = 200))
        val sorted = sortGalleryImages(images, GallerySortOrder.CREATED_ASC)
        assertThat(sorted.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun createdDesc_newestFirst() {
        val images = listOf(img(1, createdAt = 300), img(2, createdAt = 100), img(3, createdAt = 200))
        val sorted = sortGalleryImages(images, GallerySortOrder.CREATED_DESC)
        assertThat(sorted.map { it.id }).containsExactly(1L, 3L, 2L).inOrder()
    }

    @Test
    fun equalCreatedAt_tieKeepsDaoOrder_bothDirections() {
        // Même import (createdAt identique) : l'ordre DAO (position) doit survivre au tri.
        val daoOrder = listOf(
            img(10, createdAt = 500, position = 0),
            img(11, createdAt = 500, position = 1),
            img(12, createdAt = 500, position = 2)
        )
        assertThat(sortGalleryImages(daoOrder, GallerySortOrder.CREATED_ASC).map { it.id })
            .containsExactly(10L, 11L, 12L).inOrder()
        assertThat(sortGalleryImages(daoOrder, GallerySortOrder.CREATED_DESC).map { it.id })
            .containsExactly(10L, 11L, 12L).inOrder()
    }

    // ── Critère du menu de tri ──────────────────────────────────────────────────

    @Test
    fun criteria_singleCreatedCriterion_alwaysSelected_tapInverts() {
        var selected: GallerySortOrder? = null
        val asc = gallerySortCriteria(GallerySortOrder.CREATED_ASC) { selected = it }
        assertThat(asc).hasSize(1)
        assertThat(asc.single().labelRes).isEqualTo(R.string.sort_criterion_created)
        assertThat(asc.single().selected).isTrue()
        assertThat(asc.single().direction).isEqualTo(JtrSortDirection.ASC)
        // Re-tap du critère actif = INVERSION du sens (modèle à deux axes, v6.2.6).
        asc.single().onClick()
        assertThat(selected).isEqualTo(GallerySortOrder.CREATED_DESC)

        val desc = gallerySortCriteria(GallerySortOrder.CREATED_DESC) { selected = it }
        assertThat(desc.single().direction).isEqualTo(JtrSortDirection.DESC)
        desc.single().onClick()
        assertThat(selected).isEqualTo(GallerySortOrder.CREATED_ASC)
    }

    // ── ViewModel : pref persistée, fallback, flow trié ─────────────────────────

    private lateinit var editor: SharedPreferences.Editor
    private lateinit var galleryDao: GalleryImageDao

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)

        galleryDao = mockk(relaxed = true)
        every { galleryDao.observeByPerson("p1") } returns flowOf(emptyList())
        val personDao = mockk<PersonDao>(relaxed = true)
        every { personDao.observeById(any()) } returns flowOf(null)

        val db = mockk<AppDatabase>(relaxed = true)
        every { db.galleryImageDao() } returns galleryDao
        every { db.personDao() } returns personDao
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
        Dispatchers.resetMain()
    }

    private fun fakeApp(storedSort: String? = null): Application {
        editor = mockk(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getString("gallery_sort", any()) } returns storedSort
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        val app = mockk<Application>(relaxed = true)
        every { app.getSharedPreferences(any(), any()) } returns prefs
        every { app.applicationContext } returns app
        return app
    }

    private fun vm(storedSort: String? = null) =
        GalleryViewModel(fakeApp(storedSort), SavedStateHandle(mapOf("personId" to "p1")))

    private fun <T> awaitValue(flow: StateFlow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(2_000) { flow.first(predicate) } }

    @Test
    fun unknownPref_fallsBackToCreatedAsc() {
        assertThat(vm(storedSort = "GARBAGE").sortOrder.value)
            .isEqualTo(GallerySortOrder.CREATED_ASC)
    }

    @Test
    fun storedPref_isRestored() {
        assertThat(vm(storedSort = "CREATED_DESC").sortOrder.value)
            .isEqualTo(GallerySortOrder.CREATED_DESC)
    }

    @Test
    fun setSortOrder_persistsPref() {
        val vm = vm()
        vm.setSortOrder(GallerySortOrder.CREATED_DESC)
        assertThat(vm.sortOrder.value).isEqualTo(GallerySortOrder.CREATED_DESC)
        verify { editor.putString("gallery_sort", "CREATED_DESC") }
    }

    @Test
    fun imagesFlow_emitsInChosenSort_andReordersOnChange() {
        every { galleryDao.observeByPerson("p1") } returns flowOf(
            listOf(img(1, createdAt = 100), img(2, createdAt = 300), img(3, createdAt = 200))
        )
        val vm = vm(storedSort = "CREATED_DESC")
        val desc = awaitValue(vm.images) { it.size == 3 }
        assertThat(desc.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()

        vm.setSortOrder(GallerySortOrder.CREATED_ASC)
        val asc = awaitValue(vm.images) { it.map(GalleryImage::id) == listOf(1L, 3L, 2L) }
        assertThat(asc.map { it.id }).containsExactly(1L, 3L, 2L).inOrder()
    }
}
