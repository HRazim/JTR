package com.jtr.app.data.local

import androidx.room.*
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.flow.Flow


@Dao
interface PersonDao {

    @Query("SELECT * FROM persons WHERE deletedAt IS NULL ORDER BY isFavorite DESC, firstName ASC")
    fun getAllActive(): Flow<List<Person>>

    @Query("""
        SELECT * FROM persons
        WHERE deletedAt IS NULL
        AND (
            firstName LIKE '%' || :query || '%'
            OR lastName  LIKE '%' || :query || '%'
            OR city      LIKE '%' || :query || '%'
            OR notes     LIKE '%' || :query || '%'
            OR likes     LIKE '%' || :query || '%'
            OR origin    LIKE '%' || :query || '%'
        )
        ORDER BY isFavorite DESC, firstName ASC
    """)
    fun search(query: String): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: String): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person)

    @Update
    suspend fun update(person: Person)

    @Query("UPDATE persons SET deletedAt = :timestamp WHERE id = :id")
    suspend fun softDelete(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM persons WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeleted(): Flow<List<Person>>

    @Query("DELETE FROM persons WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeOldDeleted(cutoff: Long)

    // PP3 : Filtrer par catégorie
    @Query("SELECT * FROM persons WHERE categoryId = :categoryId AND deletedAt IS NULL ORDER BY isFavorite DESC, firstName ASC")
    fun getByCategory(categoryId: String): Flow<List<Person>>

    @Query("UPDATE persons SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM persons WHERE deletedAt IS NOT NULL")
    suspend fun hardDeleteAllDeleted()

    // PP3 : Marquer comme contacté
    @Query("UPDATE persons SET lastContactedAt = :timestamp WHERE id = :id")
    suspend fun markAsContacted(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE persons SET deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteMultiple(ids: List<String>, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE persons SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun assignCategory(ids: List<String>, categoryId: String?)

    @Query("UPDATE persons SET deletedAt = :timestamp WHERE categoryId = :categoryId AND deletedAt IS NULL")
    suspend fun softDeleteByCategory(categoryId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE persons SET deletedAt = NULL WHERE categoryId = :categoryId")
    suspend fun restoreByCategory(categoryId: String)

    @Query("DELETE FROM persons WHERE categoryId = :categoryId AND deletedAt IS NOT NULL")
    suspend fun hardDeleteByCategory(categoryId: String)

    @Query("SELECT COUNT(*) FROM persons WHERE categoryId = :categoryId AND deletedAt IS NULL")
    suspend fun countActiveByCategory(categoryId: String): Int
}
