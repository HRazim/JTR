package com.jtr.app.ui.backup

import android.net.Uri
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.data.backup.BackupManager

/**
 * Dialogue « Sauvegarde & restauration » :
 *  - EXPORT via [ActivityResultContracts.CreateDocument] — l'utilisateur choisit
 *    l'emplacement (Téléchargements, Drive…) du fichier `.jtr` ;
 *  - IMPORT via [ActivityResultContracts.GetContent] + dialogue de CONFIRMATION
 *    explicite avant toute écriture en base (fusion, doublons écrasés).
 * Les états (en cours / succès / échec) viennent du [BackupViewModel] (UDF).
 */
@Composable
fun BackupDialog(
    onDismiss: () -> Unit,
    viewModel: BackupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.export(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) pendingImportUri = uri }

    // Confirmation de restauration AVANT d'écraser/fusionner les données.
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    viewModel.import(uri)
                }) { Text(stringResource(R.string.backup_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { viewModel.reset(); onDismiss() },
        icon = { Icon(Icons.Default.SettingsBackupRestore, null,
            tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.backup_menu)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_dialog_text),
                    style = MaterialTheme.typography.bodyMedium)

                when (val s = state) {
                    is BackupUiState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_working),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    is BackupUiState.Success -> Text(
                        text = if (s.isExport)
                            stringResource(R.string.backup_export_success, s.count)
                        else stringResource(R.string.backup_import_success, s.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is BackupUiState.Error -> Text(
                        text = stringResource(R.string.backup_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    BackupUiState.Idle -> {}
                }

                Button(
                    onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                    enabled = state !is BackupUiState.Working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.backup_export))
                }
                OutlinedButton(
                    onClick = { importLauncher.launch("*/*") },
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
            TextButton(onClick = { viewModel.reset(); onDismiss() }) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )
}
