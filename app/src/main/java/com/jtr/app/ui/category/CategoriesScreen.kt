package com.jtr.app.ui.category

import android.net.Uri
import android.util.Log
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.ImageCropDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.data.repository.TopOrderRef
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.components.FavoriteStar
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.components.JtrSelectionCheck
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.components.rememberGalleryImagePicker
import com.jtr.app.ui.navigation.nestedScreenContentInsets
import com.jtr.app.ui.share.CategoriesSharePreview
import com.jtr.app.ui.share.ShareCategoryItem
import com.jtr.app.ui.share.ShareFormatSheet
import com.jtr.app.ui.share.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Entrée de premier niveau de l'écran : un dossier (avec ses membres) OU une
 * catégorie indépendante. Sert au tri global (favoris → position → nom) et au
 * drag & drop mixte dossiers/catégories.
 */
internal sealed interface TopEntry {
    val isFavorite: Boolean
    val position: Int
    val sortName: String
    // v6.1.7 — horodatages pour le tri « Temps » (parité contacts).
    val createdAt: Long
    val lastActivity: Long

    data class Folder(
        val group: CategoryGroup,
        val members: List<Category>,
        val subGroupCount: Int = 0,
        // Dernière activité du dossier = max des activités de ses catégories ; à
        // défaut (dossier vide), retombe sur sa date de création.
        override val lastActivity: Long = group.createdAt
    ) : TopEntry {
        override val isFavorite get() = group.isFavorite
        override val position get() = group.position
        override val sortName get() = group.name.lowercase()
        override val createdAt get() = group.createdAt
    }

    data class Single(
        val category: Category,
        // Activité de la catégorie ; à défaut (aucun membre actif), sa date de création.
        override val lastActivity: Long = category.createdAt
    ) : TopEntry {
        override val isFavorite get() = category.isFavorite
        override val position get() = category.position
        override val sortName get() = category.name.lowercase()
        override val createdAt get() = category.createdAt
    }
}

/**
 * Couleur d'accent d'une catégorie — SOURCE UNIQUE (v6.2.5) partagée par TOUS les
 * modes (liste, détail, grille, sélection). Parse `category.color` ; en cas de
 * valeur absente/invalide, retombe sur un TOKEN DE THÈME (`colorScheme.primary`)
 * plutôt qu'un bleu codé en dur → la vraie couleur est respectée de façon identique
 * partout, et aucune couleur n'est codée en dur.
 */
