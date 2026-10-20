package com.jtr.app.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DeletedCategoryGroup(
    val category: Category,
    val persons: List<Person>
)

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val personRepo = PersonRepository(application.applicationContext)
    private val categoryRepo = CategoryRepository(application.applicationContext)

    private val allDeletedPersons: Flow<List<Person>> = personRepo.getDeleted()
    private val allDeletedCategories: Flow<List<Category>> = categoryRepo.getDeleted()

    /**
     * Catégories en corbeille (v7.1.71) : supprimer une catégorie ne supprime PLUS ses contacts
     * → une catégorie en corbeille apparaît SEULE, sans membre cascadé. On conserve le type
     * [DeletedCategoryGroup] (compat UI) avec une liste de membres toujours vide.
     */
    val deletedCategoryGroups: StateFlow<List<DeletedCategoryGroup>> =
        allDeletedCategories
            .map { cats -> cats.map { DeletedCategoryGroup(it, emptyList()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Contacts en corbeille (v7.1.71) : il n'existe plus de contact « cascadé » avec une catégorie.
     * Tout contact supprimé (y compris un contact supprimé individuellement mais encore lié à une
     * catégorie elle-même en corbeille) apparaît ici, de façon autonome.
     */
    val deletedOrphanPersons: StateFlow<List<Person>> =
        allDeletedPersons
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Actions sur les catégories ──────────────────────────────────────────

    fun restoreCategoryGroup(categoryId: String) {
        viewModelScope.launch { categoryRepo.restoreCategory(categoryId) }
    }

    fun hardDeleteCategoryGroup(categoryId: String) {
        viewModelScope.launch { categoryRepo.hardDeleteCategory(categoryId) }
    }

    // ── Actions granulaires sur les membres d'un groupe ────────────────────

    fun restorePersonFromGroup(personId: String) {
        viewModelScope.launch { personRepo.restore(personId) }
    }

    fun hardDeletePersonFromGroup(personId: String) {
        viewModelScope.launch { personRepo.hardDelete(personId) }
    }

    // ── Actions sur les contacts orphelins ─────────────────────────────────

    fun restoreOrphan(personId: String) {
        viewModelScope.launch { personRepo.restore(personId) }
    }

    fun hardDeleteOrphan(personId: String) {
        viewModelScope.launch { personRepo.hardDelete(personId) }
    }

    // ── Vider la corbeille ─────────────────────────────────────────────────

    fun emptyTrash() {
        viewModelScope.launch {
            personRepo.hardDeleteAllDeleted()
            categoryRepo.hardDeleteAllDeleted()
        }
    }

    fun daysUntilPurge(person: Person): Int {
        val deletedAt = person.deletedAt ?: return 30
        val elapsed = (System.currentTimeMillis() - deletedAt) / (1000L * 60 * 60 * 24)
        return (30 - elapsed).coerceIn(0, 30).toInt()
    }

    fun daysUntilPurge(category: Category): Int {
        val deletedAt = category.deletedAt ?: return 30
        val elapsed = (System.currentTimeMillis() - deletedAt) / (1000L * 60 * 60 * 24)
        return (30 - elapsed).coerceIn(0, 30).toInt()
    }
}
