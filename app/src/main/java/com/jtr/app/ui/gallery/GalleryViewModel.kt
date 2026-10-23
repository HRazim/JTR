package com.jtr.app.ui.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.GalleryRepository
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.GalleryImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la GALERIE d'un contact (v7.1.89). MVVM/UDF strict : l'écran n'observe que des
 * [StateFlow] en lecture seule et remonte des intentions. Room v23, aucune migration.
 */
class GalleryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application.applicationContext)
    private val personRepository = PersonRepository(application.applicationContext)

    val personId: String = checkNotNull(savedStateHandle["personId"])

    /** Images du contact — source unique de la grille ET de la visionneuse. */
    val images: StateFlow<List<GalleryImage>> = repository.observeByPerson(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Nom du contact pour le titre ; vide tant que la lecture n'a pas émis. */
    val personName: StateFlow<String> = personRepository.observeById(personId)
        .map { it?.fullName ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Import en cours : masque le bouton d'ajout et affiche une progression. */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /**
     * Ajoute les images choisies (sélection multiple). Le redimensionnement, la compression
     * et l'écriture se font hors du thread principal (cf. [GalleryRepository]).
     */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                repository.addImages(personId, uris)
            } finally {
                _isImporting.value = false
            }
        }
    }

    /**
     * v7.1.94 — enregistre la description saisie dans le panneau de détails. Écriture hors
     * du thread principal (cf. [GalleryRepository]) ; le Flow [images] rediffuse la ligne.
     */
    fun setCaption(imageId: Long, caption: String?) {
        viewModelScope.launch { repository.setCaption(imageId, caption) }
    }

    /** v7.1.94 — enregistre la date + heure choisies dans le panneau de détails. */
    fun setCreatedAt(imageId: Long, createdAt: Long) {
        viewModelScope.launch { repository.setCreatedAt(imageId, createdAt) }
    }

    /**
     * v7.1.95 — enregistre le lieu choisi dans le sélecteur de carte, ou le RETIRE quand les
     * trois valeurs sont `null`.
     */
    fun setLocation(imageId: Long, latitude: Double?, longitude: Double?, placeLabel: String?) {
        viewModelScope.launch {
            repository.setLocation(imageId, latitude, longitude, placeLabel)
        }
    }

    /** Supprime une image : la ligne puis son fichier. */
    fun delete(image: GalleryImage) {
        viewModelScope.launch { repository.delete(image) }
    }

    /** v7.1.90 — supprime toute une sélection, même chemin unitaire appliqué en lot. */
    fun deleteAll(images: List<GalleryImage>) {
        if (images.isEmpty()) return
        viewModelScope.launch { repository.deleteAll(images) }
    }
}
