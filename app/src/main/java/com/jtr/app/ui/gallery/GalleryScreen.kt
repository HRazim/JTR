package com.jtr.app.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
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
import com.jtr.app.ui.components.SCROLL_TO_TOP_THRESHOLD
import com.jtr.app.ui.components.ScrollToTopFab
import com.jtr.app.ui.components.ZoomableImage
import com.jtr.app.ui.components.rememberVisualCenterCorrectionPx
import com.jtr.app.ui.components.rememberSystemMultiPhotoPicker
import com.jtr.app.ui.components.rememberZoomState
import kotlinx.coroutines.launch

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
    // v7.1.96 — image dont le panneau de détails est ouvert DEPUIS LA GRILLE (bouton ⓘ du
    // mode sélection) ; null = aucun panneau. La visionneuse a son propre état.
    var detailsImageId by remember { mutableStateOf<Long?>(null) }
    fun exitSelection() {
        isSelectionActive = false
        selectedIds.clear()
    }
    // Back en mode sélection = annuler la sélection, pas quitter l'écran (idiome de l'app).
    BackHandler(enabled = isSelectionActive) { exitSelection() }

    val pickImages = rememberSystemMultiPhotoPicker { uris -> viewModel.addImages(uris) }

    // v7.1.92 — FAB « remonter en haut », MÊME composant et MÊME seuil que l'Accueil, les
    // Catégories et les écrans de détail (v7.1.72/86) : aucune variante. L'état de la grille
    // est hoisté ici pour que le FAB puisse la piloter ; `derivedStateOf` ne recompose qu'au
    // FRANCHISSEMENT du seuil, pas à chaque pixel défilé.
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > SCROLL_TO_TOP_THRESHOLD }
    }

    Scaffold(
        // ⚠️ INSETS PAR DÉFAUT DU SCAFFOLD (v7.1.92) — surtout PAS `nestedScreenContentInsets`.
        // Cette règle-là ampute volontairement le BAS, parce qu'elle est écrite pour les
        // écrans à ONGLETS (Accueil/Catégories/Paramètres), dont la barre globale occupe déjà
        // le bas. La galerie est un écran EMPILÉ : rien dessous. Mesuré sur S21, l'amputation
        // faisait passer la DERNIÈRE RANGÉE de vignettes ET le FAB sous la barre de
        // navigation. On s'aligne donc sur `CategoryDetailScreen`, qui ne pose rien : le
        // Scaffold dégage alors la barre UNE SEULE FOIS pour la grille, le FAB et le footer.
        floatingActionButton = {
            // Le Scaffold pose le FAB en bas-fin (donc en miroir automatique en RTL) et le
            // remonte au-dessus de la bottomBar ET de la barre système. L'ajout étant le « + »
            // de la TopAppBar, il n'y a rien à chevaucher ; le FAB s'efface en sélection, où
            // le footer de suppression occupe le bas.
            ScrollToTopFab(
                visible = showScrollToTop && !isSelectionActive,
                onClick = { scope.launch { gridState.animateScrollToItem(0) } }
            )
        },
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
                        // v7.1.96 — « Tout sélectionner » à GAUCHE, « Annuler » à droite :
                        // c'est déjà la disposition de l'Accueil, de Catégories et du détail
                        // de dossier (tous trois le posent en `navigationIcon`). La galerie
                        // était la SEULE à faire l'inverse ; elle rentre dans le rang.
                        IconButton(onClick = {
                            selectedIds.clear()
                            selectedIds.addAll(images.map { it.id })
                        }) {
                            Icon(Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.select_all))
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
                        isSelectionActive -> {
                            // ⓘ UNIQUEMENT sur une sélection d'UNE image : des détails n'ont
                            // de sens que pour une photo précise. Même bouton, même panneau
                            // que depuis la visionneuse — aucune variante.
                            if (selectedIds.size == 1) {
                                IconButton(onClick = { detailsImageId = selectedIds.first() }) {
                                    Icon(Icons.Outlined.Info,
                                        contentDescription =
                                            stringResource(R.string.gallery_details_open))
                                }
                            }
                            IconButton(onClick = { exitSelection() }) {
                                Icon(Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_cancel))
                            }
                        }
                        isImporting -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                        // Le « + » DISPARAÎT en sélection, comme partout ailleurs dans l'app.
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
                    state = gridState,
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

    // PANNEAU DE DÉTAILS ouvert depuis la GRILLE (sélection d'une seule image) — le MÊME
    // composant que depuis la visionneuse, câblé sur les mêmes intentions.
    detailsImageId?.let { id ->
        val image = images.firstOrNull { it.id == id }
        if (image == null) {
            // L'image a disparu (suppression concurrente) : on referme plutôt que d'afficher
            // un panneau orphelin.
            detailsImageId = null
        } else {
            ImageDetailsSheet(
                image = image,
                onCaptionChange = { viewModel.setCaption(id, it) },
                onCreatedAtChange = { viewModel.setCreatedAt(id, it) },
                onLocationChange = { lat, lng, label ->
                    viewModel.setLocation(id, lat, lng, label)
                },
                onDismiss = { detailsImageId = null }
            )
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
                onRequestDelete = { pendingDelete = listOf(it) },
                onCaptionChange = { id, caption -> viewModel.setCaption(id, caption) },
                onCreatedAtChange = { id, millis -> viewModel.setCreatedAt(id, millis) },
                onLocationChange = { id, lat, lng, label ->
                    viewModel.setLocation(id, lat, lng, label)
                }
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
    onRequestDelete: (GalleryImage) -> Unit,
    onCaptionChange: (Long, String?) -> Unit,
    onCreatedAtChange: (Long, Long) -> Unit,
    onLocationChange: (Long, Double?, Double?, String?) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = startIndex) { images.size }
    // v7.1.94 — panneau de détails de l'image COURANTE (bouton ⓘ).
    var showDetails by remember { mutableStateOf(false) }
    // ⚠️ Direction RÉELLE de l'app, capturée AVANT le forçage LTR du Dialog ci-dessous. Ce
    // forçage sert au moteur de GESTES (zoom/pan en pixels) ; le panneau de détails, lui, est
    // de l'UI de TEXTE et doit se miroiter en arabe. Sans cette capture il en héritait et
    // s'affichait aligné à gauche en RTL (mesuré sur S21).
    val appLayoutDirection = LocalLayoutDirection.current
    // UN SEUL état de zoom pour le pager : seule l'image courante peut être zoomée, car le
    // pager est verrouillé tant qu'on ne revient pas à 1×. Changer d'image remet à plat.
    val zoomState = rememberZoomState()
    LaunchedEffect(pagerState.currentPage) { zoomState.reset() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Gestes en pixels (zoom/pan, ancrage du double-tap) → LTR forcé pour qu'ils soient
        // identiques en arabe, exactement comme la visionneuse d'avatar.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // ⚠️ DOIT être appelé À L'INTÉRIEUR du Dialog : c'est la fenêtre du DIALOGUE
            // qu'on mesure. Appelé au-dessus, `LocalView` est celle de l'activité et la
            // correction vaut 0 (vérifié sur appareil : image toujours 80 px trop basse).
            val centerCorrection = rememberVisualCenterCorrectionPx()
            Box(modifier = Modifier.fillMaxSize()) {
                // Fond noir qui s'estompe au glissement de fermeture, en SYNCHRO avec
                // l'image (même source : dismissFraction).
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 1f - zoomState.dismissFraction() }
                        .background(Color.Black)
                )

                HorizontalPager(
                    state = pagerState,
                    // Recentrage sur l'écran RÉEL : la fenêtre du dialogue est posée sous la
                    // barre d'état, donc son centre n'est pas celui de l'écran. Appliqué aux
                    // seules IMAGES — le fond noir, lui, doit couvrir toute la fenêtre.
                    modifier = Modifier.fillMaxSize().offset { IntOffset(0, centerCorrection) },
                    // VERROU : zoomée, l'image prend tous les glissements pour son pan ;
                    // le pager ne doit pas voler le geste.
                    userScrollEnabled = !zoomState.isZoomed
                ) { page ->
                    ZoomableImage(
                        model = images[page].path,
                        onDismiss = onDismiss,
                        state = zoomState,
                        // Au repos, l'horizontal appartient au PAGER (changer d'image) et
                        // le vertical ferme. C'est tout l'arbitrage du mode pager.
                        horizontalDragGoesToParent = true,
                        contentDescription = stringResource(R.string.gallery_image_cd)
                    )
                }

                // CONTRÔLES sur scrim circulaire sombre : lisibles sur une photo claire
                // comme sombre (ils étaient blancs sur blanc avant v7.1.91). Estompés par
                // la même progression que le fond.
                ViewerControlButton(
                    icon = Icons.Default.Close,
                    label = stringResource(R.string.common_close),
                    onClick = onDismiss,
                    alphaProvider = { 1f - zoomState.dismissFraction() },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                )
                // ⓘ et corbeille GROUPÉS en haut-fin, dans cet ordre : l'action destructrice
                // reste la plus excentrée, comme partout dans l'app.
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ViewerControlButton(
                        icon = Icons.Outlined.Info,
                        label = stringResource(R.string.gallery_details_open),
                        onClick = { showDetails = true },
                        alphaProvider = { 1f - zoomState.dismissFraction() }
                    )
                    ViewerControlButton(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.gallery_delete_confirm),
                        onClick = {
                            images.getOrNull(pagerState.currentPage)?.let(onRequestDelete)
                        },
                        alphaProvider = { 1f - zoomState.dismissFraction() },
                        // Rouge CLAIR : le fond est noir quel que soit le thème.
                        tint = Color(0xFFFF8A80)
                    )
                }
            }

            // PANNEAU DE DÉTAILS, posé PAR-DESSUS la visionneuse : le pager reste monté
            // derrière, donc fermer le panneau rend la visionneuse intacte, sur la même
            // image. On relit la ligne à chaque composition pour refléter une écriture.
            if (showDetails) {
                val current = images.getOrNull(pagerState.currentPage)
                if (current == null) {
                    showDetails = false
                } else {
                    // On REND au panneau la direction réelle de l'app (cf. plus haut).
                    CompositionLocalProvider(
                        LocalLayoutDirection provides appLayoutDirection
                    ) {
                        ImageDetailsSheet(
                            image = current,
                            onCaptionChange = { onCaptionChange(current.id, it) },
                            onCreatedAtChange = { onCreatedAtChange(current.id, it) },
                            onLocationChange = { lat, lng, label ->
                                onLocationChange(current.id, lat, lng, label)
                            },
                            onDismiss = { showDetails = false }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bouton de contrôle de la visionneuse : icône blanche (ou teintée) sur un disque sombre.
 * Le scrim est ce qui le rend lisible par-dessus n'importe quelle photo — même grammaire
 * que la croix de [com.jtr.app.ui.components.PhotoZoomDialog]. Cible 40 dp + padding → 48 dp.
 */
@Composable
private fun ViewerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { alpha = alphaProvider() }
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}
