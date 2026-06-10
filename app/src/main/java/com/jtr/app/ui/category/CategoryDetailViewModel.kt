package com.jtr.app.ui.category

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.utils.normalizeForSearch
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Critères de tri des contacts d'une catégorie (persistés par catégorie). */
enum class ContactSortOrder { NAME_ASC, NAME_DESC, CREATED_DESC, UPDATED_DESC }

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

    private fun sortPersons(list: List<Person>, order: ContactSortOrder): List<Person> = when (order) {
        ContactSortOrder.NAME_ASC -> list.sortedBy { it.fullName.lowercase() }
        ContactSortOrder.NAME_DESC -> list.sortedByDescending { it.fullName.lowercase() }
        ContactSortOrder.CREATED_DESC -> list.sortedByDescending { it.createdAt }
        ContactSortOrder.UPDATED_DESC ->
            list.sortedByDescending { it.updatedAt.takeIf { u -> u > 0L } ?: it.createdAt }
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

    val persons: StateFlow<List<Person>> = combine(
        repository.getByCategory(categoryId),
        _searchQuery,
        _sortOrder
    ) { allPersons, query, order ->
        val filtered = if (query.isBlank()) allPersons
        else {
            val normalizedQuery = query.normalizeForSearch()
            allPersons.filter {
                it.firstName.normalizeForSearch().contains(normalizedQuery) ||
                    it.lastName?.normalizeForSearch()?.contains(normalizedQuery) == true
            }
        }
        sortPersons(filtered, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _categoryName.value = categoryDao.getById(categoryId)?.name ?: ""
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

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
}
