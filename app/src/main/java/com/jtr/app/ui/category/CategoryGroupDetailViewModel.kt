package com.jtr.app.ui.category

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.CategoryRepository
import com.jtr.app.data.repository.TopOrderRef
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
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
 * ViewModel de l'écran « intérieur d'un dossier » (drill-down). Expose le groupe et
 * ses sous-catégories (catégories dont `parentGroupId == groupId`), de façon réactive.
 */
class CategoryGroupDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repo = CategoryRepository(application.applicationContext)
    private val groupId: Long = savedStateHandle.get<Long>("groupId") ?: -1L
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    // ── Recherche / tri / affichage (TopAppBar harmonisée) ─────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    /** Réinitialise la recherche (appelé quand l'écran quitte la composition). */
    fun clearSearch() { _searchQuery.value = "" }

    private val _sortOrder = MutableStateFlow(
        runCatching { CategorySortOrder.valueOf(prefs.getString(SORT_PREF_KEY, null) ?: "") }
            .getOrDefault(CategorySortOrder.CUSTOM)
    )
    val sortOrder: StateFlow<CategorySortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: CategorySortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(SORT_PREF_KEY, order.name).apply()
    }

    private val _viewMode = MutableStateFlow(
        JtrViewMode.fromPref(prefs.getString(VIEW_PREF_KEY, null), JtrViewMode.GRID)
    )
    val viewMode: StateFlow<JtrViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: JtrViewMode) {
        _viewMode.value = mode
        prefs.edit().putString(VIEW_PREF_KEY, mode.name).apply()
    }

    val group: StateFlow<CategoryGroup?> = repo.getGroups()
        .map { list -> list.firstOrNull { it.id == groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val members: StateFlow<List<Category>> = repo.getAllActive()
        .map { list -> list.filter { it.parentGroupId == groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Sous-groupes directs de ce groupe (imbrication). */
    val subGroups: StateFlow<List<CategoryGroup>> = repo.getGroups()
        .map { list -> list.filter { it.parentGroupId == groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Contenu mixte du dossier : sous-groupes (folders) + sous-catégories (singles),
     * filtré par la recherche et trié selon le critère choisi (UDF complet).
     */
    internal val topEntries: StateFlow<List<TopEntry>> = combine(
        repo.getAllActive(), repo.getGroups(), _searchQuery, _sortOrder
    ) { cats, grps, query, order ->
        val membersByGroup = cats.filter { it.parentGroupId != null }.groupBy { it.parentGroupId!! }
        val subByParent = grps.filter { it.parentGroupId != null }.groupBy { it.parentGroupId!! }
        val folders = grps.filter { it.parentGroupId == groupId }.map { sg ->
            TopEntry.Folder(sg, membersByGroup[sg.id] ?: emptyList(), subByParent[sg.id]?.size ?: 0)
        }
        val singles = cats.filter { it.parentGroupId == groupId }.map { TopEntry.Single(it) }
        val all = folders + singles
        val filtered =
            if (query.isBlank()) all
            else all.filter { it.sortName.contains(query.trim().lowercase()) }
        sortTopEntries(filtered, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personCountByCategory: StateFlow<Map<String, Int>> = repo.getPersonCountsPerCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Catégories candidates à l'ajout : toutes celles qui ne sont pas déjà dans ce groupe. */
    val candidateCategories: StateFlow<List<Category>> = repo.getAllActive()
        .map { list -> list.filter { it.parentGroupId != groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Rattache des catégories existantes à ce groupe. */
    fun addExisting(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { repo.assignToGroup(ids, groupId) }
    }

    /** Crée une catégorie dans ce groupe — image définissable DÈS la création. */
    fun createInGroup(name: String, color: String, imagePath: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repo.add(Category(name = trimmed, color = color,
                imagePath = imagePath, parentGroupId = groupId))
        }
    }

    /**
     * Met le dossier à la CORBEILLE : ses catégories (et leurs contacts) passent
     * en soft-delete, ses sous-groupes REMONTENT d'un niveau (jamais orphelins),
     * puis la ligne du groupe est supprimée.
     */
    fun deleteGroupToTrash() {
        viewModelScope.launch {
            val parent = group.value?.parentGroupId
            subGroups.value.forEach { repo.updateGroup(it.copy(parentGroupId = parent)) }
            val memberIds = members.value.map { it.id }
            if (memberIds.isNotEmpty()) repo.softDeleteMultipleWithCascade(memberIds)
            repo.deleteGroupRow(groupId)
        }
    }

    /** Défait le groupe : ses catégories redeviennent indépendantes (racine). */
    fun dissolve() {
        viewModelScope.launch {
            repo.deleteGroup(groupId, members.value.map { it.id })
        }
    }

    // ── Mode sélection à l'intérieur du dossier ────────────────────────────────

    /** Autres groupes (cibles de transfert), excluant le dossier courant. */
    val otherGroups: StateFlow<List<CategoryGroup>> = repo.getGroups()
        .map { list -> list.filter { it.id != groupId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFavorite(ids: List<String>, favorite: Boolean) {
        viewModelScope.launch { ids.forEach { repo.setFavorite(it, favorite) } }
    }

    fun deleteCategories(ids: List<String>) {
        viewModelScope.launch { repo.softDeleteMultipleWithCascade(ids) }
    }

    fun rename(category: Category, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repo.update(category.copy(name = trimmed)) }
    }

    fun updateCategory(updated: Category) {
        viewModelScope.launch { repo.update(updated) }
    }

    /** Déplace des sous-catégories : vers la racine (null) ou vers un autre groupe. */
    fun moveToGroup(ids: List<String>, targetGroupId: Long?) {
        if (ids.isEmpty()) return
        viewModelScope.launch { repo.assignToGroup(ids, targetGroupId) }
    }

    /** Persiste le nouvel ordre (drag & drop) du contenu du dossier. */
    fun persistOrder(refs: List<TopOrderRef>) {
        viewModelScope.launch { repo.persistTopOrder(refs) }
    }

    // ── Sous-groupes imbriqués (création par superposition / insertion) ─────────

    /** Crée un SOUS-GROUPE dans ce dossier (fusion de 2 sous-catégories au centre). */
    fun createSubGroup(name: String, ids: List<String>) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || ids.isEmpty()) return
        viewModelScope.launch { repo.createGroupWith(trimmed, ids, parentGroupId = groupId) }
    }

    /** Insère une catégorie dans un sous-groupe (drop d'une catégorie sur un dossier). */
    fun moveCategoryToSubGroup(categoryId: String, subGroupId: Long) {
        viewModelScope.launch { repo.assignToGroup(listOf(categoryId), subGroupId) }
    }

    fun toggleGroupFavorite(g: CategoryGroup) {
        viewModelScope.launch { repo.setGroupFavorite(g.id, !g.isFavorite) }
    }

    fun renameGroup(g: CategoryGroup, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repo.renameGroup(g, trimmed) }
    }

    fun setGroupImage(g: CategoryGroup, path: String) {
        viewModelScope.launch { repo.updateGroup(g.copy(imagePath = path)) }
    }

    /** Favori de masse pour la sélection mixte (catégories + sous-groupes). */
    fun setFavoriteForSelection(categoryIds: List<String>, groupIds: List<Long>, favorite: Boolean) {
        viewModelScope.launch {
            categoryIds.forEach { repo.setFavorite(it, favorite) }
            groupIds.forEach { repo.setGroupFavorite(it, favorite) }
        }
    }

    /** Supprime la sélection : catégories + sous-groupes (et leur contenu, cascade). */
    fun deleteSelection(categoryIds: List<String>, subGroups: List<CategoryGroup>, allCategories: List<Category>) {
        viewModelScope.launch {
            if (categoryIds.isNotEmpty()) repo.softDeleteMultipleWithCascade(categoryIds)
            subGroups.forEach { sg ->
                val ids = allCategories.filter { it.parentGroupId == sg.id }.map { it.id }
                if (ids.isNotEmpty()) repo.softDeleteMultipleWithCascade(ids)
                repo.deleteGroupRow(sg.id)
            }
        }
    }

    /**
     * RELOCALISE la sélection vers un autre groupe (ou la racine si null) : les
     * catégories changent de `parentGroupId`, les sous-groupes aussi (restent intacts).
     */
    fun relocateSelection(
        categoryIds: List<String>,
        subGroups: List<CategoryGroup>,
        targetGroupId: Long?
    ) {
        viewModelScope.launch {
            if (categoryIds.isNotEmpty()) repo.assignToGroup(categoryIds, targetGroupId)
            subGroups.forEach { repo.updateGroup(it.copy(parentGroupId = targetGroupId)) }
        }
    }

    /** Persiste l'ordre global mélangé (sous-groupes + catégories). */
    fun persistTopOrder(refs: List<TopOrderRef>) {
        viewModelScope.launch { repo.persistTopOrder(refs) }
    }

    companion object {
        private const val SORT_PREF_KEY = "groups_sort_order"
        private const val VIEW_PREF_KEY = "groups_view_mode"
    }
}
