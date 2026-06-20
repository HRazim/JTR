package com.jtr.app.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.CategoryGroup
import com.jtr.app.ui.category.AddCategoryDialog
import com.jtr.app.ui.category.rememberCategoryColor

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
 */
private fun buildCategoryPickerRows(
    categories: List<Category>,
    groups: List<CategoryGroup>
): List<CategoryPickerRow> {
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
 * Sélecteur Material 3 « Gérer les catégories » d'une personne (v7.1.5).
 *
 * - Liste TOUTES les catégories existantes, cases à cocher multiples, cases pré-cochées
 *   pour celles déjà associées ; la hiérarchie dossiers/sous-groupes est présentée de
 *   façon cohérente avec l'écran Catégories.
 * - Cocher/décocher appelle [onToggle] → persistance immédiate (auto-save) ; la fiche
 *   se met à jour de façon réactive.
 * - « + Nouvelle catégorie » réutilise EXACTEMENT le formulaire de création de l'écran
 *   Catégories ([AddCategoryDialog]) ; à la confirmation, [onCreateCategory] crée la
 *   catégorie ET y rattache la personne (auto-sélection), et le sélecteur reste ouvert.
 *
 * Présentation pure : les données et callbacks sont fournis par l'appelant (UDF) ;
 * aucune logique métier ici.
 */
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
    val rows = remember(categories, groups) { buildCategoryPickerRows(categories, groups) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.person_categories_dialog_title)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.person_categories_empty),
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
                // Création rapide — réutilise le formulaire de l'écran Catégories.
                item {
                    TextButton(
                        onClick = { showAddCategory = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.person_category_new))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        }
    )

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
        // Le rôle/clic est porté par la Row → la case ne re-gère pas l'événement.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