@Composable
internal fun rememberCategoryColor(hex: String): Color {
    val fallback = MaterialTheme.colorScheme.primary
    return remember(hex, fallback) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (String) -> Unit = {},
    onGroupClick: (Long) -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {},
    isPickingMoveTarget: Boolean = false,
    onCancelMoveTarget: () -> Unit = {},
    onCreateMoveTarget: (name: String, color: String, imagePath: String?) -> Unit = { _, _, _ -> },
    viewModel: CategoryViewModel = viewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val personCountByCategory by viewModel.personCountByCategory.collectAsStateWithLifecycle()
    // « Dernière modification » par catégorie (v6.1.7) → tri « Temps ».
    val categoryActivity by viewModel.categoryActivity.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val favoritePersonCount by viewModel.favoritePersonCount.collectAsStateWithLifecycle()
    // Mode d'affichage persistant (LIST / GRID / DETAIL), restauré depuis jtr_prefs.
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingEditCategory by remember { mutableStateOf<Category?>(null) }
    // Mode recherche de la TopAppBar (état d'UI local ; la query vient du ViewModel).
    var searchActive by remember { mutableStateOf(false) }

    val groups by viewModel.groups.collectAsStateWithLifecycle()

    // ── Mode sélection multiple « Samsung Galerie » ───────────────────────────
    var isSelectionActive by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }       // catégories cochées
    val selectedGroupIds = remember { mutableStateListOf<Long>() }    // dossiers cochés
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var renameGroupTarget by remember { mutableStateOf<CategoryGroup?>(null) }
    // Fusion par superposition (drop d'une catégorie sur une autre) → nommer le groupe.
    var pendingMergeIds by remember { mutableStateOf<List<String>?>(null) }
    // Changement d'image de couverture d'un dossier (mode sélection).
    var groupImageTarget by remember { mutableStateOf<CategoryGroup?>(null) }
    var pendingGroupCropUri by remember { mutableStateOf<Uri?>(null) }
    val screenContext = LocalContext.current
    val screenScope = rememberCoroutineScope()
    // Galerie IN-APP par ALBUMS (v5.5).
    val groupPhotoPicker = rememberGalleryImagePicker { uri -> pendingGroupCropUri = uri }

    // Reporte l'état de sélection au conteneur (masque la nav globale).
    LaunchedEffect(isSelectionActive) { onSelectionModeChange(isSelectionActive) }
    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
            // Sortie d'écran → réinitialisation du filtre (aucune query fantôme au retour).
            viewModel.clearSearch()
        }
    }

    val totalSelected = selectedIds.size + selectedGroupIds.size
    fun exitSelection() {
        isSelectionActive = false; selectedIds.clear(); selectedGroupIds.clear()
    }

    // Bouton Retour en mode sélection : vide la sélection et reste sur l'écran.
    // Inactif hors sélection → la navigation standard (retour d'écran) reprend.
    BackHandler(enabled = isSelectionActive) { exitSelection() }
    // Cocher/décocher NE ferme PAS le mode sélection : on y reste (affichage « 0
    // sélectionné »). Seul « Annuler » de la TopBar quitte le mode.
    fun toggleSelectCategory(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }
    fun toggleSelectGroup(id: Long) {
        if (selectedGroupIds.contains(id)) selectedGroupIds.remove(id) else selectedGroupIds.add(id)
    }
    fun startSelectionCategory(id: String) {
        isSelectionActive = true
        if (!selectedIds.contains(id)) selectedIds.add(id)
    }
    fun startSelectionGroup(id: Long) {
        isSelectionActive = true
        if (!selectedGroupIds.contains(id)) selectedGroupIds.add(id)
    }

    // Nettoie les cochés qui n'existent plus (suppression concurrente). On NE quitte
    // PAS le mode sélection même si la liste devient vide (l'utilisateur reste maître).
    LaunchedEffect(categories, groups) {
        selectedIds.retainAll(categories.map { it.id }.toSet())
        selectedGroupIds.retainAll(groups.map { it.id }.toSet())
    }

    val selectedCategories = categories.filter { it.id in selectedIds }
    val selectedGroups = groups.filter { it.id in selectedGroupIds }

    // ── Entrées de premier niveau : dossiers + catégories indépendantes ───────
    val isSearching = searchQuery.isNotBlank()
    val topEntries = remember(categories, groups, sortOrder, categoryActivity) {
        val membersByGroup = categories.filter { it.parentGroupId != null }
            .groupBy { it.parentGroupId!! }
        val subByParent = groups.filter { it.parentGroupId != null }
            .groupBy { it.parentGroupId!! }
        // Activité d'une catégorie : la valeur calculée, sinon sa date de création.
        fun activityOf(c: Category) = categoryActivity[c.id] ?: c.createdAt
        // À la racine : seulement les groupes de PREMIER NIVEAU (parentGroupId == null).
        val folders = groups.filter { it.parentGroupId == null }.map { g ->
            val memberCats = membersByGroup[g.id]?.sortedBy { it.name.lowercase() } ?: emptyList()
            // Dernière activité du dossier = max(création du dossier, activités membres).
            val folderActivity = memberCats.fold(g.createdAt) { acc, c -> maxOf(acc, activityOf(c)) }
            TopEntry.Folder(g, memberCats, subByParent[g.id]?.size ?: 0, lastActivity = folderActivity)
        }
        val singles = categories.filter { it.parentGroupId == null }
            .map { TopEntry.Single(it, lastActivity = activityOf(it)) }
        sortTopEntries(folders + singles, sortOrder)
    }
    fun selectAll() {
        topEntries.forEach { entry ->
            when (entry) {
                is TopEntry.Folder -> if (entry.group.id !in selectedGroupIds) selectedGroupIds.add(entry.group.id)
                is TopEntry.Single -> if (entry.category.id !in selectedIds) selectedIds.add(entry.category.id)
            }
        }
        if (selectedIds.isNotEmpty() || selectedGroupIds.isNotEmpty()) isSelectionActive = true
    }

    // ── Dialogues du mode sélection ───────────────────────────────────────────
    if (showCreateGroupDialog) {
        TextPromptDialog(
            title = stringResource(R.string.categories_create_group),
            hint = stringResource(R.string.categories_group_name_hint),
            initial = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name ->
                viewModel.createGroup(name, selectedIds.toList())
                showCreateGroupDialog = false
                exitSelection()
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }

    renameTarget?.let { target ->
        TextPromptDialog(
            title = stringResource(R.string.categories_rename),
            hint = stringResource(R.string.common_name_label),
            initial = target.name,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name ->
                viewModel.rename(target, name)
                renameTarget = null
                exitSelection()
            },
            onDismiss = { renameTarget = null }
        )
    }

    // Fusion par geste : nommer le groupe créé à partir des 2 catégories superposées.
    pendingMergeIds?.let { ids ->
        TextPromptDialog(
            title = stringResource(R.string.categories_create_group),
            hint = stringResource(R.string.categories_group_name_hint),
            initial = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name ->
                viewModel.createGroup(name, ids)
                pendingMergeIds = null
                exitSelection()
            },
            onDismiss = { pendingMergeIds = null }
        )
    }

    renameGroupTarget?.let { target ->
        TextPromptDialog(
            title = stringResource(R.string.categories_rename),
            hint = stringResource(R.string.categories_group_name_hint),
            initial = target.name,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name ->
                viewModel.renameGroup(target, name)
                renameGroupTarget = null
                exitSelection()
            },
            onDismiss = { renameGroupTarget = null }
        )
    }

    if (showBulkDeleteDialog) {
        // Dialogue type-aware : groupe unique → texte « groupe + ses catégories ».
        val singleGroup = selectedGroups.singleOrNull()?.takeIf { selectedIds.isEmpty() }
        val singleGroupMemberCount = singleGroup?.let { g ->
            categories.count { it.parentGroupId == g.id }
        } ?: 0
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    if (singleGroup != null)
                        stringResource(R.string.categories_delete_group_title, singleGroup.name)
                    else stringResource(R.string.categories_delete_selected_title, totalSelected)
                )
            },
            text = {
                Text(
                    if (singleGroup != null)
                        stringResource(R.string.categories_delete_group_text,
                            singleGroup.name, singleGroupMemberCount)
                    else stringResource(R.string.categories_delete_selected_text)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedIds.isNotEmpty()) viewModel.deleteSelected(selectedIds.toList())
                        // Suppression du groupe ET de son contenu (cascade).
                        selectedGroups.forEach { g ->
                            viewModel.deleteGroupWithContents(
                                g, categories.filter { it.parentGroupId == g.id }.map { it.id })
                        }
                        showBulkDeleteDialog = false
                        exitSelection()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingGroupCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = CropShape.RECTANGLE,
            onCropComplete = { cropped ->
                groupImageTarget?.let { target ->
                    screenScope.launch {
                        val path = withContext(Dispatchers.IO) {
                            copyCategoryPhotoToStorage(screenContext, cropped)
                        }
                        if (path != null) viewModel.setGroupImage(target, path)
                    }
                }
                pendingGroupCropUri = null
                groupImageTarget = null
                exitSelection()
            },
            onDismiss = { pendingGroupCropUri = null; groupImageTarget = null }
        )
    }


    pendingEditCategory?.let { category ->
        EditCategoryDialog(
            category = category,
            onConfirm = { name, color, imagePath ->
                viewModel.updateCategory(category.copy(name = name, color = color, imagePath = imagePath))
                pendingEditCategory = null
            },
            onDismiss = { pendingEditCategory = null }
        )
    }

    // Création de catégorie À LA VOLÉE pendant le déplacement de contacts (mode
    // cible) : MÊME formulaire unifié que partout (nom + couleur + image + crop).
    var showCreateTargetDialog by remember { mutableStateOf(false) }
    if (showCreateTargetDialog) {
        AddCategoryDialog(
            onConfirm = { name, color, imagePath ->
                showCreateTargetDialog = false
                onCreateMoveTarget(name, color, imagePath)
            },
            onDismiss = { showCreateTargetDialog = false }
        )
    }

    // Partage contextuel : instantané de la sélection (catégories + dossiers).
    var shareItems by remember { mutableStateOf<List<ShareCategoryItem>?>(null) }
    shareItems?.let { items ->
        ShareFormatSheet(
            onDismiss = { shareItems = null },
            buildText = { ShareUtils.buildCategoriesShareText(screenContext, items) },
            writePdf = { ShareUtils.writeCategoriesPdf(screenContext, items) },
            preview = { CategoriesSharePreview(items) }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionActive) {
                // En-tête de sélection : Tout sélectionner (G) · compteur (C) · Annuler (D).
                TopAppBar(
                    title = {
                        Text(
                            if (totalSelected == 0) stringResource(R.string.categories_selection_none)
                            else stringResource(R.string.categories_selection_count, totalSelected)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectAll() }) {
                            Icon(Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.select_all))
                        }
                    },
                    actions = {
                        TextButton(onClick = { exitSelection() }) {
                            Text(stringResource(R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else if (isPickingMoveTarget) {
                // Mode « choisir une cible » (déplacement de contacts depuis l'Accueil) :
                // toucher une catégorie l'assigne, toucher un dossier y descend.
                TopAppBar(
                    title = { Text(stringResource(R.string.move_pick_target_title)) },
                    actions = {
                        TextButton(onClick = onCancelMoveTarget) {
                            Text(stringResource(R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            } else {
                // Barre harmonisée : loupe (à gauche du « + ») + ajout + menu 3 points
                // (sous-sections Tri / Affichage — l'ancien bouton de bascule disparaît).
                JtrSearchableTopAppBar(
                    title = stringResource(R.string.categories_title),
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it },
                    searchPlaceholder = stringResource(R.string.home_search_placeholder),
                    actions = {
                        // Header minimaliste (v5.3.1) : Loupe → « + » → 3 points.
                        // Tri et Affichage vivent EXCLUSIVEMENT dans le menu 3 points.
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.categories_fab_add_cd)
                            )
                        }
                        JtrOverflowMenu(
                            sortCriteria = categorySortCriteria(sortOrder) { viewModel.setSortOrder(it) },
                            viewMode = viewMode,
                            onViewModeChange = { viewModel.setViewMode(it) }
                        )
                    }
                )
            }
        },
        // Bas couvert par le Scaffold racine (footer global) / la BottomAppBar contextuelle ;
        // on n'applique au CONTENU que l'inset HORIZONTAL (barre de nav latérale en paysage
        // 3 boutons) via la règle centralisée — sinon le contenu passe sous la barre (v7.1.11).
        contentWindowInsets = nestedScreenContentInsets,
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionActive,
                enter = JtrBottomBarTransitions.enter,
                exit = JtrBottomBarTransitions.exit
            ) {
                val allFavorite = totalSelected > 0 &&
                    selectedCategories.all { it.isFavorite } && selectedGroups.all { it.isFavorite }
                val movableGroups = groups.filter { it.id !in selectedGroupIds }
                SelectionFooter(
                    canFavorite = totalSelected >= 1,
                    favoriteActive = allFavorite,
                    onToggleFavorite = {
                        viewModel.setFavoriteForSelection(
                            selectedIds.toList(), selectedGroupIds.toList(), !allFavorite)
                        exitSelection()
                    },
                    canCreateGroup = selectedIds.size >= 2 && selectedGroupIds.isEmpty(),
                    movableGroups = movableGroups,
                    canMove = totalSelected >= 1 && movableGroups.isNotEmpty(),
                    onCreateGroup = { showCreateGroupDialog = true },
                    onMoveToGroup = { targetId ->
                        val merges = selectedGroupIds.associateWith { gid ->
                            categories.filter { it.parentGroupId == gid }.map { it.id }
                        }
                        viewModel.moveSelectionToGroup(selectedIds.toList(), merges, targetId)
                        exitSelection()
                    },
                    canDelete = totalSelected >= 1,
                    onDelete = { showBulkDeleteDialog = true },
                    canShare = totalSelected >= 1,
                    onShare = {
                        // Dossiers : noms des catégories membres (via topEntries) ;
                        // catégories : compteur de contacts.
                        val folderEntries = topEntries.filterIsInstance<TopEntry.Folder>()
                        shareItems = buildList {
                            selectedGroups.forEach { g ->
                                val entry = folderEntries.firstOrNull { it.group.id == g.id }
                                add(ShareCategoryItem(
                                    name = g.name,
                                    isFolder = true,
                                    memberNames = entry?.members?.map { it.name } ?: emptyList(),
                                    subGroupCount = entry?.subGroupCount ?: 0
                                ))
                            }
                            selectedCategories.forEach { c ->
                                add(ShareCategoryItem(
                                    name = c.name,
                                    isFolder = false,
                                    personCount = personCountByCategory[c.id] ?: 0
                                ))
                            }
                        }
                    },
                    canRename = totalSelected == 1,
                    onRename = {
                        val cat = selectedCategories.singleOrNull()
                        if (cat != null) renameTarget = cat
                        else selectedGroups.singleOrNull()?.let { renameGroupTarget = it }
                    },
                    canChangeImage = totalSelected == 1,
                    onChangeImage = {
                        val cat = selectedCategories.singleOrNull()
                        if (cat != null) {
                            pendingEditCategory = cat
                        } else selectedGroups.singleOrNull()?.let { g ->
                            groupImageTarget = g
                            groupPhotoPicker()
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val countOf: (String) -> Int = { personCountByCategory[it] ?: 0 }
            if (isSelectionActive) {
                // Mode sélection : le mode d'affichage courant est CONSERVÉ (le clic
                // long ne force plus la grille). Grille → drag & drop 2D ; Liste /
                // Détail → réordonnancement vertical, même logique persistée.
                when (viewMode) {
                    JtrViewMode.GRID -> ReorderableTopGrid(
                        entries = topEntries,
                        isCategorySelected = { it in selectedIds },
                        isGroupSelected = { it in selectedGroupIds },
                        countOf = countOf,
                        onToggleCategory = { toggleSelectCategory(it) },
                        onToggleGroup = { toggleSelectGroup(it) },
                        onPersistOrder = { viewModel.persistTopOrder(it) },
                        onMergeRequest = { ids -> pendingMergeIds = ids },
                        onMoveToFolder = { categoryId, groupId ->
                            viewModel.moveCategoryToGroup(categoryId, groupId)
                            exitSelection()
                        },
                        enableMerge = true
                    )
                    else -> ReorderableTopList(
                        entries = topEntries,
                        isCategorySelected = { it in selectedIds },
                        isGroupSelected = { it in selectedGroupIds },
                        countOf = countOf,
                        onToggleCategory = { toggleSelectCategory(it) },
                        onToggleGroup = { toggleSelectGroup(it) },
                        onPersistOrder = { viewModel.persistTopOrder(it) },
                        onMergeRequest = { ids -> pendingMergeIds = ids },
                        onMoveToFolder = { categoryId, groupId ->
                            viewModel.moveCategoryToGroup(categoryId, groupId)
                            exitSelection()
                        },
                        enableMerge = true
                    )
                }
            } else if (categories.isEmpty() && groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.categories_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.categories_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Mode cible : « ➕ Nouvelle catégorie » en PREMIER choix fixe — crée
                // la cible à la volée et y déplace immédiatement les contacts cochés.
                if (isPickingMoveTarget) {
                    OutlinedButton(
                        onClick = { showCreateTargetDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.move_create_category))
                    }
                }
                // Recherche : entrées filtrées à plat (catégories seules, sans dossiers) ;
                // sinon arborescence complète. Rendu commutable LIST / GRID / DETAIL.
                val entries =
                    if (isSearching) categories.map { TopEntry.Single(it) } else topEntries
                TopEntriesBrowser(
                    entries = entries,
                    viewMode = viewMode,
                    countOf = countOf,
                    onCategoryClick = onCategoryClick,
                    onGroupClick = onGroupClick,
                    onCategoryLongClick = { startSelectionCategory(it) },
                    onGroupLongClick = { startSelectionGroup(it) },
                    // Tuile virtuelle « Favoris » : hors recherche et hors mode cible
                    // (elle ne peut pas recevoir de contacts déplacés).
                    favoritesCount = if (isSearching || isPickingMoveTarget) 0 else favoritePersonCount,
                    onFavoritesClick = {
                        onCategoryClick(CategoryDetailViewModel.FAVORITES_CATEGORY_ID)
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onConfirm = { name, color, imagePath ->
                viewModel.addCategory(name, color, imagePath)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/**
 * Calque de protection des tuiles à image : dégradé vertical ASYMÉTRIQUE
 * (transparent → noir 70 %) démarrant à mi-tuile (45 % de la hauteur) et
 * s'assombrissant vers le bas — n'importe quelle photo (claire, sombre,
 * paysage, portrait) laisse le texte blanc lisible.
 *
 * Performance 120 Hz : dessiné via [drawWithCache] — le [Brush] n'est recréé
 * QUE si la taille de la tuile change. Les translations du drag & drop passent
 * par graphicsLayer (aucune re-mesure) : zéro recomposition, zéro réallocation
 * de shader pendant le glissement.
 */
private fun Modifier.bottomScrim(): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
        startY = size.height * 0.45f,
        endY = size.height
    )
    onDrawBehind { drawRect(brush) }
}

/**
 * Tuile de catégorie style « Galerie » : la photo de couverture remplit le fond,
 * le nom (et le nombre de contacts) est superposé en bas sur le calque de
 * protection [bottomScrim]. Clic court → ouvre ; clic long → mode sélection.
 * Aucun menu d'action individuel : l'appui long + le footer contextuel sont les
 * seuls maîtres.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryGridTile(
    category: Category,
    personCount: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val accent = rememberCategoryColor(category.color)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(accent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Fond : photo de couverture en grand format, sinon icône centrée.
        if (category.imagePath != null) {
            AsyncImage(
                model = category.imagePath,
                contentDescription = category.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp).align(Alignment.Center)
            )
        }

        // Calque de protection proportionnel (mi-tuile → bas), mis en cache.
        Box(modifier = Modifier.fillMaxSize().bottomScrim())

        // Étoile « favori » en haut à DROITE (indicateur de statut, non interactif).
        if (category.isFavorite) {
            FavoriteStar(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                size = 22.dp
            )
        }

        // Identité incrustée : titre sur UNE ligne + compteur de contacts.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.categories_person_count, personCount),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Ligne de catégorie en mode Liste : avatar circulaire + nom + compteur, avec
 * le menu « 3 points » à droite. Clic court → ouvre ; clic long → menu d'actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryListRow(
    category: Category,
    personCount: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val accent = rememberCategoryColor(category.color)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Barre d'accent verticale à la couleur de la catégorie — enfant clippé
            // par la carte arrondie (pas de border-left aux coins disgracieux).
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(accent))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar : couverture si dispo, sinon cercle teinté + icône dans la couleur.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (category.imagePath != null) accent else accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (category.imagePath != null) {
                        AsyncImage(
                            model = category.imagePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = accent,
                            modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = category.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        text = stringResource(R.string.categories_person_count, personCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                // Étoile « favori » à DROITE (indicateur de statut, non interactif).
                if (category.isFavorite) {
                    FavoriteStar(size = 20.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Clé stable d'une entrée mixte (dossier / catégorie) pour les listes lazy. */
internal fun topEntryKey(entry: TopEntry): String = when (entry) {
    is TopEntry.Folder -> "g_${entry.group.id}"
    is TopEntry.Single -> "c_${entry.category.id}"
}

/**
 * Rendu commutable (LIST / GRID / DETAIL) des entrées de premier niveau — PARTAGÉ
 * entre la racine des catégories et l'intérieur des dossiers :
 * - GRID   : carrés « Galerie » (tuiles photo) ;
 * - LIST   : lignes compactes (avatar + nom uniquement) ;
 * - DETAIL : lignes larges avec compteurs (contacts / sous-éléments).
 */
@Composable
internal fun TopEntriesBrowser(
    entries: List<TopEntry>,
    viewMode: JtrViewMode,
    countOf: (String) -> Int,
    onCategoryClick: (String) -> Unit,
    onGroupClick: (Long) -> Unit,
    onCategoryLongClick: (String) -> Unit,
    onGroupLongClick: (Long) -> Unit,
    favoritesCount: Int = 0,
    onFavoritesClick: () -> Unit = {}
) {
    // Catégorie VIRTUELLE « Favoris » : visible dès 2 contacts favoris, toujours
    // en tête (les favoris restent par ailleurs premiers dans leurs catégories).
    val showFavorites = favoritesCount >= 2
    when (viewMode) {
        JtrViewMode.GRID -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showFavorites) {
                item(key = "jtr_favorites") {
                    FavoritesGridTile(count = favoritesCount, onClick = onFavoritesClick)
                }
            }
            items(entries, key = { topEntryKey(it) }) { entry ->
                when (entry) {
                    is TopEntry.Folder -> FolderGridTile(
                        group = entry.group,
                        memberCount = entry.members.size,
                        subGroupCount = entry.subGroupCount,
                        onClick = { onGroupClick(entry.group.id) },
                        onLongClick = { onGroupLongClick(entry.group.id) },
                        // Cumul des contacts du dossier (somme des compteurs membres).
                        personTotal = entry.members.sumOf { countOf(it.id) })
                    is TopEntry.Single -> CategoryGridTile(
                        entry.category, countOf(entry.category.id),
                        onClick = { onCategoryClick(entry.category.id) },
                        onLongClick = { onCategoryLongClick(entry.category.id) })
                }
            }
        }

        JtrViewMode.DETAIL -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showFavorites) {
                item(key = "jtr_favorites") {
                    FavoritesRow(count = favoritesCount, showCount = true,
                        onClick = onFavoritesClick)
                }
            }
            items(entries, key = { topEntryKey(it) }) { entry ->
                when (entry) {
                    is TopEntry.Folder -> FolderListRow(
                        group = entry.group,
                        memberCount = entry.members.size,
                        subGroupCount = entry.subGroupCount,
                        onClick = { onGroupClick(entry.group.id) },
                        onLongClick = { onGroupLongClick(entry.group.id) })
                    is TopEntry.Single -> CategoryListRow(
                        entry.category, countOf(entry.category.id),
                        onClick = { onCategoryClick(entry.category.id) },
                        onLongClick = { onCategoryLongClick(entry.category.id) })
                }
            }
        }

        JtrViewMode.LIST -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showFavorites) {
                item(key = "jtr_favorites") {
                    FavoritesRow(count = favoritesCount, showCount = false,
                        onClick = onFavoritesClick)
                }
            }
            items(entries, key = { topEntryKey(it) }) { entry ->
                when (entry) {
                    is TopEntry.Folder -> EntryCompactRow(
                        isFolder = true,
                        name = entry.group.name,
                        isFavorite = entry.group.isFavorite,
                        imagePath = entry.group.imagePath,
                        accent = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { onGroupClick(entry.group.id) },
                        onLongClick = { onGroupLongClick(entry.group.id) })
                    is TopEntry.Single -> {
                        val accent = rememberCategoryColor(entry.category.color)
                        EntryCompactRow(
                            isFolder = false,
                            name = entry.category.name,
                            isFavorite = entry.category.isFavorite,
                            imagePath = entry.category.imagePath,
                            accent = accent,
                            onClick = { onCategoryClick(entry.category.id) },
                            onLongClick = { onCategoryLongClick(entry.category.id) })
                    }
                }
            }
        }
    }
}

/** Tuile (grille) de la catégorie virtuelle « Favoris » : or + étoile + compteur. */
@Composable
private fun FavoritesGridTile(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                )
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(56.dp).align(Alignment.Center)
        )
        Box(modifier = Modifier.fillMaxSize().bottomScrim())
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.favorites_category),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.categories_person_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

/** Ligne (liste/détail) de la catégorie virtuelle « Favoris ». */
@Composable
private fun FavoritesRow(count: Int, showCount: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        // Surface ACCORDÉE à la palette (même token que l'accueil/Paramètres/mode Détails),
        // au lieu du `surfaceContainerHighest` par défaut du Card (neutre/lavande en clair).
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (showCount) 48.dp else 40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFFFD54F), Color(0xFFFFA000))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.favorites_category),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (showCount) {
                    Text(
                        text = stringResource(R.string.categories_person_count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Mode LIST : ligne compacte d'une entrée (avatar + nom uniquement). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCompactRow(
    isFolder: Boolean,
    name: String,
    isFavorite: Boolean,
    imagePath: String?,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        // Surface ACCORDÉE à la palette (même token que l'accueil/Paramètres/mode Détails),
        // au lieu du `surfaceContainerHighest` par défaut du Card (neutre/lavande en clair).
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(if (isFolder) RoundedCornerShape(10.dp) else CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                if (imagePath != null) {
                    AsyncImage(model = imagePath, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        tint = if (isFolder) MaterialTheme.colorScheme.onPrimaryContainer
                        else Color.White,
                        modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isFavorite) {
                FavoriteStar(size = 18.dp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode sélection « Samsung Galerie » : footer contextuel, liste réordonnable, dialogue
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Footer contextuel (barre basse) du mode sélection. 4 actions dont l'activation
 * dépend strictement du nombre de catégories cochées.
 */
@Composable
internal fun SelectionFooter(
    canFavorite: Boolean,
    favoriteActive: Boolean,
    onToggleFavorite: () -> Unit,
    canCreateGroup: Boolean,
    movableGroups: List<CategoryGroup>,
    canMove: Boolean,
    onCreateGroup: () -> Unit,
    onMoveToGroup: (Long) -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    canRename: Boolean,
    onRename: () -> Unit,
    canChangeImage: Boolean,
    onChangeImage: () -> Unit,
    canMoveOut: Boolean = false,
    onMoveOut: (() -> Unit)? = null,
    canShare: Boolean = false,
    onShare: () -> Unit = {}
) {
    var folderMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    val folderEnabled = canCreateGroup || canMove || (canMoveOut && onMoveOut != null)
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        // Favori (aligne / désaligne l'état favori de la sélection).
        Box(Modifier.weight(1f).fillMaxHeight()) {
            FooterActionColumn(
                if (favoriteActive) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(R.string.categories_fav_action), canFavorite, onToggleFavorite)
        }
        // Grouper / Déplacer (menu : créer un groupe + déplacer vers un groupe + sortir).
        Box(Modifier.weight(1f).fillMaxHeight()) {
            FooterActionColumn(Icons.Default.CreateNewFolder,
                stringResource(R.string.categories_group_action),
                enabled = folderEnabled, onClick = { folderMenu = true })
            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                if (canCreateGroup) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.categories_create_group)) },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                        onClick = { folderMenu = false; onCreateGroup() })
                }
                if (canMoveOut && onMoveOut != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.categories_move_out)) },
                        leadingIcon = { Icon(Icons.Default.FolderOff, null) },
                        onClick = { folderMenu = false; onMoveOut() })
                }
                if (canMove) {
                    movableGroups.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_move_to, g.name)) },
                            leadingIcon = { Icon(Icons.Default.Folder, null) },
                            onClick = { folderMenu = false; onMoveToGroup(g.id) })
                    }
                }
            }
        }
        // Partager (texte / image / PDF) la sélection.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            FooterActionColumn(Icons.Default.Share, stringResource(R.string.share_action),
                canShare, onShare)
        }
        // Supprimer — rouge vif d'alerte (action destructive sur la sélection).
        Box(Modifier.weight(1f).fillMaxHeight()) {
            FooterActionColumn(Icons.Default.Delete, stringResource(R.string.common_delete),
                canDelete, onDelete, tintOverride = MaterialTheme.colorScheme.error)
        }
        // Overflow (Renommer / Changer l'image).
        Box(Modifier.weight(1f).fillMaxHeight()) {
            FooterActionColumn(Icons.Default.MoreVert,
                stringResource(R.string.common_more_actions),
                enabled = canRename || canChangeImage, onClick = { overflowMenu = true })
            DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.categories_rename)) },
                    enabled = canRename,
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { overflowMenu = false; onRename() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.categories_change_image)) },
                    enabled = canChangeImage,
                    leadingIcon = { Icon(Icons.Default.Image, null) },
                    onClick = { overflowMenu = false; onChangeImage() })
            }
        }
    }
}

