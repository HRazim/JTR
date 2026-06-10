package com.jtr.app.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtr.app.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** États d'UI réactifs du module de sauvegarde (UDF strict). */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val isExport: Boolean) : BackupUiState
    data class Success(val isExport: Boolean, val count: Int) : BackupUiState
    data class Error(val isExport: Boolean) : BackupUiState
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BackupManager(application.applicationContext)

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Exporte la base complète vers l'URI choisie par l'utilisateur (SAF). */
    fun export(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working(isExport = true)
            manager.exportTo(uri)
                .onSuccess { _state.value = BackupUiState.Success(isExport = true, count = it) }
                .onFailure { _state.value = BackupUiState.Error(isExport = true) }
        }
    }

    /** Restaure l'archive .jtr choisie (après confirmation explicite côté UI). */
    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Working(isExport = false)
            manager.importFrom(uri)
                .onSuccess { _state.value = BackupUiState.Success(isExport = false, count = it) }
                .onFailure { _state.value = BackupUiState.Error(isExport = false) }
        }
    }

    fun reset() { _state.value = BackupUiState.Idle }
}
