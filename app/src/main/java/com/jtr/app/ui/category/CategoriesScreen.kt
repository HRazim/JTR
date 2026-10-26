package com.jtr.app.ui.category

import android.net.Uri
import android.util.Log
import com.jtr.app.data.files.ImageFileStore
import com.jtr.app.ui.components.SelectAllToggle
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.ImageCropDialog
import com.jtr.app.ui.person.PickerThumbnail
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
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
import com.jtr.app.ui.components.FormPhotoHeader
import com.jtr.app.ui.components.FormTextField
import com.jtr.app.ui.components.PhotoZoomDialog
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.components.JtrSelectionCheck
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.components.ReorderableGrid
import com.jtr.app.ui.components.SCROLL_TO_TOP_THRESHOLD
import com.jtr.app.ui.components.ScrollToTopFab
import com.jtr.app.ui.components.rememberSystemPhotoPicker
import com.jtr.app.ui.navigation.nestedScreenContentInsets
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
 * v7.1.79 — PALETTE CURATÉE partagée (formulaire catégorie + création à la volée de
 * l'Accueil) : 12 teintes DOUCES (gamme « 300 » Material : saturation modérée, luminosité
 * homogène ~65 %) qui remplacent les 6 aplats saturés « Flat UI ». Lisibles en clair ET
 * en sombre, en aplat (tuiles sans image) comme en accent dérivé (initiale sur teinte
 * 16 %, anneau — langage v7.1.77). Le STOCKAGE ne change pas : `Category.color` reste un
 * hex String NOT NULL, et les anciens hex restent valides partout au rendu (en édition,
 * un hex hors palette est présenté comme pastille « couleur actuelle » en tête).
 */
