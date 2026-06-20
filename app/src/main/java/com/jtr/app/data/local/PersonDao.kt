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

    // NOTE : la recherche de personnes ne passe PLUS par une requête SQL. L'ancien
    // `search()` en LIKE champ-par-champ était incohérent (« Nathan Jamel » ne matchait
    // aucun champ isolé, l'espace final cassait le LIKE) ET inutilisé. Le moteur UNIQUE
    // est désormais en mémoire (utils/TextUtils.matchesSearch) : multi-mots, accent/casse
    // insensible, débouncé hors thread principal — identique sur tous les écrans.

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: String): Person?

    /**
     * Cherche les ids des contacts actifs portant ce nom (prénom, nom, ou « prénom nom »),
     * insensible à la casse. Renvoie TOUS les correspondants (PAS de `LIMIT 1`) afin que
     * l'appelant détecte l'AMBIGUÏTÉ : v7.1.6 — le nom n'étant ni unique ni stable, on ne
     * relie une relation héritée à un contact QUE si la résolution est SANS ambiguïté
     * (exactement un match). Plusieurs matches ⇒ « à vérifier », jamais de lien deviné.
     * Les relations créées désormais portent directement [DynamicLine.linkedPersonId].
     */
    @Query("""
        SELECT id FROM persons
        WHERE deletedAt IS NULL AND (
            LOWER(firstName) = LOWER(:name)
            OR LOWER(TRIM(firstName || ' ' || COALESCE(lastName, ''))) = LOWER(:name)
            OR LOWER(lastName) = LOWER(:name)
        )
    """)
    suspend fun findIdsByName(name: String): List<String>

    /** Flow réactif — émet à chaque écriture sur cette ligne. */
    @Query("SELECT * FROM persons WHERE id = :id")
    fun observeById(id: String): Flow<Person?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: Person)

    /** Insertion par lots (importation des contacts natifs, restauration). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(persons: List<Person>)

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

    // ── Moteur de Proximité (v5.4) ────────────────────────────────────────────

    /**
     * Requête CIBLÉE du Worker de proximité : uniquement les contacts actifs dont
     * le rappel est activé ET qui disposent de coordonnées valides — la base n'est
     * jamais balayée intégralement en tâche de fond.
     */
    @Query("""
        SELECT * FROM persons
        WHERE deletedAt IS NULL
        AND cityNotify = 1
        AND cityLat IS NOT NULL
        AND cityLng IS NOT NULL
    """)
    suspend fun getProximityCandidates(): List<Person>

    /** Anti-spam : horodate la dernière alerte de proximité envoyée (fenêtre 48 h). */
    @Query("UPDATE persons SET proximityNotifiedAt = :timestamp WHERE id = :id")
    suspend fun markProximityNotified(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE persons SET deletedAt = :timestamp WHERE id IN (:ids)")
    suspend fun softDeleteMultiple(ids: List<String>, timestamp: Long = System.currentTimeMillis())
}
