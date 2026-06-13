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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jtr.app.R
import com.jtr.app.data.repository.GalleryAlbum

/**
 * Sélecteur d'image IN-APP « style Instagram » : feuille modale affichant les
 * albums (buckets MediaStore) dans un menu déroulant et les miniatures de
 * l'album courant dans une grille — l'utilisateur ne quitte JAMAIS
 * l'application, contrairement à l'ancien `ACTION_PICK` vers la galerie
 * native (v5.3.4).
 *
 * L'écran appelant n'est pas recréé et aucun aller-retour d'Activity n'a
 * lieu : l'état du formulaire (ViewModel) est intégralement préservé
 * (esprit UDF v5.3.3). L'URI retournée alimente le workflow de recadrage
 * existant (ImageCropDialog) exactement comme avant.
 *
 * Permissions : READ_MEDIA_IMAGES (API 33+) ou READ_EXTERNAL_STORAGE
 * (API < 33), demandées dans le flux UI à l'ouverture de la feuille.
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

/**
 * Feuille modale de la galerie in-app : header avec menu déroulant des albums
 * (« Récents », « Camera », « WhatsApp Images »…) et grille de tuiles carrées
 * chargées en asynchrone par Coil (miniatures sous-échantillonnées — pas
 * d'OutOfMemory sur les grosses galeries).
 *
 * Gère elle-même la permission de lecture : demande système à l'ouverture si
 * nécessaire, et état explicatif avec re-demande / accès aux paramètres en
 * cas de refus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomImagePickerBottomSheet(
    onImagePicked: (Uri) -> Unit,
    onDismiss: () -> Unit,
    viewModel: GalleryPickerViewModel = viewModel(),
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasGalleryReadPermission(context)) }
    // Vrai après un refus explicite : on propose alors les paramètres système,
    // car Android cesse d'afficher le dialogue après deux refus.
    var wasDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = hasGalleryReadPermission(context)
        if (!hasPermission) wasDenied = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(galleryReadPermissions())
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.refresh()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
        ) {
            when {
                !hasPermission -> GalleryPermissionContent(
                    showSettingsButton = wasDenied,
                    onRequest = { permissionLauncher.launch(galleryReadPermissions()) },
                    onOpenSettings = { context.openAppSettings() },
                )

                else -> {
                    AlbumDropdownHeader(
                        albums = state.albums,
                        selected = state.selectedAlbum,
                        onAlbumSelected = viewModel::onAlbumSelected,
                    )
                    when {
                        state.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        state.images.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.gallery_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // État de scroll EXPLICITE : survit aux recompositions de la
                        // feuille (sélection, focus…). Le changement d'album passe par
                        // l'état « chargement » qui retire cette branche de la
                        // composition → la position repart proprement du haut.
                        else -> ImageGrid(
                            images = state.images,
                            gridState = rememberLazyGridState(),
                            onImagePicked = onImagePicked
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header « Instagram » : nom de l'album courant + flèche, ouvrant un menu
 * déroulant listant chaque album avec sa miniature de couverture et son
 * nombre de photos.
 */
@Composable
private fun AlbumDropdownHeader(
    albums: List<GalleryAlbum>,
    selected: GalleryAlbum?,
    onAlbumSelected: (GalleryAlbum) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val recentsLabel = stringResource(R.string.gallery_album_recents)
    val context = LocalContext.current

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = albums.isNotEmpty()) { expanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = selected?.displayName(recentsLabel)
                    ?: stringResource(R.string.gallery_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.gallery_album_dropdown_cd),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            albums.forEach { album ->
                DropdownMenuItem(
                    leadingIcon = {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(album.coverUri)
                                .size(96)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = album.displayName(recentsLabel),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(
                                    R.string.gallery_photo_count, album.imageCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onAlbumSelected(album)
                    },
                )
            }
        }
    }
}

/**
 * Grille 3 colonnes de tuiles carrées. Coil sous-échantillonne chaque image à
 * la taille de la tuile (`size(300)`) : seules les miniatures résident en
 * mémoire, jamais les pleines résolutions.
 */
@Composable
private fun ImageGrid(
    images: List<Uri>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onImagePicked: (Uri) -> Unit,
) {
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // Clé stable = URI de l'image : la position de scroll et le recyclage des
        // tuiles restent corrects à travers les recompositions.
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

/** État « permission refusée » : explication + re-demande, puis paramètres. */
@Composable
private fun GalleryPermissionContent(
    showSettingsButton: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
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
            modifier = Modifier.padding(vertical = 16.dp),
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

/** Libellé d'affichage : l'album virtuel (bucketId null) devient « Récents ». */
private fun GalleryAlbum.displayName(recentsLabel: String): String =
    if (bucketId == null) recentsLabel else name ?: "—"

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
 * Accès lecture effectif. Sur Android 14+, l'accès PARTIEL (« Sélectionner
 * des photos ») accorde READ_MEDIA_VISUAL_USER_SELECTED sans
 * READ_MEDIA_IMAGES : le MediaStore ne retourne alors que la sélection de
 * l'utilisateur — la grille reste pleinement fonctionnelle.
 */
private fun hasGalleryReadPermission(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
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
