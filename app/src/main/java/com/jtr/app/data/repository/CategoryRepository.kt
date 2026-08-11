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
        categoryIds.forEach { softDeleteWithCascade(it) }
    }

    /**
     * Crée un groupe À LA FIN (position = max + 1) et y rattache les catégories.
     * [parentGroupId] non nul → SOUS-GROUPE imbriqué dans ce groupe parent.
     */
    suspend fun createGroupWith(
        name: String, categoryIds: List<String>, parentGroupId: Long? = null
    ): Long {
        val pos = categoryGroupDao.maxPosition() + 1
        val groupId = categoryGroupDao.insert(
            CategoryGroup(name = name, position = pos, parentGroupId = parentGroupId))
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
     * Soft-delete de la catégorie + tous ses membres actifs en cascade.
     * Utilise la table de jointure pour trouver les membres (Many-to-Many).
     */
    suspend fun softDeleteWithCascade(categoryId: String) {
        val ts = System.currentTimeMillis()
        val activeIds = personCategoryDao.getActivePersonIdsInCategory(categoryId)
        categoryDao.softDelete(categoryId, ts)
        if (activeIds.isNotEmpty()) {
            personDao.softDeleteMultiple(activeIds, ts)
        }
    }

    /**
     * Restaure la catégorie ET tous ses membres supprimés logiquement.
     */
    suspend fun restoreWithCascade(categoryId: String) {
        categoryDao.restore(categoryId)
        val allIds = personCategoryDao.getAllPersonIdsInCategory(categoryId)
        if (allIds.isNotEmpty()) {
            personDao.restoreMultiple(allIds)
        }
    }

    /**
     * Suppression définitive de la catégorie et des membres déjà en corbeille.
     * Les membres actifs ne sont PAS supprimés (ils restent sans cette catégorie).
     */
    suspend fun hardDeleteWithCascade(categoryId: String) {
        val deletedIds = personCategoryDao.getDeletedPersonIdsInCategory(categoryId)
        deletedIds.forEach { personDao.hardDelete(it) }
        categoryDao.hardDelete(categoryId)
        // Les lignes de jointure sont supprimées par CASCADE sur categoryId
    }

    suspend fun hardDeleteAllDeleted() = categoryDao.hardDeleteAllDeleted()

    /**
     * Flow réactif : nombre de contacts actifs par categoryId.
     * Combine les deux tables via Many-to-Many.
     */
    fun getPersonCountsPerCategory(): Flow<Map<String, Int>> =
        combine(personCategoryDao.getAllJoins(), personDao.getAllActive()) { joins, activePersons ->
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
}
