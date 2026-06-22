package com.jtr.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jtr.app.R
import com.jtr.app.data.repository.GalleryAlbum
import kotlinx.coroutines.launch

/** Nombre d'albums affichés avant le repli « Voir tout » dans la vue C.3. */
private const val ALBUM_PREVIEW_COUNT = 6

/**
 * Sélecteur d'image IN-APP « façon Instagram » : feuille modale s'ouvrant à
 * mi-hauteur (glissable jusqu'au plein écran), header à menu déroulant de
 * catégories (Récents / Favoris / Photos / Tous les albums) et navigateur
 * d'albums plein écran — l'utilisateur ne quitte JAMAIS l'application.
 *
 * L'écran appelant n'est pas recréé et aucun aller-retour d'Activity n'a lieu :
 * l'état du formulaire (ViewModel) est intégralement préservé (esprit UDF).
 * L'URI retournée alimente le workflow de recadrage existant (ImageCropDialog)
 * exactement comme avant — la signature publique ci-dessous est INCHANGÉE.
 *
 * Permissions : READ_MEDIA_IMAGES (API 33+) ou READ_EXTERNAL_STORAGE (API < 33),
 * avec accès partiel Android 14+ (READ_MEDIA_VISUAL_USER_SELECTED), demandées
 * dans le flux UI à l'ouverture de la feuille.
 *
 * @return une lambda à invoquer pour ouvrir la galerie.
 */
@Composable
fun rememberGalleryImagePicker(onImagePicked: (Uri) -> Unit): () -> Unit {
    var showSheet by remember { mutableStateOf(false) }
    val currentOnImagePicked by rememberUpdatedState(onImagePicked)

    if (showSheet) {
        CustomImagePickerBottomSheet(
            onImagePicked = { uri ->
                showSheet = false
                currentOnImagePicked(uri)
            },
            onDismiss = { showSheet = false },
        )
    }
    return remember { { showSheet = true } }
}

/** Niveau d'accès effectif à la galerie selon la version d'Android. */
private enum class GalleryAccess { FULL, PARTIAL, NONE }

/**
 * Feuille modale de la galerie in-app. Gère elle-même la permission de lecture
 * (explication AVANT la demande, re-demande, accès partiel Android 14+, repli
 * vers les paramètres système après refus) et délègue tout le contenu à
 * [GalleryPickerViewModel] (UDF).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomImagePickerBottomSheet(
    onImagePicked: (Uri) -> Unit,
    onDismiss: () -> Unit,
    viewModel: GalleryPickerViewModel = viewModel(),
) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(galleryAccessLevel(context)) }
    // Vrai après un refus explicite : on propose alors les paramètres système,
    // car Android cesse d'afficher le dialogue après deux refus.
    var wasDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        access = galleryAccessLevel(context)
        // Recharge à chaque (ré)octroi, y compris l'élargissement d'un accès
        // partiel (« Sélectionner plus ») où le niveau reste PARTIAL.
        if (access == GalleryAccess.NONE) wasDenied = true else viewModel.refresh()
    }

    // Accès déjà accordé à l'ouverture : charge immédiatement.
    LaunchedEffect(Unit) {
        if (access != GalleryAccess.NONE) viewModel.refresh()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    // Sélection d'une image : on FERME d'abord la feuille (animation de masquage),
    // PUIS on livre l'URI au consommateur, une fois la feuille réellement cachée.
    // La fenêtre de la feuille a ainsi totalement disparu avant l'ouverture du
    // dialogue de recadrage (Dialog) : plus de course de fenêtres — le recadrage
    // s'ouvre 100 % des fois (fini le « une fois sur deux ») et son
    // BoxWithConstraints mesure des contraintes stables (clamp du pan fiable).
    fun confirmPick(uri: Uri) {
        scope.launch { sheetState.hide() }
            .invokeOnCompletion { if (!sheetState.isVisible) onImagePicked(uri) }
    }

    // Ouverture à mi-hauteur (PartiallyExpanded par défaut) ; le navigateur
    // d'albums (C.3) et l'écran d'explication s'affichent en plein écran.
    LaunchedEffect(state.viewMode, access) {
        if (state.viewMode == GalleryViewMode.ALBUM_LIST || access == GalleryAccess.NONE) {
            sheetState.expand()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (access) {
                GalleryAccess.NONE -> GalleryPermissionContent(
                    showSettingsButton = wasDenied,
                    onRequest = { permissionLauncher.launch(galleryReadPermissions()) },
                    onOpenSettings = { context.openAppSettings() },
                )

                else -> {
                    PickerHeader(
                        title = headerTitle(state),
                        isAlbumList = state.viewMode == GalleryViewMode.ALBUM_LIST,
                        showFavorites = state.showFavorites,
                        onSelectCategory = viewModel::selectCategory,
                        onOpenAlbumList = viewModel::openAlbumList,
                        onCloseAlbumList = viewModel::closeAlbumList,
                    )

                    if (access == GalleryAccess.PARTIAL) {
                        PartialAccessBanner(
                            onManage = { permissionLauncher.launch(galleryReadPermissions()) }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            state.viewMode == GalleryViewMode.ALBUM_LIST -> AlbumListContent(
                                state = state,
                                onSelectCategory = viewModel::selectCategory,
                                onSelectAlbum = viewModel::selectAlbum,
                            )

                            state.isLoading -> CenteredLoader()

                            state.images.isEmpty() -> CenteredMessage(
                                stringResource(R.string.gallery_empty)
                            )

                            // Clé stable par sélection : chaque album/catégorie obtient
                            // un LazyGridState neuf → la position de scroll repart
                            // proprement du haut au changement de source.
                            else -> key(selectionKey(state.selection)) {
                                ImageGrid(
                                    images = state.images,
                                    onImagePicked = { confirmPick(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────

/**
 * En-tête « Instagram » : titre cliquable de la source courante + chevron. En
 * grille, le tap ouvre le menu déroulant des catégories (C.2) ; dans le
 * navigateur d'albums, le chevron pointe vers le haut et le tap revient à la
 * grille.
 */
