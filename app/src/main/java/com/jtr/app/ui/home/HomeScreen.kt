package com.jtr.app.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.toColorInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.domain.model.Category
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.ui.category.FooterActionColumn
import com.jtr.app.ui.category.contactSortCriteria
import com.jtr.app.ui.components.FavoriteStar
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrOverflowMenu
import com.jtr.app.ui.components.JtrSearchableTopAppBar
import com.jtr.app.ui.share.PersonsSharePreview
import com.jtr.app.ui.share.ShareFormatSheet
import com.jtr.app.ui.share.ShareUtils
import com.jtr.app.utils.getSocialIcon
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAddPerson: () -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit = {},
    onMoveSelectionToCategory: (List<String>) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val persons by viewModel.persons.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val socialLinksMap by viewModel.socialLinksMap.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val upcomingEvents by viewModel.upcomingEvents.collectAsStateWithLifecycle()
    val allActivePersons by viewModel.allActivePersons.collectAsStateWithLifecycle()

    // Mode recherche de la TopAppBar (état d'UI local ; la query vient du ViewModel).
    var searchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Fermeture INSTANTANÉE du clavier avant de naviguer depuis un résultat de
    // recherche : la transition vers la fiche ne reste plus saccadée par l'IME.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Bouton Retour en mode sélection : annule la sélection (événement remonté au
    // ViewModel) au lieu de quitter l'écran. Inactif si rien n'est sélectionné →
    // la navigation standard reprend normalement.
    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }

    // Cible du partage contextuel : instantané des profils cochés au moment du clic.
    var shareTargets by remember { mutableStateOf<List<Person>?>(null) }
    shareTargets?.let { targets ->
        // Capture PNG autorisée seulement quand les avatars Coil de l'aperçu
        // sont tous résolus (v5.5) — jamais de placeholders dans l'export.
        var previewImagesReady by remember(targets) { mutableStateOf(false) }
        ShareFormatSheet(
            onDismiss = { shareTargets = null },
            buildText = { ShareUtils.buildPersonsShareText(context, targets) },
            writePdf = { ShareUtils.writePersonsPdf(context, targets) },
            previewReady = previewImagesReady,
            preview = {
                PersonsSharePreview(targets) { ready -> previewImagesReady = ready }
            }
        )
    }

    // Reporte l'état de sélection au conteneur : la barre de navigation globale
    // s'efface et c'est le footer contextuel ci-dessous qui prend sa place.
    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }
    DisposableEffect(Unit) {
        onDispose {
            onSelectionModeChange(false)
            // Sortie d'écran (détail / autre onglet) → le filtre de recherche est
            // réinitialisé : au retour, TOUS les profils réapparaissent.
            viewModel.clearSearch()
        }
    }

    Scaffold(
        // Les insets bas sont déjà couverts par le Scaffold racine (footer global) :
        // on les neutralise ici pour supprimer la bande blanche au-dessus du footer.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (isSelectionMode && searchActive) {
                // Recherche CONTEXTUELLE pendant la sélection : filtre l'affichage
                // sans JAMAIS toucher au Set des ids cochés (persistance stricte UDF) —
                // fermer la recherche efface la query, pas la sélection.
                JtrSearchableTopAppBar(
                    title = "",
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    searchActive = true,
                    onSearchActiveChange = { searchActive = it },
                    searchPlaceholder = stringResource(R.string.home_search_placeholder)
                )
            } else if (isSelectionMode) {
                // Gauche : « Tout sélectionner (n) » · Droite : Loupe + « Annuler ».
                TopAppBar(
                    title = { Text(stringResource(R.string.select_all_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.select_all))
                        }
                    },
                    actions = {
                        // Loupe accessible EN mode sélection (grands volumes) : on
                        // cherche, on coche, on recommence — les coches survivent.
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search))
                        }
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text(stringResource(R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            } else {
                // Barre harmonisée : loupe (à gauche du « + ») + ajout + menu 3 points
                // (sous-sections Tri / Affichage).
                JtrSearchableTopAppBar(
                    title = stringResource(R.string.home_title),
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    searchActive = searchActive,
                    onSearchActiveChange = { searchActive = it },
                    searchPlaceholder = stringResource(R.string.home_search_placeholder),
                    actions = {
                        // Header minimaliste (v5.3.1) : Loupe → « + » → 3 points.
                        // Tri et Affichage vivent EXCLUSIVEMENT dans le menu 3 points.
                        IconButton(onClick = onNavigateToAddPerson) {
                            Icon(Icons.Default.Add,
                                contentDescription = stringResource(R.string.home_fab_add_person))
                        }
                        // Sauvegarde & restauration : relocalisée dans Paramètres
                        // → section Données (v5.5).
                        JtrOverflowMenu(
                            sortCriteria = contactSortCriteria(sortOrder) { viewModel.setSortOrder(it) },
                            viewMode = viewMode,
                            onViewModeChange = { viewModel.setViewMode(it) }
                        )
                    }
                )
            }
        },
        bottomBar = {
            // Footer contextuel du mode sélection : il « remplace » la barre de
            // navigation globale (masquée par le conteneur pendant la sélection).
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = JtrBottomBarTransitions.enter,
                exit = JtrBottomBarTransitions.exit
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        FooterActionColumn(
                            Icons.Default.Share,
                            stringResource(R.string.share_action),
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                // Liste NON filtrée : les contacts cochés sous une autre
                                // recherche font bien partie du partage.
                                shareTargets = allActivePersons.filter { it.id in selectedIds }
                            }
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        // Sémantique v5.3 : suppression globale = mise à la CORBEILLE.
                        FooterActionColumn(
                            Icons.Default.Delete,
                            stringResource(R.string.action_trash_short),
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { viewModel.deleteSelected() },
                            tintOverride = MaterialTheme.colorScheme.error
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        FooterActionColumn(
                            Icons.AutoMirrored.Filled.DriveFileMove,
                            stringResource(R.string.action_move),
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                // Aucun dialogue : on bascule vers l'onglet Catégories où
                                // l'utilisateur touche directement la cible.
                                val ids = selectedIds.toList()
                                viewModel.clearSelection()
                                onMoveSelectionToCategory(ids)
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (persons.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.home_empty_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.home_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Rendu commutable LIST / GRID / DETAIL (composant partagé). Le hub
                // des événements est injecté EN EN-TÊTE de la liste/grille (v5.3.4) :
                // il défile avec le contenu et s'étale sur toute la largeur en Grille.
                PersonListContent(
                    viewMode = viewMode,
                    persons = persons,
                    socialLinksMap = socialLinksMap,
                    selectedIds = selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { person ->
                        if (isSelectionMode) {
                            viewModel.toggleSelection(person.id)
                        } else {
                            // Hide AVANT la navigation : pas de clavier résiduel.
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onNavigateToPersonDetail(person.id)
                        }
                    },
                    onLongClick = { viewModel.toggleSelection(it.id) },
                    onFavoriteClick = { viewModel.toggleFavorite(it) },
                    header = if (!isSelectionMode && upcomingEvents.isNotEmpty()) {
                        {
                            UpcomingEventsBanner(
                                events = upcomingEvents,
                                onEventClick = { onNavigateToPersonDetail(it.person.id) }
                            )
                        }
                    } else null
                )
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
    var mode by remember {
        mutableStateOf(if (categories.isEmpty()) CategoryDialogMode.CREATE else CategoryDialogMode.SELECT)
    }

    var newName by remember { mutableStateOf("") }
    val colorOptions = listOf("#2E86C1", "#E74C3C", "#27AE60", "#F39C12", "#8E44AD", "#16A085")
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (mode == CategoryDialogMode.SELECT)
                        stringResource(R.string.dialog_assign_category_title)
                    else
                        stringResource(R.string.dialog_new_category_title),
                    modifier = Modifier.weight(1f)
                )
                if (mode == CategoryDialogMode.SELECT) {
                    IconButton(
                        onClick = { mode = CategoryDialogMode.CREATE },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline,
                            contentDescription = stringResource(R.string.dialog_create_category_cd),
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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(
                            onClick = { mode = CategoryDialogMode.CREATE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dialog_create_new_category))
                        }
                    }
                }

                CategoryDialogMode.CREATE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text(stringResource(R.string.dialog_category_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(stringResource(R.string.common_color_label),
                            style = MaterialTheme.typography.labelMedium)
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
                CategoryDialogMode.SELECT -> {}
                CategoryDialogMode.CREATE -> {
                    TextButton(
                        onClick = {
                            if (newName.isNotBlank()) onCreateAndAssign(newName.trim(), selectedColor)
                        },
                        enabled = newName.isNotBlank()
                    ) { Text(stringResource(R.string.dialog_create_and_assign)) }
                }
            }
        },
        dismissButton = {
            if (mode == CategoryDialogMode.CREATE && categories.isNotEmpty()) {
                TextButton(onClick = { mode = CategoryDialogMode.SELECT }) {
                    Text(stringResource(R.string.common_back))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
            .shadow(
                elevation = if (isSelected) 8.dp else 3.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor  = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                            text = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                                .format(Date(person.birthdate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (person.origin != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = person.origin,
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
                    val cd = stringResource(R.string.home_favorite_cd)
                    if (person.isFavorite) {
                        FavoriteStar(size = 24.dp, contentDescription = cd)
                    } else {
                        Icon(
                            imageVector = Icons.Default.StarBorder,
                            contentDescription = cd,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}
