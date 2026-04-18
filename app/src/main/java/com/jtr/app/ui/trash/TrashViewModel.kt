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

    /** Catégories supprimées avec leur liste de membres en corbeille. */
    val deletedCategoryGroups: StateFlow<List<DeletedCategoryGroup>> =
        combine(allDeletedCategories, allDeletedPersons) { cats, persons ->
            val deletedCatIds = cats.map { it.id }.toSet()
            val personsByCategory = persons
                .filter { it.categoryId != null && it.categoryId in deletedCatIds }
                .groupBy { it.categoryId!! }
            cats.map { cat -> DeletedCategoryGroup(cat, personsByCategory[cat.id] ?: emptyList()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Contacts supprimés sans catégorie supprimée associée (orphelins). */
    val deletedOrphanPersons: StateFlow<List<Person>> =
        combine(allDeletedCategories, allDeletedPersons) { cats, persons ->
            val deletedCatIds = cats.map { it.id }.toSet()
            persons.filter { it.categoryId == null || it.categoryId !in deletedCatIds }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Actions sur les catégories ──────────────────────────────────────────

    /** Restaure la catégorie ET tous ses membres supprimés. */
    fun restoreCategoryGroup(categoryId: String) {
        viewModelScope.launch { categoryRepo.restoreWithCascade(categoryId) }
    }

    /** Supprime définitivement la catégorie ET ses membres en corbeille. */
    fun hardDeleteCategoryGroup(categoryId: String) {
        viewModelScope.launch { categoryRepo.hardDeleteWithCascade(categoryId) }
    }

    // ── Actions granulaires sur les membres d'un groupe ────────────────────

    /** Restaure un seul membre (reste assigné à sa catégorie). */
    fun restorePersonFromGroup(personId: String) {
        viewModelScope.launch { personRepo.restore(personId) }
    }

    /** Supprime définitivement un seul membre. */
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
