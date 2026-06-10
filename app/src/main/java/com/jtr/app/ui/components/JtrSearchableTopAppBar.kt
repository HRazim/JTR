package com.jtr.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jtr.app.R

/**
 * Modes d'affichage commutables des listes principales (Accueil, Catégories,
 * Dossiers, détail de catégorie). Persistés dans `jtr_prefs` par écran.
 */
enum class JtrViewMode {
    /** Liste compacte : avatar + nom uniquement. */
    LIST,

    /** Grands carrés tactiles (optimisés pour le drag & drop). */
    GRID,

    /** Liste large façon « Notion » : informations clés sous le nom. */
    DETAIL;

    companion object {
        /** Restaure un mode persisté ; [default] si la valeur est absente/inconnue. */
        fun fromPref(value: String?, default: JtrViewMode): JtrViewMode =
            entries.firstOrNull { it.name == value } ?: default
    }
}

/** Une option de tri du menu 3 points (libellé localisé + état + action). */
data class JtrSortOption(
    @StringRes val labelRes: Int,
    val selected: Boolean,
    val onSelect: () -> Unit
)

/**
 * TopAppBar réutilisable avec recherche intégrée (« JtrSearchableTopAppBar ») :
 *
 * - Mode normal : titre + [navigationIcon] + loupe (placée À GAUCHE des [actions]
 *   fournies par l'écran : « + », menu 3 points…).
 * - Mode recherche : la barre se TRANSFORME en champ de saisie (focus automatique),
 *   flèche retour pour fermer (et effacer la query), « X » pour vider le texte.
 *
 * La query vient du ViewModel (UDF) ; l'état ouvert/fermé est local à l'écran.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JtrSearchableTopAppBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    searchPlaceholder: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    if (searchActive) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(searchPlaceholder) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            },
            navigationIcon = {
                // Fermer la recherche = effacer la query (aucun filtre fantôme persistant).
                IconButton(onClick = {
                    onSearchQueryChange("")
                    onSearchActiveChange(false)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back))
                }
            },
            actions = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear,
                            contentDescription = stringResource(R.string.common_clear))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    } else {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            actions = {
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search))
                }
                actions()
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

/** Entrées du sélecteur de mode d'affichage (icône + libellé localisé). */
private fun viewModeEntries() = listOf(
    Triple(JtrViewMode.LIST, R.string.view_mode_list, Icons.AutoMirrored.Filled.ViewList),
    Triple(JtrViewMode.GRID, R.string.view_mode_grid, Icons.Default.GridView),
    Triple(JtrViewMode.DETAIL, R.string.view_mode_detail, Icons.Default.ViewAgenda)
)

/**
 * Menu « 3 points » harmonisé — SEUL point d'accès au Tri et à l'Affichage
 * (v5.3.1 : header minimaliste Loupe → « + » → 3 points sur TOUS les écrans :
 * Accueil, Catégories, Dossiers, détail de catégorie).
 * Deux sous-sections — Tri ([sortOptions], fournies par l'écran, coche sur
 * l'option active) puis Affichage (LIST / GRID / DETAIL). [extraContent] permet
 * d'ajouter des actions propres à l'écran (sauvegarde, corbeille…) sous un
 * séparateur.
 */
@Composable
fun JtrOverflowMenu(
    sortOptions: List<JtrSortOption>,
    viewMode: JtrViewMode,
    onViewModeChange: (JtrViewMode) -> Unit,
    extraContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.common_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
            MenuSectionLabel(stringResource(R.string.sort_title))
            sortOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = { SelectedCheckIcon(option.selected) },
                    onClick = { option.onSelect(); dismiss() }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            MenuSectionLabel(stringResource(R.string.view_mode_title))
            viewModeEntries().forEach { (mode, labelRes, icon) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    leadingIcon = { SelectedCheckIcon(viewMode == mode) },
                    trailingIcon = { Icon(icon, contentDescription = null) },
                    onClick = { onViewModeChange(mode); dismiss() }
                )
            }

            extraContent(dismiss)
        }
    }
}

/** En-tête discret d'une sous-section du menu (Tri / Affichage). */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** Coche d'option sélectionnée (espace réservé sinon, pour aligner les libellés). */
@Composable
private fun SelectedCheckIcon(selected: Boolean) {
    if (selected) {
        Icon(Icons.Default.Check, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary)
    } else {
        Spacer(Modifier.size(24.dp))
    }
}
