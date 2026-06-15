package com.jtr.app.ui.person

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.extractSocialLinks
import com.jtr.app.worker.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
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
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    /** La notif de proximité ne peut être vraie que si tout est activé globalement. */
    private fun proximityAllowed(): Boolean =
        prefs.getBoolean("notifications_enabled", true) &&
            prefs.getBoolean("proximity_enabled", false)

    // categoryId transmis depuis CategoryDetailScreen (peut être null ou vide)
    private val presetCategoryId: String? =
        savedStateHandle.get<String>("categoryId")?.takeIf { it.isNotBlank() }

    // --- Form state ---
    private val _firstName = MutableStateFlow("")
    val firstName: StateFlow<String> = _firstName.asStateFlow()

    private val _lastName = MutableStateFlow("")
    val lastName: StateFlow<String> = _lastName.asStateFlow()

    private val _city = MutableStateFlow("")
    val city: StateFlow<String> = _city.asStateFlow()

    private val _cityLat = MutableStateFlow<Double?>(null)
    val cityLat: StateFlow<Double?> = _cityLat.asStateFlow()

    private val _cityLng = MutableStateFlow<Double?>(null)
    val cityLng: StateFlow<Double?> = _cityLng.asStateFlow()

    private val _origin = MutableStateFlow("")
    val origin: StateFlow<String> = _origin.asStateFlow()

    private val _jobTitle = MutableStateFlow("")
    val jobTitle: StateFlow<String> = _jobTitle.asStateFlow()

    private val _department = MutableStateFlow("")
    val department: StateFlow<String> = _department.asStateFlow()

    private val _company = MutableStateFlow("")
    val company: StateFlow<String> = _company.asStateFlow()

    private val _likes = MutableStateFlow("")
    val likes: StateFlow<String> = _likes.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    // ── Listes dynamiques « Contacts Google » (v4.5) ──────────────────────────
    private val _nameDetails = MutableStateFlow(NameDetails())
    val nameDetails: StateFlow<NameDetails> = _nameDetails.asStateFlow()

    private val _phoneLines = MutableStateFlow(listOf(DynamicLine(label = FieldTypes.PHONE_MOBILE)))
    val phoneLines: StateFlow<List<DynamicLine>> = _phoneLines.asStateFlow()

    private val _emailLines = MutableStateFlow(listOf(DynamicLine(label = FieldTypes.EMAIL_HOME)))
    val emailLines: StateFlow<List<DynamicLine>> = _emailLines.asStateFlow()

    private val _dateLines = MutableStateFlow(listOf(DynamicLine(label = FieldTypes.DATE_BIRTHDAY)))
    val dateLines: StateFlow<List<DynamicLine>> = _dateLines.asStateFlow()

    private val _relationLines = MutableStateFlow(listOf(DynamicLine(label = FieldTypes.RELATION_FRIEND)))
    val relationLines: StateFlow<List<DynamicLine>> = _relationLines.asStateFlow()

    /** Noms des contacts existants — alimente l'autocomplétion des relations. */
    val relationSuggestions: StateFlow<List<String>> = repository.getAllActive()
        .map { list -> list.map { it.fullName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onNameDetailsChanged(v: NameDetails) { _nameDetails.value = v }
    fun onPhoneLinesChanged(v: List<DynamicLine>) { _phoneLines.value = v }
    fun onEmailLinesChanged(v: List<DynamicLine>) { _emailLines.value = v }
    fun onDateLinesChanged(v: List<DynamicLine>) { _dateLines.value = v }
    fun onRelationLinesChanged(v: List<DynamicLine>) { _relationLines.value = v }
    // ──────────────────────────────────────────────────────────────────────────

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    private val _firstNameError = MutableStateFlow(false)
    val firstNameError: StateFlow<Boolean> = _firstNameError.asStateFlow()

    private val _cityNotify = MutableStateFlow(false)
    val cityNotify: StateFlow<Boolean> = _cityNotify.asStateFlow()

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
    fun onCityChanged(v: String) { _city.value = v; _cityLat.value = null; _cityLng.value = null }
    fun onCityNotifyChanged(v: Boolean) { _cityNotify.value = v }
    fun onOriginChanged(v: String) { _origin.value = v }
    fun onJobTitleChanged(v: String) { _jobTitle.value = v }
    fun onDepartmentChanged(v: String) { _department.value = v }
    fun onCompanyChanged(v: String) { _company.value = v }
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
            // Listes complètes persistées en JSON ; scalaires `phoneNumber`/`email`/
            // `birthdate` = projection « 1ère ligne » dénormalisée pour les workers,
            // cartes et actions rapides qui lisent encore ces colonnes.
            val spec = resolveDateFormatSpec(Locale.getDefault())
            val phone = _phoneLines.value.firstOrNull { it.value.isNotBlank() }?.value?.trim()
            // Projection scalaire : 1er email syntaxiquement valide (contenant « @ »).
            val email = _emailLines.value
                .firstOrNull { it.value.isNotBlank() && it.value.contains('@') }?.value?.trim()
            val birthday = _dateLines.value
                .filter { it.label == FieldTypes.DATE_BIRTHDAY }
                .firstNotNullOfOrNull { rawDigitsToMillis(it.value, spec) }
            // birthdateNotify dénormalise désormais la cloche de la ligne anniversaire.
            val notifyBirthday = _dateLines.value
                .any { it.label == FieldTypes.DATE_BIRTHDAY && it.notify }
            // …et birthdateReminderOffsetMinutes le délai de rappel de cette même ligne.
            val birthdayOffset = _dateLines.value
                .firstOrNull { it.label == FieldTypes.DATE_BIRTHDAY }?.reminderOffsetMinutes ?: 0
            val nd = _nameDetails.value

            val person = Person(
                firstName = _firstName.value.trim(),
                lastName = _lastName.value.trim().ifBlank { null },
                birthdate = birthday,
                birthdateNotify = notifyBirthday,
                birthdateReminderOffsetMinutes = birthdayOffset,
                city = _city.value.trim().ifBlank { null },
                cityLat = _cityLat.value,
                cityLng = _cityLng.value,
                cityNotify = _cityNotify.value && proximityAllowed(),
                photoUri = _photoUri.value,
                notes = _notes.value.trim().ifBlank { null },
                likes = _likes.value.trim().ifBlank { null },
                origin = _origin.value.trim().ifBlank { null },
                jobTitle = _jobTitle.value.trim().ifBlank { null },
                department = _department.value.trim().ifBlank { null },
                company = _company.value.trim().ifBlank { null },
                phoneNumber = phone,
                email = email,
                prefix = nd.prefix.trim().ifBlank { null },
                middleName = nd.middleName.trim().ifBlank { null },
                suffix = nd.suffix.trim().ifBlank { null },
                phonetic = nd.phonetic.trim().ifBlank { null },
                nickname = nd.nickname.trim().ifBlank { null },
                phoneLines = sanitizeLines(_phoneLines.value),
                emailLines = sanitizeEmailLines(_emailLines.value),
                dateLines = sanitizeLines(_dateLines.value),
                relationLines = sanitizeLines(_relationLines.value)
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

            // Relations miroirs (v5.4.1) : création → toutes les relations sont
            // « nouvelles » (aucune snapshot antérieure).
            repository.syncMirrorRelations(person, previousLines = null)

            // Réarme les rappels (délais par date) immédiatement après l'écriture en base :
            // appel DIRECT (pas via WorkManager) → ni report Doze ni course avec la base. Une
            // date déjà due aujourd'hui est rattrapée à l'instant par [rescheduleAll].
            ReminderScheduler.rescheduleAll(getApplication())

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
