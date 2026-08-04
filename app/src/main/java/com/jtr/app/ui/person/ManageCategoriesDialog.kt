package com.jtr.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.category.AddCategoryDialog
import com.jtr.app.ui.category.rememberCategoryColor
import com.jtr.app.ui.components.FavoriteStar
import com.jtr.app.utils.matchesAllTokens
import com.jtr.app.utils.searchTokens

/**
 * Une ligne du sélecteur de catégories, à plat avec son niveau d'indentation pour
 * refléter la hiérarchie récursive (dossiers → sous-dossiers → catégories).
 */
private sealed interface CategoryPickerRow {
    val indent: Int

    /** En-tête de dossier (NON cochable : une personne appartient à des catégories, pas à un dossier). */
    data class Folder(val group: CategoryGroup, override val indent: Int) : CategoryPickerRow

    /** Catégorie cochable. */
    data class Item(val category: Category, override val indent: Int) : CategoryPickerRow
}

/**
 * Aplatit catégories + dossiers en lignes ordonnées et indentées, dans les MÊMES
 * sémantiques d'appartenance que l'écran Catégories : dossiers de premier niveau (avec
 * leurs catégories puis leurs sous-dossiers, récursivement), puis catégories libres.
 *
 * v7.1.50 — [query] non vide ⇒ **liste PLATE filtrée** (ni en-tête de dossier ni indentation),
 * comme l'écran Catégories : pendant une recherche, l'arbre n'apporte rien et fragmenterait
 * les résultats. Requête vide ⇒ arbre groupé, strictement inchangé.
 */
private fun buildCategoryPickerRows(
    categories: List<Category>,
    groups: List<CategoryGroup>,
    query: String
): List<CategoryPickerRow> {
    // Moteur de recherche CENTRAL (multi-mots, insensible casse/accents, sûr en arabe).
    val tokens = query.searchTokens()
    if (tokens.isNotEmpty()) {
        return categories
            .filter { it.name.matchesAllTokens(tokens) }
            .map { CategoryPickerRow.Item(it, 0) }
    }

    val rows = mutableListOf<CategoryPickerRow>()
    val catsByGroup = categories.filter { it.parentGroupId != null }.groupBy { it.parentGroupId!! }
    val subByParent = groups.filter { it.parentGroupId != null }.groupBy { it.parentGroupId!! }

    fun emitGroup(group: CategoryGroup, indent: Int) {
        rows += CategoryPickerRow.Folder(group, indent)
        catsByGroup[group.id].orEmpty().forEach { rows += CategoryPickerRow.Item(it, indent + 1) }
        subByParent[group.id].orEmpty().forEach { emitGroup(it, indent + 1) }
    }

    groups.filter { it.parentGroupId == null }.forEach { emitGroup(it, 0) }
    categories.filter { it.parentGroupId == null }.forEach { rows += CategoryPickerRow.Item(it, 0) }
    return rows
}

