package com.jtr.app.ui.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.TopOrderRef
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CategoryRepository(application.applicationContext)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // L'ordre vient du DAO : favoris d'abord (isFavorite DESC), puis position ASC,
    // puis nom. On NE re-trie PAS ici pour préserver le tri personnalisé / favoris.
    val categories: StateFlow<List<Category>> = combine(
        repo.getAllActive(),
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nombre de contacts actifs par categoryId — utilisé pour le dialogue de confirmation. */
    val personCountByCategory: StateFlow<Map<String, Int>> = repo.getPersonCountsPerCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Dossiers/groupes (triés favoris d'abord, puis position). */
    val groups: StateFlow<List<CategoryGroup>> = repo.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun addCategory(name: String, color: String) {
        viewModelScope.launch { repo.add(Category(name = name, color = color)) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { repo.update(category) }
    }

    /** Soft delete en cascade : catégorie + tous ses membres actifs. */
    fun deleteCategoryWithCascade(categoryId: String) {
        viewModelScope.launch { repo.softDeleteWithCascade(categoryId) }
    }

    /** Bascule le favori d'une catégorie (la fait flotter en tête de liste). */
    fun toggleFavorite(category: Category) {
        viewModelScope.launch { repo.setFavorite(category.id, !category.isFavorite) }
    }

    // ── Mode sélection « Samsung Galerie » (actions de masse) ───────────────────

    /** Renomme une catégorie (mode sélection : action « Renommer »). */
    fun rename(category: Category, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repo.update(category.copy(name = trimmed)) }
    }

    /** Suppression de masse (soft-delete en cascade) des catégories cochées. */
    fun deleteSelected(ids: List<String>) {
        viewModelScope.launch { repo.softDeleteMultipleWithCascade(ids) }
    }

    /** Crée un groupe et y rattache les catégories cochées (≥ 2). */
    fun createGroup(name: String, ids: List<String>) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || ids.size < 2) return
        viewModelScope.launch { repo.createGroupWith(trimmed, ids) }
    }

    /** Persiste le nouvel ordre (drag & drop) : position = index dans la liste. */
    fun persistOrder(orderedIds: List<String>) {
        viewModelScope.launch { repo.persistOrder(orderedIds) }
    }

    // ── Dossiers (CategoryGroup) ───────────────────────────────────────────────

    fun toggleGroupFavorite(group: CategoryGroup) {
        viewModelScope.launch { repo.setGroupFavorite(group.id, !group.isFavorite) }
    }

    /** Attribue une image de couverture à un dossier. */
    fun setGroupImage(group: CategoryGroup, path: String) {
        viewModelScope.launch { repo.updateGroup(group.copy(imagePath = path)) }
    }

    fun renameGroup(group: CategoryGroup, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repo.renameGroup(group, trimmed) }
    }

    /** Dissout un groupe (détache ses catégories, qui redeviennent indépendantes). */
    fun deleteGroup(group: CategoryGroup, memberIds: List<String>) {
        viewModelScope.launch { repo.deleteGroup(group.id, memberIds) }
    }

    /** Supprime un groupe ET les catégories qu'il contient (soft-delete en cascade). */
    fun deleteGroupWithContents(group: CategoryGroup, memberIds: List<String>) {
        viewModelScope.launch {
            if (memberIds.isNotEmpty()) repo.softDeleteMultipleWithCascade(memberIds)
            repo.deleteGroupRow(group.id)
        }
    }

    /** Persiste l'ordre global mélangé (dossiers + catégories indépendantes). */
    fun persistTopOrder(refs: List<TopOrderRef>) {
        viewModelScope.launch { repo.persistTopOrder(refs) }
    }

    /** Insertion directe d'une catégorie dans un dossier (drop sur un dossier). */
    fun moveCategoryToGroup(categoryId: String, groupId: Long) {
        viewModelScope.launch { repo.assignToGroup(listOf(categoryId), groupId) }
    }

    /** Favori de masse pour la sélection (catégories + dossiers). */
    fun setFavoriteForSelection(categoryIds: List<String>, groupIds: List<Long>, favorite: Boolean) {
        viewModelScope.launch {
            categoryIds.forEach { repo.setFavorite(it, favorite) }
            groupIds.forEach { repo.setGroupFavorite(it, favorite) }
        }
    }

    /**
     * Déplace/fusionne la sélection vers un groupe cible : les catégories cochées y
     * sont rattachées ; chaque dossier coché est fusionné (ses membres rattachés au
     * cible, puis le dossier source supprimé). Expérience « Galerie Samsung ».
     */
    fun moveSelectionToGroup(
        categoryIds: List<String>,
        groupsToMerge: Map<Long, List<String>>,
        targetGroupId: Long
    ) {
        viewModelScope.launch {
            if (categoryIds.isNotEmpty()) repo.assignToGroup(categoryIds, targetGroupId)
            groupsToMerge.forEach { (gid, members) ->
                if (members.isNotEmpty()) repo.assignToGroup(members, targetGroupId)
                repo.deleteGroupRow(gid)
            }
        }
    }
}
