package com.jtr.app.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.home.PersonListContent
import com.jtr.app.ui.person.formatDateTimeLong
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onNavigateToAddPerson: () -> Unit,
    onAddExistingContacts: () -> Unit = {},
    viewModel: CategoryDetailViewModel = viewModel()
) {
    val persons by viewModel.persons.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    // Bouton Retour en mode sélection : vide la sélection (événement remonté au
    // ViewModel) et reste sur l'écran ; inactif sinon (navigation standard).
    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    // Catégorie virtuelle « Favoris » : lecture seule (ni ajout, ni édition, ni retrait).
    val isVirtual = viewModel.isVirtualFavorites

    val lastModified by viewModel.lastModified.collectAsStateWithLifecycle()

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    // Mode recherche de la TopAppBar (état d'UI local ; la query vient du ViewModel).
    var searchActive by remember { mutableStateOf(false) }

    // v7.1.70 — snackbar d'annulation du retrait en lot de contacts (« Retirer de … »).
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.common_undo)

    // Sortie d'écran → réinitialisation du filtre : aucune query fantôme au retour.
    DisposableEffect(Unit) { onDispose { viewModel.clearSearch() } }

    // « Modifier » : même formulaire unifié que la création (nom, couleur, image).
    if (showEditDialog) {
        category?.let { current ->
            EditCategoryDialog(
                category = current,
                onConfirm = { name, color, imagePath ->
                    viewModel.updateCategory(
                        current.copy(name = name, color = color, imagePath = imagePath))
                    showEditDialog = false
                },
                onDismiss = { showEditDialog = false }
            )
        }
    }

    // « Informations » (v7.1.48) : dates de création / dernière modification, calqué sur le
    // dialogue des contacts (PersonDetailScreen). Les libellés `person_info_*` sont GÉNÉRIQUES
    // (« Created on » / « Last modified », aucun ne mentionne « contact ») et déjà traduits
    // dans les 13 langues → réutilisés tels quels, zéro nouvelle string.
    if (showInfoDialog) {
        category?.let { current ->
            // Keyé sur la locale courante (cf. PersonDetailScreen) : jamais de format périmé
            // après un changement de langue in-app (v7.1.30).
            val infoLocale = Locale.getDefault()
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.person_info_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Ligne MASQUÉE si l'horodatage est absent (0) plutôt qu'affichée à
                        // « — » ou « 1970 » : dans un dialogue à deux lignes, une ligne vide
                        // est plus bruyante qu'une ligne absente.
                        current.createdAt.takeIf { it > 0L }?.let { created ->
                            Column {
                                Text(stringResource(R.string.person_info_created),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatDateTimeLong(created, infoLocale),
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        lastModified?.let { modified ->
                            Column {
                                Text(stringResource(R.string.person_info_updated),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatDateTimeLong(modified, infoLocale),
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            )
        }
    }

    // « Mettre à la corbeille » : confirmation explicite (cascade sur les contacts).
    if (showTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showTrashConfirm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.category_trash_confirm_title)) },
            text = { Text(stringResource(R.string.category_trash_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTrashConfirm = false
                        viewModel.deleteCategoryToTrash()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showCategoryDialog) {
        // v7.1.80 — sélecteur de cible MODERNE (langage v7.1.77 : vignettes, dossiers
        // dépliables, recherche), sélection SIMPLE : taper une catégorie déplace.
        CategoryPickerSheet(
            title = stringResource(R.string.dialog_assign_category_title),
            categories = categories,
            groups = groups,
            // La catégorie SOURCE n'est plus proposée comme cible (un « move » vers
            // soi-même n'a pas de sens — elle était listée avant v7.1.80).
            excludeCategoryIds = setOf(viewModel.categoryId),
            // v7.1.70 — depuis une catégorie (source définie) : VRAI DÉPLACEMENT transactionnel
            // (retrait de la source + ajout à la cible). La multi-appartenance passe par
            // « Gérer les catégories » sur la fiche.
            onPickSingle = { targetId ->
                viewModel.moveSelectedToCategory(targetId)
                showCategoryDialog = false
            },
            // « + » → la feuille unifiée de création (image incluse), puis move.
            onCreateCategory = { name, color, imagePath ->
                viewModel.createCategoryAndMoveToSelected(name, color, imagePath)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(pluralStringResource(R.plurals.home_selection_count, selectedIds.size, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                // Barre harmonisée : retour + loupe + « + » (existant / nouveau) + menu
                // 3 points (Tri / Affichage).
                JtrSearchableTopAppBar(
                    title = categoryName.ifBlank { stringResource(R.string.category_detail_default_title) },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it },
                    searchPlaceholder = stringResource(R.string.category_detail_search_placeholder),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (!isVirtual) {
                            AddContactMenu(
                                onAddExisting = onAddExistingContacts,
                                onCreateNew = onNavigateToAddPerson
                            )
                        }
                        JtrOverflowMenu(
                            sortCriteria = contactSortCriteria(sortOrder) { viewModel.setSortOrder(it) },
                            viewMode = viewMode,
                            onViewModeChange = { viewModel.setViewMode(it) }
                        ) { dismiss ->
                            // Actions sur la catégorie elle-même (v5.3) — masquées
                            // pour la catégorie virtuelle « Favoris ».
                            if (!isVirtual) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_edit)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { dismiss(); showEditDialog = true }
                                )
                                DropdownMenuItem(
                                    // `person_info_title` (« Information ») et NON
                                    // `person_info_menu`, qui vaut « Profile information » /
                                    // « Informations du profil » — spécifique au contact, faux
                                    // pour une catégorie. Le libellé neutre sert ici de titre
                                    // d'entrée de menu ET de titre de dialogue.
                                    text = { Text(stringResource(R.string.person_info_title)) },
                                    leadingIcon = { Icon(Icons.Default.Info, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { dismiss(); showInfoDialog = true }
                                )
                                // v7.1.70 — « Supprimer » (icône corbeille + rouge), MÊME action
                                // visuelle que le footer de l'écran Catégories (#2). La confirmation
                                // (inchangée) décrit honnêtement le passage en corbeille.
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.common_delete),
                                            color = MaterialTheme.colorScheme.error)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null,
                                        tint = MaterialTheme.colorScheme.error) },
                                    onClick = { dismiss(); showTrashConfirm = true }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = JtrBottomBarTransitions.enter,
                exit = JtrBottomBarTransitions.exit
            ) {
                // v7.1.70 — footer harmonisé avec l'Accueil/Catégories (FooterActionColumn :
                // icône au-dessus d'un libellé court, qui tient enfin ; fini les libellés rognés).
                // LA COULEUR PORTE LE SENS : neutre pour un retrait d'appartenance (Retirer / Déplacer),
                // ROUGE (error) uniquement pour une suppression (Corbeille).
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    // « Retirer » : enlève SEULEMENT le lien → NEUTRE + undo (snackbar nommant la
                    // catégorie). Sans objet pour la catégorie virtuelle « Favoris ».
                    if (!isVirtual) {
                        val removedMsg = stringResource(
                            R.string.category_detail_removed_snackbar, categoryName)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            FooterActionColumn(
                                Icons.Default.LinkOff,
                                stringResource(R.string.category_detail_btn_remove),
                                enabled = true,
                                onClick = {
                                    val removed = selectedIds.toList()
                                    viewModel.removeSelectedFromCategory()
                                    scope.launch {
                                        val res = snackbarHostState.showSnackbar(
                                            message = removedMsg,
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.undoRemoveFromCategory(removed)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    // « Déplacer » (vrai déplacement : retire de CETTE catégorie, ajoute à la
                    // cible choisie) → NEUTRE.
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        FooterActionColumn(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            stringResource(R.string.action_move),
                            enabled = true,
                            onClick = { showCategoryDialog = true }
                        )
                    }
                    // « Corbeille » : suppression GLOBALE du contact → ROUGE (error).
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        FooterActionColumn(
                            Icons.Default.Delete,
                            stringResource(R.string.action_trash_short),
                            enabled = true,
                            onClick = { viewModel.deleteSelected() },
                            tintOverride = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (persons.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.category_detail_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.category_detail_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Rendu commutable LIST / GRID / DETAIL (composant partagé avec l'Accueil).
                PersonListContent(
                    viewMode = viewMode,
                    persons = persons,
                    selectedIds = selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { person ->
                        if (isSelectionMode) viewModel.toggleSelection(person.id)
                        else onNavigateToPersonDetail(person.id)
                    },
                    onLongClick = { viewModel.toggleSelection(it.id) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) }
                )
            }
        }
    }
}

/** Bouton « + » de la TopAppBar : associer un contact existant OU créer un contact. */
@Composable
private fun AddContactMenu(
    onAddExisting: () -> Unit,
    onCreateNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Add,
                contentDescription = stringResource(R.string.category_detail_fab_add))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.category_detail_add_existing)) },
                leadingIcon = { Icon(Icons.Default.PersonSearch, null) },
                onClick = { expanded = false; onAddExisting() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.category_detail_create_new)) },
                leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                onClick = { expanded = false; onCreateNew() }
            )
        }
    }
}