/**
 * Sélecteur « Gérer les catégories » d'une personne (v7.1.5, refondu en v7.1.50).
 *
 * - **Feuille montante** ([ModalBottomSheet], comme la galerie et les feuilles Tri/Affichage)
 *   plutôt qu'un `AlertDialog` centré : plus de hauteur pour une longue liste, et le contenu
 *   REMONTE au-dessus du clavier — un dialogue centré, lui, se fait recouvrir (et ses insets
 *   valent 0, cf. v7.1.49).
 * - **Recherche** (v7.1.50) : **loupe repliable** dans l'en-tête, façon [JtrSearchableTopAppBar]
 *   (la ligne de titre se transforme en champ, focus automatique ; le retour replie ET efface).
 *   Repliée par défaut : rien ne consomme de hauteur quand on a peu de catégories. État et
 *   requête PUREMENT LOCAUX — aucun repository, aucun ViewModel, aucune persistance touchés.
 * - Liste TOUTES les catégories existantes, cases à cocher multiples, cases pré-cochées
 *   pour celles déjà associées ; la hiérarchie dossiers/sous-groupes est présentée de
 *   façon cohérente avec l'écran Catégories (hors recherche).
 * - Cocher/décocher appelle [onToggle] → persistance immédiate (auto-save) ; la fiche
 *   se met à jour de façon réactive.
 * - « + » dans l'EN-TÊTE (v7.1.50 : auparavant dernier item de la liste, donc hors d'atteinte
 *   sans scroller) réutilise EXACTEMENT le formulaire de création de l'écran Catégories
 *   ([AddCategoryDialog]) ; à la confirmation, [onCreateCategory] crée la catégorie ET y
 *   rattache la personne (auto-sélection), et le sélecteur reste ouvert.
 *
 * Présentation pure : les données et callbacks sont fournis par l'appelant (UDF) ;
 * aucune logique métier ici. Le nom historique est conservé (un seul appelant).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesDialog(
    categories: List<Category>,
    groups: List<CategoryGroup>,
    memberIds: Set<String>,
    onToggle: (categoryId: String, inCategory: Boolean) -> Unit,
    onCreateCategory: (name: String, color: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddCategory by remember { mutableStateOf(false) }
    // Recherche REPLIÉE par défaut : une simple loupe dans l'en-tête, aucun champ ne
    // consomme de hauteur tant qu'on ne le demande pas.
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val rows = remember(categories, groups, query) {
        buildCategoryPickerRows(categories, groups, query)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // Le clavier pousse le contenu vers le haut : la feuille étant ancrée en
                // bas, le champ de recherche et les premières lignes restent visibles.
                .imePadding()
        ) {
            // ── En-tête COMMUTABLE (v7.1.50), même grammaire que JtrSearchableTopAppBar :
            // la ligne de titre se TRANSFORME en champ de saisie. Aucun champ permanent →
            // avec peu de catégories, la feuille reste minimale.
            if (searchActive) {
                val focusRequester = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fermer la recherche EFFACE la query : jamais de filtre fantôme
                    // invisible au retour de l'arbre.
                    IconButton(onClick = { query = ""; searchActive = false }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.person_categories_search_placeholder)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        trailingIcon = if (query.isNotEmpty()) {
                            {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.common_clear)
                                    )
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder, null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.person_categories_dialog_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        // Combien de catégories pour ce contact, d'un coup d'œil. Le pluriel
                        // « N sélectionnée(s) » est déjà accordé au FÉMININ (catégories) dans
                        // les 13 langues → aucune nouvelle ressource.
                        if (memberIds.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.categories_selection_count,
                                    memberIds.size, memberIds.size
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { searchActive = true }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showAddCategory = true }) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.person_category_new),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // `fill = false` : la liste prend la place RESTANTE (donc se réduit
                    // quand le clavier monte, sans jamais rogner l'en-tête ni « Terminé »),
                    // et pas plus que son contenu — la feuille reste courte si peu de
                    // catégories. Le plafond garde la feuille sous la moitié d'écran.
                    .weight(1f, fill = false)
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(
                                // Catalogue vide vs recherche infructueuse : deux messages,
                                // « créez-en une » n'aurait aucun sens en filtrant.
                                if (query.isBlank()) R.string.person_categories_empty
                                else R.string.person_categories_no_results
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                items(rows, key = ::pickerRowKey) { row ->
                    when (row) {
                        is CategoryPickerRow.Folder -> FolderHeaderRow(row.group, row.indent)
                        is CategoryPickerRow.Item -> CategoryCheckRow(
                            category = row.category,
                            indent = row.indent,
                            checked = row.category.id in memberIds,
                            onCheckedChange = { onToggle(row.category.id, it) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
            }
        }
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onConfirm = { name, color, imagePath ->
                onCreateCategory(name, color, imagePath)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false }
        )
    }
}

private fun pickerRowKey(row: CategoryPickerRow): String = when (row) {
    is CategoryPickerRow.Folder -> "g_${row.group.id}"
    is CategoryPickerRow.Item -> "c_${row.category.id}"
}

@Composable
private fun FolderHeaderRow(group: CategoryGroup, indent: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Folder, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            group.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryCheckRow(
    category: Category,
    indent: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // Toute la ligne est cochable (accessibilité : rôle Checkbox + libellé = nom).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(start = (indent * 16).dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(rememberCategoryColor(category.color))
        )
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        // Étoile (v7.1.50) : les favoris remontent en tête (ORDER BY isFavorite DESC côté
        // DAO) — sans repère visuel, l'ordre paraissait arbitraire.
        if (category.isFavorite) {
            FavoriteStar(size = 16.dp)
        }
        // Le rôle/clic est porté par la Row → la case ne re-gère pas l'événement.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}