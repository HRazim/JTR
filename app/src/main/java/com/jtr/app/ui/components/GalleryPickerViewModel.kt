package com.jtr.app.ui.components

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.GalleryAlbum
import com.jtr.app.data.repository.MediaStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Vue affichée par la feuille : grille de miniatures ou navigateur d'albums. */
enum class GalleryViewMode { GRID, ALBUM_LIST }

/**
 * Catégorie virtuelle (raccourcis du menu déroulant et de la vue « Tous les
 * albums »). « Vidéos » est volontairement absent : JTR ne sélectionne que des
 * images pour les photos de profil/couverture.
 */
enum class GalleryCategory { RECENTS, FAVORITES, PHOTOS }

/**
 * Source courante de la grille : soit une catégorie virtuelle, soit un dossier
 * réel. Type scellé → impossible d'avoir simultanément « catégorie » ET
 * « album » actifs (états illégaux exclus par construction).
 */
sealed interface GallerySelection {
    data class Category(val category: GalleryCategory) : GallerySelection
    data class Album(val album: GalleryAlbum) : GallerySelection
}

/**
 * État UDF de la galerie in-app « façon Instagram ».
 *
 * @param viewMode grille de miniatures (C.1) ou navigateur d'albums plein écran (C.3).
 * @param selection catégorie ou album dont la grille est affichée.
 * @param albums dossiers disponibles (couverture + compteur).
 * @param images URIs de la sélection courante, triées DATE_ADDED DESC.
 * @param allCount / allCover total et couverture de « Récents » / « Photos ».
 * @param favoritesSupported / favoriteCount / favoriteCover métadonnées « Favoris ».
 */
data class GalleryPickerUiState(
    val isLoading: Boolean = true,
    val viewMode: GalleryViewMode = GalleryViewMode.GRID,
    val selection: GallerySelection = GallerySelection.Category(GalleryCategory.RECENTS),
    val albums: List<GalleryAlbum> = emptyList(),
    val images: List<Uri> = emptyList(),
    val allCount: Int = 0,
    val allCover: Uri? = null,
    val favoritesSupported: Boolean = false,
    val favoriteCount: Int = 0,
    val favoriteCover: Uri? = null,
) {
    /** Vrai si l'entrée « Favoris » doit apparaître (supportée ET non vide). */
    val showFavorites: Boolean get() = favoritesSupported && favoriteCount > 0
}

/**
 * ViewModel de [CustomImagePickerBottomSheet]. Toutes les lectures MediaStore
 * passent par [MediaStoreRepository] (Dispatchers.IO) ; l'UI ne consomme que le
 * [uiState] réactif. La gestion des permissions reste côté Composable (liée aux
 * launchers d'Activity) et déclenche [refresh] dès que l'accès est accordé.
 */
class GalleryPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(GalleryPickerUiState())
    val uiState: StateFlow<GalleryPickerUiState> = _uiState.asStateFlow()

    /**
     * (Re)charge la photothèque puis les images de la sélection courante. Appelé
     * à chaque (ré)octroi d'accès — la galerie a pu changer entre deux ouvertures
     * (photo prise, suppression, accès partiel élargi). Conserve l'album/la
     * catégorie en cours s'ils restent valides, sinon retombe sur « Récents ».
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val library = repository.loadLibrary()

            val selection = when (val current = _uiState.value.selection) {
                is GallerySelection.Album ->
                    library.albums.firstOrNull { it.bucketId == current.album.bucketId }
                        ?.let { GallerySelection.Album(it) }
                        ?: GallerySelection.Category(GalleryCategory.RECENTS)

                is GallerySelection.Category ->
                    if (current.category == GalleryCategory.FAVORITES &&
                        !(library.favoritesSupported && library.favoriteCount > 0)
                    ) GallerySelection.Category(GalleryCategory.RECENTS) else current
            }

            val images = loadImagesFor(selection)
            _uiState.value = GalleryPickerUiState(
                isLoading = false,
                viewMode = GalleryViewMode.GRID,
                selection = selection,
                albums = library.albums,
                images = images,
                allCount = library.allCount,
                allCover = library.allCover,
                favoritesSupported = library.favoritesSupported,
                favoriteCount = library.favoriteCount,
                favoriteCover = library.favoriteCover,
            )
        }
    }

    /** Sélectionne une catégorie virtuelle et revient à la grille. */
    fun selectCategory(category: GalleryCategory) {
        select(GallerySelection.Category(category))
    }

    /** Sélectionne un dossier réel et revient à la grille. */
    fun selectAlbum(album: GalleryAlbum) {
        select(GallerySelection.Album(album))
    }

    private fun select(selection: GallerySelection) {
        if (selection == _uiState.value.selection &&
            _uiState.value.viewMode == GalleryViewMode.GRID
        ) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selection = selection,
                    viewMode = GalleryViewMode.GRID,
                    isLoading = true,
                )
            }
            val images = loadImagesFor(selection)
            _uiState.update { it.copy(images = images, isLoading = false) }
        }
    }

    /** Bascule vers le navigateur d'albums plein écran (C.3). */
    fun openAlbumList() {
        _uiState.update { it.copy(viewMode = GalleryViewMode.ALBUM_LIST) }
    }

    /** Revient à la grille de miniatures depuis le navigateur d'albums. */
    fun closeAlbumList() {
        _uiState.update { it.copy(viewMode = GalleryViewMode.GRID) }
    }

    private suspend fun loadImagesFor(selection: GallerySelection): List<Uri> =
        when (selection) {
            is GallerySelection.Album ->
                repository.loadImages(selection.album.bucketId)

            is GallerySelection.Category -> when (selection.category) {
                GalleryCategory.RECENTS,
                GalleryCategory.PHOTOS -> repository.loadImages(bucketId = null)
                GalleryCategory.FAVORITES -> repository.loadImages(null, favoritesOnly = true)
            }
        }
}
