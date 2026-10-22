package com.jtr.app.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.person.CategoryCheckRow
import com.jtr.app.ui.person.CategoryPickerRow
import com.jtr.app.ui.person.FolderHeaderRow
import com.jtr.app.ui.person.buildCategoryPickerRows
import com.jtr.app.ui.person.pickerRowKey

/**
 * v7.1.80 — sélecteur de catégorie PARTAGÉ au langage moderne de v7.1.77 (vignettes
 * d'images / initiale+teinte+anneau, dossiers en bandeaux dépliables aux enfants
 * indentés, recherche repliable, dividers légers), pour les flux qui n'en étaient
 * pas encore :
 *
 * - **Sélection SIMPLE** ([multiSelect] = false) : taper une catégorie LA CHOISIT
 *   ([onPickSingle]) — le « Move » d'un membre depuis le détail de catégorie, où la
 *   SOURCE est retirée des cibles via [excludeCategoryIds].
 * - **Sélection MULTIPLE** ([multiSelect] = true) : cases + bouton de confirmation
 *   pleine largeur ([confirmLabel] → [onConfirmMulti]) — le rattachement de catégories
 *   existantes à un dossier.
 *
 * RÉUTILISE le moteur et les lignes du sélecteur validé de la fiche contact
 * ([com.jtr.app.ui.person.ManageCategoriesDialog]) : [buildCategoryPickerRows]
 * (avec exclusions et élagage des dossiers sans candidate), [FolderHeaderRow],
 * [CategoryCheckRow] (mode simple = `checked == null`). Aucun fork de rendu.
 *
 * [onCreateCategory] non nul ⇒ un « + » dans l'en-tête ouvre la NOUVELLE feuille de
 * création ([AddCategoryDialog], image incluse) ; à la confirmation l'appelant crée
 * ET applique (ex. créer puis y déplacer les contacts). Présentation pure (UDF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String,
    categories: List<Category>,
    groups: List<CategoryGroup>,
    excludeCategoryIds: Set<String> = emptySet(),
    multiSelect: Boolean = false,
    confirmLabel: String = "",
    onPickSingle: (categoryId: String) -> Unit = {},
    onConfirmMulti: (categoryIds: List<String>) -> Unit = {},
    onCreateCategory: ((name: String, color: String, imagePath: String?) -> Unit)? = null,
    // v7.1.83 — mode DOSSIER-CIBLE : ce sont les DOSSIERS qu'on choisit (déplacer des
    // catégories dans un groupe), en sélection simple. Les catégories ne sont alors pas
    // listées (bruit : on ne peut pas déplacer dans une catégorie), la hiérarchie des
    // sous-dossiers reste navigable (chevron) et [excludeGroupIds] retire les cibles
    // invalides (dossier courant, dossiers en cours de déplacement).
    folderTargets: Boolean = false,
    excludeGroupIds: Set<Long> = emptySet(),
    onPickFolder: (groupId: Long) -> Unit = {},
    onDismiss: () -> Unit
) {
    var showAddCategory by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Repli des dossiers : mêmes règles que le sélecteur de la fiche (tout replié au
    // départ, set IMMUABLE pour servir de clé de remember — cf. ManageCategoriesDialog).
    var expandedFolders by remember { mutableStateOf(emptySet<Long>()) }
    // Sélection multiple (set immuable, même motif).
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val rows = remember(
        categories, groups, query, expandedFolders, excludeCategoryIds, folderTargets, excludeGroupIds
    ) {
        buildCategoryPickerRows(
            // Mode dossier-cible : aucune catégorie listée, que l'arbre des dossiers.
            categories = if (folderTargets) emptyList() else categories,
            groups = groups,
            query = query,
            expandedFolders = expandedFolders,
            excludeCategoryIds = excludeCategoryIds,
            // Un picker de CIBLES n'affiche pas de bandeau sans candidate (ex. le
            // dossier où l'on est déjà, ou un dossier vidé par l'exclusion) — mais en
            // mode dossier-cible, un dossier VIDE reste une cible légitime.
            pruneEmptyFolders = !folderTargets,
            excludeGroupIds = excludeGroupIds
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding()
        ) {
            // ── En-tête commutable (titre ↔ recherche), même grammaire que le sélecteur.
            if (searchActive) {
                val focusRequester = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fermer la recherche EFFACE la query : jamais de filtre fantôme.
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
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        if (multiSelect && selected.isNotEmpty()) {
                            Text(
                                pluralStringResource(
                                    R.plurals.categories_selection_count,
                                    selected.size, selected.size
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
                    if (onCreateCategory != null) {
                        IconButton(onClick = { showAddCategory = true }) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = stringResource(R.string.person_category_new),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(
                                if (query.isBlank()) R.string.person_categories_empty
                                else R.string.person_categories_no_results
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                itemsIndexed(rows, key = { _, row -> pickerRowKey(row) }) { index, row ->
                    when (row) {
                        is CategoryPickerRow.Folder -> FolderHeaderRow(
                            group = row.group,
                            indent = row.indent,
                            expanded = row.expanded,
                            hasChildren = row.hasChildren,
                            onToggleExpand = {
                                val id = row.group.id
                                expandedFolders =
                                    if (id in expandedFolders) expandedFolders - id
                                    else expandedFolders + id
                            },
                            // Mode dossier-cible : taper le bandeau CHOISIT ce dossier.
                            onSelect = if (folderTargets) {
                                { onPickFolder(row.group.id) }
                            } else null
                        )
                        is CategoryPickerRow.Item -> CategoryCheckRow(
                            category = row.category,
                            indent = row.indent,
                            // Simple : pas de case, le tap CHOISIT (checked == null).
                            checked = if (multiSelect) row.category.id in selected else null,
                            onCheckedChange = { nowChecked ->
                                val id = row.category.id
                                if (multiSelect) {
                                    selected = if (nowChecked) selected + id else selected - id
                                } else {
                                    onPickSingle(id)
                                }
                            },
                            showDivider = rows.getOrNull(index + 1) is CategoryPickerRow.Item
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (multiSelect) {
                Button(
                    onClick = { onConfirmMulti(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(confirmLabel) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAddCategory && onCreateCategory != null) {
        AddCategoryDialog(
            onConfirm = { name, color, imagePath ->
                onCreateCategory(name, color, imagePath)
                showAddCategory = false
            },
            onDismiss = { showAddCategory = false }
        )
    }
}
