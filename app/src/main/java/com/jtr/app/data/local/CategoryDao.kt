package com.jtr.app.data.local

import androidx.room.*
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    // v4.5 : favoris flottants d'abord, puis STRICTEMENT l'ordre personnalisé
    // (position). Aucun tri alphabétique : il écraserait le tri manuel de l'utilisateur.
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY isFavorite DESC, position ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeleted(): Flow<List<Category>>

    /** Export intégral (sauvegarde .jtr) : TOUTES les lignes, corbeille incluse. */
    @Query("SELECT * FROM categories")
    suspend fun getAllSync(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    /** Flow réactif d'une catégorie — édition en direct depuis son écran de détail. */
    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeById(id: String): Flow<Category?>

    @Query("UPDATE categories SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE categories SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    // ── v4.5 : favoris, tri personnalisé et regroupement ─────────────────────
    @Query("UPDATE categories SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("UPDATE categories SET parentGroupId = :groupId WHERE id IN (:ids)")
    suspend fun assignGroup(ids: List<String>, groupId: Long?)

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxPosition(): Int

    @Query("UPDATE categories SET deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteMultiple(ids: List<String>, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM categories WHERE deletedAt IS NOT NULL")
    suspend fun hardDeleteAllDeleted()
}
