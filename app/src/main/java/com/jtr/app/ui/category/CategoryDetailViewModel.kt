package com.jtr.app.ui.category

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.components.JtrSortCriterion
import com.jtr.app.ui.components.jtrSortCriterion
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.utils.matchesSearch
import com.jtr.app.utils.normalizeForSearch
import com.jtr.app.utils.searchTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Critères de tri des contacts (Accueil et catégories — persistés par écran). */
enum class ContactSortOrder { NAME_ASC, NAME_DESC, UPDATED_DESC, UPDATED_ASC, CREATED_DESC, CREATED_ASC }

/**
 * Critères de tri UNIFIÉS (Accueil + détail de catégorie/dossier), modèle à deux axes
 * (v6.2.6) : Nom · Date de modification · Date de création, chacun avec un sens
 * croissant/décroissant. Sens par défaut : Nom → croissant, dates → décroissant.
 * Favoris toujours épinglés en tête ; tout repose sur des champs existants
 * (`updatedAt`, `createdAt`) — aucun ajout de colonne Room.
 */
internal fun contactSortCriteria(
    current: ContactSortOrder,
    onSelect: (ContactSortOrder) -> Unit
): List<JtrSortCriterion> = listOf(
    jtrSortCriterion(R.string.common_name_label, current,
        ContactSortOrder.NAME_ASC, ContactSortOrder.NAME_DESC,
        defaultDescending = false, onSelect = onSelect),
    jtrSortCriterion(R.string.sort_criterion_modified, current,
        ContactSortOrder.UPDATED_ASC, ContactSortOrder.UPDATED_DESC,
        defaultDescending = true, onSelect = onSelect),
    jtrSortCriterion(R.string.sort_criterion_created, current,
        ContactSortOrder.CREATED_ASC, ContactSortOrder.CREATED_DESC,
        defaultDescending = true, onSelect = onSelect),
)

/**
 * Applique un critère de tri à une liste de contacts. Partagé entre l'Accueil et
 * le détail de catégorie. Les favoris restent épinglés en tête (cohérent avec
 * l'ordre DAO `isFavorite DESC` utilisé partout dans l'app).
 */
internal fun sortPersonsBy(list: List<Person>, order: ContactSortOrder): List<Person> {
    val comparator = when (order) {
        ContactSortOrder.NAME_ASC -> compareBy<Person> { it.fullName.lowercase() }
        ContactSortOrder.NAME_DESC -> compareByDescending { it.fullName.lowercase() }
        ContactSortOrder.UPDATED_DESC ->
            compareByDescending { it.updatedAt.takeIf { u -> u > 0L } ?: it.createdAt }
        ContactSortOrder.UPDATED_ASC ->
            compareBy { it.updatedAt.takeIf { u -> u > 0L } ?: it.createdAt }
        ContactSortOrder.CREATED_DESC -> compareByDescending { it.createdAt }
        ContactSortOrder.CREATED_ASC -> compareBy { it.createdAt }
    }
    return list.sortedWith(compareByDescending<Person> { it.isFavorite }.then(comparator))
}

class CategoryDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val categoryId: String = checkNotNull(savedStateHandle["categoryId"])
    private val repository = PersonRepository(application.applicationContext)
    private val categoryRepo = CategoryRepository(application.applicationContext)
    private val categoryDao = AppDatabase.getInstance(application.applicationContext).categoryDao()
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
    private val sortPrefKey = "cat_sort_$categoryId"

    /**
     * Catégorie VIRTUELLE « Favoris » : générée dynamiquement (≥ 2 favoris) en
     * tête de l'écran Catégories — aucune ligne Room, donc ni édition, ni
     * corbeille, ni retrait/ajout de membres : seuls la consultation, le tri et
     * la recherche s'appliquent.
     */
    val isVirtualFavorites: Boolean = categoryId == FAVORITES_CATEGORY_ID

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Requête débouncée alimentant le filtre (cf. HomeViewModel) — vide = sans délai. */
    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> =
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    // Tri préféré pour CETTE catégorie, restauré depuis les préférences.
    private val _sortOrder = MutableStateFlow(
        runCatching { ContactSortOrder.valueOf(prefs.getString(sortPrefKey, null) ?: "") }
            .getOrDefault(ContactSortOrder.NAME_ASC)
    )
    val sortOrder: StateFlow<ContactSortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: ContactSortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(sortPrefKey, order.name).apply()
    }

    // Mode d'affichage des contacts (LIST / GRID / DETAIL), partagé par toutes les
    // catégories et persisté.
    private val _viewMode = MutableStateFlow(
        JtrViewMode.fromPref(prefs.getString(VIEW_PREF_KEY, null), JtrViewMode.DETAIL)
    )
    val viewMode: StateFlow<JtrViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: JtrViewMode) {
        _viewMode.value = mode
        prefs.edit().putString(VIEW_PREF_KEY, mode.name).apply()
    }

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val categories: StateFlow<List<Category>> = categoryDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Catégorie observée en direct (null pour la virtuelle « Favoris »). */
    val category: StateFlow<Category?> =
        if (isVirtualFavorites) MutableStateFlow(null)
        else categoryDao.observeById(categoryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * « Dernière modification » AFFICHÉE (v7.1.48) = max entre l'édition du CONTENU
     * (`Category.updatedAt`, estampillé par le repository) et l'ACTIVITÉ DES MEMBRES
     * (`getCategoryLastActivity` : ajout d'un membre ou édition d'un membre, dérivée à la
     * lecture — aucune écriture par membre) : [categoryLastModified], la MÊME fonction que le
     * tri « Dernière modification » des listes → aucun écart entre la liste et la fiche.
     *
     * Repli des valeurs à 0 sur `createdAt` (archives .jtr d'avant v22 — cf. Person, motif
     * identique) ; `null` si même `createdAt` vaut 0 → la ligne est MASQUÉE côté écran.
     */
    val lastModified: StateFlow<Long?> =
        if (isVirtualFavorites) MutableStateFlow(null)
        else combine(category, categoryRepo.getCategoryLastActivity()) { cat, activity ->
            cat?.let { categoryLastModified(it, activity[categoryId]).takeIf { v -> v > 0L } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categoryName: StateFlow<String> =
        if (isVirtualFavorites) {
            MutableStateFlow(application.getString(R.string.favorites_category))
        } else {
            category.map { it?.name ?: "" }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
        }

    // Libellés localisés des types de relation (moteur multi-critères partagé).
    private val relationTypeLabels: Map<String, String> =
        FieldTypes.RELATION.filter { it.key != FieldTypes.CUSTOM }
            .associate { it.key to application.getString(it.labelRes).normalizeForSearch() }

    // Source : membres de la catégorie, OU tous les favoris pour la virtuelle.
    private val personsSource =
        if (isVirtualFavorites) repository.getAllActive()
            .map { list -> list.filter { it.isFavorite } }
        else repository.getByCategory(categoryId)

    // Filtrage multi-critères + tri sur Dispatchers.Default (UI 120 Hz préservée).
    val persons: StateFlow<List<Person>> = combine(
        personsSource,
        debouncedQuery,
        _sortOrder
    ) { allPersons, query, order ->
        val tokens = query.searchTokens()
        val filtered = if (tokens.isEmpty()) allPersons
        else allPersons.filter {
            it.matchesSearch(tokens) { key -> relationTypeLabels[key] }
        }
        sortPersonsBy(filtered, order)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Met à jour nom / couleur / image de la catégorie (menu « Modifier »). */
    fun updateCategory(updated: Category) {
        if (isVirtualFavorites) return
        viewModelScope.launch { categoryRepo.update(updated) }
    }

    /** Met la catégorie À LA CORBEILLE (cascade : ses contacts aussi). */
    fun deleteCategoryToTrash() {
        if (isVirtualFavorites) return
        viewModelScope.launch { categoryRepo.softDeleteWithCascade(categoryId) }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    /** Réinitialise la recherche (appelé quand l'écran quitte la composition). */
    fun clearSearch() { _searchQuery.value = "" }

    fun toggleFavorite(person: Person) {
        viewModelScope.launch { repository.toggleFavorite(person) }
    }

    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    /**
     * Retire les contacts sélectionnés de CETTE catégorie uniquement.
     * Les contacts ne sont PAS supprimés de la base de données ni de leurs autres catégories.
     */
    fun removeSelectedFromCategory() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.removeFromCategory(ids, categoryId)
            _selectedIds.value = emptySet()
        }
    }

    /** Supprime définitivement (soft-delete) les contacts sélectionnés. */
    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.softDeleteMultiple(ids)
            _selectedIds.value = emptySet()
        }
    }

    fun createCategoryAndAssignToSelected(name: String, color: String) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val category = Category(name = name, color = color)
            categoryRepo.add(category)
            repository.assignCategory(ids, category.id)
            _selectedIds.value = emptySet()
        }
    }

    fun assignCategoryToSelected(newCategoryId: String) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.assignCategory(ids, newCategoryId)
            _selectedIds.value = emptySet()
        }
    }

    companion object {
        private const val VIEW_PREF_KEY = "category_detail_view_mode"

        /** Délai d'inactivité avant de relancer le filtre de recherche (ms). */
        private const val SEARCH_DEBOUNCE_MS = 250L

        /** Id sentinelle de la catégorie virtuelle « Favoris » (aucune ligne Room). */
        const val FAVORITES_CATEGORY_ID = "jtr_favorites"
    }
}