@Composable
private fun PickerHeader(
    title: String,
    isAlbumList: Boolean,
    showFavorites: Boolean,
    onSelectCategory: (GalleryCategory) -> Unit,
    onOpenAlbumList: () -> Unit,
    onCloseAlbumList: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (isAlbumList) onCloseAlbumList() else expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = if (isAlbumList) Icons.Default.KeyboardArrowUp
                else Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.gallery_album_dropdown_cd),
            )
        }

        if (!isAlbumList) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CategoryMenuItem(R.string.gallery_album_recents, Icons.Default.Schedule) {
                    expanded = false; onSelectCategory(GalleryCategory.RECENTS)
                }
                if (showFavorites) {
                    CategoryMenuItem(R.string.gallery_category_favorites, Icons.Default.Favorite) {
                        expanded = false; onSelectCategory(GalleryCategory.FAVORITES)
                    }
                }
                CategoryMenuItem(R.string.gallery_category_photos, Icons.Outlined.PhotoLibrary) {
                    expanded = false; onSelectCategory(GalleryCategory.PHOTOS)
                }
                HorizontalDivider()
                CategoryMenuItem(R.string.gallery_all_albums, Icons.Default.Collections) {
                    expanded = false; onOpenAlbumList()
                }
            }
        }
    }
}

@Composable
private fun CategoryMenuItem(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, contentDescription = null) },
        text = { Text(stringResource(labelRes)) },
        onClick = onClick,
    )
}

/** Bannière d'accès partiel Android 14+ : la grille reste fonctionnelle. */
@Composable
private fun PartialAccessBanner(onManage: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.gallery_partial_access_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onManage) {
            Text(stringResource(R.string.gallery_partial_access_manage))
        }
    }
}

// ── Grille de miniatures (C.1) ───────────────────────────────────────────────

/**
 * Grille 3 colonnes de tuiles carrées. Coil sous-échantillonne chaque image à
 * la taille de la tuile (`size(300)`) : seules les miniatures résident en
 * mémoire, jamais les pleines résolutions.
 */
@Composable
private fun ImageGrid(images: List<Uri>, onImagePicked: (Uri) -> Unit) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = rememberLazyGridState(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(images, key = { it.toString() }) { uri ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uri)
                    .size(300)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onImagePicked(uri) },
            )
        }
    }
}

// ── Navigateur d'albums plein écran (C.3) ────────────────────────────────────

/**
 * Vue « Sélectionner un album » : rangée de raccourcis circulaires (Récents /
 * Favoris / Photos) puis grille 2 colonnes des dossiers avec couverture, nom et
 * compteur. Le lien « Voir tout » déplie l'intégralité des albums.
 */