/** Colonne d'action de footer contextuel (icône + libellé) — partagée avec l'Accueil. */
@Composable
internal fun FooterActionColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tintOverride: Color? = null
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
        tintOverride != null -> tintOverride
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/**
 * Tuile « Dossier » (mode normal, grille) : carré comme une catégorie, avec image de
 * couverture si présente, sinon icône de dossier premium. Clic = drill-down (navigue) ;
 * appui long = sélectionne ; étoile = favori.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FolderGridTile(
    group: CategoryGroup,
    memberCount: Int,
    subGroupCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    personTotal: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Compteur enrichi : sous-catégories + CUMUL des contacts du dossier.
        val counter = stringResource(R.string.categories_folder_counter, memberCount, personTotal)

        // Disposition STRICTEMENT identique à CategoryGridTile (unification v5.5) :
        // fond (photo de couverture sinon icône centrée) + calque de protection +
        // identité incrustée en bas + étoile favori en haut à droite. Seule l'icône
        // « dossier » distingue un groupe d'une catégorie.
        if (group.imagePath != null) {
            AsyncImage(model = group.imagePath, contentDescription = group.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Folder, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp).align(Alignment.Center))
        }

        // Calque de protection proportionnel (mi-tuile → bas), mis en cache.
        Box(modifier = Modifier.fillMaxSize().bottomScrim())

        // Étoile « favori » en haut à DROITE (parité avec CategoryGridTile).
        if (group.isFavorite) {
            FavoriteStar(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), size = 22.dp)
        }

        // Identité incrustée en bas : nom + compteur, blanc sur le calque (lisible
        // sur toute couverture comme sur le fond primaryContainer assombri).
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(counter,
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }

        // Sous-groupes éventuels : pastille discrète en haut à gauche (icône + nombre,
        // purement numérique — l'intitulé complet reste dans les vues Liste/Détail).
        if (subGroupCount > 0) {
            val badgeTint = if (group.imagePath != null) Color.White
            else MaterialTheme.colorScheme.onPrimaryContainer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (group.imagePath != null) Color.Black.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null,
                    tint = badgeTint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text("$subGroupCount", style = MaterialTheme.typography.labelSmall,
                    color = badgeTint)
            }
        }
    }
}

/** Ligne « Dossier » (mode normal, liste) : avatar dossier + nom + nombre. Clic = navigue. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderListRow(
    group: CategoryGroup,
    memberCount: Int,
    subGroupCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Les dossiers ne portent pas de couleur propre → accent = primaire du thème.
    val accent = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(accent))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (group.imagePath != null) {
                        AsyncImage(model = group.imagePath, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Folder, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(stringResource(R.string.categories_group_counter, memberCount, subGroupCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                // Étoile « favori » à DROITE (parité avec CategoryListRow).
                if (group.isFavorite) {
                    FavoriteStar(size = 20.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * GRILLE réordonnable (drag & drop 2D) du mode sélection, sur les entrées de premier
 * niveau mélangées (dossiers + catégories). L'affichage reste STRICTEMENT une grille
 * de carrés ([LazyVerticalGrid]).
 *
 * `data` est une copie de travail re-synchronisée hors glissement. L'élément suivi
 * est translaté en X et Y pour rester sous le doigt (déplacement multi-directionnel) ;
 * les autres carrés se décalent via `animateItem()`. Au drop, l'ordre est persisté.
 */
