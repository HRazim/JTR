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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EditPersonViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)

    private val _person = MutableStateFlow<Person?>(null)
    val person: StateFlow<Person?> = _person.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Mode édition globale ──────────────────────────────────────────────────
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    fun enterEditMode() { _isEditing.value = true }
    // ─────────────────────────────────────────────────────────────────────────

    // ── Champs formulaire ─────────────────────────────────────────────────────
    private val _firstName = MutableStateFlow("")
    val firstName: StateFlow<String> = _firstName.asStateFlow()

    private val _lastName = MutableStateFlow("")
    val lastName: StateFlow<String> = _lastName.asStateFlow()

    private val _gender = MutableStateFlow<String?>(null)
    val gender: StateFlow<String?> = _gender.asStateFlow()

    private val _birthdate = MutableStateFlow<Long?>(null)
    val birthdate: StateFlow<Long?> = _birthdate.asStateFlow()

    private val _birthdateNotify = MutableStateFlow(false)
    val birthdateNotify: StateFlow<Boolean> = _birthdateNotify.asStateFlow()

    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()

    private val _cityLat = MutableStateFlow<Double?>(null)
    val cityLat: StateFlow<Double?> = _cityLat.asStateFlow()

    private val _cityLng = MutableStateFlow<Double?>(null)
    val cityLng: StateFlow<Double?> = _cityLng.asStateFlow()

    private val _cityNotify = MutableStateFlow(false)
    val cityNotify: StateFlow<Boolean> = _cityNotify.asStateFlow()

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
    // ─────────────────────────────────────────────────────────────────────────

    // ── Liens sociaux (réactifs depuis Room) ──────────────────────────────────
    @OptIn(ExperimentalCoroutinesApi::class)
    val socialLinks: StateFlow<List<SocialLinkEntity>> = _person
        .flatMapLatest { p ->
            if (p != null) repository.getSocialLinks(p.id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addSocialLink(url: String) {
        val p = _person.value ?: return
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val platform = extractSocialLinks(trimmed).firstOrNull()?.platform?.displayName ?: "Lien"
        viewModelScope.launch {
            repository.addSocialLink(SocialLinkEntity(personId = p.id, url = trimmed, platform = platform))
        }
    }

    fun removeSocialLink(id: String) {
        viewModelScope.launch { repository.removeSocialLink(id) }
    }
    // ─────────────────────────────────────────────────────────────────────────

    private var personLoaded = false

    fun onCityFromMap(city: String, lat: Double?, lng: Double?) {
        _city.value = city
        _cityLat.value = lat
        _cityLng.value = lng
    }

    fun loadPerson(personId: String) {
        if (personLoaded) return
        viewModelScope.launch {
            _isLoading.value = true
            val p = repository.getById(personId)
            if (p != null) populateFields(p)
            _person.value = p
            _isLoading.value = false
        }
    }

    private fun populateFields(p: Person) {
        _firstName.value = p.firstName
        _lastName.value = p.lastName ?: ""
        _gender.value = p.gender
        _birthdate.value = p.birthdate
        _birthdateNotify.value = p.birthdateNotify
        _city.value = p.city ?: ""
        _cityLat.value = p.cityLat
        _cityLng.value = p.cityLng
        _cityNotify.value = p.cityNotify
        _origin.value = p.origin ?: ""
        _likes.value = p.likes ?: ""
        _notes.value = p.notes ?: ""
        _photoUri.value = p.photoUri
        personLoaded = true
    }

    fun onFirstNameChanged(v: String)    { _firstName.value = v; _firstNameError.value = false }
    fun onLastNameChanged(v: String)     { _lastName.value = v }
    fun onGenderChanged(v: String?)      { _gender.value = v }
    fun onBirthdateChanged(v: Long?)     { _birthdate.value = v }
    fun onBirthdateNotifyChanged(v: Boolean) { _birthdateNotify.value = v }
    fun onCityChanged(v: String)         { _city.value = v; _cityLat.value = null; _cityLng.value = null }
    fun onCityNotifyChanged(v: Boolean)  { _cityNotify.value = v }
    fun onOriginChanged(v: String)       { _origin.value = v }
    fun onLikesChanged(v: String)        { _likes.value = v }
    fun onNotesChanged(v: String)        { _notes.value = v }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyPhotoToStorage(uri) }
            _photoUri.value = path
        }
    }

    /** Sauvegarde globale depuis PersonDetailScreen — reste sur l'écran. */
    fun commitAllEdits() {
        val p = _person.value ?: return
        if (_firstName.value.isBlank()) { _firstNameError.value = true; return }
        viewModelScope.launch {
            val updated = buildUpdatedPerson(p)
            if (_city.value.trim() != (p.city ?: "") && _cityLat.value == null) {
                repository.updateWithGeocoding(updated, p.city)
            } else {
                repository.update(updated)
            }
            _person.value = updated
            _isEditing.value = false
        }
    }

    /** Annule le mode édition et restaure les champs depuis la snapshot locale. */
    fun cancelEdit() {
        val p = _person.value ?: run { _isEditing.value = false; return }
        populateFields(p)
        _firstNameError.value = false
        _isEditing.value = false
    }

    /** Sauvegarde et navigue — utilisé depuis EditPersonScreen. */
    fun updatePerson(onSuccess: () -> Unit) {
        val p = _person.value ?: return
        if (_firstName.value.isBlank()) { _firstNameError.value = true; return }
        viewModelScope.launch {
            val updated = buildUpdatedPerson(p)
            if (_city.value.trim() != (p.city ?: "") && _cityLat.value == null) {
                repository.updateWithGeocoding(updated, p.city)
            } else {
                repository.update(updated)
            }
            onSuccess()
        }
    }

    private fun buildUpdatedPerson(p: Person) = p.copy(
        firstName      = _firstName.value.trim(),
        lastName       = _lastName.value.trim().ifBlank { null },
        gender         = _gender.value,
        birthdate      = _birthdate.value,
        birthdateNotify = _birthdateNotify.value,
        city           = _city.value.trim().ifBlank { null },
        cityLat        = _cityLat.value,
        cityLng        = _cityLng.value,
        cityNotify     = _cityNotify.value,
        photoUri       = _photoUri.value,
        notes          = _notes.value.trim().ifBlank { null },
        likes          = _likes.value.trim().ifBlank { null },
        origin         = _origin.value.trim().ifBlank { null }
    )

    private fun copyPhotoToStorage(uri: Uri): String? = try {
        val context = getApplication<Application>().applicationContext
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        val dest = File(dir, "profile_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        dest.absolutePath
    } catch (_: Exception) { null }
}
