package com.jtr.app.ui.welcome

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.contacts.ContactsImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Lance l'importation native (permission READ_CONTACTS déjà accordée). */
    fun startImport() {
        if (_state.value is WelcomeUiState.Importing) return
        _state.value = WelcomeUiState.Importing(0, 0)
        viewModelScope.launch {
            importer.import { done, total ->
                _state.value = WelcomeUiState.Importing(done, total)
            }.onSuccess { count ->
                markOnboardingComplete()
                _state.value = WelcomeUiState.Done(count)
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
    }
}
