package com.jtr.app.data.repository

import android.content.Context
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val categoryDao = db.categoryDao()
    private val personDao = db.personDao()

    fun getAllActive(): Flow<List<Category>> = categoryDao.getAll()

    fun getDeleted(): Flow<List<Category>> = categoryDao.getDeleted()

    suspend fun add(category: Category) = categoryDao.insert(category)

    suspend fun update(category: Category) = categoryDao.update(category)

    /** Soft delete de la catégorie + tous ses membres actifs en une seule transaction. */
    suspend fun softDeleteWithCascade(categoryId: String) {
        val ts = System.currentTimeMillis()
        categoryDao.softDelete(categoryId, ts)
        personDao.softDeleteByCategory(categoryId, ts)
    }

    /** Restaure la catégorie et tous ses membres supprimés. */
    suspend fun restoreWithCascade(categoryId: String) {
        categoryDao.restore(categoryId)
        personDao.restoreByCategory(categoryId)
    }

    /** Suppression définitive de la catégorie et de ses membres en corbeille. */
    suspend fun hardDeleteWithCascade(categoryId: String) {
        personDao.hardDeleteByCategory(categoryId)
        categoryDao.hardDelete(categoryId)
    }

    suspend fun hardDeleteAllDeleted() = categoryDao.hardDeleteAllDeleted()

    /** Flow réactif : nombre de contacts actifs par categoryId. */
    fun getPersonCountsPerCategory(): Flow<Map<String, Int>> =
        personDao.getAllActive().map { persons ->
            persons.groupingBy { it.categoryId ?: "" }.eachCount()
        }

    suspend fun countActivePersons(categoryId: String): Int =
        personDao.countActiveByCategory(categoryId)
}
