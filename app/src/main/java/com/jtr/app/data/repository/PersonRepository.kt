package com.jtr.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jtr.app.JTRApplication
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.data.local.PersonDao
import com.jtr.app.data.local.PersonCategoryDao
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.PersonCategoryJoin
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.matchesSearch
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
/**
 * Dictionnaire de réciprocité des types de relation (clés stables du catalogue
 * FieldTypes.RELATION). Les types symétriques se reflètent à l'identique ;
 * « mère »/« père » se reflètent en « enfant ». Tout type inconnu ou
 * personnalisé (« Cousin », « Collègue »…) est appliqué TEXTUELLEMENT en miroir.
 */
private val MIRROR_RELATION_LABELS = mapOf(
    "mother" to "child",
    "father" to "child",
    "brother" to "brother",
    "sister" to "sister",
    "spouse" to "spouse",
    "friend" to "friend",
)

/** Type de la relation inverse (réciprocité), identique par défaut. */
internal fun mirrorRelationLabel(label: String): String =
    MIRROR_RELATION_LABELS[label] ?: label

class PersonRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao: PersonDao = db.personDao()
    private val categoryDao: PersonCategoryDao = db.personCategoryDao()
    private val socialLinkDao = db.socialLinkDao()
    private val appContext = context.applicationContext

    // =========================================================
    // LECTURE (Flow réactif)
    // =========================================================

    fun getAllActive(): Flow<List<Person>> = dao.getAllActive()

    /**
     * Recherche multi-critères accent-insensitive via le matcher partagé
     * [matchesSearch] (nom, entreprise, poste, ville/origine, relations…).
     * Ex : "therese" trouve "Thérèse", "francois" trouve "François".
     */
    fun search(query: String): Flow<List<Person>> {
        val normalizedQuery = query.normalizeForSearch()
        return dao.getAllActive().map { persons ->
            persons.filter { it.matchesSearch(normalizedQuery) }
        }
    }

    /** Contacts actifs d'une catégorie (via table de jointure Many-to-Many). */
    fun getByCategory(categoryId: String): Flow<List<Person>> =
        categoryDao.getActivePersonsInCategory(categoryId)

    fun getDeleted(): Flow<List<Person>> = dao.getDeleted()

    /** Tous les liens personne-catégorie (utilisé par TrashViewModel pour le regroupement). */
    fun getAllCategoryJoins(): Flow<List<PersonCategoryJoin>> = categoryDao.getAllJoins()

    suspend fun getById(id: String): Person? = dao.getById(id)

    /** Résout l'id d'un contact par son nom (relations cliquables). */
    suspend fun findIdByName(name: String): String? = dao.findIdByName(name)

    fun observeById(id: String): kotlinx.coroutines.flow.Flow<Person?> = dao.observeById(id)

    /** IDs des catégories d'une personne (version suspend). */
    suspend fun getCategoryIdsForPerson(personId: String): List<String> =
        categoryDao.getCategoryIdsForPersonSync(personId)

    /**
     * IDs des catégories d'une personne — Flow RÉACTIF (table de jointure).
     * Pilote les badges de la fiche et le sélecteur « Gérer les catégories » :
     * toute insertion/suppression de lien se reflète immédiatement dans l'UI.
     */
    fun observeCategoryIdsForPerson(personId: String): Flow<List<String>> =
        categoryDao.getCategoryIdsForPerson(personId)

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
        // Rayon automatique fixe (zéro friction) — voir JTRApplication.PROXIMITY_RADIUS_KM.
        val radiusMeters = JTRApplication.PROXIMITY_RADIUS_KM * 1000f
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

    // ── Moteur de Proximité (v5.4) ────────────────────────────────────────────

    /** Contacts actifs éligibles aux alertes de proximité (toggle + coordonnées). */
    suspend fun getProximityCandidates(): List<Person> = dao.getProximityCandidates()

    /** Anti-spam : enregistre l'envoi d'une alerte de proximité pour ce contact. */
    suspend fun markProximityNotified(personId: String) = dao.markProximityNotified(personId)

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
    // RELATIONS MIROIRS AUTOMATIQUES (v5.4.1)
    // =========================================================

    /**
     * Synchronise les fiches LIÉES après la sauvegarde de [person], dans UNE
     * transaction Room :
     *  - chaque relation AJOUTÉE (présente maintenant, absente de
     *    [previousLines]) insère la relation INVERSE — type résolu par
     *    [mirrorRelationLabel] — sur la fiche cible, résolue par NOM comme les
     *    liens cliquables ([PersonDao.findIdByName]) ;
     *  - chaque relation SUPPRIMÉE nettoie instantanément son miroir (même nom
     *    + même type inverse) — aucune donnée fantôme.
     *
     * Idempotent : un miroir déjà présent n'est jamais dupliqué. Les valeurs ne
     * résolvant aucun contact (nom libre) sont ignorées silencieusement.
     */
    suspend fun syncMirrorRelations(person: Person, previousLines: List<DynamicLine>?) {
        val selfName = person.fullName.trim()
        if (selfName.isBlank()) return

        fun keyOf(line: DynamicLine) = line.value.trim().lowercase() to line.label
        val current = person.relationLines.orEmpty().filter { it.value.isNotBlank() }
        val previous = previousLines.orEmpty().filter { it.value.isNotBlank() }
        val currentKeys = current.map(::keyOf).toSet()
        val previousKeys = previous.map(::keyOf).toSet()
        val added = current.filter { keyOf(it) !in previousKeys }
        val removed = previous.filter { keyOf(it) !in currentKeys }
        if (added.isEmpty() && removed.isEmpty()) return

        db.withTransaction {
            added.forEach { line ->
                val targetId = dao.findIdByName(line.value.trim()) ?: return@forEach
                if (targetId == person.id) return@forEach
                val target = dao.getById(targetId) ?: return@forEach
                val mirrorLabel = mirrorRelationLabel(line.label)
                val lines = target.relationLines.orEmpty()
                val alreadyMirrored = lines.any {
                    it.value.trim().equals(selfName, ignoreCase = true) && it.label == mirrorLabel
                }
                if (!alreadyMirrored) {
                    dao.update(target.copy(
                        relationLines = lines + DynamicLine(value = selfName, label = mirrorLabel),
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
            removed.forEach { line ->
                val targetId = dao.findIdByName(line.value.trim()) ?: return@forEach
                if (targetId == person.id) return@forEach
                val target = dao.getById(targetId) ?: return@forEach
                val mirrorLabel = mirrorRelationLabel(line.label)
                val original = target.relationLines.orEmpty()
                val filtered = original.filterNot {
                    it.value.trim().equals(selfName, ignoreCase = true) && it.label == mirrorLabel
                }
                if (filtered.size != original.size) {
                    dao.update(target.copy(
                        relationLines = filtered.takeIf { it.isNotEmpty() },
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    /**
     * Propage un changement de nom : remplace, dans les `relationLines` de TOUS les
     * AUTRES contacts, l'ancien nom complet de [personId] par le nouveau. Les noms
     * liés étant dénormalisés dans le JSON `relationLines` (pas de table de relations
     * normalisée → un JOIN n'est pas possible), c'est cette propagation qui maintient
     * une source de vérité cohérente : renommer « Manon » en « Manon du Hameau » met
     * à jour instantanément la fiche de tous ceux qui la citent en relation.
     *
     * Transaction Room. À appeler APRÈS la mise à jour du contact lui-même et AVANT
     * [syncMirrorRelations], afin que les lignes miroirs portent déjà le nouveau nom
     * (sinon une suppression de relation simultanée ne retrouverait pas son miroir).
     * Idempotent : ne réécrit que les lignes réellement concernées.
     */
    suspend fun propagateRelationNameChange(personId: String, oldName: String, newName: String) {
        val old = oldName.trim()
        val new = newName.trim()
        if (old.isBlank() || new.isBlank() || old.equals(new, ignoreCase = true)) return

        db.withTransaction {
            dao.getAllSync().forEach { other ->
                if (other.id == personId) return@forEach
                val lines = other.relationLines ?: return@forEach
                var changed = false
                val rewritten = lines.map { line ->
                    if (line.value.trim().equals(old, ignoreCase = true)) {
                        changed = true
                        line.copy(value = new)
                    } else line
                }
                if (changed) {
                    dao.update(other.copy(
                        relationLines = rewritten,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

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
