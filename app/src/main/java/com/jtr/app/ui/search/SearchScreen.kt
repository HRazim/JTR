package com.jtr.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.domain.model.Person
import com.jtr.app.ui.category.rememberCategoryColor
import com.jtr.app.ui.home.PersonAvatar
import com.jtr.app.ui.home.PersonListContent
import com.jtr.app.ui.navigation.nestedScreenContentInsets

/**
 * Écran de RECHERCHE dédié (v7.1.69), ouvert depuis la loupe de l'accueil.
 *
 * - Champ de recherche en tête, FOCUS + clavier affichés à l'ouverture.
 * - Requête vide → section « Récents » (rangée stories), inchangée (v7.1.69).
 * - En cours de frappe → résultats HARMONISÉS (v7.1.73) en sections, chacune seulement si
 *   non vide, dans l'ordre Contacts → Catégories → Dossiers (moteur central unique).
 * - Taps : contact → fiche ; catégorie → détail de la catégorie ; dossier → intérieur du
 *   dossier ; flèche retour → accueil.
 *
 * Réutilise [PersonListContent] (rendu Liste/Détail/Grille — les sections Catégories/Dossiers
 * passent par son slot `footer`, donc tout défile ensemble) et [PersonAvatar] ; aucune logique
 * de recherche dupliquée (tout passe par [SearchViewModel] + `utils.matchesSearch`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit,
    onNavigateToGroup: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val categoryResults by viewModel.categoryResults.collectAsStateWithLifecycle()
    val groupResults by viewModel.groupResults.collectAsStateWithLifecycle()
    val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val socialLinksMap by viewModel.socialLinksMap.collectAsStateWithLifecycle()
    // v7.1.85/87 — périmètre : nom de la catégorie OU du dossier d'où l'on vient.
    val scopedName by viewModel.scopedCategoryName.collectAsStateWithLifecycle()
    val scopedFolderName by viewModel.scopedGroupName.collectAsStateWithLifecycle()
    val isScoped = viewModel.isScoped

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Ouverture de l'écran → focus sur le champ → clavier affiché immédiatement.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Ferme le clavier AVANT de naviguer (transition non saccadée) — même règle pour les
    // trois destinations : fiche, catégorie, dossier.
    val openPerson: (String) -> Unit = { id ->
        keyboardController?.hide()
        focusManager.clearFocus()
        onNavigateToPersonDetail(id)
    }
    val openCategory: (String) -> Unit = { id ->
        keyboardController?.hide()
        focusManager.clearFocus()
        onNavigateToCategory(id)
    }
    val openGroup: (Long) -> Unit = { id ->
        keyboardController?.hide()
        focusManager.clearFocus()
        onNavigateToGroup(id)
    }

    Scaffold(
        // Haut géré par la TopAppBar, bas absent (écran plein, hors onglets) ; on n'applique
        // au contenu que l'inset HORIZONTAL des barres système (paysage 3 boutons), règle centralisée.
        contentWindowInsets = nestedScreenContentInsets,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = {
                            Text(
                                // Scopé : le périmètre est NOMMÉ dans le champ lui-même
                                // (le slot « titre » de la barre porte déjà la saisie).
                                // Les deux périmètres s'excluent : au plus un nom est non vide.
                                if (scopedFolderName.isNotBlank())
                                    stringResource(R.string.search_in_folder, scopedFolderName)
                                else if (scopedName.isNotBlank())
                                    stringResource(R.string.search_in_category, scopedName)
                                // Hors périmètre, cette page remonte AUSSI les catégories et
                                // les dossiers en sections croisées (v7.1.73) ; le libellé
                                // reste malgré tout le simple « contact », par préférence
                                // explicite — nommer les trois alourdissait le champ.
                                else stringResource(R.string.home_search_placeholder)
                            )
                        },
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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
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
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isScoped) {
                // v7.1.85 — PÉRIMÈTRE CATÉGORIE : une seule liste, celle des membres,
                // filtrée en direct. Ni récents ni sections Catégories/Dossiers (globaux,
                // hors périmètre) ; `results` vaut déjà les membres quand la requête est vide.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (results.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        PersonListContent(
                            viewMode = viewModel.viewMode,
                            persons = results,
                            socialLinksMap = socialLinksMap,
                            onClick = { openPerson(it.id) }
                        )
                    }
                }
            } else if (query.isBlank()) {
                // ÉTAT VIDE : UNIQUEMENT la rangée « Récents » (stories), aucune liste de contacts.
                // Rendue en enfant DIRECT de la Column (pas via le slot `header` de PersonListContent) :
                // sa visibilité réagit de façon fiable à la première émission du Flow `recents`.
                // Recents vide → seul le champ de recherche reste visible.
                if (recents.isNotEmpty()) {
                    // v7.1.69 — un peu d'air entre le header et la rangée stories (sinon « collée »).
                    Spacer(Modifier.height(16.dp))
                    RecentsRow(persons = recents, onPersonClick = openPerson)
                }
            } else {
                // REQUÊTE SAISIE : résultats HARMONISÉS (v7.1.73) — Contacts → Catégories →
                // Dossiers, chaque section seulement si non vide ; la rangée Récents disparaît.
                val hasAnyResult = results.isNotEmpty() ||
                    categoryResults.isNotEmpty() || groupResults.isNotEmpty()
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (!hasAnyResult) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Les contacts gardent leur rendu existant (Liste/Détail/Grille) ; les
                        // sections Catégories/Dossiers défilent À LA SUITE via le slot `footer`.
                        PersonListContent(
                            viewMode = viewModel.viewMode,
                            persons = results,
                            socialLinksMap = socialLinksMap,
                            onClick = { openPerson(it.id) },
                            header = if (results.isEmpty()) null else {
                                { SearchSectionHeader(stringResource(R.string.search_section_contacts)) }
                            },
                            footer = if (categoryResults.isEmpty() && groupResults.isEmpty()) null else {
                                {
                                    SearchCrossSections(
                                        categories = categoryResults,
                                        counts = categoryCounts,
                                        groups = groupResults,
                                        onCategoryClick = openCategory,
                                        onGroupClick = openGroup
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Libellé « Récents » + [LazyRow] horizontale d'avatars (réutilise [PersonAvatar]).
 * Tap sur un avatar → ouverture de la fiche. Défilement horizontal (se miroite en RTL).
 * Chaque item porte un `contentDescription` = nom complet (TalkBack l'annonce comme bouton).
 * N'est rendue que lorsque [persons] est non vide (masquée sinon côté appelant).
 */
