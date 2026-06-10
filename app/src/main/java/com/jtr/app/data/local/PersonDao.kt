package com.jtr.app.data.local

import androidx.room.*
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.flow.Flow

/**
 * PersonDao — opérations CRUD sur la table persons.
 *
 * Note : les opérations liées aux catégories (filtrage, assignation) sont
 * déléguées à PersonCategoryDao (relation Many-to-Many via table de jointure).
 */
@Dao
interface PersonDao {

    @Query("SELECT * FROM persons WHERE deletedAt IS NULL ORDER BY isFavorite DESC, firstName ASC")
    fun getAllActive(): Flow<List<Person>>

    /** Export intégral (sauvegarde .jtr) : TOUTES les lignes, corbeille incluse. */
    @Query("SELECT * FROM persons")
    suspend fun getAllSync(): List<Person>

    // v5.2 : moteur multi-critères — couvre aussi l'entreprise, le poste, le
    // département et les relations (colonne JSON relationLines : noms liés +
    // libellés). Le filtrage accent-insensible reste assuré côté repository.
    @Query("""
        SELECT * FROM persons
        WHERE deletedAt IS NULL
        AND (
            firstName   LIKE '%' || :query || '%'
            OR lastName  LIKE '%' || :query || '%'
            OR nickname  LIKE '%' || :query || '%'
            OR company   LIKE '%' || :query || '%'
            OR jobTitle  LIKE '%' || :query || '%'
            OR department LIKE '%' || :query || '%'
            OR city      LIKE '%' || :query || '%'
            OR notes     LIKE '%' || :query || '%'
            OR likes     LIKE '%' || :query || '%'
            OR origin    LIKE '%' || :query || '%'
            OR phoneNumber LIKE '%' || :query || '%'
            OR email     LIKE '%' || :query || '%'
            OR relationLines LIKE '%' || :query || '%'
        )
        ORDER BY isFavorite DESC, firstName ASC
    """)
    fun search(query: String): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: String): Person?

    /**
     * Cherche l'id d'un contact actif par son nom (prénom, nom, ou « prénom nom »),
     * insensible à la casse — utilisé par les relations cliquables du profil.
     */
    @Query("""
        SELECT id FROM persons
        WHERE deletedAt IS NULL AND (
            LOWER(firstName) = LOWER(:name)
            OR LOWER(TRIM(firstName || ' ' || COALESCE(lastName, ''))) = LOWER(:name)
            OR LOWER(lastName) = LOWER(:name)
        )
        LIMIT 1
    """)
    suspend fun findIdByName(name: String): String?

    /** Flow réactif — émet à chaque écriture sur cette ligne. */
    @Query("SELECT * FROM persons WHERE id = :id")
    fun observeById(id: String): Flow<Person?>

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

    @Query("UPDATE persons SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("UPDATE persons SET deletedAt = NULL WHERE id IN (:ids)")
    suspend fun restoreMultiple(ids: List<String>)

    @Query("DELETE FROM persons WHERE deletedAt IS NOT NULL")
    suspend fun hardDeleteAllDeleted()

    @Query("UPDATE persons SET lastContactedAt = :timestamp WHERE id = :id")
    suspend fun markAsContacted(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE persons SET deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteMultiple(ids: List<String>, timestamp: Long = System.currentTimeMillis())
}
