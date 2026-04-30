package com.jtr.app.ui.trash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
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
    // Liens Many-to-Many pour retrouver à quelle catégorie appartient un contact supprimé
    private val allJoins: Flow<List<PersonCategoryJoin>> = personRepo.getAllCategoryJoins()

    /**
     * Catégories supprimées avec leurs membres également en corbeille.
     * Un membre figure dans le groupe s'il possède un lien vers cette catégorie supprimée
     * ET s'il est lui-même supprimé logiquement.
     */
    val deletedCategoryGroups: StateFlow<List<DeletedCategoryGroup>> =
        combine(allDeletedCategories, allDeletedPersons, allJoins) { cats, persons, joins ->
            val deletedPersonMap = persons.associateBy { it.id }
            val joinsByCategoryId = joins.groupBy { it.categoryId }
            cats.map { cat ->
                val memberIds = joinsByCategoryId[cat.id]?.map { it.personId }?.toSet() ?: emptySet()
                val deletedMembers = persons.filter { it.id in memberIds }
                DeletedCategoryGroup(cat, deletedMembers)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Contacts supprimés qui n'appartiennent à AUCUNE catégorie elle-même supprimée.
     * Ce sont les contacts "orphelins" dans la corbeille.
     */
    val deletedOrphanPersons: StateFlow<List<Person>> =
        combine(allDeletedCategories, allDeletedPersons, allJoins) { cats, persons, joins ->
            val deletedCatIds = cats.map { it.id }.toSet()
            val personIdsInDeletedCats = joins
                .filter { it.categoryId in deletedCatIds }
                .map { it.personId }
                .toSet()
            persons.filter { it.id !in personIdsInDeletedCats }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Actions sur les catégories ──────────────────────────────────────────

    fun restoreCategoryGroup(categoryId: String) {
        viewModelScope.launch { categoryRepo.restoreWithCascade(categoryId) }
    }

    fun hardDeleteCategoryGroup(categoryId: String) {
        viewModelScope.launch { categoryRepo.hardDeleteWithCascade(categoryId) }
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
