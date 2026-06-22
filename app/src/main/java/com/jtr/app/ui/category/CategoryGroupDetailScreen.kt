package com.jtr.app.ui.category

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.components.rememberGalleryImagePicker
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.ImageCropDialog
import com.jtr.app.ui.share.CategoriesSharePreview
import com.jtr.app.ui.share.ShareCategoryItem
import com.jtr.app.ui.share.ShareFormatSheet
import com.jtr.app.ui.share.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran « intérieur d'un dossier » (drill-down RÉCURSIF). Affiche le contenu mixte
 * du groupe : sous-groupes (folders) + sous-catégories. Les mêmes gestes qu'à la
 * racine s'appliquent : appui long → sélection + footer ; drag & drop ; fusion au
 * centre (catégorie sur catégorie → SOUS-GROUPE ; catégorie sur sous-groupe → insertion).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryGroupDetailScreen(
    onNavigateBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onGroupClick: (Long) -> Unit = {},
    viewModel: CategoryGroupDetailViewModel = viewModel()
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val subGroups by viewModel.subGroups.collectAsStateWithLifecycle()
    val topEntries by viewModel.topEntries.collectAsStateWithLifecycle()
    val counts by viewModel.personCountByCategory.collectAsStateWithLifecycle()
    val candidates by viewModel.candidateCategories.collectAsStateWithLifecycle()
    val otherGroups by viewModel.otherGroups.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    var addMenu by remember { mutableStateOf(false) }
    // Mode recherche de la TopAppBar (état d'UI local ; la query vient du ViewModel).
    var searchActive by remember { mutableStateOf(false) }
    var showAddExisting by remember { mutableStateOf(false) }
    var showGroupTrashConfirm by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showDissolveConfirm by remember { mutableStateOf(false) }

    // ── Mode sélection mixte (sous-catégories + sous-groupes) ─────────────────
    var isSelectionActive by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectedGroupIds = remember { mutableStateListOf<Long>() }
    var showBulkDelete by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Category?>(null) }
    var renameGroupTarget by remember { mutableStateOf<CategoryGroup?>(null) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var pendingMergeIds by remember { mutableStateOf<List<String>?>(null) }
    var groupImageTarget by remember { mutableStateOf<CategoryGroup?>(null) }
    var pendingGroupCropUri by remember { mutableStateOf<Uri?>(null) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Galerie IN-APP par ALBUMS (v5.5).
    val groupPhotoPicker = rememberGalleryImagePicker { uri -> pendingGroupCropUri = uri }

    LaunchedEffect(members, subGroups) {
        selectedIds.retainAll(members.map { it.id }.toSet())
        selectedGroupIds.retainAll(subGroups.map { it.id }.toSet())
    }
    // Sortie d'écran → réinitialisation du filtre (aucune query fantôme au retour).
    DisposableEffect(Unit) { onDispose { viewModel.clearSearch() } }

    // Partage contextuel : instantané de la sélection (sous-catégories + sous-groupes).
    var shareItems by remember { mutableStateOf<List<ShareCategoryItem>?>(null) }
    shareItems?.let { items ->
        ShareFormatSheet(
            onDismiss = { shareItems = null },
            buildText = { ShareUtils.buildCategoriesShareText(ctx, items) },
            writePdf = { ShareUtils.writeCategoriesPdf(ctx, items) },
            preview = { CategoriesSharePreview(items) }
        )
    }
    fun exitSelection() {
        isSelectionActive = false; selectedIds.clear(); selectedGroupIds.clear()
    }

    // Bouton Retour en mode sélection : vide la sélection et reste sur l'écran ;
    // inactif sinon → le retour navigue normalement vers le dossier parent.
    BackHandler(enabled = isSelectionActive) { exitSelection() }
    fun toggleCategory(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }
    fun toggleGroup(id: Long) {
        if (selectedGroupIds.contains(id)) selectedGroupIds.remove(id) else selectedGroupIds.add(id)
    }
    fun startSelectionCategory(id: String) {
        isSelectionActive = true; if (id !in selectedIds) selectedIds.add(id)
    }
    fun startSelectionGroup(id: Long) {
        isSelectionActive = true; if (id !in selectedGroupIds) selectedGroupIds.add(id)
    }
    fun selectAll() {
        topEntries.forEach {
            when (it) {
                is TopEntry.Folder -> if (it.group.id !in selectedGroupIds) selectedGroupIds.add(it.group.id)
                is TopEntry.Single -> if (it.category.id !in selectedIds) selectedIds.add(it.category.id)
            }
        }
        if (topEntries.isNotEmpty()) isSelectionActive = true
    }

    val totalSelected = selectedIds.size + selectedGroupIds.size
    val selectedCategories = members.filter { it.id in selectedIds }
    val selectedGroups = subGroups.filter { it.id in selectedGroupIds }
    val allFavorite = totalSelected > 0 &&
        selectedCategories.all { it.isFavorite } && selectedGroups.all { it.isFavorite }

    // ── Dialogues ─────────────────────────────────────────────────────────────
    if (showAddExisting) {
        AddExistingCategoriesDialog(
            candidates = candidates,
            onConfirm = { ids -> viewModel.addExisting(ids); showAddExisting = false },
            onDismiss = { showAddExisting = false }
        )
    }
    if (showCreate) {
        AddCategoryDialog(
            onConfirm = { name, color, imagePath ->
                viewModel.createInGroup(name, color, imagePath); showCreate = false
            },
            onDismiss = { showCreate = false }
        )
    }
    // Fusion par geste → nommer le SOUS-GROUPE créé.
    pendingMergeIds?.let { ids ->
        TextPromptDialog(
            title = stringResource(R.string.categories_create_group),
            hint = stringResource(R.string.categories_group_name_hint),
            initial = "",
            confirmLabel = stringResource(R.string.common_create),
            onConfirm = { name -> viewModel.createSubGroup(name, ids); pendingMergeIds = null; exitSelection() },
            onDismiss = { pendingMergeIds = null }
        )
    }
    renameTarget?.let { target ->
        TextPromptDialog(
            title = stringResource(R.string.categories_rename),
            hint = stringResource(R.string.common_name_label),
            initial = target.name,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name -> viewModel.rename(target, name); renameTarget = null; exitSelection() },
            onDismiss = { renameTarget = null }
        )
    }
    renameGroupTarget?.let { target ->
        TextPromptDialog(
            title = stringResource(R.string.categories_rename),
            hint = stringResource(R.string.categories_group_name_hint),
            initial = target.name,
            confirmLabel = stringResource(R.string.common_save),
            onConfirm = { name -> viewModel.renameGroup(target, name); renameGroupTarget = null; exitSelection() },
            onDismiss = { renameGroupTarget = null }
        )
    }
    editTarget?.let { target ->
        EditCategoryDialog(
            category = target,
            onConfirm = { name, color, imagePath ->
                viewModel.updateCategory(target.copy(name = name, color = color, imagePath = imagePath))
                editTarget = null; exitSelection()
            },
            onDismiss = { editTarget = null }
        )
    }
    pendingGroupCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = CropShape.RECTANGLE,
            onCropComplete = { cropped ->
                groupImageTarget?.let { target ->
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { copyCategoryPhotoToStorage(ctx, cropped) }
                        if (path != null) viewModel.setGroupImage(target, path)
                    }
                }
                pendingGroupCropUri = null; groupImageTarget = null; exitSelection()
            },
            onDismiss = { pendingGroupCropUri = null; groupImageTarget = null }
        )
    }
    if (showBulkDelete) {
        val singleGroup = selectedGroups.singleOrNull()?.takeIf { selectedIds.isEmpty() }
        val singleGroupMembers = singleGroup?.let { g -> members.count { it.parentGroupId == g.id } } ?: 0
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
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
                        stringResource(R.string.categories_delete_group_text, singleGroup.name, singleGroupMembers)
                    else stringResource(R.string.categories_delete_selected_text)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelection(selectedIds.toList(), selectedGroups, members)
                        showBulkDelete = false; exitSelection()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    // « Mettre à la corbeille » le dossier : catégories + contacts en corbeille,
    // sous-groupes remontés d'un niveau (confirmation explicite avant cascade).
    if (showGroupTrashConfirm) {
        val groupName = group?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { showGroupTrashConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.categories_delete_group_title, groupName)) },
            text = { Text(stringResource(R.string.categories_delete_group_text,
                groupName, members.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGroupTrashConfirm = false
                        viewModel.deleteGroupToTrash()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = { showGroupTrashConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDissolveConfirm) {
        AlertDialog(
            onDismissRequest = { showDissolveConfirm = false },
            icon = { Icon(Icons.Default.FolderOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.categories_dissolve_group)) },
            text = { Text(stringResource(R.string.categories_dissolve_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.dissolve(); showDissolveConfirm = false; onNavigateBack() }) {
                    Text(stringResource(R.string.categories_dissolve_group))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionActive) {
                TopAppBar(
                    title = {
                        Text(
                            if (totalSelected == 0) stringResource(R.string.categories_selection_none)
                            else pluralStringResource(R.plurals.categories_selection_count, totalSelected, totalSelected)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
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
            } else {
                // Barre harmonisée : retour + loupe + « + » + menu 3 points (Tri /
                // Affichage, puis actions propres au dossier : « Défaire le groupe »).
                JtrSearchableTopAppBar(
                    title = group?.name ?: stringResource(R.string.categories_title),
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it },
                    searchPlaceholder = stringResource(R.string.home_search_placeholder),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { addMenu = true }) {
                                Icon(Icons.Default.Add,
                                    contentDescription = stringResource(R.string.categories_add_to_group_title))
                            }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.categories_add_existing)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                                    onClick = { addMenu = false; showAddExisting = true })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.categories_create_here)) },
                                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                    onClick = { addMenu = false; showCreate = true })
                            }
                        }
                        JtrOverflowMenu(
                            sortCriteria = categorySortCriteria(sortOrder) { viewModel.setSortOrder(it) },
                            viewMode = viewMode,
                            onViewModeChange = { viewModel.setViewMode(it) }
                        ) { dismiss ->
                            // Actions sur le dossier lui-même (v5.3) : Modifier
                            // (nom / image) et mise à la corbeille.
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.categories_rename)) },
                                leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                onClick = {
                                    dismiss()
                                    group?.let { renameGroupTarget = it }
                                })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.categories_change_image)) },
                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                onClick = {
                                    dismiss()
                                    group?.let {
                                        groupImageTarget = it
                                        groupPhotoPicker()
                                    }
                                })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.categories_dissolve_group)) },
                                leadingIcon = { Icon(Icons.Default.FolderOff, null,
                                    tint = MaterialTheme.colorScheme.error) },
                                onClick = { dismiss(); showDissolveConfirm = true })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_move_to_trash)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null,
                                    tint = MaterialTheme.colorScheme.error) },
                                onClick = { dismiss(); showGroupTrashConfirm = true })
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionActive,
                enter = JtrBottomBarTransitions.enter,
                exit = JtrBottomBarTransitions.exit
            ) {
                val movableGroups = otherGroups.filter { it.id !in selectedGroupIds }
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
                    onCreateGroup = {
                        // Crée un SOUS-GROUPE depuis les catégories cochées.
                        if (selectedIds.size >= 2) pendingMergeIds = selectedIds.toList()
                    },
                    onMoveToGroup = { tid ->
                        viewModel.relocateSelection(selectedIds.toList(), selectedGroups, tid)
                        exitSelection()
                    },
                    canDelete = totalSelected >= 1,
                    onDelete = { showBulkDelete = true },
                    canShare = totalSelected >= 1,
                    onShare = {
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
                                    personCount = counts[c.id] ?: 0
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
                        if (cat != null) editTarget = cat
                        else selectedGroups.singleOrNull()?.let { g ->
                            groupImageTarget = g
                            groupPhotoPicker()
                        }
                    },
                    canMoveOut = totalSelected >= 1,
                    onMoveOut = {
                        // Sortir d'un niveau : vers le parent du groupe courant (racine si null).
                        viewModel.relocateSelection(selectedIds.toList(), selectedGroups, group?.parentGroupId)
                        exitSelection()
                    }
                )
            }
        }
    ) { padding ->
        if (topEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.categories_group_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (isSelectionActive) {
            // Mode sélection : le mode d'affichage courant est CONSERVÉ. Grille →
            // drag & drop 2D + fusion (sous-groupes) ; Liste / Détail → vertical.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (viewMode) {
                    JtrViewMode.GRID -> ReorderableTopGrid(
                        entries = topEntries,
                        isCategorySelected = { it in selectedIds },
                        isGroupSelected = { it in selectedGroupIds },
                        countOf = { counts[it] ?: 0 },
                        onToggleCategory = { toggleCategory(it) },
                        onToggleGroup = { toggleGroup(it) },
                        onPersistOrder = { viewModel.persistTopOrder(it) },
                        onMergeRequest = { ids -> pendingMergeIds = ids },
                        onMoveToFolder = { catId, gid ->
                            viewModel.moveCategoryToSubGroup(catId, gid); exitSelection()
                        },
                        enableMerge = true
                    )
                    else -> ReorderableTopList(
                        entries = topEntries,
                        isCategorySelected = { it in selectedIds },
                        isGroupSelected = { it in selectedGroupIds },
                        countOf = { counts[it] ?: 0 },
                        onToggleCategory = { toggleCategory(it) },
                        onToggleGroup = { toggleGroup(it) },
                        onPersistOrder = { viewModel.persistTopOrder(it) },
                        onMergeRequest = { ids -> pendingMergeIds = ids },
                        onMoveToFolder = { catId, gid ->
                            viewModel.moveCategoryToSubGroup(catId, gid); exitSelection()
                        },
                        enableMerge = true
                    )
                }
            }
        } else {
            // Mode navigation : contenu mixte commutable LIST / GRID / DETAIL.
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                TopEntriesBrowser(
                    entries = topEntries,
                    viewMode = viewMode,
                    countOf = { counts[it] ?: 0 },
                    onCategoryClick = onCategoryClick,
                    onGroupClick = onGroupClick,
                    onCategoryLongClick = { startSelectionCategory(it) },
                    onGroupLongClick = { startSelectionGroup(it) }
                )
            }
        }
    }
}

/** Dialogue de sélection multiple de catégories existantes à rattacher au groupe. */
@Composable
private fun AddExistingCategoriesDialog(
    candidates: List<Category>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_add_to_group_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.categories_no_candidates))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.id }) { category ->
                        val checked = category.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (checked) selected.remove(category.id) else selected.add(category.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                if (checked) selected.remove(category.id) else selected.add(category.id)
                            })
                            val accent = rememberCategoryColor(category.color)
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (category.imagePath != null) {
                                    AsyncImage(model = category.imagePath, contentDescription = null,
                                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Default.Folder, null, tint = Color.White,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(category.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
