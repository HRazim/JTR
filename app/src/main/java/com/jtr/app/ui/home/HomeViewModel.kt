package com.jtr.app.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.repository.PersonRepository
import com.jtr.app.domain.model.Person
import com.jtr.app.domain.model.SocialLinkEntity
import com.jtr.app.ui.category.ContactSortOrder
import com.jtr.app.ui.category.sortPersonsBy
import com.jtr.app.ui.components.JtrViewMode
import com.jtr.app.ui.person.FieldTypes
import com.jtr.app.ui.person.storedDateToMillis
import com.jtr.app.utils.LocationUtils
import com.jtr.app.utils.matchesSearch
import com.jtr.app.utils.normalizeForSearch
import com.jtr.app.utils.searchTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * Événement à venir (≤ 7 jours) affiché dans le bandeau de l'Accueil :
 * anniversaire ou date clé d'un contact.
 */
data class UpcomingEvent(
    val person: Person,
    /** Clé de type ([FieldTypes.DATE]) ou libellé personnalisé brut. */
    val typeKey: String,
    /** 0 = aujourd'hui, 1..7 = dans n jours. */
    val daysUntil: Int
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PersonRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Requête débouncée alimentant le FILTRE (le champ texte, lui, reste branché sur
     * [searchQuery] pour un retour visuel immédiat). Une requête vide passe sans délai
     * (effacement instantané) ; une saisie attend ~250 ms d'inactivité avant de filtrer
     * → pas de recalcul à chaque frappe, fluide même à plusieurs milliers de contacts.
     */
    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> =
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isLocationEnabled: StateFlow<Boolean> = locationEnabledFlow(getApplication())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val socialLinksMap: StateFlow<Map<String, List<SocialLinkEntity>>> =
        repository.getAllSocialLinks()
            .map { links -> links.groupBy { it.personId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Tri préféré de l'Accueil, restauré depuis les préférences (même mécanique que
    // le tri par catégorie de CategoryDetailViewModel).
    private val _sortOrder = MutableStateFlow(
        runCatching { ContactSortOrder.valueOf(prefs.getString(SORT_PREF_KEY, null) ?: "") }
            .getOrDefault(ContactSortOrder.NAME_ASC)
    )
    val sortOrder: StateFlow<ContactSortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: ContactSortOrder) {
        _sortOrder.value = order
        prefs.edit().putString(SORT_PREF_KEY, order.name).apply()
    }

    // Mode d'affichage de la liste (LIST / GRID / DETAIL), persisté.
    private val _viewMode = MutableStateFlow(
        JtrViewMode.fromPref(prefs.getString(VIEW_PREF_KEY, null), JtrViewMode.DETAIL)
    )
    val viewMode: StateFlow<JtrViewMode> = _viewMode.asStateFlow()

    fun setViewMode(mode: JtrViewMode) {
        _viewMode.value = mode
        prefs.edit().putString(VIEW_PREF_KEY, mode.name).apply()
    }

    // Libellés localisés des types de relation standards (clé → libellé normalisé) :
    // taper « Ami » ou « Collègue » matche les profils portant ce type de relation.
    private val relationTypeLabels: Map<String, String> =
        FieldTypes.RELATION.filter { it.key != FieldTypes.CUSTOM }
            .associate { it.key to application.getString(it.labelRes).normalizeForSearch() }

    // Filtrage multi-critères + tri exécutés sur Dispatchers.Default (flowOn) :
    // jamais de travail bloquant sur le thread principal — l'UI reste à 120 Hz.
    val persons: StateFlow<List<Person>> = combine(
        repository.getAllActive(), debouncedQuery, _sortOrder
    ) { list, query, order ->
        val tokens = query.searchTokens()
        val filtered = if (tokens.isEmpty()) list
            else list.filter { it.matchesSearch(tokens) { key -> relationTypeLabels[key] } }
        sortPersonsBy(filtered, order)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Liste active NON filtrée — sert aux actions de masse (partage…) : un contact
     * coché sous la recherche « A » reste ciblé même quand la requête devient « B »
     * et qu'il n'est plus affiché. La sélection ne dépend JAMAIS du filtre.
     */
    val allActivePersons: StateFlow<List<Person>> = repository.getAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Anniversaires et dates clés des 7 PROCHAINS JOURS, triés chronologiquement.
     * Les dates dynamiques (dateLines) priment ; repli sur le scalaire birthdate
     * pour les profils legacy. Calcul hors thread principal.
     */
    val upcomingEvents: StateFlow<List<UpcomingEvent>> = repository.getAllActive()
        .map { persons -> computeUpcomingEvents(persons) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun computeUpcomingEvents(persons: List<Person>): List<UpcomingEvent> {
        val today = LocalDate.now()
        val events = ArrayList<UpcomingEvent>()
        persons.forEach { person ->
            val dates: List<Pair<Long, String>> =
                person.dateLines?.takeIf { it.isNotEmpty() }
                    ?.mapNotNull { line ->
                        // Interprétation LOCALE-LIBRE (ISO canonique), repli hérité géré.
                        storedDateToMillis(line.value)?.let { it to line.label }
                    }
                    ?: person.birthdate?.let { listOf(it to FieldTypes.DATE_BIRTHDAY) }.orEmpty()
            dates.forEach { (millis, label) ->
                val cal = Calendar.getInstance().apply { timeInMillis = millis }
                val next = nextOccurrence(
                    today, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
                ) ?: return@forEach
                val days = ChronoUnit.DAYS.between(today, next).toInt()
                if (days in 0..UPCOMING_WINDOW_DAYS) {
                    events.add(UpcomingEvent(person, label, days))
                }
            }
        }
        return events.sortedWith(
            compareBy({ it.daysUntil }, { it.person.fullName.lowercase() })
        )
    }

    /** Prochaine occurrence (année courante ou suivante) d'un mois/jour donné. */
    private fun nextOccurrence(today: LocalDate, month: Int, day: Int): LocalDate? = try {
        val thisYear = LocalDate.of(today.year, month, day)
        if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
    } catch (_: Exception) {
        null // 29 février hors année bissextile, valeurs corrompues…
    }

    init {
        viewModelScope.launch {
            repository.migrateFromJson()
            repository.purgeOldDeleted()
        }
    }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query }

    /**
     * Réinitialise le filtre de recherche. Appelé quand l'écran quitte la composition
     * (navigation vers un détail / un autre onglet) : au retour, la liste COMPLÈTE
     * réapparaît — plus aucun filtre fantôme (« disparition des contacts »).
     */
    fun clearSearch() { _searchQuery.value = "" }

    fun toggleFavorite(person: Person) {
        viewModelScope.launch { repository.toggleFavorite(person) }
    }

    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    /**
     * Coche tous les profils actuellement AFFICHÉS (recherche comprise) — ADDITIF :
     * les coches faites sous une autre requête de recherche ne sont jamais perdues.
     */
    fun selectAll() {
        _selectedIds.update { current -> current + persons.value.map { it.id } }
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.softDeleteMultiple(ids)
            _selectedIds.value = emptySet()
        }
    }

    companion object {
        private const val SORT_PREF_KEY = "home_sort_order"
        private const val VIEW_PREF_KEY = "home_view_mode"
        private const val UPCOMING_WINDOW_DAYS = 7

        /** Délai d'inactivité avant de relancer le filtre de recherche (ms). */
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}

private fun locationEnabledFlow(context: Context): Flow<Boolean> = callbackFlow {
    trySend(LocationUtils.isLocationEnabled(context))

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            trySend(LocationUtils.isLocationEnabled(context))
        }
    }
    context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
    awaitClose { context.unregisterReceiver(receiver) }
}
