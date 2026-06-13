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

/**
 * État UDF de la galerie in-app : albums disponibles, album sélectionné et
 * images de cet album (URIs triées DATE_ADDED DESC).
 */
data class GalleryPickerUiState(
    val isLoading: Boolean = true,
    val albums: List<GalleryAlbum> = emptyList(),
    val selectedAlbum: GalleryAlbum? = null,
    val images: List<Uri> = emptyList(),
)

/**
 * ViewModel de [CustomImagePickerBottomSheet]. Toutes les lectures MediaStore
 * passent par [MediaStoreRepository] (Dispatchers.IO) ; l'UI ne consomme que
 * le [uiState] réactif.
 */
class GalleryPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(GalleryPickerUiState())
    val uiState: StateFlow<GalleryPickerUiState> = _uiState.asStateFlow()

    /**
     * (Re)charge albums et images. Appelé à chaque ouverture de la feuille —
     * la galerie a pu changer entre deux ouvertures (photo prise, suppression…).
     * Conserve l'album sélectionné s'il existe encore, sinon retombe sur
     * « Récents » (premier de la liste).
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val albums = repository.loadAlbums()
            val previousId = _uiState.value.selectedAlbum?.bucketId
            val selected = albums.firstOrNull { it.bucketId == previousId } ?: albums.firstOrNull()
            val images = selected?.let { repository.loadImages(it.bucketId) }.orEmpty()
            _uiState.value = GalleryPickerUiState(
                isLoading = false,
                albums = albums,
                selectedAlbum = selected,
                images = images,
            )
        }
    }

    fun onAlbumSelected(album: GalleryAlbum) {
        if (album.bucketId == _uiState.value.selectedAlbum?.bucketId) return
        viewModelScope.launch {
            _uiState.update { it.copy(selectedAlbum = album, isLoading = true) }
            val images = repository.loadImages(album.bucketId)
            _uiState.update { it.copy(images = images, isLoading = false) }
        }
    }
}