internal val categoryColorPalette = listOf(
    "#E57373", // rouge doux
    "#F06292", // rose
    "#BA68C8", // mauve
    "#9575CD", // violet
    "#7986CB", // indigo
    "#64B5F6", // bleu
    "#4FC3F7", // cyan
    "#4DB6AC", // teal
    "#81C784", // vert
    "#FFB74D", // ambre
    "#FF8A65", // corail
    "#A1887F", // taupe
)

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
    // v7.1.83 — sélecteur de dossier CIBLE (« Déplacer vers un groupe… »).
    var showMoveToGroupPicker by remember { mutableStateOf(false) }
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
    // Photo Picker système (v7.1.66) — aucune permission requise.
    val groupPhotoPicker = rememberSystemPhotoPicker { uri -> pendingGroupCropUri = uri }

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

    // v7.1.98 — priorité du bouton Retour, identique sur les 5 écrans de sélection :
    // 1) la recherche inline est ouverte → on la ferme, la sélection SURVIT ;
    // 2) sinon on quitte la sélection ; 3) sinon la navigation reprend normalement.
    BackHandler(enabled = (isSelectionActive && searchActive) || isSelectionActive) {
        if (searchActive) {
            searchActive = false
            viewModel.setSearchQuery("")
        } else {
            exitSelection()
        }
    }
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
        // Dernière modification d'une catégorie — formule PARTAGÉE avec le dialogue
        // « Informations » (v7.1.48) : contenu (updatedAt) ∪ activité des membres.
        fun activityOf(c: Category) = categoryLastModified(c, categoryActivity[c.id])
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

    /** v7.1.98 — vrai quand TOUTES les entrées de la racine sont cochées (bascule partagée). */
    val allTopEntriesSelected = topEntries.isNotEmpty() && topEntries.all { entry ->
        when (entry) {
            is TopEntry.Folder -> entry.group.id in selectedGroupIds
            is TopEntry.Single -> entry.category.id in selectedIds
        }
    }

    /**
     * Décoche les entrées AFFICHÉES, et elles seules — pendant exact de [selectAll], qui
     * ne coche que `topEntries`. Sous une recherche, les coches hors filtre survivent.
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

    // ── Dialogues du mode sélection ───────────────────────────────────────────
    if (showMoveToGroupPicker) {
        // v7.1.83 — sélecteur de dossiers MODERNE (vignettes, hiérarchie, recherche) au
        // lieu d'un item « Move to X » par dossier. Le déplacement reste celui de
        // v7.1.70 (transactionnel : rattachement des catégories + fusion des dossiers
        // cochés dans la cible).
        CategoryPickerSheet(
            title = stringResource(R.string.categories_move_to_group),
            categories = categories,
            groups = groups,
            folderTargets = true,
            // On ne propose pas comme cible un dossier qu'on est en train de déplacer.
            excludeGroupIds = selectedGroupIds.toSet(),
            onPickFolder = { targetId ->
                val merges = selectedGroupIds.associateWith { gid ->
                    categories.filter { it.parentGroupId == gid }.map { it.id }
                }
                viewModel.moveSelectionToGroup(selectedIds.toList(), merges, targetId)
                showMoveToGroupPicker = false
                exitSelection()
            },
            onDismiss = { showMoveToGroupPicker = false }
        )
    }

    if (showCreateGroupDialog) {
        // v7.1.82 — feuille moderne (aperçu vivant, image de couverture, vignettes de ce
        // qu'on groupe) à la place du dialogue nu. Le nommage d'un groupe né du geste de
        // FUSION et le renommage gardent [TextPromptDialog] (hors périmètre V1).
        CreateGroupSheet(
            members = selectedCategories,
            // v7.1.84 — le dossier parent se choisit DANS la feuille (défaut : racine).
            groups = groups,
            initialParentId = null,
            onConfirm = { name, imagePath, parentId ->
                viewModel.createGroup(name, selectedIds.toList(), imagePath, parentId)
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

    // FAB « remonter en haut » (v7.1.72) : états de défilement du navigateur NORMAL
    // (TopEntriesBrowser). En mode sélection, le contenu bascule sur ReorderableTopGrid/List
    // (états internes propres) → le FAB est masqué (visible=false). derivedStateOf reclé sur
    // viewMode → ne recompose qu'au franchissement du seuil.
    val topListState = rememberLazyListState()
    val topGridState = rememberLazyGridState()
    val showScrollToTop by remember(viewMode) {
        derivedStateOf {
            if (isSelectionActive) false
            else {
                val index = if (viewMode == JtrViewMode.GRID) topGridState.firstVisibleItemIndex
                else topListState.firstVisibleItemIndex
                index > SCROLL_TO_TOP_THRESHOLD
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionActive && searchActive) {
                // v7.1.98 — recherche CONTEXTUELLE pendant la sélection : elle filtre
                // l'affichage sans toucher aux coches, et la fermer n'efface que la requête.
                JtrSearchableTopAppBar(
                    title = "",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    searchActive = true,
                    onSearchActiveChange = { searchActive = it },
                    // v7.1.99 — cet écran filtre des CATÉGORIES et des dossiers par leur nom
                    // (`CategoryViewModel`), pas des contacts : le placeholder le dit enfin.
                    searchPlaceholder = stringResource(R.string.category_search_placeholder)
                )
            } else if (isSelectionActive) {
                // En-tête de sélection : Tout sélectionner (G) · compteur (C) · Annuler (D).
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
                        // v7.1.98 — loupe conservée EN sélection (comme l'Accueil) : on
                        // cherche, on coche, on recommence — les coches survivent au filtre.
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
                    // v7.1.99 — même périmètre qu'en sélection : des catégories.
                    searchPlaceholder = stringResource(R.string.category_search_placeholder),
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
                            // v7.1.78 — includeCustom : « Personnalisé » n'est offert qu'à la
                            // RACINE (le détail de dossier garde ses trois critères).
                            sortCriteria = categorySortCriteria(sortOrder, includeCustom = true) { viewModel.setSortOrder(it) },
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
                    canMove = totalSelected >= 1 && movableGroups.isNotEmpty(),
                    onCreateGroup = { showCreateGroupDialog = true },
                    onMoveToGroup = { showMoveToGroupPicker = true },
                    canDelete = totalSelected >= 1,
                    onDelete = { showBulkDeleteDialog = true },
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
        },
        // FAB secondaire « remonter en haut » (v7.1.72) : bas-fin, au-dessus de la nav bar
        // globale (padding racine). L'ajout de catégorie est une icône « + » en TopAppBar :
        // aucun FAB d'ajout à chevaucher.
        floatingActionButton = {
            ScrollToTopFab(
                visible = showScrollToTop,
                onClick = {
                    screenScope.launch {
                        if (viewMode == JtrViewMode.GRID) topGridState.animateScrollToItem(0)
                        else topListState.animateScrollToItem(0)
                    }
                }
            )
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
                        enableMerge = true,
                        // Fix v7.1.81 — MÊME état que le navigateur normal : l'appui
                        // long entre en sélection sans bouger la liste.
                        gridState = topGridState
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
                        enableMerge = true,
                        listState = topListState
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
                    listState = topListState,
                    gridState = topGridState
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
                text = pluralStringResource(R.plurals.categories_person_count, personCount, personCount),
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
                // v7.1.126 — MÊME vignette que les feuilles de sélection ([PickerThumbnail]),
                // d'où un ANNEAU à la couleur de la catégorie autour de l'image. Avant, la
                // couleur ne teintait que le FOND du disque — qu'une photo recouvrait
                // entièrement : deux catégories illustrées ne se distinguaient plus que par
                // la barre de 5 dp. L'anneau, lui, survit à l'image. Effet voulu du partage :
                // sans photo, le repli devient l'INITIALE sur teinte douce, bien plus
                // distinctive que l'icône de dossier identique pour toutes.
                PickerThumbnail(
                    imagePath = category.imagePath,
                    name = category.name,
                    colorHex = category.color,
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = category.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        text = pluralStringResource(R.plurals.categories_person_count, personCount, personCount),
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
    // États de défilement hoistés (v7.1.72) pour le FAB « remonter en haut ». LIST/DETAIL
    // partagent [listState] (LazyColumn), GRID a [gridState]. Défauts = aucun appelant impacté.
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState()
) {
    // v7.1.100 — la tuile virtuelle « Favoris » a été retirée. Elle s'insérait ICI, en
    // tête, alors que le mode sélection rend la MÊME liste sans elle : les deux vues
    // partageant le même état de défilement (v7.1.81), tout glissait d'une cellule à
    // l'appui long. Ce navigateur et le réordonnable affichent désormais exactement
    // les mêmes entrées. Les favoris restent portés par `isFavorite` (étoile + tri).
    when (viewMode) {
        JtrViewMode.GRID -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(entries, key = { topEntryKey(it) }) { entry ->
                when (entry) {
                    is TopEntry.Folder -> EntryCompactRow(
                        isFolder = true,
                        name = entry.group.name,
                        isFavorite = entry.group.isFavorite,
                        imagePath = entry.group.imagePath,
                        colorHex = null,
                        onClick = { onGroupClick(entry.group.id) },
                        onLongClick = { onGroupLongClick(entry.group.id) })
                    is TopEntry.Single -> EntryCompactRow(
                        isFolder = false,
                        name = entry.category.name,
                        isFavorite = entry.category.isFavorite,
                        imagePath = entry.category.imagePath,
                        colorHex = entry.category.color,
                        onClick = { onCategoryClick(entry.category.id) },
                        onLongClick = { onCategoryLongClick(entry.category.id) })
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
    // v7.1.126 — la COULEUR STOCKÉE (hex), et non plus une couleur déjà résolue : la
    // vignette partagée en tire l'anneau et sa teinte de repli. `null` pour un dossier,
    // qui n'a pas de couleur propre et retombe donc sur un liseré neutre.
    colorHex: String?,
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
            // v7.1.126 — c'est CE mode qui s'appelle « Liste », et c'est ici que la plainte
            // portait : avatar de 40 dp dont le fond `accent` disparaissait sous l'image,
            // ne laissant AUCUN indice de couleur. La vignette partagée des feuilles de
            // sélection apporte l'ANNEAU, qui lui survit à la photo. La FORME est conservée
            // telle quelle (carré arrondi = dossier, disque = catégorie) : c'est elle qui
            // dit la nature de l'entrée, l'anneau ne dit que son identité.
            PickerThumbnail(
                imagePath = imagePath,
                name = name,
                colorHex = colorHex,
                size = 40.dp,
                shape = if (isFolder) RoundedCornerShape(10.dp) else CircleShape,
                // Idem : dossier sans photo teinté comme avant le lot. Sans effet sur une
                // catégorie, dont le repli est l'initiale (autre branche du composant).
                folderContainerColor = MaterialTheme.colorScheme.primaryContainer,
                folderIconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                folderIconSize = 20.dp
            )
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
 *
 * v7.1.55 — le partage de catégories a été RETIRÉ (il n'exportait qu'un nom et un
 * compteur, et le mode sélection en faisait un export de masse mal cadré). Le
 * partage de PROFILS, lui, est conservé (HomeScreen). Les 4 actions restantes se
 * répartissent d'elles-mêmes : chaque `Box` porte `weight(1f)`, donc un quart de la
 * barre chacune — rien à recalculer.
 */
