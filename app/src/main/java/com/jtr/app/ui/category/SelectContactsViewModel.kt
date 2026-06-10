package com.jtr.app.ui.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.utils.matchesSearch
import com.jtr.app.utils.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran « Ajouter des contacts existants à une catégorie » :
 * cinématique type Accueil (liste + recherche + multi-sélection), sans dialogue.
 */
class SelectContactsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val categoryId: String = checkNotNull(savedStateHandle["categoryId"])
    private val repository = PersonRepository(application.applicationContext)
    private val categoryDao = AppDatabase.getInstance(application.applicationContext).categoryDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    private val _categoryName = MutableStateFlow("")
    val categoryName: StateFlow<String> = _categoryName.asStateFlow()

    init {
        viewModelScope.launch {
            _categoryName.value = categoryDao.getById(categoryId)?.name ?: ""
        }
    }

    // Libellés localisés des types de relation (moteur multi-critères partagé).
    private val relationTypeLabels: Map<String, String> =
        FieldTypes.RELATION.filter { it.key != FieldTypes.CUSTOM }
            .associate { it.key to application.getString(it.labelRes).normalizeForSearch() }

    /** Contacts actifs HORS de la catégorie, filtrés par la recherche multi-critères. */
    val candidates: StateFlow<List<Person>> = combine(
        repository.getAllActive(),
        repository.getByCategory(categoryId),
        _searchQuery
    ) { all, members, query ->
        val memberIds = members.mapTo(HashSet()) { it.id }
        val available = all.filter { it.id !in memberIds }
        if (query.isBlank()) available
        else {
            val normalized = query.normalizeForSearch()
            available.filter {
                it.matchesSearch(normalized) { key -> relationTypeLabels[key] }
            }
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Associe la sélection à la catégorie (Many-to-Many) puis signale la fin. */
    fun confirm(onDone: () -> Unit) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.assignCategory(ids, categoryId)
            _selectedIds.value = emptySet()
            onDone()
        }
    }
}
