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
import com.jtr.app.ui.components.JtrSortOption
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.utils.matchesSearch
import com.jtr.app.utils.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Critères de tri des contacts (Accueil et catégories — persistés par écran). */
enum class ContactSortOrder { NAME_ASC, NAME_DESC, CREATED_DESC, CREATED_ASC, UPDATED_DESC }

/** Options du menu harmonisé (section Tri) pour une liste de CONTACTS. */
internal fun contactSortOptions(
    current: ContactSortOrder,
    onSelect: (ContactSortOrder) -> Unit
): List<JtrSortOption> = listOf(
    ContactSortOrder.NAME_ASC to R.string.sort_name_asc,
    ContactSortOrder.NAME_DESC to R.string.sort_name_desc,
    ContactSortOrder.CREATED_DESC to R.string.sort_created_desc,
    ContactSortOrder.CREATED_ASC to R.string.sort_created_asc,
    ContactSortOrder.UPDATED_DESC to R.string.sort_updated_desc,
).map { (order, labelRes) -> JtrSortOption(labelRes, order == current) { onSelect(order) } }

/**
 * Applique un critère de tri à une liste de contacts. Partagé entre l'Accueil et
 * le détail de catégorie. Les favoris restent épinglés en tête (cohérent avec
 * l'ordre DAO `isFavorite DESC` utilisé partout dans l'app).
 */
internal fun sortPersonsBy(list: List<Person>, order: ContactSortOrder): List<Person> {
    val comparator = when (order) {
        ContactSortOrder.NAME_ASC -> compareBy<Person> { it.fullName.lowercase() }
        ContactSortOrder.NAME_DESC -> compareByDescending { it.fullName.lowercase() }
        ContactSortOrder.CREATED_DESC -> compareByDescending { it.createdAt }
        ContactSortOrder.CREATED_ASC -> compareBy { it.createdAt }
        ContactSortOrder.UPDATED_DESC ->
            compareByDescending { it.updatedAt.takeIf { u -> u > 0L } ?: it.createdAt }
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    private val _categoryName = MutableStateFlow("")
    val categoryName: StateFlow<String> = _categoryName.asStateFlow()

    // Libellés localisés des types de relation (moteur multi-critères partagé).
    private val relationTypeLabels: Map<String, String> =
        FieldTypes.RELATION.filter { it.key != FieldTypes.CUSTOM }
            .associate { it.key to application.getString(it.labelRes).normalizeForSearch() }

    // Filtrage multi-critères + tri sur Dispatchers.Default (UI 120 Hz préservée).
    val persons: StateFlow<List<Person>> = combine(
        repository.getByCategory(categoryId),
        _searchQuery,
        _sortOrder
    ) { allPersons, query, order ->
        val filtered = if (query.isBlank()) allPersons
        else {
            val normalizedQuery = query.normalizeForSearch()
            allPersons.filter {
                it.matchesSearch(normalizedQuery) { key -> relationTypeLabels[key] }
            }
        }
        sortPersonsBy(filtered, order)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _categoryName.value = categoryDao.getById(categoryId)?.name ?: ""
        }
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
    }
}