@Composable
internal fun SelectionFooter(
    canFavorite: Boolean,
    favoriteActive: Boolean,
    onToggleFavorite: () -> Unit,
    canCreateGroup: Boolean,
    canMove: Boolean,
    onCreateGroup: () -> Unit,
    // v7.1.83 — UNE entrée « Déplacer vers un groupe… » qui ouvre le sélecteur de
    // dossiers moderne, au lieu d'un item par dossier (liste plate ingérable).
    onMoveToGroup: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    canRename: Boolean,
    onRename: () -> Unit,
    canChangeImage: Boolean,
    onChangeImage: () -> Unit,
    canMoveOut: Boolean = false,
    onMoveOut: (() -> Unit)? = null
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
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.categories_move_to_group)) },
                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                        onClick = { folderMenu = false; onMoveToGroup() })
                }
            }
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
                // v7.1.126 — même vignette partagée que les catégories, pour que les deux
                // lignes se lisent comme une famille. Un dossier n'a pas de couleur propre :
                // `colorHex = null` lui donne un liseré neutre, et le carré arrondi continue
                // de dire « dossier ».
                PickerThumbnail(
                    imagePath = group.imagePath,
                    name = group.name,
                    colorHex = null,
                    size = 44.dp,
                    shape = RoundedCornerShape(12.dp),
                    // Habillage du dossier sans photo REPRIS DE L'AVANT-LOT : teinté par la
                    // palette, pas neutre. Le neutre reste le défaut du composant, donc les
                    // feuilles de sélection gardent le leur.
                    folderContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    folderIconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    folderIconSize = 24.dp
                )
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
 * v7.1.125 — n'est plus qu'un ADAPTATEUR : la mécanique du geste vit dans
 * [ReorderableGrid] (copie de travail re-synchronisée hors glissement, élément suivi
 * translaté en X et Y sous le doigt, voisins décalés par `animateItem()`, ordre persisté
 * au drop). Ce qui reste ici est le métier des catégories.
 */
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
    enableMerge: Boolean = true,
    // Fix v7.1.81 — état de défilement HOISTABLE : l'appelant passe le MÊME état que son
    // navigateur normal, sinon l'entrée en mode sélection recréait une grille à l'index 0
    // (« flash » + liste renvoyée tout en haut sous le doigt).
    gridState: LazyGridState = rememberLazyGridState()
) {
    // v7.1.125 — la mécanique du drag (copie de travail semée, hit-test en coordonnées
    // fenêtre, auto-défilement, action de drop différée hors canal tactile) vit désormais
    // dans [ReorderableGrid], partagé avec la galerie. Ne reste ici que le MÉTIER des
    // catégories : ce qu'un drop au CENTRE veut dire (fusionner deux catégories, ou
    // déposer une catégorie dans un dossier) et le rendu des tuiles.
    val mergeAllowed: (TopEntry, TopEntry) -> Boolean = { dragged, _ ->
        dragged is TopEntry.Single
    }
    ReorderableGrid(
        items = entries,
        key = { entry ->
            when (entry) {
                is TopEntry.Folder -> "g_${entry.group.id}"
                is TopEntry.Single -> "c_${entry.category.id}"
            }
        },
        columns = 2,
        gridState = gridState,
        onPersistOrder = { ordered ->
            onPersistOrder(
                ordered.map {
                    when (it) {
                        is TopEntry.Folder -> TopOrderRef.Group(it.group.id)
                        is TopEntry.Single -> TopOrderRef.Cat(it.category.id)
                    }
                }
            )
        },
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Le footer contextuel du mode sélection recouvre le bas de la grille (~125 dp) :
        // la zone d'auto-défilement est élargie pour commencer au-dessus de lui.
        autoScrollBottomZone = 190.dp,
        canCenterDrop = if (enableMerge) mergeAllowed else null,
        onCenterDrop = { dragged, target ->
            val cat = (dragged as? TopEntry.Single)?.category
            if (cat != null) {
                when (target) {
                    is TopEntry.Single ->
                        if (target.category.id != cat.id) {
                            onMergeRequest(listOf(cat.id, target.category.id))
                        }
                    is TopEntry.Folder -> onMoveToFolder(cat.id, target.group.id)
                }
            }
        }
    ) { entry, _, isCenterTarget, mod ->
        when (entry) {
            is TopEntry.Folder -> TopSelectionTile(
                isFolder = true,
                name = entry.group.name,
                isFavorite = entry.group.isFavorite,
                // Compteur enrichi : sous-categories + cumul des contacts.
                subtitle = stringResource(R.string.categories_folder_counter,
                    entry.members.size, entry.members.sumOf { countOf(it.id) }),
                imagePath = entry.group.imagePath,
                accent = MaterialTheme.colorScheme.primaryContainer,
                selected = isGroupSelected(entry.group.id),
                mergeHighlight = isCenterTarget,
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
                    subtitle = pluralStringResource(R.plurals.categories_person_count, c, c),
                    imagePath = entry.category.imagePath,
                    accent = accent,
                    selected = isCategorySelected(entry.category.id),
                    mergeHighlight = isCenterTarget,
                    onClick = { onToggleCategory(entry.category.id) },
                    modifier = mod
                )
            }
        }
    }
}

