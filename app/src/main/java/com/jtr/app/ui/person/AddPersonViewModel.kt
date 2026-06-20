package com.jtr.app.ui.person

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.JTRApplication
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.DynamicLine
import com.jtr.app.domain.model.NoteSection
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.utils.extractSocialLinks
import com.jtr.app.worker.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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

    // ── Auto-save / brouillon (v7.1.4) ────────────────────────────────────────
    // Scope SURVIVANT (applicatif) pour le flush final ; repli local hors JTRApplication (tests).
    private val appScope: CoroutineScope =
        (application as? JTRApplication)?.applicationScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Id du BROUILLON, stable une fois créé (anti-doublon) ; survit à la mort du process. */
    private var draftId: String? = savedStateHandle.get<String>("draft_id")
    private val dirty = AtomicBoolean(false)
    private val autoSave = AutoSaveController(viewModelScope, appScope) { final -> persistDraft(final) }
    private fun markDirty() { dirty.set(true); autoSave.markDirty() }
    private fun newDraftId(): String =
        UUID.randomUUID().toString().also { draftId = it; savedStateHandle["draft_id"] = it }
    // ──────────────────────────────────────────────────────────────────────────

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

    // v7.0.3 — sections de notes personnalisables (remplacent notes/likes dans l'UI).
    // Liste vide au départ : l'écran sème la section « Notes » par défaut (titre localisé).
    private val _noteSections = MutableStateFlow<List<NoteSection>>(emptyList())
    val noteSections: StateFlow<List<NoteSection>> = _noteSections.asStateFlow()

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

    /** Contacts (id + nom) existants — alimente l'autocomplétion des relations.
     *  v7.1.6 : la sélection stocke l'id (clé stable) dans linkedPersonId, pas le nom. */
    val relationSuggestions: StateFlow<List<PersonRef>> = repository.getAllActive()
        .map { list -> list.map { PersonRef(it.id, it.fullName) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onNameDetailsChanged(v: NameDetails) { _nameDetails.value = v; markDirty() }
    fun onPhoneLinesChanged(v: List<DynamicLine>) { _phoneLines.value = v; markDirty() }
    fun onEmailLinesChanged(v: List<DynamicLine>) { _emailLines.value = v; markDirty() }
    fun onDateLinesChanged(v: List<DynamicLine>) { _dateLines.value = v; markDirty() }
    fun onRelationLinesChanged(v: List<DynamicLine>) { _relationLines.value = v; markDirty() }
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
        markDirty()
    }

    fun removePendingLink(url: String) {
        _pendingLinks.value = _pendingLinks.value.filter { it.url != url }
        markDirty()
    }

    fun onCityFromMap(city: String, lat: Double?, lng: Double?) {
        _city.value = city
        _cityLat.value = lat
        _cityLng.value = lng
        markDirty()
    }

    fun onFirstNameChanged(v: String) { _firstName.value = v; _firstNameError.value = false; markDirty() }
    fun onLastNameChanged(v: String) { _lastName.value = v; markDirty() }
    fun onCityChanged(v: String) { _city.value = v; _cityLat.value = null; _cityLng.value = null; markDirty() }
    fun onCityNotifyChanged(v: Boolean) { _cityNotify.value = v; markDirty() }
    fun onOriginChanged(v: String) { _origin.value = v; markDirty() }
    fun onJobTitleChanged(v: String) { _jobTitle.value = v; markDirty() }
    fun onDepartmentChanged(v: String) { _department.value = v; markDirty() }
    fun onCompanyChanged(v: String) { _company.value = v; markDirty() }
    fun onNoteSectionsChanged(v: List<NoteSection>) { _noteSections.value = v; markDirty() }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyPhotoToStorage(uri) }
            _photoUri.value = path
            markDirty()
        }
    }

    /**
     * Construit la [Person] depuis l'état du formulaire, avec un [id] STABLE (réutilisé entre le
     * brouillon auto-sauvegardé et la sauvegarde manuelle → jamais de doublon). Réutilise la
     * canonisation ISO des dates (v7.1.0), les sections JSON et les projections scalaires.
     */
    private fun buildPerson(id: String): Person {
        val spec = resolveDateFormatSpec(Locale.getDefault())
        // Listes complètes persistées en JSON ; scalaires `phoneNumber`/`email`/`birthdate` =
        // projection « 1ère ligne » dénormalisée pour les workers, cartes et actions rapides.
        val phone = _phoneLines.value.firstOrNull { it.value.isNotBlank() }?.value?.trim()
        val email = _emailLines.value
            .firstOrNull { it.value.isNotBlank() && it.value.contains('@') }?.value?.trim()
        val birthday = _dateLines.value
            .filter { it.label == FieldTypes.DATE_BIRTHDAY }
            .firstNotNullOfOrNull { rawDigitsToMillis(it.value, spec) }
        val notifyBirthday = _dateLines.value
            .any { it.label == FieldTypes.DATE_BIRTHDAY && it.notify }
        val birthdayOffset = _dateLines.value
            .firstOrNull { it.label == FieldTypes.DATE_BIRTHDAY }?.reminderOffsetMinutes ?: 0
        val nd = _nameDetails.value
        return Person(
            id = id,
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
            noteSections = sanitizeNoteSections(_noteSections.value),
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
            // v7.1.0 — dates en ISO canonique (locale-libre), jamais en chiffres bruts ordonnés
            // par la locale (ancien bug du changement de langue).
            dateLines = sanitizeLines(canonicalizeDateLinesForStorage(_dateLines.value, spec)),
            relationLines = sanitizeLines(_relationLines.value)
        )
    }

    /** Insère les liens sociaux en attente NON encore présents (dédoublonnage par URL). */
    private suspend fun persistPendingLinks(personId: String) {
        if (_pendingLinks.value.isEmpty()) return
        val existing = repository.getSocialLinks(personId).first().map { it.url }.toSet()
        _pendingLinks.value.forEach { link ->
            if (link.url !in existing) {
                repository.addSocialLink(
                    SocialLinkEntity(personId = personId, url = link.url, platform = link.platform)
                )
            }
        }
    }

    /**
     * Auto-save du BROUILLON (v7.1.4). Ne crée RIEN tant qu'aucun nom n'est saisi (anti contact
     * fantôme) ; supprime un brouillon devenu sans nom. Sinon upsert (insert REPLACE, id stable).
     * [final] (sortie/arrière-plan) finalise : liens en attente, relations miroirs, rappels. Aucune
     * géolocalisation réseau ici (réservée au save manuel).
     */
    private suspend fun persistDraft(final: Boolean) {
        // getAndSet : consomme le flag ATOMIQUEMENT (anti-perte, cf. EditPersonViewModel).
        if (!dirty.getAndSet(false)) return
        if (_firstName.value.isBlank()) {
            // Pas de nom → ne rien créer ; un brouillon antérieur devenu anonyme est supprimé.
            draftId?.let { id ->
                repository.hardDelete(id)
                draftId = null
                savedStateHandle.remove<String>("draft_id")
            }
            return
        }
        val id = draftId ?: newDraftId()
        repository.add(buildPerson(id))
        if (final) {
            persistPendingLinks(id)
            repository.syncMirrorRelations(buildPerson(id), previousLines = null)
            ReminderScheduler.rescheduleAll(getApplication())
        }
    }

    fun savePerson(onSuccess: () -> Unit) {
        if (_firstName.value.isBlank()) { _firstNameError.value = true; return }
        // v7.0.5 — refuse la sauvegarde si une date importante est incomplète/invalide
        // (ex. année à 3 chiffres) ; le champ affiche déjà l'erreur côté formulaire.
        val spec = resolveDateFormatSpec(Locale.getDefault())
        if (_dateLines.value.any { !isDateLineValid(it.value, spec) }) return
        viewModelScope.launch {
            // Réutilise l'id du brouillon si déjà créé (sinon en crée un) → jamais de doublon.
            val person = buildPerson(draftId ?: newDraftId())
            val hasCoords = _cityLat.value != null && _cityLng.value != null

            if (presetCategoryId != null) {
                if (hasCoords) repository.addToCategory(person, presetCategoryId)
                else repository.addWithGeocodingToCategory(person, presetCategoryId)
            } else {
                if (hasCoords) repository.add(person)
                else repository.addWithGeocoding(person)
            }

            persistPendingLinks(person.id)

            // Relations miroirs (v5.4.1) : création → toutes les relations sont « nouvelles ».
            repository.syncMirrorRelations(person, previousLines = null)

            // Réarme les rappels (délais par date) immédiatement après l'écriture en base :
            // appel DIRECT (pas via WorkManager) → ni report Doze ni course avec la base. Une
            // date déjà due aujourd'hui est rattrapée à l'instant par [rescheduleAll].
            ReminderScheduler.rescheduleAll(getApplication())
            dirty.set(false)
            onSuccess()
        }
    }

    /**
     * Écran d'Ajout quitté/fermé : flush final GARANTI (scope survivant) — crée/met à jour le
     * brouillon si un nom est présent, sinon ne laisse AUCUN contact (cf. persistDraft).
     */
    override fun onCleared() {
        super.onCleared()
        autoSave.dispose()
    }

    private fun copyPhotoToStorage(uri: Uri): String? = try {
        val context = getApplication<Application>().applicationContext
        val dir = File(context.filesDir, "photos").also { it.mkdirs() }
        val dest = File(dir, "profile_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
        dest.absolutePath
    } catch (_: Exception) { null }
}
