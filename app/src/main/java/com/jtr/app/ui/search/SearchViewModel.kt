package com.jtr.app.ui.search

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.ui.category.ContactSortOrder
import com.jtr.app.ui.category.sortPersonsBy
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.person.FieldTypes
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
 *  - [results] : liste active filtrée par la requête (ou liste complète si requête vide).
 *
 * MVVM/UDF strict : l'UI n'expose que des [StateFlow] en lecture seule. Room v22, aucune migration.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun onQueryChanged(q: String) { _query.value = q }

    /** Mode d'affichage MIROIR de l'accueil (lecture seule) : même rendu Liste/Détail/Grille. */
    val viewMode: JtrViewMode =
        JtrViewMode.fromPref(prefs.getString("home_view_mode", null), JtrViewMode.DETAIL)

    /** Tri MIROIR de l'accueil (lecture seule) : la liste complète apparaît comme sur l'accueil. */
    private val sortOrder: ContactSortOrder =
        runCatching { ContactSortOrder.valueOf(prefs.getString("home_sort_order", null) ?: "") }
            .getOrDefault(ContactSortOrder.NAME_ASC)

    /** Contacts récemment ouverts (tri `lastContactedAt` DESC, plafonné). Vide tant que rien ouvert. */
    val recents: StateFlow<List<Person>> = repository.getRecent(RECENTS_LIMIT)
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
        repository.getAllActive(), debouncedQuery
    ) { list, q ->
        val tokens = q.searchTokens()
        val filtered = if (tokens.isEmpty()) list
            else list.filter { it.matchesSearch(tokens) { key -> relationTypeLabels[key] } }
        sortPersonsBy(filtered, sortOrder)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        /** Nombre maximum de contacts dans la section « Récents ». */
        private const val RECENTS_LIMIT = 8

        /** Délai d'inactivité avant de relancer le filtre (ms) — aligné sur l'accueil. */
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
