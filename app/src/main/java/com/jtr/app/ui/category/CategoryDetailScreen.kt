package com.jtr.app.ui.category

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.home.AssignCategoryDialog
import com.jtr.app.ui.home.PersonListContent

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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    // Catégorie virtuelle « Favoris » : lecture seule (ni ajout, ni édition, ni retrait).
    val isVirtual = viewModel.isVirtualFavorites

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    // Mode recherche de la TopAppBar (état d'UI local ; la query vient du ViewModel).
    var searchActive by remember { mutableStateOf(false) }

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
        AssignCategoryDialog(
            categories = categories,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { categoryId ->
                viewModel.assignCategoryToSelected(categoryId)
                showCategoryDialog = false
            },
            onCreateAndAssign = { name, color ->
                viewModel.createCategoryAndAssignToSelected(name, color)
                showCategoryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_selection_count, selectedIds.size)) },
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
                            sortOptions = contactSortOptions(sortOrder) { viewModel.setSortOrder(it) },
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
                                    text = { Text(stringResource(R.string.action_move_to_trash)) },
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
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // « Retirer de la catégorie » : enlève SEULEMENT le lien —
                        // sans objet pour la catégorie virtuelle « Favoris ».
                        if (!isVirtual) {
                            OutlinedButton(
                                onClick = { viewModel.removeSelectedFromCategory() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.category_detail_btn_remove))
                            }
                        }
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_btn_category))
                        }
                        // Sémantique v5.3 : suppression GLOBALE du profil = corbeille.
                        Button(
                            onClick = { viewModel.deleteSelected() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_trash_short))
                        }
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