@Composable
private fun RecentsRow(
    persons: List<Person>,
    onPersonClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.recents_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(items = persons, key = { it.id }) { person ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPersonClick(person.id) }
                        .padding(vertical = 4.dp)
                        // TalkBack : annonce le nom une seule fois, comme un bouton.
                        .clearAndSetSemantics {
                            contentDescription = person.fullName
                            role = Role.Button
                        }
                ) {
                    PersonAvatar(person = person, size = 56.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = person.firstName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * En-tête de section DISCRET des résultats (Contacts / Catégories / Dossiers) — même style
 * que le libellé « Récents ». TalkBack le lit comme un simple texte avant les lignes.
 */
@Composable
private fun SearchSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
    )
}

/**
 * Sections Catégories puis Dossiers (v7.1.73), rendues dans le slot `footer` de
 * [PersonListContent] : elles défilent à la suite des contacts, chacune SEULEMENT si
 * non vide. Lignes alignées sur la grammaire des lignes compactes de contact (carte,
 * pastille 40 dp, nom). RTL : Row/padding en start/end → miroir automatique.
 */
@Composable
private fun SearchCrossSections(
    categories: List<Category>,
    counts: Map<String, Int>,
    groups: List<CategoryGroup>,
    onCategoryClick: (String) -> Unit,
    onGroupClick: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (categories.isNotEmpty()) {
            SearchSectionHeader(stringResource(R.string.search_section_categories))
            categories.forEach { category ->
                CategoryResultRow(
                    category = category,
                    memberCount = counts[category.id] ?: 0,
                    onClick = { onCategoryClick(category.id) }
                )
            }
        }
        if (groups.isNotEmpty()) {
            SearchSectionHeader(stringResource(R.string.search_section_folders))
            groups.forEach { group ->
                FolderResultRow(group = group, onClick = { onGroupClick(group.id) })
            }
        }
    }
}

/**
 * Ligne de résultat CATÉGORIE : pastille à la couleur d'accent (source unique
 * [rememberCategoryColor]) + nom + compteur de membres actifs (plurals existant).
 */
@Composable
private fun CategoryResultRow(
    category: Category,
    memberCount: Int,
    onClick: () -> Unit
) {
    val accent = rememberCategoryColor(category.color)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = pluralStringResource(
                    R.plurals.categories_person_count, memberCount, memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Ligne de résultat DOSSIER : pastille avec icône dossier + nom. */
@Composable
private fun FolderResultRow(
    group: CategoryGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
