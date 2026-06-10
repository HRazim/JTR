package com.jtr.app.data.local

import androidx.room.*
import com.jtr.app.domain.model.CategoryGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryGroupDao {

    // Favoris d'abord, puis STRICTEMENT la position (aucun tri alphabétique).
    @Query("SELECT * FROM category_groups ORDER BY isFavorite DESC, position ASC")
    fun getAll(): Flow<List<CategoryGroup>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM category_groups")
    suspend fun maxPosition(): Int

    /** Export intégral (sauvegarde .jtr). */
    @Query("SELECT * FROM category_groups")
    suspend fun getAllSync(): List<CategoryGroup>

    @Query("SELECT * FROM category_groups WHERE id = :id")
    suspend fun getById(id: Long): CategoryGroup?

    /** Renvoie l'id auto-généré du groupe créé (pour lier les catégories ensuite). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: CategoryGroup): Long

    @Update
    suspend fun update(group: CategoryGroup)

    @Query("DELETE FROM category_groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE category_groups SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE category_groups SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)
}
