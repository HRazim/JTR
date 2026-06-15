package com.jtr.app.ui.category

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.R
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.data.repository.TopOrderRef
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.components.JtrSortCriterion
import com.jtr.app.ui.components.jtrSortCriterion
import com.jtr.app.ui.components.JtrViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Tri des entrées de premier niveau (catégories + dossiers), en PARITÉ avec les
 * contacts (v6.1.7) : Nom A → Z / Z → A + Dernière modification + Création
 * (récent → ancien / ancien → récent). Favoris toujours épinglés en tête.
 * L'« ordre personnalisé » (position du drag & drop) a été retiré (v6.1.3). Les
 * horodatages reposent sur `Category.createdAt` / `CategoryGroup.createdAt` et sur
 * la « dernière activité » calculée ([CategoryRepository.getCategoryLastActivity]).
 */
enum class CategorySortOrder { NAME_ASC, NAME_DESC, UPDATED_DESC, UPDATED_ASC, CREATED_DESC, CREATED_ASC }

/**
 * Critères de tri des CATÉGORIES / DOSSIERS, identiques aux contacts (mêmes libellés,
 * modèle à deux axes v6.2.6) : Nom · Date de modification · Date de création, chacun
 * avec un sens croissant/décroissant (défaut : Nom croissant, dates décroissant).
 */
internal fun categorySortCriteria(
    current: CategorySortOrder,
    onSelect: (CategorySortOrder) -> Unit
): List<JtrSortCriterion> = listOf(
    jtrSortCriterion(R.string.common_name_label, current,
        CategorySortOrder.NAME_ASC, CategorySortOrder.NAME_DESC,
        defaultDescending = false, onSelect = onSelect),
    jtrSortCriterion(R.string.sort_criterion_modified, current,
        CategorySortOrder.UPDATED_ASC, CategorySortOrder.UPDATED_DESC,
        defaultDescending = true, onSelect = onSelect),
    jtrSortCriterion(R.string.sort_criterion_created, current,
        CategorySortOrder.CREATED_ASC, CategorySortOrder.CREATED_DESC,
        defaultDescending = true, onSelect = onSelect),
)

/** Applique [order] à des entrées mixtes (dossiers + catégories indépendantes). */
internal fun sortTopEntries(entries: List<TopEntry>, order: CategorySortOrder): List<TopEntry> {
    val base = when (order) {
        CategorySortOrder.NAME_ASC -> compareBy<TopEntry> { it.sortName }
        CategorySortOrder.NAME_DESC -> compareByDescending<TopEntry> { it.sortName }
        CategorySortOrder.UPDATED_DESC -> compareByDescending<TopEntry> { it.lastActivity }
        CategorySortOrder.UPDATED_ASC -> compareBy<TopEntry> { it.lastActivity }
        CategorySortOrder.CREATED_DESC -> compareByDescending<TopEntry> { it.createdAt }
        CategorySortOrder.CREATED_ASC -> compareBy<TopEntry> { it.createdAt }
    }
    // Favoris toujours en tête (cohérent avec l'ordre DAO `isFavorite DESC`).
    return entries.sortedWith(compareByDescending<TopEntry> { it.isFavorite }.then(base))
}

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CategoryRepository(application.applicationContext)
    private val personRepo = PersonRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    /**
     * Nombre de contacts favoris actifs — pilote la catégorie VIRTUELLE
     * « Favoris » (affichée en tête dès 2 favoris, cf. TopEntriesBrowser).
     */
    val favoritePersonCount: StateFlow<Int> = personRepo.getAllActive()
        .map { list -> list.count { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Tri des entrées de premier niveau, persisté.
    private val _sortOrder = MutableStateFlow(
        runCatching { CategorySortOrder.valueOf(prefs.getString(SORT_PREF_KEY, null) ?: "") }
            .getOrDefault(CategorySortOrder.NAME_ASC)
    )
    val sortOrder: StateFlow<CategorySortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: CategorySortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(SORT_PREF_KEY, order.name).apply()
    }

    // Mode d'affichage (LIST / GRID / DETAIL), persisté.
    private val _viewMode = MutableStateFlow(
        JtrViewMode.fromPref(prefs.getString(VIEW_PREF_KEY, null), JtrViewMode.GRID)
    )
    val viewMode: StateFlow<JtrViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: JtrViewMode) {
        _viewMode.value = mode
        prefs.edit().putString(VIEW_PREF_KEY, mode.name).apply()
    }

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

    /**
     * « Dernière modification » par categoryId (v6.1.7) : max de l'ajout d'un membre
     * et de l'édition d'un membre. Alimente le tri « Dernière modification ».
     */
    val categoryActivity: StateFlow<Map<String, Long>> = repo.getCategoryLastActivity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Dossiers/groupes (triés favoris d'abord, puis position). */
    val groups: StateFlow<List<CategoryGroup>> = repo.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /** Réinitialise la recherche (appelé quand l'écran quitte la composition). */
    fun clearSearch() { _searchQuery.value = "" }

    /** Crée une catégorie — image de couverture définissable DÈS la création. */
    fun addCategory(name: String, color: String, imagePath: String? = null) {
        viewModelScope.launch { repo.add(Category(name = name, color = color, imagePath = imagePath)) }
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

    companion object {
        private const val SORT_PREF_KEY = "categories_sort_order"
        private const val VIEW_PREF_KEY = "categories_view_mode"
    }
}
