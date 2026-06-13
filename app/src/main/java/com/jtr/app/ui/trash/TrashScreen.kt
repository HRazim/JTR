package com.jtr.app.ui.trash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onNavigateBack: () -> Unit,
    vm: TrashViewModel = viewModel()
) {
    val categoryGroups by vm.deletedCategoryGroups.collectAsState()
    val orphanPersons by vm.deletedOrphanPersons.collectAsState()
    var showEmptyDialog by remember { mutableStateOf(false) }

    val totalPersonCount = orphanPersons.size + categoryGroups.sumOf { it.persons.size }
    val isEmpty = categoryGroups.isEmpty() && orphanPersons.isEmpty()

    if (showEmptyDialog) {
        EmptyTrashDialog(
            categoryCount = categoryGroups.size,
            personCount = totalPersonCount,
            onConfirm = { vm.emptyTrash(); showEmptyDialog = false },
            onDismiss = { showEmptyDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (!isEmpty) {
                        IconButton(onClick = { showEmptyDialog = true }) {
                            Icon(Icons.Default.DeleteForever,
                                contentDescription = stringResource(R.string.trash_empty_cd),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.trash_auto_purge_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (isEmpty) {
                TrashEmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 6.dp)
                ) {

                    if (categoryGroups.isNotEmpty()) {
                        item {
                            TrashSectionHeader(
                                icon = Icons.Default.Folder,
                                title = stringResource(R.string.trash_section_categories),
                                count = categoryGroups.size
                            )
                        }
                        items(categoryGroups, key = { it.category.id }) { group ->
                            DeletedCategoryGroupCard(
                                group = group,
                                daysUntilPurge = vm.daysUntilPurge(group.category),
                                onRestoreGroup = { vm.restoreCategoryGroup(group.category.id) },
                                onDeleteGroup = { vm.hardDeleteCategoryGroup(group.category.id) },
                                onRestorePerson = { vm.restorePersonFromGroup(it) },
                                onDeletePerson = { vm.hardDeletePersonFromGroup(it) },
                                daysUntilPurgePerson = { vm.daysUntilPurge(it) }
                            )
                        }
                    }

                    if (orphanPersons.isNotEmpty()) {
                        item {
                            TrashSectionHeader(
                                icon = Icons.Default.Person,
                                title = stringResource(R.string.trash_section_contacts),
                                count = orphanPersons.size
                            )
                        }
                        items(orphanPersons, key = { it.id }) { person ->
                            // Carte par contact : cohérence visuelle stricte avec les
                            // cartes de dossiers (mêmes coins, mêmes marges).
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                TrashPersonRow(
                                    person = person,
                                    daysUntilPurge = vm.daysUntilPurge(person),
                                    onRestore = { vm.restoreOrphan(person.id) },
                                    onDelete = { vm.hardDeleteOrphan(person.id) },
                                    indented = false
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }

                Surface(shadowElevation = 4.dp) {
                    Button(
                        onClick = { showEmptyDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.trash_btn_empty))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DeletedCategoryGroupCard(
    group: DeletedCategoryGroup,
    daysUntilPurge: Int,
    onRestoreGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onRestorePerson: (String) -> Unit,
    onDeletePerson: (String) -> Unit,
    daysUntilPurgePerson: (Person) -> Int
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }

    if (showDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text(stringResource(R.string.trash_delete_dialog_title)) },
            text = {
                val n = group.persons.size
                if (n > 0)
                    Text(stringResource(R.string.trash_delete_category_with_contacts,
                        group.category.name, n))
                else
                    Text(stringResource(R.string.trash_delete_category_empty,
                        group.category.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteGroup(); showDeleteGroupDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            try { Color(android.graphics.Color.parseColor(group.category.color)) }
                            catch (e: Exception) { Color(0xFF2E86C1) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val purgeColor = if (daysUntilPurge <= 3)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    val purgeText = when (daysUntilPurge) {
                        0    -> stringResource(R.string.trash_group_purge_imminent)
                        1    -> stringResource(R.string.trash_group_purge_1_day)
                        else -> stringResource(R.string.trash_group_purge_days, daysUntilPurge)
                    }
                    Text(
                        text = stringResource(R.string.trash_group_status,
                            group.persons.size, purgeText),
                        style = MaterialTheme.typography.labelSmall,
                        color = purgeColor
                    )
                }
                IconButton(onClick = onRestoreGroup) {
                    Icon(Icons.Default.Restore,
                        contentDescription = stringResource(R.string.trash_restore_all_cd),
                        tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showDeleteGroupDialog = true }) {
                    Icon(Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.trash_delete_permanently_cd),
                        tint = MaterialTheme.colorScheme.error)
                }
                if (group.persons.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) stringResource(R.string.trash_collapse_cd)
                                                 else stringResource(R.string.trash_expand_cd)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider()
                    group.persons.forEach { person ->
                        TrashPersonRow(
                            person = person,
                            daysUntilPurge = daysUntilPurgePerson(person),
                            onRestore = { onRestorePerson(person.id) },
                            onDelete = { onDeletePerson(person.id) },
                            indented = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashPersonRow(
    person: Person,
    daysUntilPurge: Int,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    indented: Boolean
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.trash_delete_dialog_title)) },
            text = { Text(stringResource(R.string.trash_delete_person_text, person.fullName)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    ListItem(
        modifier = Modifier.padding(start = if (indented) 16.dp else 0.dp),
        // Conteneur transparent : la carte parente (dossier ou contact orphelin)
        // fournit le fond, on évite ainsi la double surface.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.initials.ifEmpty { "?" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        headlineContent = {
            Text(person.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
        },
        supportingContent = {
            val purgeColor = if (daysUntilPurge <= 3)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            Column {
                person.deletedAt?.let { ts ->
                    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ts))
                    Text(stringResource(R.string.trash_deleted_at, date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text = when (daysUntilPurge) {
                        0    -> stringResource(R.string.trash_purge_imminent)
                        1    -> stringResource(R.string.trash_purge_1_day)
                        else -> stringResource(R.string.trash_purge_days, daysUntilPurge)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = purgeColor
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Restore,
                        contentDescription = stringResource(R.string.trash_restore_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.trash_delete_permanently_cd),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp))
                }
            }
        }
    )
}

@Composable
private fun EmptyTrashDialog(
    categoryCount: Int,
    personCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val separatorAnd = stringResource(R.string.separator_and)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
        title = { Text(stringResource(R.string.trash_empty_dialog_title)) },
        text = {
            val parts = buildList {
                if (categoryCount > 0)
                    add(stringResource(R.string.trash_empty_dialog_part_categories, categoryCount))
                if (personCount > 0)
                    add(stringResource(R.string.trash_empty_dialog_part_contacts, personCount))
            }
            Text(stringResource(R.string.trash_empty_dialog_text, parts.joinToString(separatorAnd)))
        },
        confirmButton = {
            TextButton(onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.trash_empty_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun TrashEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.trash_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.trash_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