/** Action différée déclenchée au Drop — traitée HORS du callback tactile (anti-crash). */
private sealed interface DropAction {
    data class Merge(val ids: List<String>) : DropAction
    data class Insert(val categoryId: String, val groupId: Long) : DropAction
    data class Reorder(val refs: List<TopOrderRef>) : DropAction
}

@Composable
internal fun ReorderableTopGrid(
    entries: List<TopEntry>,
    isCategorySelected: (String) -> Boolean,
    isGroupSelected: (Long) -> Boolean,
    countOf: (String) -> Int,
    onToggleCategory: (String) -> Unit,
    onToggleGroup: (Long) -> Unit,
    onPersistOrder: (List<TopOrderRef>) -> Unit,
    onMergeRequest: (List<String>) -> Unit,
    onMoveToFolder: (String, Long) -> Unit,
    enableMerge: Boolean = true
) {
    val gridState = rememberLazyGridState()
    val data = remember { mutableStateListOf<TopEntry>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var initialOffset by remember { mutableStateOf(IntOffset.Zero) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    // Position du doigt en coordonnées GLOBALES (Window) — stable, indépendante de la
    // tuile déplacée. Base unique et fiable de la détection de collision.
    var pointerWin by remember { mutableStateOf(Offset.Zero) }
    // Coordonnées de la grille dans la fenêtre → conversion local ↔ window.
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Clé de la tuile « cible de fusion » (le doigt survole une autre tuile).
    var mergeTargetKey by remember { mutableStateOf<String?>(null) }
    // Action de Drop différée : assignée DANS le geste, TRAITÉE en dehors (anti-crash
    // InputDispatcher — on ne déclenche jamais dialogue/Room dans le callback tactile).
    var pendingDrop by remember { mutableStateOf<DropAction?>(null) }

    LaunchedEffect(entries) {
        if (draggingIndex == null) { data.clear(); data.addAll(entries) }
    }

    // Traite l'action de Drop hors du canal tactile (libéré) — ouvre le dialogue ou
    // appelle le ViewModel sans stresser l'InputDispatcher.
    LaunchedEffect(pendingDrop) {
        when (val action = pendingDrop) {
            is DropAction.Merge -> onMergeRequest(action.ids)
            is DropAction.Insert -> onMoveToFolder(action.categoryId, action.groupId)
            is DropAction.Reorder -> onPersistOrder(action.refs)
            null -> {}
        }
        if (pendingDrop != null) pendingDrop = null
    }

    fun keyOf(e: TopEntry) = when (e) {
        is TopEntry.Folder -> "g_${e.group.id}"
        is TopEntry.Single -> "c_${e.category.id}"
    }
    fun refs(): List<TopOrderRef> = data.map {
        when (it) {
            is TopEntry.Folder -> TopOrderRef.Group(it.group.id)
            is TopEntry.Single -> TopOrderRef.Cat(it.category.id)
        }
    }
    fun draggedTranslation(): Offset {
        val idx = draggingIndex ?: return Offset.Zero
        val current = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val off = current?.offset ?: IntOffset.Zero
        return Offset((initialOffset.x + delta.x) - off.x, (initialOffset.y + delta.y) - off.y)
    }
    // Coin haut-gauche d'une tuile (offset local de la grille) en coordonnées Window.
    fun itemWindowTopLeft(localOffset: IntOffset): Offset {
        val c = gridCoords ?: return Offset(localOffset.x.toFloat(), localOffset.y.toFloat())
        return c.localToWindow(Offset(localOffset.x.toFloat(), localOffset.y.toFloat()))
    }
    // Trouve la tuile (≠ index `from`) dont la BBOX GLOBALE contient le doigt.
    fun targetUnderFinger(from: Int) =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            if (info.index == from || info.index !in data.indices) return@firstOrNull false
            val tl = itemWindowTopLeft(info.offset)
            pointerWin.x >= tl.x && pointerWin.x <= tl.x + info.size.width &&
                pointerWin.y >= tl.y && pointerWin.y <= tl.y + info.size.height
        }
    // Le doigt est-il dans la zone CENTRALE (50 % des deux axes) de la tuile ?
    // → intention de fusion/insertion ; sur les bords → réordonnancement (les
    // tuiles adjacentes se décalent en temps réel via animateItem()).
    fun inMergeZone(info: androidx.compose.foundation.lazy.grid.LazyGridItemInfo): Boolean {
        val tl = itemWindowTopLeft(info.offset)
        val w = info.size.width.toFloat()
        val h = info.size.height.toFloat()
        return pointerWin.x >= tl.x + w * 0.25f && pointerWin.x <= tl.x + w * 0.75f &&
            pointerWin.y >= tl.y + h * 0.25f && pointerWin.y <= tl.y + h * 0.75f
    }
    // CORRECTIF v5.5 (superposition) : le réordonnancement n'est déclenché qu'une
    // fois le doigt passé AU-DELÀ du centre de la tuile survolée, du côté opposé
    // à l'emplacement de la tuile déplacée (projection vectorielle en coordonnées
    // Window). Avant, l'échange partait dès le bord proche (25 %) : la cible
    // « fuyait » par swap avant que le doigt n'atteigne la zone centrale, rendant
    // la fusion inaccessible (poursuite infinie). Bord proche neutre → fusion
    // atteignable ; traversée complète → réordonnancement, comme un springboard.
    fun fingerPastTargetCenter(from: Int, info: androidx.compose.foundation.lazy.grid.LazyGridItemInfo): Boolean {
        val fromInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }
            ?: return true
        val fromTl = itemWindowTopLeft(fromInfo.offset)
        val fromCenter = Offset(
            fromTl.x + fromInfo.size.width / 2f, fromTl.y + fromInfo.size.height / 2f)
        val tl = itemWindowTopLeft(info.offset)
        val center = Offset(tl.x + info.size.width / 2f, tl.y + info.size.height / 2f)
        val dirX = center.x - fromCenter.x
        val dirY = center.y - fromCenter.y
        return (pointerWin.x - center.x) * dirX + (pointerWin.y - center.y) * dirY >= 0f
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { gridCoords = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val coords = gridCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        // `offset` est local à la grille → on le passe en coordonnées Window.
                        pointerWin = coords.localToWindow(offset)
                        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            val tl = itemWindowTopLeft(info.offset)
                            pointerWin.x >= tl.x && pointerWin.x <= tl.x + info.size.width &&
                                pointerWin.y >= tl.y && pointerWin.y <= tl.y + info.size.height
                        }
                        if (hit != null) {
                            draggingIndex = hit.index
                            initialOffset = hit.offset
                            delta = Offset.Zero
                            mergeTargetKey = null
                            Log.d("JTR_DRAG", "onDragStart finger(win)=(${pointerWin.x},${pointerWin.y}) grab=${keyOf(data[hit.index])}")
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        val coords = gridCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        delta += dragAmount
                        // Position GLOBALE (Window) du doigt — stable, indépendante de la
                        // tuile en cours de déplacement.
                        pointerWin = coords.localToWindow(change.position)
                        val target = targetUnderFinger(from)
                        val draggedIsSingle = data.getOrNull(from) is TopEntry.Single
                        // ZONE CENTRALE (50 %) : intention de fusion/insertion. Le
                        // réordonnancement n'a lieu qu'APRÈS traversée du centre de la
                        // cible (fingerPastTargetCenter) — le bord proche est neutre,
                        // la cible ne « fuit » plus avant la fusion (v5.5).
                        if (enableMerge && draggedIsSingle && target != null && inMergeZone(target)) {
                            mergeTargetKey = keyOf(data[target.index])
                            Log.d("JTR_DRAG", "onDrag finger(win)=(${pointerWin.x},${pointerWin.y}) hover=${keyOf(data[target.index])} idx=${target.index} mergeTarget=$mergeTargetKey")
                        } else {
                            mergeTargetKey = null
                            if (target != null && fingerPastTargetCenter(from, target)) {
                                data.add(target.index, data.removeAt(from))
                                draggingIndex = target.index
                            }
                        }
                    },
                    onDragEnd = {
                        // ANTI-CRASH : on NE déclenche RIEN ici (ni dialogue, ni Room).
                        // On lit l'état figé, on calcule l'action, on la DÉPOSE dans
                        // `pendingDrop`, et on libère immédiatement le canal tactile. Le
                        // traitement réel est fait par le LaunchedEffect(pendingDrop).
                        try {
                            val coords = gridCoords
                            if (coords == null || !coords.isAttached) {
                                // Layout invalide au lâcher → on annule poliment, sans
                                // forcer le moindre calcul de coordonnées.
                                Log.d("JTR_DRAG", "onDragEnd ANNULÉ (layout détaché)")
                            } else {
                                val from = draggingIndex
                                val dragged = from?.let { data.getOrNull(it) } as? TopEntry.Single
                                var action: DropAction? = null
                                if (enableMerge && from != null && dragged != null) {
                                    // SEULE la cible figée pendant le survol (zone centrale)
                                    // déclenche une fusion : un drop sur les bords reste un
                                    // réordonnancement — pas de fusion accidentelle.
                                    val te: TopEntry? = mergeTargetKey?.let { key ->
                                        data.firstOrNull { keyOf(it) == key } }
                                    when (val entry = te) {
                                        is TopEntry.Single -> if (entry.category.id != dragged.category.id) {
                                            Log.d("JTR_DRAG", "onDragEnd ACTION=MERGE ${dragged.category.id} + ${entry.category.id}")
                                            action = DropAction.Merge(listOf(dragged.category.id, entry.category.id))
                                        }
                                        is TopEntry.Folder -> {
                                            Log.d("JTR_DRAG", "onDragEnd ACTION=INSERT ${dragged.category.id} -> group ${entry.group.id}")
                                            action = DropAction.Insert(dragged.category.id, entry.group.id)
                                        }
                                        else -> {}
                                    }
                                }
                                if (action == null) {
                                    Log.d("JTR_DRAG", "onDragEnd ACTION=REORDER (aucune cible)")
                                    action = DropAction.Reorder(refs())
                                }
                                pendingDrop = action
                            }
                        } catch (e: Exception) {
                            Log.e("JTR_ERROR", "Crash évité dans onDragEnd", e)
                        } finally {
                            // Réinitialisation défensive de l'état du geste.
                            draggingIndex = null
                            mergeTargetKey = null
                        }
                    },
                    onDragCancel = { draggingIndex = null; mergeTargetKey = null }
                )
            },
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(data, key = { _, e -> keyOf(e) }) { index, entry ->
            val isDragged = index == draggingIndex
            val mod = if (isDragged) {
                Modifier.zIndex(1f).graphicsLayer {
                    val t = draggedTranslation(); translationX = t.x; translationY = t.y
                }
            } else {
                Modifier.animateItem()
            }
            val mergeHighlight = mergeTargetKey == keyOf(entry)
            when (entry) {
                is TopEntry.Folder -> TopSelectionTile(
                    isFolder = true,
                    name = entry.group.name,
                    isFavorite = entry.group.isFavorite,
                    // Compteur enrichi : sous-catégories + cumul des contacts.
                    subtitle = stringResource(R.string.categories_folder_counter,
                        entry.members.size, entry.members.sumOf { countOf(it.id) }),
                    imagePath = entry.group.imagePath,
                    accent = MaterialTheme.colorScheme.primaryContainer,
                    selected = isGroupSelected(entry.group.id),
                    mergeHighlight = mergeHighlight,
                    onClick = { onToggleGroup(entry.group.id) },
                    modifier = mod
                )
                is TopEntry.Single -> {
                    val accent = rememberCategoryColor(entry.category.color)
                    val c = countOf(entry.category.id)
                    TopSelectionTile(
                        isFolder = false,
                        name = entry.category.name,
                        isFavorite = entry.category.isFavorite,
                        subtitle = stringResource(R.string.categories_person_count, c),
                        imagePath = entry.category.imagePath,
                        accent = accent,
                        selected = isCategorySelected(entry.category.id),
                        mergeHighlight = mergeHighlight,
                        onClick = { onToggleCategory(entry.category.id) },
                        modifier = mod
                    )
                }
            }
        }
    }
}

