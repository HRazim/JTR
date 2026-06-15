package com.jtr.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

/** Sens de tri (croissant / décroissant) appliqué au critère sélectionné. */
enum class JtrSortDirection { ASC, DESC }

/**
 * Critère de tri à DEUX AXES (v6.2.6) : Nom · Date de modification · Date de création.
 * [selected] = critère actif ; [direction] = son sens courant ; [onClick] sélectionne
 * le critère (sens par défaut) ou, s'il est déjà actif, INVERSE le sens.
 */
data class JtrSortCriterion(
    @StringRes val labelRes: Int,
    val selected: Boolean,
    val direction: JtrSortDirection,
    val onClick: () -> Unit
)

/**
 * Fabrique générique d'un critère à deux sens, partagée par l'Accueil et les
 * Catégories (mêmes règles partout). [asc]/[desc] = les deux valeurs d'enum du
 * critère ; [defaultDescending] = sens appliqué quand on sélectionne ce critère.
 * Taper le critère déjà actif inverse le sens ; taper un autre le sélectionne au
 * sens par défaut.
 */
fun <T> jtrSortCriterion(
    @StringRes labelRes: Int,
    current: T,
    asc: T,
    desc: T,
    defaultDescending: Boolean,
    onSelect: (T) -> Unit
): JtrSortCriterion {
    val selected = current == asc || current == desc
    val direction = when {
        current == asc -> JtrSortDirection.ASC
        current == desc -> JtrSortDirection.DESC
        defaultDescending -> JtrSortDirection.DESC
        else -> JtrSortDirection.ASC
    }
    return JtrSortCriterion(labelRes, selected, direction) {
        val next = when {
            !selected -> if (defaultDescending) desc else asc
            current == asc -> desc
            else -> asc
        }
        onSelect(next)
    }
}

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
    // Ordre demandé (Accueil ET Catégories) : Liste · Détails · Grille.
    Triple(JtrViewMode.LIST, R.string.view_mode_list, Icons.AutoMirrored.Filled.ViewList),
    Triple(JtrViewMode.DETAIL, R.string.view_mode_detail, Icons.Default.ViewAgenda),
    Triple(JtrViewMode.GRID, R.string.view_mode_grid, Icons.Default.GridView)
)

/**
 * Menu « 3 points » harmonisé — SEUL point d'accès au Tri et à l'Affichage, présentés
 * en FEUILLES MONTANTES (`ModalBottomSheet`, façon Notion). Deux entrées « Tri » et
 * « Affichage » ouvrent chacune sa feuille ; [extraContent] ajoute des actions propres
 * à l'écran sous ces entrées. Composant FACTORISÉ, réutilisé tel quel sur l'Accueil,
 * les Catégories, les Dossiers et le détail de catégorie (même mécanisme, mêmes
 * options, mêmes couleurs de thème partout).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JtrOverflowMenu(
    sortCriteria: List<JtrSortCriterion>,
    viewMode: JtrViewMode,
    onViewModeChange: (JtrViewMode) -> Unit,
    extraContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showViewSheet by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.common_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = dismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_title)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                onClick = { dismiss(); showSortSheet = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_mode_title)) },
                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                onClick = { dismiss(); showViewSheet = true }
            )
            extraContent(dismiss)
        }
    }

    if (showSortSheet) {
        JtrOptionsSheet(
            title = stringResource(R.string.sort_title),
            onDismiss = { showSortSheet = false }
        ) {
            // La feuille reste OUVERTE au clic : on sélectionne un critère puis on
            // peut le re-taper pour inverser le sens, sans la rouvrir (fermeture au scrim).
            sortCriteria.forEach { criterion ->
                JtrSheetCriterionRow(criterion)
            }
        }
    }

    if (showViewSheet) {
        JtrOptionsSheet(
            title = stringResource(R.string.view_mode_title),
            onDismiss = { showViewSheet = false }
        ) {
            viewModeEntries().forEach { (mode, labelRes, icon) ->
                JtrSheetOption(
                    label = stringResource(labelRes),
                    selected = viewMode == mode,
                    leadingIcon = icon,
                    onClick = { onViewModeChange(mode); showViewSheet = false }
                )
            }
        }
    }
}

/** Feuille montante générique (« Tri » / « Affichage ») : titre + liste d'options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JtrOptionsSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        content()
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Ligne d'option d'une feuille : (icône) + libellé + coche si sélectionnée. */
@Composable
private fun JtrSheetOption(
    label: String,
    selected: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Ligne d'un critère de tri (modèle à deux axes, v6.2.6) : libellé du critère et,
 * UNIQUEMENT s'il est sélectionné, le sens courant (libellé « Croissant / Décroissant »
 * + chevron ↑/↓). Un tap sélectionne le critère ou inverse son sens (cf. [jtrSortCriterion]).
 */
@Composable
private fun JtrSheetCriterionRow(criterion: JtrSortCriterion) {
    val selected = criterion.selected
    val ascending = criterion.direction == JtrSortDirection.ASC
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = criterion.onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(criterion.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            val dirLabel = stringResource(
                if (ascending) R.string.sort_direction_ascending
                else R.string.sort_direction_descending
            )
            Text(text = dirLabel, style = MaterialTheme.typography.labelMedium, color = accent)
            Spacer(Modifier.size(6.dp))
            Icon(
                if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = dirLabel,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
