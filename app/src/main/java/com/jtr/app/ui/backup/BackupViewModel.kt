package com.jtr.app.ui.backup

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jtr.app.R
import com.jtr.app.data.backup.BackupManager
import com.jtr.app.data.backup.RestoreError
import com.jtr.app.data.backup.RestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** États d'UI réactifs du module de sauvegarde (UDF strict). */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val isExport: Boolean) : BackupUiState
    data class Success(val isExport: Boolean, val count: Int) : BackupUiState
    /** Échec : [messageRes] = message DISTINCT selon la cause (v7.1.8). */
    data class Error(val isExport: Boolean, @StringRes val messageRes: Int) : BackupUiState
    /** Sélecteur refermé sans choix (v7.1.143) : dit explicitement que rien n'a été fait. */
    data class Cancelled(val isExport: Boolean) : BackupUiState
}

/** Sélecteur à ouvrir par [BackupPickers] ; l'événement est consommé au lancement. */
enum class BackupPickerRequest { EXPORT, IMPORT }

/**
 * Session « Sauvegarde & restauration », à portée d'ACTIVITÉ (v7.1.143) : elle vit au-dessus
 * de la porte du verrou. Le dialogue n'est qu'une vue de cet état, il peut quitter la
 * composition (écran de verrou affiché pendant le sélecteur) sans qu'une opération lancée
 * par l'utilisateur ne soit perdue ni que son issue reste muette.
 */
class BackupViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // Paresseux : ce ViewModel naît avec l'activité, la base n'est ouverte qu'à la 1re opération.
    private val manager by lazy { BackupManager(application.applicationContext) }

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _pickerRequest = MutableStateFlow<BackupPickerRequest?>(null)
    val pickerRequest: StateFlow<BackupPickerRequest?> = _pickerRequest.asStateFlow()

    /**
     * Archive en attente de CONFIRMATION (sélecteur ou ouverture externe), jamais appliquée
     * seule. Vidée par la confirmation comme par l'annulation : elle ne se ré-arme donc pas
     * aux déverrouillages suivants.
     */
    val pendingImportUri: StateFlow<Uri?> =
        savedStateHandle.getStateFlow<Uri?>(KEY_PENDING_IMPORT, null)

    private val dialogOpen = MutableStateFlow(false)

    /**
     * Le dialogue reste visible tant qu'il est ouvert OU qu'une confirmation ou une issue
     * attend l'utilisateur : une opération terminée pendant l'écran de verrou s'affiche au
     * déverrouillage.
     */
    val dialogVisible: StateFlow<Boolean> =
        combine(dialogOpen, pendingImportUri, _state) { open, pending, state ->
            open || pending != null || state !is BackupUiState.Idle
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun openDialog() {
        dialogOpen.value = true
    }

    /** Fermeture du dialogue ; une opération en cours reste affichée jusqu'à son issue. */
    fun closeDialog() {
        dialogOpen.value = false
        if (_state.value !is BackupUiState.Working) _state.value = BackupUiState.Idle
    }

    fun requestExport() = request(BackupPickerRequest.EXPORT)

    fun requestImport() = request(BackupPickerRequest.IMPORT)

    private fun request(picker: BackupPickerRequest) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Idle
        _pickerRequest.value = picker
    }

    fun onPickerLaunched() {
        _pickerRequest.value = null
    }

    /** Retour du sélecteur d'export : `null` = refermé sans choix, aucun fichier créé. */
    fun onExportDestination(uri: Uri?) {
        if (uri == null) {
            _state.value = BackupUiState.Cancelled(isExport = true)
            return
        }
        viewModelScope.launch {
            _state.value = BackupUiState.Working(isExport = true)
            manager.exportTo(uri)
                .onSuccess { _state.value = BackupUiState.Success(isExport = true, count = it) }
                .onFailure {
                    // Le sélecteur a déjà CRÉÉ le document : un fichier vide ou tronqué ne doit
                    // pas rester sur l'appareil en se faisant passer pour une sauvegarde.
                    deleteDocument(uri)
                    _state.value = BackupUiState.Error(isExport = true, messageRes = R.string.backup_failed)
                }
        }
    }

    /** Retour du sélecteur de restauration : l'archive attend la confirmation explicite. */
    fun onImportSource(uri: Uri?) {
        if (uri == null) _state.value = BackupUiState.Cancelled(isExport = false)
        else savedStateHandle[KEY_PENDING_IMPORT] = uri
    }

    /** `.jtr` ouvert depuis un gestionnaire de fichiers (ACTION_VIEW), offert une seule fois. */
    fun offerExternalImport(uri: Uri) {
        savedStateHandle[KEY_PENDING_IMPORT] = uri
        dialogOpen.value = true
    }

    fun cancelImport() {
        savedStateHandle[KEY_PENDING_IMPORT] = null
    }

    /** Restaure l'archive en attente, après confirmation explicite côté UI. */
    fun confirmImport() {
        val uri = pendingImportUri.value ?: return
        savedStateHandle[KEY_PENDING_IMPORT] = null
        viewModelScope.launch {
            _state.value = BackupUiState.Working(isExport = false)
            manager.importFrom(uri)
                .onSuccess { _state.value = BackupUiState.Success(isExport = false, count = it) }
                .onFailure { e ->
                    // Message DISTINCT selon la cause typée (v7.1.8) au lieu du fourre-tout.
                    val res = when ((e as? RestoreException)?.reason) {
                        RestoreError.UNREADABLE -> R.string.backup_restore_unreadable
                        RestoreError.NOT_ARCHIVE -> R.string.backup_restore_not_archive
                        RestoreError.UNSUPPORTED_VERSION -> R.string.backup_restore_version
                        RestoreError.CORRUPT -> R.string.backup_restore_corrupt
                        RestoreError.WRITE_FAILED -> R.string.backup_restore_write_failed
                        null -> R.string.backup_failed
                    }
                    _state.value = BackupUiState.Error(isExport = false, messageRes = res)
                }
        }
    }

    private fun deleteDocument(uri: Uri) {
        runCatching {
            DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri)
        }
    }

    private companion object {
        const val KEY_PENDING_IMPORT = "backup_pending_import_uri"
    }
}