/**
 * LISTE réordonnable (drag & drop vertical) du mode sélection : quand le mode Liste
 * est actif, le clic long NE bascule PLUS en grille — l'affichage reste une liste.
 *
 * Mêmes règles que [ReorderableTopGrid] (copie de travail `data`, action de Drop
 * différée anti-crash, persistance via [TopOrderRef]), adaptées à l'axe vertical :
 * une catégorie glissée sur le CŒUR d'une autre ligne (bande centrale 50 %) propose
 * la fusion / l'insertion dans le dossier ; sur les bords, elle se réordonne.
 */
@Composable
internal fun ReorderableTopList(
    entries: List<TopEntry>,
    isCategorySelected: (String) -> Boolean,
    isGroupSelected: (Long) -> Boolean,
    countOf: (String) -> Int,
    onToggleCategory: (String) -> Unit,
    onToggleGroup: (Long) -> Unit,
    onPersistOrder: (List<TopOrderRef>) -> Unit,
    onMergeRequest: (List<String>) -> Unit,
    onMoveToFolder: (String, Long) -> Unit,
    enableMerge: Boolean = true
) {
    val listState = rememberLazyListState()
    val data = remember { mutableStateListOf<TopEntry>() }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var initialOffsetY by remember { mutableIntStateOf(0) }
    var deltaY by remember { mutableFloatStateOf(0f) }
    // Ordonnée du doigt en coordonnées GLOBALES (Window) — base de la détection.
    var pointerWinY by remember { mutableFloatStateOf(0f) }
    var listCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var mergeTargetKey by remember { mutableStateOf<String?>(null) }
    // Action de Drop différée : assignée DANS le geste, traitée HORS du callback
    // tactile (anti-crash InputDispatcher — voir ReorderableTopGrid).
    var pendingDrop by remember { mutableStateOf<DropAction?>(null) }

    LaunchedEffect(entries) {
        if (draggingIndex == null) { data.clear(); data.addAll(entries) }
    }
    LaunchedEffect(pendingDrop) {
        when (val action = pendingDrop) {
            is DropAction.Merge -> onMergeRequest(action.ids)
            is DropAction.Insert -> onMoveToFolder(action.categoryId, action.groupId)
            is DropAction.Reorder -> onPersistOrder(action.refs)
            null -> {}
        }
        if (pendingDrop != null) pendingDrop = null
    }

    fun keyOf(e: TopEntry) = when (e) {
        is TopEntry.Folder -> "g_${e.group.id}"
        is TopEntry.Single -> "c_${e.category.id}"
    }
    fun refs(): List<TopOrderRef> = data.map {
        when (it) {
            is TopEntry.Folder -> TopOrderRef.Group(it.group.id)
            is TopEntry.Single -> TopOrderRef.Cat(it.category.id)
        }
    }
    fun itemWindowTop(localTop: Int): Float {
        val c = listCoords ?: return localTop.toFloat()
        return c.localToWindow(Offset(0f, localTop.toFloat())).y
    }
    fun draggedTranslationY(): Float {
        val idx = draggingIndex ?: return 0f
        val current = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }
        val off = current?.offset ?: 0
        return (initialOffsetY + deltaY) - off
    }
    // Ligne (≠ index `from`) dont la bande verticale GLOBALE contient le doigt.
    fun targetUnderFinger(from: Int) =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            if (info.index == from || info.index !in data.indices) return@firstOrNull false
            val top = itemWindowTop(info.offset)
            pointerWinY >= top && pointerWinY <= top + info.size
        }
    // Bande CENTRALE (50 %) de la ligne survolée → intention de fusion/insertion ;
    // bords haut/bas → intention de réordonnancement.
    fun inMergeBand(info: androidx.compose.foundation.lazy.LazyListItemInfo): Boolean {
        val top = itemWindowTop(info.offset)
        return pointerWinY >= top + info.size * 0.25f && pointerWinY <= top + info.size * 0.75f
    }
    // CORRECTIF v5.5 (superposition) — pendant vertical de la règle de la grille :
    // réordonnancement uniquement quand le doigt a TRAVERSÉ le centre de la ligne
    // survolée (côté opposé à la ligne déplacée). Le bord proche reste neutre, la
    // bande centrale de fusion est donc toujours atteignable.
    fun fingerPastTargetCenter(from: Int, info: androidx.compose.foundation.lazy.LazyListItemInfo): Boolean {
        val fromInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }
            ?: return true
        val fromCenter = itemWindowTop(fromInfo.offset) + fromInfo.size / 2f
        val center = itemWindowTop(info.offset) + info.size / 2f
        val dir = center - fromCenter
        return (pointerWinY - center) * dir >= 0f
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { listCoords = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val coords = listCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        pointerWinY = coords.localToWindow(offset).y
                        val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            val top = itemWindowTop(info.offset)
                            pointerWinY >= top && pointerWinY <= top + info.size
                        }
                        if (hit != null) {
                            draggingIndex = hit.index
                            initialOffsetY = hit.offset
                            deltaY = 0f
                            mergeTargetKey = null
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        val coords = listCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        deltaY += dragAmount.y
                        pointerWinY = coords.localToWindow(change.position).y
                        val target = targetUnderFinger(from)
                        val draggedIsSingle = data.getOrNull(from) is TopEntry.Single
                        if (enableMerge && draggedIsSingle && target != null && inMergeBand(target)) {
                            mergeTargetKey = keyOf(data[target.index])
                        } else {
                            mergeTargetKey = null
                            // Réordonnancement seulement après traversée du centre (v5.5).
                            if (target != null && fingerPastTargetCenter(from, target)) {
                                data.add(target.index, data.removeAt(from))
                                draggingIndex = target.index
                            }
                        }
                    },
                    onDragEnd = {
                        // ANTI-CRASH : aucun dialogue/Room ici — voir ReorderableTopGrid.
                        // Seule la cible FIGÉE pendant le survol (bande centrale) déclenche
                        // une fusion ; un drop sur les bords reste un réordonnancement.
                        try {
                            val coords = listCoords
                            if (coords != null && coords.isAttached) {
                                val from = draggingIndex
                                val dragged = from?.let { data.getOrNull(it) } as? TopEntry.Single
                                var action: DropAction? = null
                                if (enableMerge && dragged != null) {
                                    val te = mergeTargetKey?.let { key ->
                                        data.firstOrNull { keyOf(it) == key }
                                    }
                                    when (te) {
                                        is TopEntry.Single -> if (te.category.id != dragged.category.id) {
                                            action = DropAction.Merge(
                                                listOf(dragged.category.id, te.category.id))
                                        }
                                        is TopEntry.Folder ->
                                            action = DropAction.Insert(dragged.category.id, te.group.id)
                                        else -> {}
                                    }
                                }
                                if (action == null) action = DropAction.Reorder(refs())
                                pendingDrop = action
                            }
                        } catch (e: Exception) {
                            Log.e("JTR_ERROR", "Crash évité dans onDragEnd (liste)", e)
                        } finally {
                            draggingIndex = null
                            mergeTargetKey = null
                        }
                    },
                    onDragCancel = { draggingIndex = null; mergeTargetKey = null }
                )
            },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(data, key = { _, e -> keyOf(e) }) { index, entry ->
            val isDragged = index == draggingIndex
            val mod = if (isDragged) {
                Modifier.zIndex(1f).graphicsLayer { translationY = draggedTranslationY() }
            } else {
                Modifier.animateItem()
            }
            val mergeHighlight = mergeTargetKey == keyOf(entry)
            when (entry) {
                is TopEntry.Folder -> TopSelectionRow(
                    isFolder = true,
                    name = entry.group.name,
                    isFavorite = entry.group.isFavorite,
                    subtitle = stringResource(R.string.categories_group_counter,
                        entry.members.size, entry.subGroupCount),
                    imagePath = entry.group.imagePath,
                    accent = MaterialTheme.colorScheme.primaryContainer,
                    selected = isGroupSelected(entry.group.id),
                    mergeHighlight = mergeHighlight,
                    onClick = { onToggleGroup(entry.group.id) },
                    modifier = mod
                )
                is TopEntry.Single -> {
                    val accent = rememberCategoryColor(entry.category.color)
                    TopSelectionRow(
                        isFolder = false,
                        name = entry.category.name,
                        isFavorite = entry.category.isFavorite,
                        subtitle = stringResource(R.string.categories_person_count,
                            countOf(entry.category.id)),
                        imagePath = entry.category.imagePath,
                        accent = accent,
                        selected = isCategorySelected(entry.category.id),
                        mergeHighlight = mergeHighlight,
                        onClick = { onToggleCategory(entry.category.id) },
                        modifier = mod
                    )
                }
            }
        }
    }
}

