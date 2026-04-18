package com.jtr.app.ui.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CategoryRepository(application.applicationContext)

    val categories: StateFlow<List<Category>> = repo.getAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nombre de contacts actifs par categoryId — utilisé pour le dialogue de confirmation. */
    val personCountByCategory: StateFlow<Map<String, Int>> = repo.getPersonCountsPerCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addCategory(name: String, color: String) {
        viewModelScope.launch { repo.add(Category(name = name, color = color)) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { repo.update(category) }
    }

    /** Soft delete en cascade : catégorie + tous ses membres actifs. */
    fun deleteCategoryWithCascade(categoryId: String) {
        viewModelScope.launch { repo.softDeleteWithCascade(categoryId) }
    }
}
