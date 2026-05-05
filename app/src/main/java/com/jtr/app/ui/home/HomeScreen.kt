package com.jtr.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.toColorInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.getSocialIcon
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddPerson: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val persons by viewModel.persons.collectAsStateWithLifecycle()
    // État local pour préserver la composition IME lors de la frappe de caractères accentués.
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isLocationEnabled by viewModel.isLocationEnabled.collectAsStateWithLifecycle()
    val socialLinksMap by viewModel.socialLinksMap.collectAsStateWithLifecycle()

    var showCategoryDialog by remember { mutableStateOf(false) }

    if (showCategoryDialog) {
        AssignCategoryDialog(
            categories = categories,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { categoryId ->
                viewModel.assignCategoryToSelected(categoryId)
                showCategoryDialog = false
            },
            onCreateAndAssign = { name, color ->
                viewModel.createCategoryAndAssignToSelected(name, color)
                showCategoryDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} sélectionné(s)") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("JTR — Mes Contacts") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCategoryDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Label, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Catégorie")
                        }
                        Button(
                            onClick = { viewModel.deleteSelected() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Supprimer")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = onNavigateToAddPerson,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter une personne")
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // ── Bannière GPS désactivé ────────────────────────────────────────
            AnimatedVisibility(visible = !isLocationEnabled && !isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GpsOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Activez la localisation pour les rappels de proximité",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (!isSelectionMode) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Rechercher un contact...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Effacer")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp) // pill
                )
            }

            if (persons.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Aucun contact trouvé",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Appuyez sur + pour ajouter une personne",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = persons, key = { it.id }) { person ->
                        PersonCard(
                            person = person,
                            socialLinks = socialLinksMap[person.id] ?: emptyList(),
                            isSelected = person.id in selectedIds,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) viewModel.toggleSelection(person.id)
                                else onNavigateToPersonDetail(person.id)
                            },
                            onLongClick = { viewModel.toggleSelection(person.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(person) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogue d'assignation / création de catégorie à la volée
// ─────────────────────────────────────────────────────────────────────────────

private enum class CategoryDialogMode { SELECT, CREATE }

@Composable
fun AssignCategoryDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onCreateAndAssign: (name: String, color: String) -> Unit
) {
    // Démarre directement en création si aucune catégorie n'existe
    var mode by remember {
        mutableStateOf(if (categories.isEmpty()) CategoryDialogMode.CREATE else CategoryDialogMode.SELECT)
    }

    // État formulaire de création
    var newName by remember { mutableStateOf("") }
    val colorOptions = listOf("#2E86C1", "#E74C3C", "#27AE60", "#F39C12", "#8E44AD", "#16A085")
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (mode == CategoryDialogMode.SELECT)
                        "Assigner une catégorie" else "Nouvelle catégorie",
                    modifier = Modifier.weight(1f)
                )
                // Bouton "+" visible uniquement en mode sélection
                if (mode == CategoryDialogMode.SELECT) {
                    IconButton(
                        onClick = { mode = CategoryDialogMode.CREATE },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline,
                            contentDescription = "Créer une catégorie",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        text = {
            when (mode) {
                CategoryDialogMode.SELECT -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        categories.forEach { category ->
                            TextButton(
                                onClick = { onCategorySelected(category.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(parseCategoryColor(category.color))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(category.name,
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        // Séparateur + bouton création en bas de liste
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(
                            onClick = { mode = CategoryDialogMode.CREATE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Créer une nouvelle catégorie")
                        }
                    }
                }

                CategoryDialogMode.CREATE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nom de la catégorie") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Couleur", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colorOptions.forEach { color ->
                                val isSelected = selectedColor == color
                                Surface(
                                    onClick = { selectedColor = color },
                                    shape = CircleShape,
                                    color = parseCategoryColor(color),
                                    modifier = Modifier.size(34.dp),
                                    border = if (isSelected)
                                        androidx.compose.foundation.BorderStroke(
                                            2.dp, MaterialTheme.colorScheme.onSurface)
                                    else null
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (mode) {
                CategoryDialogMode.SELECT -> {} // sélection = action directe sur chaque item
                CategoryDialogMode.CREATE -> {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) onCreateAndAssign(newName.trim(), selectedColor)
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("Créer et assigner") }
                }
            }
        },
        dismissButton = {
            if (mode == CategoryDialogMode.CREATE && categories.isNotEmpty()) {
                TextButton(onClick = { mode = CategoryDialogMode.SELECT }) { Text("Retour") }
            } else {
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}

private fun parseCategoryColor(hex: String): Color = try {
    Color(hex.toColorInt())
} catch (_: Exception) {
    Color.Gray
}

// ─────────────────────────────────────────────────────────────────────────────
// PersonCard
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonCard(
    person: Person,
    socialLinks: List<SocialLinkEntity> = emptyList(),
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // Ombre colorée : ambientColor reprend la teinte primaire du thème
            .shadow(
                elevation = if (isSelected) 8.dp else 3.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor  = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        // elevation = 0 : la shadow Modifier gère l'effet de profondeur
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // ── Avatar ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .then(
                        if (person.photoUri == null)
                            Modifier.background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        else
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (person.photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(person.photoUri)
                            .crossfade(300)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = person.initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = person.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (person.city != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = person.city,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (person.birthdate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Cake,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH)
                                .format(Date(person.birthdate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (socialLinks.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        socialLinks.take(6).forEach { link ->
                            Icon(
                                painter = painterResource(getSocialIcon(link.url)),
                                contentDescription = link.platform,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Unspecified
                            )
                        }
                    }
                }
            }

            if (!isSelectionMode) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (person.isFavorite) Icons.Default.Favorite
                        else Icons.Default.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (person.isFavorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}