/** Ligne du mode sélection (dossier ou catégorie) : case à cocher + avatar + nom. */
@Composable
private fun TopSelectionRow(
    isFolder: Boolean,
    name: String,
    isFavorite: Boolean,
    subtitle: String?,
    imagePath: String?,
    accent: Color,
    selected: Boolean,
    mergeHighlight: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (mergeHighlight) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            JtrSelectionCheck(selected = selected)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (isFolder) RoundedCornerShape(12.dp) else CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                if (imagePath != null) {
                    AsyncImage(model = imagePath, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        tint = if (isFolder) MaterialTheme.colorScheme.onPrimaryContainer
                        else Color.White,
                        modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (mergeHighlight) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp).size(22.dp))
            }
            if (isFavorite) {
                FavoriteStar(size = 20.dp)
            }
        }
    }
}

/** Carré du mode sélection (dossier ou catégorie) : style Galerie + case à cocher. */
@Composable
internal fun TopSelectionTile(
    isFolder: Boolean,
    name: String,
    isFavorite: Boolean,
    subtitle: String?,
    imagePath: String?,
    accent: Color,
    selected: Boolean,
    mergeHighlight: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(accent)
            .then(
                if (mergeHighlight) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        if (imagePath != null) {
            AsyncImage(model = imagePath, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Folder, contentDescription = null,
                tint = if (isFolder) MaterialTheme.colorScheme.onPrimaryContainer
                else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp).align(Alignment.Center))
        }

        // Calque de protection proportionnel (mi-tuile → bas), mis en cache.
        Box(modifier = Modifier.fillMaxSize().bottomScrim())

        // Voile bleuté quand sélectionné.
        if (selected) {
            Box(modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)))
        }

        // Indice de fusion : voile + icône « créer un dossier » au centre.
        if (mergeHighlight) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        // Case à cocher en surimpression (haut-gauche) — disque protecteur
        // contrastant : lisible sur image claire comme sur accent gris (v5.5).
        JtrSelectionCheck(
            selected = selected,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
        )

        if (isFavorite) {
            FavoriteStar(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp), size = 22.dp)
        }

        // Identité incrustée bas-gauche : la case à cocher vit en HAUT-gauche, donc
        // aucun chevauchement ; titre sur UNE ligne pour rester net sous le halo
        // de fusion qui englobe toute la tuile.
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

