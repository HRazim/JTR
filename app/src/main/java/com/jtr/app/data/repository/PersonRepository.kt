package com.jtr.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonDao
import com.jtr.app.domain.model.Person
import com.jtr.app.utils.normalizeForSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * PersonRepository — PP3 VERSION COMPLÈTE.
 *
 * Inclut : CRUD, recherche, migration JSON→Room, géocodage intégré,
 * gestion des catégories, markAsContacted, purge corbeille.
 */
class PersonRepository(context: Context) {

    private val dao: PersonDao = AppDatabase.getInstance(context).personDao()
    private val appContext = context.applicationContext

    // =========================================================
    // LECTURE (Flow réactif)
    // =========================================================

    fun getAllActive(): Flow<List<Person>> = dao.getAllActive()

    /**
     * Recherche accent-insensitive et case-insensitive via filtrage en mémoire.
     * Remplace la requête SQL LIKE qui ne normalise pas les caractères accentués.
     * Ex : "therese" trouve "Thérèse", "francois" trouve "François".
     */
    fun search(query: String): Flow<List<Person>> {
        val normalizedQuery = query.normalizeForSearch()
        return dao.getAllActive().map { persons ->
            persons.filter { person ->
                listOfNotNull(
                    person.firstName, person.lastName, person.city,
                    person.notes, person.likes, person.origin
                ).any { field -> field.normalizeForSearch().contains(normalizedQuery) }
            }
        }
    }

    fun getByCategory(categoryId: String): Flow<List<Person>> = dao.getByCategory(categoryId)

    fun getDeleted(): Flow<List<Person>> = dao.getDeleted()

    suspend fun getById(id: String): Person? = dao.getById(id)

    // =========================================================
    // ÉCRITURE
    // =========================================================

    suspend fun add(person: Person) = dao.insert(person)

    /**
     * Ajoute une personne avec géocodage automatique de la ville.
     * Si la ville est renseignée mais sans coordonnées, interroge Nominatim.
     */
    suspend fun addWithGeocoding(person: Person): Person {
        val enriched = if (person.city != null && !person.hasGeoCoordinates) {
            try {
                val geocodingRepo = GeocodingRepository()
                val coords = geocodingRepo.getCityCoordinates(person.city)
                if (coords != null) {
                    person.copy(cityLat = coords.latitude, cityLng = coords.longitude)
                } else person
            } catch (e: Exception) {
                person // En cas d'erreur réseau, on sauvegarde sans coordonnées
            }
        } else person

        dao.insert(enriched)
        return enriched
    }

    suspend fun update(person: Person) = dao.update(person)

    /**
     * Met à jour avec géocodage si la ville a changé.
     */
    suspend fun updateWithGeocoding(person: Person, oldCity: String?): Person {
        val enriched = if (person.city != null && person.city != oldCity) {
            try {
                val geocodingRepo = GeocodingRepository()
                val coords = geocodingRepo.getCityCoordinates(person.city)
                if (coords != null) {
                    person.copy(cityLat = coords.latitude, cityLng = coords.longitude)
                } else person
            } catch (e: Exception) {
                person
            }
        } else person

        dao.update(enriched)
        return enriched
    }

    suspend fun softDelete(id: String) = dao.softDelete(id)

    suspend fun hardDelete(id: String) = dao.hardDelete(id)

    suspend fun toggleFavorite(person: Person) {
        dao.update(person.copy(isFavorite = !person.isFavorite))
    }

    /**
     * Marque un contact comme "contacté aujourd'hui".
     * Utilisé pour le calcul de daysSinceLastContact dans le ProximityCheckWorker.
     */
    suspend fun markAsContacted(personId: String) {
        dao.markAsContacted(personId)
    }

    /**
     * Purge les éléments supprimés depuis plus de 30 jours.
     */
    suspend fun purgeOldDeleted() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.purgeOldDeleted(thirtyDaysAgo)
    }

    // =========================================================
    // MIGRATION JSON → ROOM (données du PP1)
    // =========================================================

    suspend fun restore(id: String) = dao.restore(id)

    suspend fun hardDeleteAllDeleted() = dao.hardDeleteAllDeleted()

    suspend fun softDeleteMultiple(ids: List<String>) = dao.softDeleteMultiple(ids)

    suspend fun assignCategory(ids: List<String>, categoryId: String?) =
        dao.assignCategory(ids, categoryId)

    suspend fun migrateFromJson() {
        val file = File(appContext.filesDir, "persons.json")
        if (!file.exists()) return
        try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<Person>>() {}.type
            val persons: List<Person> = Gson().fromJson(json, type)
            persons.forEach { dao.insert(it) }
            file.delete()
        } catch (e: Exception) {
            // Ignore migration errors — app starts with empty list
        }
    }
}
