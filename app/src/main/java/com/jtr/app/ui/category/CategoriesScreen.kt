package com.jtr.app.ui.category

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import com.jtr.app.ui.person.CropShape
import com.jtr.app.ui.person.ImageCropDialog
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Modes d'affichage de la liste des catégories. */
enum class CategoryViewMode { LIST, GRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (String) -> Unit = {},
    viewModel: CategoryViewModel = viewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val personCountByCategory by viewModel.personCountByCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<Category?>(null) }
    var pendingEditCategory by remember { mutableStateOf<Category?>(null) }
    var contextMenuCategory by remember { mutableStateOf<Category?>(null) }
    // Mode d'affichage persistant (survit aux rotations et à la mort du processus).
    var viewMode by rememberSaveable { mutableStateOf(CategoryViewMode.GRID) }

    contextMenuCategory?.let { category ->
        CategoryActionsDialog(
            category = category,
            onEdit = { pendingEditCategory = category; contextMenuCategory = null },
            onDelete = { pendingDeleteCategory = category; contextMenuCategory = null },
            onDismiss = { contextMenuCategory = null }
        )
    }

    pendingDeleteCategory?.let { category ->
        val count = personCountByCategory[category.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
            },
            title = { Text(stringResource(R.string.categories_delete_title, category.name)) },
            text = {
                if (count > 0) {
                    Text(stringResource(R.string.categories_delete_with_contacts, count))
                } else {
                    Text(stringResource(R.string.categories_delete_empty))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategoryWithCascade(category.id)
                        pendingDeleteCategory = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingEditCategory?.let { category ->
        EditCategoryDialog(
            category = category,
            onConfirm = { name, color, imagePath ->
                viewModel.updateCategory(category.copy(name = name, color = color, imagePath = imagePath))
                pendingEditCategory = null
            },
            onDismiss = { pendingEditCategory = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                actions = {
                    IconButton(onClick = {
                        viewMode = if (viewMode == CategoryViewMode.GRID)
                            CategoryViewMode.LIST else CategoryViewMode.GRID
                    }) {
                        if (viewMode == CategoryViewMode.GRID) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.categories_view_list_cd),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                Icons.Default.GridView,
                                contentDescription = stringResource(R.string.categories_view_grid_cd),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.categories_fab_add_cd))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.categories_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.categories_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Tri alphabétique A-Z (insensible à la casse), mémoïsé.
                val sortedCategories = remember(categories) {
                    categories.sortedBy { it.name.lowercase() }
                }
                when (viewMode) {
                    CategoryViewMode.GRID -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedCategories, key = { it.id }) { category ->
                            val count = personCountByCategory[category.id] ?: 0
                            CategoryGridTile(
                                category = category,
                                personCount = count,
                                onClick = { onCategoryClick(category.id) },
                                onLongClick = { contextMenuCategory = category },
                                onEdit = { pendingEditCategory = category },
                                onDelete = { pendingDeleteCategory = category }
                            )
                        }
                    }
                    CategoryViewMode.LIST -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedCategories, key = { it.id }) { category ->
                            val count = personCountByCategory[category.id] ?: 0
                            CategoryListRow(
                                category = category,
                                personCount = count,
                                onClick = { onCategoryClick(category.id) },
                                onLongClick = { contextMenuCategory = category },
                                onEdit = { pendingEditCategory = category },
                                onDelete = { pendingDeleteCategory = category }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onConfirm = { name, color ->
                viewModel.addCategory(name, color)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/**
 * Tuile de catégorie style « Galerie » : la photo de couverture remplit le fond,
 * le nom (et le nombre de contacts) est superposé en bas sur un dégradé sombre
 * pour rester lisible. Clic court → ouvre ; clic long → menu Modifier/Supprimer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryGridTile(
    category: Category,
    personCount: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val accent = remember(category.color) {
        try { Color(android.graphics.Color.parseColor(category.color)) }
        catch (e: Exception) { Color(0xFF2E86C1) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(accent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Fond : photo de couverture en grand format, sinon icône centrée.
        if (category.imagePath != null) {
            AsyncImage(
                model = category.imagePath,
                contentDescription = category.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(56.dp).align(Alignment.Center)
            )
        }

        // Léger voile en haut : assure la lisibilité du menu « 3 points ».
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent)
                    )
                )
        )

        // Dégradé sombre en bas pour la lisibilité du texte superposé.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        startY = 200f
                    )
                )
        )

        // Menu « 3 points » superposé en haut à droite, sur le voile.
        CategoryOverflowMenu(
            tint = Color.White,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2
            )
            if (personCount > 0) {
                Text(
                    text = stringResource(R.string.categories_person_count, personCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * Ligne de catégorie en mode Liste : avatar circulaire + nom + compteur, avec
 * le menu « 3 points » à droite. Clic court → ouvre ; clic long → menu d'actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryListRow(
    category: Category,
    personCount: Int = 0,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val accent = remember(category.color) {
        try { Color(android.graphics.Color.parseColor(category.color)) }
        catch (e: Exception) { Color(0xFF2E86C1) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                if (category.imagePath != null) {
                    AsyncImage(
                        model = category.imagePath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                if (personCount > 0) {
                    Text(
                        text = stringResource(R.string.categories_person_count, personCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            CategoryOverflowMenu(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onEdit = onEdit,
                onDelete = onDelete
            )
        }
    }
}

/**
 * Icône « 3 points » (More vert) + DropdownMenu Material 3 (Modifier / Supprimer).
 * Réutilisé par la tuile Grille et la ligne Liste.
 */
@Composable
private fun CategoryOverflowMenu(
    tint: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.common_more_actions),
                tint = tint
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_edit)) },
                onClick = { expanded = false; onEdit() },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                onClick = { expanded = false; onDelete() },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                }
            )
        }
    }
}

