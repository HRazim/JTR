package com.jtr.app.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.components.JtrAlertDialog
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.components.SCROLL_TO_TOP_THRESHOLD
import com.jtr.app.ui.components.ScrollToTopFab
import com.jtr.app.ui.components.SelectAllToggle
import kotlinx.coroutines.launch

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
    // v7.1.87 — ouverture de la page de recherche dédiée, SCOPÉE à ce dossier.
    onNavigateToScopedSearch: (Long) -> Unit = {},
    viewModel: CategoryGroupDetailViewModel = viewModel()
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val subGroups by viewModel.subGroups.collectAsStateWithLifecycle()
    val topEntries by viewModel.topEntries.collectAsStateWithLifecycle()
    val counts by viewModel.personCountByCategory.collectAsStateWithLifecycle()
    val candidates by viewModel.candidateCategories.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroups.collectAsStateWithLifecycle()
    val otherGroups by viewModel.otherGroups.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    var addMenu by remember { mutableStateOf(false) }
    var showAddExisting by remember { mutableStateOf(false) }
    var showGroupTrashConfirm by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showDissolveConfirm by remember { mutableStateOf(false) }

    // ── Mode sélection mixte (sous-catégories + sous-groupes) ─────────────────
    var isSelectionActive by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectedGroupIds = remember { mutableStateListOf<Long>() }
    // Fix v7.1.81 — états de défilement HOISTÉS au niveau de l'écran et PARTAGÉS entre le
    // navigateur normal et le mode sélection : l'appui long n'emporte plus la liste en haut.
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // v7.1.86 — FAB « remonter en haut » : MÊME composant et MÊME seuil que l'Accueil
    // (v7.1.72), branché sur les états ci-dessus — donc valable en navigation comme en
    // sélection, et dans les 3 modes (GRID a son état, LIST/DETAIL partagent l'autre).
    val showScrollToTop by remember(viewMode) {
        derivedStateOf {
            val index = if (viewMode == JtrViewMode.GRID) gridState.firstVisibleItemIndex
            else listState.firstVisibleItemIndex
            index > SCROLL_TO_TOP_THRESHOLD
        }
    }
    var showBulkDelete by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    // v7.1.127 — dossier en cours d'édition dans la feuille partagée (footer).
    var editGroupTarget by remember { mutableStateOf<CategoryGroup?>(null) }
    var pendingMergeIds by remember { mutableStateOf<List<String>?>(null) }
    // v7.1.82 — création EXPLICITE d'un sous-dossier depuis le footer : état DISTINCT de
    // [pendingMergeIds] pour que le geste de FUSION garde son dialogue historique.
    var pendingCreateSubGroupIds by remember { mutableStateOf<List<String>?>(null) }
    // v7.1.83 — sélecteur de dossier CIBLE (« Déplacer vers un groupe… »).
    var showMoveToGroupPicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(members, subGroups) {
        selectedIds.retainAll(members.map { it.id }.toSet())
        selectedGroupIds.retainAll(subGroups.map { it.id }.toSet())
    }
    // Sortie d'écran → réinitialisation du filtre (aucune query fantôme au retour).
    DisposableEffect(Unit) { onDispose { viewModel.clearSearch() } }

    fun exitSelection() {
        isSelectionActive = false; selectedIds.clear(); selectedGroupIds.clear()
    }

    // v7.1.98 — la recherche INLINE revient, mais UNIQUEMENT en mode sélection : hors
    // sélection la loupe garde la page scopée de v7.1.87 (qui cherche des CONTACTS) ;
    // en sélection elle filtre les entrées affichées, celles qu'on est en train de cocher.
    var searchActive by remember { mutableStateOf(false) }

    // v7.1.98 — priorité du bouton Retour, identique sur les 5 écrans de sélection :
    // 1) la recherche inline est ouverte → on la ferme, la sélection SURVIT ;
    // 2) sinon on quitte la sélection ; 3) sinon la navigation reprend normalement.
    BackHandler(enabled = isSelectionActive) {
        if (searchActive) {
            searchActive = false
            viewModel.setSearchQuery("")
        } else {
            exitSelection()
        }
    }
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

    /** v7.1.98 — vrai quand TOUTES les entrées du dossier sont cochées (bascule partagée). */
    val allTopEntriesSelected = topEntries.isNotEmpty() && topEntries.all { entry ->
        when (entry) {
            is TopEntry.Folder -> entry.group.id in selectedGroupIds
            is TopEntry.Single -> entry.category.id in selectedIds
        }
    }

    /**
     * Décoche les entrées AFFICHÉES, et elles seules — pendant exact de [selectAll].
     * On RESTE en mode sélection : seul Annuler / Retour en sort.
     */
    fun deselectVisible() {
        topEntries.forEach { entry ->
            when (entry) {
                is TopEntry.Folder -> selectedGroupIds.remove(entry.group.id)
                is TopEntry.Single -> selectedIds.remove(entry.category.id)
            }
        }
    }

    val totalSelected = selectedIds.size + selectedGroupIds.size
    val selectedCategories = members.filter { it.id in selectedIds }
    val selectedGroups = subGroups.filter { it.id in selectedGroupIds }
    val allFavorite = totalSelected > 0 &&
        selectedCategories.all { it.isFavorite } && selectedGroups.all { it.isFavorite }

    // ── Dialogues ─────────────────────────────────────────────────────────────
    if (showAddExisting) {
        // v7.1.80 — sélection MULTIPLE moderne (langage v7.1.77 : vignettes, dossiers
        // dépliables, recherche) au lieu des cases + disques pleins ; les dossiers sans
        // candidate (dont CE dossier) sont élagués par le composant.
        CategoryPickerSheet(
            title = stringResource(R.string.categories_add_to_group_title),
            categories = candidates,
            groups = allGroups,
            multiSelect = true,
            confirmLabel = stringResource(R.string.common_add),
            onConfirmMulti = { ids -> viewModel.addExisting(ids); showAddExisting = false },
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
    if (showMoveToGroupPicker) {
        // v7.1.83 — sélecteur de dossiers MODERNE. Cibles exclues : le dossier COURANT
        // (on y est déjà) et les sous-dossiers cochés (on ne les déplace pas en eux-mêmes).
        CategoryPickerSheet(
            title = stringResource(R.string.categories_move_to_group),
            categories = members,
            groups = allGroups,
            folderTargets = true,
            excludeGroupIds = selectedGroupIds.toSet() + listOfNotNull(group?.id),
            onPickFolder = { targetId ->
                viewModel.relocateSelection(selectedIds.toList(), selectedGroups, targetId)
                showMoveToGroupPicker = false
                exitSelection()
            },
            onDismiss = { showMoveToGroupPicker = false }
        )
    }

    // v7.1.82 — création explicite d'un sous-dossier : MÊME feuille moderne qu'à la racine.
    pendingCreateSubGroupIds?.let { ids ->
        CreateGroupSheet(
            members = selectedCategories,
            groups = allGroups,
            // v7.1.84 — pré-rempli avec le dossier COURANT (on crée un sous-groupe ici),
            // mais modifiable : on peut le faire naître ailleurs, ou à la racine.
            initialParentId = group?.id,
            onConfirm = { name, imagePath, parentId ->
                viewModel.createSubGroup(name, ids, imagePath, parentId)
                pendingCreateSubGroupIds = null
                exitSelection()
            },
            onDismiss = { pendingCreateSubGroupIds = null }
        )
    }

    // Fusion par geste → nommer le SOUS-GROUPE créé (dialogue historique, inchangé).
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
    // v7.1.127 — ÉDITION d'un dossier (nom + image) dans la feuille de sa création.
    editGroupTarget?.let { target ->
        EditGroupSheet(
            group = target,
            onConfirm = { name, imagePath ->
                viewModel.updateGroup(target, name, imagePath)
                editGroupTarget = null
                exitSelection()
            },
            onDismiss = { editGroupTarget = null }
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
    if (showBulkDelete) {
        val singleGroup = selectedGroups.singleOrNull()?.takeIf { selectedIds.isEmpty() }
        val singleGroupMembers = singleGroup?.let { g -> members.count { it.parentGroupId == g.id } } ?: 0
        // v7.1.130 — même bascule « groupe unique / sélection » qu'à l'écran Catégories,
        // résolue en `String` avant l'appel.
        JtrAlertDialog(
            onDismissRequest = { showBulkDelete = false },
            icon = Icons.Default.Warning,
            title = if (singleGroup != null)
                stringResource(R.string.categories_delete_group_title, singleGroup.name)
            else stringResource(R.string.categories_delete_selected_title, totalSelected),
            text = if (singleGroup != null)
                stringResource(R.string.categories_delete_group_text, singleGroup.name, singleGroupMembers)
            else stringResource(R.string.categories_delete_selected_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.deleteSelection(selectedIds.toList(), selectedGroups, members)
                showBulkDelete = false; exitSelection()
            },
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true
        )
    }
    // « Mettre à la corbeille » le dossier : catégories + contacts en corbeille,
    // sous-groupes remontés d'un niveau (confirmation explicite avant cascade).
    if (showGroupTrashConfirm) {
        val groupName = group?.name.orEmpty()
        JtrAlertDialog(
            onDismissRequest = { showGroupTrashConfirm = false },
            icon = Icons.Default.Warning,
            title = stringResource(R.string.categories_delete_group_title, groupName),
            text = stringResource(R.string.categories_delete_group_text, groupName, members.size),
            confirmLabel = stringResource(R.string.action_move_to_trash),
            onConfirm = {
                showGroupTrashConfirm = false
                viewModel.deleteGroupToTrash()
                onNavigateBack()
            },
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true
        )
    }

    if (showDissolveConfirm) {
        // v7.1.130 — ⚠️ CHANGEMENT VISUEL ASSUMÉ : la confirmation passe en ROUGE. Elle était
        // la SEULE des huit à garder un bouton neutre alors que son icône était déjà en `error`,
        // incohérence d'autant plus gênante que dissoudre est irréversible. L'icône `FolderOff`
        // (et non `Warning`) est conservée : elle dit ce que l'action fait, pas qu'elle alarme.
        JtrAlertDialog(
            onDismissRequest = { showDissolveConfirm = false },
            icon = Icons.Default.FolderOff,
            title = stringResource(R.string.categories_dissolve_group),
            text = stringResource(R.string.categories_dissolve_confirm_text),
            confirmLabel = stringResource(R.string.categories_dissolve_group),
            onConfirm = { viewModel.dissolve(); showDissolveConfirm = false; onNavigateBack() },
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true
        )
    }

    Scaffold(
        // v7.1.86 — même agencement qu'à l'Accueil : bas-fin, au-dessus de la bottomBar ;
        // masqué en sélection, où le SelectionFooter occupe le bas.
        floatingActionButton = {
            ScrollToTopFab(
                visible = showScrollToTop && !isSelectionActive,
                onClick = {
                    scope.launch {
                        if (viewMode == JtrViewMode.GRID) gridState.animateScrollToItem(0)
                        else listState.animateScrollToItem(0)
                    }
                }
            )
        },
        topBar = {
            if (isSelectionActive && searchActive) {
                // Recherche CONTEXTUELLE pendant la sélection : filtre l'affichage sans
                // toucher aux coches ; la fermer n'efface que la requête.
                JtrSearchableTopAppBar(
                    title = "",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    searchActive = true,
                    onSearchActiveChange = { searchActive = it },
                    // v7.1.99 — EN SÉLECTION la recherche filtre le CONTENU du dossier
                    // (ses catégories et sous-dossiers, `sortName`), pas des contacts —
                    // contrairement à la loupe hors sélection, qui ouvre la page scopée
                    // aux contacts de v7.1.87. Deux périmètres, deux libellés.
                    searchPlaceholder = stringResource(R.string.category_search_placeholder)
                )
            } else if (isSelectionActive) {
                TopAppBar(
                    title = {
                        // Le plural couvre 0 (« 0 selected ») : plus de chaîne séparée pour
                        // ce cas, c'est désormais la même forme sur les 5 écrans (v7.1.98).
                        Text(
                            pluralStringResource(
                                R.plurals.categories_selection_count, totalSelected, totalSelected
                            )
                        )
                    },
                    navigationIcon = {
                        SelectAllToggle(
                            allSelected = allTopEntriesSelected,
                            onSelectAll = { selectAll() },
                            onDeselectAll = { deselectVisible() }
                        )
                    },
                    actions = {
                        // v7.1.98 — loupe conservée EN sélection, comme les 3 autres écrans
                        // qui ont une recherche : on filtre, on coche, les coches survivent.
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search))
                        }
                        TextButton(onClick = { exitSelection() }) {
                            Text(stringResource(R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                // Barre harmonisée : retour + loupe + « + » + menu 3 points (Tri /
                // Affichage, puis actions propres au dossier : « Défaire le groupe »).
                JtrSearchableTopAppBar(
                    title = group?.name ?: stringResource(R.string.categories_title),
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    // v7.1.87 — la loupe NAVIGUE vers la page de recherche dédiée, scopée
                    // aux CONTACTS de tout ce dossier (l'ancienne recherche inline filtrait
                    // les catégories/sous-dossiers par nom : elle n'est plus déclenchable,
                    // d'où l'état figé à false et le retrait de l'état local).
                    searchActive = false,
                    onSearchActiveChange = {},
                    onSearchClick = { onNavigateToScopedSearch(viewModel.groupId) },
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
                            // Actions sur le dossier lui-même (v5.3) : modifier et mise à
                            // la corbeille. v7.1.127 — « Renommer » et « Changer l'image »
                            // n'étaient que deux moitiés du même formulaire : une seule
                            // entrée « Modifier » les remplace, comme dans le footer de
                            // sélection et comme à l'intérieur d'une catégorie.
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    dismiss()
                                    group?.let { editGroupTarget = it }
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
                    canMove = totalSelected >= 1 && movableGroups.isNotEmpty(),
                    onCreateGroup = {
                        // Crée un SOUS-GROUPE depuis les catégories cochées (feuille
                        // moderne v7.1.82, distincte du nommage par fusion).
                        if (selectedIds.size >= 2) pendingCreateSubGroupIds = selectedIds.toList()
                    },
                    onMoveToGroup = { showMoveToGroupPicker = true },
                    canDelete = totalSelected >= 1,
                    onDelete = { showBulkDelete = true },
                    canEdit = totalSelected == 1,
                    onEdit = {
                        val cat = selectedCategories.singleOrNull()
                        if (cat != null) editTarget = cat
                        else selectedGroups.singleOrNull()?.let { editGroupTarget = it }
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
                        enableMerge = true,
                        // Fix v7.1.81 — même état que le navigateur normal (pas de reset).
                        gridState = gridState
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
                        enableMerge = true,
                        listState = listState
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
                    onGroupLongClick = { startSelectionGroup(it) },
                    listState = listState,
                    gridState = gridState
                )
            }
        }
    }
}