@Composable
private fun AlbumListContent(
    state: GalleryPickerUiState,
    onSelectCategory: (GalleryCategory) -> Unit,
    onSelectAlbum: (GalleryAlbum) -> Unit,
) {
    var albumsExpanded by remember { mutableStateOf(false) }
    val visibleAlbums = if (albumsExpanded) state.albums
    else state.albums.take(ALBUM_PREVIEW_COUNT)
    val canToggle = state.albums.size > ALBUM_PREVIEW_COUNT

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CategoryShortcutsRow(
                showFavorites = state.showFavorites,
                onSelectCategory = onSelectCategory,
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gallery_albums_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (canToggle) {
                    TextButton(onClick = { albumsExpanded = !albumsExpanded }) {
                        Text(
                            stringResource(
                                if (albumsExpanded) R.string.gallery_see_less
                                else R.string.gallery_see_all
                            )
                        )
                    }
                }
            }
        }
        items(visibleAlbums, key = { it.bucketId }) { album ->
            AlbumGridTile(album = album, onClick = { onSelectAlbum(album) })
        }
    }
}

/** Rangée de raccourcis circulaires (icône dans un cercle + libellé dessous). */
@Composable
private fun CategoryShortcutsRow(
    showFavorites: Boolean,
    onSelectCategory: (GalleryCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        CategoryShortcut(
            labelRes = R.string.gallery_album_recents,
            icon = Icons.Default.Schedule,
            onClick = { onSelectCategory(GalleryCategory.RECENTS) },
        )
        if (showFavorites) {
            CategoryShortcut(
                labelRes = R.string.gallery_category_favorites,
                icon = Icons.Default.Favorite,
                onClick = { onSelectCategory(GalleryCategory.FAVORITES) },
            )
        }
        CategoryShortcut(
            labelRes = R.string.gallery_category_photos,
            icon = Icons.Outlined.PhotoLibrary,
            onClick = { onSelectCategory(GalleryCategory.PHOTOS) },
        )
    }
}

@Composable
private fun CategoryShortcut(labelRes: Int, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Tuile d'album : couverture carrée arrondie + nom + compteur de photos. */
@Composable
private fun AlbumGridTile(album: GalleryAlbum, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(album.coverUri)
                .size(300)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.gallery_photo_count, album.imageCount, album.imageCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
        )
    }
}

// ── États transverses ────────────────────────────────────────────────────────

@Composable
private fun CenteredLoader() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Écran « permission requise » : explication AVANT la demande, puis paramètres. */
@Composable
private fun GalleryPermissionContent(
    showSettingsButton: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.gallery_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.gallery_permission_grant))
        }
        if (showSettingsButton) {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.gallery_permission_settings))
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Titre du header : nom d'album, libellé de catégorie, ou « Sélectionner un album ». */
@Composable
private fun headerTitle(state: GalleryPickerUiState): String =
    if (state.viewMode == GalleryViewMode.ALBUM_LIST) {
        stringResource(R.string.gallery_select_album_title)
    } else when (val sel = state.selection) {
        is GallerySelection.Album -> sel.album.name
        is GallerySelection.Category -> stringResource(categoryLabelRes(sel.category))
    }

private fun categoryLabelRes(category: GalleryCategory): Int = when (category) {
    GalleryCategory.RECENTS -> R.string.gallery_album_recents
    GalleryCategory.FAVORITES -> R.string.gallery_category_favorites
    GalleryCategory.PHOTOS -> R.string.gallery_category_photos
}

/** Clé stable identifiant la sélection (réinitialise l'état de scroll de la grille). */
private fun selectionKey(selection: GallerySelection): String = when (selection) {
    is GallerySelection.Album -> "album:${selection.album.bucketId}"
    is GallerySelection.Category -> "cat:${selection.category}"
}

/** Permissions de lecture d'images selon la version d'Android. */
private fun galleryReadPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * Niveau d'accès effectif. Sur Android 14+, l'accès PARTIEL (« Sélectionner des
 * photos ») accorde READ_MEDIA_VISUAL_USER_SELECTED sans READ_MEDIA_IMAGES : le
 * MediaStore ne retourne alors que la sélection de l'utilisateur — la grille
 * reste pleinement fonctionnelle.
 */
private fun galleryAccessLevel(context: Context): GalleryAccess {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.FULL
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.PARTIAL
            else -> GalleryAccess.NONE
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            if (granted(Manifest.permission.READ_MEDIA_IMAGES)) GalleryAccess.FULL
            else GalleryAccess.NONE
        else ->
            if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) GalleryAccess.FULL
            else GalleryAccess.NONE
    }
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )
}
