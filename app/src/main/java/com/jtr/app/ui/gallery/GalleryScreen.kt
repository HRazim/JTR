package com.jtr.app.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jtr.app.R
import com.jtr.app.domain.model.GalleryImage
import com.jtr.app.ui.category.FooterActionColumn
import com.jtr.app.ui.components.JtrBottomBarTransitions
import com.jtr.app.ui.components.JtrSelectionCheck
import com.jtr.app.ui.components.rememberSystemMultiPhotoPicker
import com.jtr.app.ui.navigation.nestedScreenContentInsets

/**
 * GALERIE d'un contact (v7.1.89) — grille de souvenirs, ajout multiple et visionneuse.
 *
 * v1 volontairement resserrée sur AJOUTER / VOIR / SUPPRIMER : `caption` et `position`
 * existent déjà en base (DB v23) mais ne sont pas exposées — légendes et réordonnancement
 * viendront sans aucune migration.
 *
 * Les vignettes sont CARRÉES à l'affichage seulement (`ContentScale.Crop` sur un ratio 1:1) :
 * le fichier conserve ses proportions d'origine, et la visionneuse le montre entier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val personName by viewModel.personName.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()

    // Index de l'image ouverte en plein écran ; null = grille seule.
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    // Images dont la suppression est en attente de confirmation (1 depuis la visionneuse,
    // N depuis le mode sélection) — un seul dialogue pour les deux chemins.
    var pendingDelete by remember { mutableStateOf<List<GalleryImage>>(emptyList()) }

    // v7.1.90 — mode SÉLECTION de la grille, sur appui long. Les identifiants survivent aux
    // recompositions ; une image supprimée disparaît naturellement de la liste observée.
    var isSelectionActive by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    fun exitSelection() {
        isSelectionActive = false
        selectedIds.clear()
    }
    // Back en mode sélection = annuler la sélection, pas quitter l'écran (idiome de l'app).
    BackHandler(enabled = isSelectionActive) { exitSelection() }

    val pickImages = rememberSystemMultiPhotoPicker { uris -> viewModel.addImages(uris) }

    Scaffold(
        // Écran plein (hors onglets) : seuls les insets horizontaux s'appliquent au contenu,
        // règle centralisée partagée avec les autres écrans empilés.
        contentWindowInsets = nestedScreenContentInsets,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // En sélection, le titre devient le compteur — comme l'Accueil
                        // et Catégories, qui réutilisent la même chaîne plurielle.
                        text = if (isSelectionActive)
                            pluralStringResource(
                                R.plurals.home_selection_count, selectedIds.size, selectedIds.size
                            )
                        else personName.ifBlank { stringResource(R.string.gallery_title) },
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    if (isSelectionActive) {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_cancel))
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back))
                        }
                    }
                },
                actions = {
                    when {
                        // Le « + » DISPARAÎT en sélection, comme partout ailleurs dans l'app ;
                        // il cède la place à « Tout sélectionner ».
                        isSelectionActive -> IconButton(onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(images.map { it.id })
                        }) {
                            Icon(Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.select_all))
                        }
                        isImporting -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                        else -> IconButton(onClick = pickImages) {
                            Icon(Icons.Default.Add,
                                contentDescription = stringResource(R.string.gallery_add))
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
        },
        bottomBar = {
            // Footer contextuel : même grammaire que l'Accueil et Catégories
            // (FooterActionColumn partagé), et la COULEUR porte le sens — rouge `error`
            // pour une suppression, jamais pour autre chose.
            AnimatedVisibility(
                visible = isSelectionActive,
                enter = JtrBottomBarTransitions.enter,
                exit = JtrBottomBarTransitions.exit
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        FooterActionColumn(
                            icon = Icons.Default.Delete,
                            label = stringResource(R.string.gallery_delete_confirm),
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                pendingDelete = images.filter { it.id in selectedIds }
                            },
                            tintOverride = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (images.isEmpty()) {
                GalleryEmptyState(onAdd = pickImages)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = images, key = { it.id }) { image ->
                        val index = images.indexOf(image)
                        GalleryThumbnail(
                            image = image,
                            isSelectionActive = isSelectionActive,
                            isSelected = image.id in selectedIds,
                            onClick = {
                                if (isSelectionActive) {
                                    // Bascule ; vider la sélection ne quitte PAS le mode
                                    // (on peut re-cocher sans refaire un appui long).
                                    if (image.id in selectedIds) selectedIds.remove(image.id)
                                    else selectedIds.add(image.id)
                                } else {
                                    viewerIndex = index
                                }
                            },
                            onLongClick = {
                                if (!isSelectionActive) {
                                    isSelectionActive = true
                                    selectedIds.add(image.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // VISIONNEUSE plein écran : pagination horizontale entre les images du contact.
    viewerIndex?.let { startIndex ->
        if (images.isEmpty()) {
            viewerIndex = null
        } else {
            GalleryViewer(
                images = images,
                startIndex = startIndex.coerceIn(0, images.lastIndex),
                onDismiss = { viewerIndex = null },
                onRequestDelete = { pendingDelete = listOf(it) }
            )
        }
    }

    // UNE seule confirmation pour les deux chemins : une image depuis la visionneuse,
    // ou toute une sélection depuis la grille. Les chaînes sont au pluriel, donc le
    // cas « 1 » se lit exactement comme avant.
    if (pendingDelete.isNotEmpty()) {
        val targets = pendingDelete
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = {
                Text(pluralStringResource(
                    R.plurals.gallery_delete_n_title, targets.size, targets.size))
            },
            text = { Text(stringResource(R.string.gallery_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll(targets)
                    pendingDelete = emptyList()
                    // La visionneuse se referme (l'image affichée vient de disparaître) et
                    // le mode sélection se termine : plus rien à y faire.
                    viewerIndex = null
                    exitSelection()
                }) {
                    Text(stringResource(R.string.gallery_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptyList() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/**
 * Vignette carrée (recadrage d'AFFICHAGE uniquement — le fichier garde ses proportions).
 *
 * Appui long = entrer en sélection, comme l'Accueil et Catégories. En sélection, un voile
 * assombrit les images COCHÉES et la coche universelle [JtrSelectionCheck] (obligatoire pour
 * tout nouvel affichage) reste lisible sur n'importe quelle photo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryThumbnail(
    image: GalleryImage,
    isSelectionActive: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.path).crossfade(200).build(),
            contentDescription = stringResource(R.string.gallery_image_cd),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (isSelectionActive) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
            JtrSelectionCheck(
                selected = isSelected,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            )
        }
    }
}

/** État vide accueillant : on explique et on propose l'action, plutôt qu'un écran nu. */
@Composable
private fun GalleryEmptyState(onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.gallery_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.gallery_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.height(24.dp))
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Box(Modifier.size(8.dp))
                Text(stringResource(R.string.gallery_add))
            }
        }
    }
}

/**
 * Visionneuse plein écran : [HorizontalPager] entre les images, fond noir, fermeture et
 * suppression en surimpression. L'image est montrée ENTIÈRE ([ContentScale.Fit]) — c'est là
 * qu'on voit le souvenir tel qu'il a été enregistré, sans le recadrage carré des vignettes.
 *
 * Le pager se miroite automatiquement en RTL (comportement natif de Compose).
 */
@Composable
private fun GalleryViewer(
    images: List<GalleryImage>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onRequestDelete: (GalleryImage) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(images[page].path).crossfade(200).build(),
                    contentDescription = stringResource(R.string.gallery_image_cd),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, tint = Color.White,
                    contentDescription = stringResource(R.string.common_close))
            }

            IconButton(
                onClick = { images.getOrNull(pagerState.currentPage)?.let(onRequestDelete) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Delete, tint = Color.White,
                    contentDescription = stringResource(R.string.gallery_delete_confirm))
            }

            // Compteur « 2 / 7 » : repère discret quand on feuillette.
            Text(
                text = stringResource(
                    R.string.gallery_position, pagerState.currentPage + 1, images.size
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            )
        }
    }
}
