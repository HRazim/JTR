package com.jtr.app.ui.person

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Tout le state du formulaire vit ici pour survivre à la navigation vers MapScreen.
 * SavedStateHandle fournit les résultats de la carte automatiquement.
 */
class AddPersonViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)

    // --- Map results (auto-populated by Navigation when returning from MapScreen) ---
    val mapCity: StateFlow<String?> = savedStateHandle.getStateFlow("selected_city", null)
    val mapLat: StateFlow<Double?> = savedStateHandle.getStateFlow("selected_lat", null)
    val mapLng: StateFlow<Double?> = savedStateHandle.getStateFlow("selected_lng", null)

    // --- Form state (survives navigation, no remember needed in composable) ---
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

    init {
        // Sync map result into city fields as soon as we return from MapScreen.
        // Read lat/lng via savedStateHandle.get() directly to avoid any StateFlow timing issue —
        // all three keys are set synchronously in Navigation before popBackStack().
        viewModelScope.launch {
            mapCity.collect { newCity ->
                if (newCity != null) {
                    _city.value = newCity
                    _cityLat.value = savedStateHandle.get<Double>("selected_lat")
                    _cityLng.value = savedStateHandle.get<Double>("selected_lng")
                }
            }
        }
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
            if (_cityLat.value != null && _cityLng.value != null) {
                repository.add(person)
            } else {
                repository.addWithGeocoding(person)
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
