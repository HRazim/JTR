package com.jtr.app.data.repository

import android.content.Context
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CategoryRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val categoryDao = db.categoryDao()
    private val personDao = db.personDao()
    private val personCategoryDao = db.personCategoryDao()

    fun getAllActive(): Flow<List<Category>> = categoryDao.getAll()

    fun getDeleted(): Flow<List<Category>> = categoryDao.getDeleted()

    suspend fun add(category: Category) = categoryDao.insert(category)

    suspend fun update(category: Category) = categoryDao.update(category)

    /**
     * Soft-delete de la catégorie + tous ses membres actifs en cascade.
     * Utilise la table de jointure pour trouver les membres (Many-to-Many).
     */
    suspend fun softDeleteWithCascade(categoryId: String) {
        val ts = System.currentTimeMillis()
        val activeIds = personCategoryDao.getActivePersonIdsInCategory(categoryId)
        categoryDao.softDelete(categoryId, ts)
        if (activeIds.isNotEmpty()) {
            personDao.softDeleteMultiple(activeIds, ts)
        }
    }

    /**
     * Restaure la catégorie ET tous ses membres supprimés logiquement.
     */
    suspend fun restoreWithCascade(categoryId: String) {
        categoryDao.restore(categoryId)
        val allIds = personCategoryDao.getAllPersonIdsInCategory(categoryId)
        if (allIds.isNotEmpty()) {
            personDao.restoreMultiple(allIds)
        }
    }

    /**
     * Suppression définitive de la catégorie et des membres déjà en corbeille.
     * Les membres actifs ne sont PAS supprimés (ils restent sans cette catégorie).
     */
    suspend fun hardDeleteWithCascade(categoryId: String) {
        val deletedIds = personCategoryDao.getDeletedPersonIdsInCategory(categoryId)
        deletedIds.forEach { personDao.hardDelete(it) }
        categoryDao.hardDelete(categoryId)
        // Les lignes de jointure sont supprimées par CASCADE sur categoryId
    }

    suspend fun hardDeleteAllDeleted() = categoryDao.hardDeleteAllDeleted()

    /**
     * Flow réactif : nombre de contacts actifs par categoryId.
     * Combine les deux tables via Many-to-Many.
     */
    fun getPersonCountsPerCategory(): Flow<Map<String, Int>> =
        combine(personCategoryDao.getAllJoins(), personDao.getAllActive()) { joins, activePersons ->
            val activeIds = activePersons.map { it.id }.toSet()
            joins.filter { it.personId in activeIds }
                .groupingBy { it.categoryId }
                .eachCount()
        }

    suspend fun countActivePersons(categoryId: String): Int =
        personCategoryDao.countActivePersonsInCategory(categoryId)
}
