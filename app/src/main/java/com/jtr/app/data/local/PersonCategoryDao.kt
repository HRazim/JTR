package com.jtr.app.data.local

import androidx.room.*
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour la table de jointure person_category_join (Many-to-Many).
 *
 * Principe clé : les opérations de ce DAO ne touchent QUE les liens.
 * Supprimer un lien (personne ↔ catégorie) ne supprime jamais la personne ni la catégorie.
 */
@Dao
interface PersonCategoryDao {

    // ── Insertion ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(join: PersonCategoryJoin)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(joins: List<PersonCategoryJoin>)

    // ── Suppression de liens (ne supprime PAS les personnes) ─────────────────

    /** Retire UNE personne d'UNE catégorie sans la supprimer de la base. */
    @Query("DELETE FROM person_category_join WHERE personId = :personId AND categoryId = :categoryId")
    suspend fun removePersonFromCategory(personId: String, categoryId: String)

    /** Retire PLUSIEURS personnes d'UNE catégorie sans les supprimer. */
    @Query("DELETE FROM person_category_join WHERE personId IN (:personIds) AND categoryId = :categoryId")
    suspend fun removePersonsFromCategory(personIds: List<String>, categoryId: String)

    /** Retire tous les liens d'une catégorie (utilisé avant suppression de catégorie). */
    @Query("DELETE FROM person_category_join WHERE categoryId = :categoryId")
    suspend fun removeAllFromCategory(categoryId: String)

    /** Retire tous les liens d'une personne (utilisé lors de la suppression définitive). */
    @Query("DELETE FROM person_category_join WHERE personId = :personId")
    suspend fun removeAllCategoriesForPerson(personId: String)

    // ── Lecture ──────────────────────────────────────────────────────────────

    /** Contacts actifs (non supprimés) appartenant à une catégorie. */
    @Query("""
        SELECT p.* FROM persons p
        INNER JOIN person_category_join pcj ON pcj.personId = p.id
        WHERE pcj.categoryId = :categoryId AND p.deletedAt IS NULL
        ORDER BY p.isFavorite DESC, p.firstName ASC
    """)
    fun getActivePersonsInCategory(categoryId: String): Flow<List<Person>>

    /** IDs des catégories auxquelles appartient une personne (Flow réactif). */
    @Query("SELECT categoryId FROM person_category_join WHERE personId = :personId")
    fun getCategoryIdsForPerson(personId: String): Flow<List<String>>

    /** IDs des catégories d'une personne (version suspend pour usage ponctuel). */
    @Query("SELECT categoryId FROM person_category_join WHERE personId = :personId")
    suspend fun getCategoryIdsForPersonSync(personId: String): List<String>

    /** Tous les liens actifs (utilisé par TrashViewModel pour le regroupement). */
    @Query("SELECT * FROM person_category_join")
    fun getAllJoins(): Flow<List<PersonCategoryJoin>>

    /** IDs des personnes ACTIVES dans une catégorie (pour cascade soft-delete). */
    @Query("""
        SELECT p.id FROM persons p
        INNER JOIN person_category_join pcj ON p.id = pcj.personId
        WHERE pcj.categoryId = :categoryId AND p.deletedAt IS NULL
    """)
    suspend fun getActivePersonIdsInCategory(categoryId: String): List<String>

    /** IDs des personnes SUPPRIMÉES LOGIQUEMENT dans une catégorie (pour hard-delete en cascade). */
    @Query("""
        SELECT p.id FROM persons p
        INNER JOIN person_category_join pcj ON p.id = pcj.personId
        WHERE pcj.categoryId = :categoryId AND p.deletedAt IS NOT NULL
    """)
    suspend fun getDeletedPersonIdsInCategory(categoryId: String): List<String>

    /** Tous les IDs de personnes dans une catégorie (actifs + supprimés logiquement). */
    @Query("SELECT personId FROM person_category_join WHERE categoryId = :categoryId")
    suspend fun getAllPersonIdsInCategory(categoryId: String): List<String>

    /** Nombre de contacts actifs dans une catégorie. */
    @Query("""
        SELECT COUNT(DISTINCT pcj.personId) FROM person_category_join pcj
        INNER JOIN persons p ON p.id = pcj.personId
        WHERE pcj.categoryId = :categoryId AND p.deletedAt IS NULL
    """)
    suspend fun countActivePersonsInCategory(categoryId: String): Int
}
