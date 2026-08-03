package com.jtr.app.ui.welcome

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.contacts.ContactsImporter
import com.jtr.app.data.contacts.DeviceContact
import com.jtr.app.data.contacts.DuplicateStrategy
import com.jtr.app.utils.matchesAllTokens
import com.jtr.app.utils.searchTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** États d'UI réactifs de l'onboarding (UDF strict). */
sealed interface WelcomeUiState {
    data object Idle : WelcomeUiState

    /** Importation en cours : [done] insérés sur [total] détectés. */
    data class Importing(val done: Int, val total: Int) : WelcomeUiState

    data class Done(val count: Int) : WelcomeUiState
    data object Error : WelcomeUiState
}

class WelcomeViewModel(application: Application) : AndroidViewModel(application) {

    private val importer = ContactsImporter(application.applicationContext)
    private val prefs = application.getSharedPreferences("jtr_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<WelcomeUiState>(WelcomeUiState.Idle)
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    // ── Importation SÉLECTIVE (v5.4.1) — état autonome du ViewModel (UDF) ─────

    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())

    private val _contactSearch = MutableStateFlow("")
    val contactSearch: StateFlow<String> = _contactSearch.asStateFlow()

    /** Requête débouncée alimentant le filtre (cf. HomeViewModel) — vide = sans délai. */
    @OptIn(FlowPreview::class)
    private val debouncedSearch: Flow<String> =
        _contactSearch.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    fun setContactSearch(query: String) { _contactSearch.value = query }

    /** Coches de l'écran de sélection — survivent à la recherche et au scroll. */
    private val _selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedContactIds: StateFlow<Set<Long>> = _selectedContactIds.asStateFlow()

    fun toggleContact(id: Long) {
        _selectedContactIds.update { if (id in it) it - id else it + id }
    }

    /** Liste native filtrée par la recherche (calcul hors thread principal). */
    val filteredDeviceContacts: StateFlow<List<DeviceContact>> = combine(
        _deviceContacts, debouncedSearch
    ) { contacts, query ->
        val tokens = query.searchTokens()
        if (tokens.isEmpty()) contacts
        else contacts.filter { it.displayName.matchesAllTokens(tokens) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Charge (une fois) la liste légère du répertoire natif pour la sélection. */
    fun loadDeviceContacts() {
        if (_deviceContacts.value.isNotEmpty()) return
        viewModelScope.launch {
            _deviceContacts.value = importer.listDeviceContacts()
        }
    }

    /**
     * Lance l'importation native (permission READ_CONTACTS déjà accordée).
     * @param selectedIds restreint l'import aux contacts cochés ; null = tout.
     * @param strategy comportement sur doublon (B7) ; défaut SKIP — à l'onboarding la base
     *   est vierge, donc sans effet, mais l'écran de sélection peut tout de même le fournir.
     */
    fun startImport(
        selectedIds: Set<Long>? = null,
        strategy: DuplicateStrategy = DuplicateStrategy.SKIP
    ) {
        if (_state.value is WelcomeUiState.Importing) return
        _state.value = WelcomeUiState.Importing(0, 0)
        viewModelScope.launch {
            importer.import(selectedIds, strategy) { done, total ->
                _state.value = WelcomeUiState.Importing(done, total)
            }.onSuccess { result ->
                markOnboardingComplete()
                // Onboarding : la base est vierge au premier lancement → `skipped` vaut 0 ;
                // on n'expose que le nombre importé (UX d'accueil inchangée).
                _state.value = WelcomeUiState.Done(result.imported)
            }.onFailure {
                _state.value = WelcomeUiState.Error
            }
        }
    }

    /** Marque la première ouverture comme consommée (flag persistant). */
    fun markOnboardingComplete() {
        prefs.edit().putBoolean(FIRST_LAUNCH_KEY, false).apply()
    }

    companion object {
        const val FIRST_LAUNCH_KEY = "is_first_launch"

        /** Délai d'inactivité avant de relancer le filtre de recherche (ms). */
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
