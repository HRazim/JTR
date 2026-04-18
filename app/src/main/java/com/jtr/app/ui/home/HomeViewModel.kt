package com.jtr.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)
    private val categoryRepository = CategoryRepository(application.applicationContext)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val categories: StateFlow<List<Category>> = categoryRepository.getAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val persons: StateFlow<List<Person>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.getAllActive()
            else repository.search(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.migrateFromJson()
            repository.purgeOldDeleted()
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

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.softDeleteMultiple(ids)
            _selectedIds.value = emptySet()
        }
    }

    fun assignCategoryToSelected(categoryId: String) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.assignCategory(ids, categoryId)
            _selectedIds.value = emptySet()
        }
    }

    /** Crée une catégorie à la volée et l'assigne aux contacts sélectionnés. */
    fun createCategoryAndAssignToSelected(name: String, color: String) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val category = Category(name = name, color = color)
            categoryRepository.add(category)
            repository.assignCategory(ids, category.id)
            _selectedIds.value = emptySet()
        }
    }
}
