package com.jtr.app.ui.search

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.CategoryDao
import com.jtr.app.data.local.CategoryGroupDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.data.local.PersonDao
import com.jtr.app.data.local.SocialLinkDao
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.domain.model.Person
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests du ViewModel de l'écran de recherche dédié (v7.1.69) : le filtrage réutilise le
 * moteur central `utils.matchesSearch` (ET sur tokens / OU sur champs). On vérifie :
 *  - requête vide → liste complète ;
 *  - requête → liste filtrée (accent/casse-insensible) via le moteur central ;
 *  - requête sans correspondance → vide ;
 *  - recents = ce que fournit [PersonRepository.getRecent].
 *
 * v7.1.73 — recherche HARMONISÉE : mêmes vérifications pour les catégories et les dossiers
 * ([SearchViewModel.categoryResults]/[groupResults], moteur `matchesAllTokens` — mêmes tokens),
 * plus l'EXCLUSION d'une catégorie en corbeille et le vide garanti sur requête vide.
 *
 * Pilote le VRAI SearchViewModel + les VRAIS repositories contre des DAO mockés. Le flux de
 * résultats passe par `flowOn(Dispatchers.Default)` (vrai thread) + un `debounce` : on garde un
 * collecteur actif (WhileSubscribed) et on SONDE la [StateFlow] jusqu'au résultat attendu (deadline).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val activeFlow = MutableStateFlow<List<Person>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    private val groupsFlow = MutableStateFlow<List<CategoryGroup>>(emptyList())

    /** v7.1.85 — membres de la catégorie de périmètre (source de la recherche SCOPÉE). */
    private val membersFlow = MutableStateFlow<List<Person>>(emptyList())

    /** v7.1.87 — contacts du SOUS-ARBRE d'un dossier (source de la recherche scopée dossier). */
    private val folderMembersFlow = MutableStateFlow<List<Person>>(emptyList())

    @Before
    fun setUp() {
        // viewModelScope = Main.immediate ; Unconfined exécute inline mais respecte les delays réels.
        Dispatchers.setMain(Dispatchers.Unconfined)

        val personDao = mockk<PersonDao>(relaxed = true)
        every { personDao.getAllActive() } returns activeFlow
        every { personDao.getRecent(any()) } returns flowOf(listOf(person("Zoe", opened = 100)))
        val socialDao = mockk<SocialLinkDao>(relaxed = true)
        every { socialDao.getAll() } returns flowOf(emptyList())
        val categoryDao = mockk<CategoryDao>(relaxed = true)
        every { categoryDao.getAll() } returns categoriesFlow
        val groupDao = mockk<CategoryGroupDao>(relaxed = true)
        every { groupDao.getAll() } returns groupsFlow
        val personCategoryDao = mockk<PersonCategoryDao>(relaxed = true)
        every { personCategoryDao.getActiveCategoryJoins() } returns flowOf(emptyList())
        // v7.1.85 — source du mode SCOPÉ (PersonRepository.getByCategory → ce DAO).
        every { personCategoryDao.getActivePersonsInCategory(SCOPED_ID) } returns membersFlow
        // v7.1.87 — source du périmètre DOSSIER (PersonRepository.getByCategories → ce DAO).
        every { personCategoryDao.getActivePersonsInCategories(any()) } returns folderMembersFlow

        val db = mockk<AppDatabase>(relaxed = true)
        every { db.personDao() } returns personDao
        every { db.socialLinkDao() } returns socialDao
        every { db.categoryDao() } returns categoryDao
        every { db.categoryGroupDao() } returns groupDao
        every { db.personCategoryDao() } returns personCategoryDao
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getInstance(any()) } returns db
    }

    @After
    fun tearDown() {
        unmockkObject(AppDatabase.Companion)
        Dispatchers.resetMain()
    }

    private fun person(name: String, opened: Long? = null) =
        Person(firstName = name, lastContactedAt = opened)

    private fun fakeApp(): Application {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null  // défauts : DETAIL + NAME_ASC
        val app = mockk<Application>(relaxed = true)
        every { app.getSharedPreferences(any(), any()) } returns prefs
        every { app.applicationContext } returns app
        every { app.getString(any()) } returns ""
        return app
    }

    /** Garde un collecteur actif puis sonde la valeur jusqu'à [predicate] (ou échec après ~2 s). */
    private fun <T> awaitValue(flow: StateFlow<T>, predicate: (T) -> Boolean): T = runBlocking {
        val job: Job = launch(Dispatchers.Unconfined) { flow.collect { } }
        try {
            val deadline = System.currentTimeMillis() + 2000
            while (System.currentTimeMillis() < deadline) {
                if (predicate(flow.value)) return@runBlocking flow.value
                Thread.sleep(10)
            }
            flow.value
        } finally {
            job.cancel()
        }
    }

    @Test
    fun emptyQuery_returnsAllActive() {
        activeFlow.value = listOf(person("Alice"), person("Bob"), person("Charlie"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        val res = awaitValue(vm.results) { it.size == 3 }
        assertThat(res.map { it.firstName }).containsExactly("Alice", "Bob", "Charlie")
    }

    @Test
    fun query_filtersViaCentralEngine_caseInsensitive() {
        activeFlow.value = listOf(person("Alice"), person("Bob"), person("Alicia"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        vm.onQueryChanged("ALI")
        val res = awaitValue(vm.results) { r -> r.map { it.firstName }.toSet() == setOf("Alice", "Alicia") }
        assertThat(res.map { it.firstName }).containsExactly("Alice", "Alicia")
    }

    @Test
    fun query_noMatch_returnsEmpty() {
        activeFlow.value = listOf(person("Alice"), person("Bob"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        vm.onQueryChanged("zzzz")
        val res = awaitValue(vm.results) { it.isEmpty() && vm.query.value == "zzzz" }
        // Laisse le debounce (250 ms) s'appliquer puis vérifie l'absence de résultat.
        assertThat(res).isEmpty()
    }

    @Test
    fun recents_exposedFromRepository() {
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        val res = awaitValue(vm.recents) { it.isNotEmpty() }
        assertThat(res.map { it.firstName }).containsExactly("Zoe")
    }

    // ── v7.1.73 — recherche harmonisée : contacts + catégories + dossiers ──────────

    @Test
    fun query_matchesAllThreeTypes_inTheirSections() {
        activeFlow.value = listOf(person("Amine"), person("Bob"))
        categoriesFlow.value = listOf(Category(name = "Amis"), Category(name = "Travail"))
        groupsFlow.value = listOf(CategoryGroup(id = 1, name = "Amicale"), CategoryGroup(id = 2, name = "Pro"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        vm.onQueryChanged("ami")
        val contacts = awaitValue(vm.results) { r -> r.map { it.firstName } == listOf("Amine") }
        val cats = awaitValue(vm.categoryResults) { it.size == 1 }
        val groups = awaitValue(vm.groupResults) { it.size == 1 }
        assertThat(contacts.map { it.firstName }).containsExactly("Amine")
        assertThat(cats.map { it.name }).containsExactly("Amis")
        assertThat(groups.map { it.name }).containsExactly("Amicale")
    }

    @Test
    fun trashedCategory_matchingName_isExcluded() {
        // Le DAO (getAll, `deletedAt IS NULL`) ne renvoie jamais de catégorie en corbeille ;
        // on en injecte une pour vérifier que le contrat v7.1.71 est AUSSI garanti côté VM.
        categoriesFlow.value = listOf(
            Category(name = "Amis"),
            Category(name = "Amis d'enfance", deletedAt = 123L)
        )
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        vm.onQueryChanged("amis")
        val cats = awaitValue(vm.categoryResults) { it.size == 1 && vm.query.value == "amis" }
        assertThat(cats.map { it.name }).containsExactly("Amis")
    }

    @Test
    fun query_noMatch_categoriesAndGroupsEmpty() {
        categoriesFlow.value = listOf(Category(name = "Amis"))
        groupsFlow.value = listOf(CategoryGroup(id = 1, name = "Pro"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        vm.onQueryChanged("zzzz")
        val cats = awaitValue(vm.categoryResults) { it.isEmpty() && vm.query.value == "zzzz" }
        val groups = awaitValue(vm.groupResults) { it.isEmpty() && vm.query.value == "zzzz" }
        assertThat(cats).isEmpty()
        assertThat(groups).isEmpty()
    }

    @Test
    fun emptyQuery_categoriesAndGroupsEmpty_recentsUntouched() {
        categoriesFlow.value = listOf(Category(name = "Amis"))
        groupsFlow.value = listOf(CategoryGroup(id = 1, name = "Amicale"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        // Transition réelle : frappe (sections pleines) puis EFFACEMENT → sections vides
        // (l'état vide de l'écran reste la rangée Récents, inchangée).
        vm.onQueryChanged("ami")
        awaitValue(vm.categoryResults) { it.isNotEmpty() }
        awaitValue(vm.groupResults) { it.isNotEmpty() }
        vm.onQueryChanged("")
        val cats = awaitValue(vm.categoryResults) { it.isEmpty() }
        val groups = awaitValue(vm.groupResults) { it.isEmpty() }
        assertThat(cats).isEmpty()
        assertThat(groups).isEmpty()
        val recents = awaitValue(vm.recents) { it.isNotEmpty() }
        assertThat(recents.map { it.firstName }).containsExactly("Zoe")
    }

    @Test
    fun categoryAndGroupMatching_usesCentralEngine_accentAndCaseInsensitive() {
        categoriesFlow.value = listOf(Category(name = "Collègues"), Category(name = "Famille"))
        groupsFlow.value = listOf(CategoryGroup(id = 1, name = "Collège Sainte-Thérèse"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        // « COLLEGE » sans accent ni casse : le moteur central (normalizeForSearch) doit matcher
        // « Collègues » ET « Collège Sainte-Thérèse » — cohérent avec la recherche de contacts.
        vm.onQueryChanged("COLLEG")
        val cats = awaitValue(vm.categoryResults) { it.size == 1 }
        val groups = awaitValue(vm.groupResults) { it.size == 1 }
        assertThat(cats.map { it.name }).containsExactly("Collègues")
        assertThat(groups.map { it.name }).containsExactly("Collège Sainte-Thérèse")
    }

    // ── v7.1.85 — recherche SCOPÉE à une catégorie ────────────────────────────────

    /** Scopé, requête vide : la liste est celle des MEMBRES, pas tous les contacts actifs. */
    @Test
    fun scoped_emptyQuery_showsOnlyCategoryMembers() {
        activeFlow.value = listOf(person("Alice"), person("Bob"), person("Carla"))
        membersFlow.value = listOf(person("Alice"), person("Carla"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("categoryId" to SCOPED_ID)))
        assertThat(vm.isScoped).isTrue()
        val res = awaitValue(vm.results) { it.size == 2 }
        assertThat(res.map { it.firstName }).containsExactly("Alice", "Carla")
    }

    /**
     * Scopé, requête saisie : le filtre ne voit QUE les membres — un contact d'une AUTRE
     * catégorie qui matcherait la requête n'apparaît JAMAIS (garantie centrale du lot).
     */
    @Test
    fun scoped_query_neverLeaksContactOutsideTheCategory() {
        // « Bob » matche « bo » mais n'est PAS membre : il ne doit pas remonter.
        activeFlow.value = listOf(person("Bob"), person("Boris"))
        membersFlow.value = listOf(person("Boris"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("categoryId" to SCOPED_ID)))
        vm.onQueryChanged("bo")
        val res = awaitValue(vm.results) { r -> r.map { it.firstName } == listOf("Boris") }
        assertThat(res.map { it.firstName }).containsExactly("Boris")
        assertThat(res.map { it.firstName }).doesNotContain("Bob")
    }

    /** Scopé : ni récents globaux, ni sections Catégories/Dossiers (hors périmètre). */
    @Test
    fun scoped_hidesRecentsAndCrossSections() {
        membersFlow.value = listOf(person("Alice"))
        categoriesFlow.value = listOf(Category(name = "Amis"))
        groupsFlow.value = listOf(CategoryGroup(id = 1, name = "Amicale"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("categoryId" to SCOPED_ID)))
        vm.onQueryChanged("ami")
        awaitValue(vm.results) { vm.query.value == "ami" }
        assertThat(vm.recents.value).isEmpty()
        assertThat(vm.categoryResults.value).isEmpty()
        assertThat(vm.groupResults.value).isEmpty()
    }

    /** Sans argument de route : comportement GLOBAL d'origine (non-régression accueil). */
    @Test
    fun unscoped_isGlobal_asBefore() {
        activeFlow.value = listOf(person("Alice"), person("Bob"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle())
        assertThat(vm.isScoped).isFalse()
        val res = awaitValue(vm.results) { it.size == 2 }
        assertThat(res.map { it.firstName }).containsExactly("Alice", "Bob")
    }

    // ── v7.1.87 — recherche SCOPÉE à un DOSSIER (sous-arbre complet) ──────────────

    /**
     * Le sous-arbre descend à TOUTE profondeur : catégories du dossier, de ses sous-dossiers,
     * et des sous-sous-dossiers. Une catégorie hors du dossier, ou à la racine, est exclue.
     */
    @Test
    fun categoryIdsUnderGroup_collectsWholeSubtree() {
        val groups = listOf(
            CategoryGroup(id = 1, name = "Racine"),
            CategoryGroup(id = 2, name = "Enfant", parentGroupId = 1),
            CategoryGroup(id = 3, name = "Petit-enfant", parentGroupId = 2),
            CategoryGroup(id = 9, name = "Ailleurs")
        )
        val cats = listOf(
            Category(id = "a", name = "A", parentGroupId = 1),
            Category(id = "b", name = "B", parentGroupId = 2),
            Category(id = "c", name = "C", parentGroupId = 3),
            Category(id = "x", name = "X", parentGroupId = 9),
            Category(id = "r", name = "R")  // racine, sans dossier
        )
        assertThat(categoryIdsUnderGroup(1L, cats, groups)).containsExactly("a", "b", "c")
    }

    /** Hiérarchie accidentellement cyclique : la collecte s'arrête au lieu de boucler. */
    @Test
    fun categoryIdsUnderGroup_cycleDoesNotHang() {
        val groups = listOf(
            CategoryGroup(id = 1, name = "Un", parentGroupId = 2),
            CategoryGroup(id = 2, name = "Deux", parentGroupId = 1)
        )
        val cats = listOf(Category(id = "a", name = "A", parentGroupId = 2))
        assertThat(categoryIdsUnderGroup(1L, cats, groups)).containsExactly("a")
    }

    /** Scopé dossier, requête vide : les contacts du dossier, pas tous les contacts actifs. */
    @Test
    fun folderScoped_emptyQuery_showsOnlyFolderContacts() {
        activeFlow.value = listOf(person("Alice"), person("Bob"), person("Carla"))
        categoriesFlow.value = listOf(Category(id = "a", name = "A", parentGroupId = 7))
        groupsFlow.value = listOf(CategoryGroup(id = 7, name = "Carrefour"))
        folderMembersFlow.value = listOf(person("Alice"), person("Carla"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("groupId" to "7")))
        assertThat(vm.isScoped).isTrue()
        assertThat(vm.scopedGroupId).isEqualTo(7L)
        val res = awaitValue(vm.results) { it.size == 2 }
        assertThat(res.map { it.firstName }).containsExactly("Alice", "Carla")
    }

    /** Scopé dossier : un contact hors du dossier ne remonte JAMAIS, même s'il matche. */
    @Test
    fun folderScoped_query_neverLeaksContactOutsideTheFolder() {
        activeFlow.value = listOf(person("Bob"), person("Boris"))
        categoriesFlow.value = listOf(Category(id = "a", name = "A", parentGroupId = 7))
        groupsFlow.value = listOf(CategoryGroup(id = 7, name = "Carrefour"))
        folderMembersFlow.value = listOf(person("Boris"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("groupId" to "7")))
        vm.onQueryChanged("bo")
        val res = awaitValue(vm.results) { r -> r.map { it.firstName } == listOf("Boris") }
        assertThat(res.map { it.firstName }).containsExactly("Boris")
        assertThat(res.map { it.firstName }).doesNotContain("Bob")
    }

    /** Scopé dossier : ni récents globaux, ni sections Catégories/Dossiers. */
    @Test
    fun folderScoped_hidesRecentsAndCrossSections() {
        categoriesFlow.value = listOf(Category(id = "a", name = "Amis", parentGroupId = 7))
        groupsFlow.value = listOf(CategoryGroup(id = 7, name = "Amicale"))
        folderMembersFlow.value = listOf(person("Alice"))
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("groupId" to "7")))
        vm.onQueryChanged("ami")
        awaitValue(vm.results) { vm.query.value == "ami" }
        assertThat(vm.recents.value).isEmpty()
        assertThat(vm.categoryResults.value).isEmpty()
        assertThat(vm.groupResults.value).isEmpty()
    }

    /** Non-régression du lot 1 : le périmètre CATÉGORIE reste exclusif du périmètre dossier. */
    @Test
    fun categoryScope_leavesGroupScopeNull() {
        val vm = SearchViewModel(fakeApp(), SavedStateHandle(mapOf("categoryId" to SCOPED_ID)))
        assertThat(vm.scopedGroupId).isNull()
        assertThat(vm.scopedCategoryId).isEqualTo(SCOPED_ID)
    }

    private companion object {
        const val SCOPED_ID = "cat-scope-1"
    }
}
