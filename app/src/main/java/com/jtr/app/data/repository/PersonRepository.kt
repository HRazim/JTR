package com.jtr.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jtr.app.JTRApplication
import com.jtr.app.data.files.ImageFileStore
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
    private val galleryImageDao = db.galleryImageDao()
    private val appContext = context.applicationContext

    // =========================================================
    // LECTURE (Flow réactif)
    // =========================================================

    fun getAllActive(): Flow<List<Person>> = dao.getAllActive()

    /** Contacts récemment ouverts (tri `lastContactedAt` DESC, plafonné). */
    fun getRecent(limit: Int): Flow<List<Person>> = dao.getRecent(limit)

    // La recherche de personnes n'a PAS de point d'entrée Repository dédié : tous les
    // écrans observent getAllActive() et appliquent le moteur CENTRAL en mémoire
    // (utils/TextUtils.matchesSearch, multi-mots accent-insensible, débouncé hors
    // thread principal). Une seule logique, aucune requête LIKE incohérente.

    /** Contacts actifs d'une catégorie (via table de jointure Many-to-Many). */
    fun getByCategory(categoryId: String): Flow<List<Person>> =
        categoryDao.getActivePersonsInCategory(categoryId)

    /**
     * v7.1.87 — contacts actifs de PLUSIEURS catégories, DÉDOUBLONNÉS : périmètre d'un
     * dossier (ses catégories + celles de ses sous-dossiers). Lecture seule.
     */
    fun getByCategories(categoryIds: List<String>): Flow<List<Person>> =
        categoryDao.getActivePersonsInCategories(categoryIds)

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

    /**
     * v7.1.105 (lot C1) — `insert` est en REPLACE : le BROUILLON de l'Ajout ré-insère la même
     * ligne à chaque auto-save, donc changer la photo du brouillon écrasait la référence en
     * laissant l'ancien fichier orphelin. L'ancienne photo (si différente) est effacée APRÈS
     * l'écriture — jamais la nouvelle.
     */
    suspend fun add(person: Person) {
        val old = dao.getById(person.id)
        dao.insert(person)
        old?.let { ImageFileStore.deleteReplacedFile(appContext, it.photoUri, person.photoUri) }
    }

    /**
     * Ajoute une personne ET l'assigne directement à une catégorie.
     * Utilisé depuis CategoryDetailScreen → AddPersonScreen.
     */
    suspend fun addToCategory(person: Person, categoryId: String) {
        add(person)
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
        add(enriched)
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

    /**
     * v7.1.105 (lot C1) — une photo REMPLACÉE efface son ancien fichier (après l'écriture,
     * via le garde-fou [ImageFileStore] ; no-op si elle n'a pas changé). Avant, chaque
     * changement de photo laissait l'ancien `photos/profile_…` orphelin.
     */
    suspend fun update(person: Person) {
        val old = dao.getById(person.id)
        dao.update(person)
        old?.let { ImageFileStore.deleteReplacedFile(appContext, it.photoUri, person.photoUri) }
        syncGeofences()
    }

    suspend fun updateWithGeocoding(person: Person, oldCity: String?): Person {
        // v7.1.122 — géocode aussi une ville INCHANGÉE mais sans coordonnées : l'auto-save
        // (qui ne géocode jamais, design v7.1.4) peut avoir persisté la ville avant le save
        // manuel ; elle n'est alors plus « changée » mais attend toujours ses coordonnées.
        val enriched = if (person.city != null && (person.city != oldCity || !person.hasGeoCoordinates)) {
            try {
                val coords = GeocodingRepository().getCityCoordinates(person.city)
                if (coords != null) person.copy(cityLat = coords.latitude, cityLng = coords.longitude)
                else person
            } catch (_: Exception) { person }
        } else person
        update(enriched)
        return enriched
    }

    suspend fun softDelete(id: String) {
        dao.softDelete(id)
        syncGeofences()
    }

    /**
     * v7.1.105 (lot C1) — la suppression DÉFINITIVE efface aussi les FICHIERS du contact :
     * sa photo de profil et ses images de galerie. Les chemins sont lus AVANT le DELETE
     * (la CASCADE FK efface les lignes `gallery_images` avec lui — après, il n'y a plus
     * rien à lire) et les fichiers effacés APRÈS son succès, via le garde-fou
     * [ImageFileStore.deleteFileIfOwned] (jamais hors de `photos/`/`gallery/`/`crops/`).
     */
    suspend fun hardDelete(id: String) {
        val photoUri = dao.getById(id)?.photoUri
        val galleryPaths = galleryImageDao.pathsForPerson(id)
        dao.hardDelete(id)
        ImageFileStore.deleteFileIfOwned(appContext, photoUri)
        galleryPaths.forEach { ImageFileStore.deleteFileIfOwned(appContext, it) }
    }

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
        // v7.1.105 — fichiers lus AVANT la purge, effacés APRÈS (cf. hardDelete).
        val photoUris = dao.getDeletedPhotoUrisOlderThan(thirtyDaysAgo)
        val galleryPaths = galleryImageDao.pathsForPersonsDeletedBefore(thirtyDaysAgo)
        dao.purgeOldDeleted(thirtyDaysAgo)
        (photoUris + galleryPaths).forEach { ImageFileStore.deleteFileIfOwned(appContext, it) }
    }

    suspend fun restore(id: String) {
        dao.restore(id)
        syncGeofences()
    }

    suspend fun hardDeleteAllDeleted() {
        // v7.1.105 — fichiers lus AVANT le vidage de la corbeille, effacés APRÈS.
        val photoUris = dao.getDeletedPhotoUris()
        val galleryPaths = galleryImageDao.pathsForDeletedPersons()
        dao.hardDeleteAllDeleted()
        (photoUris + galleryPaths).forEach { ImageFileStore.deleteFileIfOwned(appContext, it) }
    }

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

    /**
     * VRAI DÉPLACEMENT TRANSACTIONNEL (v7.1.70) : retire [ids] de [fromCategoryId] ET les ajoute
     * à [toCategoryId] en UNE SEULE transaction atomique. `from == to` ⇒ no-op. L'ajout est
     * idempotent (PK composite + `insertAll` OnConflict.IGNORE) : un contact déjà dans la cible
     * est simplement retiré de la source, sans doublon. Les AUTRES catégories des contacts ne sont
     * PAS touchées (seul le lien à la source disparaît). La multi-appartenance (ajout sans retrait)
     * reste gérée par « Gérer les catégories ».
     */
    suspend fun moveToCategory(ids: List<String>, fromCategoryId: String, toCategoryId: String) {
        if (ids.isEmpty() || fromCategoryId == toCategoryId) return
        db.withTransaction {
            categoryDao.insertAll(ids.map { PersonCategoryJoin(it, toCategoryId) })
            categoryDao.removePersonsFromCategory(ids, fromCategoryId)
        }
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
