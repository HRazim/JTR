package com.jtr.app.ui.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.ui.navigation.Routes
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.ui.category.ContactSortOrder
import com.jtr.app.ui.category.sortPersonsBy
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.utils.matchesAllTokens
import com.jtr.app.utils.matchesSearch
import com.jtr.app.utils.normalizeForSearch
import com.jtr.app.utils.searchTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel de l'écran de RECHERCHE dédié (v7.1.69).
 *
 * Réutilise le MÊME moteur central que l'accueil ([utils.matchesSearch], tokenisé,
 * accent/casse-insensible, débouncé hors thread principal) et le MÊME tri ([sortPersonsBy]) —
 * aucune logique de recherche dupliquée. Expose :
 *  - [recents] : contacts récemment ouverts (déplacé de l'accueil, [PersonRepository.getRecent]),
 *    visible seulement quand la requête est vide ;
 *  - [results] : liste active filtrée par la requête (ou liste complète si requête vide) ;
 *  - [categoryResults] / [groupResults] (v7.1.73) : catégories ACTIVES et dossiers dont le nom
 *    matche la requête, via le même moteur ([matchesAllTokens], mêmes tokens normalisés).
 *
 * MVVM/UDF strict : l'UI n'expose que des [StateFlow] en lecture seule. Room v22, aucune migration.
 */
class SearchViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)
    private val categoryRepository = CategoryRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    /**
     * v7.1.85 — PÉRIMÈTRE de la recherche. `null` (argument de route absent) = recherche
     * GLOBALE, soit exactement l'écran de l'accueil depuis v7.1.69. Non nul = recherche
     * SCOPÉE aux contacts de cette catégorie : seule la SOURCE change, le moteur de
     * filtrage reste le même.
     */
    val scopedCategoryId: String? = savedStateHandle[Routes.ARG_SEARCH_CATEGORY_ID]
    val isScoped: Boolean = scopedCategoryId != null

    /** Nom de la catégorie de périmètre (titre/placeholder contextuels) ; vide si global. */
    val scopedCategoryName: StateFlow<String> =
        if (scopedCategoryId == null) MutableStateFlow("")
        else categoryRepository.getAllActive()
            .map { list -> list.firstOrNull { it.id == scopedCategoryId }?.name ?: "" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun onQueryChanged(q: String) { _query.value = q }

    /**
     * Mode d'affichage MIROIR de l'écran d'origine (lecture seule) : l'accueil en global,
     * le détail de catégorie en scopé — la page ressemble à l'écran d'où l'on vient.
     */
    val viewMode: JtrViewMode = JtrViewMode.fromPref(
        prefs.getString(if (isScoped) "category_detail_view_mode" else "home_view_mode", null),
        JtrViewMode.DETAIL
    )

    /**
     * Tri MIROIR de l'accueil (lecture seule) : la liste complète apparaît comme sur l'accueil.
     * En mode SCOPÉ, tri par défaut (nom A→Z) : le tri par catégorie (`cat_sort_<id>`) est
     * délibérément NON couplé ici — il changera peut-être de forme (harmonisation à venir).
     */
    private val sortOrder: ContactSortOrder =
        if (isScoped) ContactSortOrder.NAME_ASC
        else runCatching { ContactSortOrder.valueOf(prefs.getString("home_sort_order", null) ?: "") }
            .getOrDefault(ContactSortOrder.NAME_ASC)

    /**
     * Contacts récemment ouverts (tri `lastContactedAt` DESC, plafonné). Vide tant que rien ouvert.
     * En mode SCOPÉ : AUCUN récent (ils sont globaux — hors périmètre) ; l'état « requête vide »
     * montre alors les MEMBRES de la catégorie, via [results].
     */
    val recents: StateFlow<List<Person>> =
        if (isScoped) MutableStateFlow(emptyList())
        else repository.getRecent(RECENTS_LIMIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val socialLinksMap: StateFlow<Map<String, List<SocialLinkEntity>>> =
        repository.getAllSocialLinks()
            .map { links -> links.groupBy { it.personId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Libellés localisés des types de relation (clé → libellé normalisé), comme l'accueil :
    // taper « Ami »/« Collègue » matche les profils portant ce type de relation.
    private val relationTypeLabels: Map<String, String> =
        FieldTypes.RELATION.filter { it.key != FieldTypes.CUSTOM }
            .associate { it.key to application.getString(it.labelRes).normalizeForSearch() }

    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> =
        _query.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    /**
     * Résultats : liste active filtrée via le moteur central (ET sur tokens / OU sur champs).
     * Requête vide → liste complète. Filtrage + tri hors thread principal ([flowOn]).
     */
    val results: StateFlow<List<Person>> = combine(
        // v7.1.85 — SEULE la source change selon le périmètre ; le filtrage, le tri et le
        // débounce restent identiques. Requête vide ⇒ la source entière, c'est-à-dire la
        // liste complète (global) ou les MEMBRES de la catégorie (scopé).
        if (scopedCategoryId == null) repository.getAllActive()
        else repository.getByCategory(scopedCategoryId),
        debouncedQuery
    ) { list, q ->
        val tokens = q.searchTokens()
        val filtered = if (tokens.isEmpty()) list
            else list.filter { it.matchesSearch(tokens) { key -> relationTypeLabels[key] } }
        sortPersonsBy(filtered, sortOrder)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Catégories correspondantes (v7.1.73) : catégories ACTIVES dont le nom matche la requête,
     * via le MÊME moteur central ([matchesAllTokens], tokens identiques aux contacts). Requête
     * vide → AUCUN résultat : l'état vide de l'écran reste la rangée Récents, inchangée.
     * La source [CategoryRepository.getAllActive] exclut déjà la corbeille en SQL
     * (`deletedAt IS NULL`) ; le filtre `deletedAt == null` redouble ici ce contrat v7.1.71
     * (explicite et testable) — une catégorie jetée ne peut JAMAIS apparaître en résultat.
     */
    val categoryResults: StateFlow<List<Category>> =
        if (isScoped) MutableStateFlow(emptyList())
        else combine(
            categoryRepository.getAllActive(), debouncedQuery
        ) { list, q ->
            val tokens = q.searchTokens()
            if (tokens.isEmpty()) emptyList()
            else list.filter { it.deletedAt == null && it.name.matchesAllTokens(tokens) }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Dossiers correspondants (v7.1.73) : même moteur sur le nom. Les dossiers n'ont pas de
     * corbeille (une suppression les dissout immédiatement) → [CategoryRepository.getGroups]
     * ne renvoie que des dossiers vivants. Requête vide → aucun résultat.
     */
    val groupResults: StateFlow<List<CategoryGroup>> =
        if (isScoped) MutableStateFlow(emptyList())
        else combine(
            categoryRepository.getGroups(), debouncedQuery
        ) { list, q ->
            val tokens = q.searchTokens()
            if (tokens.isEmpty()) emptyList()
            else list.filter { it.name.matchesAllTokens(tokens) }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nombre de contacts ACTIFS par catégorie — compteur des lignes de résultat (v7.1.71). */
    val categoryCounts: StateFlow<Map<String, Int>> =
        categoryRepository.getPersonCountsPerCategory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    companion object {
        /** Nombre maximum de contacts dans la section « Récents ». */
        private const val RECENTS_LIMIT = 8

        /** Délai d'inactivité avant de relancer le filtre (ms) — aligné sur l'accueil. */
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