/** Dialogue générique de saisie d'une seule chaîne (créer groupe / renommer). */
@Composable
internal fun TextPromptDialog(
    title: String,
    hint: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(hint) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** Édition d'une catégorie existante — délègue au formulaire unifié. */
@Composable
fun EditCategoryDialog(
    category: Category,
    onConfirm: (name: String, color: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) = CategoryFormDialog(
    title = stringResource(R.string.categories_edit_dialog_title),
    initialName = category.name,
    initialColor = category.color,
    initialImagePath = category.imagePath,
    confirmLabel = stringResource(R.string.common_save),
    onConfirm = onConfirm,
    onDismiss = onDismiss
)

/**
 * Création d'une catégorie — MÊME formulaire que l'édition (v5.3) : l'image de
 * couverture se choisit et se recadre DÈS la création, sans étape de retouche.
 */
@Composable
fun AddCategoryDialog(
    onConfirm: (name: String, color: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) = CategoryFormDialog(
    title = stringResource(R.string.categories_new_dialog_title),
    initialName = "",
    initialColor = "#2E86C1",
    initialImagePath = null,
    confirmLabel = stringResource(R.string.common_create),
    onConfirm = onConfirm,
    onDismiss = onDismiss
)

/** Formulaire de catégorie UNIFIÉ (création ET édition) : nom + couleur + image. */
@OptIn(ExperimentalLayoutApi::class) // FlowRow (pastilles de couleur, cibles tactiles 48 dp)
@Composable
private fun CategoryFormDialog(
    title: String,
    initialName: String,
    initialColor: String,
    initialImagePath: String?,
    confirmLabel: String,
    onConfirm: (name: String, color: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialName) }
    val presetColors = listOf("#2E86C1", "#E74C3C", "#27AE60", "#F39C12", "#8E44AD", "#16A085")
    var selectedColor by remember { mutableStateOf(initialColor) }
    var imagePath by remember { mutableStateOf(initialImagePath) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    // Galerie IN-APP par ALBUMS (v5.5).
    val photoPicker = rememberGalleryImagePicker { uri -> pendingCropUri = uri }

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = CropShape.RECTANGLE,
            onCropComplete = { croppedUri ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        copyCategoryPhotoToStorage(context, croppedUri)
                    }
                    if (path != null) imagePath = path
                }
                pendingCropUri = null
            },
            onDismiss = { pendingCropUri = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(rememberCategoryColor(selectedColor))
                        .clickable { photoPicker() },
                    contentAlignment = Alignment.Center
                ) {
                    if (imagePath != null) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = stringResource(R.string.categories_image_cd),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.common_photo),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White)
                        }
                    }
                }

                if (imagePath != null) {
                    TextButton(onClick = { imagePath = null }) {
                        Text(stringResource(R.string.categories_remove_photo),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.common_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.common_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                // FlowRow : se replie sur une 2e ligne si l'espace manque (écrans étroits /
                // grande police) au lieu de rogner la dernière pastille.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    presetColors.forEach { color ->
                        // Cible tactile ≥ 48 dp (a11y) : pastille VISIBLE de 36 dp centrée
                        // dans une zone CLIQUABLE de 48 dp (le visuel est inchangé).
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(color))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == color) {
                                    Icon(Icons.Default.Check, contentDescription = null,
                                        tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor, imagePath) },
                enabled = name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

internal fun copyCategoryPhotoToStorage(context: android.content.Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "photos").also { it.mkdirs() }
    val dest = File(dir, "category_${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
    dest.absolutePath
} catch (_: Exception) { null }
