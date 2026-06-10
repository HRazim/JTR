package com.jtr.app.ui.category

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.home.PersonListContent

/**
 * Écran « Ajouter des contacts existants » à une catégorie : réutilise la structure
 * de l'Accueil en mode sélection (liste compacte + recherche dans la TopAppBar),
 * SANS dialogue. L'utilisateur coche, valide, et revient au détail de la catégorie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectContactsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SelectContactsViewModel = viewModel()
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val categoryName by viewModel.categoryName.collectAsStateWithLifecycle()

    var searchActive by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { viewModel.clearSearch() } }

    Scaffold(
        topBar = {
            JtrSearchableTopAppBar(
                title = categoryName.ifBlank {
                    stringResource(R.string.category_detail_add_existing)
                },
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
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Button(
                    onClick = { viewModel.confirm(onNavigateBack) },
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_count, selectedIds.size))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (candidates.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonSearch, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.category_detail_no_candidates),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Liste compacte cochable — la structure native de l'Accueil.
                PersonListContent(
                    viewMode = JtrViewMode.LIST,
                    persons = candidates,
                    selectedIds = selectedIds,
                    isSelectionMode = true,
                    onClick = { viewModel.toggleSelection(it.id) },
                    onLongClick = { viewModel.toggleSelection(it.id) }
                )
            }
        }
    }
}
