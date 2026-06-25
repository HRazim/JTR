package com.jtr.app.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.contacts.ContactsImporter
import com.jtr.app.data.contacts.DeviceContact
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

/**
 * Import de contacts depuis les PARAMÈTRES (v7.1.28) — point d'entrée RÉ-IMPORTABLE,
 * distinct de l'onboarding (`WelcomeViewModel`) mais s'appuyant sur le MÊME et UNIQUE
 * importateur [ContactsImporter] (zéro duplication de la logique d'import / dédup).
 *
 * Contrairement à l'onboarding, ce flux ne touche PAS le flag de première ouverture et
 * expose le bilan complet (`imported` + `skipped`) pour le récap. Le dédoublonnage SKIP
 * vit dans [ContactsImporter.import] → identique aux deux chemins.
 */
class ImportContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val importer = ContactsImporter(application.applicationContext)

    /** États d'UI réactifs (UDF strict), miroir léger de l'onboarding sans sa sémantique. */
    sealed interface UiState {
        /** Sélection en cours (liste cochable). */
        data object Selecting : UiState

        /** Importation en cours : [done] traités sur [total]. */
        data class Importing(val done: Int, val total: Int) : UiState

        /** Terminé : [imported] insérés, [skipped] ignorés (déjà dans JTR). */
        data class Done(val imported: Int, val skipped: Int) : UiState

        data object Error : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Selecting)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())

    private val _contactSearch = MutableStateFlow("")
    val contactSearch: StateFlow<String> = _contactSearch.asStateFlow()

    @OptIn(FlowPreview::class)
    private val debouncedSearch: Flow<String> =
        _contactSearch.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    fun setContactSearch(query: String) { _contactSearch.value = query }

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

    private var loaded = false

    /** Charge (une seule fois) la liste légère du répertoire natif. Permission déjà accordée. */
    fun loadDeviceContacts() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _deviceContacts.value = importer.listDeviceContacts()
        }
    }

    /** Lance l'import des contacts cochés (dédoublonnage SKIP appliqué par l'importateur). */
    fun startImport() {
        if (_state.value is UiState.Importing) return
        _state.value = UiState.Importing(0, 0)
        viewModelScope.launch {
            importer.import(_selectedContactIds.value) { done, total ->
                _state.value = UiState.Importing(done, total)
            }.onSuccess { result ->
                _state.value = UiState.Done(result.imported, result.skipped)
            }.onFailure {
                _state.value = UiState.Error
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