/**
 * Action différée déclenchée au Drop — traitée HORS du callback tactile (anti-crash).
 * v7.1.125 : ne sert plus qu'à la LISTE ; la grille, passée au moteur partagé
 * [ReorderableGrid], porte désormais cette mécanique en interne.
 */
private sealed interface DropAction {
    data class Merge(val ids: List<String>) : DropAction
    data class Insert(val categoryId: String, val groupId: Long) : DropAction
    data class Reorder(val refs: List<TopOrderRef>) : DropAction
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
    enableMerge: Boolean = true,
    // Fix v7.1.81 — même hoisting que ReorderableTopGrid (pas de reset à l'appui long).
    listState: LazyListState = rememberLazyListState()
) {
    // Fix v7.1.81 (2e cause) — semée à la création : voir ReorderableTopGrid.
    val data = remember { mutableStateListOf<TopEntry>().apply { addAll(entries) } }
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
    // v7.1.78 — auto-défilement pendant le drag (voir ReorderableTopGrid) : vitesse en
    // px/frame, posée par onDrag, appliquée par la boucle LaunchedEffect, remise à zéro
    // à la fin du geste.
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    val autoDensity = LocalDensity.current
    val autoScrollZonePx = with(autoDensity) { 64.dp.toPx() }
    // Zone basse élargie : la liste passe SOUS le footer contextuel (voir la grille).
    val autoScrollBottomZonePx = with(autoDensity) { 190.dp.toPx() }
    val autoScrollMaxStepPx = with(autoDensity) { 10.dp.toPx() }

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
    // Résolution cible → fusion/réordonnancement, partagée entre onDrag et la boucle
    // d'auto-défilement (même raison que la grille : la liste glisse sous un doigt
    // immobile, les échanges doivent continuer de cascader).
    fun resolveDragTarget() {
        val from = draggingIndex ?: return
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
    }
    // v7.1.78 — pas d'auto-défilement selon la proximité des bords (voir la grille).
    fun autoScrollStepFor(pointerY: Float): Float {
        val c = listCoords ?: return 0f
        if (!c.isAttached) return 0f
        val top = c.localToWindow(Offset.Zero).y
        val bottom = top + c.size.height
        return when {
            pointerY <= top + autoScrollZonePx ->
                -autoScrollMaxStepPx * ((top + autoScrollZonePx - pointerY) / autoScrollZonePx).coerceIn(0.25f, 1f)
            pointerY >= bottom - autoScrollBottomZonePx ->
                autoScrollMaxStepPx * ((pointerY - (bottom - autoScrollBottomZonePx)) / autoScrollBottomZonePx).coerceIn(0.25f, 1f)
            else -> 0f
        }
    }

    // Boucle d'auto-défilement (une itération par frame, voir ReorderableTopGrid).
    LaunchedEffect(draggingIndex != null) {
        while (draggingIndex != null) {
            val step = autoScrollSpeed
            if (step != 0f) {
                listState.scrollBy(step)
                resolveDragTarget()
            }
            withFrameMillis { }
        }
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
                            autoScrollSpeed = 0f
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggingIndex == null) return@detectDragGesturesAfterLongPress
                        val coords = listCoords
                        if (coords == null || !coords.isAttached)
                            return@detectDragGesturesAfterLongPress
                        deltaY += dragAmount.y
                        pointerWinY = coords.localToWindow(change.position).y
                        // Fusion/réordonnancement : logique partagée avec l'auto-défilement.
                        resolveDragTarget()
                        autoScrollSpeed = autoScrollStepFor(pointerWinY)
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
                            autoScrollSpeed = 0f
                        }
                    },
                    onDragCancel = { draggingIndex = null; mergeTargetKey = null; autoScrollSpeed = 0f }
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
                        subtitle = pluralStringResource(R.plurals.categories_person_count,
                            countOf(entry.category.id), countOf(entry.category.id)),
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
    // v7.1.79 — le défaut vient de la PALETTE CURATÉE (bleu doux, analogue au bleu
    // historique #2E86C1) : pas de pastille « couleur actuelle » parasite en création.
    initialColor = "#64B5F6",
    initialImagePath = null,
    confirmLabel = stringResource(R.string.common_create),
    onConfirm = onConfirm,
    onDismiss = onDismiss
)

