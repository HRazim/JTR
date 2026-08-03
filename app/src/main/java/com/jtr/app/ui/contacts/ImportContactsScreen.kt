package com.jtr.app.ui.contacts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jtr.app.R
import com.jtr.app.ui.welcome.ContactSelectionScreen

/**
 * Écran d'import des contacts du téléphone depuis les PARAMÈTRES (v7.1.28).
 *
 * Réutilise tel quel l'écran de sélection de l'onboarding ([ContactSelectionScreen]) et
 * l'unique importateur via [ImportContactsViewModel]. La permission READ_CONTACTS est
 * déjà accordée AVANT la navigation ici (machine à états des Paramètres) → on charge la
 * liste directement. La progression et le récap (X importés · Y ignorés) sont posés
 * EN SURCOUCHE pour garder la liste en contexte derrière.
 */
@Composable
fun ImportContactsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportContactsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filtered by viewModel.filteredDeviceContacts.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedContactIds.collectAsStateWithLifecycle()
    val search by viewModel.contactSearch.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadDeviceContacts() }

    // La liste cochable reste le fond ; importation/récap viennent par-dessus.
    ContactSelectionScreen(
        contacts = filtered,
        selectedIds = selectedIds,
        searchQuery = search,
        onSearchChange = viewModel::setContactSearch,
        onToggle = viewModel::toggleContact,
        onBack = onNavigateBack,
        onConfirm = { strategy -> viewModel.startImport(strategy) }
    )

    when (val s = state) {
        is ImportContactsViewModel.UiState.Importing -> {
            // Bloquant volontairement (pas d'annulation au milieu d'une écriture par lots).
            BackHandler(enabled = true) { }
            ImportProgressDialog(done = s.done, total = s.total)
        }

        is ImportContactsViewModel.UiState.Done -> {
            ImportRecapDialog(
                imported = s.imported,
                updated = s.updated,
                skipped = s.skipped,
                onDismiss = onNavigateBack
            )
        }

        is ImportContactsViewModel.UiState.Error -> {
            AlertDialog(
                onDismissRequest = onNavigateBack,
                icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
                title = { Text(stringResource(R.string.welcome_import_failed)) },
                confirmButton = {
                    TextButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
            )
        }

        ImportContactsViewModel.UiState.Selecting -> Unit
    }
}

/** Progression d'import (non annulable) : barre déterminée dès que le total est connu. */
@Composable
private fun ImportProgressDialog(done: Int, total: Int) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (total > 0)
                        pluralStringResource(R.plurals.welcome_importing, total, total)
                    else stringResource(R.string.settings_import_contacts_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { done / total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Récap final. Sans mise à jour (B7) → message historique « X importés · Y ignorés » (aucune
 * régression onboarding/SKIP). Avec ≥1 mise à jour → message à 3 nombres « X · Y · Z ».
 */
@Composable
private fun ImportRecapDialog(imported: Int, updated: Int, skipped: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Contacts, contentDescription = null) },
        title = { Text(stringResource(R.string.import_contacts_done_title)) },
        text = {
            Text(
                if (updated > 0)
                    stringResource(R.string.import_contacts_done_message_updated, imported, updated, skipped)
                else stringResource(R.string.import_contacts_done_message, imported, skipped)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        }
    )
}