/**
 * Menu d'actions affiché sur clic long d'une tuile de catégorie.
 */
@Composable
private fun CategoryActionsDialog(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(category.name) },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.common_edit)) },
                    leadingContent = {
                        Icon(Icons.Default.Edit, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable(onClick = onEdit),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.common_delete)) },
                    leadingContent = {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable(onClick = onDelete),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun EditCategoryDialog(
    category: Category,
    onConfirm: (name: String, color: String, imagePath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(category.name) }
    val presetColors = listOf("#2E86C1", "#E74C3C", "#27AE60", "#F39C12", "#8E44AD", "#16A085")
    var selectedColor by remember { mutableStateOf(category.color) }
    var imagePath by remember { mutableStateOf(category.imagePath) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) pendingCropUri = uri
    }

    pendingCropUri?.let { uri ->
        ImageCropDialog(
            sourceUri = uri,
            cropShape = CropShape.RECTANGLE,
            onCropComplete = { croppedUri ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        copyCategoryPhotoToStorage(context, croppedUri)
                    }
                    if (path != null) imagePath = path
                }
                pendingCropUri = null
            },
            onDismiss = { pendingCropUri = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_edit_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            try { Color(android.graphics.Color.parseColor(selectedColor)) }
                            catch (e: Exception) { Color(0xFF2E86C1) }
                        )
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (imagePath != null) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = stringResource(R.string.categories_image_cd),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(stringResource(R.string.common_photo),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White)
                        }
                    }
                }

                if (imagePath != null) {
                    TextButton(onClick = { imagePath = null }) {
                        Text(stringResource(R.string.categories_remove_photo),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.common_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.common_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(color)))
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor, imagePath) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun AddCategoryDialog(
    onConfirm: (name: String, color: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colors = listOf("#2E86C1", "#E74C3C", "#27AE60", "#F39C12", "#8E44AD", "#16A085")
    var selectedColor by remember { mutableStateOf(colors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_new_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.common_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.common_color_label),
                    style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(color)))
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == color) {
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.common_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

private fun copyCategoryPhotoToStorage(context: android.content.Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "photos").also { it.mkdirs() }
    val dest = File(dir, "category_${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
    dest.absolutePath
} catch (_: Exception) { null }
