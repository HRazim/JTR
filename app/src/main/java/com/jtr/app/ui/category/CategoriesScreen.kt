package com.jtr.app.ui.category

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jtr.app.domain.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (String) -> Unit = {},
    viewModel: CategoryViewModel = viewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val personCountByCategory by viewModel.personCountByCategory.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteCategory by remember { mutableStateOf<Category?>(null) }
    var pendingEditCategory by remember { mutableStateOf<Category?>(null) }

    pendingDeleteCategory?.let { category ->
        val count = personCountByCategory[category.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Supprimer « ${category.name} » ?") },
            text = {
                if (count > 0) {
                    Text(
                        "Cette catégorie contient $count contact(s). " +
                        "Êtes-vous sûr de vouloir supprimer la catégorie ainsi que " +
                        "les $count contact(s) qu'elle contient ?\n\n" +
                        "Vous pourrez les restaurer depuis la Corbeille."
                    )
                } else {
                    Text("La catégorie sera déplacée dans la corbeille. Vous pourrez la restaurer.")
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
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCategory = null }) { Text("Annuler") }
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
                title = { Text("Catégories") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter une catégorie")
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Aucune catégorie",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Appuyez sur + pour en créer une",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    val count = personCountByCategory[category.id] ?: 0
                    CategoryCard(
                        category = category,
                        personCount = count,
                        onClick = { onCategoryClick(category.id) },
                        onEdit = { pendingEditCategory = category },
                        onDelete = { pendingDeleteCategory = category }
                    )
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

@Composable
fun CategoryCard(
    category: Category,
    personCount: Int = 0,
    onClick: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        try { Color(android.graphics.Color.parseColor(category.color)) }
                        catch (e: Exception) { Color(0xFF2E86C1) }
                    ),
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
                        text = "$personCount contact${if (personCount > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Modifier",
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
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

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) {
                    copyCategoryPhotoToStorage(context, uri)
                }
                if (path != null) imagePath = path
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la catégorie") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Photo de couverture
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
                            contentDescription = "Image de la catégorie",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Overlay crayon
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
                            Text("Photo", style = MaterialTheme.typography.labelSmall,
                                color = Color.White)
                        }
                    }
                }

                if (imagePath != null) {
                    TextButton(onClick = { imagePath = null }) {
                        Text("Supprimer la photo", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Couleur", style = MaterialTheme.typography.labelMedium,
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
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
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
        title = { Text("Nouvelle catégorie") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Couleur", style = MaterialTheme.typography.labelMedium)
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
            ) { Text("Créer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

private fun copyCategoryPhotoToStorage(context: android.content.Context, uri: Uri): String? = try {
    val dir = File(context.filesDir, "photos").also { it.mkdirs() }
    val dest = File(dir, "category_${UUID.randomUUID()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
    dest.absolutePath
} catch (_: Exception) { null }
