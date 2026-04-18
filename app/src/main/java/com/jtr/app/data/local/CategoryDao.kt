package com.jtr.app.data.local

import androidx.room.*
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY `order` ASC, name ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeleted(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Query("UPDATE categories SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM categories WHERE deletedAt IS NOT NULL")
    suspend fun hardDeleteAllDeleted()
}
