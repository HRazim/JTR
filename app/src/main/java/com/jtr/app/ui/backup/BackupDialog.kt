package com.jtr.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtr.app.R
import com.jtr.app.data.backup.BackupManager
import com.jtr.app.ui.components.JtrAlertDialog

/**
 * Sélecteurs SAF de la sauvegarde (v7.1.143), à monter AU-DESSUS de la porte du verrou :
 * enregistrés en permanence, ils reçoivent leur résultat même si l'écran de verrou s'est
 * affiché pendant que le sélecteur était ouvert. Montés sous la porte, ils se désinscrivaient
 * et le résultat était perdu (export de 0 octet, restauration sans effet, sans message).
 */
@Composable
fun BackupPickers(viewModel: BackupViewModel) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
        viewModel::onExportDestination
    )
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
        viewModel::onImportSource
    )
    val request by viewModel.pickerRequest.collectAsState()
    LaunchedEffect(request) {
        when (request ?: return@LaunchedEffect) {
            BackupPickerRequest.EXPORT -> exportLauncher.launch(BackupManager.suggestedFileName())
            BackupPickerRequest.IMPORT -> importLauncher.launch("*/*")
        }
        viewModel.onPickerLaunched()
    }
}

/** Affiche le dialogue tant que la session de sauvegarde le demande (à monter sous la porte). */
@Composable
fun BackupDialogHost(viewModel: BackupViewModel) {
    val visible by viewModel.dialogVisible.collectAsStateWithLifecycle()
    if (visible) BackupDialog(viewModel)
}

/**
 * Dialogue « Sauvegarde & restauration » :
 *  - EXPORT via [ActivityResultContracts.CreateDocument] — l'utilisateur choisit
 *    l'emplacement (Téléchargements, Drive…) du fichier `.jtr` ;
 *  - IMPORT via [ActivityResultContracts.GetContent] + dialogue de CONFIRMATION
 *    explicite avant toute écriture en base (fusion, doublons écrasés).
 * Vue sans état propre : sélecteurs, archive en attente et issues vivent dans le
 * [BackupViewModel] d'activité (UDF), qui survit à l'écran de verrou.
 */
@Composable
private fun BackupDialog(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingImportUri by viewModel.pendingImportUri.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = viewModel::closeDialog,
        icon = { Icon(Icons.Default.SettingsBackupRestore, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.backup_menu)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_dialog_text),
                    style = MaterialTheme.typography.bodyMedium)

                when (val s = state) {
                    // v7.1.136 — 24 dp comme [com.jtr.app.ui.components.JtrProgressDialog] :
                    // deux tailles de cercle pour la même idée, c'est une de trop.
                    is BackupUiState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_working),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    // v7.1.136 — succès en `onSurfaceVariant` et non `primary` : en Corail,
                    // `primary` est le rouge brique, et « sauvegarde exportée » s'affichait
                    // donc en rouge, juste au-dessus du message d'échec. Le rouge reste au
                    // seul `error` (ci-dessous) — 5e morsure de `primary`, même remède.
                    is BackupUiState.Success -> Text(
                        text = if (s.isExport)
                            pluralStringResource(R.plurals.backup_export_success, s.count, s.count)
                        else pluralStringResource(R.plurals.backup_import_success, s.count, s.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is BackupUiState.Error -> Text(
                        text = stringResource(s.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    is BackupUiState.Cancelled -> Text(
                        text = stringResource(
                            if (s.isExport) R.string.backup_export_cancelled
                            else R.string.backup_import_cancelled
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    BackupUiState.Idle -> {}
                }

                Button(
                    onClick = viewModel::requestExport,
                    enabled = state !is BackupUiState.Working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_export))
                }
                // v7.1.134 — paire canonique sous « Exporter » (`Button` plein) : plein fort
                // puis plein doux. Volontairement PAS en `error` bien qu'il mène à une
                // restauration destructive — le rouge est porté par la confirmation, le poser
                // dès l'entrée le banaliserait.
                FilledTonalButton(
                    onClick = viewModel::requestImport,
                    enabled = state !is BackupUiState.Working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_import))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::closeDialog) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )

    // Confirmation de restauration AVANT d'écraser/fusionner les données. Composée APRÈS le
    // dialogue principal : chaque dialogue ouvre sa fenêtre dans l'ordre de composition, et
    // au déverrouillage les deux entrent ensemble (archive déjà en attente) — composée avant,
    // la confirmation s'ouvrait SOUS le dialogue principal, invisible et inatteignable.
    if (pendingImportUri != null) {
        // v7.1.132 - DESTRUCTIF au sens du socle : la restauration fusionne et ECRASE les
        // fiches de meme identifiant, sans retour arriere. L'icone d'alerte reste donc rouge
        // (c'est elle qui porte la distinction depuis v7.1.131), et la confirmation avec elle.
        JtrAlertDialog(
            onDismissRequest = viewModel::cancelImport,
            icon = Icons.Default.Warning,
            title = stringResource(R.string.backup_confirm_title),
            text = stringResource(R.string.backup_confirm_text),
            confirmLabel = stringResource(R.string.backup_confirm_action),
            onConfirm = viewModel::confirmImport,
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true
        )
    }
}
