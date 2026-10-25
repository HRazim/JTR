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
 * v7.1.101 — tri d'AFFICHAGE de la galerie par date de création, puis v7.1.103 — par date
 * de MODIFICATION (aucune migration, `modifiedAt` posé en v25) :
 *
 *  - [sortGalleryImages] ordonne par `createdAt`, ou par `COALESCE(modifiedAt, createdAt)`
 *    pour le critère « modifié » (une image jamais éditée se classe par sa création), dans
 *    le sens choisi ; tri STABLE : à clé égale, l'ordre DAO (`position`/`createdAt`) départage ;
 *  - [gallerySortCriteria] expose « Date de modification » puis « Date de création » (ordre
 *    de l'Accueil/Catégories), modèle à deux axes : re-tap du critère actif = inversion ;
 *  - pref `gallery_sort` : absente ou inconnue → fallback CREATED_DESC (v7.1.109, plus
 *    récent en haut) ; persistée → restaurée telle quelle ; [GalleryViewModel.setSortOrder]
 *    écrit la pref ; le flow [GalleryViewModel.images] rediffuse la liste dans le tri choisi.
 *
 * Pilote le VRAI GalleryViewModel contre des DAO mockés (même grammaire que
 * CategorySortOrderTest / SearchViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GallerySortOrderTest {

    // ── Fabrique d'images ───────────────────────────────────────────────────────

    private fun img(id: Long, createdAt: Long, position: Int = 0, modifiedAt: Long? = null) =
        GalleryImage(
            id = id, personId = "p1", path = "f$id.jpg", position = position,
            createdAt = createdAt, modifiedAt = modifiedAt
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

    // ── Tri « Date de modification » (v7.1.103) — COALESCE(modifiedAt, createdAt) ──

    @Test
    fun modifiedDesc_editedLeads_neverEditedFallsBackToCreatedAt() {
        val images = listOf(
            img(1, createdAt = 100),                            // jamais éditée → clé 100
            img(2, createdAt = 300),                            // jamais éditée → clé 300
            img(3, createdAt = 200, modifiedAt = 900),          // éditée        → clé 900
            img(4, createdAt = 400, modifiedAt = 250)           // éditée        → clé 250
        )
        val sorted = sortGalleryImages(images, GallerySortOrder.MODIFIED_DESC)
        // 900 (éditée) > 300 (création) > 250 (éditée) > 100 (création) : le COALESCE
        // entremêle bien les deux populations sur une seule échelle.
        assertThat(sorted.map { it.id }).containsExactly(3L, 2L, 4L, 1L).inOrder()
    }

    @Test
    fun modifiedAsc_isTheExactReverse() {
        val images = listOf(
            img(1, createdAt = 100),
            img(2, createdAt = 300),
            img(3, createdAt = 200, modifiedAt = 900),
            img(4, createdAt = 400, modifiedAt = 250)
        )
        val sorted = sortGalleryImages(images, GallerySortOrder.MODIFIED_ASC)
        assertThat(sorted.map { it.id }).containsExactly(1L, 4L, 2L, 3L).inOrder()
    }

    @Test
    fun modifiedSort_equalKeys_tieKeepsDaoOrder() {
        // Une éditée et une jamais-éditée peuvent partager la MÊME clé (modifiedAt de l'une
        // == createdAt de l'autre) : l'ordre DAO (position) départage, dans les deux sens.
        val daoOrder = listOf(
            img(10, createdAt = 500, position = 0),
            img(11, createdAt = 100, position = 1, modifiedAt = 500)
        )
        assertThat(sortGalleryImages(daoOrder, GallerySortOrder.MODIFIED_ASC).map { it.id })
            .containsExactly(10L, 11L).inOrder()
        assertThat(sortGalleryImages(daoOrder, GallerySortOrder.MODIFIED_DESC).map { it.id })
            .containsExactly(10L, 11L).inOrder()
    }

    // ── Critères du menu de tri ─────────────────────────────────────────────────

    @Test
    fun criteria_modifiedThenCreated_selectionAndTapInvertPerCriterion() {
        var selected: GallerySortOrder? = null
        // Ordre des critères aligné sur l'Accueil/Catégories : Modification puis Création.
        val onCreatedAsc = gallerySortCriteria(GallerySortOrder.CREATED_ASC) { selected = it }
        assertThat(onCreatedAsc).hasSize(2)
        assertThat(onCreatedAsc[0].labelRes).isEqualTo(R.string.sort_criterion_modified)
        assertThat(onCreatedAsc[1].labelRes).isEqualTo(R.string.sort_criterion_created)
        // Seule « Date de création » est active ; son re-tap inverse le sens.
        assertThat(onCreatedAsc[0].selected).isFalse()
        assertThat(onCreatedAsc[1].selected).isTrue()
        assertThat(onCreatedAsc[1].direction).isEqualTo(JtrSortDirection.ASC)
        onCreatedAsc[1].onClick()
        assertThat(selected).isEqualTo(GallerySortOrder.CREATED_DESC)
        // Taper l'AUTRE critère le sélectionne à son sens par défaut (descendant).
        onCreatedAsc[0].onClick()
        assertThat(selected).isEqualTo(GallerySortOrder.MODIFIED_DESC)

        // Côté « Date de modification » active : re-tap = inversion, et l'autre critère
        // repart à son sens par défaut.
        val onModifiedDesc = gallerySortCriteria(GallerySortOrder.MODIFIED_DESC) { selected = it }
        assertThat(onModifiedDesc[0].selected).isTrue()
        assertThat(onModifiedDesc[0].direction).isEqualTo(JtrSortDirection.DESC)
        assertThat(onModifiedDesc[1].selected).isFalse()
        onModifiedDesc[0].onClick()
        assertThat(selected).isEqualTo(GallerySortOrder.MODIFIED_ASC)
        onModifiedDesc[1].onClick()
        assertThat(selected).isEqualTo(GallerySortOrder.CREATED_DESC)
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
    fun unknownOrMissingPref_fallsBackToCreatedDesc() {
        // v7.1.109 — le DÉFAUT (pref absente ou illisible) est CREATED_DESC ; une pref
        // stockée, elle, reste lue telle quelle (test suivant).
        assertThat(vm(storedSort = "GARBAGE").sortOrder.value)
            .isEqualTo(GallerySortOrder.CREATED_DESC)
        assertThat(vm(storedSort = null).sortOrder.value)
            .isEqualTo(GallerySortOrder.CREATED_DESC)
    }

    @Test
    fun storedPref_isRestored() {
        assertThat(vm(storedSort = "CREATED_DESC").sortOrder.value)
            .isEqualTo(GallerySortOrder.CREATED_DESC)
        assertThat(vm(storedSort = "MODIFIED_DESC").sortOrder.value)
            .isEqualTo(GallerySortOrder.MODIFIED_DESC)
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