/**
 * Formulaire de catégorie UNIFIÉ (création ET édition) : nom + couleur + image.
 *
 * v7.1.79 — refonte V1 : FEUILLE MONTANTE (cohérente avec le sélecteur, le tri et les
 * langues) au lieu du dialogue centré ; APERÇU VIVANT en tête (image, sinon initiale sur
 * teinte douce + anneau — langage v7.1.77) avec badge caméra ; palette CURATÉE douce
 * ([categoryColorPalette]) en section compacte sous le nom (l'image prime, la couleur
 * reste le moteur du repli) ; bouton de validation pleine largeur. Les chaînes photo
 * (Photo Picker → crop → filesDir) et la signature `onConfirm(name, color, imagePath)`
 * sont inchangées — Dialog (zoom/crop) sur feuille : même empilement qu'avant.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    // En ÉDITION d'une catégorie dont l'hex (ancienne palette, restauration .jtr…) est
    // HORS palette curatée : il devient la PREMIÈRE pastille (« couleur actuelle ») —
    // la couleur d'origine reste sélectionnable, aucune perte silencieuse.
    val palette = remember(initialColor) {
        if (initialColor in categoryColorPalette) categoryColorPalette
        else listOf(initialColor) + categoryColorPalette
    }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var imagePath by remember { mutableStateOf(initialImagePath) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = rememberCategoryColor(selectedColor)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Fix v7.1.79 — le bouton de validation est ÉPINGLÉ hors de la zone scrollable,
        // dans la Column porteuse de l'imePadding : dès que le clavier s'ouvre (focus
        // auto en création), il REMONTE au-dessus et reste tappable sans « Done ». Le
        // contenu (aperçu, nom, palette) se compresse et défile au-dessus de lui
        // (`weight(1f, fill = false)` : même grammaire que la liste du sélecteur).
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            // ── APERÇU VIVANT (v7.1.79, extrait en v7.1.111) : image si présente, sinon
            // initiale du nom sur teinte douce + anneau d'accent — le composant partagé
            // porte aussi le zoom (Remplacer/Retirer), le Photo Picker et le recadrage.
            FormPhotoHeader(
                photo = imagePath,
                accent = accent,
                fallbackIcon = Icons.Default.Folder,
                photoContentDescription = stringResource(R.string.categories_image_cd),
                cropShape = CropShape.RECTANGLE,
                onPhotoCropped = { croppedUri ->
                    scope.launch {
                        val path = withContext(Dispatchers.IO) {
                            copyCategoryPhotoToStorage(context, croppedUri)
                        }
                        if (path != null) imagePath = path
                    }
                },
                onPhotoRemoved = { imagePath = null },
                fallbackInitialFrom = name
            )
            Spacer(Modifier.height(8.dp))

            val focusRequester = remember { FocusRequester() }
            // v7.1.112 — champ consolidé sur FormTextField (même rendu : il EST la version
            // factorisée de cet OutlinedTextField).
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.common_name_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            if (initialName.isEmpty()) {
                // Focus auto en CRÉATION seulement : en édition, le clavier recouvrirait
                // l'aperçu et la palette qu'on vient précisément retoucher.
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
            Spacer(Modifier.height(20.dp))

            // ── Couleur : section compacte SECONDAIRE (l'image prime ; la couleur reste
            // le moteur du repli initiale/anneau et des tuiles sans image).
            Text(stringResource(R.string.common_color_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // FlowRow : 2 rangées de 6 ; se replie proprement (écran étroit, grande police).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                palette.forEach { color ->
                    val swatch = rememberCategoryColor(color)
                    val selected = selectedColor == color
                    // Cible tactile 48 dp ; sélection = ANNEAU détaché + coche (plus de
                    // coche blanche posée sur un aplat).
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .then(if (selected) Modifier.border(2.dp, swatch, CircleShape) else Modifier)
                            .clickable { selectedColor = color },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            }

            // Barre d'action FIXE (voir le commentaire d'en-tête de la Column).
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor, imagePath) },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text(confirmLabel) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal fun copyCategoryPhotoToStorage(context: android.content.Context, uri: Uri): String? {
    val path = try {
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        val dest = File(dir, "category_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        dest.absolutePath
    } catch (_: Exception) { null }
    // v7.1.116 (a') — le `crop_` source (sas filesDir/crops/) part APRÈS la copie, succès
    // OU échec : seul le flux d'édition de contact nettoyait son sas, les quatre call-sites
    // de cette fonction (création/édition de catégorie et de groupe) laissaient chacun un
    // orphelin par recadrage validé. L'effacement vit ICI pour qu'aucun futur appelant ne
    // puisse l'oublier ; `deleteFileIfOwned` ne touche que nos dossiers (une URI de picker
    // `content://` est refusée par construction) et un fichier déjà absent est un no-op.
    ImageFileStore.deleteFileIfOwned(context, uri.toString())
    return path
}
