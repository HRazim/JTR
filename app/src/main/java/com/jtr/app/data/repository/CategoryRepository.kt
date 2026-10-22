package com.jtr.app.data.repository

import android.content.Context
import com.jtr.app.data.local.AppDatabase
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Référence d'entrée de premier niveau pour le tri global (dossier OU catégorie). */
sealed interface TopOrderRef {
    data class Group(val id: Long) : TopOrderRef
    data class Cat(val id: String) : TopOrderRef
}

class CategoryRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val categoryDao = db.categoryDao()
    private val categoryGroupDao = db.categoryGroupDao()
    private val personDao = db.personDao()
    private val personCategoryDao = db.personCategoryDao()

    fun getAllActive(): Flow<List<Category>> = categoryDao.getAll()

    fun getDeleted(): Flow<List<Category>> = categoryDao.getDeleted()

    /** Insère une catégorie À LA FIN (position = max + 1), jamais selon l'alphabet. */
    suspend fun add(category: Category) {
        val pos = categoryDao.maxPosition() + 1
        categoryDao.insert(category.copy(position = pos))
    }

    /**
     * Édition du CONTENU d'une catégorie (nom, couleur, image) — point d'entrée UNIQUE des
     * trois ViewModels (liste, détail, dossier) et du renommage rapide. L'horodatage est posé
     * ICI, jamais dans la UI : aucun appelant ne peut l'oublier, et aucun `copy()` construit
     * depuis un Flow Room ne peut réintroduire une valeur périmée (v7.1.48).
     *
     * Les mutations d'ORGANISATION gardent volontairement leur propre requête sans estampille :
     * [setFavorite] (statut d'affichage, cf. EditPersonViewModel), [setPosition]/[persistOrder]
     * /[persistTopOrder] (un drag & drop bumperait N catégories d'un coup) et [assignToGroup]
     * (la même requête sert à [deleteGroup], qui désassigne EN MASSE). Les mouvements de membres
     * sont dérivés à la lecture par [getCategoryLastActivity] — aucune écriture.
     */
    suspend fun update(category: Category) =
        categoryDao.update(category.copy(updatedAt = System.currentTimeMillis()))

    // ── v4.5 : favoris, tri personnalisé, regroupement et actions de masse ──────
    fun getGroups(): Flow<List<CategoryGroup>> = categoryGroupDao.getAll()

    suspend fun setFavorite(id: String, favorite: Boolean) =
        categoryDao.setFavorite(id, favorite)

    suspend fun setPosition(id: String, position: Int) =
        categoryDao.setPosition(id, position)

    /** Persiste un nouvel ordre complet (drag & drop) : position = index. */
    suspend fun persistOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> categoryDao.setPosition(id, index) }
    }

    suspend fun softDeleteMultipleWithCascade(categoryIds: List<String>) {
        categoryIds.forEach { softDeleteCategory(it) }
    }

    /**
     * Crée un groupe À LA FIN (position = max + 1) et y rattache les catégories.
     * [parentGroupId] non nul → SOUS-GROUPE imbriqué dans ce groupe parent.
     */
    suspend fun createGroupWith(
        name: String, categoryIds: List<String>, parentGroupId: Long? = null,
        // v7.1.82 — image de couverture définissable DÈS la création (la feuille moderne
        // la propose ; `imagePath` existe en base depuis la DB v15 → aucune migration).
        imagePath: String? = null
    ): Long {
        val pos = categoryGroupDao.maxPosition() + 1
        val groupId = categoryGroupDao.insert(
            CategoryGroup(
                name = name, position = pos, parentGroupId = parentGroupId,
                imagePath = imagePath
            ))
        categoryDao.assignGroup(categoryIds, groupId)
        return groupId
    }

    suspend fun assignToGroup(categoryIds: List<String>, groupId: Long?) =
        categoryDao.assignGroup(categoryIds, groupId)

    suspend fun renameGroup(group: CategoryGroup, name: String) =
        categoryGroupDao.update(group.copy(name = name))

    suspend fun setGroupFavorite(id: Long, favorite: Boolean) =
        categoryGroupDao.setFavorite(id, favorite)

    suspend fun updateGroup(group: CategoryGroup) = categoryGroupDao.update(group)

    /** Dissout un groupe : détache ses catégories puis supprime le groupe. */
    suspend fun deleteGroup(groupId: Long, memberIds: List<String>) {
        categoryDao.assignGroup(memberIds, null)
        categoryGroupDao.delete(groupId)
    }

    /** Supprime la SEULE ligne de groupe (membres déjà réaffectés ailleurs — fusion). */
    suspend fun deleteGroupRow(groupId: Long) = categoryGroupDao.delete(groupId)

    /**
     * Persiste l'ordre GLOBAL des entrées de premier niveau (dossiers + catégories
     * indépendantes mélangés) : `position = index`, dans la table correspondante.
     */
    suspend fun persistTopOrder(refs: List<TopOrderRef>) {
        refs.forEachIndexed { index, ref ->
            when (ref) {
                is TopOrderRef.Group -> categoryGroupDao.setPosition(ref.id, index)
                is TopOrderRef.Cat -> categoryDao.setPosition(ref.id, index)
            }
        }
    }

    /**
     * Soft-delete d'une catégorie SEULE (v7.1.71). Les contacts membres restent ACTIFS (ils
     * perdent seulement l'étiquette) ; les liens de jointure sont CONSERVÉS — ils sont masqués
     * des vues actives par le filtre `category.deletedAt IS NULL` (cf. DAO) et permettent une
     * restauration gratuite. Aucun contact n'est jamais touché par la suppression d'une catégorie.
     */
    suspend fun softDeleteCategory(categoryId: String) {
        categoryDao.softDelete(categoryId, System.currentTimeMillis())
    }

    /**
     * Restaure une catégorie (v7.1.71) : efface simplement `deletedAt`. Les liens n'ayant jamais
     * été retirés et les contacts jamais cachés, les anciens membres ENCORE PRÉSENTS réapparaissent
     * automatiquement dans la catégorie. Un contact supprimé individuellement entre-temps n'est PAS
     * ressuscité (on ne touche plus aux contacts à la restauration).
     */
    suspend fun restoreCategory(categoryId: String) {
        categoryDao.restore(categoryId)
    }

    /**
     * Suppression DÉFINITIVE d'une catégorie (v7.1.71) : retire ses liens de jointure puis
     * hard-delete la catégorie. Un CONTACT n'est JAMAIS hard-deleté ici — même un contact
     * supprimé individuellement mais encore lié à cette catégorie SURVIT (seule l'auto-purge des
     * contacts, sur son propre calendrier, peut le supprimer). Le retrait explicite des liens
     * double la CASCADE FK sur categoryId (comportement explicite et testable).
     */
    suspend fun hardDeleteCategory(categoryId: String) {
        personCategoryDao.removeAllFromCategory(categoryId)
        categoryDao.hardDelete(categoryId)
    }

    suspend fun hardDeleteAllDeleted() = categoryDao.hardDeleteAllDeleted()

    /**
     * AUTO-PURGE de la corbeille des catégories (v7.1.70) : supprime définitivement les catégories
     * en corbeille depuis plus de 30 jours, en retirant d'abord leurs liens de jointure. Les
     * CONTACTS ne sont JAMAIS supprimés ici (ils perdent seulement l'étiquette) ; les contacts
     * mis en corbeille EN MÊME TEMPS que la catégorie sont purgés, eux, par l'auto-purge des
     * contacts ([PersonRepository.purgeOldDeleted], même seuil de 30 jours). Idempotent et
     * auto-réparant (relancé au démarrage). Le retrait des liens est explicite (en plus de la
     * CASCADE FK sur `categoryId`), pour un comportement testable et sans dépendance implicite.
     */
    suspend fun purgeOldDeletedCategories() {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        val expired = categoryDao.getDeletedIdsOlderThan(cutoff)
        expired.forEach { id ->
            personCategoryDao.removeAllFromCategory(id)
            categoryDao.hardDelete(id)
        }
    }

    /**
     * Flow réactif : nombre de contacts actifs par categoryId.
     * Combine les deux tables via Many-to-Many.
     */
    fun getPersonCountsPerCategory(): Flow<Map<String, Int>> =
        // v7.1.71 — liens des catégories ACTIVES uniquement (une catégorie en corbeille ne porte
        // plus de compte fantôme). `getAllJoins` reste réservé à l'écran Corbeille.
        combine(personCategoryDao.getActiveCategoryJoins(), personDao.getAllActive()) { joins, activePersons ->
            val activeIds = activePersons.map { it.id }.toSet()
            joins.filter { it.personId in activeIds }
                .groupingBy { it.categoryId }
                .eachCount()
        }

    suspend fun countActivePersons(categoryId: String): Int =
        personCategoryDao.countActivePersonsInCategory(categoryId)

    /**
     * Flow réactif : « dernière modification » par categoryId (v6.1.7) — max de
     * l'ajout d'un membre (`addedAt`) et de l'édition d'un membre (`updatedAt`).
     * Absent de la map si la catégorie n'a aucun membre actif (repli sur `createdAt`).
     */
    fun getCategoryLastActivity(): Flow<Map<String, Long>> =
        personCategoryDao.getCategoryActivity()
            .map { rows -> rows.associate { it.categoryId to it.lastActivity } }

    companion object {
        /** Seuil d'auto-purge de la corbeille : 30 jours (aligné sur l'auto-purge des contacts). */
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
