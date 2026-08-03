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
import com.jtr.app.domain.relations.reconcileMirrorLines
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * PersonRepository — PP3 VERSION FINALE avec Many-to-Many.
 *
 * Les opérations de catégorie passent désormais par PersonCategoryDao
 * (table de jointure) au lieu du champ Person.categoryId.
 * Supprimer un contact d'une catégorie ne supprime PAS le contact.
 */
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

    // La recherche de personnes n'a PAS de point d'entrée Repository dédié : tous les
    // écrans observent getAllActive() et appliquent le moteur CENTRAL en mémoire
    // (utils/TextUtils.matchesSearch, multi-mots accent-insensible, débouncé hors
    // thread principal). Une seule logique, aucune requête LIKE incohérente.

    /** Contacts actifs d'une catégorie (via table de jointure Many-to-Many). */
    fun getByCategory(categoryId: String): Flow<List<Person>> =
        categoryDao.getActivePersonsInCategory(categoryId)

    fun getDeleted(): Flow<List<Person>> = dao.getDeleted()

    /** Tous les liens personne-catégorie (utilisé par TrashViewModel pour le regroupement). */
    fun getAllCategoryJoins(): Flow<List<PersonCategoryJoin>> = categoryDao.getAllJoins()

    suspend fun getById(id: String): Person? = dao.getById(id)

    /**
     * Ids des contacts actifs portant ce nom (v7.1.6) — l'appelant classifie :
     * 0 = introuvable, 1 = résolu sans ambiguïté, ≥2 = homonymes (« à vérifier »).
     * Jamais de « devinette » : un nom ambigu ne relie rien. Repli pour les relations
     * HÉRITÉES uniquement ; les relations récentes portent [DynamicLine.linkedPersonId].
     */
    suspend fun findIdsByName(name: String): List<String> = dao.findIdsByName(name.trim())

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
     * transaction Room — par IDENTIFIANT STABLE, jamais par nom (v7.1.6).
     *
     * v7.1.43 — RÉCONCILIATION PAR CIBLE (et non plus deux boucles ajout/retrait
     * indépendantes) : pour chaque fiche dont l'ensemble des relations reçues a changé,
     * [reconcileMirrorLines] recalcule ses lignes miroirs à partir des relations COURANTES
     * de [person] vers elle. L'inversion (« enfant » ⇒ « parent », « manager » ⇒
     * « employé », symétriques inchangés) vit exclusivement dans `domain/relations`.
     * Conséquences : un miroir équivalent n'est jamais dupliqué, un miroir devenu obsolète
     * est retiré, et un miroir encore justifié survit à un changement de type
     * (« mère » → « père » conserve l'« enfant » d'en face).
     *
     * Une cible introuvable ou un nom AMBIGU (homonymes) n'engendre AUCUN miroir
     * (jamais de faux lien). L'auto-relation est exclue.
     */
    suspend fun syncMirrorRelations(person: Person, previousLines: List<DynamicLine>?) {
        val selfName = person.fullName.trim()
        if (selfName.isBlank()) return

        // v7.1.6 — la CIBLE d'une relation est son id STABLE, jamais son nom : linkedPersonId
        // en priorité ; repli SANS AMBIGUÏTÉ par nom (un seul homonyme) pour l'hérité ; jamais
        // d'auto-relation. Un nom ambigu (≥2 homonymes) ou introuvable ne génère AUCUN miroir.
        suspend fun targetIdOf(line: DynamicLine): String? {
            val id = line.linkedPersonId ?: dao.findIdsByName(line.value.trim()).singleOrNull()
            return id?.takeIf { it != person.id }
        }
        // Les relations sont regroupées PAR CIBLE : une fiche peut en recevoir plusieurs.
        suspend fun labelsByTarget(lines: List<DynamicLine>): Map<String, Set<String>> =
            lines.filter { it.value.isNotBlank() || it.linkedPersonId != null }
                .mapNotNull { l -> targetIdOf(l)?.let { it to l.label } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, labels) -> labels.toSet() }

        val current = labelsByTarget(person.relationLines.orEmpty())
        val previous = labelsByTarget(previousLines.orEmpty())
        val affected = (current.keys + previous.keys)
            .filter { current[it].orEmpty() != previous[it].orEmpty() }
        if (affected.isEmpty()) return

        db.withTransaction {
            affected.forEach { targetId ->
                val target = dao.getById(targetId) ?: return@forEach
                val reconciled = reconcileMirrorLines(
                    targetLines = target.relationLines.orEmpty(),
                    sourceId = person.id,
                    sourceName = selfName,
                    currentLabels = current[targetId].orEmpty(),
                    previousLabels = previous[targetId].orEmpty(),
                ) ?: return@forEach
                dao.update(target.copy(
                    relationLines = reconciled.takeIf { it.isNotEmpty() },
                    updatedAt = System.currentTimeMillis()
                ))
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
        if (new.isBlank() || old.equals(new, ignoreCase = true)) return

        db.withTransaction {
            dao.getAllSync().forEach { other ->
                if (other.id == personId) return@forEach
                val lines = other.relationLines ?: return@forEach
                var changed = false
                val rewritten = lines.map { line ->
                    // v7.1.6 — la cible est identifiée par l'id (précis, sûr face aux homonymes) ;
                    // repli sur le nom uniquement pour les relations HÉRITÉES (sans linkedPersonId).
                    val pointsToRenamed = line.linkedPersonId == personId ||
                        (line.linkedPersonId == null && old.isNotBlank() &&
                            line.value.trim().equals(old, ignoreCase = true))
                    if (pointsToRenamed && line.value != new) {
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
