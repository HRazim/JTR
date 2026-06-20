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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class EditPersonViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    /** La notif de proximité ne peut être vraie que si tout est activé globalement. */
    private fun proximityAllowed(): Boolean =
        prefs.getBoolean("notifications_enabled", true) &&
            prefs.getBoolean("proximity_enabled", false)

    // ── Auto-save (v7.1.4) ────────────────────────────────────────────────────
    // Scope SURVIVANT (applicatif) pour le flush final ; repli local hors JTRApplication (tests).
    private val appScope: CoroutineScope =
        (application as? JTRApplication)?.applicationScope
            ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** « Sale » = au moins un champ du profil modifié depuis la dernière persistance. */
    private val dirty = AtomicBoolean(false)
    private val autoSave = AutoSaveController(viewModelScope, appScope) { final -> persistEdits(final) }
    /** À appeler à toute modification d'un champ éditable → arme le debounce de l'auto-save. */
    private fun markDirty() { dirty.set(true); autoSave.markDirty() }
    // ──────────────────────────────────────────────────────────────────────────

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

    // v7.0.3 — sections de notes personnalisables (source de vérité de l'UI ; les
    // colonnes notes/likes restent héritées). Peuplées depuis Room, semées si legacy.
    private val _noteSections = MutableStateFlow<List<NoteSection>>(emptyList())
    val noteSections: StateFlow<List<NoteSection>> = _noteSections.asStateFlow()
    fun onNoteSectionsChanged(v: List<NoteSection>) { _noteSections.value = v; markDirty() }

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

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

    fun onNameDetailsChanged(v: NameDetails) { _nameDetails.value = v; markDirty() }
    fun onPhoneLinesChanged(v: List<DynamicLine>) { _phoneLines.value = v; markDirty() }
    fun onEmailLinesChanged(v: List<DynamicLine>) { _emailLines.value = v; markDirty() }
    fun onDateLinesChanged(v: List<DynamicLine>) { _dateLines.value = v; markDirty() }
    fun onRelationLinesChanged(v: List<DynamicLine>) { _relationLines.value = v; markDirty() }

    /** Noms des autres contacts — alimente l'autocomplétion des relations. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val relationSuggestions: StateFlow<List<String>> = _person
        .flatMapLatest { p ->
            repository.getAllActive().map { list ->
                list.filter { it.id != p?.id }.map { it.fullName }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // ──────────────────────────────────────────────────────────────────────────

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    /**
     * Photo sélectionnée mais pas encore persistée (filesDir/crops/).
     * Sauvegardée dans SavedStateHandle pour survivre à la mort du processus.
     * La copie vers filesDir/photos/ et la mise à jour Room n'ont lieu que
     * dans onCleared() — c'est-à-dire quand l'utilisateur quitte définitivement
     * l'écran (back stack entry détruite).
     */
    private val _pendingPhotoUri = MutableStateFlow<Uri?>(
        savedStateHandle.get<Uri>("pending_photo_uri")?.takeIf { uri ->
            uri.path?.let { File(it).exists() } == true
        }
    )
    val pendingPhotoUri: StateFlow<Uri?> = _pendingPhotoUri.asStateFlow()

    private val _firstNameError = MutableStateFlow(false)
    val firstNameError: StateFlow<Boolean> = _firstNameError.asStateFlow()
    // ─────────────────────────────────────────────────────────────────────────

    fun markAsContacted() {
        val id = _person.value?.id ?: return
        viewModelScope.launch {
            repository.markAsContacted(id)
            _person.value = _person.value?.copy(lastContactedAt = System.currentTimeMillis())
        }
    }

    /**
     * Résout (en arrière-plan, Coroutine) l'id d'un contact par son nom, pour les
     * relations cliquables. [onResult] est rappelé sur le thread principal.
     */
    fun findPersonIdByName(name: String, onResult: (String?) -> Unit) {
        if (name.isBlank()) { onResult(null); return }
        viewModelScope.launch { onResult(repository.findIdByName(name.trim())) }
    }

    /** Bascule le favori instantanément en base (sans toucher à updatedAt). */
    fun toggleFavorite() {
        val p = _person.value ?: return
        val updated = p.copy(isFavorite = !p.isFavorite)
        _person.value = updated
        viewModelScope.launch { repository.update(updated) }
    }
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

    /**
     * Résultat de la carte : FUSION ciblée — seuls ville + lat/lng sont mis à
     * jour, le reste de l'état du formulaire (notes, origine, relations…) est
     * strictement préservé.
     */
    fun onCityFromMap(city: String, lat: Double?, lng: Double?) {
        _city.value = city
        _cityLat.value = lat
        _cityLng.value = lng
        markDirty()
    }

    fun loadPerson(personId: String) {
        // Garde d'idempotence ARMÉE (hotfix v5.3.3) : le formulaire n'est peuplé
        // qu'UNE seule fois par cycle de vie du ViewModel. Au retour de l'écran
        // Map, la recomposition complète rappelle loadPerson() — sans cette garde,
        // populateFields() écrasait les saisies en cours (notes, relations,
        // origine…) avec les valeurs Room, pendant que seule la ville survivait
        // via onCityFromMap. La garde était déclarée mais jamais mise à true.
        if (personLoaded) return
        personLoaded = true
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
        _city.value = p.city ?: ""
        _cityLat.value = p.cityLat
        _cityLng.value = p.cityLng
        _cityNotify.value = p.cityNotify
        _origin.value = p.origin ?: ""
        _jobTitle.value = p.jobTitle ?: ""
        _department.value = p.department ?: ""
        _company.value = p.company ?: ""
        _likes.value = p.likes ?: ""
        _notes.value = p.notes ?: ""
        // Sections persistées telles quelles ; pour un profil legacy (liste vide), l'écran
        // sème la conversion des notes héritées (titres localisés) à l'entrée en édition.
        _noteSections.value = p.noteSections
        _phoneNumber.value = p.phoneNumber ?: ""
        _email.value = p.email ?: ""
        _photoUri.value = p.photoUri

        // Listes dynamiques : on lit en priorité le JSON désérialisé (DB v12).
        // Repli legacy : si la liste est absente (profil pré-v12), on reconstruit
        // depuis les colonnes scalaires conservées. Toujours ≥ 1 ligne pour l'UI.
        val spec = resolveDateFormatSpec(Locale.getDefault())
        _nameDetails.value = NameDetails(
            prefix = p.prefix ?: "",
            middleName = p.middleName ?: "",
            suffix = p.suffix ?: "",
            phonetic = p.phonetic ?: "",
            nickname = p.nickname ?: ""
        )
        _phoneLines.value = p.phoneLines?.takeIf { it.isNotEmpty() }
            ?: p.phoneNumber?.takeIf { it.isNotBlank() }
                ?.let { listOf(DynamicLine(value = it, label = FieldTypes.PHONE_MOBILE)) }
            ?: listOf(DynamicLine(label = FieldTypes.PHONE_MOBILE))
        _emailLines.value = p.emailLines?.takeIf { it.isNotEmpty() }
            ?: p.email?.takeIf { it.isNotBlank() }
                ?.let { listOf(DynamicLine(value = it, label = FieldTypes.EMAIL_HOME)) }
            ?: listOf(DynamicLine(label = FieldTypes.EMAIL_HOME))
        _dateLines.value = p.dateLines?.takeIf { it.isNotEmpty() }
            // v7.1.0 — la valeur STOCKÉE est en ISO canonique : on la reconvertit en chiffres
            // bruts dans la locale COURANTE pour la saisie (changer de langue ne casse plus rien).
            ?.map { it.copy(value = storedDateToRawDigits(it.value, spec)) }
            ?: p.birthdate?.let {
                listOf(DynamicLine(
                    value = millisToRawDigits(it, spec.order),
                    label = FieldTypes.DATE_BIRTHDAY,
                    notify = p.birthdateNotify, // reporte l'ancienne cloche globale sur la ligne
                    reminderOffsetMinutes = p.birthdateReminderOffsetMinutes // …et le délai scalaire
                ))
            }
            ?: listOf(DynamicLine(label = FieldTypes.DATE_BIRTHDAY))
        _relationLines.value = p.relationLines?.takeIf { it.isNotEmpty() }
            ?: listOf(DynamicLine(label = FieldTypes.RELATION_FRIEND))

        personLoaded = true
    }

    /** Replie les listes dynamiques sur les scalaires Room (1ère ligne valide). */
    private fun collapseDynamicLines() {
        val spec = resolveDateFormatSpec(Locale.getDefault())
        _phoneNumber.value = _phoneLines.value.firstOrNull { it.value.isNotBlank() }?.value?.trim() ?: ""
        // Projection scalaire : 1er email syntaxiquement valide (contenant « @ »).
        _email.value = _emailLines.value
            .firstOrNull { it.value.isNotBlank() && it.value.contains('@') }?.value?.trim() ?: ""
        _birthdate.value = _dateLines.value
            .filter { it.label == FieldTypes.DATE_BIRTHDAY }
            .firstNotNullOfOrNull { rawDigitsToMillis(it.value, spec) }
    }

    fun onFirstNameChanged(v: String)    { _firstName.value = v; _firstNameError.value = false; markDirty() }
    fun onLastNameChanged(v: String)     { _lastName.value = v; markDirty() }
    fun onGenderChanged(v: String?)      { _gender.value = v; markDirty() }
    fun onBirthdateChanged(v: Long?)     { _birthdate.value = v; markDirty() }
    fun onCityChanged(v: String)         { _city.value = v; _cityLat.value = null; _cityLng.value = null; markDirty() }
    fun onCityNotifyChanged(v: Boolean)  { _cityNotify.value = v; markDirty() }
    fun onOriginChanged(v: String)        { _origin.value = v; markDirty() }
    fun onJobTitleChanged(v: String)     { _jobTitle.value = v; markDirty() }
    fun onDepartmentChanged(v: String)   { _department.value = v; markDirty() }
    fun onCompanyChanged(v: String)      { _company.value = v; markDirty() }
    fun onLikesChanged(v: String)        { _likes.value = v; markDirty() }
    fun onNotesChanged(v: String)        { _notes.value = v; markDirty() }
    fun onPhoneNumberChanged(v: String)  { _phoneNumber.value = v; markDirty() }
    fun onEmailChanged(v: String)        { _email.value = v; markDirty() }

    fun onPhotoSelected(uri: Uri) {
        // Nettoie l'éventuel fichier crops/ précédent avant de le remplacer
        _pendingPhotoUri.value?.path?.let { old ->
            if (old != uri.path) File(old).delete()
        }
        _pendingPhotoUri.value = uri
        savedStateHandle["pending_photo_uri"] = uri
    }

    /** Sauvegarde globale depuis PersonDetailScreen — reste sur l'écran. */
    fun commitAllEdits() {
        val p = _person.value ?: return
        if (_firstName.value.isBlank()) { _firstNameError.value = true; return }
        // v7.0.5 — refuse une date importante incomplète/invalide (ex. année à 3 chiffres).
        val dateSpec = resolveDateFormatSpec(Locale.getDefault())
        if (_dateLines.value.any { !isDateLineValid(it.value, dateSpec) }) return
        collapseDynamicLines()
        viewModelScope.launch {
            // Photo en attente persistée ici aussi (le bouton « Enregistrer » = flush immédiat).
            finalizePendingPhoto()
            val updated = buildUpdatedPerson(p)
            if (_city.value.trim() != (p.city ?: "") && _cityLat.value == null) {
                repository.updateWithGeocoding(updated, p.city)
            } else {
                repository.update(updated)
            }
            // Propagation du renommage AVANT la synchro miroir : les fiches qui citent
            // ce contact en relation affichent immédiatement le nouveau nom (source de
            // vérité unique malgré la dénormalisation des noms dans relationLines).
            repository.propagateRelationNameChange(updated.id, p.fullName, updated.fullName)
            // Relations miroirs (v5.4.1) : reflète les ajouts/retraits de
            // relations sur les fiches liées (diff avec la snapshot d'avant édition).
            repository.syncMirrorRelations(updated, p.relationLines)
            _person.value = updated
            dirty.set(false)
            _isEditing.value = false
            // Réarme les rappels (délais par date) immédiatement après l'écriture en base :
            // appel DIRECT (pas via WorkManager) → ni report Doze ni course avec la base.
            ReminderScheduler.rescheduleAll(getApplication())
        }
    }

    /**
     * Quitte le mode édition en CONSERVANT les modifications (v7.1.4) : déclenche un flush final
     * dans le scope SURVIVANT (la saisie est de toute façon auto-sauvegardée en continu), puis
     * repasse en lecture. Remplace l'ancien « annuler » — avec l'auto-save, plus de rejet/perte.
     * Appelé par le retour Android et la croix de la barre en mode édition.
     */
    fun exitEdit() {
        autoSave.flush()
        _firstNameError.value = false
        _isEditing.value = false
    }

    /**
     * Auto-save : persiste les champs du profil si « sale », en RÉUTILISANT la logique existante
     * ([buildUpdatedPerson] → canonisation ISO des dates, sections JSON, relations miroirs). Jamais
     * d'écriture identique (comparaison hors `updatedAt`) ni de fiche sans nom (raw préservé en
     * mémoire). [final] (sortie/arrière-plan) ajoute la finalisation : photo définitive + rappels.
     * Aucune géolocalisation réseau ici (réservée au save manuel) → l'auto-save ne fait jamais d'I/O
     * réseau. S'exécute dans `viewModelScope` (frappe) ou `applicationScope` (flush survivant).
     */
    private suspend fun persistEdits(final: Boolean) {
        val p = _person.value ?: return
        // Ne persiste jamais une fiche au nom vide (saisie en cours) — le brut reste en mémoire ;
        // dirty NON consommé → la sauvegarde reprend dès qu'un nom est saisi.
        if (_firstName.value.isBlank()) return
        val pendingPhoto = _pendingPhotoUri.value != null
        // getAndSet : consomme le flag ATOMIQUEMENT. Une frappe survenant pendant l'écriture le
        // RÉARME (+ re-signale le debounce) → jamais d'écrasement silencieux = aucune perte.
        val wasDirty = dirty.getAndSet(false)
        if (!wasDirty && !(final && pendingPhoto)) return
        if (final && pendingPhoto) finalizePendingPhoto()
        collapseDynamicLines()
        val updated = buildUpdatedPerson(p)
        // Pas d'écriture identique : comparaison hors `updatedAt` (estampille de l'écriture).
        if (updated.copy(updatedAt = p.updatedAt) != p) {
            repository.update(updated)
            repository.propagateRelationNameChange(updated.id, p.fullName, updated.fullName)
            repository.syncMirrorRelations(updated, p.relationLines)
            _person.value = updated
        }
        if (final) ReminderScheduler.rescheduleAll(getApplication())
    }

    /** Copie la photo recadrée (crops/ temporaire) vers le stockage permanent (filesDir/photos/). */
    private suspend fun finalizePendingPhoto() {
        val uri = _pendingPhotoUri.value ?: return
        val path = withContext(Dispatchers.IO) { copyPhotoToStorage(uri) } ?: return
        _photoUri.value = path
        uri.path?.let { File(it).delete() }
        _pendingPhotoUri.value = null
        savedStateHandle.remove<Uri>("pending_photo_uri")
    }

    /** Sauvegarde et navigue — utilisé depuis EditPersonScreen. */
    fun updatePerson(onSuccess: () -> Unit) {
        val p = _person.value ?: return
        if (_firstName.value.isBlank()) { _firstNameError.value = true; return }
        // v7.0.5 — refuse une date importante incomplète/invalide (ex. année à 3 chiffres).
        val dateSpec = resolveDateFormatSpec(Locale.getDefault())
        if (_dateLines.value.any { !isDateLineValid(it.value, dateSpec) }) return
        viewModelScope.launch {
            val updated = buildUpdatedPerson(p)
            if (_city.value.trim() != (p.city ?: "") && _cityLat.value == null) {
                repository.updateWithGeocoding(updated, p.city)
            } else {
                repository.update(updated)
            }
            repository.propagateRelationNameChange(updated.id, p.fullName, updated.fullName)
            repository.syncMirrorRelations(updated, p.relationLines)
            // Réarme les rappels (délais par date) immédiatement après l'écriture en base :
            // appel DIRECT (pas via WorkManager) → ni report Doze ni course avec la base. Une
            // date déjà due aujourd'hui est rattrapée à l'instant par [rescheduleAll].
            ReminderScheduler.rescheduleAll(getApplication())
            onSuccess()
        }
    }

    private fun buildUpdatedPerson(p: Person): Person {
        // v7.1.0 — dates converties en ISO canonique (locale-libre) avant persistance.
        val spec = resolveDateFormatSpec(Locale.getDefault())
        return p.copy(
        updatedAt      = System.currentTimeMillis(),
        firstName      = _firstName.value.trim(),
        lastName       = _lastName.value.trim().ifBlank { null },
        gender         = _gender.value,
        birthdate      = _birthdate.value,
        // Dénormalisation : la cloche globale vient désormais de la ligne anniversaire.
        birthdateNotify = _dateLines.value.any { it.label == FieldTypes.DATE_BIRTHDAY && it.notify },
        // …de même que le délai de rappel scalaire (projection de la ligne anniversaire).
        birthdateReminderOffsetMinutes = _dateLines.value
            .firstOrNull { it.label == FieldTypes.DATE_BIRTHDAY }?.reminderOffsetMinutes ?: 0,
        city           = _city.value.trim().ifBlank { null },
        cityLat        = _cityLat.value,
        cityLng        = _cityLng.value,
        cityNotify     = _cityNotify.value && proximityAllowed(),
        photoUri       = _photoUri.value,
        notes          = _notes.value.trim().ifBlank { null },
        likes          = _likes.value.trim().ifBlank { null },
        // v7.0.3 — les sections deviennent la source de vérité (notes/likes héritées
        // laissées telles quelles, inutilisées par l'UI).
        noteSections   = sanitizeNoteSections(_noteSections.value),
        origin         = _origin.value.trim().ifBlank { null },
        jobTitle       = _jobTitle.value.trim().ifBlank { null },
        department     = _department.value.trim().ifBlank { null },
        company        = _company.value.trim().ifBlank { null },
        phoneNumber    = _phoneNumber.value.trim().ifBlank { null },
        email          = _email.value.trim().ifBlank { null },
        prefix         = _nameDetails.value.prefix.trim().ifBlank { null },
        middleName     = _nameDetails.value.middleName.trim().ifBlank { null },
        suffix         = _nameDetails.value.suffix.trim().ifBlank { null },
        phonetic       = _nameDetails.value.phonetic.trim().ifBlank { null },
        nickname       = _nameDetails.value.nickname.trim().ifBlank { null },
        phoneLines     = sanitizeLines(_phoneLines.value),
        emailLines     = sanitizeEmailLines(_emailLines.value),
        dateLines      = sanitizeLines(canonicalizeDateLinesForStorage(_dateLines.value, spec)),
        relationLines  = sanitizeLines(_relationLines.value)
        )
    }

    /**
     * Back-stack entry définitivement détruite (back, navigation, fermeture). Le `viewModelScope`
     * est déjà en cours d'annulation → le flush final passe par le scope APPLICATIF survivant via
     * [AutoSaveController.dispose] : la persistance des dernières modifications ET de la photo en
     * attente se TERMINE même si le ViewModel est nettoyé dans la foulée (cf. persistEdits final).
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
