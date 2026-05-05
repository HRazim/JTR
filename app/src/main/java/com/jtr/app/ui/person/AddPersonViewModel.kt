package com.jtr.app.ui.person

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.extractSocialLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Lien social en attente de persistance (avant que le personId soit connu). */
data class PendingLink(val url: String, val platform: String)

/**
 * Tout le state du formulaire vit ici pour survivre à la navigation vers MapScreen.
 * SavedStateHandle fournit les résultats de la carte automatiquement.
 *
 * Si la route contient un categoryId, le nouveau contact est automatiquement
 * assigné à cette catégorie lors de la sauvegarde (Many-to-Many).
 */
class AddPersonViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)

    // categoryId transmis depuis CategoryDetailScreen (peut être null ou vide)
    private val presetCategoryId: String? =
        savedStateHandle.get<String>("categoryId")?.takeIf { it.isNotBlank() }

    // --- Form state ---
    private val _firstName = MutableStateFlow("")
    val firstName: StateFlow<String> = _firstName.asStateFlow()

    private val _lastName = MutableStateFlow("")
    val lastName: StateFlow<String> = _lastName.asStateFlow()

    private val _gender = MutableStateFlow<String?>(null)
    val gender: StateFlow<String?> = _gender.asStateFlow()

    private val _birthdate = MutableStateFlow<Long?>(null)
    val birthdate: StateFlow<Long?> = _birthdate.asStateFlow()

    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()

    private val _cityLat = MutableStateFlow<Double?>(null)
    val cityLat: StateFlow<Double?> = _cityLat.asStateFlow()

    private val _cityLng = MutableStateFlow<Double?>(null)
    val cityLng: StateFlow<Double?> = _cityLng.asStateFlow()

    private val _origin = MutableStateFlow("")
    val origin: StateFlow<String> = _origin.asStateFlow()

    private val _likes = MutableStateFlow("")
    val likes: StateFlow<String> = _likes.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    private val _firstNameError = MutableStateFlow(false)
    val firstNameError: StateFlow<Boolean> = _firstNameError.asStateFlow()

    // Liens sociaux en attente — persistés vers Room après création de la personne
    private val _pendingLinks = MutableStateFlow<List<PendingLink>>(emptyList())
    val pendingLinks: StateFlow<List<PendingLink>> = _pendingLinks.asStateFlow()

    fun addPendingLink(url: String) {
        val trimmed = url.trim().takeIf { it.isNotBlank() } ?: return
        if (_pendingLinks.value.any { it.url == trimmed }) return
        val platform = extractSocialLinks(trimmed).firstOrNull()?.platform?.displayName ?: "Lien"
        _pendingLinks.value = _pendingLinks.value + PendingLink(trimmed, platform)
    }

    fun removePendingLink(url: String) {
        _pendingLinks.value = _pendingLinks.value.filter { it.url != url }
    }

    fun onCityFromMap(city: String, lat: Double?, lng: Double?) {
        _city.value = city
        _cityLat.value = lat
        _cityLng.value = lng
    }

    fun onFirstNameChanged(v: String) { _firstName.value = v; _firstNameError.value = false }
    fun onLastNameChanged(v: String) { _lastName.value = v }
    fun onGenderChanged(v: String?) { _gender.value = v }
    fun onBirthdateChanged(v: Long?) { _birthdate.value = v }
    fun onCityChanged(v: String) { _city.value = v; _cityLat.value = null; _cityLng.value = null }
    fun onOriginChanged(v: String) { _origin.value = v }
    fun onLikesChanged(v: String) { _likes.value = v }
    fun onNotesChanged(v: String) { _notes.value = v }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyPhotoToStorage(uri) }
            _photoUri.value = path
        }
    }

    fun savePerson(onSuccess: () -> Unit) {
        if (_firstName.value.isBlank()) { _firstNameError.value = true; return }
        viewModelScope.launch {
            val person = Person(
                firstName = _firstName.value.trim(),
                lastName = _lastName.value.trim().ifBlank { null },
                gender = _gender.value,
                birthdate = _birthdate.value,
                city = _city.value.trim().ifBlank { null },
                cityLat = _cityLat.value,
                cityLng = _cityLng.value,
                photoUri = _photoUri.value,
                notes = _notes.value.trim().ifBlank { null },
                likes = _likes.value.trim().ifBlank { null },
                origin = _origin.value.trim().ifBlank { null }
            )

            val hasCoords = _cityLat.value != null && _cityLng.value != null

            if (presetCategoryId != null) {
                if (hasCoords) repository.addToCategory(person, presetCategoryId)
                else repository.addWithGeocodingToCategory(person, presetCategoryId)
            } else {
                if (hasCoords) repository.add(person)
                else repository.addWithGeocoding(person)
            }

            // L'ID de la personne est connu dès sa création (UUID local) — on peut
            // insérer tous les liens en attente sans attendre le retour de Room.
            _pendingLinks.value.forEach { link ->
                repository.addSocialLink(
                    SocialLinkEntity(personId = person.id, url = link.url, platform = link.platform)
                )
            }

            onSuccess()
        }
    }

    private fun copyPhotoToStorage(uri: Uri): String? = try {
        val context = getApplication<Application>().applicationContext
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        val dest = File(dir, "profile_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        dest.absolutePath
    } catch (_: Exception) { null }
}
