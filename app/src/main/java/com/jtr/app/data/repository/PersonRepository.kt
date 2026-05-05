package com.jtr.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jtr.app.JTRApplication
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.normalizeForSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * PersonRepository — PP3 VERSION FINALE avec Many-to-Many.
 *
 * Les opérations de catégorie passent désormais par PersonCategoryDao
 * (table de jointure) au lieu du champ Person.categoryId.
 * Supprimer un contact d'une catégorie ne supprime PAS le contact.
 */
class PersonRepository(context: Context) {

    private val dao: PersonDao = AppDatabase.getInstance(context).personDao()
    private val categoryDao: PersonCategoryDao = AppDatabase.getInstance(context).personCategoryDao()
    private val socialLinkDao = AppDatabase.getInstance(context).socialLinkDao()
    private val appContext = context.applicationContext

    // =========================================================
    // LECTURE (Flow réactif)
    // =========================================================

    fun getAllActive(): Flow<List<Person>> = dao.getAllActive()

    /**
     * Recherche accent-insensitive via filtrage en mémoire.
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

    /** Contacts actifs d'une catégorie (via table de jointure Many-to-Many). */
    fun getByCategory(categoryId: String): Flow<List<Person>> =
        categoryDao.getActivePersonsInCategory(categoryId)

    fun getDeleted(): Flow<List<Person>> = dao.getDeleted()

    /** Tous les liens personne-catégorie (utilisé par TrashViewModel pour le regroupement). */
    fun getAllCategoryJoins(): Flow<List<PersonCategoryJoin>> = categoryDao.getAllJoins()

    suspend fun getById(id: String): Person? = dao.getById(id)

    /** IDs des catégories d'une personne (version suspend). */
    suspend fun getCategoryIdsForPerson(personId: String): List<String> =
        categoryDao.getCategoryIdsForPersonSync(personId)

    // =========================================================
    // SOCIAL LINKS
    // =========================================================

    fun getSocialLinks(personId: String) = socialLinkDao.getForPerson(personId)

    fun getAllSocialLinks() = socialLinkDao.getAll()

    suspend fun addSocialLink(link: SocialLinkEntity) = socialLinkDao.insert(link)

    suspend fun removeSocialLink(id: String) = socialLinkDao.deleteById(id)

    // =========================================================
    // SYNCHRONISATION GEOFENCES
    // =========================================================

    /** Re-enregistre tous les geofences actifs. No-op si permission absente ou manager non initialisé. */
    private suspend fun syncGeofences() {
        val manager = JTRApplication.geofenceManager ?: return
        val prefs = appContext.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)
        val radiusMeters = prefs.getFloat("proximity_radius_km", 5f) * 1000f
        val persons = dao.getAllActive().first()
        manager.unregisterAll()
        manager.registerAll(persons, radiusMeters)
    }

    // =========================================================
    // ÉCRITURE (CRUD)
    // =========================================================

    suspend fun add(person: Person) = dao.insert(person)

    /**
     * Ajoute une personne ET l'assigne directement à une catégorie.
     * Utilisé depuis CategoryDetailScreen → AddPersonScreen.
     */
    suspend fun addToCategory(person: Person, categoryId: String) {
        dao.insert(person)
        categoryDao.insert(PersonCategoryJoin(person.id, categoryId))
    }

    /**
     * Ajoute une personne avec géocodage automatique si ville sans coordonnées.
     */
    suspend fun addWithGeocoding(person: Person): Person {
        val enriched = if (person.city != null && !person.hasGeoCoordinates) {
            try {
                val coords = GeocodingRepository().getCityCoordinates(person.city)
                if (coords != null) person.copy(cityLat = coords.latitude, cityLng = coords.longitude)
                else person
            } catch (_: Exception) { person }
        } else person
        dao.insert(enriched)
        syncGeofences()
        return enriched
    }

    /**
     * Ajoute avec géocodage ET assigne à une catégorie.
     */
    suspend fun addWithGeocodingToCategory(person: Person, categoryId: String): Person {
        val enriched = addWithGeocoding(person)
        categoryDao.insert(PersonCategoryJoin(enriched.id, categoryId))
        return enriched
    }

    suspend fun update(person: Person) {
        dao.update(person)
        syncGeofences()
    }

    suspend fun updateWithGeocoding(person: Person, oldCity: String?): Person {
        val enriched = if (person.city != null && person.city != oldCity) {
            try {
                val coords = GeocodingRepository().getCityCoordinates(person.city)
                if (coords != null) person.copy(cityLat = coords.latitude, cityLng = coords.longitude)
                else person
            } catch (_: Exception) { person }
        } else person
        dao.update(enriched)
        syncGeofences()
        return enriched
    }

    suspend fun softDelete(id: String) {
        dao.softDelete(id)
        syncGeofences()
    }

    suspend fun hardDelete(id: String) = dao.hardDelete(id)

    suspend fun toggleFavorite(person: Person) {
        dao.update(person.copy(isFavorite = !person.isFavorite))
    }

    suspend fun markAsContacted(personId: String) = dao.markAsContacted(personId)

    suspend fun purgeOldDeleted() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        dao.purgeOldDeleted(thirtyDaysAgo)
    }

    suspend fun restore(id: String) {
        dao.restore(id)
        syncGeofences()
    }

    suspend fun hardDeleteAllDeleted() = dao.hardDeleteAllDeleted()

    suspend fun softDeleteMultiple(ids: List<String>) = dao.softDeleteMultiple(ids)

    // =========================================================
    // GESTION MANY-TO-MANY DES CATÉGORIES
    // =========================================================

    /**
     * Assigne plusieurs personnes à une catégorie (ajoute les liens, ne supprime pas les anciens).
     */
    suspend fun assignCategory(ids: List<String>, categoryId: String) {
        val joins = ids.map { PersonCategoryJoin(it, categoryId) }
        categoryDao.insertAll(joins)
    }

    /**
     * Retire plusieurs personnes d'une catégorie spécifique SANS les supprimer.
     */
    suspend fun removeFromCategory(ids: List<String>, categoryId: String) {
        categoryDao.removePersonsFromCategory(ids, categoryId)
    }

    // =========================================================
    // MIGRATION JSON → ROOM (données du PP1)
    // =========================================================

    suspend fun migrateFromJson() {
        val file = File(appContext.filesDir, "persons.json")
        if (!file.exists()) return
        try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<Person>>() {}.type
            val persons: List<Person> = Gson().fromJson(json, type)
            persons.forEach { dao.insert(it) }
            file.delete()
        } catch (_: Exception) { }
    }
}
