package com.jtr.app.ui.category

import android.app.Application
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.jtr.app.R
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.CategoryDao
import com.jtr.app.data.local.CategoryGroupDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.data.local.PersonDao
import com.jtr.app.data.repository.TopOrderRef
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * v7.1.78 — tri « Personnalisé » (CUSTOM) des entrées de premier niveau, rebranché sur le
 * champ `position` que le drag & drop du mode sélection écrivait déjà (fantôme depuis v6.1.3) :
 *
 *  - CUSTOM trie par `position` croissante SEULE : ordre manuel TOTAL (décision HR), une
 *    non-favorite placée avant une favorite y reste ;
 *  - les autres tris sont INCHANGÉS (favoris épinglés en tête, puis critère) ;
 *  - pref inconnue → fallback NAME_ASC ; pref "CUSTOM" persistée → restaurée telle quelle ;
 *  - après un réordonnancement ([CategoryViewModel.persistTopOrder]), la pref bascule AUTO
 *    sur CUSTOM — fin du « ça se re-trie par nom sous les doigts » ;
 *  - le critère « Personnalisé » n'est offert qu'à la RACINE ([categorySortCriteria] avec
 *    `includeCustom = true`) et il est MONO-SENS (chevron masqué, re-tap sans bascule).
 *
 * Pilote le VRAI CategoryViewModel contre des DAO mockés (même grammaire que SearchViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategorySortOrderTest {

    // ── Fabriques d'entrées ─────────────────────────────────────────────────────

    private fun cat(
        name: String,
        position: Int,
        favorite: Boolean = false,
        createdAt: Long = 0L
    ) = TopEntry.Single(
        Category(name = name, position = position, isFavorite = favorite, createdAt = createdAt),
        lastActivity = createdAt
    )

    private fun folder(
        name: String,
        position: Int,
        favorite: Boolean = false,
        createdAt: Long = 0L
    ) = TopEntry.Folder(
        CategoryGroup(name = name, position = position, isFavorite = favorite, createdAt = createdAt),
        members = emptyList(),
        lastActivity = createdAt
    )

    // ── sortTopEntries ──────────────────────────────────────────────────────────

    @Test
    fun custom_sortsByPositionOnly_noFavoritePinning() {
        val entries = listOf(
            cat("Alpha", position = 3, favorite = true), // favorite placée EN DERNIER par le drag
            folder("Dossier", position = 1),
            cat("Zebra", position = 0),
            cat("Milieu", position = 2)
        )
        val sorted = sortTopEntries(entries, CategorySortOrder.CUSTOM)
        // Position croissante seule : la non-favorite « Zebra » passe AVANT la favorite.
        assertThat(sorted.map { it.sortName })
            .containsExactly("zebra", "dossier", "milieu", "alpha").inOrder()
    }

    @Test
    fun nameAsc_unchanged_favoritesStayPinned() {
        val entries = listOf(
            cat("Zebra", position = 0, favorite = true),
            cat("Alpha", position = 1),
            folder("Milieu", position = 2)
        )
        val sorted = sortTopEntries(entries, CategorySortOrder.NAME_ASC)
        // La favorite reste épinglée en tête même si son nom la classerait dernière.
        assertThat(sorted.map { it.sortName })
            .containsExactly("zebra", "alpha", "milieu").inOrder()
    }

    @Test
    fun createdDesc_unchanged_favoritesStayPinned() {
        val entries = listOf(
            cat("Vieille", position = 0, createdAt = 10L),
            cat("Recente", position = 1, createdAt = 100L),
            cat("Fav", position = 2, favorite = true, createdAt = 1L)
        )
        val sorted = sortTopEntries(entries, CategorySortOrder.CREATED_DESC)
        assertThat(sorted.map { it.sortName })
            .containsExactly("fav", "recente", "vieille").inOrder()
    }

    // ── Critères du menu de tri ─────────────────────────────────────────────────

    @Test
    fun criteria_default_excludesCustom_forFolderDetail() {
        val criteria = categorySortCriteria(CategorySortOrder.NAME_ASC) {}
        assertThat(criteria).hasSize(3)
        assertThat(criteria.map { it.labelRes }).doesNotContain(R.string.type_custom)
    }

    @Test
    fun criteria_root_includesCustom_asMonoDirectionFourth() {
        var selected: CategorySortOrder? = null
        val criteria = categorySortCriteria(
            CategorySortOrder.CUSTOM, includeCustom = true
        ) { selected = it }
        assertThat(criteria).hasSize(4)
        val custom = criteria.last()
        assertThat(custom.labelRes).isEqualTo(R.string.type_custom)
        assertThat(custom.selected).isTrue()
        // Mono-sens : pas de chevron, et un re-tap re-sélectionne CUSTOM (pas d'inversion).
        assertThat(custom.showDirection).isFalse()
        custom.onClick()
        assertThat(selected).isEqualTo(CategorySortOrder.CUSTOM)
        // Les 3 critères à deux axes gardent leur chevron.
        assertThat(criteria.dropLast(1).all { it.showDirection }).isTrue()
    }

    // ── ViewModel : pref persistée, fallback, bascule auto ──────────────────────

    private lateinit var editor: SharedPreferences.Editor
    private lateinit var categoryDao: CategoryDao
    private lateinit var groupDao: CategoryGroupDao

    // Même garde que les autres tests de ViewModel : Main est tenu par la règle, drainé
    // avant d'être rendu (cf. [MainDispatcherRule]).
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        val personDao = mockk<PersonDao>(relaxed = true)
        every { personDao.getAllActive() } returns flowOf(emptyList())
        categoryDao = mockk(relaxed = true)
        every { categoryDao.getAll() } returns flowOf(emptyList())
        groupDao = mockk(relaxed = true)
        every { groupDao.getAll() } returns flowOf(emptyList())
        val personCategoryDao = mockk<PersonCategoryDao>(relaxed = true)

        val db = mockk<AppDatabase>(relaxed = true)
        every { db.personDao() } returns personDao
        every { db.categoryDao() } returns categoryDao
        every { db.categoryGroupDao() } returns groupDao
        every { db.personCategoryDao() } returns personCategoryDao
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
    }

    private fun fakeApp(storedSort: String? = null): Application {
        editor = mockk(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null
        every { prefs.getString("categories_sort_order", any()) } returns storedSort
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        val app = mockk<Application>(relaxed = true)
        every { app.getSharedPreferences(any(), any()) } returns prefs
        every { app.applicationContext } returns app
        return app
    }

    @Test
    fun unknownPref_fallsBackToNameAsc() {
        val vm = CategoryViewModel(fakeApp(storedSort = "GARBAGE"))
        assertThat(vm.sortOrder.value).isEqualTo(CategorySortOrder.NAME_ASC)
    }

    @Test
    fun customPref_isRestored() {
        val vm = CategoryViewModel(fakeApp(storedSort = "CUSTOM"))
        assertThat(vm.sortOrder.value).isEqualTo(CategorySortOrder.CUSTOM)
    }

    @Test
    fun persistTopOrder_autoSwitchesSortToCustom_andPersistsIt() {
        val vm = CategoryViewModel(fakeApp(storedSort = null))
        assertThat(vm.sortOrder.value).isEqualTo(CategorySortOrder.NAME_ASC)

        vm.persistTopOrder(listOf(TopOrderRef.Cat("c1"), TopOrderRef.Group(7L)))

        // Bascule AUTO : l'ordre manuel s'applique et reste visible…
        assertThat(vm.sortOrder.value).isEqualTo(CategorySortOrder.CUSTOM)
        // …et il est PERSISTÉ (survit à la fermeture de l'écran).
        verify { editor.putString("categories_sort_order", "CUSTOM") }
        // Le réordonnancement lui-même est bien délégué au repository (position = index).
        coVerify { categoryDao.setPosition("c1", 0) }
        coVerify { groupDao.setPosition(7L, 1) }
    }
}
